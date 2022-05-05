package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.view.View
import android.view.ViewGroup
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.viewer.hasMissingChapters
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.widget.ViewPagerAdapter
import kotlinx.coroutines.delay
import timber.log.Timber
import kotlin.math.max

/**
 * Pager adapter used by this [viewer] to where [ViewerChapters] updates are posted.
 */
class PagerViewerAdapter(private val viewer: PagerViewer) : ViewPagerAdapter() {

    /**
     * Paired list of currently set items.
     */
    var joinedItems: MutableList<Pair<Any, Any?>> = mutableListOf()
        private set

    /** Single list of items */
    private var subItems: MutableList<Any> = mutableListOf()

    var nextTransition: ChapterTransition.Next? = null
        private set

    /** Page used to start the shifted pages */
    var pageToShift: ReaderPage? = null

    /** Varibles used to check if config of the pages have changed */
    private var shifted = viewer.config.shiftDoublePage
    private var doubledUp = viewer.config.doublePages

    var currentChapter: ReaderChapter? = null
    var forceTransition = false

    /**
     * Updates this adapter with the given [chapters]. It handles setting a few pages of the
     * next/previous chapter to allow seamless transitions and inverting the pages if the viewer
     * has R2L direction.
     */
    fun setChapters(chapters: ViewerChapters, forceTransition: Boolean) {
        val newItems = mutableListOf<Any>()

        // Force chapter transition page if there are missing chapters
        val prevHasMissingChapters = hasMissingChapters(chapters.currChapter, chapters.prevChapter)
        val nextHasMissingChapters = hasMissingChapters(chapters.nextChapter, chapters.currChapter)

        this.forceTransition = forceTransition
        // Add previous chapter pages and transition.
        if (chapters.prevChapter != null) {
            // We only need to add the last few pages of the previous chapter, because it'll be
            // selected as the current chapter when one of those pages is selected.
            val prevPages = chapters.prevChapter.pages
            // We will take an even number of pages if the page count if even
            // however we should take account full pages when deciding
            val numberOfFullPages =
                (
                    chapters.prevChapter.pages?.count { it.fullPage == true || it.isolatedPage }
                        ?: 0
                    )
            if (prevPages != null) {
                newItems.addAll(prevPages.takeLast(if ((prevPages.size + numberOfFullPages) % 2 == 0) 2 else 3))
            }
        }

        // Skip transition page if the chapter is loaded & current page is not a transition page
        if (prevHasMissingChapters || forceTransition || chapters.prevChapter?.state !is ReaderChapter.State.Loaded) {
            newItems.add(ChapterTransition.Prev(chapters.currChapter, chapters.prevChapter))
        }

        // Add current chapter.
        val currPages = chapters.currChapter.pages
        if (currPages != null) {
            newItems.addAll(currPages)
        }

        currentChapter = chapters.currChapter

        // Add next chapter transition and pages.
        nextTransition = ChapterTransition.Next(chapters.currChapter, chapters.nextChapter)
            .also {
                if (nextHasMissingChapters || forceTransition ||
                    chapters.nextChapter?.state !is ReaderChapter.State.Loaded
                ) {
                    newItems.add(it)
                }
            }

        if (chapters.nextChapter != null) {
            // Add at most two pages, because this chapter will be selected before the user can
            // swap more pages.
            val nextPages = chapters.nextChapter.pages
            if (nextPages != null) {
                newItems.addAll(nextPages.take(2))
            }
        }

        subItems = newItems.toMutableList()

        var useSecondPage = false
        if (shifted != viewer.config.shiftDoublePage || (doubledUp != viewer.config.doublePages && doubledUp)) {
            if (shifted && (doubledUp == viewer.config.doublePages)) {
                useSecondPage = true
            }
            shifted = viewer.config.shiftDoublePage
        }
        doubledUp = viewer.config.doublePages
        setJoinedItems(useSecondPage)
    }

    /**
     * Returns the amount of items of the adapter.
     */
    override fun getCount(): Int {
        return joinedItems.size
    }

    /**
     * Creates a new view for the item at the given [position].
     */
    override fun createView(container: ViewGroup, position: Int): View {
        val item = joinedItems[position].first
        val item2 = joinedItems[position].second
        return when (item) {
            is ReaderPage -> PagerPageHolder(viewer, item, item2 as? ReaderPage)
            is ChapterTransition -> PagerTransitionHolder(viewer, item)
            else -> throw NotImplementedError("Holder for ${item.javaClass} not implemented")
        }
    }

