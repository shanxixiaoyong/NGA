package gov.anzong.androidnga.activity.compose.topic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicPolicyTest {
    @Test
    fun unopenedTopicStaysVisibleAndUnmarked() {
        assertEquals(TopicReadState(false, false, false), projectTopicReadState(12, null))
    }

    @Test
    fun partiallyReadTopicIsGrayAndMarkedWhileFullyReadTopicIsHidden() {
        val partial = TopicReadProgress(highestReadFloor = 5, observedReplies = 10, touchedAt = 1)
        val partialState = projectTopicReadState(10, partial)
        assertTrue(partialState.hasBeenOpened)
        assertTrue(partialState.hasUnreadReplies)
        assertFalse(partialState.isFullyRead)

        val completeState = projectTopicReadState(5, partial)
        assertTrue(completeState.hasBeenOpened)
        assertFalse(completeState.hasUnreadReplies)
        assertTrue(completeState.isFullyRead)
    }

    @Test
    fun relativeTimeUsesStableBuckets() {
        val now = 2_000_000_000_000L
        assertEquals("刚刚", relativeReplyTime((now / 1000 - 20).toInt(), now))
        assertEquals("5分钟", relativeReplyTime((now / 1000 - 300).toInt(), now))
        assertEquals("2小时", relativeReplyTime((now / 1000 - 7_200).toInt(), now))
        assertEquals("3天", relativeReplyTime((now / 1000 - 259_200).toInt(), now))
    }

    @Test
    fun progressCodecKeepsLegacyPersonalBuildEntries() {
        val progress = TopicReadProgress(19, 25, 100)
        assertEquals(
            progress,
            TopicLocalState.decodeProgressEntry(TopicLocalState.encodeProgressEntry(progress)),
        )
        assertEquals(null, TopicLocalState.decodeProgressEntry("broken"))
    }
}
