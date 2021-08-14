package eu.kanade.tachiyomi.ui.download

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.view.Menu
import android.view.MenuItem
import android.widget.LinearLayout
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.google.android.material.bottomsheet.BottomSheetBehavior
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.DownloadService
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.databinding.DownloadBottomSheetBinding
import eu.kanade.tachiyomi.ui.extension.ExtensionDividerItemDecoration
import eu.kanade.tachiyomi.ui.recents.RecentsController
import eu.kanade.tachiyomi.util.view.collapse
import eu.kanade.tachiyomi.util.view.doOnApplyWindowInsets
import eu.kanade.tachiyomi.util.view.expand
import eu.kanade.tachiyomi.util.view.hide
import eu.kanade.tachiyomi.util.view.isCollapsed
import eu.kanade.tachiyomi.util.view.isExpanded
import eu.kanade.tachiyomi.util.view.isHidden
import eu.kanade.tachiyomi.util.view.toolbarHeight

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

    lateinit var binding: DownloadBottomSheetBinding
    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = DownloadBottomSheetBinding.bind(this)
    }

    fun onCreate(controller: RecentsController) {
        // Initialize adapter, scroll listener and recycler views
        adapter = DownloadAdapter(this)
        sheetBehavior = BottomSheetBehavior.from(this)
        activity = controller.activity
        // Create recycler and set adapter.
        binding.dlRecycler.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        binding.dlRecycler.adapter = adapter
        adapter?.isHandleDragEnabled = true
        adapter?.isSwipeEnabled = true
        adapter?.fastScroller = binding.fastScroller
        binding.dlRecycler.setHasFixedSize(true)
        binding.dlRecycler.addItemDecoration(ExtensionDividerItemDecoration(context))
        this.controller = controller
        updateDLTitle()

        val attrsArray = intArrayOf(android.R.attr.actionBarSize)
        val array = context.obtainStyledAttributes(attrsArray)
        val headerHeight = array.getDimensionPixelSize(0, 0)
        array.recycle()
        binding.recyclerLayout.doOnApplyWindowInsets { v, windowInsets, _ ->
            v.updateLayoutParams<MarginLayoutParams> {
                topMargin = windowInsets.systemWindowInsetTop +
                    (controller.toolbarHeight ?: headerHeight) -
                    binding.sheetLayout.height
            }
        }
        binding.sheetLayout.setOnClickListener {
            if (!sheetBehavior.isExpanded()) {
                sheetBehavior?.expand()
            } else {
                sheetBehavior?.collapse()
            }
        }
        binding.downloadFab.setOnClickListener {
            if (controller.presenter.downloadManager.isPaused()) {
                DownloadService.start(context)
            } else {
                DownloadService.stop(context)
                presenter.pauseDownloads()
            }
            updateFab()
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
        binding.downloadFab.isInvisible = presenter.downloadQueue.isEmpty()
    }

    private fun updateDLTitle() {
        val extCount = presenter.downloadQueue.firstOrNull()
        binding.titleText.text = if (extCount != null) resources.getString(
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
        binding.downloadFab.isInvisible = presenter.downloadQueue.isEmpty()
        updateFab()
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
        return binding.dlRecycler?.findViewHolderForItemId(download.chapter.id!!) as? DownloadHolder
    }

    /**
     * Set information view when queue is empty
     */
    private fun setInformationView() {
        updateDLTitle()
        setBottomSheet()
        if (presenter.downloadQueue.isEmpty()) {
            binding.emptyView.show(
                R.drawable.ic_download_off_24dp,
                R.string.nothing_is_downloading
            )
        } else {
            binding.emptyView.hide()
        }
    }

    fun prepareMenu(menu: Menu) {
        updateFab()
        // Set clear button visibility.
        menu.findItem(R.id.clear_queue)?.isVisible = !presenter.downloadQueue.isEmpty()

        // Set reorder button visibility.
        menu.findItem(R.id.reorder)?.isVisible = !presenter.downloadQueue.isEmpty()
    }

    private fun updateFab() {
        binding.downloadFab.text = context.getString(if (isRunning) R.string.pause else R.string.resume)
        binding.downloadFab.setIconResource(if (isRunning) R.drawable.ic_pause_24dp else R.drawable.ic_play_arrow_24dp)
    }

    fun onOptionsItemSelected(item: MenuItem): Boolean {
        val context = activity ?: return false
        when (item.itemId) {
            R.id.clear_queue -> {
                DownloadService.stop(context)
                presenter.clearQueue()
            }
            R.id.newest, R.id.oldest -> {
                val adapter = adapter ?: return false
                val items = adapter.currentItems.sortedBy { it.download.chapter.date_upload }
                    .toMutableList()
                if (item.itemId == R.id.newest) {
                    items.reverse()
                }
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
        controller.updateChapterDownload(download, false)
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
                if (menuItem.itemId == R.id.move_to_top) {
                    items.add(0, item)
                } else {
                    items.add(item)
                }
                adapter?.updateDataSet(items)
                val downloads = items.mapNotNull { it.download }
                presenter.reorder(downloads)
            }
            R.id.cancel_series -> {
                val download = adapter?.getItem(position)?.download ?: return
                val allDownloadsForSeries = adapter?.currentItems
                    ?.filter { download.manga.id == it.download.manga.id }
                    ?.map(DownloadItem::download)
                if (!allDownloadsForSeries.isNullOrEmpty()) {
                    presenter.cancelDownloads(allDownloadsForSeries)
                }
            }
        }
    }
}
