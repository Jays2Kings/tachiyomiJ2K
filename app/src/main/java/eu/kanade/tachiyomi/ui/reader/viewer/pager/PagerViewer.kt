package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.graphics.PointF
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.viewpager.widget.ViewPager
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.viewer.BaseViewer
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import timber.log.Timber
import uy.kohesive.injekt.injectLazy

/**
 * Implementation of a [BaseViewer] to display pages with a [ViewPager].
 */
@Suppress("LeakingThis")
abstract class PagerViewer(val activity: ReaderActivity) : BaseViewer {

    val downloadManager: DownloadManager by injectLazy()

    val scope = MainScope()

    /**
     * View pager used by this viewer. It's abstract to implement L2R, R2L and vertical pagers on
     * top of this class.
     */
    val pager = createPager()

    /**
     * Configuration used by the pager, like allow taps, scale mode on images, page transitions...
     */
    val config = PagerConfig(scope, this)

    /**
     * Adapter of the pager.
     */
    private val adapter = PagerViewerAdapter(this)

    /**
     * Currently active item. It can be a chapter page or a chapter transition.
     */
    private var currentPage: Any? = null

    /**
     * Viewer chapters to set when the pager enters idle mode. Otherwise, if the view was settling
     * or dragging, there'd be a noticeable and annoying jump.
     */
    private var awaitingIdleViewerChapters: ViewerChapters? = null

    /**
     * Whether the view pager is currently in idle mode. It sets the awaiting chapters if setting
     * this field to true.
     */
    private var isIdle = true
        set(value) {
            field = value
            if (value) {
                awaitingIdleViewerChapters?.let {
                    setChaptersDoubleShift(it)
                    awaitingIdleViewerChapters = null
                }
            }
        }

    /**
     * Variable used to hold the forward pos for reader activity shared transitions
     * Without this var landscapezoom wont work with activity transitions
     * */
    var heldForwardZoom: Pair<Int, Boolean>? = null

    private var pagerListener = object : ViewPager.SimpleOnPageChangeListener() {
        override fun onPageSelected(position: Int) {
            onPageChange(position)
        }

        override fun onPageScrollStateChanged(state: Int) {
            isIdle = state == ViewPager.SCROLL_STATE_IDLE
        }
    }

    init {
        pager.isVisible = false // Don't layout the pager yet
        pager.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        pager.offscreenPageLimit = 1
        pager.id = R.id.reader_pager
        pager.adapter = adapter
        pager.addOnPageChangeListener(pagerListener)
        pager.tapListener = f@{ event ->
            val pos = PointF(event.rawX / pager.width, event.rawY / pager.height)
            val navigator = config.navigator
            when (navigator.getAction(pos)) {
                ViewerNavigation.NavigationRegion.MENU -> activity.toggleMenu()
                ViewerNavigation.NavigationRegion.NEXT -> moveToNext()
                ViewerNavigation.NavigationRegion.PREV -> moveToPrevious()
                ViewerNavigation.NavigationRegion.RIGHT -> moveRight()
                ViewerNavigation.NavigationRegion.LEFT -> moveLeft()
            }
        }
        pager.longTapListener = f@{
            if (activity.menuVisible || config.longTapEnabled) {
                val item = adapter.joinedItems.getOrNull(pager.currentItem)
                val firstPage = item?.first as? ReaderPage
                val secondPage = item?.second as? ReaderPage
                if (firstPage is ReaderPage) {
                    activity.onPageLongTap(firstPage, secondPage)
                    return@f true
                }
            }
            false
        }

        config.imagePropertyChangedListener = {
            refreshAdapter()
        }

        config.reloadChapterListener = {
            activity.reloadChapters(it)
        }

        config.navigationModeChangedListener = {
            val showOnStart = config.navigationOverlayForNewUser
            activity.binding.navigationOverlay.setNavigation(config.navigator, showOnStart)
        }
        config.navigationModeInvertedListener = { activity.binding.navigationOverlay.showNavigationAgain() }
    }

    /**
     * Creates a new ViewPager.
     */
    abstract fun createPager(): Pager

    /**
     * Returns the view this viewer uses.
     */
    override fun getView(): View {
        return pager
    }

    override fun destroy() {
        super.destroy()
        scope.cancel()
    }

    /**
     * Returns the PagerPageHolder for the provided page
     */
    private fun getPageHolder(page: ReaderPage): PagerPageHolder? =
        pager.children
            .filterIsInstance(PagerPageHolder::class.java)
            .firstOrNull { it.item.first.index == page.index || it.item.second?.index == page.index }

