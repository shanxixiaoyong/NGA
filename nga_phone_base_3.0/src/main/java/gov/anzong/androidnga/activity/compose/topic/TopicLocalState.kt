package gov.anzong.androidnga.activity.compose.topic

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import gov.anzong.androidnga.base.util.ContextUtils
import sp.phone.param.ContentSource
import kotlin.math.max

data class TopicReadProgress(
    val highestReadFloor: Int,
    val observedReplies: Int,
    val touchedAt: Long,
)

data class TopicReadState(
    val hasBeenOpened: Boolean,
    val hasUnreadReplies: Boolean,
    val isFullyRead: Boolean,
)

data class HiddenBoard(val fid: Int, val name: String, val source: Int = ContentSource.NGA)

fun projectTopicReadState(replies: Int, progress: TopicReadProgress?): TopicReadState {
    val normalizedReplies = max(0, replies)
    val opened = progress != null
    val unread = progress?.let { normalizedReplies > it.highestReadFloor } == true
    return TopicReadState(
        hasBeenOpened = opened,
        hasUnreadReplies = unread,
        isFullyRead = opened && !unread,
    )
}

fun relativeReplyTime(lastPostSeconds: Int, nowMillis: Long = System.currentTimeMillis()): String {
    if (lastPostSeconds <= 0) return "时间未知"
    val seconds = max(0L, nowMillis / 1000L - lastPostSeconds.toLong())
    return when {
        seconds < 60 -> "刚刚"
        seconds < 3_600 -> "${seconds / 60}分钟"
        seconds < 86_400 -> "${seconds / 3_600}小时"
        seconds < 86_400 * 30L -> "${seconds / 86_400}天"
        else -> "${seconds / (86_400 * 30L)}个月"
    }
}

/**
 * Device-local topic state shared by ordinary board lists and the article reader.
 *
 * The legacy file/key names are intentionally retained so an in-place upgrade from an earlier
 * personal build keeps its hidden topics, hidden boards, and read positions. Rows consume copied
 * snapshots; no SharedPreferences lookup occurs during RecyclerView binding or scrolling.
 */
