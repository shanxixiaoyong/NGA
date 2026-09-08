package sp.phone.linuxdo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LinuxDoPerformanceArchitectureContractTest {
    private fun source(name: String) = File("src/main/java/sp/phone/linuxdo/$name").readText()

    @Test
    fun articleJsonAndAvatarTrafficUseIndependentCronetLanes() {
        val cronet = source("LinuxDoCronetSession.java")

        assertTrue(cronet.contains("linuxdo-cronet-json"))
        assertTrue(cronet.contains("linuxdo-cronet-media"))
        assertTrue(cronet.contains("mRequestExecutor.execute(() -> start("))
        assertTrue(cronet.contains("mBinaryExecutor.execute(() -> startBinary("))
        assertTrue(cronet.contains("url, state, mBinaryExecutor"))
        assertTrue(cronet.contains("ORIGIN + path, state, mRequestExecutor"))
        assertFalse(cronet.contains("private final ExecutorService mExecutor"))
    }

    @Test
    fun resolverWarmupAndProcessStableInputsAreReused() {
        val dns = source("LinuxDoHttpEngineDns.java")
        val session = source("LinuxDoHttpSession.java")

        assertTrue(dns.contains("CACHE_TTL_MS = 3L * 60L * 1000L"))
        assertTrue(dns.contains("cachedAddresses(hostname)"))
        assertTrue(dns.contains("private HttpEngine engine()"))
        assertTrue(session.contains("WARMUP_REUSE_MS = 3L * 60L * 1000L"))
        assertTrue(session.contains("private String userAgent()"))
    }

    @Test
    fun tappedTopicOverlapsNavigationAndHtmlPatternsArePrecompiled() {
        val repository = source("LinuxDoRepository.java")
        val topicFragment = File(
            "src/main/java/sp/phone/ui/fragment/TopicSearchFragment.java",
        ).readText()

        assertTrue(repository.contains("public void prefetchArticle(int topicId)"))
        assertTrue(repository.contains("public void prefetchArticle(int topicId, int appPage)"))
        assertTrue(topicFragment.contains("prefetchArticle(info.getTid(), param.page)"))
        assertTrue(repository.contains("parseTopicSnapshotAndWarmFirstFloor(json)"))
        assertTrue(repository.contains("snapshot.pageWaiters.get(page)"))
        assertTrue(repository.contains("completeArticlePage(snapshot, page"))
        assertTrue(repository.contains("DANGEROUS_CONTAINER.matcher(cooked)"))
        assertFalse(repository.contains("cooked\n                .replaceAll("))
        assertTrue(repository.contains("private static final DateTimeFormatter DISPLAY_TIME"))
    }

    @Test
    fun articleResponseAvoidsUiBounceAndFirstPaintAvoidsDuplicateMediaFetch() {
        val webSession = source("LinuxDoWebSession.java")
        val http = source("LinuxDoHttpSession.java")
        val repository = source("LinuxDoRepository.java")
        val adapter = File(
            "src/main/java/sp/phone/ui/adapter/ArticleListAdapter.java",
        ).readText()

        assertTrue(webSession.contains("interface BackgroundSuccessCallback extends Callback"))
        assertTrue(http.contains("Looper.myLooper() == Looper.getMainLooper()"))
        assertTrue(http.contains("callback instanceof LinuxDoWebSession.BackgroundSuccessCallback"))
        val cronet = source("LinuxDoCronetSession.java")
        assertTrue(cronet.contains("UrlRequest.Builder.REQUEST_PRIORITY_HIGHEST"))
        assertTrue(cronet.contains("callback instanceof LinuxDoWebSession.CriticalSuccessCallback"))
        assertTrue(repository.contains("new ArticleSessionCallback()"))
        assertTrue(adapter.contains("EXTERNAL_INITIAL_READY_FLOORS = 1"))
        assertTrue(adapter.contains("EXTERNAL_PREWARM_FLOORS = 2"))
        assertFalse(adapter.contains("prefetchInitialExternalMedia()"))
        assertTrue(repository.contains("interface ProgressiveArticleCallback"))
        assertTrue(repository.contains("snapshot.firstFloorPreview"))
        assertTrue(repository.contains("/posts.json?post_number=2"))
        assertTrue(repository.contains("publishFirstFloor(topicId, preview)"))
        assertTrue(adapter.contains("isExternalProgressiveUpgrade(data)"))
        assertTrue(adapter.contains(
            "mExternalContentReady && isExternalMediaPriority(position)",
        ))
    }
}
