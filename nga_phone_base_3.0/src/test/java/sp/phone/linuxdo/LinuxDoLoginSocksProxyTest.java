package sp.phone.linuxdo;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class LinuxDoLoginSocksProxyTest {

    @Test
    public void domainConnectUsesInjectedResolverAndRelaysOpaqueBytes() throws Exception {
        AtomicReference<String> resolvedHost = new AtomicReference<>();
        LinuxDoLoginSocksProxy.Resolver resolver = new LinuxDoLoginSocksProxy.Resolver() {
            @Override
            public List<InetAddress> lookup(String hostname) {
                resolvedHost.set(hostname);
                return Collections.singletonList(InetAddress.getLoopbackAddress());
            }

            @Override
            public void close() {
            }
        };
        byte[] payload = new byte[]{1, 3, 3, 7, 9};
        CountDownLatch echoed = new CountDownLatch(1);
        try (ServerSocket origin = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             LinuxDoLoginSocksProxy proxy = new LinuxDoLoginSocksProxy(resolver)) {
            Thread originThread = new Thread(() -> {
                try (Socket socket = origin.accept()) {
                    byte[] request = readExact(socket.getInputStream(), payload.length);
                    socket.getOutputStream().write(request);
                    socket.getOutputStream().flush();
                    echoed.countDown();
                } catch (Exception ignored) {
                }
            });
            originThread.setDaemon(true);
            originThread.start();

            try (Socket browser = new Socket(InetAddress.getLoopbackAddress(), proxy.start())) {
                browser.setSoTimeout(5_000);
                OutputStream output = browser.getOutputStream();
                InputStream input = browser.getInputStream();
                String connect = "CONNECT linux.do:" + origin.getLocalPort()
                        + " HTTP/1.1\r\nHost: linux.do:" + origin.getLocalPort()
                        + "\r\nProxy-Connection: keep-alive\r\n\r\n";
                output.write(connect.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
                output.flush();
                String reply = readHeader(input);
                assertTrue(reply.startsWith("HTTP/1.1 200 Connection Established\r\n"));

                output.write(payload);
                output.flush();
                assertArrayEquals(payload, readExact(input, payload.length));
                assertEquals("linux.do", resolvedHost.get());
                assertEquals(true, echoed.await(2, TimeUnit.SECONDS));
            }
        }
    }

    private static byte[] readExact(InputStream input, int size) throws Exception {
        byte[] result = new byte[size];
        int offset = 0;
        while (offset < size) {
            int read = input.read(result, offset, size - offset);
            if (read < 0) throw new AssertionError("unexpected EOF");
            offset += read;
        }
        return result;
    }

    private static String readHeader(InputStream input) throws Exception {
        java.io.ByteArrayOutputStream result = new java.io.ByteArrayOutputStream();
        int matched = 0;
        while (result.size() < 16 * 1024 && matched < 4) {
            int value = input.read();
            if (value < 0) throw new AssertionError("unexpected EOF");
            result.write(value);
            if ((matched == 0 && value == '\r')
                    || (matched == 1 && value == '\n')
                    || (matched == 2 && value == '\r')
                    || (matched == 3 && value == '\n')) {
                matched++;
            } else {
                matched = value == '\r' ? 1 : 0;
            }
        }
        return result.toString(java.nio.charset.StandardCharsets.ISO_8859_1.name());
    }
}
