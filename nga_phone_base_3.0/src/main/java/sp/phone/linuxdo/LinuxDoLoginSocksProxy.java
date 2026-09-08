package sp.phone.linuxdo;


import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bounded loopback HTTP CONNECT tunnel for the isolated LINUX DO login WebView.
 * It resolves domain targets through the configured DoH and relays TLS opaquely.
 */
final class LinuxDoLoginSocksProxy implements Closeable {

    interface Resolver extends Closeable {
        List<InetAddress> lookup(String hostname) throws UnknownHostException;
    }

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int HANDSHAKE_TIMEOUT_MS = 15_000;
    private static final int MAX_CONNECTION_THREADS = 24;
    private static final int MAX_CONNECT_HEADER_BYTES = 16 * 1024;

    private final Resolver mResolver;
    private final AtomicBoolean mRunning = new AtomicBoolean();
    private final AtomicInteger mAddressCursor = new AtomicInteger();
    private final Set<Socket> mSockets = Collections.synchronizedSet(new HashSet<>());
    private final ExecutorService mAcceptExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "linuxdo-login-socks-accept");
        thread.setDaemon(true);
        return thread;
    });
    private final ThreadPoolExecutor mConnectionExecutor = new ThreadPoolExecutor(
            0, MAX_CONNECTION_THREADS, 30L, TimeUnit.SECONDS,
            new SynchronousQueue<>(), runnable -> {
                Thread thread = new Thread(runnable, "linuxdo-login-socks-io");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    // A tunnel needs one blocking pump in each direction. Keeping the reverse pumps in a
    // separate bounded pool prevents a Cloudflare/Discourse page with many parallel assets
    // from consuming both slots per connection and rejecting otherwise valid TLS tunnels.
    private final ThreadPoolExecutor mRelayExecutor = new ThreadPoolExecutor(
            0, MAX_CONNECTION_THREADS, 30L, TimeUnit.SECONDS,
            new SynchronousQueue<>(), runnable -> {
                Thread thread = new Thread(runnable, "linuxdo-login-socks-relay");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    private ServerSocket mServerSocket;

    LinuxDoLoginSocksProxy(Resolver resolver) {
        if (resolver == null) throw new IllegalArgumentException("resolver == null");
        mResolver = resolver;
    }

    synchronized int start() throws IOException {
        if (mRunning.get()) return mServerSocket.getLocalPort();
        ServerSocket server = new ServerSocket();
        server.setReuseAddress(false);
        // WebView's proxy rule targets 127.0.0.1. Android may return ::1 from
        // getLoopbackAddress(), leaving an IPv6-only listener that the IPv4 rule cannot reach.
        server.bind(new InetSocketAddress(
                InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), 0), 16);
        mServerSocket = server;
        mRunning.set(true);
        mAcceptExecutor.execute(this::acceptLoop);
        return server.getLocalPort();
    }

    private void acceptLoop() {
        while (mRunning.get()) {
            Socket client = null;
            try {
                client = mServerSocket.accept();
                if (!client.getInetAddress().isLoopbackAddress()) {
                    closeQuietly(client);
                    continue;
                }
                track(client);
                Socket accepted = client;
                mConnectionExecutor.execute(() -> handle(accepted));
            } catch (SocketException error) {
                if (mRunning.get()) closeQuietly(client);
                return;
            } catch (Exception error) {
                closeQuietly(client);
            }
        }
    }

    private void handle(Socket client) {
        Socket upstream = null;
        String destination = "unparsed";
        boolean tunnelEstablished = false;
        try {
            client.setSoTimeout(HANDSHAKE_TIMEOUT_MS);
            InputStream input = client.getInputStream();
            OutputStream output = client.getOutputStream();
            Target target = readConnectTarget(input);
            destination = target.hostname == null ? "literal-address" : target.hostname;
            upstream = connect(target);
            track(upstream);
            writeSuccess(output);
            tunnelEstablished = true;
            client.setSoTimeout(0);
            upstream.setSoTimeout(0);
            Socket finalUpstream = upstream;
            mRelayExecutor.execute(() -> relay(finalUpstream, client));
            copy(input, upstream.getOutputStream());
        } catch (Exception error) {
            java.util.logging.Logger.getLogger("LinuxDoLoginProxy").warning("tunnel " + destination
                    + " established=" + tunnelEstablished + " failed="
                    + error.getClass().getSimpleName());
            // Once CONNECT succeeds the stream contains TLS bytes, never another HTTP reply.
            if (!tunnelEstablished) writeFailureQuietly(client, error);
        } finally {
            closeAndUntrack(upstream);
            closeAndUntrack(client);
        }
    }

    private static Target readConnectTarget(InputStream input) throws IOException {
        java.io.ByteArrayOutputStream header = new java.io.ByteArrayOutputStream(512);
        int matched = 0;
        while (header.size() < MAX_CONNECT_HEADER_BYTES) {
            int value = readByte(input);
            header.write(value);
            if ((matched == 0 && value == '\r')
                    || (matched == 1 && value == '\n')
                    || (matched == 2 && value == '\r')
                    || (matched == 3 && value == '\n')) {
                matched++;
                if (matched == 4) break;
            } else {
                matched = value == '\r' ? 1 : 0;
            }
        }
        if (matched != 4) throw new IOException("CONNECT header too large");
        String request = header.toString(StandardCharsets.ISO_8859_1.name());
        int lineEnd = request.indexOf("\r\n");
        if (lineEnd <= 0) throw new IOException("missing CONNECT line");
        String[] parts = request.substring(0, lineEnd).trim().split("\\s+");
        if (parts.length != 3 || !"CONNECT".equalsIgnoreCase(parts[0])) {
            throw new IOException("only CONNECT is supported");
        }
        String authority = parts[1];
        String hostname;
        int port;
        if (authority.startsWith("[")) {
            int bracket = authority.indexOf(']');
            if (bracket <= 1 || bracket + 2 >= authority.length()
                    || authority.charAt(bracket + 1) != ':') {
                throw new IOException("invalid IPv6 CONNECT authority");
            }
            hostname = authority.substring(1, bracket);
            port = parsePort(authority.substring(bracket + 2));
        } else {
            int colon = authority.lastIndexOf(':');
            if (colon <= 0 || colon == authority.length() - 1) {
                throw new IOException("invalid CONNECT authority");
            }
            hostname = authority.substring(0, colon);
            port = parsePort(authority.substring(colon + 1));
        }
        if (hostname.length() > 253 || hostname.indexOf('\0') >= 0) {
            throw new IOException("invalid CONNECT hostname");
        }
        InetAddress literal = null;
        if (hostname.matches("[0-9.]+") || hostname.indexOf(':') >= 0) {
            try {
                literal = InetAddress.getByName(hostname);
            } catch (UnknownHostException ignored) {
                throw new IOException("invalid CONNECT address");
            }
        }
        return new Target(literal == null ? hostname : null, literal, port);
    }

    private static int parsePort(String value) throws IOException {
        try {
            int port = Integer.parseInt(value);
            if (port <= 0 || port > 65535) throw new IOException("invalid CONNECT port");
            return port;
        } catch (NumberFormatException error) {
            throw new IOException("invalid CONNECT port", error);
        }
    }

    private Socket connect(Target target) throws IOException {
        List<InetAddress> addresses = target.literal == null
                ? mResolver.lookup(target.hostname)
                : Collections.singletonList(target.literal);
        IOException last = null;
        int start = addresses.size() <= 1 ? 0
                : Math.floorMod(mAddressCursor.getAndIncrement(), addresses.size());
        for (int offset = 0; offset < addresses.size(); offset++) {
            InetAddress address = addresses.get((start + offset) % addresses.size());
            Socket socket = new Socket();
            try {
                socket.connect(new InetSocketAddress(address, target.port), CONNECT_TIMEOUT_MS);
                return socket;
            } catch (IOException error) {
                last = error;
                closeQuietly(socket);
            }
        }
        if (last != null) throw last;
        throw new UnknownHostException(target.hostname);
    }

    private static void writeSuccess(OutputStream output) throws IOException {
        output.write(("HTTP/1.1 200 Connection Established\r\n"
                + "Proxy-Agent: NGA-LinuxDo-DoH\r\n\r\n")
                .getBytes(StandardCharsets.ISO_8859_1));
        output.flush();
    }

    private static void writeFailureQuietly(Socket client, Exception error) {
        if (client == null || client.isClosed()) return;
        try {
            OutputStream output = client.getOutputStream();
            String kind = error == null ? "unknown" : error.getClass().getSimpleName();
            output.write(("HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\n"
                    + "X-NGA-Proxy-Error: " + kind + "\r\n"
                    + "Content-Length: 0\r\n\r\n")
                    .getBytes(StandardCharsets.ISO_8859_1));
            output.flush();
        } catch (IOException ignored) {
        }
    }

    private static void relay(Socket source, Socket destination) {
        try {
            copy(source.getInputStream(), destination.getOutputStream());
        } catch (IOException ignored) {
        } finally {
            closeQuietly(source);
            closeQuietly(destination);
        }
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[16 * 1024];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
            output.flush();
        }
    }

    private static int readByte(InputStream input) throws IOException {
        int value = input.read();
        if (value < 0) throw new EOFException();
        return value;
    }

    private static byte[] readExact(InputStream input, int length) throws IOException {
        byte[] result = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = input.read(result, offset, length - offset);
            if (read < 0) throw new EOFException();
            offset += read;
        }
        return result;
    }

    private void track(Socket socket) {
        if (socket != null) mSockets.add(socket);
    }

    private void closeAndUntrack(Socket socket) {
        if (socket == null) return;
        mSockets.remove(socket);
        closeQuietly(socket);
    }

    private static void closeQuietly(Socket socket) {
        if (socket == null) return;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    @Override
    public synchronized void close() {
        if (!mRunning.getAndSet(false)) return;
        if (mServerSocket != null) {
            try {
                mServerSocket.close();
            } catch (IOException ignored) {
            }
        }
        synchronized (mSockets) {
            for (Socket socket : mSockets) closeQuietly(socket);
            mSockets.clear();
        }
        mAcceptExecutor.shutdownNow();
        mConnectionExecutor.shutdownNow();
        mRelayExecutor.shutdownNow();
        try {
            mResolver.close();
        } catch (IOException | RuntimeException ignored) {
            // Resolver teardown is best effort. A platform DoH engine can reject shutdown while
            // its last lookup is still in flight; never surface that from a worker thread.
        }
    }

    private static final class Target {
        final String hostname;
        final InetAddress literal;
        final int port;

        Target(String hostname, InetAddress literal, int port) {
            this.hostname = hostname;
            this.literal = literal;
            this.port = port;
        }
    }
}