    /**
     * Returns the current position of the given [view] on the adapter.
     */
    override fun getItemPosition(view: Any): Int {
        if (view is PositionableView) {
            val position = joinedItems.indexOfFirst {
                if (it.first is InsertPage && view.item is Pair<*, *>) {
                    ((view.item as? Pair<*, *>?)?.first as? InsertPage)?.let { viewPage ->
                        return@indexOfFirst (it.first as? InsertPage)?.isFromSamePage(viewPage) == true &&
                            (it.first as? InsertPage)?.firstHalf == viewPage.firstHalf
                    }
                }
                val secondPage = it.second as? ReaderPage
                view.item == it.first to secondPage
            }
            if (position != -1) {
                return position
            } else {
                Timber.d("Position for ${view.item} not found")
            }
        }
        return POSITION_NONE
    }

    fun splitDoublePages(current: ReaderPage) {
        val oldCurrent = joinedItems.getOrNull(viewer.pager.currentItem)
        setJoinedItems(
            if (viewer.config.splitPages) {
                (oldCurrent?.first as? ReaderPage)?.firstHalf == false
            } else {
                oldCurrent?.second == current ||
                    (current.index + 1) < (
                    (
                        oldCurrent?.second
                            ?: oldCurrent?.first
                        ) as? ReaderPage
                    )?.index ?: 0
            },
        )

        // The listener may be removed when we split a page, so the ui may not have updated properly
        // This case usually happens when we load a new chapter and the first 2 pages need to split og
        viewer.scope.launchUI {
            delay(100)
            viewer.onPageChange(viewer.pager.currentItem)
        }
    }

