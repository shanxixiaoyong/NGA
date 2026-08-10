package sp.phone.mvp.presenter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ArticlePageRequestStateTest {

    @Test
    public void prefetchIsDeduplicatedAndReadySkipsAutomaticForegroundLoad() {
        ArticlePageRequestState state = new ArticlePageRequestState();

        assertTrue(state.beginPrefetch());
        assertFalse(state.beginPrefetch());
        assertFalse(state.completePrefetch());
        assertEquals(ArticlePageRequestState.State.READY, state.getState());
        assertFalse(state.beginPrefetch());
        assertEquals(
                ArticlePageRequestState.ForegroundLoadDecision.NONE,
                state.requestForegroundLoad(false));
    }

    @Test
    public void enteringDuringPrefetchWaitsAndFailureStartsNormalForegroundLoad() {
        ArticlePageRequestState state = new ArticlePageRequestState();

        assertTrue(state.beginPrefetch());
        assertEquals(
                ArticlePageRequestState.ForegroundLoadDecision.WAIT_FOR_PREFETCH,
                state.requestForegroundLoad(false));
        assertTrue(state.isPrefetchPromotedToForeground());
        assertTrue(state.failPrefetch());
        assertEquals(ArticlePageRequestState.State.IDLE, state.getState());
        assertEquals(
                ArticlePageRequestState.ForegroundLoadDecision.START,
                state.requestForegroundLoad(false));
        assertEquals(ArticlePageRequestState.State.FOREGROUND_LOADING, state.getState());
    }

    @Test
    public void enteringDuringPrefetchReusesASuccessfulRequest() {
        ArticlePageRequestState state = new ArticlePageRequestState();

        assertTrue(state.beginPrefetch());
        assertEquals(
                ArticlePageRequestState.ForegroundLoadDecision.WAIT_FOR_PREFETCH,
                state.requestForegroundLoad(false));
        assertTrue(state.completePrefetch());
        assertEquals(ArticlePageRequestState.State.READY, state.getState());
        assertEquals(
                ArticlePageRequestState.ForegroundLoadDecision.NONE,
                state.requestForegroundLoad(false));
    }

    @Test
    public void leavingBeforePrefetchFailureKeepsTheFailureInTheBackground() {
        ArticlePageRequestState state = new ArticlePageRequestState();

        assertTrue(state.beginPrefetch());
        assertEquals(
                ArticlePageRequestState.ForegroundLoadDecision.WAIT_FOR_PREFETCH,
                state.requestForegroundLoad(false));
        assertTrue(state.movePrefetchToBackground());

        assertFalse(state.isPrefetchPromotedToForeground());
        assertFalse(state.failPrefetch());
        assertEquals(ArticlePageRequestState.State.IDLE, state.getState());
    }

    @Test
    public void explicitRefreshCoalescesWithAnInFlightPrefetchAndFallsBackNormally() {
        ArticlePageRequestState state = new ArticlePageRequestState();

        assertTrue(state.beginPrefetch());
        assertEquals(
                ArticlePageRequestState.ForegroundLoadDecision.WAIT_FOR_PREFETCH,
                state.requestForegroundLoad(true));
        assertTrue(state.failPrefetch());
        assertEquals(
                ArticlePageRequestState.ForegroundLoadDecision.START,
                state.requestForegroundLoad(false));
    }

    @Test
    public void backgroundPrefetchFailureReturnsToIdleForLaterForegroundLoad() {
        ArticlePageRequestState state = new ArticlePageRequestState();

        assertTrue(state.beginPrefetch());
        assertFalse(state.failPrefetch());
        assertEquals(ArticlePageRequestState.State.IDLE, state.getState());
        assertEquals(
                ArticlePageRequestState.ForegroundLoadDecision.START,
                state.requestForegroundLoad(false));
    }

    @Test
    public void explicitRefreshStillStartsFromReadyAndFailureKeepsExistingDataReady() {
        ArticlePageRequestState state = new ArticlePageRequestState();

        assertEquals(
                ArticlePageRequestState.ForegroundLoadDecision.START,
                state.requestForegroundLoad(false));
        state.completeForegroundLoad();
        assertEquals(ArticlePageRequestState.State.READY, state.getState());
        assertEquals(
                ArticlePageRequestState.ForegroundLoadDecision.START,
                state.requestForegroundLoad(true));
        assertEquals(
                ArticlePageRequestState.ForegroundLoadDecision.NONE,
                state.requestForegroundLoad(true));
        state.failForegroundLoad(true);
        assertEquals(ArticlePageRequestState.State.READY, state.getState());
    }
}
