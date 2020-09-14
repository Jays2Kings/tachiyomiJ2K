package eu.kanade.tachiyomi.ui.recent_updates

import android.app.Activity
import android.view.View
import androidx.core.content.ContextCompat
import coil.api.clear
import coil.transform.CircleCropTransformation
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.image.coil.loadLibraryManga
import eu.kanade.tachiyomi.ui.manga.chapter.BaseChapterHolder
import eu.kanade.tachiyomi.util.chapter.ChapterUtil
import eu.kanade.tachiyomi.util.system.getResourceColor
import kotlinx.android.synthetic.main.download_button.*
import kotlinx.android.synthetic.main.recent_chapters_item.*

/**
 * Holder that contains chapter item
 * Uses R.layout.item_recent_chapters.
 * UI related actions should be called from here.
 *
 * @param view the inflated view for this holder.
 * @param adapter the adapter handling this holder.
 * @param listener a listener to react to single tap and long tap events.
 * @constructor creates a new recent chapter holder.
 */
class RecentChapterHolder(private val view: View, private val adapter: RecentChaptersAdapter) :
    BaseChapterHolder(view, adapter) {

    /**
     * Color of read chapter
     */
    private var readColor = view.context.getResourceColor(android.R.attr.textColorHint)

    /**
     * Color of unread chapter
     */
    private var unreadColor = view.context.getResourceColor(android.R.attr.textColorPrimary)

    /**
     * Currently bound item.
     */
    private var item: RecentChapterItem? = null

    init {
        manga_cover.setOnClickListener {
            adapter.coverClickListener.onCoverClick(adapterPosition)
        }
    }

    /**
     * Set values of view
     *
     * @param item item containing chapter information
     */
    fun bind(item: RecentChapterItem) {
        this.item = item

        // Set chapter title
        chapter_title.text = item.chapter.name

        // Set manga title
        title.text = item.manga.title

        if (front_view.translationX == 0f) {
            read.setImageDrawable(
                ContextCompat.getDrawable(
                    read.context,
                    if (item.read) R.drawable.ic_eye_off_24dp
                    else R.drawable.ic_eye_24dp
                )
            )
        }

        // Set cover
        if ((view.context as? Activity)?.isDestroyed != true) {
            manga_cover.clear()
            manga_cover.loadLibraryManga(item.manga) {
                transformations(CircleCropTransformation())
            }
        }

        val chapterColor = ChapterUtil.chapterColor(itemView.context, item)
        chapter_title.setTextColor(chapterColor)
        title.setTextColor(chapterColor)

        // Set chapter status
        notifyStatus(item.status, item.progress)
        resetFrontView()
    }

    private fun resetFrontView() {
        if (front_view.translationX != 0f) itemView.post { adapter.notifyItemChanged(adapterPosition) }
    }

    override fun getFrontView(): View {
        return front_view
    }

    override fun getRearRightView(): View {
        return right_view
    }

    /**
     * Updates chapter status in view.
     *
     * @param status download status
     */
    fun notifyStatus(status: Int, progress: Int) =
        download_button.setDownloadStatus(status, progress)
}