    private fun setJoinedItems(useSecondPage: Boolean = false) {
        val oldCurrent = joinedItems.getOrNull(viewer.pager.currentItem)
        if (!viewer.config.doublePages) {
            // If not in double mode, set up items like before
            subItems.forEach {
                (it as? ReaderPage)?.shiftedPage = false
                (it as? ReaderPage)?.firstHalf = null
            }
            if (viewer.config.splitPages) {
                var itemIndex = 0
                val pagedItems = subItems.toMutableList()
                while (itemIndex < pagedItems.size) {
                    val page = pagedItems[itemIndex] as? ReaderPage
                    if (page == null) {
                        itemIndex++
                        continue
                    }
                    if (page.longPage == true) {
                        page.firstHalf = true
                        // Add a second halved page after each full page.
                        pagedItems[itemIndex] = InsertPage(page).apply { firstHalf = true }
                        val secondHalf = InsertPage(page)
                        pagedItems.add(itemIndex + 1, secondHalf)
                        itemIndex++
                    }
                    itemIndex++
                }
                this.joinedItems = pagedItems.map {
                    Pair<Any, Any?>(
                        it,
                        if ((it as? ReaderPage)?.fullPage == true) (it as? ReaderPage)?.firstHalf else null,
                    )
                }.toMutableList()
            } else {
                this.joinedItems = subItems.map { Pair<Any, Any?>(it, null) }.toMutableList()
            }
            if (viewer is R2LPagerViewer) {
                joinedItems.reverse()
            }
        } else {
            val pagedItems = mutableListOf<MutableList<ReaderPage?>>()
            val otherItems = mutableListOf<Any>()
            pagedItems.add(mutableListOf())
            // Step 1: segment the pages and transition pages
            subItems.forEach {
                if (it is ReaderPage) {
                    if (pagedItems.last().lastOrNull() != null &&
                        pagedItems.last().last()?.chapter?.chapter?.id != it.chapter.chapter.id
                    ) {
                        pagedItems.add(mutableListOf())
                    }
                    pagedItems.last().add(it)
                } else {
                    otherItems.add(it)
                    pagedItems.add(mutableListOf())
                }
            }
            var pagedIndex = 0
            val subJoinedItems = mutableListOf<Pair<Any, Any?>>()
            // Step 2: run through each set of pages
            pagedItems.forEach { items ->

                items.forEach {
                    it?.shiftedPage = false
                    it?.firstHalf = null
                }
                // Step 3: If pages have been shifted,
                if (viewer.config.shiftDoublePage) {
                    run loop@{
                        var index = items.indexOf(pageToShift)
                        if (pageToShift?.fullPage == true) {
                            index = max(0, index - 1)
                        }
                        // Go from the current page and work your way back to the first page,
                        // or the first page that's a full page.
                        // This is done in case user tries to shift a page after a full page
                        val fullPageBeforeIndex = max(
                            0,
                            (
                                if (index > -1) (
                                    items.take(index).indexOfLast { it?.fullPage == true }
                                    ) else -1
                                ),
                        )
                        // Add a shifted page to the first place there isnt a full page
                        (fullPageBeforeIndex until items.size).forEach {
                            if (items[it]?.fullPage != true) {
                                items[it]?.shiftedPage = true
                                return@loop
                            }
                        }
                    }
                }

                // Step 4: Add blanks for chunking
                var itemIndex = 0
                while (itemIndex < items.size) {
                    items[itemIndex]?.isolatedPage = false
                    if (items[itemIndex]?.fullPage == true || items[itemIndex]?.shiftedPage == true) {
                        // Add a 'blank' page after each full page. It will be used when chunked to solo a page
                        items.add(itemIndex + 1, null)
                        if (items[itemIndex]?.fullPage == true && itemIndex > 0 &&
                            items[itemIndex - 1] != null && (itemIndex - 1) % 2 == 0
                        ) {
                            // If a page is a full page, check if the previous page needs to be isolated
                            // we should check if it's an even or odd page, since even pages need shifting
                            // For example if Page 1 is full, Page 0 needs to be isolated
                            // No need to take account shifted pages, because null additions should
                            // always have an odd index in the list
                            items[itemIndex - 1]?.isolatedPage = true
                            items.add(itemIndex, null)
                            itemIndex++
                        }
                        itemIndex++
                    }
                    itemIndex++
                }

                // Step 5: chunk em
                if (items.isNotEmpty()) {
                    subJoinedItems.addAll(
                        items.chunked(2).map { Pair(it.first()!!, it.getOrNull(1)) },
                    )
                }
                otherItems.getOrNull(pagedIndex)?.let {
                    subJoinedItems.add(Pair(it, null))
                    pagedIndex++
                }
            }
            if (viewer is R2LPagerViewer) {
                subJoinedItems.reverse()
            }

            this.joinedItems = subJoinedItems
        }
        notifyDataSetChanged()

        // Step 6: Move back to our previous page or transition page
        // The listener is likely off around now, but either way when shifting or doubling,
        // we need to set the page back correctly
        // We will however shift to the first page of the new chapter if the last page we were are
        // on is not in the new chapter that has loaded
        val newPage =
            when {
                (oldCurrent?.first as? ReaderPage)?.chapter != currentChapter &&
                    (oldCurrent?.first as? ChapterTransition)?.from != currentChapter -> subItems.find { (it as? ReaderPage)?.chapter == currentChapter }
                useSecondPage && oldCurrent?.second is ReaderPage -> (oldCurrent.second ?: oldCurrent.first)
                else -> oldCurrent?.first ?: return
            }
        var index = joinedItems.indexOfFirst {
            val readerPage = it.first as? ReaderPage
            val readerPage2 = it.second as? ReaderPage
            val newReaderPage = newPage as? ReaderPage
            it.first == newPage || it.second == newPage ||
                (
                    readerPage != null && newReaderPage != null &&
                        (
                            readerPage.isFromSamePage(newReaderPage) ||
                                readerPage2?.isFromSamePage(newReaderPage) == true
                            ) &&
                        (readerPage.firstHalf == !useSecondPage || readerPage.firstHalf == null)
                    )
        }
        if (newPage is ChapterTransition && index == -1 && !forceTransition) {
            val newerPage = if (newPage is ChapterTransition.Next) {
                joinedItems.filter {
                    (it.first as? ReaderPage)?.chapter == newPage.to
                }.minByOrNull { (it.first as? ReaderPage)?.index ?: Int.MAX_VALUE }?.first
            } else {
                joinedItems.filter {
                    (it.first as? ReaderPage)?.chapter == newPage.to
                }.maxByOrNull { (it.first as? ReaderPage)?.index ?: Int.MIN_VALUE }?.first
            }
            index = joinedItems.indexOfFirst { it.first == newerPage || it.second == newerPage }
        }
        if (index > -1) {
            viewer.pager.setCurrentItem(index, false)
        }
    }
}