class TopicLocalState @JvmOverloads constructor(
    private val source: Int = ContentSource.NGA,
    private val preferences: SharedPreferences = ContextUtils.getContext().getSharedPreferences(
        FILE_NAME,
        Context.MODE_PRIVATE,
    ),
) {
    private val lock = Any()
    private val hiddenTopics = mutableSetOf<Int>()
    private val hiddenBoards = mutableSetOf<Int>()
    private val followedTopics = mutableSetOf<Int>()
    private val hiddenCategories = mutableSetOf<Int>()
    private val hiddenTags = mutableSetOf<String>()
    private val blockedTitleTerms = mutableSetOf<String>()

    init {
        reloadHiddenState()
    }

    fun reloadHiddenState() = synchronized(lock) {
        hiddenTopics.clear()
        hiddenTopics.addAll(
            preferences.getStringSet(hiddenTopicsKey(), emptySet())
                .orEmpty()
                .mapNotNull(String::toIntOrNull),
        )
        hiddenBoards.clear()
        hiddenBoards.addAll(
            preferences.getStringSet(hiddenBoardsKey(), emptySet())
                .orEmpty()
                .mapNotNull(String::toIntOrNull),
        )
        followedTopics.clear()
        followedTopics.addAll(
            preferences.getStringSet(followedTopicsKey(), emptySet())
                .orEmpty()
                .mapNotNull(String::toIntOrNull),
        )
        hiddenCategories.clear()
        hiddenCategories.addAll(
            preferences.getStringSet(hiddenCategoriesKey(), emptySet())
                .orEmpty()
                .mapNotNull(String::toIntOrNull),
        )
        hiddenTags.clear()
        hiddenTags.addAll(preferences.getStringSet(hiddenTagsKey(), emptySet()).orEmpty())
        blockedTitleTerms.clear()
        blockedTitleTerms.addAll(
            preferences.getStringSet(blockedTitleTermsKey(), emptySet()).orEmpty(),
        )
    }

    fun hiddenTopicSnapshot(): Set<Int> = synchronized(lock) { hiddenTopics.toSet() }

    fun hiddenBoardSnapshot(): Set<Int> = synchronized(lock) { hiddenBoards.toSet() }

    fun followedTopicSnapshot(): Set<Int> = synchronized(lock) { followedTopics.toSet() }

    /**
     * Categories disabled from the aggregate LinuxDo board feed.
     *
     * LinuxDo topics historically used the hidden-board store when the user chose
     * “屏蔽板块” from a topic row, while the category picker used a separate
     * hidden-category store.  Return the union so both entry points observe the
     * same switch (and old installations are upgraded without losing choices).
     */
    fun hiddenCategorySnapshot(): Set<Int> = synchronized(lock) {
        if (source != ContentSource.LINUX_DO) hiddenCategories.toSet()
        else (hiddenCategories + hiddenBoards).toSet()
    }

    fun isCategoryHidden(categoryId: Int): Boolean = synchronized(lock) {
        source == ContentSource.LINUX_DO && categoryId > 0
                && (hiddenCategories.contains(categoryId) || hiddenBoards.contains(categoryId))
    }

    fun setCategoryHidden(categoryId: Int, hidden: Boolean) = synchronized(lock) {
        if (source != ContentSource.LINUX_DO || categoryId <= 0) return@synchronized
        if (hidden) {
            hiddenCategories.add(categoryId)
            // Keep the topic-row “屏蔽板块” list and the category picker in sync.
            hiddenBoards.add(categoryId)
        } else {
            hiddenCategories.remove(categoryId)
            hiddenBoards.remove(categoryId)
        }
        persistIds(hiddenCategoriesKey(), hiddenCategories)
        persistIds(hiddenBoardsKey(), hiddenBoards)
    }

    fun hiddenTagSnapshot(): Set<String> = synchronized(lock) { hiddenTags.toSet() }

    fun blockedTitleTermsSnapshot(): Set<String> = synchronized(lock) {
        blockedTitleTerms.toSet()
    }

    fun hideTag(tag: String) = synchronized(lock) {
        val normalized = normalizeFilterText(tag)
        if (source == ContentSource.LINUX_DO && normalized.isNotEmpty() && hiddenTags.add(normalized)) {
            preferences.edit().putStringSet(hiddenTagsKey(), hiddenTags.toSet()).apply()
        }
    }

    fun unhideTag(tag: String) = synchronized(lock) {
        if (hiddenTags.remove(normalizeFilterText(tag))) {
            preferences.edit().putStringSet(hiddenTagsKey(), hiddenTags.toSet()).apply()
        }
    }

    fun setBlockedTitleTerms(multiline: String) = synchronized(lock) {
        val next = multiline.lineSequence()
            .map(::normalizeFilterText)
            .filter(String::isNotEmpty)
            .map { it.take(MAX_FILTER_TERM_LENGTH) }
            .distinct()
            .take(MAX_FILTER_TERMS)
            .toSet()
        blockedTitleTerms.clear()
        blockedTitleTerms.addAll(next)
        preferences.edit().putStringSet(blockedTitleTermsKey(), next).apply()
    }

    fun isTopicFollowed(tid: Int): Boolean = synchronized(lock) { followedTopics.contains(tid) }

    fun setTopicFollowed(tid: Int, followed: Boolean) = synchronized(lock) {
        if (tid <= 0) return@synchronized
        if (followed) followedTopics.add(tid) else followedTopics.remove(tid)
        persistIds(followedTopicsKey(), followedTopics)
    }

    fun hiddenBoardEntries(nameFallback: (Int) -> String): List<HiddenBoard> = synchronized(lock) {
        hiddenBoards.sorted().map { fid ->
            HiddenBoard(
                fid = fid,
                source = source,
                name = preferences.getString(boardNameKey(fid), null)
                    ?.takeIf(String::isNotBlank)
                    ?: nameFallback(fid),
            )
        }
    }

    fun readProgressSnapshot(): Map<Int, TopicReadProgress> = synchronized(PROGRESS_LOCK) {
        preferences.all.mapNotNull { (key, value) ->
            if (!key.startsWith(readProgressPrefix()) || value !is String) {
                return@mapNotNull null
            }
            val tid = key.removePrefix(readProgressPrefix()).toIntOrNull()
                ?: return@mapNotNull null
            decodeProgressEntry(value)?.let { tid to it }
        }.toMap()
    }

    fun readProgress(tid: Int): TopicReadProgress? = synchronized(PROGRESS_LOCK) {
        decodeProgressEntry(preferences.getString(progressKey(tid), null))
    }

    fun isTopLikedPageOnly(tid: Int): Boolean = synchronized(PROGRESS_LOCK) {
        tid > 0 && preferences.getBoolean(topLikedOnlyKey(tid), false)
    }

    /** Marks a topic opened without treating the non-chronological high-liked floors as read. */
    fun recordTopLikedPage(
        tid: Int,
        replies: Int,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        if (tid <= 0) return
        synchronized(PROGRESS_LOCK) {
            val key = progressKey(tid)
            val previous = decodeProgressEntry(preferences.getString(key, null))
            val alreadyTopOnly = preferences.getBoolean(topLikedOnlyKey(tid), false)
            if (previous != null && !alreadyTopOnly) return@synchronized
            val progress = TopicReadProgress(
                highestReadFloor = 0,
                observedReplies = max(max(0, replies), previous?.observedReplies ?: 0),
                touchedAt = nowMillis,
            )
            val editor = preferences.edit()
                .putString(key, encodeProgressEntry(progress))
                .putBoolean(topLikedOnlyKey(tid), true)
            if (previous == null) {
                val stored = readProgressSnapshot()
                if (stored.size >= MAX_PROGRESS_ENTRIES) {
                    stored.minByOrNull { it.value.touchedAt }?.let {
                        editor.remove(progressKey(it.key))
                        editor.remove(topLikedOnlyKey(it.key))
                    }
                }
            }
            editor.apply()
        }
    }

    fun markChronologicalPageOpened(tid: Int) {
        if (tid > 0) preferences.edit().remove(topLikedOnlyKey(tid)).apply()
    }

    fun hideTopic(tid: Int) = synchronized(lock) {
        if (tid > 0 && hiddenTopics.add(tid)) {
            persistIds(hiddenTopicsKey(), hiddenTopics)
        }
    }

    fun hideBoard(fid: Int, name: String) = synchronized(lock) {
        if (fid != 0) {
            hiddenBoards.add(fid)
            if (source == ContentSource.LINUX_DO) hiddenCategories.add(fid)
            preferences.edit()
                .putStringSet(hiddenBoardsKey(), hiddenBoards.map(Int::toString).toSet())
                .putStringSet(hiddenCategoriesKey(), hiddenCategories.map(Int::toString).toSet())
                .putString(boardNameKey(fid), name.takeIf(String::isNotBlank) ?: "板块 $fid")
                .apply()
        }
    }

    fun unhideBoard(fid: Int) = synchronized(lock) {
        val removedBoard = hiddenBoards.remove(fid)
        val removedCategory = source == ContentSource.LINUX_DO && hiddenCategories.remove(fid)
        if (removedBoard || removedCategory) {
            preferences.edit()
                .putStringSet(hiddenBoardsKey(), hiddenBoards.map(Int::toString).toSet())
                .putStringSet(hiddenCategoriesKey(), hiddenCategories.map(Int::toString).toSet())
                .remove(boardNameKey(fid))
                .apply()
        }
    }

    fun recordReadFloor(
        tid: Int,
        floor: Int,
        replies: Int,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        if (tid <= 0 || floor < 0) return
        synchronized(PROGRESS_LOCK) {
            val key = progressKey(tid)
            val previous = decodeProgressEntry(preferences.getString(key, null))
            val progress = TopicReadProgress(
                highestReadFloor = max(floor, previous?.highestReadFloor ?: 0),
                observedReplies = max(max(0, replies), previous?.observedReplies ?: 0),
                touchedAt = nowMillis,
            )
            val editor = preferences.edit()
                .putString(key, encodeProgressEntry(progress))
                .remove(topLikedOnlyKey(tid))
            if (previous == null) {
                val stored = readProgressSnapshot()
                if (stored.size >= MAX_PROGRESS_ENTRIES) {
                    stored.minByOrNull { it.value.touchedAt }?.let {
                        editor.remove(progressKey(it.key))
                        editor.remove(topLikedOnlyKey(it.key))
                    }
                }
            }
            editor.apply()
        }
    }

    private fun persistIds(key: String, values: Set<Int>) {
        preferences.edit().putStringSet(key, values.map(Int::toString).toSet()).apply()
    }

    private fun namespace(): String = if (source == ContentSource.LINUX_DO) "linuxdo_" else ""
    private fun hiddenTopicsKey() = namespace() + KEY_HIDDEN_TOPICS
    private fun hiddenBoardsKey() = namespace() + KEY_HIDDEN_BOARDS
    private fun readProgressPrefix() = namespace() + KEY_READ_PROGRESS_PREFIX
    private fun followedTopicsKey() = namespace() + KEY_FOLLOWED_TOPICS
    private fun hiddenCategoriesKey() = namespace() + KEY_HIDDEN_CATEGORIES
    private fun hiddenTagsKey() = namespace() + KEY_HIDDEN_TAGS
    private fun blockedTitleTermsKey() = namespace() + KEY_BLOCKED_TITLE_TERMS
    private fun progressKey(tid: Int) = "${readProgressPrefix()}$tid"
    private fun topLikedOnlyKey(tid: Int) = "${namespace()}$KEY_TOP_LIKED_ONLY_PREFIX$tid"
    private fun boardNameKey(fid: Int) = "${namespace()}$KEY_HIDDEN_BOARD_NAME_PREFIX$fid"

    companion object {
        // Compatibility with personal builds that introduced the local recommendation store.
        const val FILE_NAME = "recommendation_local_state"
        private const val KEY_HIDDEN_TOPICS = "hidden_topics"
        private const val KEY_HIDDEN_BOARDS = "hidden_boards"
        private const val KEY_READ_PROGRESS_PREFIX = "read_progress_"
        private const val KEY_TOP_LIKED_ONLY_PREFIX = "top_liked_only_"
        private const val KEY_FOLLOWED_TOPICS = "followed_topics"
        private const val KEY_HIDDEN_CATEGORIES = "hidden_categories"
        private const val KEY_HIDDEN_TAGS = "hidden_tags"
        private const val KEY_BLOCKED_TITLE_TERMS = "blocked_title_terms"
        private const val KEY_HIDDEN_BOARD_NAME_PREFIX = "hidden_board_name_"
        private val PROGRESS_LOCK = Any()
        const val MAX_PROGRESS_ENTRIES = 500
        const val MAX_FILTER_TERMS = 200
        const val MAX_FILTER_TERM_LENGTH = 80

        @JvmStatic
        fun normalizeFilterText(value: String?): String = value.orEmpty().trim().lowercase()

        @VisibleForTesting
        fun encodeProgressEntry(progress: TopicReadProgress): String =
            "${progress.highestReadFloor}:${progress.observedReplies}:${progress.touchedAt}"

        @VisibleForTesting
        fun decodeProgressEntry(value: String?): TopicReadProgress? {
            val parts = value?.split(':') ?: return null
            val floor = parts.getOrNull(0)?.toIntOrNull()
            val replies = parts.getOrNull(1)?.toIntOrNull()
            val touchedAt = parts.getOrNull(2)?.toLongOrNull()
            return if (
                floor != null && floor >= 0 &&
                replies != null && replies >= 0 &&
                touchedAt != null && touchedAt >= 0
            ) {
                TopicReadProgress(floor, replies, touchedAt)
            } else {
                null
            }
        }
    }
}
