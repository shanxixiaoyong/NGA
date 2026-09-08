package sp.phone.linuxdo;

/** Small Android-free lifecycle gate: late callbacks cannot complete a newer/closed flow. */
public final class LinuxDoAuthFlow {
    public enum Mode { LOGIN, VERIFICATION, BROWSER }
    public enum State { IDLE, CONNECTING, WEB, CHECKING, COMPLETE, ERROR, CLOSED }
    private long generation;
    private State state = State.IDLE;
    public long begin() { state = State.CONNECTING; return ++generation; }
    public boolean accepts(long token) { return token == generation && state != State.CLOSED; }
    public boolean move(long token, State next) {
        if (!accepts(token) || state == State.COMPLETE) return false;
        state = next;
        return true;
    }
    public State state() { return state; }
    public void close() { ++generation; state = State.CLOSED; }

    public static boolean isFirstParty(String url) {
        try {
            java.net.URI uri = new java.net.URI(url);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "linux.do".equalsIgnoreCase(uri.getHost())
                    && uri.getUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443);
        } catch (Exception ignored) { return false; }
    }
}
