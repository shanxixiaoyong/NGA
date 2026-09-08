package sp.phone.linuxdo;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebViewFeature;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Temporary process-scoped proxy for the official LINUX DO login/challenge WebView.
 *
 * <p>The proxy is provided by sing-box's mixed inbound on 127.0.0.1:7891. This
 * class only installs/removes WebView's proxy override; it never opens a Java
 * CONNECT tunnel and never changes the native OkHttp/Cronet transport.</p>
 */
public final class LinuxDoLoginProxyController {
    public interface Listener {
        void onReady();
        void onUnsupported();
        void onFailure(String message);
    }

    private static final String TAG = "LinuxDoProxy";
    private static final String PROXY_HOST = "127.0.0.1";
    private static final int PROXY_PORT = 7891;
    private static final String PROXY_URL = "http://127.0.0.1:7891";
    private static final int PROBE_TIMEOUT_MS = 800;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService SERIAL = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "linuxdo-web-proxy");
        thread.setDaemon(true);
        return thread;
    });

    // ProxyController is process-wide. Keep a reference-counted lease so one
    // Activity cannot clear another login/verification page's proxy.
    private static final Set<LinuxDoLoginProxyController> OWNERS = new HashSet<>();
    private static final List<LinuxDoLoginProxyController> WAITING = new ArrayList<>();
    private static ProxyController controller;
    private static boolean installed;
    private static boolean changing;

    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private Listener listener;

    public LinuxDoLoginProxyController() {
    }

    /** Installs the sing-box proxy lease, notifying only after WebView accepts it. */
    public void start(Listener listener) {
        if (listener == null || !started.compareAndSet(false, true) || closed.get()) return;
        this.listener = listener;
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            MAIN.post(() -> {
                if (!closed.get()) listener.onUnsupported();
            });
            return;
        }
        SERIAL.execute(() -> {
            if (closed.get()) return;
            WAITING.add(this);
            drain();
        });
    }

    @SuppressLint("RequiresFeature")
    private static ProxyController getController() {
        return ProxyController.getInstance();
    }

    /** Must run on SERIAL. */
    private static void drain() {
        if (changing) return;
        WAITING.removeIf(owner -> owner.closed.get());

        if (installed) {
            promoteWaiting();
            stopIfIdle();
            return;
        }
        if (WAITING.isEmpty()) return;

        changing = true;
        if (!localProxyAvailable()) {
            changing = false;
            List<LinuxDoLoginProxyController> failed = new ArrayList<>(WAITING);
            WAITING.clear();
            for (LinuxDoLoginProxyController owner : failed) {
                owner.fail("未检测到 sing-box 登录代理。请确认 sing-box 已启动，并监听 127.0.0.1:"
                        + PROXY_PORT);
            }
            return;
        }

        try {
            controller = getController();
            ProxyConfig config = new ProxyConfig.Builder()
                    .addProxyRule(PROXY_URL)
                    // NGA WebViews, if any, remain direct; linux.do is not bypassed.
                    .addBypassRule("*.nga.cn").addBypassRule("nga.cn")
                    .addBypassRule("*.ngacn.cc").addBypassRule("ngacn.cc")
                    .addBypassRule("*.178.com").addBypassRule("178.com")
                    .build();
            Log.i(TAG, "install WebView proxy http://" + PROXY_HOST + ":" + PROXY_PORT);
            controller.setProxyOverride(config, SERIAL, () -> {
                installed = true;
                changing = false;
                Log.i(TAG, "WebView proxy installed");
                promoteWaiting();
                stopIfIdle();
                drain();
            });
        } catch (RuntimeException error) {
            changing = false;
            Log.e(TAG, "setProxyOverride failed", error);
            List<LinuxDoLoginProxyController> failed = new ArrayList<>(WAITING);
            WAITING.clear();
            for (LinuxDoLoginProxyController owner : failed) {
                owner.fail("无法启用登录代理：" + error.getClass().getSimpleName());
            }
        }
    }

    private static void promoteWaiting() {
        WAITING.removeIf(owner -> owner.closed.get());
        for (LinuxDoLoginProxyController owner : WAITING) {
            OWNERS.add(owner);
            owner.ready();
        }
        WAITING.clear();
    }

    private void ready() {
        MAIN.post(() -> {
            if (!closed.get() && listener != null) listener.onReady();
        });
    }

    private void fail(String message) {
        MAIN.post(() -> {
            if (!closed.get() && listener != null) listener.onFailure(message);
        });
    }

    /** Releases this Activity's lease; the last lease clears the process proxy. */
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        SERIAL.execute(() -> {
            WAITING.remove(this);
            OWNERS.remove(this);
            stopIfIdle();
            drain();
        });
    }

    /** Must run on SERIAL. */
    private static void stopIfIdle() {
        if (changing || !installed || !OWNERS.isEmpty() || !WAITING.isEmpty()
                || controller == null) return;
        changing = true;
        Log.i(TAG, "clear WebView proxy");
        try {
            controller.clearProxyOverride(SERIAL, () -> {
                installed = false;
                changing = false;
                Log.i(TAG, "WebView proxy cleared");
                drain();
            });
        } catch (RuntimeException error) {
            changing = false;
            Log.e(TAG, "clearProxyOverride failed", error);
        }
    }

    /** Only probes the loopback listener; no linux.do traffic is generated here. */
    private static boolean localProxyAvailable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(PROXY_HOST, PROXY_PORT), PROBE_TIMEOUT_MS);
            return true;
        } catch (IOException error) {
            Log.w(TAG, "sing-box local proxy unavailable", error);
            return false;
        }
    }
}
