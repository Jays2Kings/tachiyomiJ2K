package eu.kanade.tachiyomi.ui.manga.chapter

import android.text.format.DateUtils
import android.view.View
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.ui.manga.MangaDetailsAdapter
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.visibleIf
import kotlinx.android.synthetic.main.chapters_item.*
import kotlinx.android.synthetic.main.download_button.*
import java.util.Date

class ChapterHolder(
    view: View,
    private val adapter: MangaDetailsAdapter
) : BaseChapterHolder(view, adapter) {

    private var localSource = false
    init {
        download_button.setOnLongClickListener {
            adapter.delegate.startDownloadRange(adapterPosition)
            true
        }
    }

    fun bind(item: ChapterItem, manga: Manga) {
        val chapter = item.chapter
        val isLocked = item.isLocked
        chapter_title.text = when (manga.displayMode) {
            Manga.DISPLAY_NUMBER -> {
                val number = adapter.decimalFormat.format(chapter.chapter_number.toDouble())
                itemView.context.getString(R.string.chapter_, number)
            }
            else -> chapter.name
        }

        localSource = manga.source == LocalSource.ID
        download_button.visibleIf(!localSource)

        if (isLocked) download_button.gone()
        if (chapter.read) download_button.alpha = .3f

        // Set correct text color
        chapter_title.setTextColor(
            if (chapter.read && !isLocked) adapter.readColor else adapter.unreadColor
        )
        if (chapter.bookmark && !isLocked) chapter_title.setTextColor(adapter.bookmarkedColor)

        val statuses = mutableListOf<String>()

        if (chapter.date_upload > 0) {
            statuses.add(
                DateUtils.getRelativeTimeSpanString(
                    chapter.date_upload, Date().time, DateUtils.HOUR_IN_MILLIS
                ).toString()
            )
        }

        if (!chapter.read && chapter.last_page_read > 0 && chapter.pages_left > 0 && !isLocked) {
            statuses.add(
                itemView.resources.getQuantityString(
                    R.plurals.pages_left, chapter.pages_left, chapter.pages_left
                )
            )
        } else if (!chapter.read && chapter.last_page_read > 0 && !isLocked) {
            statuses.add(
                itemView.context.getString(
                    R.string.page_, chapter.last_page_read + 1
                )
            )
        }

        if (!chapter.scanlator.isNullOrBlank()) {
            statuses.add(chapter.scanlator!!)
        }

        if (front_view.translationX == 0f) {
            read.setImageDrawable(
                ContextCompat.getDrawable(
                    read.context, if (item.read) R.drawable.ic_eye_off_24dp
                    else R.drawable.ic_eye_24dp
                )
            )
            bookmark.setImageDrawable(
                ContextCompat.getDrawable(
                    read.context, if (item.bookmark) R.drawable.ic_bookmark_off_24dp
                    else R.drawable.ic_bookmark_24dp
                )
            )
        }
        chapter_scanlator.setTextColor(if (chapter.read) adapter.readColor else adapter.unreadColor)
        chapter_scanlator.text = statuses.joinToString(" • ")
        notifyStatus(
            if (adapter.isSelected(adapterPosition)) Download.CHECKED else item.status,
            item.isLocked,
            item.progress
        )
        resetFrontView()
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

    private fun resetFrontView() {
        if (front_view.translationX != 0f) itemView.post { adapter.notifyItemChanged(adapterPosition) }
    }

    fun notifyStatus(status: Int, locked: Boolean, progress: Int) = with(download_button) {
        if (locked) {
            gone()
            return
        }
        download_button.visibleIf(!localSource)
        setDownloadStatus(status, progress)
    }
}
