package sp.phone.util;


import android.graphics.Bitmap;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;

import gov.anzong.androidnga.common.util.AppEnvironment;
import gov.anzong.androidnga.common.util.NgaRequestPolicy;

public class HttpUtil {

    public static final String NGA_ATTACHMENT_HOST = "img.nga.178.com"; //img.ngacn.cc";
    public static final String Servlet_phone = "/servlet/PhoneServlet";
    public static final String Servlet_timer = "/servlet/TimerServlet";
    private static final String[] servers = {"https://nga.178.com", "https://bbs.ngacn.cc"};
    private static final String TAG = HttpUtil.class.getSimpleName();
    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;
    private static final int READ_TIMEOUT_MILLIS = 10_000;
    private static final String[] host_arr = {};
    public static String PATH = AppEnvironment.getExternalStoragePictureDirectory() + "/nga_cache";
    public static String PATH_AVATAR = PATH + "/nga_cache";

    public static String Server = "https://bbs.nga.cn";
    public static String NonameServer = "https://ngac.sinaapp.com/nganoname";
    public static String HOST = "";
    public static String HOST_PORT = "";
    //软件名/版本 (硬件信息; 操作系统信息)
    //AndroidNga/571 (Xiaomi MI 2S; Android 4.1.1)
    public static String MODEL = android.os.Build.MODEL.toUpperCase(Locale.US);
    public static String MANUFACTURER = android.os.Build.MANUFACTURER.toUpperCase(Locale.US);

    @SuppressWarnings("unused")
    public static void selectServer2() {
        for (String host : host_arr) {
            HttpURLConnection conn = null;
            try {
                URL url = parseTrustedNgaUrl(host);
                if (url == null) {
                    continue;
                }
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
                conn.setReadTimeout(READ_TIMEOUT_MILLIS);
                conn.setInstanceFollowRedirects(false);
                int result = conn.getResponseCode();
                if (result == HttpURLConnection.HTTP_OK) {
                    HOST = host;//
                    break;
                }
            } catch (IOException | RuntimeException ignored) {
            } finally {
                if (conn != null) {
                    conn.disconnect();
                    conn = null;
                }
            }

        }
    }

    public static void switchServer() {
        int i = 0;
        for (; i < servers.length; ++i) {
            if (Server.equals(servers[i]))
                break;
        }
        i = (i + 1) % servers.length;
        Server = servers[i];
    }

    public static void downImage(String uri, String fileName) {
        URL url = parseHttpsUrl(uri);
        if (url == null) {
            return;
        }
        try {
            File file = new File(fileName);

            FileUtils.copyURLToFile(url, file, 2000, 5000);
        } catch (IOException | RuntimeException ignored) {
        }
    }

    public static void downImage3(Bitmap bitmap, String fileName) {
        File f = new File(fileName);
        InputStream is = null;
        if (f.exists()) {
            f.delete();
        }
        try {
            FileOutputStream out = new FileOutputStream(f);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            out.flush();
            out.close();
        } catch (IOException ignored) {
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    @SuppressWarnings("unused")
    private static void writeFile(URL url, String fileName) {
        if (url == null || parseHttpsUrl(url.toString()) == null) {
            return;
        }
        try {
            FileUtils.copyURLToFile(url, new File(fileName), 4000, 3000);
        } catch (IOException | RuntimeException ignored) {
        }
    }

    private static URL parseTrustedNgaUrl(String value) {
        return parseHttpsUrl(value, true);
    }

    private static URL parseHttpsUrl(String value) {
        return parseHttpsUrl(value, false);
    }

    private static URL parseHttpsUrl(String value, boolean requireNgaHost) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            URL url = new URL(value);
            int port = url.getPort();
            if (url.getUserInfo() != null || (port != -1 && port != 443)) {
                return null;
            }
            if (requireNgaHost
                    && !NgaRequestPolicy.isTrustedHttps(url.getProtocol(), url.getHost())) {
                return null;
            }
            if (!requireNgaHost && (url.getProtocol() == null
                    || !"https".equalsIgnoreCase(url.getProtocol())
                    || StringUtils.isEmpty(url.getHost()))) {
                return null;
            }
            return url;
        } catch (MalformedURLException ignored) {
            return null;
        }
    }

}
