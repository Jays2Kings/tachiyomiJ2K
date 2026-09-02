package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.graphics.Color
import android.graphics.PointF
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.WebtoonLayoutManager
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.viewer.BaseViewer
import eu.kanade.tachiyomi.ui.reader.viewer.GamepadHoldLoop
import eu.kanade.tachiyomi.ui.reader.viewer.JOYSTICK_DEADZONE
import eu.kanade.tachiyomi.ui.reader.viewer.JOYSTICK_PAN_INTERVAL_MS
import eu.kanade.tachiyomi.ui.reader.viewer.JOYSTICK_PAN_STEP
import eu.kanade.tachiyomi.ui.reader.viewer.PAN_STEP
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.ZOOM_HOLD_INTERVAL_MS
import eu.kanade.tachiyomi.ui.reader.viewer.gamepadZoomRate
import eu.kanade.tachiyomi.ui.reader.viewer.isDpadHatMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import uy.kohesive.injekt.injectLazy
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/**
 * Implementation of a [BaseViewer] to display pages with a [RecyclerView].
 */
class WebtoonViewer(
    val activity: ReaderActivity,
    val hasMargins: Boolean = false,
) : BaseViewer {
    val downloadManager: DownloadManager by injectLazy()

    private val scope = MainScope()

    /**
     * Recycler view used by this viewer.
     */
    val recycler = WebtoonRecyclerView(activity)

    /**
     * Frame containing the recycler view.
     */
    private val frame = WebtoonFrame(activity)

    /**
     * Layout manager of the recycler view.
     */
    private val layoutManager = WebtoonLayoutManager(activity)

    /**
     * Adapter of the recycler view.
     */
    private val adapter = WebtoonAdapter(this)

    /**
     * Distance to scroll when the user taps on one side of the recycler view.
     */
    private var scrollDistance = activity.resources.displayMetrics.heightPixels * 3 / 4

    /**
     * Currently active item. It can be a chapter page or a chapter transition.
     */
    private var currentPage: Any? = null

    /**
     * Configuration used by this viewer, like allow taps, or crop image borders.
     */
    val config = WebtoonConfig(scope)

    /**
     * Last known left analog stick position, used by [startJoystickPanLoop] to keep panning (X,
     * while zoomed in) and scrolling (Y, always) for as long as it's held away from center.
     */
    private var joystickX = 0f
    private var joystickY = 0f

    /**
     * Job for the loop that keeps panning/scrolling while the joystick is held, see
     * [startJoystickPanLoop].
     */
    private var joystickPanJob: Job? = null

    /**
     * Last known combined zoom rate from the L2/R2 triggers and the right stick's Y axis, in
     * [-1, 1] (negative zooms out, positive zooms in). Applied by [zoomLoop].
     */
    private var zoomRate = 0f

    /**
     * Keeps zooming for as long as [zoomRate] is non-zero, e.g. while a trigger is held.
     */
    private val zoomLoop = GamepadHoldLoop(scope, activity, ZOOM_HOLD_INTERVAL_MS, { zoomRate }, ::zoomBy)

    init {
        recycler.setBackgroundColor(Color.BLACK)
        recycler.isVisible = false // Don't let the recycler layout yet
        recycler.layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        recycler.itemAnimator = null
        recycler.layoutManager = layoutManager
        recycler.adapter = adapter
        recycler.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(
                    recyclerView: RecyclerView,
                    dx: Int,
                    dy: Int,
                ) {
                    onScrolled()

                    if (dy > config.menuThreshold || dy < -config.menuThreshold) {
                        activity.hideMenu()
                    }

                    if (dy < 0) {
                        val firstIndex = layoutManager.findFirstVisibleItemPosition()
                        val firstItem = adapter.items.getOrNull(firstIndex)
                        if (firstItem is ChapterTransition.Prev && firstItem.to != null) {
                            activity.requestPreloadChapter(firstItem.to)
                        }
                    }

                    val lastIndex = layoutManager.findLastEndVisibleItemPosition()
                    val lastItem = adapter.items.getOrNull(lastIndex)
                    if (lastItem is ChapterTransition.Next && lastItem.to == null) {
                        activity.showMenu()
                    }
                }
            },
        )
        recycler.tapListener = f@{ event ->
            val pos = PointF(event.x / recycler.width, event.y / recycler.height)
            val navigator = config.navigator
            when (navigator.getAction(pos)) {
                ViewerNavigation.NavigationRegion.MENU -> activity.toggleMenu()
                ViewerNavigation.NavigationRegion.NEXT, ViewerNavigation.NavigationRegion.RIGHT -> moveToNext()
                ViewerNavigation.NavigationRegion.PREV, ViewerNavigation.NavigationRegion.LEFT -> moveToPrevious()
            }
        }
        recycler.longTapListener = f@{ event ->
            if (activity.menuVisible || config.longTapEnabled) {
                val child = recycler.findChildViewUnder(event.x, event.y)
                if (child != null) {
                    val position = recycler.getChildAdapterPosition(child)
                    val item = adapter.items.getOrNull(position)
                    if (item is ReaderPage) {
                        activity.onPageLongTap(item)
                        return@f true
                    }
                }
            }
            false
        }

        config.imagePropertyChangedListener = {
            refreshAdapter()
        }

        config.zoomPropertyChangedListener = {
            frame.enableZoomOut = it
        }

        config.doubleTapZoomChangedListener = {
            recycler.doubleTapZoom = it
        }

        config.navigationModeChangedListener = {
            val showOnStart = config.navigationOverlayForNewUser
            activity.binding.navigationOverlay.setNavigation(config.navigator, showOnStart)
        }
        config.navigationModeInvertedListener = { activity.binding.navigationOverlay.showNavigationAgain() }

        frame.layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        frame.addView(recycler)
    }

    private fun checkAllowPreload(page: ReaderPage?): Boolean {
        // Page is transition page - preload allowed
        page ?: return true

        // Initial opening - preload allowed
        currentPage ?: return true

        val nextItem = adapter.items.getOrNull(adapter.items.size - 1)
        val nextChapter = (nextItem as? ChapterTransition.Next)?.to ?: (nextItem as? ReaderPage)?.chapter

        // Allow preload for
        // 1. Going between pages of same chapter
        // 2. Next chapter page
        return when (page.chapter) {
            (currentPage as? ReaderPage)?.chapter -> true
            nextChapter -> true
            else -> false
        }
    }

    /**
     * Returns the view this viewer uses.
     */
    override fun getView(): View = frame

    /**
     * Destroys this viewer. Called when leaving the reader or swapping viewers.
     */
    override fun destroy() {
        super.destroy()
        scope.cancel()
    }

    /**
     * Called from the RecyclerView listener when a [page] is marked as active. It notifies the
     * activity of the change and requests the preload of the next chapter if this is the last page.
     */
    private fun onPageSelected(
        page: ReaderPage,
        allowPreload: Boolean,
    ) {
        activity.onPageSelected(page, false)

        val pages = page.chapter.pages ?: return
        Timber.d("onReaderPageSelected: ${page.number}/${pages.size}")
        // Preload next chapter once we're within the last 5 pages of the current chapter
        val inPreloadRange = pages.size - page.number < 5
        if (inPreloadRange && allowPreload && page.chapter == adapter.currentChapter) {
            Timber.d("Request preload next chapter because we're at page ${page.number} of ${pages.size}")
            val nextItem = adapter.items.getOrNull(adapter.items.size - 1)
            val transitionChapter = (nextItem as? ChapterTransition.Next)?.to ?: (nextItem as?ReaderPage)?.chapter
            if (transitionChapter != null) {
                Timber.d("Requesting to preload chapter ${transitionChapter.chapter.chapter_number}")
                activity.requestPreloadChapter(transitionChapter)
            }
        }
    }

    /**
     * Called from the RecyclerView listener when a [transition] is marked as active. It requests the
     * preload of the destination chapter of the transition.
     */
    private fun onTransitionSelected(transition: ChapterTransition) {
        Timber.d("onTransitionSelected: $transition")
        val toChapter = transition.to
        if (toChapter != null) {
            Timber.d("Request preload destination chapter because we're on the transition")
            activity.requestPreloadChapter(toChapter)
        }
    }

    /**
     * Tells this viewer to set the given [chapters] as active.
     */
    override fun setChapters(chapters: ViewerChapters) {
        Timber.d("setChapters")
        val forceTransition = config.alwaysShowChapterTransition || currentPage is ChapterTransition
        adapter.setChapters(chapters, forceTransition)

        if (recycler.isGone) {
            Timber.d("Recycler first layout")
            val pages = chapters.currChapter.pages ?: return
            moveToPage(pages[min(chapters.currChapter.requestedPage, pages.lastIndex)])
            recycler.isVisible = true
        }
    }

    /**
     * Tells this viewer to move to the given [page].
     */
    override fun moveToPage(
        page: ReaderPage,
        animated: Boolean,
    ) {
        Timber.d("moveToPage")
        val position = adapter.items.indexOf(page)
        if (position != -1) {
            recycler.scrollToPosition(position)
            if (layoutManager.findLastEndVisibleItemPosition() == -1) {
                onScrolled(position)
            }
        } else {
            Timber.d("Page $page not found in adapter")
        }
    }

    fun onScrolled(pos: Int? = null) {
        val position = pos ?: layoutManager.findLastEndVisibleItemPosition()
        val item = adapter.items.getOrNull(position)
        val allowPreload = checkAllowPreload(item as? ReaderPage)
        if (item != null && currentPage != item) {
            currentPage = item
            when (item) {
                is ReaderPage -> onPageSelected(item, allowPreload)
                is ChapterTransition -> onTransitionSelected(item)
            }
        }
    }

    /**
     * Scrolls up by [scrollDistance].
     */
    override fun moveToPrevious() {
        if (config.usePageTransitions) {
            recycler.smoothScrollBy(0, -scrollDistance)
        } else {
            recycler.scrollBy(0, -scrollDistance)
        }
    }

    /**
     * Scrolls down by [scrollDistance].
     */
    override fun moveToNext() {
        if (config.usePageTransitions) {
            recycler.smoothScrollBy(0, scrollDistance)
        } else {
            recycler.scrollBy(0, scrollDistance)
        }
    }

    override fun isZoomedIn(): Boolean = recycler.isZoomedIn()

    override fun isAtEndOfReader(): Boolean = (currentPage as? ChapterTransition.Next)?.let { it.to == null } ?: false

    /**
     * Zooms the recycler in by one step, e.g. from a gamepad or keyboard press.
     */
    override fun zoomIn() {
        recycler.onScale(ZOOM_STEP)
    }

    /**
     * Zooms the recycler out by one step, e.g. from a gamepad or keyboard press.
     */
    override fun zoomOut() {
        recycler.onScale(1f / ZOOM_STEP)
    }

    /**
     * Pans the zoomed recycler content by [dxRatio]/[dyRatio] of its width/height - the same
     * translation a touch drag applies while zoomed in.
     */
    override fun pan(
        dxRatio: Float,
        dyRatio: Float,
    ) {
        if (!recycler.isZoomedIn()) return
        recycler.zoomScrollBy((recycler.width * dxRatio).roundToInt(), (recycler.height * dyRatio).roundToInt())
    }

    /**
     * Applies one non-animated zoom step scaled by [rate] to the whole recycler, the same way a
     * pinch gesture does.
     */
    override fun zoomBy(rate: Float) {
        if (rate == 0f) return
        val factor = 1f + (ZOOM_HOLD_FACTOR - 1f) * rate.coerceIn(-1f, 1f)
        recycler.onScale(factor)
    }

    /**
     * Called from the containing activity when a key [event] is received. It should return true
     * if the event was handled, false otherwise.
     */
    override fun handleKeyEvent(event: KeyEvent): Boolean {
        // Unlike the pager, the strip scrolls freely, so scrolling and pan/zoom keep going on every
        // auto-repeated ACTION_DOWN a held key generates. One move per press for the rest: the
        // bumpers and left/right while not zoomed, plus the volume keys, whose repeat is OEM-dependent.
        val isDown = event.action == KeyEvent.ACTION_DOWN
        val isInitialDown = isDown && event.repeatCount == 0

        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (!config.volumeKeysEnabled || activity.menuVisible) {
                    return false
                } else if (isInitialDown) {
                    if (!config.volumeKeysInverted) moveToNext() else moveToPrevious()
                }
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (!config.volumeKeysEnabled || activity.menuVisible) {
                    return false
                } else if (isInitialDown) {
                    if (!config.volumeKeysInverted) moveToPrevious() else moveToNext()
                }
            }
            // While the menu is open, let the dpad/arrow keys drive normal Android focus
            // navigation between the menu's buttons instead of scrolling/panning pages. Up/down
            // always scroll - the reading motion is vertical, so it should never get hijacked
            // into panning. Left/right pan while zoomed in (matching the bumpers' direction
            // below), otherwise scroll a step at a time like the bumpers do.
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (activity.menuVisible) return false
                if (isZoomedIn()) {
                    if (isDown) pan(-PAN_STEP, 0f)
                } else if (isInitialDown) {
                    moveToNext()
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (activity.menuVisible) return false
                if (isZoomedIn()) {
                    if (isDown) pan(PAN_STEP, 0f)
                } else if (isInitialDown) {
                    moveToPrevious()
                }
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (activity.menuVisible) return false
                if (isDown) moveToPrevious()
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (activity.menuVisible) return false
                if (isDown) moveToNext()
            }
            KeyEvent.KEYCODE_PAGE_UP -> if (isDown) moveToPrevious()
            KeyEvent.KEYCODE_PAGE_DOWN -> if (isDown) moveToNext()

            // Gamepad shoulder buttons scroll up/down.
            KeyEvent.KEYCODE_BUTTON_L1 -> if (isInitialDown) moveToPrevious()
            KeyEvent.KEYCODE_BUTTON_R1 -> if (isInitialDown) moveToNext()

            // Gamepad X/Y zoom the recycler in/out, repeating while held.
            KeyEvent.KEYCODE_BUTTON_Y -> if (isDown) zoomIn()
            KeyEvent.KEYCODE_BUTTON_X -> if (isDown) zoomOut()
            else -> return false
        }
        return true
    }

    /**
     * Called from the containing activity when a generic motion [event] is received. It should
     * return true if the event was handled, false otherwise.
     */
    override fun handleGenericMotionEvent(event: MotionEvent): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK) || event.action != MotionEvent.ACTION_MOVE) {
            return false
        }

        if (event.isDpadHatMotion()) return false

        // The horizontal axis pans while zoomed in; the vertical axis always scrolls the strip
        // instead, regardless of zoom (see the d-pad up/down comment above). The loop itself
        // runs whenever either axis is deflected - it's only the pan action that's zoom-gated.
        joystickX = event.getAxisValue(MotionEvent.AXIS_X)
        joystickY = event.getAxisValue(MotionEvent.AXIS_Y)
        val deflected = abs(joystickX) > JOYSTICK_DEADZONE || abs(joystickY) > JOYSTICK_DEADZONE
        if (deflected && !activity.menuVisible) {
            startJoystickPanLoop()
        } else {
            joystickPanJob?.cancel()
        }

        zoomRate = event.gamepadZoomRate()
        zoomLoop.update()

        return deflected || zoomRate != 0f
    }

    /**
     * The joystick only sends a [MotionEvent] when its axes change, but panning/scrolling should
     * continue for as long as it's held away from center. This starts a loop, independent of
     * zoom level, that keeps applying the last known [joystickX]/[joystickY] values until both
     * axes are released/re-centered or the menu opens - panning horizontally while zoomed in,
     * and always scrolling vertically.
     */
    private fun startJoystickPanLoop() {
        if (joystickPanJob?.isActive == true) return
        joystickPanJob =
            scope.launch {
                while (isActive) {
                    val x = joystickX
                    val y = joystickY
                    if (activity.menuVisible || (abs(x) <= JOYSTICK_DEADZONE && abs(y) <= JOYSTICK_DEADZONE)) {
                        break
                    }
                    if (isZoomedIn() && abs(x) > JOYSTICK_DEADZONE) {
                        pan(x * -JOYSTICK_PAN_STEP, 0f)
                    }
                    if (abs(y) > JOYSTICK_DEADZONE) {
                        recycler.scrollBy(0, (recycler.height * y * JOYSTICK_SCROLL_STEP).roundToInt())
                    }
                    delay(JOYSTICK_PAN_INTERVAL_MS.milliseconds)
                }
            }
    }

    /**
     * Notifies adapter of changes around the current page to trigger a relayout in the recycler.
     * Used when an image configuration is changed.
     */
    private fun refreshAdapter() {
        val position = layoutManager.findLastEndVisibleItemPosition()
        adapter.notifyItemRangeChanged(
            max(0, position - 3),
            min(position + 3, adapter.itemCount - 1),
        )
    }
}

/** Fraction of the recycler's height scrolled per joystick tick at full deflection. */
private const val JOYSTICK_SCROLL_STEP = 0.03f

/** Per-tick scale multiplier at full rate for [WebtoonViewer.zoomBy]'s held zoom. */
private const val ZOOM_HOLD_FACTOR = 1.035f

/** Scale multiplier applied per gamepad/keyboard press for [WebtoonViewer.zoomIn]/[WebtoonViewer.zoomOut]. */
private const val ZOOM_STEP = 1.25f
