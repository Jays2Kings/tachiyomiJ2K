package eu.kanade.tachiyomi.ui.manga.chapter

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.ui.manga.MangaDetailsAdapter
import eu.kanade.tachiyomi.util.chapter.ChapterUtil
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.isVisible
import eu.kanade.tachiyomi.util.view.visible
import eu.kanade.tachiyomi.util.view.visibleIf
import eu.kanade.tachiyomi.widget.EndAnimatorListener
import eu.kanade.tachiyomi.widget.StartAnimatorListener
import kotlinx.android.synthetic.main.chapters_item.*
import kotlinx.android.synthetic.main.download_button.*

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
        download_button.visibleIf(!localSource && !isLocked)

        ChapterUtil.setTextViewForChapter(chapter_title, item, hideStatus = isLocked)

        val statuses = mutableListOf<String>()

        ChapterUtil.relativeDate(chapter)?.let { statuses.add(it) }

        val showPagesLeft = !chapter.read && chapter.last_page_read > 0 && !isLocked

        if (showPagesLeft && chapter.pages_left > 0) {
            statuses.add(
                itemView.resources.getQuantityString(
                    R.plurals.pages_left,
                    chapter.pages_left,
                    chapter.pages_left
                )
            )
        } else if (showPagesLeft) {
            statuses.add(
                itemView.context.getString(
                    R.string.page_,
                    chapter.last_page_read + 1
                )
            )
        }

        chapter.scanlator?.isNotBlank()?.let { statuses.add(chapter.scanlator!!) }

        if (front_view.translationX == 0f) {
            read.setImageResource(
                if (item.read) R.drawable.ic_eye_off_24dp else R.drawable.ic_eye_24dp
            )
            bookmark.setImageResource(
                if (item.bookmark) R.drawable.ic_bookmark_off_24dp else R.drawable.ic_bookmark_24dp
            )
        }
        // this will color the scanlator the same bookmarks
        ChapterUtil.setTextViewForChapter(
            chapter_scanlator,
            item,
            showBookmark = false,
            hideStatus = isLocked
        )
        chapter_scanlator.text = statuses.joinToString(" • ")

        val status = when {
            adapter.isSelected(adapterPosition) -> Download.CHECKED
            else -> item.status
        }

        notifyStatus(status, item.isLocked, item.progress)
        resetFrontView()
        if (adapterPosition == 1) {
            if (!adapter.hasShownSwipeTut.get()) showSlideAnimation()
        }
    }

    private fun showSlideAnimation() {
        val slide = 100f.dpToPx
        val animatorSet = AnimatorSet()
        val anim1 = slideAnimation(0f, slide)
        anim1.startDelay = 1000
        anim1.addListener(StartAnimatorListener { left_view.visible() })
        val anim2 = slideAnimation(slide, -slide)
        anim2.duration = 600
        anim2.startDelay = 500
        anim2.addUpdateListener {
            if (left_view.isVisible() && front_view.translationX <= 0) {
                left_view.gone()
                right_view.visible()
            }
        }
        val anim3 = slideAnimation(-slide, 0f)
        anim3.startDelay = 750
        animatorSet.playSequentially(anim1, anim2, anim3)
        animatorSet.addListener(
            EndAnimatorListener {
                adapter.hasShownSwipeTut.set(true)
            }
        )
        animatorSet.start()
    }

    private fun slideAnimation(from: Float, to: Float): ObjectAnimator {
        return ObjectAnimator.ofFloat(front_view, View.TRANSLATION_X, from, to)
            .setDuration(300)
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
