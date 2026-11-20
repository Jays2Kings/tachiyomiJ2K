package eu.kanade.tachiyomi.ui.reader.chapter

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.view.ViewParent
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import androidx.core.view.children
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.listeners.ClickEventHook
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.ReaderChaptersSheetBinding
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.view.backgroundColor
import eu.kanade.tachiyomi.util.view.collapse
import eu.kanade.tachiyomi.util.view.expand
import eu.kanade.tachiyomi.util.view.isCollapsed
import eu.kanade.tachiyomi.util.view.isExpanded
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ReaderChapterSheet
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : LinearLayout(context, attrs) {
        var sheetBehavior: BottomSheetBehavior<View>? = null
        lateinit var viewModel: ReaderViewModel
        var adapter: FastAdapter<ReaderChapterItem>? = null
        private val itemAdapter = ItemAdapter<ReaderChapterItem>()
        var selectedChapterId = -1L

        var loadingPos = 0
        lateinit var binding: ReaderChaptersSheetBinding
        var lastScale = 1f

        override fun onFinishInflate() {
            super.onFinishInflate()
            binding = ReaderChaptersSheetBinding.bind(this)
        }

        fun setup(activity: ReaderActivity) {
            viewModel = activity.viewModel
            val fullPrimary = activity.getResourceColor(R.attr.colorSurface)
            val primary = ColorUtils.setAlphaComponent(fullPrimary, 200)
            val navPrimary =
                ColorUtils.setAlphaComponent(
                    fullPrimary,
                    200,
                )
            sheetBehavior = BottomSheetBehavior.from(this)
            binding.chaptersButton.setOnClickListener {
                if (sheetBehavior.isExpanded()) {
                    sheetBehavior?.collapse()
                } else {
                    sheetBehavior?.expand()
                }
            }

            // Listener to show/hide the chapter sheet just be going down on a d-pad/arrow keys
            viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus ->
                if (!activity.menuVisible || newFocus == null || isInTouchMode) return@addOnGlobalFocusChangeListener
                if (sheetBehavior?.state == BottomSheetBehavior.STATE_SETTLING ||
                    sheetBehavior?.state == BottomSheetBehavior.STATE_DRAGGING
                ) {
                    return@addOnGlobalFocusChangeListener
                }
                when {
                    newFocus.isOrDescendantOf(binding.chapterRecycler) -> {
                        if (sheetBehavior?.isCollapsed() == true) {
                            sheetBehavior?.expand()
                        }
                    }
                    newFocus.isOrDescendantOf(binding.topbarLayout) -> {
                        if (sheetBehavior?.isExpanded() == true) {
                            sheetBehavior?.collapse()
                        }
                    }
                }
            }

            post {
                binding.chapterRecycler.alpha = if (sheetBehavior.isExpanded()) 1f else 0f
                binding.chapterRecycler.isInvisible = !sheetBehavior.isExpanded()
                binding.chapterRecycler.isClickable = sheetBehavior.isExpanded()
                val canShowNav = (viewModel.getCurrentChapter()?.pages?.size ?: 1) > 1
                if (canShowNav) {
                    activity.binding.readerNav.root.isVisible = sheetBehavior.isCollapsed()
                }
            }

            sheetBehavior?.addBottomSheetCallback(
                object : BottomSheetBehavior.BottomSheetCallback() {
                    override fun onSlide(
                        bottomSheet: View,
                        progress: Float,
                    ) {
                        activity.binding.touchView.isVisible = progress > 0f
                        binding.root.isVisible = true
                        binding.pill.alpha = (1 - max(0f, progress)) * 0.25f
                        val trueProgress = max(progress, 0f)
                        activity.binding.readerNav.root.alpha = (1 - abs(progress)).coerceIn(0f, 1f)
                        backgroundTintList =
                            ColorStateList.valueOf(lerpColor(primary, fullPrimary, trueProgress))
                        binding.chapterRecycler.alpha = trueProgress
                        binding.chapterRecycler.isInvisible = progress <= 0f
                        if (activity.sheetManageNavColor && progress > 0f) {
                            activity.binding.navBar.backgroundColor =
                                lerpColor(ColorUtils.setAlphaComponent(navPrimary, 0), navPrimary, trueProgress)
                        }
                        if (lastScale != 1f && scaleY != 1f) {
                            val scaleProgress = ((1f - progress) * (1f - lastScale)) + lastScale
                            scaleX = scaleProgress
                            scaleY = scaleProgress
                            for (i in 0 until childCount) {
                                val childView = getChildAt(i)
                                childView.scaleY = scaleProgress
                            }
                        }
                    }

                    override fun onStateChanged(
                        p0: View,
                        state: Int,
                    ) {
                        val canShowNav = (viewModel.getCurrentChapter()?.pages?.size ?: 1) > 1
                        if (state == BottomSheetBehavior.STATE_COLLAPSED) {
                            activity.binding.touchView.isVisible = false
                            sheetBehavior?.isHideable = false
                            if (canShowNav) {
                                activity.binding.readerNav.root.isVisible = true
                            }
                            activity.binding.readerNav.root.alpha = 1f
                            binding.chapterRecycler.alpha = 0f
                            binding.chapterRecycler.isInvisible = true
                        }
                        if (state == BottomSheetBehavior.STATE_DRAGGING || state == BottomSheetBehavior.STATE_SETTLING) {
                            if (canShowNav) {
                                activity.binding.readerNav.root.isVisible = true
                            }
                        }
                        if (state == BottomSheetBehavior.STATE_EXPANDED) {
                            activity.binding.touchView.isVisible = true
                            binding.chapterRecycler.isInvisible = false
                            binding.chapterRecycler.alpha = 1f
                            (binding.chapterRecycler.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
                                adapter?.getPosition(viewModel.getCurrentChapter()?.chapter?.id ?: 0L)
                                    ?: 0,
                                binding.chapterRecycler.height / 2 - 30.dpToPx,
                            )
                            if (canShowNav) {
                                activity.binding.readerNav.root.isInvisible = true
                            }
                            activity.binding.readerNav.root.alpha = 0f
                            if (activity.sheetManageNavColor) {
                                activity.binding.navBar.backgroundColor =
                                    navPrimary
                            }
                        }
                        if (state == BottomSheetBehavior.STATE_HIDDEN) {
                            activity.binding.touchView.isVisible = false
                            activity.binding.readerNav.root.alpha = 0f
                            if (canShowNav) {
                                activity.binding.readerNav.root.isInvisible = true
                            }
                            binding.chapterRecycler.alpha = 0f
                            binding.chapterRecycler.isInvisible = true
                            binding.root.isInvisible = true
                        } else if (binding.root.isVisible) {
                            binding.root.isVisible = true
                        }
                        binding.chapterRecycler.isClickable =
                            state == BottomSheetBehavior.STATE_EXPANDED
                        activity.reEnableBackPressedCallBack()

                        if ((
                                state == BottomSheetBehavior.STATE_COLLAPSED ||
                                    state == BottomSheetBehavior.STATE_EXPANDED ||
                                    state == BottomSheetBehavior.STATE_HIDDEN
                            ) &&
                            scaleY != 1f
                        ) {
                            scaleX = 1f
                            scaleY = 1f
                            pivotY = 0f
                            translationX = 0f
                            for (i in 0 until childCount) {
                                val childView = getChildAt(i)
                                childView.scaleY = 1f
                            }
                            lastScale = 1f
                        }
                    }
                },
            )

            adapter = FastAdapter.with(itemAdapter)
            binding.chapterRecycler.adapter = adapter
            adapter?.onClickListener = { _, _, item, position ->
                if (!sheetBehavior.isExpanded() || activity.isLoading) {
                    false
                } else {
                    if (item.chapter.id != viewModel.getCurrentChapter()?.chapter?.id) {
                        activity.binding.readerNav.leftChapter.isInvisible = true
                        activity.binding.readerNav.rightChapter.isInvisible = true
                        activity.isScrollingThroughPagesOrChapters = true

                        loadingPos = position
                        val itemView =
                            (
                                binding.chapterRecycler.findViewHolderForAdapterPosition(
                                    position,
                                ) as? ReaderChapterItem.ViewHolder
                            )?.binding
                        itemView?.bookmarkImage?.isVisible = false
                        itemView?.progress?.isVisible = true
                        activity.lifecycleScope.launch {
                            activity.loadChapter(item.chapter)
                        }
                    }
                    true
                }
            }
            adapter?.addEventHook(
                object : ClickEventHook<ReaderChapterItem>() {
                    override fun onBind(viewHolder: RecyclerView.ViewHolder): View? =
                        if (viewHolder is ReaderChapterItem.ViewHolder) {
                            viewHolder.binding.bookmarkButton
                        } else {
                            null
                        }

                    override fun onClick(
                        v: View,
                        position: Int,
                        fastAdapter: FastAdapter<ReaderChapterItem>,
                        item: ReaderChapterItem,
                    ) {
                        if (!activity.isLoading && sheetBehavior.isExpanded()) {
                            viewModel.toggleBookmark(item.chapter)
                            refreshList()
                        }
                    }
                },
            )

            backgroundTintList =
                ColorStateList.valueOf(
                    if (!sheetBehavior.isExpanded()) {
                        primary
                    } else {
                        fullPrimary
                    },
                )

            binding.chapterRecycler.addOnScrollListener(
                object : RecyclerView.OnScrollListener() {
                    override fun onScrollStateChanged(
                        recyclerView: RecyclerView,
                        newState: Int,
                    ) {
                        super.onScrollStateChanged(recyclerView, newState)
                        if (newState == RecyclerView.SCROLL_STATE_IDLE ||
                            newState == RecyclerView.SCROLL_STATE_SETTLING
                        ) {
                            sheetBehavior?.isDraggable = true
                        } else {
                            sheetBehavior?.isDraggable = !recyclerView.canScrollVertically(-1)
                        }
                    }
                },
            )

            binding.chapterRecycler.layoutManager = LinearLayoutManager(context)
            refreshList()
        }

        fun resetChapter() {
            val itemView = (binding.chapterRecycler.findViewHolderForAdapterPosition(loadingPos) as? ReaderChapterItem.ViewHolder)?.binding
            itemView?.bookmarkImage?.isVisible = true
            itemView?.progress?.isVisible = false
        }

        fun focusCurrentChapter() {
            if (isInTouchMode) return
            val position = adapter?.getPosition(viewModel.getCurrentChapter()?.chapter?.id ?: 0L) ?: 0
            binding.chapterRecycler.post {
                binding.chapterRecycler
                    .findViewHolderForAdapterPosition(position)
                    ?.itemView
                    ?.requestFocus()
            }
        }

        fun focusFirstReaderBottomButton() {
            binding.buttonGroup.children
                .firstOrNull { it.isVisible }
                ?.requestFocus()
        }

        fun refreshList() {
            launchUI {
                val chapters = viewModel.getChapters()

                selectedChapterId = chapters.find { it.isCurrent }?.chapter?.id ?: -1L
                itemAdapter.clear()
                itemAdapter.add(chapters)

                (binding.chapterRecycler.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
                    adapter?.getPosition(viewModel.getCurrentChapter()?.chapter?.id ?: 0L) ?: 0,
                    binding.chapterRecycler.height / 2 - 30.dpToPx,
                )
            }
        }

        fun lerpColor(
            colorStart: Int,
            colorEnd: Int,
            percent: Float,
        ): Int {
            val perc = (percent * 100).roundToInt()
            return Color.argb(
                lerpColorCalc(Color.alpha(colorStart), Color.alpha(colorEnd), perc),
                lerpColorCalc(Color.red(colorStart), Color.red(colorEnd), perc),
                lerpColorCalc(Color.green(colorStart), Color.green(colorEnd), perc),
                lerpColorCalc(Color.blue(colorStart), Color.blue(colorEnd), perc),
            )
        }

        fun lerpColorCalc(
            colorStart: Int,
            colorEnd: Int,
            percent: Int,
        ): Int =
            (
                min(colorStart, colorEnd) * (100 - percent) + max(
                    colorStart,
                    colorEnd,
                ) * percent
            ) / 100
    }

/**
 * Whether this view is [ancestor] itself, or nested somewhere underneath it.
 */
private fun View.isOrDescendantOf(ancestor: View): Boolean {
    if (this === ancestor) return true
    var current: ViewParent? = parent
    while (current != null) {
        if (current === ancestor) return true
        current = current.parent
    }
    return false
}
