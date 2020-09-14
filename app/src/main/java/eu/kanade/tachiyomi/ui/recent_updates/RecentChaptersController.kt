package eu.kanade.tachiyomi.ui.recent_updates

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.DownloadService
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.library.LibraryUpdateService
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.ui.base.controller.BaseController
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.ui.manga.chapter.BaseChapterAdapter
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.util.system.notificationManager
import eu.kanade.tachiyomi.util.view.scrollViewWith
import eu.kanade.tachiyomi.util.view.setStyle
import eu.kanade.tachiyomi.util.view.snack
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import kotlinx.android.synthetic.main.download_bottom_sheet.*
import kotlinx.android.synthetic.main.recent_chapters_controller.*
import kotlinx.android.synthetic.main.recent_chapters_controller.empty_view
import timber.log.Timber

/**
 * Fragment that shows recent chapters.
 * Uses [R.layout.recent_chapters_controller].
 * UI related actions should be called from here.
 */
class RecentChaptersController(bundle: Bundle? = null) :
    BaseController(bundle),
    FlexibleAdapter.OnItemClickListener,
    FlexibleAdapter.OnUpdateListener,
    FlexibleAdapter.OnItemMoveListener,
    RecentChaptersAdapter.OnCoverClickListener,
    BaseChapterAdapter.DownloadInterface {

    /**
     * Adapter containing the recent chapters.
     */
    var adapter: RecentChaptersAdapter? = null
        private set

    private var presenter = RecentChaptersPresenter(this)
    private var snack: Snackbar? = null
    private var lastChapterId: Long? = null

    override fun getTitle(): String? {
        return resources?.getString(R.string.recent_updates)
    }

    override fun inflateView(inflater: LayoutInflater, container: ViewGroup): View {
        return inflater.inflate(R.layout.recent_chapters_controller, container, false)
    }

    /**
     * Called when view is created
     * @param view created view
     */
    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        // view.applyWindowInsetsForController()

        view.context.notificationManager.cancel(Notifications.ID_NEW_CHAPTERS)
        // Init RecyclerView and adapter
        val layoutManager = LinearLayoutManager(view.context)
        recycler.layoutManager = layoutManager
        recycler.addItemDecoration(DividerItemDecoration(view.context, DividerItemDecoration.VERTICAL))
        recycler.setHasFixedSize(true)
        adapter = RecentChaptersAdapter(this@RecentChaptersController)
        recycler.adapter = adapter

        adapter?.isSwipeEnabled = true
        adapter?.itemTouchHelperCallback?.setSwipeFlags(
            ItemTouchHelper.LEFT
        )
        if (presenter.chapters.isNotEmpty()) adapter?.updateDataSet(presenter.chapters.toList())
        swipe_refresh.setStyle()
        swipe_refresh.setDistanceToTriggerSync((2 * 64 * view.resources.displayMetrics.density).toInt())
        swipe_refresh.setOnRefreshListener {
            if (!LibraryUpdateService.isRunning()) {
                LibraryUpdateService.start(view.context)
                snack = view.snack(R.string.updating_library)
            }
            // It can be a very long operation, so we disable swipe refresh and show a snackbar.
            swipe_refresh.isRefreshing = false
        }

        scrollViewWith(recycler, swipeRefreshLayout = swipe_refresh, padBottom = true)

        presenter.onCreate()
    }

    override fun onDestroy() {
        super.onDestroy()
        presenter.onDestroy()
    }

    override fun onDestroyView(view: View) {
        adapter = null
        snack = null
        super.onDestroyView(view)
    }

    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)
        if (view != null) {
            refresh()
            dl_bottom_sheet?.update()
        }
    }

    fun refresh() = presenter.getUpdates()

    /**
     * Called when item in list is clicked
     * @param position position of clicked item
     */
    override fun onItemClick(view: View?, position: Int): Boolean {
        val adapter = adapter ?: return false

        // Get item from position
        val item = adapter.getItem(position) as? RecentChapterItem ?: return false
        openChapter(item)
        return false
    }

    /**
     * Open chapter in reader
     * @param chapter selected chapter
     */
    private fun openChapter(item: RecentChapterItem) {
        val activity = activity ?: return
        val intent = ReaderActivity.newIntent(activity, item.manga, item.chapter)
        startActivity(intent)
    }

    /**
     * Populate adapter with chapters
     * @param chapters list of [Any]
     */
    fun onNextRecentChapters(chapters: List<RecentChapterItem>) {
        adapter?.setItems(chapters)
    }

    fun updateChapterDownload(download: Download) {
        if (view == null) return
        val id = download.chapter.id ?: return
        val holder = recycler.findViewHolderForItemId(id) as? RecentChapterHolder ?: return
        holder.notifyStatus(download.status, download.progress)
    }

    override fun onUpdateEmptyView(size: Int) {
        if (size > 0) {
            empty_view?.hide()
        } else {
            empty_view?.show(R.drawable.ic_update_24dp, R.string.no_recent_chapters)
        }
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int) { }
    override fun shouldMoveItem(fromPosition: Int, toPosition: Int) = true

    override fun onActionStateChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        swipe_refresh.isEnabled = actionState != ItemTouchHelper.ACTION_STATE_SWIPE
    }

    /**
     * Update download status of chapter
     * @param download [Download] object containing download progress.
     */
    fun onChapterStatusChange(download: Download) {
        getHolder(download)?.notifyStatus(download.status, download.progress)
    }

    /**
     * Returns holder belonging to chapter
     * @param download [Download] object containing download progress.
     */
    private fun getHolder(download: Download): RecentChapterHolder? {
        return recycler?.findViewHolderForItemId(download.chapter.id!!) as? RecentChapterHolder
    }

    /**
     * Mark chapter as read
     * @param position position of chapter item
     */
    fun toggleMarkAsRead(position: Int) {
        val item = adapter?.getItem(position) as? RecentChapterItem ?: return
        val chapter = item.chapter
        val lastRead = chapter.last_page_read
        val pagesLeft = chapter.pages_left
        val read = item.chapter.read
        lastChapterId = chapter.id
        presenter.markChapterRead(item, !read)
        if (!read) {
            snack = view?.snack(R.string.marked_as_read, Snackbar.LENGTH_INDEFINITE) {
                var undoing = false
                setAction(R.string.undo) {
                    presenter.markChapterRead(item, read, lastRead, pagesLeft)
                    undoing = true
                }
                addCallback(
                    object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                        override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                            super.onDismissed(transientBottomBar, event)
                            if (!undoing && presenter.preferences.removeAfterMarkedAsRead()) {
                                lastChapterId = chapter.id
                                presenter.deleteChapter(chapter, item.manga)
                            }
                        }
                    }
                )
            }
            (activity as? MainActivity)?.setUndoSnackBar(snack)
        }
        // presenter.markChapterRead(item, !item.chapter.read)
    }

    override fun downloadChapter(position: Int) {
        val view = view ?: return
        val item = adapter?.getItem(position) as? RecentChapterItem ?: return
        val chapter = item.chapter
        val manga = item.manga
        if (item.status != Download.NOT_DOWNLOADED && item.status != Download.ERROR) {
            presenter.deleteChapter(chapter, manga)
        } else {
            if (item.status == Download.ERROR) DownloadService.start(view.context)
            else presenter.downloadChapters(listOf(item))
        }
    }

    override fun startDownloadNow(position: Int) {
        val chapter = (adapter?.getItem(position) as? RecentChapterItem)?.chapter ?: return
        presenter.startDownloadChapterNow(chapter)
    }

    override fun onCoverClick(position: Int) {
        val chapterClicked = adapter?.getItem(position) as? RecentChapterItem ?: return
        openManga(chapterClicked)
    }

    fun openManga(chapter: RecentChapterItem) {
        router.pushController(MangaDetailsController(chapter.manga).withFadeTransaction())
    }

    /**
     * Called when chapters are deleted
     */
    fun onChaptersDeleted() {
        adapter?.notifyDataSetChanged()
    }

    /**
     * Called when error while deleting
     * @param error error message
     */
    fun onChaptersDeletedError(error: Throwable) {
        Timber.e(error)
    }
}
