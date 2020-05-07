package eu.kanade.tachiyomi.ui.library

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.PopupMenu
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.github.florent37.viewtooltip.ViewTooltip
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.SelectableAdapter
import eu.davidea.flexibleadapter.items.AbstractHeaderItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.library.LibraryUpdateService
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
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

class LibraryHeaderItem(
    private val categoryF: (Int) -> Category,
    private val catId: Int
) :
    AbstractHeaderItem<LibraryHeaderItem.Holder>() {

    override fun getLayoutRes(): Int {
        return R.layout.library_category_header_item
    }

    override fun createViewHolder(
        view: View,
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>
    ): Holder {
        return Holder(view, adapter as LibraryCategoryAdapter)
    }

    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: Holder,
        position: Int,
        payloads: MutableList<Any?>?
    ) {
        holder.bind(this)
    }

    val category: Category
        get() = categoryF(catId)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is LibraryHeaderItem) {
            return category.id == other.category.id
        }
        return false
    }

    override fun isDraggable(): Boolean {
        return false
    }

    override fun isSelectable(): Boolean {
        return false
    }

    override fun hashCode(): Int {
        return -(category.id!!)
    }

    class Holder(val view: View, private val adapter: LibraryCategoryAdapter) :
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
                adapter.libraryListener.manageCategory(adapterPosition)
                true
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
                topMargin = (when {
                    shorterMargin -> 2
                    previousIsCollapsed -> 5
                    else -> 32
                }).dpToPx
            }
            val category = item.category

            if (category.isFirst == true && category.isLast == true) sectionText.text = ""
            else sectionText.text = category.name
            sortText.text = itemView.context.getString(
                R.string.sort_by_, itemView.context.getString(category.sortRes())
            )

            val isAscending = category.isAscending()
            val sortingMode = category.sortingMode()
            val sortDrawable = when {
                sortingMode == LibrarySort.DRAG_AND_DROP || sortingMode == null -> R.drawable.ic_sort_white_24dp
                if (sortingMode == LibrarySort.DATE_ADDED || sortingMode == LibrarySort.LATEST_CHAPTER || sortingMode == LibrarySort.LAST_READ) !isAscending else isAscending -> R.drawable.ic_arrow_down_white_24dp
                else -> R.drawable.ic_arrow_up_white_24dp
            }

            sortText.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, sortDrawable, 0)
            sortText.setText(category.sortRes())
            expandImage.setImageResource(
                if (category.isHidden) R.drawable.ic_expand_more_24dp
                else R.drawable.ic_expand_less_24dp)
            when {
                adapter.mode == SelectableAdapter.Mode.MULTI -> {
                    checkboxImage.visible()
                    expandImage.invisible()
                    updateButton.gone()
                    catProgress.gone()
                    setSelection()
                }
                category.id == -1 -> {
                    expandImage.gone()
                    checkboxImage.gone()
                    updateButton.gone()
                }
                LibraryUpdateService.categoryInQueue(category.id) -> {
                    expandImage.visibleIf(!adapter.isSingleCategory)
                    checkboxImage.gone()
                    catProgress.visible()
                    updateButton.invisible()
                }
                else -> {
                    expandImage.visibleIf(!adapter.isSingleCategory)
                    catProgress.gone()
                    checkboxImage.gone()
                    updateButton.visibleIf(category.id ?: 0 > -1 && !adapter.isSingleCategory)
                }
            }
        }

        private fun addCategoryToUpdate() {
            if (adapter.libraryListener.updateCategory(adapterPosition)) {
                catProgress.visible()
                updateButton.invisible()
            }
        }

        @SuppressLint("RestrictedApi")
        private fun showCatSortOptions() {
            val category =
                (adapter.getItem(adapterPosition) as? LibraryHeaderItem)?.category ?: return
            // Create a PopupMenu, giving it the clicked view for an anchor
            val popup = PopupMenu(itemView.context, sortText)

            // Inflate our menu resource into the PopupMenu's Menu
            popup.menuInflater.inflate(
                if (category.id == -1) R.menu.main_sort
                else R.menu.cat_sort, popup.menu)

            // Set a listener so we are notified if a menu item is clicked
            popup.setOnMenuItemClickListener { menuItem ->
                onCatSortClicked(category, menuItem.itemId)
                true
            }

            val sortingMode = category.sortingMode()
            val currentItem = if (sortingMode == null) null
            else popup.menu.findItem(
                when (sortingMode) {
                    LibrarySort.DRAG_AND_DROP -> R.id.action_drag_and_drop
                    LibrarySort.TOTAL -> R.id.action_total_chaps
                    LibrarySort.LAST_READ -> R.id.action_last_read
                    LibrarySort.UNREAD -> R.id.action_unread
                    LibrarySort.LATEST_CHAPTER -> R.id.action_update
                    LibrarySort.DATE_ADDED -> R.id.action_date_added
                    else -> R.id.action_alpha
                }
            )

            if (category.id == -1)
            popup.menu.findItem(R.id.action_drag_and_drop).title = contentView.context.getString(
                R.string.category
            )

            if (sortingMode != null && popup.menu is MenuBuilder) {
                val m = popup.menu as MenuBuilder
                m.setOptionalIconsVisible(true)
            }

            val isAscending = category.isAscending()

            currentItem?.icon = tintVector(
                when {
                    sortingMode == LibrarySort.DRAG_AND_DROP -> R.drawable.ic_check_white_24dp
                    if (sortingMode == LibrarySort.DATE_ADDED ||
                        sortingMode == LibrarySort.LATEST_CHAPTER ||
                        sortingMode == LibrarySort.LAST_READ) !isAscending else isAscending ->
                        R.drawable.ic_arrow_down_white_24dp
                    else -> R.drawable.ic_arrow_up_white_24dp
                }
            )
            val s = SpannableString(currentItem?.title ?: "")
            s.setSpan(ForegroundColorSpan(itemView.context.getResourceColor(android.R.attr.colorAccent)), 0, s.length, 0)
            currentItem?.title = s

            // Finally show the PopupMenu
            popup.show()
        }

        private fun tintVector(resId: Int): Drawable? {
            return ContextCompat.getDrawable(itemView.context, resId)?.mutate()?.apply {
                setTint(itemView.context.getResourceColor(android.R.attr.colorAccent))
            }
        }

        private fun onCatSortClicked(category: Category, menuId: Int?) {
            val modType = if (menuId == null) {
                val t = (category.mangaSort?.minus('a') ?: 0) + 1
                if (t % 2 != 0) t + 1
                else t - 1
            } else {
                val order = when (menuId) {
                    R.id.action_drag_and_drop -> {
                        adapter.libraryListener.sortCategory(category.id!!, 'D' - 'a' + 1)
                        return
                    }
                    R.id.action_date_added -> 5
                    R.id.action_total_chaps -> 4
                    R.id.action_last_read -> 3
                    R.id.action_unread -> 2
                    R.id.action_update -> 1
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
                if (allSelected) R.drawable.ic_check_circle_white_24dp else R.drawable.ic_radio_button_unchecked_white_24dp
            )
            val tintedDrawable = drawable?.mutate()
            tintedDrawable?.setTint(
                ContextCompat.getColor(
                    contentView.context, if (allSelected) R.color.colorAccent
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
}
