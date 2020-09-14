package eu.kanade.tachiyomi.ui.library

import android.app.Activity
import android.graphics.Color
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.github.florent37.viewtooltip.ViewTooltip
import eu.davidea.flexibleadapter.SelectableAdapter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.library.LibraryUpdateService
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.icon
import eu.kanade.tachiyomi.ui.base.MaterialMenuSheet
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.invisible
import eu.kanade.tachiyomi.util.view.updateLayoutParams
import eu.kanade.tachiyomi.util.view.visible
import eu.kanade.tachiyomi.util.view.visibleIf
import kotlinx.android.synthetic.main.library_category_header_item.*
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class LibraryHeaderHolder(val view: View, private val adapter: LibraryCategoryAdapter) :
    BaseFlexibleViewHolder(view, adapter, true) {

    private val sectionText: TextView = view.findViewById(R.id.category_title)
    private val sortText: TextView = view.findViewById(R.id.category_sort)
    private val updateButton: ImageView = view.findViewById(R.id.update_button)
    private val checkboxImage: ImageView = view.findViewById(R.id.checkbox)
    private val expandImage: ImageView = view.findViewById(R.id.collapse_arrow)
    private val catProgress: ProgressBar = view.findViewById(R.id.cat_progress)

    init {
        category_header_layout.setOnClickListener { toggleCategory() }
        updateButton.setOnClickListener { addCategoryToUpdate() }
        sectionText.setOnLongClickListener {
            val category = (adapter.getItem(adapterPosition) as? LibraryHeaderItem)?.category
            adapter.libraryListener.manageCategory(adapterPosition)
            category?.isDynamic == false
        }
        sectionText.setOnClickListener { toggleCategory() }
        sortText.setOnClickListener { it.post { showCatSortOptions() } }
        checkboxImage.setOnClickListener { selectAll() }
        updateButton.drawable.mutate()
    }

    private fun toggleCategory() {
        adapter.libraryListener.toggleCategoryVisibility(adapterPosition)
        val tutorial = Injekt.get<PreferencesHelper>().shownLongPressCategoryTutorial()
        if (!tutorial.get()) {
            ViewTooltip.on(itemView.context as? Activity, sectionText).autoHide(true, 5000L)
                .align(ViewTooltip.ALIGN.START).position(ViewTooltip.Position.TOP)
                .text(R.string.long_press_category)
                .color(itemView.context.getResourceColor(R.attr.colorAccent))
                .textSize(TypedValue.COMPLEX_UNIT_SP, 15f).textColor(Color.WHITE)
                .withShadow(false).corner(30).arrowWidth(15).arrowHeight(15).distanceWithView(0)
                .show()
            tutorial.set(true)
        }
    }

    fun bind(item: LibraryHeaderItem) {
        val index = adapter.headerItems.indexOf(item)
        val previousIsCollapsed =
            if (index > 0) {
                (adapter.headerItems[index - 1] as? LibraryHeaderItem)?.category?.isHidden
                    ?: false
            } else {
                false
            }
        val shorterMargin = adapter.headerItems.firstOrNull() == item
        sectionText.updateLayoutParams<ConstraintLayout.LayoutParams> {
            topMargin = (
                when {
                    shorterMargin -> 2
                    previousIsCollapsed -> 5
                    else -> 32
                }
                ).dpToPx
        }
        val category = item.category

        if (category.isDynamic) {
            category_header_layout.background = null
            sectionText.background = null
        } else {
            category_header_layout.setBackgroundResource(R.drawable.list_item_selector)
            sectionText.setBackgroundResource(R.drawable.square_ripple)
        }

        if (category.isAlone && !category.isDynamic) sectionText.text = ""
        else sectionText.text = category.name
        if (category.sourceId != null) {
            val icon = adapter.sourceManager.get(category.sourceId!!)?.icon()
            icon?.setBounds(0, 0, 32.dpToPx, 32.dpToPx)
            sectionText.setCompoundDrawablesRelative(icon, null, null, null)
        } else {
            sectionText.setCompoundDrawablesRelative(null, null, null, null)
        }

        val isAscending = category.isAscending()
        val sortingMode = category.sortingMode()
        val sortDrawable = when {
            sortingMode == LibrarySort.DRAG_AND_DROP || sortingMode == null -> R.drawable.ic_sort_24dp
            if (sortingMode == LibrarySort.DATE_ADDED || sortingMode == LibrarySort.LATEST_CHAPTER || sortingMode == LibrarySort.LAST_READ) !isAscending else isAscending -> R.drawable.ic_arrow_downward_24dp
            else -> R.drawable.ic_arrow_upward_24dp
        }

        sortText.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, sortDrawable, 0)
        sortText.setText(category.sortRes())
        expandImage.setImageResource(
            if (category.isHidden) R.drawable.ic_expand_more_24dp
            else R.drawable.ic_expand_less_24dp
        )
        when {
            adapter.mode == SelectableAdapter.Mode.MULTI -> {
                checkboxImage.visibleIf(!category.isHidden)
                expandImage.visibleIf(category.isHidden && !adapter.isSingleCategory && !category.isDynamic)
                updateButton.gone()
                catProgress.gone()
                setSelection()
            }
            category.id ?: -1 < 0 -> {
                expandImage.gone()
                checkboxImage.gone()
                catProgress.gone()
                updateButton.gone()
            }
            LibraryUpdateService.categoryInQueue(category.id) -> {
                expandImage.visibleIf(!adapter.isSingleCategory && !category.isDynamic)
                checkboxImage.gone()
                catProgress.visible()
                updateButton.invisible()
            }
            else -> {
                expandImage.visibleIf(!adapter.isSingleCategory && !category.isDynamic)
                catProgress.gone()
                checkboxImage.gone()
                updateButton.visibleIf(!adapter.isSingleCategory)
            }
        }
    }

    private fun addCategoryToUpdate() {
        if (adapter.libraryListener.updateCategory(adapterPosition)) {
            catProgress.visible()
            updateButton.invisible()
        }
    }

    private fun showCatSortOptions() {
        val category =
            (adapter.getItem(adapterPosition) as? LibraryHeaderItem)?.category ?: return
        adapter.controller.activity?.let { activity ->
            val items = mutableListOf(
                MaterialMenuSheet.MenuSheetItem(
                    LibrarySort.ALPHA,
                    R.drawable.ic_sort_by_alpha_24dp,
                    R.string.title
                ),
                MaterialMenuSheet.MenuSheetItem(
                    LibrarySort.LAST_READ,
                    R.drawable.ic_recent_read_outline_24dp,
                    R.string.last_read
                ),
                MaterialMenuSheet.MenuSheetItem(
                    LibrarySort.LATEST_CHAPTER,
                    R.drawable.ic_new_releases_24dp,
                    R.string.latest_chapter
                ),
                MaterialMenuSheet.MenuSheetItem(
                    LibrarySort.UNREAD,
                    R.drawable.ic_eye_24dp,
                    R.string.unread
                ),
                MaterialMenuSheet.MenuSheetItem(
                    LibrarySort.TOTAL,
                    R.drawable.ic_sort_by_numeric_24dp,
                    R.string.total_chapters
                ),
                MaterialMenuSheet.MenuSheetItem(
                    LibrarySort.DATE_ADDED,
                    R.drawable.ic_heart_outline_24dp,
                    R.string.date_added
                )
            )
            if (category.isDynamic) {
                items.add(
                    MaterialMenuSheet.MenuSheetItem(
                        LibrarySort.DRAG_AND_DROP,
                        R.drawable.ic_label_outline_24dp,
                        R.string.category
                    )
                )
            }
            val sortingMode = category.sortingMode()
            val sheet = MaterialMenuSheet(
                activity,
                items,
                activity.getString(R.string.sort_by),
                sortingMode
            ) { sheet, item ->
                onCatSortClicked(category, item)
                val nCategory = (adapter.getItem(adapterPosition) as? LibraryHeaderItem)?.category
                val isAscending = nCategory?.isAscending() ?: false
                val drawableRes = getSortRes(item, isAscending)
                sheet.setDrawable(item, drawableRes)
                false
            }
            val isAscending = category.isAscending()
            val drawableRes = getSortRes(sortingMode, isAscending)
            sheet.setDrawable(sortingMode ?: -1, drawableRes)
            sheet.show()
        }
    }

    private fun getSortRes(sortingMode: Int?, isAscending: Boolean): Int = when {
        sortingMode == LibrarySort.DRAG_AND_DROP -> R.drawable.ic_check_24dp
        if (sortingMode == LibrarySort.DATE_ADDED ||
            sortingMode == LibrarySort.LATEST_CHAPTER ||
            sortingMode == LibrarySort.LAST_READ
        ) !isAscending else isAscending ->
            R.drawable.ic_arrow_downward_24dp
        else -> R.drawable.ic_arrow_upward_24dp
    }

    private fun onCatSortClicked(category: Category, menuId: Int?) {
        val modType = if (menuId == null) {
            val t = (category.mangaSort?.minus('a') ?: 0) + 1
            if (t % 2 != 0) t + 1
            else t - 1
        } else {
            val order = when (menuId) {
                LibrarySort.DRAG_AND_DROP -> {
                    adapter.libraryListener.sortCategory(category.id!!, 'D' - 'a' + 1)
                    return
                }
                LibrarySort.DATE_ADDED -> 5
                LibrarySort.TOTAL -> 4
                LibrarySort.LAST_READ -> 3
                LibrarySort.UNREAD -> 2
                LibrarySort.LATEST_CHAPTER -> 1
                else -> 0
            }
            if (order == category.catSortingMode()) {
                onCatSortClicked(category, null)
                return
            }
            (2 * order + 1)
        }
        adapter.libraryListener.sortCategory(category.id!!, modType)
    }

    private fun selectAll() {
        adapter.libraryListener.selectAll(adapterPosition)
    }

    fun setSelection() {
        val allSelected = adapter.libraryListener.allSelected(adapterPosition)
        val drawable = ContextCompat.getDrawable(
            contentView.context,
            if (allSelected) R.drawable.ic_check_circle_24dp else R.drawable.ic_radio_button_unchecked_24dp
        )
        val tintedDrawable = drawable?.mutate()
        tintedDrawable?.setTint(
            ContextCompat.getColor(
                contentView.context,
                if (allSelected) R.color.colorAccent
                else R.color.gray_button
            )
        )
        checkboxImage.setImageDrawable(tintedDrawable)
    }

    override fun onLongClick(view: View?): Boolean {
        super.onLongClick(view)
        return false
    }
}
