package eu.kanade.tachiyomi.ui.manga.chapter

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import androidx.core.view.isVisible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.databinding.ChaptersItemBinding
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.ui.manga.MangaDetailsAdapter
import eu.kanade.tachiyomi.util.chapter.ChapterUtil
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.widget.EndAnimatorListener
import eu.kanade.tachiyomi.widget.StartAnimatorListener

class ChapterHolder(
    view: View,
    private val adapter: MangaDetailsAdapter
) : BaseChapterHolder(view, adapter) {

    private val binding = ChaptersItemBinding.bind(view)
    private var localSource = false

    init {
        binding.downloadButton.downloadButton.setOnLongClickListener {
            adapter.delegate.startDownloadRange(flexibleAdapterPosition)
            true
        }
    }

    fun bind(item: ChapterItem, manga: Manga) {
        val chapter = item.chapter
        val isLocked = item.isLocked
        binding.chapterTitle.text = if (manga.hideChapterTitle(adapter.preferences)) {
            val number = adapter.decimalFormat.format(chapter.chapter_number.toDouble())
            itemView.context.getString(R.string.chapter_, number)
        } else chapter.name

        localSource = manga.source == LocalSource.ID
        binding.downloadButton.downloadButton.isVisible = !localSource && !isLocked

        ChapterUtil.setTextViewForChapter(binding.chapterTitle, item, hideStatus = isLocked)

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

        if (chapter.scanlator?.isNotBlank() == true) {
            statuses.add(chapter.scanlator!!)
        }

        if (binding.frontView.translationX == 0f) {
            binding.read.setImageResource(
                if (item.read) R.drawable.ic_eye_off_24dp else R.drawable.ic_eye_24dp
            )
            binding.bookmark.setImageResource(
                if (item.bookmark) R.drawable.ic_bookmark_off_24dp else R.drawable.ic_bookmark_24dp
            )
        }
        // this will color the scanlator the same bookmarks
        ChapterUtil.setTextViewForChapter(
            binding.chapterScanlator,
            item,
            showBookmark = false,
            hideStatus = isLocked
        )
        binding.chapterScanlator.text = statuses.joinToString(" • ")

        val status = when {
            adapter.isSelected(flexibleAdapterPosition) -> Download.State.CHECKED
            else -> item.status
        }

        notifyStatus(status, item.isLocked, item.progress)
        resetFrontView()
        if (flexibleAdapterPosition == 1) {
            if (!adapter.hasShownSwipeTut.get()) showSlideAnimation()
        }
    }

    private fun showSlideAnimation() {
        val slide = 100f.dpToPx
        val animatorSet = AnimatorSet()
        val anim1 = slideAnimation(0f, slide)
        anim1.startDelay = 1000
        anim1.addListener(StartAnimatorListener { binding.startView.isVisible = true })
        val anim2 = slideAnimation(slide, -slide)
        anim2.duration = 600
        anim2.startDelay = 500
        anim2.addUpdateListener {
            if (binding.startView.isVisible && binding.frontView.translationX <= 0) {
                binding.startView.isVisible = false
                binding.endView.isVisible = true
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
        return ObjectAnimator.ofFloat(binding.frontView, View.TRANSLATION_X, from, to)
            .setDuration(300)
    }

    override fun getFrontView(): View {
        return binding.frontView
    }

    override fun getRearEndView(): View {
        return binding.endView
    }

    override fun getRearStartView(): View {
        return binding.startView
    }

    private fun resetFrontView() {
        if (binding.frontView.translationX != 0f) itemView.post { adapter.notifyItemChanged(flexibleAdapterPosition) }
    }

    fun notifyStatus(status: Download.State, locked: Boolean, progress: Int, animated: Boolean = false) = with(binding.downloadButton.downloadButton) {
        if (locked) {
            isVisible = false
            return
        }
        isVisible = !localSource
        setDownloadStatus(status, progress, animated)
    }
}
