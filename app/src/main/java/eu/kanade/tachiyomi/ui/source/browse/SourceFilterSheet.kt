package eu.kanade.tachiyomi.ui.source.browse

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.WindowInsets
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.databinding.SourceFilterSheetBinding
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.view.collapse
import eu.kanade.tachiyomi.util.view.doOnApplyWindowInsets
import eu.kanade.tachiyomi.util.view.updatePaddingRelative
import eu.kanade.tachiyomi.widget.E2EBottomSheetDialog

class SourceFilterSheet(val activity: Activity) :
    E2EBottomSheetDialog<SourceFilterSheetBinding>(activity) {

    private var filterChanged = true

    val adapter: FlexibleAdapter<IFlexible<*>> = FlexibleAdapter<IFlexible<*>>(null)
        .setDisplayHeadersAtStartUp(true)

    var onSearchClicked = {}

    var onResetClicked = {}

    override var recyclerView: RecyclerView? = binding.filtersRecycler

    override fun createBinding(inflater: LayoutInflater) = SourceFilterSheetBinding.inflate(inflater)
    init {
        binding.searchBtn.setOnClickListener { dismiss() }
        binding.resetBtn.setOnClickListener { onResetClicked() }

        sheetBehavior.peekHeight = 450.dpToPx
        sheetBehavior.collapse()

        binding.titleLayout.viewTreeObserver.addOnGlobalLayoutListener(object :
                OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    activity.window.decorView.rootWindowInsets?.let {
                        setCardViewMax(it)
                    }
                    if (binding.titleLayout.height > 0) {
                        binding.titleLayout.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    }
                }
            })

        binding.cardView.doOnApplyWindowInsets { _, insets, _ ->
            binding.cardView.updateLayoutParams<ConstraintLayout.LayoutParams> {
                val fullHeight = activity.window.decorView.height
                matchConstraintMaxHeight =
                    fullHeight - insets.systemWindowInsetTop -
                    binding.titleLayout.height - 75.dpToPx
            }
        }

        val attrsArray = intArrayOf(android.R.attr.actionBarSize)
        val array = context.obtainStyledAttributes(attrsArray)
        val headerHeight = array.getDimensionPixelSize(0, 0)
        array.recycle()
        binding.root.doOnApplyWindowInsets { _, insets, _ ->
            binding.titleLayout.updatePaddingRelative(
                bottom = insets.systemWindowInsetBottom
            )
            binding.titleLayout.updateLayoutParams<ConstraintLayout.LayoutParams> {
                height = headerHeight + binding.titleLayout.paddingBottom
            }
            setCardViewMax(insets)
        }

        (binding.root.parent.parent as? View)?.viewTreeObserver?.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                updateBottomButtons()
                if (sheetBehavior.state != BottomSheetBehavior.STATE_COLLAPSED) {
                    (binding.root.parent.parent as? View)?.viewTreeObserver?.removeOnGlobalLayoutListener(this)
                }
            }
        })

        binding.filtersRecycler.viewTreeObserver.addOnScrollChangedListener {
            updateBottomButtons()
        }

        setOnShowListener {
            updateBottomButtons()
        }

        binding.filtersRecycler.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        binding.filtersRecycler.clipToPadding = false
        binding.filtersRecycler.adapter = adapter
        binding.filtersRecycler.setHasFixedSize(false)

        sheetBehavior.addBottomSheetCallback(
            object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onSlide(bottomSheet: View, progress: Float) {
                    updateBottomButtons()
                }

                override fun onStateChanged(p0: View, state: Int) {
                    updateBottomButtons()
                }
            }
        )
    }

    fun setCardViewMax(insets: WindowInsets) {
        val fullHeight = activity.window.decorView.height
        val newHeight = fullHeight - insets.systemWindowInsetTop -
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
        updateBottomButtons()
        binding.root.post {
            updateBottomButtons()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val attrsArray = intArrayOf(android.R.attr.actionBarSize)
        val array = context.obtainStyledAttributes(attrsArray)
        val headerHeight = array.getDimensionPixelSize(0, 0)
        binding.titleLayout.updatePaddingRelative(
            bottom = activity.window.decorView.rootWindowInsets.systemWindowInsetBottom
        )

        binding.titleLayout.updateLayoutParams<ConstraintLayout.LayoutParams> {
            height = headerHeight + binding.titleLayout.paddingBottom
        }
        array.recycle()
    }

    private fun updateBottomButtons() {
        val bottomSheet = binding.root.parent as View
        val bottomSheetVisibleHeight = -bottomSheet.top + (activity.window.decorView.height - bottomSheet.height)

        binding.titleLayout.translationY = bottomSheetVisibleHeight.toFloat()
    }

    override fun dismiss() {
        super.dismiss()
        if (filterChanged) {
            onSearchClicked()
        }
    }

    fun setFilters(items: List<IFlexible<*>>) {
        adapter.updateDataSet(items)
    }
}
