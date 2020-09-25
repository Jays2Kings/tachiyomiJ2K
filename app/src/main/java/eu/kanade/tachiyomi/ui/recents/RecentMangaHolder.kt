package eu.kanade.tachiyomi.ui.recents

import android.app.Activity
import android.view.View
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.image.coil.loadLibraryManga
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.ui.manga.chapter.BaseChapterHolder
import eu.kanade.tachiyomi.util.chapter.ChapterUtil
import eu.kanade.tachiyomi.util.system.timeSpanFromNow
import eu.kanade.tachiyomi.util.view.visibleIf
import kotlinx.android.synthetic.main.download_button.*
import kotlinx.android.synthetic.main.recent_manga_item.*

class RecentMangaHolder(
    view: View,
    val adapter: RecentMangaAdapter
) : BaseChapterHolder(view, adapter) {

    init {
        card_layout?.setOnClickListener { adapter.delegate.onCoverClick(adapterPosition) }
    }

    fun bind(recentsType: Int) {
        when (recentsType) {
            RecentMangaHeaderItem.CONTINUE_READING -> {
                title.setText(R.string.view_history)
            }
            RecentMangaHeaderItem.NEW_CHAPTERS -> {
                title.setText(R.string.view_all_updates)
            }
        }
    }

    fun bind(item: RecentMangaItem) {
        download_button.visibleIf(item.mch.manga.source != LocalSource.ID)

        title.apply {
            text = item.chapter.name
            ChapterUtil.setTextViewForChapter(this, item)
        }
        subtitle.apply {
            text = item.mch.manga.title
            setTextColor(ChapterUtil.readColor(context, item))
        }
        val notValidNum = item.mch.chapter.chapter_number <= 0
        body.text = when {
            item.mch.chapter.id == null -> body.context.getString(
                R.string.added_,
                item.mch.manga.date_added.timeSpanFromNow
            )
            item.mch.history.id == null -> body.context.getString(
                R.string.updated_,
                item.chapter.date_upload.timeSpanFromNow
            )
            item.chapter.id != item.mch.chapter.id ->
                body.context.getString(
                    R.string.read_,
                    item.mch.history.last_read.timeSpanFromNow
                ) + "\n" + body.context.getString(
                    if (notValidNum) R.string.last_read_ else R.string.last_read_chapter_,
                    if (notValidNum) item.mch.chapter.name else adapter.decimalFormat.format(item.mch.chapter.chapter_number)
                )
            item.chapter.pages_left > 0 && !item.chapter.read ->
                body.context.getString(
                    R.string.read_,
                    item.mch.history.last_read.timeSpanFromNow
                ) + "\n" + itemView.resources.getQuantityString(
                    R.plurals.pages_left,
                    item.chapter.pages_left,
                    item.chapter.pages_left
                )
            else -> body.context.getString(
                R.string.read_,
                item.mch.history.last_read.timeSpanFromNow
            )
        }
        if ((itemView.context as? Activity)?.isDestroyed != true) {
            cover_thumbnail.loadLibraryManga(item.mch.manga)
        }
        notifyStatus(
            if (adapter.isSelected(adapterPosition)) Download.CHECKED else item.status,
            item.progress
        )
        resetFrontView()
    }

    private fun resetFrontView() {
        if (front_view.translationX != 0f) itemView.post { adapter.notifyItemChanged(adapterPosition) }
    }

    override fun onLongClick(view: View?): Boolean {
        super.onLongClick(view)
        val item = adapter.getItem(adapterPosition) as? RecentMangaItem ?: return false
        return item.mch.history.id != null
    }

    fun notifyStatus(status: Int, progress: Int) =
        download_button.setDownloadStatus(status, progress)

    override fun getFrontView(): View {
        return front_view
    }

    override fun getRearRightView(): View {
        return right_view
    }
}
