package eu.kanade.tachiyomi.ui.library

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractSectionableItem
import eu.davidea.flexibleadapter.items.IFilterable
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.view.updateLayoutParams
import eu.kanade.tachiyomi.widget.AutofitRecyclerView
import kotlinx.android.synthetic.main.manga_grid_item.view.*
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class LibraryItem(
    val manga: LibraryManga,
    header: LibraryHeaderItem,
    private val preferences: PreferencesHelper = Injekt.get()
) :
    AbstractSectionableItem<LibraryHolder, LibraryHeaderItem>(header), IFilterable<String> {

    var downloadCount = -1
    var unreadType = 2

    private val uniformSize: Boolean
        get() = preferences.uniformGrid().getOrDefault()

    private val libraryLayout: Int
        get() = preferences.libraryLayout().getOrDefault()

    val hideReadingButton: Boolean
        get() = preferences.hideStartReadingButton().getOrDefault()

    override fun getLayoutRes(): Int {
        return if (libraryLayout == 0 || manga.isBlank())
            R.layout.manga_list_item
        else
            R.layout.manga_grid_item
    }

    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): LibraryHolder {
        val parent = adapter.recyclerView
        return if (parent is AutofitRecyclerView) {
            val libraryLayout = libraryLayout
            val isFixedSize = uniformSize
            if (libraryLayout == 0 || manga.isBlank()) {
                LibraryListHolder(view, adapter as LibraryCategoryAdapter)
            } else {
                view.apply {
                    val coverHeight = (parent.itemWidth / 3f * 4f).toInt()
                    if (libraryLayout == 1) {
                        gradient.layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            (coverHeight * 0.66f).toInt(),
                            Gravity.BOTTOM
                        )
                        card.updateLayoutParams<ConstraintLayout.LayoutParams> {
                            bottomMargin = 6.dpToPx
                        }
                    } else if (libraryLayout == 2) {
                        constraint_layout.background = ContextCompat.getDrawable(
                            context,
                            R.drawable.library_item_selector
                        )
                    }
                    if (isFixedSize) {
                        constraint_layout.layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        cover_thumbnail.maxHeight = Int.MAX_VALUE
                        cover_thumbnail.minimumHeight = 0
                        constraint_layout.minHeight = 0
                        cover_thumbnail.scaleType = ImageView.ScaleType.CENTER_CROP
                        cover_thumbnail.adjustViewBounds = false
                        cover_thumbnail.layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            (parent.itemWidth / 3f * 3.7f).toInt()
                        )
                    } else {
                        constraint_layout.minHeight = coverHeight
                        cover_thumbnail.minimumHeight = (parent.itemWidth / 3f * 3.6f).toInt()
                        cover_thumbnail.maxHeight = (parent.itemWidth / 3f * 6f).toInt()
                    }
                }
                LibraryGridHolder(
                    view,
                    adapter as LibraryCategoryAdapter,
                    parent.itemWidth,
                    libraryLayout == 1,
                    isFixedSize
                )
            }
        } else {
            LibraryListHolder(view, adapter as LibraryCategoryAdapter)
        }
    }

    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: LibraryHolder,
        position: Int,
        payloads: MutableList<Any?>?
    ) {
        holder.onSetValues(this)
    }

    /**
     * Returns true if this item is draggable.
     */
    override fun isDraggable(): Boolean {
        return !manga.isBlank()
    }

    override fun isEnabled(): Boolean {
        return !manga.isBlank()
    }

    override fun isSelectable(): Boolean {
        return !manga.isBlank()
    }

    /**
     * Filters a manga depending on a query.
     *
     * @param constraint the query to apply.
     * @return true if the manga should be included, false otherwise.
     */
    override fun filter(constraint: String): Boolean {
        if (manga.isBlank() && manga.title.isBlank())
            return constraint.isEmpty()
        val sourceManager by injectLazy<SourceManager>()
        val sourceName = if (manga.source == 0L) "Local" else
            sourceManager.getOrStub(manga.source).name
        return manga.title.contains(constraint, true) ||
            (manga.author?.contains(constraint, true) ?: false) ||
            (manga.artist?.contains(constraint, true) ?: false) ||
            sourceName.contains(constraint, true) ||
            if (constraint.contains(",")) {
                val genres = manga.genre?.split(", ")
                constraint.split(",").all { containsGenre(it.trim(), genres) }
            } else containsGenre(constraint, manga.genre?.split(", "))
    }

    @SuppressLint("DefaultLocale")
    private fun containsGenre(tag: String, genres: List<String>?): Boolean {
        if (tag.trim().isEmpty()) return true
        return if (tag.startsWith("-"))
            genres?.find {
                it.trim().toLowerCase() == tag.substringAfter("-").toLowerCase()
            } == null
        else
            genres?.find {
                it.trim().toLowerCase() == tag.toLowerCase()
            } != null
    }

    override fun equals(other: Any?): Boolean {
        if (other is LibraryItem) {
            return manga.id == other.manga.id && manga.category == other.manga.category
        }
        return false
    }

    override fun hashCode(): Int {
        return (manga.id!! + (manga.category shl 50).toLong()).hashCode() // !!.hashCode()
    }
}
