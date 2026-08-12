package sp.phone.ui.fragment;

/** Pure viewport policy for recording real floors without requiring a tall row to fit entirely. */
public final class ArticleReadProgressPolicy {
    private ArticleReadProgressPolicy() { }

    public static int highestExposedPosition(
            int firstVisiblePosition,
            int[] visiblePositions,
            int[] decoratedBottoms,
            int viewportBottom) {
        if (firstVisiblePosition < 0) return -1;
        int highest = firstVisiblePosition - 1;
        int count = Math.min(visiblePositions.length, decoratedBottoms.length);
        for (int i = 0; i < count; i++) {
            if (visiblePositions[i] >= 0 && decoratedBottoms[i] <= viewportBottom) {
                highest = Math.max(highest, visiblePositions[i]);
            }
        }
        return highest;
    }
}
