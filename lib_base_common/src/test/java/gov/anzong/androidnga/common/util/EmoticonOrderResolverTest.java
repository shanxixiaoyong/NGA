package gov.anzong.androidnga.common.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * {@link EmoticonOrderResolver} 的纯 JVM 单元测试。
 *
 * <p>本类刻意不引用 {@code EmoticonOrderStore} / {@code PreferenceUtils}，
 * 后者的静态初始化需要 Application Context，会让 host JVM 测试失败。
 */
public class EmoticonOrderResolverTest {

    private static final String[] DEFAULTS = {"ac0.png", "ac1.png", "ac2.png", "ac3.png"};

    // --- resolve: 空数据 ---

    @Test
    public void resolve_returnsIdentity_whenSavedIsNull() {
        assertArrayEquals(new int[]{0, 1, 2, 3}, EmoticonOrderResolver.resolve(DEFAULTS, null));
    }

    @Test
    public void resolve_returnsIdentity_whenSavedIsEmpty() {
        assertArrayEquals(new int[]{0, 1, 2, 3},
                EmoticonOrderResolver.resolve(DEFAULTS, Collections.<String>emptyList()));
    }

    @Test
    public void resolve_returnsEmpty_whenDefaultsIsNull() {
        assertArrayEquals(new int[0], EmoticonOrderResolver.resolve(null, Arrays.asList("ac0.png")));
    }

    @Test
    public void resolve_returnsEmpty_whenDefaultsIsEmpty() {
        assertArrayEquals(new int[0],
                EmoticonOrderResolver.resolve(new String[0], Arrays.asList("ac0.png")));
    }

    // --- resolve: 正常排列 ---

    @Test
    public void resolve_honoursFullSavedPermutation() {
        List<String> saved = Arrays.asList("ac2.png", "ac0.png", "ac3.png", "ac1.png");
        assertArrayEquals(new int[]{2, 0, 3, 1}, EmoticonOrderResolver.resolve(DEFAULTS, saved));
    }

    // --- resolve: 表情被官方移除 ---

    @Test
    public void resolve_ignoresUnknownFileName() {
        List<String> saved = Arrays.asList("ac2.png", "removed.png", "ac0.png");
        // removed.png 被忽略；ac1/ac3 属于「未出现在 saved 中」，按内置顺序追加到末尾。
        assertArrayEquals(new int[]{2, 0, 1, 3}, EmoticonOrderResolver.resolve(DEFAULTS, saved));
    }

    // --- resolve: 官方新增表情 ---

    @Test
    public void resolve_appendsNewEmoticonsAtEndKeepingDefaultOrder() {
        // 旧版本只保存了 4 项中的 2 项，其余视为新增。
        List<String> saved = Arrays.asList("ac3.png", "ac1.png");
        assertArrayEquals(new int[]{3, 1, 0, 2}, EmoticonOrderResolver.resolve(DEFAULTS, saved));
    }

    @Test
    public void resolve_appendsMultipleNewEmoticonsInDefaultRelativeOrder() {
        String[] defaults = {"a.png", "b.png", "c.png", "d.png", "e.png"};
        List<String> saved = Arrays.asList("d.png", "b.png");
        // 新增的 a/c/e 追加到末尾，且彼此保持 a -> c -> e 的内置相对顺序。
        assertArrayEquals(new int[]{3, 1, 0, 2, 4},
                EmoticonOrderResolver.resolve(defaults, saved));
    }

    // --- resolve: 损坏数据 ---

    @Test
    public void resolve_dropsDuplicateEntries() {
        List<String> saved = Arrays.asList("ac2.png", "ac2.png", "ac0.png", "ac2.png");
        assertArrayEquals(new int[]{2, 0, 1, 3}, EmoticonOrderResolver.resolve(DEFAULTS, saved));
    }

    @Test
    public void resolve_toleratesNullElements() {
        List<String> saved = new ArrayList<>();
        saved.add("ac1.png");
        saved.add(null);
        saved.add("ac0.png");
        assertArrayEquals(new int[]{1, 0, 2, 3}, EmoticonOrderResolver.resolve(DEFAULTS, saved));
    }

    @Test
    public void resolve_returnsIdentity_whenNothingMatches() {
        List<String> saved = Arrays.asList("x.png", "y.png");
        assertArrayEquals(new int[]{0, 1, 2, 3}, EmoticonOrderResolver.resolve(DEFAULTS, saved));
    }

    // --- resolve: 全局不变量 ---

