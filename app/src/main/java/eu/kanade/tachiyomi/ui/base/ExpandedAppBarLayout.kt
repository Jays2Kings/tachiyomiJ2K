package eu.kanade.tachiyomi.ui.base

import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.view.View
import android.view.ViewPropertyAnimator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.math.MathUtils
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.marginTop
import androidx.core.view.updateLayoutParams
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.bluelinelabs.conductor.Controller
import com.google.android.material.appbar.AppBarLayout
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.main.FloatingSearchInterface
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.isTablet
import eu.kanade.tachiyomi.util.view.backgroundColor
import eu.kanade.tachiyomi.util.view.isControllerVisible
import eu.kanade.tachiyomi.util.view.setTextColorAlpha
import uy.kohesive.injekt.injectLazy
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ExpandedAppBarLayout@JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    AppBarLayout(context, attrs) {

    var searchToolbar: FloatingToolbar? = null
    var cardFrame: FrameLayout? = null
    var mainToolbar: CenteredToolbar? = null
    var bigTitleView: TextView? = null
    val preferences: PreferencesHelper by injectLazy()
    var bigView: View? = null
    var imageView: ImageView? = null
    var imageLayout: FrameLayout? = null
    private var tabsFrameLayout: FrameLayout? = null
    var mainActivity: MainActivity? = null
    private var isExtraSmall = false
    val useLargeToolbar: Boolean
        get() = preferences.useLargeToolbar().get() && !isExtraSmall

    var compactSearchMode = false

    /** Defines how the toolbar layout should be */
    private var toolbarMode = ToolbarState.EXPANDED
        set(value) {
            field = value
            if (value == ToolbarState.SEARCH_ONLY) {
                mainToolbar?.isGone = true
            } else if (value == ToolbarState.COMPACT) {
                mainToolbar?.alpha = 1f
                mainToolbar?.isVisible = true
            }
            if (value != ToolbarState.EXPANDED) {
                mainToolbar?.translationY = 0f
                y = 0f
            }
        }
    var useTabsInPreLayout = false
    var yAnimator: ViewPropertyAnimator? = null

    /**
     * used to ignore updates to y
     *
     * use only on controller.onViewCreated that asynchronously loads the first set of items
     * and make false once the recycler has items
     */
    var lockYPos = false

    /** A value used to determine the offset needed for a recycler to land just under the smaller toolbar */
    val toolbarDistanceToTop: Int
        get() {
            val tabHeight = if (tabsFrameLayout?.isVisible == true) 48.dpToPx else 0
            return paddingTop - (mainToolbar?.height ?: 0) - tabHeight
        }

    /** A value used to determine the offset needed for a appbar's y to show only the smaller toolbar */
    val yNeededForSmallToolbar: Int
        get() {
            if (toolbarMode != ToolbarState.EXPANDED) return 0
            val tabHeight = if (tabsFrameLayout?.isVisible == true) 48.dpToPx else 0
            return -preLayoutHeight + (mainToolbar?.height ?: 0) + tabHeight
        }

    val attrToolbarHeight: Int = let {
        val attrsArray = intArrayOf(R.attr.mainActionBarSize)
        val array = it.context.obtainStyledAttributes(attrsArray)
        val height = array.getDimensionPixelSize(0, 0)
        array.recycle()
        height
    }

    val preLayoutHeight: Int
        get() = getEstimatedLayout(
            cardFrame?.isVisible == true && toolbarMode == ToolbarState.EXPANDED,
            useTabsInPreLayout,
            toolbarMode == ToolbarState.EXPANDED,
        )

    private val preLayoutHeightWhileSearching: Int
        get() = getEstimatedLayout(
            cardFrame?.isVisible == true && toolbarMode == ToolbarState.EXPANDED,
            useTabsInPreLayout,
            toolbarMode == ToolbarState.EXPANDED,
            true,
        )

    private var dontFullyHideToolbar = false

    /** Small toolbar height + top system insets, same size as a collapsed appbar */
    private val compactAppBarHeight: Float
        get() {
            val appBarHeight = if (mainToolbar?.height ?: 0 > 0) {
                mainToolbar?.height ?: 0
            } else {
                attrToolbarHeight
            }
            return (appBarHeight + paddingTop).toFloat()
        }

    /** Used to restrain how far up the app bar can go up. Tablets stop at the smaller toolbar */
    private val minTabletHeight: Int
        get() {
            val tabHeight = if (tabsFrameLayout?.isVisible == true) 48.dpToPx else 0
            return if (context.isTablet() || (compactSearchMode && toolbarMode == ToolbarState.EXPANDED)) {
                (mainToolbar?.height ?: 0) + paddingTop + tabHeight
            } else {
                0
            }
        }

    enum class ToolbarState {
        EXPANDED,
        COMPACT,
        SEARCH_ONLY,
    }

    fun setToolbarModeBy(controller: Controller?, useSmall: Boolean? = null) {
        toolbarMode = if (useSmall ?: !useLargeToolbar) {
            when {
                controller is FloatingSearchInterface && controller.showFloatingBar() -> {
                    ToolbarState.SEARCH_ONLY
                }
                else -> ToolbarState.COMPACT
            }
        } else {
            when (controller) {
                is SmallToolbarInterface -> {
                    if (controller is FloatingSearchInterface && controller.showFloatingBar()) {
                        ToolbarState.SEARCH_ONLY
                    } else {
                        ToolbarState.COMPACT
                    }
                }
                else -> ToolbarState.EXPANDED
            }
        }
    }

    fun hideBigView(useSmall: Boolean, force: Boolean? = null, setTitleAlpha: Boolean = true) {
        val useSmallAnyway = force ?: (useSmall || !useLargeToolbar)
        bigView?.isGone = useSmallAnyway
        if (useSmallAnyway) {
            mainToolbar?.backgroundColor = null
            if (!setTitleAlpha) return
            mainToolbar?.toolbarTitle?.setTextColorAlpha(255)
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        bigTitleView = findViewById(R.id.big_title)
        searchToolbar = findViewById(R.id.search_toolbar)
        mainToolbar = findViewById(R.id.toolbar)
        bigView = findViewById(R.id.big_toolbar)
        cardFrame = findViewById(R.id.card_frame)
        tabsFrameLayout = findViewById(R.id.tabs_frame_layout)
        imageView = findViewById(R.id.big_icon)
        imageLayout = findViewById(R.id.big_icon_layout)
        shrinkAppBarIfNeeded(resources.configuration)
    }

    fun setTitle(title: CharSequence?, setBigTitle: Boolean) {
        if (setBigTitle) {
            bigTitleView?.text = title
        }
        mainToolbar?.title = title
    }

    override fun setTranslationY(translationY: Float) {
        if (lockYPos) return
        val realHeight = (preLayoutHeightWhileSearching + paddingTop).toFloat()
        val newY = if (dontFullyHideToolbar && !useLargeToolbar) {
            0f
        } else {
            MathUtils.clamp(
                translationY,
                -realHeight + (if (context.isTablet()) minTabletHeight else 0),
                if (compactSearchMode && toolbarMode == ToolbarState.EXPANDED) -realHeight + top + minTabletHeight else 0f,
            )
        }
        super.setTranslationY(newY)
    }

    fun getEstimatedLayout(includeSearchToolbar: Boolean, includeTabs: Boolean, includeLargeToolbar: Boolean, ignoreSearch: Boolean = false): Int {
        val hasLargeToolbar = includeLargeToolbar && useLargeToolbar && (!compactSearchMode || ignoreSearch)
        val appBarHeight = attrToolbarHeight * (if (includeSearchToolbar && hasLargeToolbar) 2 else 1)
        val widthMeasureSpec = MeasureSpec.makeMeasureSpec(resources.displayMetrics.widthPixels, MeasureSpec.AT_MOST)
        val heightMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        bigTitleView?.measure(widthMeasureSpec, heightMeasureSpec)
        val textHeight = max(bigTitleView?.height ?: 0, bigTitleView?.measuredHeight ?: 0) +
            (bigTitleView?.marginTop?.plus(bigView?.paddingBottom ?: 0) ?: 0)
        return appBarHeight + (if (hasLargeToolbar) textHeight else 0) +
            if (includeTabs) 48.dpToPx else 0
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        shrinkAppBarIfNeeded(newConfig)
    }

    /**
     * For smaller devices, update the big view (with the large title) to be a smaller font and
     * less padding
     */
    private fun shrinkAppBarIfNeeded(config: Configuration?) {
        config ?: return
        dontFullyHideToolbar = config.smallestScreenWidthDp > 600
        isExtraSmall = false
        if (config.screenHeightDp < 600) {
            val bigTitleView = bigTitleView ?: return
            isExtraSmall = config.screenWidthDp < 720
            if (isExtraSmall) {
                setToolbarModeBy(null, true)
                return
            }
            val attrs = intArrayOf(R.attr.textAppearanceHeadlineMedium)
            val ta = context.obtainStyledAttributes(attrs)
            val resId = ta.getResourceId(0, 0)
            ta.recycle()
            TextViewCompat.setTextAppearance(bigTitleView, resId)
            bigTitleView.setTextColor(context.getResourceColor(R.attr.actionBarTintColor))
            bigTitleView.updateLayoutParams<MarginLayoutParams> {
                topMargin = 12.dpToPx
            }
            imageView?.updateLayoutParams<MarginLayoutParams> {
                height = 48.dpToPx
                width = 48.dpToPx
            }
            imageLayout?.updateLayoutParams<MarginLayoutParams> {
                height = 48.dpToPx
            }
        }
    }

    /**
     * Update the views in appbar based on its current Y position
     *
     * @param recyclerView used to determine how far it has scrolled down, if it has not scrolled
     * past the app bar's height, match the Y to the recyclerView's offset
     * @param cancelAnim if true, cancel the current snap animation
     */
    fun updateAppBarAfterY(recyclerView: RecyclerView?, cancelAnim: Boolean = true) {
        if (cancelAnim) {
            yAnimator?.cancel()
        }
        if (lockYPos) return
        val offset = recyclerView?.computeVerticalScrollOffset() ?: 0
        val bigHeight = bigView?.height ?: 0
        val realHeight = preLayoutHeightWhileSearching + paddingTop
        val tabHeight = if (tabsFrameLayout?.isVisible == true) 48.dpToPx else 0
        val shortH = if (toolbarMode != ToolbarState.EXPANDED || compactSearchMode) 0f else compactAppBarHeight
        val smallHeight = -realHeight + shortH + tabHeight
        val newY = when {
            // for smaller devices, when search is active, we want to shrink the app bar and never
            // extend it pass the compact state
            toolbarMode == ToolbarState.EXPANDED && compactSearchMode -> {
                MathUtils.clamp(
                    translationY,
                    -realHeight.toFloat() + top + if (context.isTablet()) minTabletHeight else 0,
                    -realHeight.toFloat() + top + minTabletHeight,
                )
            }
            // for regular compact modes, no need to clamp, setTranslationY will take care of it
            toolbarMode != ToolbarState.EXPANDED -> {
                translationY
            }
            // if the recycler hasn't scrolled past the app bars height...
            offset < realHeight - shortH - tabHeight -> {
                -offset.toFloat()
            }
            else -> {
                MathUtils.clamp(
                    translationY,
                    -realHeight.toFloat() + top + minTabletHeight,
                    max(
                        smallHeight,
                        if (offset > realHeight - shortH - tabHeight) smallHeight else min(
                            -offset.toFloat(),
                            0f,
                        ),
                    ) + top.toFloat(),
                )
            }
        }

        translationY = newY
        mainToolbar?.let { mainToolbar ->
            mainToolbar.translationY = when {
                toolbarMode != ToolbarState.EXPANDED -> 0f
                -newY <= bigHeight -> max(-newY, 0f)
                else -> bigHeight.toFloat()
            }
        }
        if (toolbarMode != ToolbarState.EXPANDED || compactSearchMode) {
            if (compactSearchMode && toolbarMode == ToolbarState.EXPANDED) {
                bigView?.alpha = 0f
                mainToolbar?.alpha = 0f
                cardFrame?.backgroundColor = null
            } else {
                mainToolbar?.alpha = 1f
            }
            useSearchToolbarForMenu(compactSearchMode || offset > realHeight - shortH - tabHeight)
            return
        }
        // If toolbar is expanded, we want to fade out the big view, then later the main toolbar
        val alpha =
            (bigHeight + newY * 2) / (bigHeight) + 0.45f // (realHeight.toFloat() + newY * 5) / realHeight.toFloat() + .33f
        bigView?.alpha = MathUtils.clamp(if (alpha.isNaN()) 1f else alpha, 0f, 1f)
        val toolbarTextView = mainToolbar?.toolbarTitle ?: return
        toolbarTextView.setTextColorAlpha(
            (
                MathUtils.clamp(
                    (1 - ((if (alpha.isNaN()) 1f else alpha) + 0.95f)) * 2, 0f, 1f,
                ) * 255
                ).roundToInt(),
        )
        val mainToolbar = mainToolbar ?: return
        mainToolbar.alpha = MathUtils.clamp(
            (mainToolbar.bottom + mainToolbar.translationY + y - paddingTop) / mainToolbar.height,
            0f,
            1f,
        )
        val mainActivity = mainActivity ?: return
        val useSearchToolbar = mainToolbar.alpha <= 0.025f
        val idle = RecyclerView.SCROLL_STATE_IDLE
        if (if (useSearchToolbar) -y >= height || recyclerView?.scrollState ?: idle <= idle || context.isTablet()
            else mainActivity.currentToolbar == searchToolbar
        ) {
            useSearchToolbarForMenu(useSearchToolbar)
        }
    }

    /**
     * Snap Appbar to hide the entire appbar or show the smaller toolbar
     *
     * Only snaps if the [recyclerView] has scrolled farther than the current app bar's height
     * @param callback closure updates along with snapping the appbar, use if something needs to
     * update alongside the appbar
     */
    fun snapAppBarY(controller: Controller?, recyclerView: RecyclerView, callback: (() -> Unit)?): Float {
        yAnimator?.cancel()
        val halfWay = compactAppBarHeight / 2
        val shortAnimationDuration = resources?.getInteger(
            if (toolbarMode != ToolbarState.EXPANDED) android.R.integer.config_shortAnimTime
            else android.R.integer.config_longAnimTime,
        ) ?: 0
        val realHeight = preLayoutHeightWhileSearching + paddingTop
        val closerToTop = abs(y) > realHeight - halfWay
        val atTop = !recyclerView.canScrollVertically(-1)
        val shortH =
            if (toolbarMode != ToolbarState.EXPANDED || compactSearchMode) 0f else compactAppBarHeight
        val lastY = if (closerToTop && !atTop) {
            -height.toFloat()
        } else {
            shortH
        }

        val onFirstItem = recyclerView.computeVerticalScrollOffset() < realHeight - shortH

        return if (!onFirstItem) {
            yAnimator = animate().y(lastY)
                .setDuration(shortAnimationDuration.toLong())
            yAnimator?.setUpdateListener {
                if (controller?.isControllerVisible == true) {
                    updateAppBarAfterY(recyclerView, false)
                    callback?.invoke()
                }
            }
            yAnimator?.start()
            useSearchToolbarForMenu(true)
            lastY
        } else {
            useSearchToolbarForMenu(mainToolbar?.alpha ?: 0f <= 0f)
            y
        }
    }

    fun useSearchToolbarForMenu(showCardTB: Boolean) {
        val mainActivity = mainActivity ?: return
        if (lockYPos) return
        if ((showCardTB || toolbarMode == ToolbarState.SEARCH_ONLY) && cardFrame?.isVisible == true) {
            if (mainActivity.currentToolbar != searchToolbar) {
                mainActivity.setFloatingToolbar(true, showSearchAnyway = true)
            } else {
                mainActivity.setSearchTBMenuIfInvalid()
            }
            if (mainActivity.currentToolbar == searchToolbar) {
                if (toolbarMode == ToolbarState.EXPANDED) {
                    mainToolbar?.isInvisible = true
                }
                mainToolbar?.backgroundColor = null
                cardFrame?.backgroundColor = null
            }
        } else {
            if (mainActivity.currentToolbar != mainToolbar) {
                mainActivity.setFloatingToolbar(false, showSearchAnyway = true)
            }
            if (toolbarMode == ToolbarState.EXPANDED) {
                mainToolbar?.isInvisible = false
            }
            if (tabsFrameLayout?.isVisible == false) {
                cardFrame?.backgroundColor = mainActivity.getResourceColor(R.attr.colorSurface)
            } else {
                cardFrame?.backgroundColor = null
            }
        }
    }
}

interface SmallToolbarInterface
