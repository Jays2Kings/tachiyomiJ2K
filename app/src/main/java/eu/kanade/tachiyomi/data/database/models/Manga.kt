package eu.kanade.tachiyomi.data.database.models

import android.content.Context
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.reader.settings.OrientationType
import eu.kanade.tachiyomi.ui.reader.settings.ReadingModeType
import eu.kanade.tachiyomi.util.manga.MangaCoverMetadata
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale

interface Manga : SManga {

    var id: Long?

    var source: Long

    var favorite: Boolean

    var last_update: Long

    var date_added: Long

    var viewer_flags: Int

    var chapter_flags: Int

    var hide_title: Boolean

    var filtered_scanlators: String?

    fun isBlank() = id == Long.MIN_VALUE
    fun isHidden() = status == -1

    fun setChapterOrder(sorting: Int, order: Int) {
        setChapterFlags(sorting, CHAPTER_SORTING_MASK)
        setChapterFlags(order, CHAPTER_SORT_MASK)
        setChapterFlags(CHAPTER_SORT_LOCAL, CHAPTER_SORT_LOCAL_MASK)
    }

    fun setSortToGlobal() = setChapterFlags(CHAPTER_SORT_FILTER_GLOBAL, CHAPTER_SORT_LOCAL_MASK)

    fun setFilterToGlobal() = setChapterFlags(CHAPTER_SORT_FILTER_GLOBAL, CHAPTER_FILTER_LOCAL_MASK)
    fun setFilterToLocal() = setChapterFlags(CHAPTER_FILTER_LOCAL, CHAPTER_FILTER_LOCAL_MASK)

    private fun setChapterFlags(flag: Int, mask: Int) {
        chapter_flags = chapter_flags and mask.inv() or (flag and mask)
    }

    private fun setViewerFlags(flag: Int, mask: Int) {
        viewer_flags = viewer_flags and mask.inv() or (flag and mask)
    }

    val sortDescending: Boolean
        get() = chapter_flags and CHAPTER_SORT_MASK == CHAPTER_SORT_DESC

    val hideChapterTitles: Boolean
        get() = displayMode == CHAPTER_DISPLAY_NUMBER

    val usesLocalSort: Boolean
        get() = chapter_flags and CHAPTER_SORT_LOCAL_MASK == CHAPTER_SORT_LOCAL

    val usesLocalFilter: Boolean
        get() = chapter_flags and CHAPTER_FILTER_LOCAL_MASK == CHAPTER_FILTER_LOCAL

    fun sortDescending(preferences: PreferencesHelper): Boolean =
        if (usesLocalSort) sortDescending else preferences.chaptersDescAsDefault().get()

    fun chapterOrder(preferences: PreferencesHelper): Int =
        if (usesLocalSort) sorting else preferences.sortChapterOrder().get()

    fun readFilter(preferences: PreferencesHelper): Int =
        if (usesLocalFilter) readFilter else preferences.filterChapterByRead().get()

    fun downloadedFilter(preferences: PreferencesHelper): Int =
        if (usesLocalFilter) downloadedFilter else preferences.filterChapterByDownloaded().get()

    fun bookmarkedFilter(preferences: PreferencesHelper): Int =
        if (usesLocalFilter) bookmarkedFilter else preferences.filterChapterByBookmarked().get()

    fun hideChapterTitle(preferences: PreferencesHelper): Boolean =
        if (usesLocalFilter) hideChapterTitles else preferences.hideChapterTitlesByDefault().get()

    fun showChapterTitle(defaultShow: Boolean): Boolean = chapter_flags and CHAPTER_DISPLAY_MASK == CHAPTER_DISPLAY_NUMBER

    fun seriesType(context: Context, sourceManager: SourceManager? = null): String {
        return context.getString(
            when (seriesType(sourceManager = sourceManager)) {
                TYPE_WEBTOON -> R.string.webtoon
                TYPE_MANHWA -> R.string.manhwa
                TYPE_MANHUA -> R.string.manhua
                TYPE_COMIC -> R.string.comic
                else -> R.string.manga
            },
        ).lowercase(Locale.getDefault())
    }

    fun getGenres(): List<String>? {
        return genre?.split(",")
            ?.mapNotNull { tag -> tag.trim().takeUnless { it.isBlank() } }
    }

    fun getOriginalGenres(): List<String>? {
        return (originalGenre ?: genre)?.split(",")
            ?.mapNotNull { tag -> tag.trim().takeUnless { it.isBlank() } }
    }

