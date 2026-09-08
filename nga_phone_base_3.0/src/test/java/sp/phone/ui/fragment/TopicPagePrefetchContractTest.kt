package sp.phone.ui.fragment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pins the Android-facing prefetch wiring. This module has no Robolectric, so lifecycle and UI
 * boundaries are verified from source while the planner and request state run as pure JVM tests.
 */
class TopicPagePrefetchContractTest {

    private val projectRoot = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) {
        it.parentFile
    }.first { File(it, "nga_phone_base_3.0").isDirectory }

    private fun source(relativePath: String) = File(projectRoot, relativePath).readText()

    private val tabFragmentSource =
        source("nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleTabFragment.java")
    private val listFragmentSource =
        source("nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleListFragment.java")
    private val searchFragmentSource =
        source("nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleSearchFragment.java")
    private val cacheActivitySource =
        source("nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/ArticleCacheActivity.java")
    private val shareViewModelSource =
        source("nga_phone_base_3.0/src/main/java/sp/phone/mvp/viewmodel/ArticleShareViewModel.java")
    private val presenterSource =
        source("nga_phone_base_3.0/src/main/java/sp/phone/mvp/presenter/ArticleListPresenter.java")
    private val modelSource =
        source("nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/ArticleListModel.java")
    private val nativePolicySource =
        source(
            "nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/web/" +
                "NgaNativeArticleRequestPolicy.java",
        )
    private val localStateSource =
        source("nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/topic/TopicLocalState.kt")

    @Test
    fun pagerKeepsTwoOffscreenPagesAndReplansFromRowsAndSelection() {
        assertTrue(
            tabFragmentSource.contains(
                "mRequestParam != null && mRequestParam.source == ContentSource.LINUX_DO ? 1 : 2",
            ),
        )
        assertTrue(tabFragmentSource.contains("mTotalPages = count;"))
        assertTrue(
            tabFragmentSource.contains(
                "mCurrentPage = mPagerAdapter.getServerPageAt(position);",
            ),
        )
        assertTrue(
            tabFragmentSource.contains(
                "ArticlePagePrefetchPlanner.plan(",
            ),
        )
        assertTrue(tabFragmentSource.split("publishPrefetchPages();").size - 1 >= 3)
    }

    @Test
    fun linuxDoKeepsPageLoadingDemandDriven() {
        val plannerSource = source(
            "nga_phone_base_3.0/src/main/java/sp/phone/mvp/viewmodel/ArticlePagePrefetchPlanner.java",
        )
        assertTrue(plannerSource.contains("if (source == ContentSource.LINUX_DO) {"))
        assertTrue(plannerSource.contains("return Collections.emptyList();"))
        assertTrue(listFragmentSource.contains("mPresenter.prefetchPage();"))
    }

    @Test
    fun candidatePublicationAlwaysCopiesIntoAnImmutableList() {
        assertTrue(shareViewModelSource.contains("LiveData<List<Integer>> getPrefetchPages()"))
        assertTrue(shareViewModelSource.contains("new ArrayList<>(prefetchPages)"))
        assertTrue(shareViewModelSource.contains("Collections.unmodifiableList(snapshot)"))
        assertTrue(shareViewModelSource.contains("mPrefetchPages.setValue"))
    }

    @Test
    fun onlyNormalOnlinePagerChildrenObserveCandidates() {
        assertTrue(listFragmentSource.contains("getParentFragment() instanceof ArticleTabFragment"))
        assertTrue(listFragmentSource.contains("!mRequestParam.loadCache"))
        assertTrue(listFragmentSource.contains("mRequestParam.searchPost == 0"))
        assertTrue(listFragmentSource.contains("viewModel.getPrefetchPages().observe(this, pages ->"))
        assertTrue(listFragmentSource.contains("pages.contains(mRequestParam.page)"))
        assertTrue(listFragmentSource.contains("mPresenter.prefetchPage();"))

        assertFalse(searchFragmentSource.contains("prefetchPage"))
        assertFalse(cacheActivitySource.contains("getPrefetchPages"))
        assertFalse(cacheActivitySource.contains("prefetchPage"))
    }

    @Test
    fun virtualPageZeroDoesNotAdvanceChronologicalReadProgress() {
        assertTrue(listFragmentSource.contains("!mRequestParam.topLikedPage"))
        assertTrue(tabFragmentSource.contains("mPagerAdapter.hasTopLikedPage()"))
        assertTrue(
            tabFragmentSource.contains(
                "mHighestReadFloor != UnreadJumpPolicy.NO_TARGET",
            ),
        )
        assertTrue(listFragmentSource.contains("recordTopLikedPage("))
        assertTrue(listFragmentSource.contains("markChronologicalPageOpened("))
        assertTrue(localStateSource.contains("KEY_TOP_LIKED_ONLY_PREFIX"))
        assertTrue(tabFragmentSource.contains("|| mTopLikedPageOnly) return;"))
    }

    @Test
    fun prefetchUsesTheExistingModelPathAndHasNoForegroundFailureSideEffects() {
        assertTrue(
            presenterSource.contains(
                "mBaseModel.loadPage(mRequestParam, mPrefetchCallback);",
            ),
        )
        assertFalse(presenterSource.contains("RetrofitService"))
        assertFalse(presenterSource.contains("ArticleConvertFactory"))

        val silentCallback = presenterSource
            .substringAfter("private class PrefetchCallback")
            .substringBefore("private final OnHttpCallBack<ThreadData> mDataCallBack")
        assertFalse(silentCallback.contains("showToast"))
        assertFalse(silentCallback.contains("showWithWebView"))
        assertFalse(silentCallback.contains("retryWithNewAccount"))

        val silentFailure = presenterSource
            .substringAfter("private void handlePrefetchFailure()")
            .substringBefore("private void requestForegroundLoad")
        assertTrue(silentFailure.contains("mPageRequestState.failPrefetch()"))
        assertTrue(silentFailure.contains("requestForegroundLoad(false);"))
        assertFalse(silentFailure.contains("showToast"))
        assertFalse(silentFailure.contains("showWithWebView"))
        assertFalse(silentFailure.contains("retryWithNewAccount"))

        assertTrue(presenterSource.contains("@OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)"))
        val backgroundTransition = presenterSource
            .substringAfter("@OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)")
            .substringBefore("public ArticleListPresenter(")
        assertTrue(
            backgroundTransition.contains(
                "boolean wasPromoted = mPageRequestState.movePrefetchToBackground();",
            ),
        )
        assertTrue(backgroundTransition.contains("mBaseView.setRefreshing(false);"))
    }

    @Test
    fun foregroundUsesBoundedNativeChannelsAndUsesNativeWebRecoveryWithoutBrowserMode() {
        assertTrue(listFragmentSource.contains("mSwipeRefreshLayout.setOnRefreshListener"))
        assertTrue(listFragmentSource.contains("mPresenter.loadPage(mRequestParam);"))
        assertTrue(presenterSource.contains("requestForegroundLoad(true);"))
        assertFalse(presenterSource.contains("retryWithNewAccount()"))
        assertFalse(presenterSource.contains("getNextCookie()"))
        assertFalse(presenterSource.contains("startJsonFallback()"))
        assertFalse(presenterSource.contains("loadJsonFallbackPage"))
        assertFalse(presenterSource.contains("showWithWebView()"))
        assertFalse(presenterSource.contains("ForumWebFragment.class.getName()"))
        assertTrue(modelSource.contains("requestNgaPageWithRetry"))
        assertTrue(modelSource.contains("WireFormat.LEGACY_GB18030"))
        assertTrue(modelSource.contains("WireFormat.UTF8_ARRAYS"))
        assertTrue(modelSource.contains("isRetryableNgaReadFailure"))
        assertTrue(modelSource.contains("loadWebFallbackPage"))
        assertTrue(modelSource.contains("NgaWebArticleFallbackPolicy.buildReadUrl"))
        assertTrue(modelSource.contains("ArticleConvertFactory.parseArticleInfo(snapshot)"))
        assertFalse(modelSource.contains("ArticleConvertFactory.parseWebArticleInfo(snapshot)"))
        assertFalse(presenterSource.contains("showWithWebView()"))
    }

    @Test
    fun threadPageWireParserAndWebRecoveryStayBoundedToDetachLifecycle() {
        assertTrue(nativePolicySource.contains("LEGACY_GB18030(8"))
        assertTrue(nativePolicySource.contains("UTF8_ARRAYS(11"))
        assertTrue(modelSource.contains("header == null || header.isEmpty()"))
        assertTrue(modelSource.contains("? mService.getRaw(url)"))
        assertTrue(modelSource.contains(": mService.getRaw(url, header)"))
        assertFalse(modelSource.contains("mService.getRaw(url, null)"))
        assertTrue(modelSource.contains("ArticleConvertFactory.parseArticleInfo(response)"))
        assertTrue(modelSource.contains("outcome.getDiagnostic()"))
        assertTrue(modelSource.contains("readBody(body, format.charset())"))
        assertTrue(modelSource.contains("parseArticleInfo(snapshot)"))
        assertTrue(modelSource.contains("NgaWebArticleFallbackSession.getInstance().load"))
        assertEquals(4, Regex("bindUntilEvent\\(FragmentEvent\\.DETACH\\)").findAll(modelSource).count())
    }
}
