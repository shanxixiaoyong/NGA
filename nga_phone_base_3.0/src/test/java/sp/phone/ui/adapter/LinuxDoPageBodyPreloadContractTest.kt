package sp.phone.ui.adapter

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LinuxDoPageBodyPreloadContractTest {
    @Test
    fun linuxDoPrioritizesVisibleBodiesAndAvoidsHiddenWorkDuringFling() {
        val source = File(
            "src/main/java/sp/phone/ui/adapter/ArticleListAdapter.java",
        ).readText()

        assertTrue(source.contains("if (mReadOnlyExternalSource) {"))
        assertTrue(source.contains("preloadExternalPageBodies();"))
        assertTrue(source.contains("private void preloadNextExternalPageBody()"))
        assertTrue(source.contains("if (mData == null || mData.getRowList() == null"))
        assertTrue(source.contains("EXTERNAL_PRELOAD_INTERVAL_MS = 16L"))
        assertTrue(source.contains("EXTERNAL_BACKGROUND_PRELOAD_INTERVAL_MS = 72L"))
        assertTrue(source.contains("mExternalPreloadHandler.post(mExternalPreloadRunnable);"))
        assertTrue(source.contains("mExternalContentReady ? EXTERNAL_BACKGROUND_PRELOAD_INTERVAL_MS : 8L"))
        assertTrue(source.contains("prewarmExternalWebViews();"))
        assertTrue(source.contains("if (!mExternalContentReady) return -1;"))
        assertTrue(source.contains("webView.setEagerNetworkImages(true);"))
        assertTrue(source.contains(
            "mExternalContentReady && isExternalMediaPriority(position)",
        ))
        assertTrue(source.contains("expandExternalProgressState"))
        assertTrue(source.contains("Let the first compositor frame settle"))
        assertTrue(source.contains("public void setListScrolling(boolean scrolling)"))
        assertTrue(source.contains(
            "Detached WebViews do not reliably advance Chromium's compositor",
        ))
        assertTrue(source.contains("mLocalWebViews[position] = webView"))
        assertTrue(source.contains("row.getFormattedHtmlData()"))
        assertTrue(source.contains("public void releaseWebViews()"))
    }

    @Test
    fun linuxDoDismissesAfterInitialVisibleWindowAndKeepsPreloading() {
        val adapterSource = File(
            "src/main/java/sp/phone/ui/adapter/ArticleListAdapter.java",
        ).readText()
        val fragmentSource = File(
            "src/main/java/sp/phone/ui/fragment/ArticleListFragment.java",
        ).readText()
        val presenterSource = File(
            "src/main/java/sp/phone/mvp/presenter/ArticleListPresenter.java",
        ).readText()
        val webClientSource = File(
            "src/main/java/sp/phone/view/webview/WebViewClientEx.java",
        ).readText()

        assertTrue(adapterSource.contains("resetExternalContentReadiness();"))
        assertTrue(adapterSource.contains("markExternalFloorReady(position)"))
        assertTrue(adapterSource.contains("EXTERNAL_INITIAL_READY_FLOORS = 1"))
        assertTrue(adapterSource.contains("EXTERNAL_PREWARM_FLOORS = 2"))
        assertTrue(adapterSource.contains("EXTERNAL_AHEAD_PRELOAD_FLOORS = 6"))
        assertTrue(adapterSource.contains("setVisibleRange"))
        assertTrue(adapterSource.contains("loadExternalBody"))
        assertTrue(adapterSource.contains("setPageFinishedListener"))
        assertTrue(adapterSource.contains("notifyExternalContentReady();"))
        assertTrue(fragmentSource.contains(
            "setExternalContentReadyListener(this::onLinuxDoContentReady);",
        ))
        assertTrue(fragmentSource.contains("LINUX_DO_CONTENT_READY_TIMEOUT_MS"))
        assertTrue(fragmentSource.contains("mListView.postDelayed("))
        assertTrue(presenterSource.contains(
            "mRequestParam.source == ContentSource.LINUX_DO",
        ))
        assertTrue(presenterSource.contains("detached"))
        assertTrue(presenterSource.contains("ArticleListAdapter"))
        assertTrue(webClientSource.contains("postVisualStateCallback"))
        assertTrue(webClientSource.contains("postOnAnimation(listener)"))
        assertTrue(webClientSource.contains("placeholder"))
        assertTrue(!webClientSource.contains("fetchAvatarBlocking"))
    }
}
