package eu.kanade.tachiyomi.ui.download

import android.view.MenuItem
import eu.davidea.flexibleadapter.FlexibleAdapter

/**
 * Adapter storing a list of downloads.
 *
 * @param context the context of the fragment containing this adapter.
 */
class DownloadAdapter(controller: DownloadItemListener) : FlexibleAdapter<DownloadItem>(
    null,
    controller,
    true
) {

    /**
     * Listener called when an item of the list is released.
     */
    val downloadItemListener: DownloadItemListener = controller

    interface DownloadItemListener {
        /**
         * Called when an item of the list is released.
         */
        fun onItemReleased(position: Int)
        fun onItemRemoved(position: Int)
        fun onMenuItemClick(position: Int, menuItem: MenuItem)
    }

    override fun onItemSwiped(position: Int, direction: Int) {
        super.onItemSwiped(position, direction)
        downloadItemListener.onItemRemoved(position)
    }

    override fun onCreateBubbleText(position: Int): String {
        return getItem(position)?.download?.manga?.title ?: ""
    }
}