    /**
     * The type of comic the manga is (ie. manga, manhwa, manhua)
     */
    fun seriesType(useOriginalTags: Boolean = false, customTags: String? = null, sourceManager: SourceManager? = null): Int {
        val sourceName by lazy { (sourceManager ?: Injekt.get()).getOrStub(source).name }
        val tags = customTags ?: if (useOriginalTags) originalGenre else genre
        val currentTags = tags?.split(",")?.map { it.trim().lowercase(Locale.US) } ?: emptyList()
        return if (currentTags.any { tag -> isMangaTag(tag) }) {
            TYPE_MANGA
        } else if (currentTags.any { tag -> isComicTag(tag) } ||
            isComicSource(sourceName)
        ) {
            TYPE_COMIC
        } else if (currentTags.any { tag -> isWebtoonTag(tag) } ||
            (
                sourceName.contains("webtoon", true) &&
                    currentTags.none { tag -> isManhuaTag(tag) } &&
                    currentTags.none { tag -> isManhwaTag(tag) }
                )
        ) {
            TYPE_WEBTOON
        } else if (currentTags.any { tag -> isManhuaTag(tag) } || sourceName.contains(
                "manhua",
                true,
            )
        ) {
            TYPE_MANHUA
        } else if (currentTags.any { tag -> isManhwaTag(tag) } || isWebtoonSource(sourceName)) {
            TYPE_MANHWA
        } else {
            TYPE_MANGA
        }
    }

    /**
     * The type the reader should use. Different from manga type as certain manga has different
     * read types
     */
    fun defaultReaderType(): Int {
        val sourceName = Injekt.get<SourceManager>().getOrStub(source).name
        val currentTags = genre?.split(",")?.map { it.trim().lowercase(Locale.US) } ?: emptyList()
        return if (currentTags.any
            { tag ->
                isManhwaTag(tag) || tag.contains("webtoon")
            } || (
                isWebtoonSource(sourceName) &&
                    currentTags.none { tag -> isManhuaTag(tag) } &&
                    currentTags.none { tag -> isComicTag(tag) }
                )
        ) {
            ReadingModeType.WEBTOON.flagValue
        } else if (currentTags.any
            { tag ->
                tag == "chinese" || tag == "manhua" ||
                    tag.startsWith("english") || tag == "comic"
            } || (
                isComicSource(sourceName) && !sourceName.contains("tapas", true) &&
                    currentTags.none { tag -> isMangaTag(tag) }
                ) ||
            (sourceName.contains("manhua", true) && currentTags.none { tag -> isMangaTag(tag) })
        ) {
            ReadingModeType.LEFT_TO_RIGHT.flagValue
        } else {
            0
        }
    }

    fun isSeriesTag(tag: String): Boolean {
        val tagLower = tag.lowercase(Locale.ROOT)
        return isMangaTag(tagLower) || isManhuaTag(tagLower) ||
            isManhwaTag(tagLower) || isComicTag(tagLower) || isWebtoonTag(tagLower)
    }

    fun isMangaTag(tag: String): Boolean {
        return tag in listOf("manga", "манга", "jp") || tag.startsWith("japanese")
    }

    fun isManhuaTag(tag: String): Boolean {
        return tag in listOf("manhua", "маньхуа", "cn", "hk", "zh-Hans", "zh-Hant") || tag.startsWith("chinese")
    }

    fun isLongStrip(): Boolean {
        val currentTags =
            genre?.split(",")?.map { it.trim().lowercase(Locale.US) } ?: emptyList()
        return currentTags.any { it == "long strip" }
    }

    fun isManhwaTag(tag: String): Boolean {
        return tag in listOf("long strip", "manhwa", "манхва", "kr") || tag.startsWith("korean")
    }

    fun isComicTag(tag: String): Boolean {
        return tag in listOf("comic", "комикс", "en", "gb") || tag.startsWith("english")
    }

    fun isWebtoonTag(tag: String): Boolean {
        return tag.startsWith("webtoon")
    }

    fun isWebtoonSource(sourceName: String): Boolean {
        return sourceName.contains("webtoon", true) ||
            sourceName.contains("manhwa", true) ||
            sourceName.contains("toonily", true)
    }

    fun isComicSource(sourceName: String): Boolean {
        return sourceName.contains("gunnerkrigg", true) ||
            sourceName.contains("dilbert", true) ||
            sourceName.contains("cyanide", true) ||
            sourceName.contains("xkcd", true) ||
            sourceName.contains("tapas", true) ||
            sourceName.contains("ComicExtra", true) ||
            sourceName.contains("Read Comics Online", true) ||
            sourceName.contains("ReadComicOnline", true)
    }

