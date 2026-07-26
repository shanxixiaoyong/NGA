package gov.anzong.androidnga.base.util;

import com.google.common.base.Strings;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;

import gov.anzong.androidnga.common.R;
import okhttp3.RequestBody;
import okio.Buffer;

/**
 * @author Justwen
 */
public class StringUtils {

    private static Map<String, Pattern> sPatternMap = new HashMap<>();

    /**
     * Resource access is intentionally lazy.  Several parser/unit-test paths
     * run before an Application context exists; eager initialization used to
     * crash those callers with an ExceptionInInitializerError.
     */
    private static String[] getSayingValues() {
        try {
            return ContextUtils.getResources().getStringArray(R.array.saying);
        } catch (RuntimeException ignored) {
            return new String[0];
        }
    }

    public static String replaceAll(String content, String regex, String replacement) {
        return getPattern(regex).matcher(content).replaceAll(replacement);
    }

    public static Pattern getPattern(String regex) {
        Pattern pattern = sPatternMap.get(regex);
        if (pattern == null) {
            pattern = Pattern.compile(regex);
            sPatternMap.put(regex, pattern);
        }
        return pattern;
    }
    public static String requestBody2String(final RequestBody request) {
        try {
            final Buffer buffer = new Buffer();
            if (request != null) {
                request.writeTo(buffer);
                return buffer.readUtf8();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }
    public static boolean isEmpty(String content) {
        return Strings.isNullOrEmpty(content);
    }


    public static String timeStamp2Date1(String timeStamp) {
        return timeStamp2Date(timeStamp, "yyyy-MM-dd HH:mm:ss");
    }

    public static String timeStamp2Date2(String timeStamp) {
        return timeStamp2Date(timeStamp, "MM-dd HH:mm");
    }

    public static String timeStamp2Date(String timeStamp, String format) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(Long.parseLong(timeStamp) * 1000);
        return new SimpleDateFormat(format, Locale.getDefault()).format(calendar.getTime());
    }

    public static String getSaying() {
        String[] sayingValues = getSayingValues();
        if (sayingValues.length == 0) {
            return "";
        }
        Random random = new Random();
        int num = random.nextInt(sayingValues.length);
        String str =  sayingValues[num];
        if (str.contains(";")) {
            str = str.replace(";", "-----");
        }
        return str;
    }

}