    @Test
    public void resolve_alwaysReturnsCompletePermutation() {
        List<List<String>> inputs = Arrays.asList(
                null,
                Collections.<String>emptyList(),
                Arrays.asList("ac3.png"),
                Arrays.asList("ac3.png", "ac3.png"),
                Arrays.asList("nope.png"),
                Arrays.asList("ac1.png", "nope.png", "ac0.png"),
                Arrays.asList("ac3.png", "ac2.png", "ac1.png", "ac0.png"));
        for (List<String> saved : inputs) {
            int[] order = EmoticonOrderResolver.resolve(DEFAULTS, saved);
            assertEquals("length must match defaults for input " + saved,
                    DEFAULTS.length, order.length);
            Set<Integer> seen = new HashSet<>();
            for (int index : order) {
                assertTrue("index out of range for input " + saved,
                        index >= 0 && index < DEFAULTS.length);
                assertTrue("duplicate index for input " + saved, seen.add(index));
            }
            assertEquals("must cover every default for input " + saved,
                    DEFAULTS.length, seen.size());
        }
    }

    // --- toFileNames ---

    @Test
    public void toFileNames_roundTripsWithResolve() {
        List<String> saved = Arrays.asList("ac2.png", "ac0.png", "ac3.png", "ac1.png");
        int[] order = EmoticonOrderResolver.resolve(DEFAULTS, saved);
        assertEquals(saved, EmoticonOrderResolver.toFileNames(DEFAULTS, order));
    }

    @Test
    public void toFileNames_skipsOutOfRangeIndexes() {
        int[] order = {2, 99, -1, 0};
        assertEquals(Arrays.asList("ac2.png", "ac0.png"),
                EmoticonOrderResolver.toFileNames(DEFAULTS, order));
    }

    @Test
    public void toFileNames_returnsEmptyOnNullInput() {
        assertTrue(EmoticonOrderResolver.toFileNames(null, new int[]{0}).isEmpty());
        assertTrue(EmoticonOrderResolver.toFileNames(DEFAULTS, null).isEmpty());
    }

    // --- move ---

    @Test
    public void move_forwards() {
        assertArrayEquals(new int[]{1, 2, 0, 3},
                EmoticonOrderResolver.move(new int[]{0, 1, 2, 3}, 0, 2));
    }

    @Test
    public void move_backwards() {
        assertArrayEquals(new int[]{0, 3, 1, 2},
                EmoticonOrderResolver.move(new int[]{0, 1, 2, 3}, 3, 1));
    }

    @Test
    public void move_toSamePositionIsNoOp() {
        assertArrayEquals(new int[]{0, 1, 2, 3},
                EmoticonOrderResolver.move(new int[]{0, 1, 2, 3}, 2, 2));
    }

    @Test
    public void move_toFirstAndLast() {
        assertArrayEquals(new int[]{3, 0, 1, 2},
                EmoticonOrderResolver.move(new int[]{0, 1, 2, 3}, 3, 0));
        assertArrayEquals(new int[]{1, 2, 3, 0},
                EmoticonOrderResolver.move(new int[]{0, 1, 2, 3}, 0, 3));
    }

    @Test
    public void move_doesNotMutateInput() {
        int[] input = {0, 1, 2, 3};
        EmoticonOrderResolver.move(input, 0, 3);
        assertArrayEquals(new int[]{0, 1, 2, 3}, input);
    }

    @Test
    public void move_ignoresOutOfRangeArguments() {
        int[] input = {0, 1, 2, 3};
        assertArrayEquals(input, EmoticonOrderResolver.move(input, -1, 2));
        assertArrayEquals(input, EmoticonOrderResolver.move(input, 1, 99));
        assertArrayEquals(new int[0], EmoticonOrderResolver.move(null, 0, 1));
    }

    @Test
    public void move_preservesPermutationProperty() {
        int[] order = {0, 1, 2, 3};
        int[][] steps = {{0, 3}, {2, 0}, {1, 3}, {3, 1}, {0, 1}};
        for (int[] step : steps) {
            order = EmoticonOrderResolver.move(order, step[0], step[1]);
            Set<Integer> seen = new HashSet<>();
            for (int index : order) {
                assertTrue(seen.add(index));
            }
            assertEquals(4, order.length);
            assertEquals(4, seen.size());
        }
    }

    // --- isDefaultOrder ---

    @Test
    public void isDefaultOrder() {
        assertTrue(EmoticonOrderResolver.isDefaultOrder(null));
        assertTrue(EmoticonOrderResolver.isDefaultOrder(new int[0]));
        assertTrue(EmoticonOrderResolver.isDefaultOrder(new int[]{0, 1, 2, 3}));
        assertFalse(EmoticonOrderResolver.isDefaultOrder(new int[]{1, 0, 2, 3}));
        assertFalse(EmoticonOrderResolver.isDefaultOrder(new int[]{0, 1, 3, 2}));
    }
}
