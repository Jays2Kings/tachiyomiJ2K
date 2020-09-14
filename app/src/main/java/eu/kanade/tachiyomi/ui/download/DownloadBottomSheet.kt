package eu.kanade.tachiyomi.ui.download

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.view.Menu
import android.view.MenuItem
import android.widget.LinearLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.DownloadService
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.ui.extension.ExtensionDividerItemDecoration
import eu.kanade.tachiyomi.ui.recents.RecentsController
import eu.kanade.tachiyomi.util.view.RecyclerWindowInsetsListener
import eu.kanade.tachiyomi.util.view.collapse
import eu.kanade.tachiyomi.util.view.doOnApplyWindowInsets
import eu.kanade.tachiyomi.util.view.expand
import eu.kanade.tachiyomi.util.view.hide
import eu.kanade.tachiyomi.util.view.isCollapsed
import eu.kanade.tachiyomi.util.view.isExpanded
import eu.kanade.tachiyomi.util.view.isHidden
import eu.kanade.tachiyomi.util.view.updateLayoutParams
import kotlinx.android.synthetic.main.download_bottom_sheet.view.*

class DownloadBottomSheet @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? =
        null
) : LinearLayout(context, attrs),
    DownloadAdapter.DownloadItemListener {
    lateinit var controller: RecentsController
    var sheetBehavior: BottomSheetBehavior<*>? = null

    /**
     * Adapter containing the active downloads.
     */
    private var adapter: DownloadAdapter? = null

    private val presenter = DownloadBottomPresenter(this)

    /**
     * Whether the download queue is running or not.
     */
    private var isRunning: Boolean = false
    private var activity: Activity? = null

    fun onCreate(controller: RecentsController) {
        // Initialize adapter, scroll listener and recycler views
        adapter = DownloadAdapter(this)
        sheetBehavior = BottomSheetBehavior.from(this)
        activity = controller.activity
        // Create recycler and set adapter.
        dl_recycler.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        dl_recycler.adapter = adapter
        adapter?.isHandleDragEnabled = true
        adapter?.isSwipeEnabled = true
        adapter?.fastScroller = fast_scroller
        dl_recycler.setHasFixedSize(true)
        dl_recycler.addItemDecoration(ExtensionDividerItemDecoration(context))
        dl_recycler.setOnApplyWindowInsetsListener(RecyclerWindowInsetsListener)
        this.controller = controller
        updateDLTitle()

        val attrsArray = intArrayOf(android.R.attr.actionBarSize)
        val array = context.obtainStyledAttributes(attrsArray)
        val headerHeight = array.getDimensionPixelSize(0, 0)
        array.recycle()
        recycler_layout.doOnApplyWindowInsets { v, windowInsets, _ ->
            v.updateLayoutParams<MarginLayoutParams> {
                topMargin = windowInsets.systemWindowInsetTop + headerHeight - sheet_layout.height
            }
        }
        sheet_layout.setOnClickListener {
            if (!sheetBehavior.isExpanded()) {
                sheetBehavior?.expand()
            } else {
                sheetBehavior?.collapse()
            }
        }
        update()
        setInformationView()
        if (!controller.hasQueue()) {
            sheetBehavior?.isHideable = true
            sheetBehavior?.hide()
        }
    }

    fun update() {
        presenter.getItems()
        onQueueStatusChange(!presenter.downloadManager.isPaused())
    }

    private fun updateDLTitle() {
        val extCount = presenter.downloadQueue.firstOrNull()
        title_text.text = if (extCount != null) resources.getString(
            R.string.downloading_,
            extCount.chapter.name
        )
        else ""
    }

    /**
     * Called when the queue's status has changed. Updates the visibility of the buttons.
     *
     * @param running whether the queue is now running or not.
     */
    private fun onQueueStatusChange(running: Boolean) {
        val oldRunning = isRunning
        isRunning = running
        if (oldRunning != running) {
            activity?.invalidateOptionsMenu()

            // Check if download queue is empty and update information accordingly.
            setInformationView()
        }
    }

    /**
     * Called from the presenter to assign the downloads for the adapter.
     *
     * @param downloads the downloads from the queue.
     */
    fun onNextDownloads(downloads: List<DownloadItem>) {
        activity?.invalidateOptionsMenu()
        setInformationView()
        adapter?.updateDataSet(downloads)
        setBottomSheet()
    }

    /**
     * Called when the progress of a download changes.
     *
     * @param download the download whose progress has changed.
     */
    fun onUpdateProgress(download: Download) {
        getHolder(download)?.notifyProgress()
    }

    /**
     * Called when a page of a download is downloaded.
     *
     * @param download the download whose page has been downloaded.
     */
    fun onUpdateDownloadedPages(download: Download) {
        getHolder(download)?.notifyDownloadedPages()
    }

    /**
     * Returns the holder for the given download.
     *
     * @param download the download to find.
     * @return the holder of the download or null if it's not bound.
     */
    private fun getHolder(download: Download): DownloadHolder? {
        return dl_recycler?.findViewHolderForItemId(download.chapter.id!!) as? DownloadHolder
    }

    /**
     * Set information view when queue is empty
     */
    private fun setInformationView() {
        updateDLTitle()
        setBottomSheet()
        if (presenter.downloadQueue.isEmpty()) {
            empty_view?.show(
                R.drawable.ic_download_off_24dp,
                R.string.nothing_is_downloading
            )
        } else {
            empty_view?.hide()
        }
    }

    fun prepareMenu(menu: Menu) {
        // Set start button visibility.
        menu.findItem(R.id.start_queue)?.isVisible = !isRunning && !presenter.downloadQueue.isEmpty()

        // Set pause button visibility.
        menu.findItem(R.id.pause_queue)?.isVisible = isRunning && !presenter.downloadQueue.isEmpty()

        // Set clear button visibility.
        menu.findItem(R.id.clear_queue)?.isVisible = !presenter.downloadQueue.isEmpty()

        // Set reorder button visibility.
        menu.findItem(R.id.reorder)?.isVisible = !presenter.downloadQueue.isEmpty()
    }

    fun onOptionsItemSelected(item: MenuItem): Boolean {
        val context = activity ?: return false
        when (item.itemId) {
            R.id.start_queue -> DownloadService.start(context)
            R.id.pause_queue -> {
                DownloadService.stop(context)
                presenter.pauseDownloads()
            }
            R.id.clear_queue -> {
                DownloadService.stop(context)
                presenter.clearQueue()
            }
            R.id.newest, R.id.oldest -> {
                val adapter = adapter ?: return false
                val items = adapter.currentItems.sortedBy { it.download.chapter.date_upload }
                    .toMutableList()
                if (item.itemId == R.id.newest)
                    items.reverse()
                adapter.updateDataSet(items)
                val downloads = items.mapNotNull { it.download }
                presenter.reorder(downloads)
            }
        }
        return true
    }

    fun dismiss() {
        if (sheetBehavior?.isHideable == true) {
            sheetBehavior?.hide()
        } else {
            sheetBehavior?.collapse()
        }
    }

    private fun setBottomSheet() {
        val hasQueue = presenter.downloadQueue.isNotEmpty()
        if (hasQueue) {
            sheetBehavior?.skipCollapsed = !hasQueue
            if (sheetBehavior.isHidden()) sheetBehavior?.collapse()
        } else {
            sheetBehavior?.isHideable = !hasQueue
            sheetBehavior?.skipCollapsed = !hasQueue
            if (sheetBehavior.isCollapsed()) sheetBehavior?.hide()
        }
        controller.setPadding(!hasQueue)
    }

    /**
     * Called when an item is released from a drag.
     *
     * @param position The position of the released item.
     */
    override fun onItemReleased(position: Int) {
        val adapter = adapter ?: return
        val downloads = (0 until adapter.itemCount).mapNotNull { adapter.getItem(it)?.download }
        presenter.reorder(downloads)
    }

    override fun onItemRemoved(position: Int) {
        val download = adapter?.getItem(position)?.download ?: return
        presenter.cancelDownload(download)

        adapter?.removeItem(position)
        val adapter = adapter ?: return
        val downloads = (0 until adapter.itemCount).mapNotNull { adapter.getItem(it)?.download }
        presenter.reorder(downloads)
    }

    /**
     * Called when the menu item of a download is pressed
     *
     * @param position The position of the item
     * @param menuItem The menu Item pressed
     */
    override fun onMenuItemClick(position: Int, menuItem: MenuItem) {
        when (menuItem.itemId) {
            R.id.move_to_top, R.id.move_to_bottom -> {
                val items = adapter?.currentItems?.toMutableList() ?: return
                val item = items[position]
                items.remove(item)
                if (menuItem.itemId == R.id.move_to_top)
                    items.add(0, item)
                else
                    items.add(item)
                adapter?.updateDataSet(items)
                val downloads = items.mapNotNull { it.download }
                presenter.reorder(downloads)
            }
        }
    }
}
