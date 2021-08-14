package eu.kanade.tachiyomi.ui.library

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.util.TypedValue
import android.view.View
import androidx.annotation.DrawableRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.github.florent37.viewtooltip.ViewTooltip
import eu.davidea.flexibleadapter.SelectableAdapter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.library.LibraryUpdateService
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.LibraryCategoryHeaderItemBinding
import eu.kanade.tachiyomi.source.icon
import eu.kanade.tachiyomi.ui.base.MaterialMenuSheet
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class LibraryHeaderHolder(val view: View, private val adapter: LibraryCategoryAdapter) :
    BaseFlexibleViewHolder(view, adapter, true) {

    private val binding = LibraryCategoryHeaderItemBinding.bind(view)
    init {
        binding.categoryHeaderLayout.setOnClickListener { toggleCategory() }
        binding.updateButton.setOnClickListener { addCategoryToUpdate() }
        binding.categoryTitle.setOnLongClickListener {
            val category = (adapter.getItem(flexibleAdapterPosition) as? LibraryHeaderItem)?.category
            adapter.libraryListener.manageCategory(flexibleAdapterPosition)
            category?.isDynamic == false
        }
        binding.categoryTitle.setOnClickListener { toggleCategory() }
        binding.categorySort.setOnClickListener { it.post { showCatSortOptions() } }
        binding.checkbox.setOnClickListener { selectAll() }
        binding.updateButton.drawable.mutate()
    }

    private fun toggleCategory() {
        adapter.libraryListener.toggleCategoryVisibility(flexibleAdapterPosition)
        val tutorial = Injekt.get<PreferencesHelper>().shownLongPressCategoryTutorial()
        if (!tutorial.get()) {
            ViewTooltip.on(itemView.context as? Activity, binding.categoryTitle).autoHide(true, 5000L)
                .align(ViewTooltip.ALIGN.START).position(ViewTooltip.Position.TOP)
                .text(R.string.long_press_category)
                .color(itemView.context.getResourceColor(R.attr.colorSecondary))
                .textSize(TypedValue.COMPLEX_UNIT_SP, 15f).textColor(Color.WHITE)
                .withShadow(false).corner(30).arrowWidth(15).arrowHeight(15).distanceWithView(0)
                .show()
            tutorial.set(true)
        }
    }

    @SuppressLint("SetTextI18n")
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
        binding.categoryTitle.updateLayoutParams<ConstraintLayout.LayoutParams> {
            topMargin = (
                when {
                    shorterMargin -> 2
                    previousIsCollapsed -> 5
                    else -> 32
                }
                ).dpToPx
        }
        val category = item.category

        binding.categoryTitle.text =
            if (category.isAlone && !category.isDynamic) { "" } else { category.name } +
            if (adapter.showNumber && !category.isHidden) {
                " (${adapter.itemsPerCategory[item.catId]})"
            } else { "" }
        if (category.sourceId != null) {
            val icon = adapter.sourceManager.get(category.sourceId!!)?.icon()
            icon?.setBounds(0, 0, 32.dpToPx, 32.dpToPx)
            binding.categoryTitle.setCompoundDrawablesRelative(icon, null, null, null)
        } else {
            binding.categoryTitle.setCompoundDrawablesRelative(null, null, null, null)
        }

        val isAscending = category.isAscending()
        val sortingMode = category.sortingMode()
        val sortDrawable = getSortRes(sortingMode, isAscending, R.drawable.ic_sort_24dp)

        binding.categorySort.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, sortDrawable, 0)
        binding.categorySort.setText(category.sortRes())
        binding.collapseArrow.setImageResource(
            if (category.isHidden) R.drawable.ic_expand_more_24dp
            else R.drawable.ic_expand_less_24dp
        )
        when {
            adapter.mode == SelectableAdapter.Mode.MULTI -> {
                binding.checkbox.isVisible = !category.isHidden
                binding.collapseArrow.isVisible = category.isHidden && !adapter.isSingleCategory
                binding.updateButton.isVisible = false
                binding.catProgress.isVisible = false
                setSelection()
            }
            category.id ?: -1 < 0 -> {
                binding.collapseArrow.isVisible = false
                binding.checkbox.isVisible = false
                binding.catProgress.isVisible = false
                binding.updateButton.isVisible = false
            }
            LibraryUpdateService.categoryInQueue(category.id) -> {
                binding.collapseArrow.isVisible = !adapter.isSingleCategory
                binding.checkbox.isVisible = false
                binding.catProgress.isVisible = true
                binding.updateButton.isInvisible = true
            }
            else -> {
                binding.collapseArrow.isVisible = !adapter.isSingleCategory
                binding.catProgress.isVisible = false
                binding.checkbox.isVisible = false
                binding.updateButton.isVisible = !adapter.isSingleCategory
            }
        }
    }

    private fun addCategoryToUpdate() {
        if (adapter.libraryListener.updateCategory(flexibleAdapterPosition)) {
            binding.catProgress.isVisible = true
            binding.updateButton.isInvisible = true
        }
    }

    private fun showCatSortOptions() {
        val category =
            (adapter.getItem(flexibleAdapterPosition) as? LibraryHeaderItem)?.category ?: return
        adapter.controller.activity?.let { activity ->
            val items = LibrarySort.values().map { it.menuSheetItem(category.isDynamic) }
            val sortingMode = category.sortingMode(true)
            val sheet = MaterialMenuSheet(
                activity,
                items,
                activity.getString(R.string.sort_by),
                sortingMode?.mainValue
            ) { sheet, item ->
                onCatSortClicked(category, item)
                val nCategory = (adapter.getItem(flexibleAdapterPosition) as? LibraryHeaderItem)?.category
                val isAscending = nCategory?.isAscending() ?: false
                val drawableRes = getSortRes(item, isAscending)
                sheet.setDrawable(item, drawableRes)
                false
            }
            val isAscending = category.isAscending()
            val drawableRes = getSortRes(sortingMode, isAscending)
            sheet.setDrawable(sortingMode?.mainValue ?: -1, drawableRes)
            sheet.show()
        }
    }

    private fun getSortRes(
        sortMode: LibrarySort?,
        isAscending: Boolean,
        @DrawableRes defaultDrawableRes: Int = R.drawable.ic_check_24dp
    ): Int {
        sortMode ?: return defaultDrawableRes
        return when (sortMode) {
            LibrarySort.DragAndDrop -> defaultDrawableRes
            else -> {
                if (if (sortMode.hasInvertedSort) !isAscending else isAscending) {
                    R.drawable.ic_arrow_downward_24dp
                } else {
                    R.drawable.ic_arrow_upward_24dp
                }
            }
        }
    }

    private fun getSortRes(
        sortingMode: Int?,
        isAscending: Boolean,
        @DrawableRes defaultDrawableRes: Int = R.drawable.ic_check_24dp
    ): Int {
        sortingMode ?: return defaultDrawableRes
        return when (val sortMode = LibrarySort.valueOf(sortingMode)) {
            LibrarySort.DragAndDrop -> defaultDrawableRes
            else -> {
                if (if (sortMode?.hasInvertedSort == true) !isAscending else isAscending) {
                    R.drawable.ic_arrow_downward_24dp
                } else {
                    R.drawable.ic_arrow_upward_24dp
                }
            }
        }
    }

    private fun onCatSortClicked(category: Category, menuId: Int?) {
        val modType = if (menuId == null) {
            val sortingMode = category.sortingMode() ?: LibrarySort.Title
            if (category.isAscending()) {
                sortingMode.categoryValueDescending
            } else {
                sortingMode.categoryValue
            }
        } else {
            val sortingMode = LibrarySort.valueOf(menuId) ?: LibrarySort.Title
            if (sortingMode != LibrarySort.DragAndDrop && sortingMode == category.sortingMode()) {
                onCatSortClicked(category, null)
                return
            }
            sortingMode.categoryValue
        }
        adapter.libraryListener.sortCategory(category.id!!, modType)
    }

    private fun selectAll() {
        adapter.libraryListener.selectAll(flexibleAdapterPosition)
    }

    fun setSelection() {
        val allSelected = adapter.libraryListener.allSelected(flexibleAdapterPosition)
        val drawable = ContextCompat.getDrawable(
            contentView.context,
            if (allSelected) R.drawable.ic_check_circle_24dp else R.drawable.ic_radio_button_unchecked_24dp
        )
        val tintedDrawable = drawable?.mutate()
        tintedDrawable?.setTint(
            if (allSelected) contentView.context.getResourceColor(R.attr.colorSecondary)
            else ContextCompat.getColor(contentView.context, R.color.gray_button)
        )
        binding.checkbox.setImageDrawable(tintedDrawable)
    }

    override fun onLongClick(view: View?): Boolean {
        super.onLongClick(view)
        return false
    }
}
