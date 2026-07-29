package gov.anzong.androidnga.common.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 表情自定义顺序的纯逻辑层。
 *
 * <p>自定义顺序以「对内置表情列表的下标排列」表示：{@code order[i] == j} 表示面板第 i 格
 * 显示内置列表中的第 j 项。{@link EmoticonUtils#EMOTICON_URL} 始终保持只读，用户偏好
 * 不写回该静态常量。
 *
 * <p>本类不依赖任何 Android 类，可在 host JVM 上直接单元测试。请勿在此引用
 * {@code PreferenceUtils}——它的静态初始化需要 Application Context。
 */
public final class EmoticonOrderResolver {

    private EmoticonOrderResolver() {
    }

    /**
     * 把持久化的文件名顺序解析为对 {@code defaultFileNames} 的下标排列。
     *
     * <p>合并规则（应对应用升级导致的表情增删）：
     * <ul>
     *     <li>已保存但内置列表中不存在的表情（官方已移除）：忽略；</li>
     *     <li>内置列表中存在但未保存的表情（官方新增）：追加到末尾，彼此保持内置相对顺序；</li>
     *     <li>保存的顺序中出现重复项：只保留首次出现的位置。</li>
     * </ul>
     *
     * @return 恒为 {@code 0..n-1} 的完整排列，长度等于 {@code defaultFileNames.length}
     */
    public static int[] resolve(String[] defaultFileNames, List<String> savedFileNames) {
        if (defaultFileNames == null) {
            return new int[0];
        }
        int size = defaultFileNames.length;
        int[] result = new int[size];
        boolean[] used = new boolean[size];
        int cursor = 0;

        if (savedFileNames != null && !savedFileNames.isEmpty()) {
            Map<String, Integer> indexOfFileName = new HashMap<>(size);
            for (int i = 0; i < size; i++) {
                // 内置列表内文件名唯一，见 EmoticonUtilsContractTest。
                indexOfFileName.put(defaultFileNames[i], i);
            }
            for (String fileName : savedFileNames) {
                Integer index = indexOfFileName.get(fileName);
                if (index == null || used[index]) {
                    // 已被官方移除，或保存数据里的重复项。
                    continue;
                }
                used[index] = true;
                result[cursor++] = index;
            }
        }

        // 官方新增的表情追加到末尾，并保持内置列表中的相对顺序。
        for (int i = 0; i < size && cursor < size; i++) {
            if (!used[i]) {
                result[cursor++] = i;
            }
        }
        return result;
    }

    /**
     * 把下标排列还原为文件名列表，用于持久化。
     *
     * <p>越界下标会被跳过，保证本方法不会因损坏数据抛异常。
     */
    public static List<String> toFileNames(String[] defaultFileNames, int[] order) {
        List<String> fileNames = new ArrayList<>();
        if (defaultFileNames == null || order == null) {
            return fileNames;
        }
        for (int index : order) {
            if (index >= 0 && index < defaultFileNames.length) {
                fileNames.add(defaultFileNames[index]);
            }
        }
        return fileNames;
    }

    /**
     * 拖拽移动：把 {@code from} 处的元素取出后插入到 {@code to} 处。
     *
     * <p>语义与 {@code ItemTouchHelper.Callback#onMove} 一致，是「移除再插入」而非「交换」，
     * 只有这样连续拖过多个位置时中间元素的让位动画才正确。
     *
     * @return 新数组；入参非法时返回原数组的副本
     */
    public static int[] move(int[] order, int from, int to) {
        if (order == null) {
            return new int[0];
        }
        int[] result = order.clone();
        if (from == to || from < 0 || to < 0 || from >= result.length || to >= result.length) {
            return result;
        }
        int moved = result[from];
        if (from < to) {
            System.arraycopy(result, from + 1, result, from, to - from);
        } else {
            System.arraycopy(result, to, result, to + 1, from - to);
        }
        result[to] = moved;
        return result;
    }

    /**
     * 判断下标排列是否等于内置顺序。
     *
     * <p>用于「未自定义就不落盘」：等于内置顺序时删除偏好项而不是写入一份冗余数据。
     */
    public static boolean isDefaultOrder(int[] order) {
        if (order == null) {
            return true;
        }
        for (int i = 0; i < order.length; i++) {
            if (order[i] != i) {
                return false;
            }
        }
        return true;
    }
}
