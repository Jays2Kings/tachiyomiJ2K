package eu.kanade.tachiyomi.ui.reader.viewer

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.core.view.isVisible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.databinding.ReaderTransitionViewBinding
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.util.system.contextCompatDrawable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ReaderTransitionView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    LinearLayout(context, attrs) {

    private val binding: ReaderTransitionViewBinding =
        ReaderTransitionViewBinding.inflate(LayoutInflater.from(context), this, true)

    init {
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    }

    fun bind(transition: ChapterTransition) {
        when (transition) {
            is ChapterTransition.Prev -> bindPrevChapterTransition(transition)
            is ChapterTransition.Next -> bindNextChapterTransition(transition)
        }

        missingChapterWarning(transition)
    }

    /**
     * Binds a previous chapter transition on this view and subscribes to the page load status.
     */
    private fun bindPrevChapterTransition(transition: ChapterTransition) {
        val prevChapter = transition.to

        val hasPrevChapter = prevChapter != null
        binding.lowerText.isVisible = hasPrevChapter
        if (hasPrevChapter) {
            binding.upperText.textAlignment = TEXT_ALIGNMENT_TEXT_START
            val downloadManager = Injekt.get<DownloadManager>()
            val db = Injekt.get<DatabaseHelper>()
            val manga = db.getManga(prevChapter!!.chapter.manga_id!!).executeAsBlocking()!!
            val isPrevDownloaded = downloadManager.isChapterDownloaded(prevChapter.chapter, manga)
            val isCurrentDownloaded =
                downloadManager.isChapterDownloaded(transition.from.chapter, manga)

            binding.upperText.text = buildSpannedString {
                bold { append(context.getString(R.string.previous_title)) }
                append("\n${prevChapter.chapter.name}")
            }
            binding.lowerText.text = buildSpannedString {
                bold { append(context.getString(R.string.current_chapter)) }
                append("\n${transition.from.chapter.name}")
            }

            val downloadIcon = context.contextCompatDrawable(R.drawable.ic_file_download_24dp)?.mutate()
            val cloudIcon = context.contextCompatDrawable(R.drawable.ic_cloud_24dp)?.mutate()
            binding.lowerImage.visibility = View.INVISIBLE

            if (!isCurrentDownloaded && isPrevDownloaded) binding.upperImage.setDrawable(downloadIcon)
            else if (isCurrentDownloaded && !isPrevDownloaded) binding.upperImage.setDrawable(cloudIcon)
            else if (!isCurrentDownloaded && !isPrevDownloaded) {
                binding.lowerImage.visibility = View.GONE
                binding.upperImage.visibility = View.GONE
            }
        } else {
            binding.upperText.textAlignment = TEXT_ALIGNMENT_CENTER
            binding.upperText.text = context.getString(R.string.theres_no_previous_chapter)
        }
    }

    /**
     * Binds a next chapter transition on this view and subscribes to the load status.
     */
    private fun bindNextChapterTransition(transition: ChapterTransition) {
        val nextChapter = transition.to

        val hasNextChapter = nextChapter != null
        binding.lowerText.isVisible = hasNextChapter
        if (hasNextChapter) {
            binding.upperText.textAlignment = TEXT_ALIGNMENT_TEXT_START
            val downloadManager = Injekt.get<DownloadManager>()
            val db = Injekt.get<DatabaseHelper>()
            val manga = db.getManga(nextChapter!!.chapter.manga_id!!).executeAsBlocking()!!
            val isCurrentDownloaded =
                downloadManager.isChapterDownloaded(transition.from.chapter, manga)
            val isNextDownloaded = downloadManager.isChapterDownloaded(nextChapter.chapter, manga)
            binding.upperText.text = buildSpannedString {
                bold { append(context.getString(R.string.finished_chapter)) }
                append("\n${transition.from.chapter.name}")
            }
            binding.lowerText.text = buildSpannedString {
                bold { append(context.getString(R.string.next_title)) }
                append("\n${nextChapter.chapter.name}")
            }

            val downloadIcon = context.contextCompatDrawable(R.drawable.ic_file_download_24dp)?.mutate()
            val cloudIcon = context.contextCompatDrawable(R.drawable.ic_cloud_24dp)?.mutate()
            binding.upperImage.visibility = View.INVISIBLE

            if (!isCurrentDownloaded && isNextDownloaded) binding.lowerImage.setDrawable(downloadIcon)
            else if (isCurrentDownloaded && !isNextDownloaded) binding.lowerImage.setDrawable(cloudIcon)
            else if (!isCurrentDownloaded && !isNextDownloaded) {
                binding.lowerImage.visibility = View.GONE
                binding.upperImage.visibility = View.GONE
            }
        } else {
            binding.upperText.textAlignment = TEXT_ALIGNMENT_CENTER
            binding.upperText.text = context.getString(R.string.theres_no_next_chapter)
        }
    }

    private fun ImageView.setDrawable(drawable: Drawable?) {
        setImageDrawable(drawable)
        drawable?.setTint(binding.upperText.currentTextColor)
        visibility = View.VISIBLE
    }

    fun setTextColors(@ColorInt color: Int) {
        binding.upperText.setTextColor(color)
        binding.warningText.setTextColor(color)
        binding.lowerText.setTextColor(color)
    }

    private fun missingChapterWarning(transition: ChapterTransition) {
        if (transition.to == null) {
            binding.warning.isVisible = false
            return
        }

        val hasMissingChapters = when (transition) {
            is ChapterTransition.Prev -> hasMissingChapters(transition.from, transition.to)
            is ChapterTransition.Next -> hasMissingChapters(transition.to, transition.from)
        }

        if (!hasMissingChapters) {
            binding.warning.isVisible = false
            return
        }

        val chapterDifference = when (transition) {
            is ChapterTransition.Prev -> calculateChapterDifference(transition.from, transition.to)
            is ChapterTransition.Next -> calculateChapterDifference(transition.to, transition.from)
        }

        binding.warningText.text = resources.getQuantityString(R.plurals.missing_chapters_warning, chapterDifference.toInt(), chapterDifference.toInt())
        binding.warning.isVisible = true
    }
}
