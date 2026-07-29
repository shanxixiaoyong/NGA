package gov.anzong.androidnga.common.util;

import android.content.SharedPreferences;

import java.util.List;

import gov.anzong.androidnga.base.util.PreferenceUtils;

/**
 * 表情自定义顺序的持久化层。
 *
 * <p>每个分类独立保存，key 为 {@code key_emoticon_order_<分类 id>}，value 为图片文件名的
 * JSON 数组。未自定义过的分类不写入任何数据。
 *
 * <p>按文件名而非数组下标保存：下标会随版本增删表情整体错位，文件名则与 assets 一一对应。
 * 合并与容错规则见 {@link EmoticonOrderResolver#resolve}。
 *
 * <p>本类依赖 {@link PreferenceUtils}（其静态初始化需要 Application Context），
 * 因此不适合 host JVM 单元测试；纯逻辑请测 {@link EmoticonOrderResolver}。
 */
public final class EmoticonOrderStore {

    private static final String PREF_KEY_PREFIX = "key_emoticon_order_";

    private EmoticonOrderStore() {
    }

    static String prefKey(String categoryId) {
        return PREF_KEY_PREFIX + categoryId;
    }

    /**
     * 读取某分类的展示顺序。
     *
     * @return 对 {@code EMOTICON_URL[categoryIndex]} 的下标排列；
     *         无自定义数据或数据损坏时返回内置顺序；分类下标越界时返回空数组
     */
    public static int[] loadOrder(int categoryIndex) {
        String[] defaultFileNames = EmoticonUtils.getFileNames(categoryIndex);
        if (defaultFileNames.length == 0) {
            return new int[0];
        }
        List<String> savedFileNames = null;
        try {
            savedFileNames = PreferenceUtils.getData(prefKey(categoryId(categoryIndex)), String.class);
        } catch (Exception e) {
            // 损坏的偏好数据不能阻断表情面板，回退到内置顺序即可。
            LogUtils.e("EmoticonOrderStore", "Unable to read emoticon order: " + e.getMessage());
        }
        return EmoticonOrderResolver.resolve(defaultFileNames, savedFileNames);
    }

    /**
     * 保存某分类的展示顺序。顺序等于内置顺序时删除该项，保持「未自定义不落盘」。
     */
    public static void saveOrder(int categoryIndex, int[] order) {
        String[] defaultFileNames = EmoticonUtils.getFileNames(categoryIndex);
        if (defaultFileNames.length == 0 || order == null) {
            return;
        }
        String key = prefKey(categoryId(categoryIndex));
        if (EmoticonOrderResolver.isDefaultOrder(order)) {
            PreferenceUtils.edit().remove(key).apply();
            return;
        }
        PreferenceUtils.putData(key, EmoticonOrderResolver.toFileNames(defaultFileNames, order));
    }

    /**
     * 清除所有分类的自定义顺序，恢复内置顺序。
     */
    public static void resetAll() {
        SharedPreferences.Editor editor = PreferenceUtils.edit();
        for (String[] label : EmoticonUtils.EMOTICON_LABEL) {
            editor.remove(prefKey(label[0]));
        }
        editor.apply();
    }

    private static String categoryId(int categoryIndex) {
        return EmoticonUtils.EMOTICON_LABEL[categoryIndex][0];
    }
}