    /**
     * Called when a new page (either a [ReaderPage] or [ChapterTransition]) is marked as active
     */
    fun onPageChange(position: Int) {
        val page = adapter.joinedItems.getOrNull(position)
        if (page != null && currentPage != page) {
            val pageF = page.first
            val allowPreload = checkAllowPreload(pageF as? ReaderPage)
            val forward =
                // if both pages have the same number, it's a split page with an InsertPage
                when {
                    // Use case happens on new chapter load
                    currentPage == pageF -> null
                    currentPage is ReaderPage && pageF is ReaderPage -> if (pageF.number == (currentPage as ReaderPage).number) {
                        // the InsertPage is always the second in the reading direction
                        pageF is InsertPage
                    } else {
                        pageF.number > (currentPage as ReaderPage).number
                    }
                    currentPage is ChapterTransition.Prev && pageF is ReaderPage ->
                        (currentPage as ChapterTransition).from == pageF.chapter
                    currentPage is ChapterTransition.Next && pageF is ReaderPage ->
                        (currentPage as ChapterTransition).to == pageF.chapter
                    else -> true
                }
            currentPage = pageF
            when (pageF) {
                is ReaderPage -> {
                    onReaderPageSelected(pageF, allowPreload, page.second is ReaderPage, forward)
                }
                is ChapterTransition -> onTransitionSelected(pageF)
            }
        }
    }

    private fun checkAllowPreload(page: ReaderPage?): Boolean {
        // Page is transition page - preload allowed
        page ?: return true

        // Initial opening - preload allowed
        currentPage ?: return true

        // Allow preload for
        // 1. Going to next chapter from chapter transition
        // 2. Going between pages of same chapter
        // 3. Next chapter page
        return when (page.chapter) {
            (currentPage as? ChapterTransition.Next)?.to -> true
            (currentPage as? ReaderPage)?.chapter -> true
            adapter.nextTransition?.to -> true
            else -> false
        }
    }

    /**
     * Called when a [ReaderPage] is marked as active. It notifies the
     * activity of the change and requests the preload of the next chapter if this is the last page.
     */
    private fun onReaderPageSelected(page: ReaderPage, allowPreload: Boolean, hasExtraPage: Boolean, forward: Boolean?) {
        activity.onPageSelected(page, hasExtraPage)

        // Notify holder of page change
        val holder = getPageHolder(page)
        if (holder == null && forward != null && heldForwardZoom == null) {
            heldForwardZoom = page.index to forward
        } else {
            holder?.onPageSelected(forward)
        }
        val offset = if (hasExtraPage) 1 else 0
        val pages = page.chapter.pages ?: return
        if (hasExtraPage) {
            Timber.d("onReaderPageSelected: ${page.number}-${page.number + offset}/${pages.size}")
        } else {
            Timber.d("onReaderPageSelected: ${page.number}/${pages.size}")
        }
        // Preload next chapter once we're within the last 5 pages of the current chapter
        val inPreloadRange = pages.size - page.number < 5
        if (inPreloadRange && allowPreload && page.chapter == adapter.currentChapter) {
            Timber.d("Request preload next chapter because we're at page ${page.number} of ${pages.size}")
            adapter.nextTransition?.to?.let {
                activity.requestPreloadChapter(it)
            }
        }
    }

    /**
     * Called when a [ChapterTransition] is marked as active. It request the
     * preload of the destination chapter of the transition.
     */
    private fun onTransitionSelected(transition: ChapterTransition) {
        Timber.d("onTransitionSelected: $transition")
        val toChapter = transition.to
        if (toChapter != null) {
            Timber.d("Request preload destination chapter because we're on the transition")
            activity.requestPreloadChapter(toChapter)
        } else if (transition is ChapterTransition.Next) {
            // No more chapters, show menu because the user is probably going to close the reader
            activity.showMenu()
        }
    }

    private fun getItem(position: Int, currentChapter: ReaderChapter?): Pair<Any, Any?>? {
        return adapter.joinedItems.firstOrNull {
            val readerPage = it.first as? ReaderPage ?: return@firstOrNull false
            readerPage.index == position && readerPage.chapter.chapter.id == currentChapter?.chapter?.id
        }
    }

    fun hasExtraPage(position: Int, currentChapter: ReaderChapter?): Boolean {
        val item = getItem(position, currentChapter) ?: return false
        return item.second is ReaderPage
    }

    fun setChaptersDoubleShift(chapters: ViewerChapters) {
        // Remove Listener since we're about to change the size of the items
        // If we don't the size change could put us on a new chapter
        pager.removeOnPageChangeListener(pagerListener)
        setChaptersInternal(chapters)
        pager.addOnPageChangeListener(pagerListener)
        // Since we removed the listener while shifting, call page change to update the ui
        onPageChange(pager.currentItem)
    }

    fun updateShifting(page: ReaderPage? = null) {
        adapter.pageToShift = page ?: adapter.joinedItems[pager.currentItem].first as? ReaderPage
    }

    fun getShiftedPage(): ReaderPage? = adapter.pageToShift

    /**
     * Tells this viewer to set the given [chapters] as active. If the pager is currently idle,
     * it sets the chapters immediately, otherwise they are saved and set when it becomes idle.
     */
    override fun setChapters(chapters: ViewerChapters) {
        if (isIdle) {
            setChaptersDoubleShift(chapters)
        } else {
            awaitingIdleViewerChapters = chapters
        }
    }

