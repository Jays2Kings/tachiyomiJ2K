package eu.kanade.tachiyomi.ui.library

import android.os.Build
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.util.lang.removeArticles
import eu.kanade.tachiyomi.util.system.timeSpanFromNow
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Adapter storing a list of manga in a certain category.
 *
 * @param view the fragment containing this adapter.
 */
class LibraryCategoryAdapter(val controller: LibraryController) :
        FlexibleAdapter<IFlexible<*>>(null, controller, true) {

    init {
        setDisplayHeadersAtStartUp(true)
    }
    /**
     * The list of manga in this category.
     */
    private var mangas: List<LibraryItem> = emptyList()

    val libraryListener: LibraryListener = controller

    val isSingleCategory
        get() = controller.singleCategory || !controller.presenter.showAllCategories

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

    /**
     * Returns the position in the adapter for the given manga.
     *
     * @param manga the manga to find.
     */
    fun indexOf(categoryOrder: Int): Int {
        return currentItems.indexOfFirst {
            if (it is LibraryHeaderItem) it.category.order == categoryOrder
            else false }
    }

    /**
     * Returns the position in the adapter for the given manga.
     *
     * @param manga the manga to find.
     */
    fun indexOf(manga: Manga): Int {
        return currentItems.indexOfFirst {
            if (it is LibraryItem) it.manga.id == manga.id
            else false }
    }

    fun getHeaderPositions(): List<Int> {
        return currentItems.mapIndexedNotNull { index, it ->
            if (it is LibraryHeaderItem) index
            else null }
    }

    /**
     * Returns the position in the adapter for the given manga.
     *
     * @param manga the manga to find.
     */
    fun allIndexOf(manga: Manga): List<Int> {
        return currentItems.mapIndexedNotNull { index, it ->
            if (it is LibraryItem && it.manga.id == manga.id) index
            else null }
    }

    fun performFilter() {
        val s = getFilter(String::class.java)
        if (s.isNullOrBlank()) {
            updateDataSet(mangas)
        } else {
            updateDataSet(mangas.filter { it.filter(s) })
        }
        isLongPressDragEnabled = libraryListener.canDrag() && s.isNullOrBlank()
    }

    private fun getFirstLetter(name: String): String {
        val letter = name.firstOrNull() ?: '#'
        return if (letter.isLetter()) getFirstChar(name) else "#"
    }

    override fun onCreateBubbleText(position: Int): String {
        val preferences: PreferencesHelper by injectLazy()
        val db: DatabaseHelper by injectLazy()
        if (position == itemCount - 1) return recyclerView.context.getString(R.string.bottom)
        return when (val item: IFlexible<*>? = getItem(position)) {
            is LibraryHeaderItem ->
                if (!preferences.hideCategories().getOrDefault()) item.category.name
                else recyclerView.context.getString(R.string.top)
            is LibraryItem -> {
                if (!isSingleCategory) {
                    item.header?.category?.name.orEmpty()
                } else if (item.manga.isBlank()) ""
                else when (getSort()) {
                    LibrarySort.DRAG_AND_DROP -> {
                        if (!preferences.hideCategories().getOrDefault()) {
                            val title = item.manga.title
                            if (preferences.removeArticles().getOrDefault())
                                getFirstChar(title.removeArticles())
                            else getFirstChar(title)
                        } else {
                            val category = db.getCategoriesForManga(item.manga)
                                .executeAsBlocking().firstOrNull()?.name
                            category ?: recyclerView.context.getString(R.string.default_value)
                        }
                    }
                    LibrarySort.LAST_READ -> {
                        val id = item.manga.id ?: return ""
                        val history = db.getHistoryByMangaId(id).executeAsBlocking()
                        val last = history.maxBy { it.last_read }
                        if (last != null && last.last_read > 100) last.last_read.timeSpanFromNow
                        else "N/A"
                    }
                    LibrarySort.UNREAD -> {
                        val unread = item.manga.unread
                        if (unread > 0) unread.toString()
                        else recyclerView.context.getString(R.string.read)
                    }
                    LibrarySort.TOTAL -> {
                        val total = item.chapterCount
                        if (total > 0) total.toString()
                        else "N/A"
                    }
                    LibrarySort.LATEST_CHAPTER -> {
                        val lastUpdate = item.manga.last_update
                        if (lastUpdate > 0) lastUpdate.timeSpanFromNow
                        // getShortDate(Date(lastUpdate))
                        else "N/A"
                    }
                    LibrarySort.DATE_ADDED -> {
                        val lastUpdate = item.manga.date_added
                        if (lastUpdate > 0) lastUpdate.timeSpanFromNow
                        else "N/A"
                    }
                    else -> {
                        val title = if (preferences.removeArticles()
                                .getOrDefault()
                        ) item.manga.title.removeArticles()
                        else item.manga.title
                        getFirstLetter(title)
                    }
                }
            }
            else -> ""
        }
    }

    private fun getSort(): Int {
        val preferences: PreferencesHelper by injectLazy()
        return if (!preferences.showAllCategories().get() && !preferences.hideCategories().getOrDefault()) {
            controller.presenter.getCurrentCategory()?.sortingMode() ?: LibrarySort.DRAG_AND_DROP
        } else {
            preferences.librarySortingMode().getOrDefault()
        }
    }

    private fun getFirstChar(string: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val chars = string.codePoints().toArray().firstOrNull() ?: return ""
            val char = Character.toChars(chars)
            return String(char).toUpperCase(Locale.US)
        } else {
            return string.toCharArray().firstOrNull()?.toString()?.toUpperCase(Locale.US) ?: ""
        }
    }

    private fun getShortRange(value: Int): String {
        return when (value) {
            1 -> "1"
            2 -> "2"
            3 -> "3"
            4 -> "4"
            5 -> "5"
            in 6..10 -> "6"
            in 11..50 -> "10"
            in 51..100 -> "50"
            in 101..500 -> "1+"
            in 499..899 -> "4+"
            in 901..Int.MAX_VALUE -> "9+"
            else -> "0"
        }
    }

    private fun getShorterDate(date: Date): String {
        val cal = Calendar.getInstance()
        cal.time = Date()

        val yearNow = cal.get(Calendar.YEAR)
        val cal2 = Calendar.getInstance()
        cal2.time = date
        val yearThen = cal2.get(Calendar.YEAR)

        return if (yearNow == yearThen)
            SimpleDateFormat("M", Locale.getDefault()).format(date)
        else
            SimpleDateFormat("''yy", Locale.getDefault()).format(date)
    }

    interface LibraryListener {
        fun startReading(position: Int)
        fun onItemReleased(position: Int)
        fun canDrag(): Boolean
        fun updateCategory(catId: Int): Boolean
        fun sortCategory(catId: Int, sortBy: Int)
        fun selectAll(position: Int)
        fun allSelected(position: Int): Boolean
        fun toggleCategoryVisibility(position: Int)
        fun manageCategory(position: Int)
    }
}
