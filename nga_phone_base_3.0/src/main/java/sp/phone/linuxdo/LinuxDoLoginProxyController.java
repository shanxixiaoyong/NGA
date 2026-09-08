package sp.phone.linuxdo;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebViewFeature;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Reference-counted lease over WebView's PROCESS-wide override. Never clear another owner's tunnel. */
public final class LinuxDoLoginProxyController {
    public interface Listener {
        void onReady();
        void onUnsupported();
        void onFailure();
    }
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService SERIAL = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "linuxdo-web-connection"); thread.setDaemon(true); return thread;
    });
    // All service state below belongs to SERIAL, including override completion callbacks.
    private static final Set<LinuxDoLoginProxyController> OWNERS = new HashSet<>();
    private static final List<LinuxDoLoginProxyController> WAITING = new ArrayList<>();
    private static LinuxDoLoginSocksProxy tunnel;
    private static ProxyController controller;
    private static boolean changing;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean started = new AtomicBoolean();
    private Context context;
    private Listener listener;

    public void start(Context context, String dohUrl, Listener listener) {
        if (!started.compareAndSet(false, true) || closed.get()) return;
        this.context = context.getApplicationContext();
        this.listener = listener;
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            MAIN.post(() -> { if (!closed.get()) listener.onUnsupported(); });
            return;
        }
        ProxyController supported = supportedController();
        SERIAL.execute(() -> {
            if (closed.get()) return;
            controller = supported;
            WAITING.add(this);
            drain();
        });
    }

    @SuppressLint("RequiresFeature")
    private static ProxyController supportedController() { return ProxyController.getInstance(); }

    private static void drain() {
        if (changing) return;
        WAITING.removeIf(owner -> owner.closed.get());
        if (tunnel != null) {
            for (LinuxDoLoginProxyController owner : WAITING) {
                OWNERS.add(owner);
                owner.ready();
            }
            WAITING.clear();
            stopIfIdle();
            return;
        }
        if (WAITING.isEmpty()) return;
        changing = true;
        LinuxDoLoginSocksProxy created = null;
        try {
            created = new LinuxDoLoginSocksProxy(new LinuxDoLoginDohResolver(
                    WAITING.get(0).context, LinuxDoDohConfig.currentUrl()));
            int port = created.start();
            LinuxDoLoginSocksProxy owned = created;
            ProxyConfig config = new ProxyConfig.Builder()
                    .addProxyRule("http://127.0.0.1:" + port)
                    // Native NGA requests never use this override; bypass its WebView hosts too.
                    .addBypassRule("*.nga.cn").addBypassRule("nga.cn")
                    .addBypassRule("*.ngacn.cc").addBypassRule("ngacn.cc")
                    .addBypassRule("*.178.com").addBypassRule("178.com")
                    .build();
            controller.setProxyOverride(config, SERIAL, () -> {
                tunnel = owned;
                changing = false;
                drain();
            });
        } catch (RuntimeException | java.io.IOException error) {
            if (created != null) created.close();
            changing = false;
            for (LinuxDoLoginProxyController owner : WAITING) {
                MAIN.post(() -> { if (!owner.closed.get()) owner.listener.onFailure(); });
            }
            WAITING.clear();
        }
    }

    private void ready() {
        MAIN.post(() -> { if (!closed.get()) listener.onReady(); });
    }

    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        SERIAL.execute(() -> { WAITING.remove(this); OWNERS.remove(this); stopIfIdle(); });
    }

    private static void stopIfIdle() {
        if (changing || tunnel == null || !OWNERS.isEmpty() || !WAITING.isEmpty()) return;
        changing = true;
        LinuxDoLoginSocksProxy old = tunnel;
        // Keep the listening socket alive until Chromium acknowledges that it no longer uses it.
        try {
            controller.clearProxyOverride(SERIAL, () -> {
                old.close();
                tunnel = null;
                changing = false;
                drain();
            });
        } catch (RuntimeException error) {
            // If Chromium refuses to clear, retain a working listener instead of leaving its
            // process-wide override pointing at a closed port. The next lease release retries.
            changing = false;
        }
    }
}
