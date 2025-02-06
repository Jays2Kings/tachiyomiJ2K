package eu.kanade.tachiyomi.ui.library

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.util.lang.chop
import eu.kanade.tachiyomi.util.lang.removeArticles
import eu.kanade.tachiyomi.util.system.isLTR
import eu.kanade.tachiyomi.util.system.timeSpanFromNow
import eu.kanade.tachiyomi.util.system.withDefContext
import kotlinx.coroutines.runBlocking
import uy.kohesive.injekt.injectLazy
import java.util.Locale

/**
 * Adapter storing a list of manga in a certain category.
 *
 * @param view the fragment containing this adapter.
 */
class LibraryCategoryAdapter(val controller: LibraryController?) :
    FlexibleAdapter<IFlexible<*>>(null, controller, true) {

    val sourceManager by injectLazy<SourceManager>()

    private val preferences: PreferencesHelper by injectLazy()

    var showNumber = preferences.categoryNumberOfItems().get()

    var showOutline = preferences.outlineOnCovers().get()

    private var lastCategory = ""

    val hasActiveFilters: Boolean
        get() = controller?.hasActiveFilters == true

    init {
        setDisplayHeadersAtStartUp(true)
    }

    /**
     * The number of manga in each category.
     */
    var itemsPerCategory: Map<Int, Int> = emptyMap()

    /**
     * The list of manga in this category.
     */
    private var mangas: List<LibraryItem> = emptyList()

    val libraryListener: LibraryListener? = controller

    val isSingleCategory
        get() = controller?.singleCategory == true || controller?.presenter?.showAllCategories == false

    /**
     * Sets a list of manga in the adapter.
     *
     * @param list the list to set.
     */
    fun setItems(list: List<LibraryItem>) {
        // A copy of manga always unfiltered.
        mangas = list.toList()

        performFilter()
    }

    private fun setItemsPerCategoryMap() {
        val controller = controller ?: return
        itemsPerCategory = headerItems.associate { header ->
            (header as LibraryHeaderItem).catId to
                controller.presenter.getItemCountInCategories(header.catId)
        }
    }

    /**
     * Returns the position in the adapter for the given manga.
     *
     * @param manga the manga to find.
     */
    fun indexOf(categoryOrder: Int): Int {
        return currentItems.indexOfFirst {
            if (it is LibraryHeaderItem) {
                it.category.order == categoryOrder
            } else {
                false
            }
        }
    }

    /**
     * Returns the position in the adapter for the given manga.
     *
     * @param manga the manga to find.
     */
    fun findCategoryHeader(catId: Int): LibraryHeaderItem? {
        return currentItems.find {
            (it is LibraryHeaderItem) && it.category.id == catId
        } as? LibraryHeaderItem
    }

    /**
     * Returns the position in the adapter for the given manga.
     *
     * @param manga the manga to find.
     */
    fun indexOf(manga: Manga): Int {
        return currentItems.indexOfFirst {
            if (it is LibraryItem) {
                it.manga.id == manga.id
            } else {
                false
            }
        }
    }

    fun getHeaderPositions(): List<Int> {
        return currentItems.mapIndexedNotNull { index, it ->
            if (it is LibraryHeaderItem) {
                index
            } else {
                null
            }
        }
    }

    /**
     * Returns the position in the adapter for the given manga.
     *
     * @param manga the manga to find.
     */
    fun allIndexOf(manga: Manga): List<Int> {
        return currentItems.mapIndexedNotNull { index, it ->
            if (it is LibraryItem && it.manga.id == manga.id) {
                index
            } else {
                null
            }
        }
    }

    private fun performFilter() {
        runBlocking { performFilterAsync() }
    }

    suspend fun performFilterAsync() {
        val s = getFilter(String::class.java)
        if (s.isNullOrBlank()) {
            if (mangas.firstOrNull()?.filter?.isNotBlank() == true) {
                mangas.forEach { it.filter = "" }
            }
            updateDataSet(mangas)
        } else {
            val filteredManga = withDefContext { mangas.filter { it.filter(s) } }
            if (filteredManga.isEmpty() && controller?.presenter?.showAllCategories == false) {
                val catId = mangas.firstOrNull()?.let { it.header?.catId ?: it.manga.category }
                val blankItem = catId?.let { controller.presenter.blankItem(it) }
                updateDataSet(blankItem ?: emptyList())
            } else {
                updateDataSet(filteredManga)
            }
        }
        isLongPressDragEnabled = libraryListener?.canDrag() == true && s.isNullOrBlank()
        setItemsPerCategoryMap()
    }

    private fun getFirstLetter(name: String): String {
        val letter = name.firstOrNull() ?: '#'
        return if (letter.isLetter()) getFirstChar(name) else "#"
    }

    private fun getFirstChar(string: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val chars = string.codePoints().toArray().firstOrNull() ?: return ""
            val char = Character.toChars(chars)
            return String(char).uppercase(Locale.US)
        } else {
            return string.toCharArray().firstOrNull()?.toString()?.uppercase(Locale.US) ?: ""
        }
    }

    override fun onCreateBubbleText(position: Int): String {
        val preferences: PreferencesHelper by injectLazy()
        val db: DatabaseHelper by injectLazy()
        val context = recyclerView.context
        if (position == itemCount - 1) return context.getString(R.string.bottom)
        return when (val item: IFlexible<*>? = getItem(position)) {
            is LibraryHeaderItem -> {
                vibrateOnCategoryChange(item.category.name)
                item.category.name
            }
            is LibraryItem -> {
                val text = if (item.manga.isBlank()) {
                    return item.header?.category?.name.orEmpty()
                } else {
                    when (getSort(position)) {
                        LibrarySort.DragAndDrop -> {
                            if (item.header.category.isDynamic) {
                                val category = db.getCategoriesForManga(item.manga).executeAsBlocking().firstOrNull()?.name
                                category ?: context.getString(R.string.default_value)
                            } else {
                                val title = item.manga.title
                                if (preferences.removeArticles().get()) {
                                    title.removeArticles().chop(15)
                                } else {
                                    title.take(10)
                                }
                            }
                        }
                        LibrarySort.DateFetched -> {
                            val id = item.manga.id ?: return ""
                            val history = db.getChapters(id).executeAsBlocking()
                            val last = history.maxOfOrNull { it.date_fetch }
                            context.timeSpanFromNow(R.string.fetched_, last ?: 0)
                        }
                        LibrarySort.NextUnread -> {
                            val id = item.manga.id ?: return ""
                            val unreadChapters = db.getUnreadChapters(id).executeAsBlocking()
                            val last = unreadChapters.minOfOrNull { it.date_upload }
                            context.timeSpanFromNow(R.string.next_unread_chapter_, last ?: 0)
                        }
                        LibrarySort.LastRead -> {
                            val id = item.manga.id ?: return ""
                            val history = db.getHistoryByMangaId(id).executeAsBlocking()
                            val last = history.maxOfOrNull { it.last_read }
                            context.timeSpanFromNow(R.string.read_, last ?: 0)
                        }
                        LibrarySort.Unread -> {
                            val unread = item.manga.unread
                            if (unread > 0) {
                                context.getString(R.string._unread, unread)
                            } else {
                                context.getString(R.string.read)
                            }
                        }
                        LibrarySort.TotalChapters -> {
                            val total = item.manga.totalChapters
                            if (total > 0) {
                                recyclerView.resources.getQuantityString(
                                    R.plurals.chapters_plural,
                                    total,
                                    total,
                                )
                            } else {
                                "N/A"
                            }
                        }
                        LibrarySort.LatestChapter -> {
                            context.timeSpanFromNow(R.string.updated_, item.manga.last_update)
                        }
                        LibrarySort.DateAdded -> {
                            context.timeSpanFromNow(R.string.added_, item.manga.date_added)
                        }
                        LibrarySort.Title -> {
                            val title = if (preferences.removeArticles().get()) {
                                item.manga.title.removeArticles()
                            } else {
                                item.manga.title
                            }
                            getFirstLetter(title)
                        }
                    }
                }
                if (!isSingleCategory) {
                    vibrateOnCategoryChange(item.header?.category?.name.orEmpty())
                }
                when {
                    isSingleCategory -> text
                    recyclerView.resources.isLTR -> text + " - " + item.header?.category?.name.orEmpty()
                    else -> item.header?.category?.name.orEmpty() + " - " + text
                }
            }
            else -> ""
        }
    }

    private fun vibrateOnCategoryChange(categoryName: String) {
        if (categoryName != lastCategory) {
            lastCategory = categoryName
            recyclerView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    private fun getSort(position: Int): LibrarySort {
        val header = (getItem(position) as? LibraryItem)?.header
        return header?.category?.sortingMode() ?: LibrarySort.DragAndDrop
    }

    interface LibraryListener {
        fun startReading(position: Int, view: View?)
        fun onItemReleased(position: Int)
        fun canDrag(): Boolean
        fun updateCategory(position: Int): Boolean
        fun sortCategory(catId: Int, sortBy: Char)
        fun selectAll(position: Int)
        fun allSelected(position: Int): Boolean
        fun toggleCategoryVisibility(position: Int)
        fun manageCategory(position: Int)
        fun globalSearch(query: String)
    }
}
