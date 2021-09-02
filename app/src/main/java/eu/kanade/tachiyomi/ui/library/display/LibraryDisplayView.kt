package eu.kanade.tachiyomi.ui.library.display

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import com.google.android.material.slider.Slider
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.LibraryDisplayLayoutBinding
import eu.kanade.tachiyomi.ui.library.filter.FilterBottomSheet
import eu.kanade.tachiyomi.ui.library.filter.ManageFilterItem
import eu.kanade.tachiyomi.util.bindToPreference
import eu.kanade.tachiyomi.util.lang.withSubtitle
import eu.kanade.tachiyomi.util.view.checkHeightThen
import eu.kanade.tachiyomi.util.view.rowsForValue
import eu.kanade.tachiyomi.widget.BaseLibraryDisplayView
import kotlin.math.roundToInt

class LibraryDisplayView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    BaseLibraryDisplayView<LibraryDisplayLayoutBinding>(context, attrs) {

    var mainView: View? = null
    override fun inflateBinding() = LibraryDisplayLayoutBinding.bind(this)
    override fun initGeneralPreferences() {
        binding.displayGroup.bindToPreference(preferences.libraryLayout())
        binding.uniformGrid.bindToPreference(preferences.uniformGrid())
        binding.gridSeekbar.value = ((preferences.gridSize().get() + .5f) * 2f).roundToInt().toFloat()
        binding.gridSeekbar.isTickVisible = false
        binding.resetGridSize.setOnClickListener {
            binding.gridSeekbar.value = 3f
        }

        binding.reorderFiltersButton.setOnClickListener {
            val recycler = RecyclerView(context)
            var filterOrder = preferences.filterOrder().get()
            if (filterOrder.count() != 6) {
                filterOrder = FilterBottomSheet.Filters.DEFAULT_ORDER
            }
            val adapter = FlexibleAdapter(
                filterOrder.toCharArray().map {
                    if (FilterBottomSheet.Filters.filterOf(it) != null) ManageFilterItem(it)
                    else null
                }.filterNotNull(),
                this,
                true
            )
            recycler.layoutManager = LinearLayoutManager(context)
            recycler.adapter = adapter
            adapter.isHandleDragEnabled = true
            adapter.isLongPressDragEnabled = true
            MaterialDialog(context).title(R.string.reorder_filters)
                .customView(view = recycler, scrollable = false)
                .negativeButton(android.R.string.cancel)
                .positiveButton(R.string.reorder) {
                    val order = adapter.currentItems.map { it.char }.joinToString("")
                    preferences.filterOrder().set(order)
                    recycler.adapter = null
                }
                .show()
        }

        binding.gridSeekbar.setLabelFormatter {
            (mainView ?: this@LibraryDisplayView).rowsForValue(it).toString()
        }
        binding.gridSeekbar.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) {
                preferences.gridSize().set((value / 2f) - .5f)
            }
            setGridText(value)
        }
        binding.gridSeekbar.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}

            override fun onStopTrackingTouch(slider: Slider) {
                preferences.gridSize().set((slider.value / 2f) - .5f)
                setGridText(slider.value)
            }
        })
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        checkHeightThen {
            setGridText(binding.gridSeekbar.value)
        }
    }

    private fun setGridText(progress: Float) {
        with(binding.gridSizeText) {
            val rows = (mainView ?: this@LibraryDisplayView).rowsForValue(progress)
            val titleText = context.getString(R.string.grid_size)
            val subtitleText = context.getString(R.string._per_row, rows)
            text = titleText.withSubtitle(context, subtitleText)
        }
    }
}
