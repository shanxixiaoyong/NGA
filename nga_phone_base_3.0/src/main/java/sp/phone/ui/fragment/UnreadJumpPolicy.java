package sp.phone.ui.fragment;

/** Pure floor/page policy for restoring through the ordinary article Pager. */
public final class UnreadJumpPolicy {
    public static final int NO_TARGET = -1;

    private UnreadJumpPolicy() { }

    public static int firstUnreadFloor(int highestReadFloor, int replies) {
        if (highestReadFloor < 0 || replies <= highestReadFloor) return NO_TARGET;
        return highestReadFloor + 1;
    }

    public static int restoreFloor(int highestReadFloor, int replies) {
        if (highestReadFloor < 0 || replies < 0) return NO_TARGET;
        return highestReadFloor < replies
                ? highestReadFloor + 1
                : Math.min(highestReadFloor, replies);
    }

    public static int serverPageForFloor(int floor) {
        return floor < 0 ? NO_TARGET : floor / 20 + 1;
    }

    public static boolean isEligibleRoute(
            int tid, int pid, int authorId, int searchPost, boolean loadCache) {
        return tid > 0 && pid == 0 && authorId == 0 && searchPost == 0 && !loadCache;
    }
}
