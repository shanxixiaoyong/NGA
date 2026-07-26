package gov.anzong.androidnga.activity.compose.board

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForumBoardReviewedReadBoundaryTest {

    @Test
    fun remoteBoardReadUsesReviewedRawResponseBoundary() {
        val method = remoteBoardMethodSource()

        assertTrue(method.contains("UserManager.captureActiveSession()"))
        assertTrue(method.contains("FoundationAccessPolicy.READ_BOARD_LIST"))
        assertTrue(method.contains("if (snapshot.isAnonymous)"))
        assertTrue(method.contains("NgaRequestContext.anonymousRead("))
        assertTrue(method.contains("NgaRequestContext.Intent.READ"))
        assertTrue(method.contains("snapshot.accountId"))
        assertTrue(method.contains("snapshot.sessionGeneration"))
        assertTrue(method.contains("snapshot.cookieHeader"))
        assertTrue(method.contains("FoundationAccessPolicy.enabledForReviewedReads()"))
        assertTrue(method.contains(".create(RetrofitServiceKt::class.java)"))
        assertTrue(method.contains("RawNgaResponse.from(service.getUrlRaw(requestContext, url))"))
        assertTrue(method.contains("NgaResponseClassifier().classify("))
        assertTrue(method.contains("classified.type != ClassifiedNgaResponse.Type.PAYLOAD"))
    }

    @Test
    fun remoteBoardReadChecksSessionBeforeCacheAndPublication() {
        val method = remoteBoardMethodSource()
        val cacheWrite = method.indexOf("writeRemoteBoardList(context, payload)")
        val sessionChecks = Regex(Regex.escape("UserManager.isSessionCurrent(snapshot)"))
            .findAll(method)
            .map { it.range.first }
            .toList()

        assertTrue("expected a cache write", cacheWrite >= 0)
        assertTrue("expected a session check before caching", sessionChecks.any { it < cacheWrite })
        assertTrue("expected a session check before publication", sessionChecks.any { it > cacheWrite })
    }

    @Test
    fun remoteBoardReadDoesNotUseLegacyOrSensitiveDiagnostics() {
        val method = remoteBoardMethodSource()

        assertFalse(method.contains("getString("))
        assertFalse(method.contains("LogUtils"))
        assertFalse(method.contains(".message"))
        assertFalse(method.contains("printStackTrace"))
    }

    private fun remoteBoardMethodSource(): String {
        val relativePath =
            "src/main/java/gov/anzong/androidnga/activity/compose/board/ForumBoardRepository.kt"
        val sourceFile = sequenceOf(
            File(relativePath),
            File("nga_phone_base_3.0", relativePath),
        ).firstOrNull(File::isFile)
            ?: error("Unable to locate ForumBoardRepository.kt from ${File(".").absolutePath}")
        val source = sourceFile.readText()
        val start = source.indexOf("suspend fun requestRemoteBoardList")
        val end = source.indexOf("fun loadRemoteBoardList", start)
        check(start >= 0 && end > start) { "Unable to isolate requestRemoteBoardList" }
        return source.substring(start, end)
    }
}
