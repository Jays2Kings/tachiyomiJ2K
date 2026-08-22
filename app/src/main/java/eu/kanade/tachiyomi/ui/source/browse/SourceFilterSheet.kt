package eu.kanade.tachiyomi.ui.source.browse

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePaddingRelative
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.data.database.models.SavedSearch
import eu.kanade.tachiyomi.databinding.SourceFilterSheetBinding
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.view.checkHeightThen
import eu.kanade.tachiyomi.util.view.collapse
import eu.kanade.tachiyomi.util.view.doOnApplyWindowInsetsCompat
import eu.kanade.tachiyomi.widget.StickyFooterBottomSheetDialog

class SourceFilterSheet(
    val activity: Activity,
) : StickyFooterBottomSheetDialog<SourceFilterSheetBinding>(activity) {
    private var filterChanged = true

    val adapter: FlexibleAdapter<IFlexible<*>> =
        FlexibleAdapter<IFlexible<*>>(null)
            .setDisplayHeadersAtStartUp(true)

    var onSearchClicked = {}

    var onResetClicked = {}

    var onSaveClicked = {}

    var onSavedSearchClicked: (SavedSearch) -> Unit = {}

    var onSavedSearchLongClicked: (SavedSearch) -> Unit = {}

    override var recyclerView: RecyclerView? = binding.filtersRecycler

    override fun createBinding(inflater: LayoutInflater) = SourceFilterSheetBinding.inflate(inflater)

    override val stickyFooterView: View
        get() = binding.titleLayout

    init {
        binding.searchBtn.setOnClickListener { dismiss() }
        binding.resetBtn.setOnClickListener { onResetClicked() }
        binding.saveBtn.setOnClickListener { onSaveClicked() }

        sheetBehavior.peekHeight = 450.dpToPx
        sheetBehavior.collapse()

        binding.titleLayout.checkHeightThen {
            val insetTop = (activity as? MainActivity)?.cachedSystemInsets?.top ?: 0
            setCardViewMax(insetTop)
        }

        binding.cardView.doOnApplyWindowInsetsCompat { _, insets, _ ->
            binding.cardView.updateLayoutParams<ConstraintLayout.LayoutParams> {
                val fullHeight = activity.window.decorView.height
                matchConstraintMaxHeight =
                    fullHeight - insets.getInsets(systemBars()).top -
                    binding.titleLayout.height - 75.dpToPx
            }
        }

        val attrsArray = intArrayOf(android.R.attr.actionBarSize)
        val array = context.obtainStyledAttributes(attrsArray)
        val headerHeight = array.getDimensionPixelSize(0, 0)
        array.recycle()
        binding.root.doOnApplyWindowInsetsCompat { _, insets, _ ->
            binding.titleLayout.updatePaddingRelative(
                bottom = insets.getInsets(systemBars()).bottom,
            )
            binding.titleLayout.updateLayoutParams<ConstraintLayout.LayoutParams> {
                height = headerHeight + binding.titleLayout.paddingBottom
            }
            setCardViewMax(insets.getInsets(systemBars()).top)
        }

        binding.filtersRecycler.viewTreeObserver.addOnScrollChangedListener {
            updateStickyFooterPosition()
        }

        binding.filtersRecycler.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        binding.filtersRecycler.clipToPadding = false
        binding.filtersRecycler.adapter = adapter
        binding.filtersRecycler.setHasFixedSize(false)
    }

    fun setCardViewMax(topInset: Int) {
        val fullHeight = activity.window.decorView.height
        val newHeight =
            fullHeight - topInset -
                binding.titleLayout.height - 75.dpToPx
        if ((binding.cardView.layoutParams as ConstraintLayout.LayoutParams).matchConstraintMaxHeight != newHeight) {
            binding.cardView.updateLayoutParams<ConstraintLayout.LayoutParams> {
                matchConstraintMaxHeight = newHeight
            }
        }
    }

    override fun onStart() {
        super.onStart()
        sheetBehavior.collapse()
        updateStickyFooterPosition()
        binding.root.post {
            updateStickyFooterPosition()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val attrsArray = intArrayOf(android.R.attr.actionBarSize)
        val array = context.obtainStyledAttributes(attrsArray)
        val headerHeight = array.getDimensionPixelSize(0, 0)
        binding.titleLayout.updatePaddingRelative(
            bottom = (activity as? MainActivity)?.cachedSystemInsets?.bottom ?: 0,
        )

        binding.titleLayout.updateLayoutParams<ConstraintLayout.LayoutParams> {
            height = headerHeight + binding.titleLayout.paddingBottom
        }
        array.recycle()
    }

    override fun dismiss() {
        super.dismiss()
        if (filterChanged) {
            onSearchClicked()
        }
    }

    fun setFilters(items: List<IFlexible<*>>) {
        items.filterIsInstance<SavedSearchesItem>().forEach {
            it.onSavedSearchClicked = { savedSearch ->
                onSavedSearchClicked(savedSearch)
                dismiss()
            }
            it.onSavedSearchLongClicked = onSavedSearchLongClicked
        }
        adapter.updateDataSet(items)
    }
}
