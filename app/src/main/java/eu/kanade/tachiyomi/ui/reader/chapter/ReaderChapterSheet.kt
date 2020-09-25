package eu.kanade.tachiyomi.ui.reader.chapter

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.listeners.ClickEventHook
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.ReaderPresenter
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.view.collapse
import eu.kanade.tachiyomi.util.view.expand
import eu.kanade.tachiyomi.util.view.isExpanded
import eu.kanade.tachiyomi.util.view.visInvisIf
import eu.kanade.tachiyomi.util.view.visibleIf
import kotlinx.android.synthetic.main.reader_chapters_sheet.view.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ReaderChapterSheet @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    LinearLayout(context, attrs) {

    var sheetBehavior: BottomSheetBehavior<View>? = null
    lateinit var presenter: ReaderPresenter
    var adapter: FastAdapter<ReaderChapterItem>? = null
    private val itemAdapter = ItemAdapter<ReaderChapterItem>()
    var shouldCollapse = true
    var selectedChapterId = -1L

    fun setup(activity: ReaderActivity) {
        presenter = activity.presenter
        val fullPrimary = activity.getResourceColor(R.attr.colorSecondary)
        val primary = ColorUtils.setAlphaComponent(fullPrimary, 200)

        sheetBehavior = BottomSheetBehavior.from(this)
        chapters_button.setOnClickListener {
            if (sheetBehavior.isExpanded()) {
                sheetBehavior?.collapse()
            } else {
                sheetBehavior?.expand()
            }
        }

        webview_button.setOnClickListener {
            activity.openMangaInBrowser()
        }

        post {
            chapter_recycler.alpha = if (sheetBehavior.isExpanded()) 1f else 0f
            chapter_recycler.isClickable = sheetBehavior.isExpanded()
            chapter_recycler.isFocusable = sheetBehavior.isExpanded()
        }

        sheetBehavior?.addBottomSheetCallback(
            object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onSlide(bottomSheet: View, progress: Float) {
                    pill.alpha = (1 - max(0f, progress)) * 0.25f
                    val trueProgress = max(progress, 0f)
                    chapters_button.alpha = 1 - trueProgress
                    webview_button.alpha = trueProgress
                    webview_button.visibleIf(webview_button.alpha > 0)
                    chapters_button.visInvisIf(chapters_button.alpha > 0)
                    backgroundTintList =
                        ColorStateList.valueOf(lerpColor(primary, fullPrimary, trueProgress))
                    chapter_recycler.alpha = trueProgress
                    if (activity.sheetManageNavColor && progress > 0f) {
                        activity.window.navigationBarColor =
                            lerpColor(ColorUtils.setAlphaComponent(primary, 0), primary, trueProgress)
                    }
                }

                override fun onStateChanged(p0: View, state: Int) {
                    if (state == BottomSheetBehavior.STATE_COLLAPSED) {
                        shouldCollapse = true
                        sheetBehavior?.isHideable = false
                        (chapter_recycler.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
                            adapter?.getPosition(presenter.getCurrentChapter()?.chapter?.id ?: 0L) ?: 0,
                            chapter_recycler.height / 2 - 30.dpToPx
                        )
                        chapters_button.alpha = 1f
                        webview_button.alpha = 0f
                    }
                    if (state == BottomSheetBehavior.STATE_EXPANDED) {
                        chapter_recycler.alpha = 1f
                        chapters_button.alpha = 0f
                        webview_button.alpha = 1f
                        if (activity.sheetManageNavColor) activity.window.navigationBarColor = primary
                    }
                    chapter_recycler.isClickable = state == BottomSheetBehavior.STATE_EXPANDED
                    chapter_recycler.isFocusable = state == BottomSheetBehavior.STATE_EXPANDED
                    webview_button.visibleIf(state != BottomSheetBehavior.STATE_COLLAPSED)
                    chapters_button.visInvisIf(state != BottomSheetBehavior.STATE_EXPANDED)
                }
            }
        )

        adapter = FastAdapter.with(itemAdapter)
        chapter_recycler.adapter = adapter
        adapter?.onClickListener = { _, _, item, _ ->
            if (!sheetBehavior.isExpanded()) {
                false
            } else {
                if (item.chapter.id != presenter.getCurrentChapter()?.chapter?.id) {
                    shouldCollapse = false
                    presenter.loadChapter(item.chapter)
                }
                true
            }
        }
        adapter?.addEventHook(
            object : ClickEventHook<ReaderChapterItem>() {
                override fun onBind(viewHolder: RecyclerView.ViewHolder): View? {
                    return if (viewHolder is ReaderChapterItem.ViewHolder) {
                        viewHolder.bookmarkButton
                    } else {
                        null
                    }
                }

                override fun onClick(
                    v: View,
                    position: Int,
                    fastAdapter: FastAdapter<ReaderChapterItem>,
                    item: ReaderChapterItem
                ) {
                    presenter.toggleBookmark(item.chapter)
                    refreshList()
                }
            }
        )

        backgroundTintList = ColorStateList.valueOf(
            if (!sheetBehavior.isExpanded()) primary
            else fullPrimary
        )

        chapter_recycler.layoutManager = LinearLayoutManager(context)
        refreshList()
    }

    fun refreshList() {
        launchUI {
            val chapters = presenter.getChapters()

            selectedChapterId = chapters.find { it.isCurrent }?.chapter?.id ?: -1L
            itemAdapter.clear()
            itemAdapter.add(chapters)

            (chapter_recycler.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
                adapter?.getPosition(presenter.getCurrentChapter()?.chapter?.id ?: 0L) ?: 0,
                chapter_recycler.height / 2 - 30.dpToPx
            )
        }
    }

    fun lerpColor(colorStart: Int, colorEnd: Int, percent: Float): Int {
        val perc = (percent * 100).roundToInt()
        return Color.argb(
            lerpColorCalc(Color.alpha(colorStart), Color.alpha(colorEnd), perc),
            lerpColorCalc(Color.red(colorStart), Color.red(colorEnd), perc),
            lerpColorCalc(Color.green(colorStart), Color.green(colorEnd), perc),
            lerpColorCalc(Color.blue(colorStart), Color.blue(colorEnd), perc)
        )
    }

    fun lerpColorCalc(colorStart: Int, colorEnd: Int, percent: Int): Int {
        return (
            min(colorStart, colorEnd) * (100 - percent) + max(
                colorStart,
                colorEnd
            ) * percent
            ) / 100
    }
}
