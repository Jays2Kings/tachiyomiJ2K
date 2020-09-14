package eu.kanade.tachiyomi.ui.download

import android.view.View
import androidx.appcompat.widget.PopupMenu
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.view.setVectorCompat
import eu.kanade.tachiyomi.util.view.visibleIf
import kotlinx.android.synthetic.main.download_item.*

/**
 * Class used to hold the data of a download.
 * All the elements from the layout file "download_item" are available in this class.
 *
 * @param view the inflated view for this holder.
 * @constructor creates a new download holder.
 */
class DownloadHolder(private val view: View, val adapter: DownloadAdapter) :
    BaseFlexibleViewHolder(view, adapter) {

    init {
        setDragHandleView(reorder)
        migration_menu.setOnClickListener { it.post { showPopupMenu(it) } }
    }

    private lateinit var download: Download

    /**
     * Binds this holder with the given category.
     *
     * @param category The category to bind.
     */
    fun bind(download: Download) {
        this.download = download
        // Update the chapter name.
        chapter_title.text = download.chapter.name

        // Update the manga title
        title.text = download.manga.title

        // Update the progress bar and the number of downloaded pages
        val pages = download.pages
        if (pages == null) {
            download_progress.progress = 0
            download_progress.max = 1
            download_progress_text.text = ""
        } else {
            download_progress.max = pages.size * 100
            notifyProgress()
            notifyDownloadedPages()
        }

        migration_menu.visibleIf(adapterPosition != 0 || adapterPosition != adapter.itemCount - 1)
        migration_menu.setVectorCompat(
            R.drawable.ic_more_vert_24dp,
            view.context
                .getResourceColor(android.R.attr.textColorPrimary)
        )
    }

    /**
     * Updates the progress bar of the download.
     */
    fun notifyProgress() {
        val pages = download.pages ?: return
        if (download_progress.max == 1) {
            download_progress.max = pages.size * 100
        }
        download_progress.progress = download.pageProgress
    }

    /**
     * Updates the text field of the number of downloaded pages.
     */
    fun notifyDownloadedPages() {
        val pages = download.pages ?: return
        download_progress_text.text = "${download.downloadedImages}/${pages.size}"
    }

    override fun onItemReleased(position: Int) {
        super.onItemReleased(position)
        adapter.downloadItemListener.onItemReleased(position)
    }

    private fun showPopupMenu(view: View) {
        val item = adapter.getItem(adapterPosition) ?: return

        // Create a PopupMenu, giving it the clicked view for an anchor
        val popup = PopupMenu(view.context, view)

        // Inflate our menu resource into the PopupMenu's Menu
        popup.menuInflater.inflate(R.menu.download_single, popup.menu)

        val download = item.download

        popup.menu.findItem(R.id.move_to_top).isVisible = adapterPosition != 0
        popup.menu.findItem(R.id.move_to_bottom).isVisible = adapterPosition != adapter
            .itemCount - 1

        // Set a listener so we are notified if a menu item is clicked
        popup.setOnMenuItemClickListener { menuItem ->
            adapter.downloadItemListener.onMenuItemClick(adapterPosition, menuItem)
            true
        }

        // Finally show the PopupMenu
        popup.show()
    }

    override fun getFrontView(): View {
        return front_view
    }

    override fun getRearRightView(): View {
        return right_view
    }

    override fun getRearLeftView(): View {
        return left_view
    }
}
