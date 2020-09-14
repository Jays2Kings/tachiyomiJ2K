package eu.kanade.tachiyomi.data.database.models

import android.content.Context
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.util.storage.DiskUtil
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale

interface Manga : SManga {

    var id: Long?

    var source: Long

    var favorite: Boolean

    var last_update: Long

    var date_added: Long

    var viewer: Int

    var chapter_flags: Int

    var hide_title: Boolean

    fun isBlank() = id == Long.MIN_VALUE

    fun setChapterOrder(order: Int) {
        setFlags(order, SORT_MASK)
        setFlags(SORT_LOCAL, SORT_SELF_MASK)
    }

    fun setSortToGlobal() = setFlags(SORT_GLOBAL, SORT_SELF_MASK)

    private fun setFlags(flag: Int, mask: Int) {
        chapter_flags = chapter_flags and mask.inv() or (flag and mask)
    }

    fun sortDescending(): Boolean = chapter_flags and SORT_MASK == SORT_DESC

    fun usesLocalSort(): Boolean = chapter_flags and SORT_SELF_MASK == SORT_LOCAL

    fun sortDescending(defaultDesc: Boolean): Boolean {
        return if (chapter_flags and SORT_SELF_MASK == SORT_GLOBAL) defaultDesc
        else sortDescending()
    }

    fun showChapterTitle(defaultShow: Boolean): Boolean = chapter_flags and DISPLAY_MASK == DISPLAY_NUMBER

    fun mangaType(context: Context): String {
        return context.getString(
            when (mangaType()) {
                TYPE_WEBTOON -> R.string.webtoon
                TYPE_MANHWA -> R.string.manhwa
                TYPE_MANHUA -> R.string.manhua
                TYPE_COMIC -> R.string.comic
                else -> R.string.manga
            }
        ).toLowerCase(Locale.getDefault())
    }

    /**
     * The type of comic the manga is (ie. manga, manhwa, manhua)
     */
    fun mangaType(): Int {
        val sourceName = Injekt.get<SourceManager>().getOrStub(source).name
        val currentTags =
            genre?.split(",")?.map { it.trim().toLowerCase(Locale.US) } ?: emptyList()
        return if (currentTags.any { tag -> tag.startsWith("japanese") || isMangaTag(tag) }) {
            TYPE_MANGA
        } else if (currentTags.any { tag -> tag.startsWith("english") || isComicTag(tag) } ||
            isComicSource(sourceName)
        ) {
            TYPE_COMIC
        } else if (currentTags.any { tag ->
            tag.startsWith("chinese") || isManhuaTag(tag)
        } || sourceName.contains("manhua", true)
        ) {
            TYPE_MANHUA
        } else if (currentTags.any { tag -> isManhwaTag(tag) } || isWebtoonSource(sourceName)) {
            TYPE_MANHWA
        } else if (currentTags.any { tag -> tag.startsWith("webtoon") }) {
            TYPE_WEBTOON
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
        val currentTags = genre?.split(",")?.map { it.trim().toLowerCase(Locale.US) }
        return if (currentTags?.any
            { tag ->
                tag == "long strip" || tag == "manhwa" || tag.contains("webtoon")
            } == true || isWebtoonSource(sourceName)
        )
            ReaderActivity.WEBTOON
        else if (currentTags?.any
            { tag ->
                tag == "chinese" || tag == "manhua" ||
                    tag.startsWith("english") || tag == "comic"
            } == true || (isComicSource(sourceName) && !sourceName.contains("tapas", true)) ||
            sourceName.contains("manhua", true)
        )
            ReaderActivity.LEFT_TO_RIGHT
        else 0
    }

    fun isMangaTag(tag: String): Boolean {
        return tag in listOf("manga", "манга")
    }

    fun isManhuaTag(tag: String): Boolean {
        return tag in listOf("manhua", "маньхуа")
    }

    fun isManhwaTag(tag: String): Boolean {
        return tag in listOf("long strip", "manhwa", "манхва")
    }

    fun isComicTag(tag: String): Boolean {
        return tag in listOf("comic", "комикс")
    }

    fun isWebtoonSource(sourceName: String): Boolean {
        return sourceName.contains("webtoon", true) ||
            sourceName.contains("manwha", true) ||
            sourceName.contains("toonily", true)
    }

    fun isComicSource(sourceName: String): Boolean {
        return sourceName.contains("gunnerkrigg", true) ||
            sourceName.contains("dilbert", true) ||
            sourceName.contains("cyanide", true) ||
            sourceName.contains("xkcd", true) ||
            sourceName.contains("tapas", true) ||
            sourceName.contains("ComicExtra", true)
    }

    fun key(): String {
        return DiskUtil.hashKeyForDisk(thumbnail_url.orEmpty())
    }

    // Used to display the chapter's title one way or another
    var displayMode: Int
        get() = chapter_flags and DISPLAY_MASK
        set(mode) = setFlags(mode, DISPLAY_MASK)

    var readFilter: Int
        get() = chapter_flags and READ_MASK
        set(filter) = setFlags(filter, READ_MASK)

    var downloadedFilter: Int
        get() = chapter_flags and DOWNLOADED_MASK
        set(filter) = setFlags(filter, DOWNLOADED_MASK)

    var bookmarkedFilter: Int
        get() = chapter_flags and BOOKMARKED_MASK
        set(filter) = setFlags(filter, BOOKMARKED_MASK)

    var sorting: Int
        get() = chapter_flags and SORTING_MASK
        set(sort) = setFlags(sort, SORTING_MASK)

    companion object {

        const val SORT_DESC = 0x00000000
        const val SORT_ASC = 0x00000001
        const val SORT_MASK = 0x00000001

        const val SORT_GLOBAL = 0x00000000
        const val SORT_LOCAL = 0x00001000
        const val SORT_SELF_MASK = 0x00001000

        // Generic filter that does not filter anything
        const val SHOW_ALL = 0x00000000

        const val SHOW_UNREAD = 0x00000002
        const val SHOW_READ = 0x00000004
        const val READ_MASK = 0x00000006

        const val SHOW_DOWNLOADED = 0x00000008
        const val SHOW_NOT_DOWNLOADED = 0x00000010
        const val DOWNLOADED_MASK = 0x00000018

        const val SHOW_BOOKMARKED = 0x00000020
        const val SHOW_NOT_BOOKMARKED = 0x00000040
        const val BOOKMARKED_MASK = 0x00000060

        const val SORTING_SOURCE = 0x00000000
        const val SORTING_NUMBER = 0x00000100
        const val SORTING_MASK = 0x00000100

        const val DISPLAY_NAME = 0x00000000
        const val DISPLAY_NUMBER = 0x00100000
        const val DISPLAY_MASK = 0x00100000

        const val TYPE_MANGA = 1
        const val TYPE_MANHWA = 2
        const val TYPE_MANHUA = 3
        const val TYPE_COMIC = 4
        const val TYPE_WEBTOON = 5

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