    fun isOneShotOrCompleted(db: DatabaseHelper): Boolean {
        val tags by lazy { genre?.split(",")?.map { it.trim().lowercase(Locale.US) } }
        val chapters by lazy { db.getChapters(this).executeAsBlocking() }
        val firstChapterName by lazy { chapters.firstOrNull()?.name?.lowercase() ?: "" }
        return status == SManga.COMPLETED || tags?.contains("oneshot") == true ||
            (
                chapters.size == 1 &&
                    (
                        Regex("one.?shot").containsMatchIn(firstChapterName) ||
                            firstChapterName.contains("oneshot")
                        )
                )
    }

    fun key(): String {
        return "manga-id-$id"
    }

    // Used to display the chapter's title one way or another
    var displayMode: Int
        get() = chapter_flags and CHAPTER_DISPLAY_MASK
        set(mode) = setChapterFlags(mode, CHAPTER_DISPLAY_MASK)

    var readFilter: Int
        get() = chapter_flags and CHAPTER_READ_MASK
        set(filter) = setChapterFlags(filter, CHAPTER_READ_MASK)

    var downloadedFilter: Int
        get() = chapter_flags and CHAPTER_DOWNLOADED_MASK
        set(filter) = setChapterFlags(filter, CHAPTER_DOWNLOADED_MASK)

    var bookmarkedFilter: Int
        get() = chapter_flags and CHAPTER_BOOKMARKED_MASK
        set(filter) = setChapterFlags(filter, CHAPTER_BOOKMARKED_MASK)

    var sorting: Int
        get() = chapter_flags and CHAPTER_SORTING_MASK
        set(sort) = setChapterFlags(sort, CHAPTER_SORTING_MASK)

    var readingModeType: Int
        get() = viewer_flags and ReadingModeType.MASK
        set(readingMode) = setViewerFlags(readingMode, ReadingModeType.MASK)

    var orientationType: Int
        get() = viewer_flags and OrientationType.MASK
        set(rotationType) = setViewerFlags(rotationType, OrientationType.MASK)

    var vibrantCoverColor: Int?
        get() = vibrantCoverColorMap[id]
        set(value) {
            id?.let { vibrantCoverColorMap[it] = value }
        }

    var dominantCoverColors: Pair<Int, Int>?
        get() = MangaCoverMetadata.getColors(this)
        set(value) {
            value ?: return
            MangaCoverMetadata.addCoverColor(this, value.first, value.second)
        }

    companion object {

        // Generic filter that does not filter anything
        const val SHOW_ALL = 0x00000000

        const val CHAPTER_SORT_DESC = 0x00000000
        const val CHAPTER_SORT_ASC = 0x00000001
        const val CHAPTER_SORT_MASK = 0x00000001

        const val CHAPTER_SORT_FILTER_GLOBAL = 0x00000000
        const val CHAPTER_SORT_LOCAL = 0x00001000
        const val CHAPTER_SORT_LOCAL_MASK = 0x00001000
        const val CHAPTER_FILTER_LOCAL = 0x00002000
        const val CHAPTER_FILTER_LOCAL_MASK = 0x00002000

        const val CHAPTER_SHOW_UNREAD = 0x00000002
        const val CHAPTER_SHOW_READ = 0x00000004
        const val CHAPTER_READ_MASK = 0x00000006

        const val CHAPTER_SHOW_DOWNLOADED = 0x00000008
        const val CHAPTER_SHOW_NOT_DOWNLOADED = 0x00000010
        const val CHAPTER_DOWNLOADED_MASK = 0x00000018

        const val CHAPTER_SHOW_BOOKMARKED = 0x00000020
        const val CHAPTER_SHOW_NOT_BOOKMARKED = 0x00000040
        const val CHAPTER_BOOKMARKED_MASK = 0x00000060

        const val CHAPTER_SORTING_SOURCE = 0x00000000
        const val CHAPTER_SORTING_NUMBER = 0x00000100
        const val CHAPTER_SORTING_UPLOAD_DATE = 0x00000200
        const val CHAPTER_SORTING_MASK = 0x00000300

        const val CHAPTER_DISPLAY_NAME = 0x00000000
        const val CHAPTER_DISPLAY_NUMBER = 0x00100000
        const val CHAPTER_DISPLAY_MASK = 0x00100000

        const val TYPE_MANGA = 1
        const val TYPE_MANHWA = 2
        const val TYPE_MANHUA = 3
        const val TYPE_COMIC = 4
        const val TYPE_WEBTOON = 5

        private val vibrantCoverColorMap: HashMap<Long, Int?> = hashMapOf()

        fun create(source: Long): Manga = MangaImpl().apply {
            this.source = source
        }

        fun create(pathUrl: String, title: String, source: Long = 0): Manga = MangaImpl().apply {
            url = pathUrl
            this.title = title
            this.source = source
        }
    }
}
