package eu.kanade.tachiyomi.ui.library.display

import android.content.Context
import android.util.AttributeSet
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.LibraryCategoryLayoutBinding
import eu.kanade.tachiyomi.util.bindToPreference
import eu.kanade.tachiyomi.util.lang.withSubtitle
import eu.kanade.tachiyomi.util.system.toInt
import eu.kanade.tachiyomi.widget.BaseLibraryDisplayView
import kotlin.math.min

class LibraryCategoryView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    BaseLibraryDisplayView<LibraryCategoryLayoutBinding>(context, attrs) {

    override fun inflateBinding() = LibraryCategoryLayoutBinding.bind(this)
    override fun initGeneralPreferences() {
        with(binding) {
            showAll.bindToPreference(preferences.showAllCategories()) {
                controller?.presenter?.getLibrary()
                binding.categoryShow.isEnabled = it
            }
            categoryShow.isEnabled = showAll.isChecked
            categoryShow.bindToPreference(preferences.showCategoryInTitle()) {
                controller?.showMiniBar()
            }
            dynamicToBottom.text = context.getString(R.string.move_dynamic_to_bottom)
                .withSubtitle(context, R.string.when_grouping_by_sources_tags)
            dynamicToBottom.bindToPreference(preferences.collapsedDynamicAtBottom()) {
                controller?.presenter?.getLibrary()
            }
            showEmptyCatsFiltering.bindToPreference(preferences.showEmptyCategoriesWhileFiltering()) {
                controller?.presenter?.requestFilterUpdate()
            }
            val hideHopper = min(
                2,
                preferences.hideHopper().get().toInt() * 2 + preferences.autohideHopper().get()
                    .toInt()
            )
            hideHopperSpinner.setSelection(hideHopper)
            hideHopperSpinner.onItemSelectedListener = {
                preferences.hideHopper().set(it == 2)
                preferences.autohideHopper().set(it == 1)
                controller?.hideHopper(it == 2)
                controller?.resetHopperY()
            }
            addCategoriesButton.setOnClickListener {
                controller?.showCategoriesController()
            }
            hopperLongPress.bindToPreference(preferences.hopperLongPressAction())
        }
    }
}