    /**
     * Sets the active [chapters] on this pager.
     */
    private fun setChaptersInternal(chapters: ViewerChapters) {
        Timber.d("setChaptersInternal")
        val forceTransition = config.alwaysShowChapterTransition || adapter.joinedItems.getOrNull(
            pager
                .currentItem
        )?.first is ChapterTransition
        adapter.setChapters(chapters, forceTransition)

        // Layout the pager once a chapter is being set
        if (pager.visibility == View.GONE) {
            Timber.d("Pager first layout")
            val pages = chapters.currChapter.pages ?: return
            moveToPage(pages[chapters.currChapter.requestedPage])
            pager.isVisible = true
        }
        activity.invalidateOptionsMenu()
    }

    /**
     * Tells this viewer to move to the given [page].
     */
    override fun moveToPage(page: ReaderPage, animated: Boolean) {
        Timber.d("moveToPage ${page.number}")
        val position = adapter.joinedItems.indexOfFirst {
            it.first == page || it.second == page ||
                (
                    config.splitPages && it.first is ReaderPage &&
                        (it.first as? ReaderPage)?.isFromSamePage(page) == true &&
                        (it.first as? ReaderPage)?.firstHalf != false
                    )
        }
        if (position != -1) {
            val currentPosition = pager.currentItem
            pager.setCurrentItem(position, animated)
            // manually call onPageChange since ViewPager listener is not triggered in this case
            if (currentPosition == position) {
                onPageChange(position)
            } else {
                // Call this since with double shift onPageChange wont get called (it shouldn't)
                // Instead just update the page count in ui
                val joinedItem = adapter.joinedItems.firstOrNull { it.first == page || it.second == page }
                activity.onPageSelected(
                    joinedItem?.first as? ReaderPage ?: page,
                    joinedItem?.second is ReaderPage
                )
            }
        } else {
            Timber.d("Page $page not found in adapter")
        }
    }

    override fun moveToNext() {
        moveRight()
    }

    override fun moveToPrevious() {
        moveLeft()
    }

    /**
     * Moves to the page at the right.
     */
    protected open fun moveRight() {
        if (pager.currentItem != adapter.count - 1) {
            val holder = (currentPage as? ReaderPage)?.let { getPageHolder(it) }
            if (holder != null && config.navigateToPan && holder.canPanRight()) {
                holder.panRight()
            } else {
                pager.setCurrentItem(pager.currentItem + 1, config.usePageTransitions)
            }
        }
    }

    /**
     * Moves to the page at the left.
     */
    protected open fun moveLeft() {
        if (pager.currentItem != 0) {
            val holder = (currentPage as? ReaderPage)?.let { getPageHolder(it) }
            if (holder != null && config.navigateToPan && holder.canPanLeft()) {
                holder.panLeft()
            } else {
                pager.setCurrentItem(pager.currentItem - 1, config.usePageTransitions)
            }
        }
    }

    /**
     * Moves to the page at the top (or previous).
     */
    protected open fun moveUp() {
        moveToPrevious()
    }

    /**
     * Moves to the page at the bottom (or next).
     */
    protected open fun moveDown() {
        moveToNext()
    }

    /**
     * Resets the adapter in order to recreate all the views. Used when a image configuration is
     * changed.
     */
    private fun refreshAdapter() {
        val currentItem = pager.currentItem
        pager.adapter = adapter
        pager.setCurrentItem(currentItem, false)
    }

    /**
     * Called from the containing activity when a key [event] is received. It should return true
     * if the event was handled, false otherwise.
     */
    override fun handleKeyEvent(event: KeyEvent): Boolean {
        val isUp = event.action == KeyEvent.ACTION_UP

        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (!config.volumeKeysEnabled || activity.menuVisible) {
                    return false
                } else if (isUp) {
                    if (!config.volumeKeysInverted) moveDown() else moveUp()
                }
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (!config.volumeKeysEnabled || activity.menuVisible) {
                    return false
                } else if (isUp) {
                    if (!config.volumeKeysInverted) moveUp() else moveDown()
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (isUp) moveRight()
            KeyEvent.KEYCODE_DPAD_LEFT -> if (isUp) moveLeft()
            KeyEvent.KEYCODE_DPAD_DOWN -> if (isUp) moveDown()
            KeyEvent.KEYCODE_DPAD_UP -> if (isUp) moveUp()
            KeyEvent.KEYCODE_PAGE_DOWN -> if (isUp) moveDown()
            KeyEvent.KEYCODE_PAGE_UP -> if (isUp) moveUp()
            KeyEvent.KEYCODE_MENU -> if (isUp) activity.toggleMenu()
            else -> return false
        }
        return true
    }

    fun splitDoublePages(currentPage: ReaderPage) {
        adapter.splitDoublePages(currentPage)
    }

    /**
     * Called from the containing activity when a generic motion [event] is received. It should
     * return true if the event was handled, false otherwise.
     */
    override fun handleGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_CLASS_POINTER != 0) {
            when (event.action) {
                MotionEvent.ACTION_SCROLL -> {
                    if (event.getAxisValue(MotionEvent.AXIS_VSCROLL) < 0.0f) {
                        moveDown()
                    } else {
                        moveUp()
                    }
                    return true
                }
            }
        }
        return false
    }
}
