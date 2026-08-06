package gov.anzong.androidnga.common.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import gov.anzong.androidnga.base.util.PreferenceUtils;
import gov.anzong.androidnga.common.PreferenceKey;

/**
 * NGA 图床地址的唯一权威来源。
 *
 * <p>历史上图床迁过两次（{@code img*.ngacn.cc} → {@code img*.nga.178.com} → {@code img*.nga.cn}），
 * 每次都因为域名散落在各解码器里而全线崩掉。所有附件地址一律从这里取，不要再各自持有域名字符串。
 *
 * <p><b>图床按路径族分主机</b>——这是本类两条归一化规则的由来：
 * <ul>
 *   <li>{@code /attachments/}（附件、正文图）只有 {@code img.nga.cn} 与 {@code img9.nga.cn} 提供；</li>
 *   <li>{@code /ngabbs/post/smile/}（表情）只有 {@code img4.nga.cn} 提供，{@code img.nga.cn} 对该路径 404。</li>
 * </ul>
 * 所以不能把旧域名一刀切重写成附件主机，否则表情路径会从「域名已死」变成「404」。
 *
 * <p><b>协议与主机绑定</b>：{@code img9.nga.cn} 的 https 稳定返回 NGA 自己的 404 页，只有 http 可用。
 * 不要为了整齐把 {@link #PRESET_BASE_URLS} 里的协议统一成 https。
 */
public final class NgaImageHost {

    /** 附件默认地址；实测唯一 http + https 双通的附件源。 */
    public static final String DEFAULT_BASE_URL = "https://img.nga.cn";

    /**
     * 「图片域名」下拉的 index → 附件 base url，序号与 {@code R.array.image_domain} 一一对应。
     *
     * <p>index 2 是「自定义」，实际值由 {@link PreferenceKey#KEY_IMAGE_DOMAIN_CUSTOM} 提供，故为 null。
     */
    static final String[] PRESET_BASE_URLS = {
            "https://img.nga.cn",   // 0 默认
            "http://img9.nga.cn",   // 1 仅 http 可用，勿改 https
            null,                   // 2 自定义
    };

    /** 「自定义」在选项列表里的序号；选择页据此决定输入框是否可编辑。 */
    public static final int INDEX_CUSTOM = 2;

    /** 遗留图床的域名后缀，附带 {@code img} 前缀锚定，避免误伤主站 nga.178.com / bbs.ngacn.cc。 */
    private static final String LEGACY_HOST = "img(\\d*)\\.(?:nga\\.178\\.com|ngacn\\.cc)";

    /** 规则 1：附件族。用前瞻匹配路径，不消费它。 */
    private static final Pattern LEGACY_ATTACHMENT = Pattern.compile(
            "https?://" + LEGACY_HOST + "(?=/attachments/)");

    /** 规则 2：其余族（表情等）。保留原编号，只换域名后缀。 */
    private static final Pattern LEGACY_OTHER = Pattern.compile(
            "https?://" + LEGACY_HOST);

    private static final Pattern VALID_HOST = Pattern.compile("[A-Za-z0-9.\\-]+(:\\d+)?");

    private static volatile String sCachedBaseUrl;

    private NgaImageHost() {
    }

    /**
     * 生效的附件 base url，如 {@code https://img.nga.cn}（不带尾斜杠）。
     *
     * <p>任何异常都回退到 {@link #DEFAULT_BASE_URL}：本方法会被 {@code lib_core} 的解码器调用，
     * 而那些解码器在纯 JVM 单测里跑得到，彼时没有 Android 环境。
     */
    public static String attachmentBaseUrl() {
        String cached = sCachedBaseUrl;
        if (cached != null) {
            return cached;
        }
        String resolved = DEFAULT_BASE_URL;
        try {
            int index = Integer.parseInt(
                    PreferenceUtils.getData(PreferenceKey.KEY_IMAGE_DOMAIN, "0"));
            if (index >= 0 && index < PRESET_BASE_URLS.length) {
                String preset = PRESET_BASE_URLS[index];
                if (preset != null) {
                    resolved = preset;
                } else {
                    String custom = sanitizeBaseUrlInput(
                            PreferenceUtils.getData(PreferenceKey.KEY_IMAGE_DOMAIN_CUSTOM, ""));
                    if (custom != null) {
                        resolved = custom;
                    }
                }
            }
        } catch (Throwable ignored) {
            // 无 Android context（JVM 单测里 PreferenceUtils 的静态初始化会抛 Error）、
            // 偏好读取失败、index 非数字——一律用默认值，图片可用优先于设置生效。
        }
        sCachedBaseUrl = resolved;
        return resolved;
    }

    /** 附件目录前缀，如 {@code https://img.nga.cn/attachments}（不带尾斜杠）。 */
    public static String attachmentsPrefix() {
        return attachmentBaseUrl() + "/attachments";
    }

    /** 「图片域名」相关设置变更后调用，让下次取值重新解析。 */
    public static void invalidate() {
        sCachedBaseUrl = null;
    }

    /**
     * 把用户填写的自定义域名归一成完整 base url。
     *
     * <p>接受 {@code img.nga.cn}、{@code https://img.nga.cn}、{@code http://img9.nga.cn/}、
     * {@code  img.nga.cn/attachments } 等写法；未写协议时默认 https，写了则尊重用户选择。
     *
     * @return 形如 {@code https://img.nga.cn} 的 base url；输入空白或非法时返回 {@code null}
     */
    public static String sanitizeBaseUrlInput(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }

        String scheme = "https";
        int schemeEnd = value.indexOf("://");
        if (schemeEnd != -1) {
            String declared = value.substring(0, schemeEnd).toLowerCase();
            if (!"http".equals(declared) && !"https".equals(declared)) {
                return null;
            }
            scheme = declared;
            value = value.substring(schemeEnd + "://".length());
        }

        int cut = value.length();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                cut = i;
                break;
            }
        }
        String host = value.substring(0, cut).trim();
        if (host.isEmpty() || !VALID_HOST.matcher(host).matches()) {
            return null;
        }
        return scheme + "://" + host;
    }

    /**
     * 把内容里遗留的图床主机重写为当前可用地址，让写死了旧域名的历史帖子恢复可见。
     *
     * <p>只改协议与主机名，路径、查询串、{@code .thumb.jpg} 等画质后缀原样保留。
     * 附件路径走当前所选图片域名，其余路径保留原编号只换域名后缀（见类注释）。
     */
    public static String normalizeLegacyHosts(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        String result = LEGACY_ATTACHMENT.matcher(content)
                .replaceAll(Matcher.quoteReplacement(attachmentBaseUrl()));
        return LEGACY_OTHER.matcher(result).replaceAll("https://img$1.nga.cn");
    }
}
