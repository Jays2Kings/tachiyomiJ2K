package eu.kanade.tachiyomi.ui.recents

import android.animation.LayoutTransition
import android.annotation.SuppressLint
import android.app.Activity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import androidx.core.view.isEmpty
import androidx.core.view.isVisible
import androidx.core.view.marginStart
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePaddingRelative
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.CornerFamily
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.ChapterHistory
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.image.coil.loadManga
import eu.kanade.tachiyomi.databinding.RecentMangaItemBinding
import eu.kanade.tachiyomi.databinding.RecentSubChapterItemBinding
import eu.kanade.tachiyomi.ui.download.DownloadButton
import eu.kanade.tachiyomi.ui.manga.chapter.BaseChapterHolder
import eu.kanade.tachiyomi.util.chapter.ChapterUtil
import eu.kanade.tachiyomi.util.chapter.ChapterUtil.Companion.preferredChapterName
import eu.kanade.tachiyomi.util.isLocal
import eu.kanade.tachiyomi.util.system.contextCompatColor
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.timeSpanFromNow
import eu.kanade.tachiyomi.util.view.setAnimVectorCompat
import eu.kanade.tachiyomi.util.view.setCards
import java.util.Date
import java.util.concurrent.TimeUnit

class RecentMangaHolder(
    view: View,
    val adapter: RecentMangaAdapter,
) : BaseChapterHolder(view, adapter) {
    private val binding = RecentMangaItemBinding.bind(view)
    var chapterId: Long? = null

    private val isUpdates get() = adapter.viewType.isUpdates
    private val isSmallUpdates get() = isUpdates && !adapter.showUpdatedTime

    init {
        binding.cardLayout.setOnClickListener { adapter.delegate.onCoverClick(flexibleAdapterPosition) }
        binding.removeHistory.setOnClickListener { adapter.delegate.onRemoveHistoryClicked(flexibleAdapterPosition) }
        binding.showMoreChapters.setOnClickListener { _ ->
            val position = flexibleAdapterPosition
            val item = adapter.getItem(position) as? RecentMangaItem ?: return@setOnClickListener
            val moreVisible = !binding.moreChaptersLayout.isVisible
            if (moreVisible) {
                buildSubChapters(item)
            }
            binding.moreChaptersLayout.isVisible = moreVisible
            adapter.delegate.updateExpandedExtraChapters(position, moreVisible)
            binding.showMoreChapters.setAnimVectorCompat(
                if (moreVisible) {
                    R.drawable.anim_expand_more_to_less
                } else {
                    R.drawable.anim_expand_less_to_more
                },
            )
            updateBody(item, moreVisible)
            binding.endView.updateLayoutParams<ViewGroup.LayoutParams> {
                height = binding.recentCard.height
            }
            item.setCorners(position, this, adapter)
            val transition =
                TransitionSet()
                    .addTransition(androidx.transition.ChangeBounds())
                    .addTransition(androidx.transition.Slide())
            transition.duration =
                itemView.resources.getInteger(android.R.integer.config_shortAnimTime).toLong()
            TransitionManager.beginDelayedTransition(adapter.recyclerView, transition)
        }
        updateCards()
        binding.frontView.layoutTransition?.enableTransitionType(LayoutTransition.APPEARING)
    }

    fun updateCards() {
        setCards(adapter.showOutline, binding.card, null)
    }

    @SuppressLint("ClickableViewAccessibility")
    fun bind(item: RecentMangaItem) {
        val showDLs = adapter.showDownloads
        binding.recentCard.transitionName = "recents chapter $bindingAdapterPosition transition"
        val showRemoveHistory = adapter.showRemoveHistory
        val showTitleFirst = adapter.showTitleFirst
        binding.downloadButton.downloadButton.isVisible = when (showDLs) {
            RecentMangaAdapter.ShowRecentsDLs.None -> false
            RecentMangaAdapter.ShowRecentsDLs.OnlyUnread, RecentMangaAdapter.ShowRecentsDLs.UnreadOrDownloaded -> !item.chapter.read
            RecentMangaAdapter.ShowRecentsDLs.OnlyDownloaded -> true
            RecentMangaAdapter.ShowRecentsDLs.All -> true
        } &&
            !item.mch.manga.isLocal()

        binding.cardLayout.updateLayoutParams<ConstraintLayout.LayoutParams> {
            height = (if (isSmallUpdates) 40 else 80).dpToPx
            width = (if (isSmallUpdates) 40 else 60).dpToPx
        }
        listOf(binding.title, binding.subtitle).forEach {
            it.updateLayoutParams<ConstraintLayout.LayoutParams> {
                if (isSmallUpdates) {
                    if (it == binding.title) topMargin = 5.dpToPx
                    endToStart = R.id.button_layout
                    endToEnd = -1
                } else {
                    if (it == binding.title) topMargin = 2.dpToPx
                    endToStart = -1
                    endToEnd = R.id.main_view
                }
            }
        }
        binding.buttonLayout.updateLayoutParams<ConstraintLayout.LayoutParams> {
            if (isSmallUpdates) {
                topToBottom = -1
                topToTop = R.id.card_layout
                bottomToBottom = R.id.card_layout
                topMargin = 4.dpToPx
            } else {
                topToTop = -1
                topToBottom = R.id.subtitle
                bottomToBottom = R.id.main_view
                topMargin = 0
            }
        }
        val freeformCovers = !isSmallUpdates && !adapter.uniformCovers
        with(binding.coverThumbnail) {
            adjustViewBounds = freeformCovers
            scaleType = if (!freeformCovers) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
        }
        listOf(binding.coverThumbnail, binding.card).forEach {
            it.updateLayoutParams<ViewGroup.LayoutParams> {
                width =
                    if (!freeformCovers) {
                        ViewGroup.LayoutParams.MATCH_PARENT
                    } else {
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    }
            }
        }

        binding.removeHistory.isVisible = item.mch.history.id != null && showRemoveHistory
        val context = itemView.context
        val chapterName =
            item.chapter.preferredChapterName(context, item.mch.manga, adapter.preferences)

        listOf(binding.title, binding.subtitle).forEach {
            it.apply {
                setCompoundDrawablesRelative(null, null, null, null)
                translationX = 0f
                text =
                    if (!showTitleFirst.xor(it === binding.subtitle)) {
                        ChapterUtil.setTextViewForChapter(this, item)
                        chapterName
                    } else {
                        setTextColor(ChapterUtil.readColor(context, item))
                        item.mch.manga.title
                    }
            }
        }
        if (binding.frontView.translationX == 0f) {
            binding.read.setImageResource(
                if (item.read) R.drawable.ic_eye_off_24dp else R.drawable.ic_eye_24dp,
            )
        }

        binding.showMoreChapters.isVisible = item.mch.extraChapters.isNotEmpty() &&
            !adapter.delegate.alwaysExpanded()
        binding.moreChaptersLayout.isVisible = item.mch.extraChapters.isNotEmpty() &&
            adapter.delegate.areExtraChaptersExpanded(flexibleAdapterPosition)
        val moreVisible = binding.moreChaptersLayout.isVisible

        updateBody(item, moreVisible)
        if ((context as? Activity)?.isDestroyed != true) {
            binding.coverThumbnail.loadManga(item.mch.manga)
        }
        if (!item.mch.manga.isLocal()) {
            notifyStatus(
                if (adapter.isSelected(flexibleAdapterPosition)) Download.State.CHECKED else item.status,
                item.progress,
                item.chapter.read,
            )
        }

        binding.showMoreChapters.setImageResource(
            if (moreVisible) {
                R.drawable.ic_expand_less_24dp
            } else {
                R.drawable.ic_expand_more_24dp
            },
        )
        if (moreVisible) {
            buildSubChapters(item)
        } else {
            releaseSubChapters()
        }
        listOf(binding.mainView, binding.downloadButton.root, binding.showMoreChapters, binding.cardLayout).forEach {
            it.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    binding.endView.translationY = binding.recentCard.y
                    binding.endView.updateLayoutParams<ViewGroup.LayoutParams> {
                        height = binding.recentCard.height
                    }
                    binding.read.setImageResource(
                        if (item.read) R.drawable.ic_eye_off_24dp else R.drawable.ic_eye_24dp,
                    )
                    binding.endView.shapeAppearanceModel = binding.recentCard.shapeAppearanceModel
                    chapterId = null
                }
                false
            }
        }
    }

    /**
     * Builds the rows for [item]'s extra chapters, reusing whatever is already in the layout and
     * taking the rest from the adapter's pool.
     */
    private fun buildSubChapters(item: RecentMangaItem) {
        val context = itemView.context
        val extraChapterMaxSize = if (adapter.viewType.isHistory) 20 else 10
        val newChapters = item.mch.extraChapters.shorterList(extraChapterMaxSize)
        val extraIds =
            binding.moreChaptersLayout.children
                .toList()
                .map { it.findViewById<DownloadButton>(R.id.download_button)?.tag }
        if (extraIds == newChapters.map { it?.id }) {
            newChapters.forEachIndexed { index, chapter ->
                RecentSubChapterItemBinding
                    .bind(binding.moreChaptersLayout.getChildAt(index))
                    .configureView(chapter, item, extraChapterMaxSize)
            }
            return
        }
        // Reuse rows from the holder, then the adapter, or inflate a new one (in the adapter)
        val existingCount = binding.moreChaptersLayout.childCount
        newChapters.forEachIndexed { index, chapter ->
            val subBinding =
                if (index < existingCount) {
                    RecentSubChapterItemBinding.bind(binding.moreChaptersLayout.getChildAt(index))
                } else {
                    RecentSubChapterItemBinding
                        .bind(adapter.obtainSubChapterView(context, binding.moreChaptersLayout))
                        .also { binding.moreChaptersLayout.addView(it.root) }
                }
            subBinding.configureView(chapter, item, extraChapterMaxSize)
        }
        for (index in existingCount - 1 downTo newChapters.size) {
            val child = binding.moreChaptersLayout.getChildAt(index)
            binding.moreChaptersLayout.removeViewAt(index)
            adapter.recycleSubChapterView(child)
        }
        if (newChapters.isEmpty()) {
            chapterId = null
        }
    }

    /** Hands this group's extra rows back to the adapter's pool for another group to use. */
    fun releaseSubChapters() {
        if (binding.moreChaptersLayout.isEmpty()) return
        val children = binding.moreChaptersLayout.children.toList()
        binding.moreChaptersLayout.removeAllViews()
        children.forEach { adapter.recycleSubChapterView(it) }
        chapterId = null
    }

    private fun updateBody(
        item: RecentMangaItem,
        expanded: Boolean,
    ) {
        val context = itemView.context
        val body = binding.body
        body.maxLines = 2
        val scanlator = item.chapter.scanlator?.takeIf { expanded && item.hasScanlatorConflict() }
        if (scanlator != null && isSmallUpdates) {
            body.maxLines = 1
            body.text = scanlator
            body.isVisible = true
            return
        }
        body.isVisible = !isSmallUpdates
        val baseText =
            when {
                item.mch.chapter.id == null -> context.timeSpanFromNow(R.string.added_, item.mch.manga.date_added)
                isSmallUpdates -> ""
                item.mch.history.id == null -> {
                    if (isUpdates) {
                        if (adapter.sortByFetched) {
                            context.timeSpanFromNow(R.string.fetched_, item.chapter.date_fetch)
                        } else {
                            context.timeSpanFromNow(R.string.updated_, item.chapter.date_upload)
                        }
                    } else {
                        context.timeSpanFromNow(R.string.fetched_, item.chapter.date_fetch) + "\n" +
                            context.timeSpanFromNow(R.string.updated_, item.chapter.date_upload)
                    }
                }
                item.chapter.id != item.mch.chapter.id -> readLastText(item, !expanded)
                item.chapter.pages_left > 0 && !item.chapter.read ->
                    context.timeSpanFromNow(R.string.read_, item.mch.history.last_read) +
                        "\n" +
                        itemView.resources.getQuantityString(
                            R.plurals.pages_left,
                            item.chapter.pages_left,
                            item.chapter.pages_left,
                        )
                else -> context.timeSpanFromNow(R.string.read_, item.mch.history.last_read)
            }
        val andMoreText =
            itemView.context.resources
                .getQuantityString(
                    R.plurals.notification_and_n_more,
                    item.mch.extraChapters.size,
                    item.mch.extraChapters.size,
                ).takeIf {
                    !expanded && isUpdates && !isSmallUpdates && item.mch.extraChapters.isNotEmpty()
                }
        val extraLine = scanlator ?: andMoreText
        body.text =
            if (extraLine != null) {
                "${baseText.substringBefore("\n")}\n$extraLine"
            } else {
                baseText
            }
    }

    /**
     * Whether an extra chapter shares the main chapter's number, which is what makes the scanlator
     * worth calling out on this row and on the sub rows.
     */
    private fun RecentMangaItem.hasScanlatorConflict(): Boolean =
        isUpdates &&
            !chapter.scanlator.isNullOrBlank() &&
            mch.extraChapters.any {
                it.isRecognizedNumber &&
                    it.chapter_number == chapter.chapter_number &&
                    !it.scanlator.isNullOrBlank()
            }

    private fun readLastText(
        item: RecentMangaItem,
        show: Boolean,
    ): String {
        val notValidNum = item.mch.chapter.chapter_number <= 0
        return if (item.chapter.id != item.mch.chapter.id) {
            if (show) {
                itemView.context.timeSpanFromNow(R.string.read_, item.mch.history.last_read) + "\n"
            } else {
                ""
            } +
                itemView.context.getString(
                    if (notValidNum) R.string.last_read_ else R.string.last_read_chapter_,
                    if (notValidNum) item.mch.chapter.name else adapter.decimalFormat.format(item.mch.chapter.chapter_number),
                )
        } else {
            ""
        }
    }

    private fun <T> List<T>.shorterList(extraChapterMaxSize: Int): List<T?> =
        if (size > extraChapterMaxSize + 1) {
            take(extraChapterMaxSize / 2) + null + takeLast(extraChapterMaxSize / 2)
        } else {
            this
        }

    @SuppressLint("ClickableViewAccessibility")
    private fun RecentSubChapterItemBinding.configureBlankView(count: Int) {
        val context = itemView.context
        title.text =
            context.resources.getQuantityString(R.plurals.notification_and_n_more, count, count)
        downloadButton.root.isVisible = false
        downloadButton.root.tag = null
        title.textSize = 13f
        title.setTextColor(context.contextCompatColor(R.color.read_chapter))
        textLayout.updateLayoutParams<ConstraintLayout.LayoutParams> {
            matchConstraintMinHeight = 16.dpToPx
        }
        root.tag = "sub ${-1L}"
        root.setOnLongClickListener { false }
        root.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                chapterId = -1L
            }
            false
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun RecentSubChapterItemBinding.configureView(
        chapter: ChapterHistory?,
        item: RecentMangaItem,
        extraChapterMaxSize: Int,
    ) {
        // Rows are recycled between real chapters and the "and N more" placeholder now, so reset
        // the state only one of those two branches assigns back to its inflated default. Without
        // this a placeholder reusing a chapter's row keeps its subtitle and click target.
        subtitle.text = ""
        subtitle.isVisible = false
        root.transitionName = null
        root.setOnClickListener(null)
        if (chapter?.id == null) {
            configureBlankView(item.mch.extraChapters.size - extraChapterMaxSize)
            return
        }
        textLayout.updateLayoutParams<ConstraintLayout.LayoutParams> {
            matchConstraintMinHeight = 48.dpToPx
        }
        val context = itemView.context
        val showDLs = adapter.showDownloads
        title.text = chapter.preferredChapterName(context, item.mch.manga, adapter.preferences)
        ChapterUtil.setTextViewForChapter(title, chapter)
        val notReadYet = item.chapter.id != item.mch.chapter.id && item.mch.history.id != null
        subtitle.text = chapter.history?.let { history ->
            context
                .timeSpanFromNow(R.string.read_, history.last_read)
                .takeIf {
                    Date().time - history.last_read < TimeUnit.DAYS.toMillis(1) ||
                        notReadYet ||
                        adapter.dateFormat.run {
                            format(history.last_read) != format(item.mch.history.last_read)
                        }
                }
        } ?: ""
        if (isUpdates &&
            chapter.isRecognizedNumber &&
            chapter.chapter_number == item.chapter.chapter_number &&
            !chapter.scanlator.isNullOrBlank()
        ) {
            subtitle.text = chapter.scanlator
        }
        subtitle.isVisible = subtitle.text.isNotBlank()
        title.textSize = (if (subtitle.isVisible) 14f else 14.5f)
        root.setOnClickListener {
            adapter.delegate.onSubChapterClicked(
                bindingAdapterPosition,
                chapter,
                it,
            )
        }
        root.setOnLongClickListener {
            adapter.delegate.onItemLongClick(bindingAdapterPosition, chapter)
        }
        listOf(root, downloadButton.root).forEach {
            it.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    binding.read.setImageResource(
                        if (chapter.read) R.drawable.ic_eye_off_24dp else R.drawable.ic_eye_24dp,
                    )
                    binding.endView.translationY = binding.moreChaptersLayout.y + root.y
                    binding.endView.updateLayoutParams<ViewGroup.LayoutParams> {
                        height = root.height
                    }
                    binding.endView.shapeAppearanceModel = root.shapeAppearanceModel
                    chapterId = chapter.id
                }
                false
            }
        }
        textLayout.updatePaddingRelative(start = if (isSmallUpdates) 64.dpToPx else 84.dpToPx)
        updateDivider()
        root.transitionName = "recents sub chapter ${chapter.id ?: 0L} transition"
        root.tag = "sub ${chapter.id}"
        downloadButton.root.tag = chapter.id
        val downloadInfo = item.downloadInfo.find { it.chapterId == chapter.id }
        if (downloadInfo == null) {
            // No status resolved for this chapter yet - hide the button rather than leaving
            // whichever state the recycled row happened to come in with.
            downloadButton.downloadButton.isVisible = false
            return
        }
        downloadButton.downloadButton.setOnClickListener {
            downloadOrRemoveMenu(it, chapter, downloadInfo.status)
        }
        downloadButton.downloadButton.isVisible = when (showDLs) {
            RecentMangaAdapter.ShowRecentsDLs.None -> false
            RecentMangaAdapter.ShowRecentsDLs.OnlyUnread, RecentMangaAdapter.ShowRecentsDLs.UnreadOrDownloaded -> !chapter.read
            RecentMangaAdapter.ShowRecentsDLs.OnlyDownloaded -> true
            RecentMangaAdapter.ShowRecentsDLs.All -> true
        } &&
            !item.mch.manga.isLocal()
        notifySubStatus(
            chapter,
            if (adapter.isSelected(flexibleAdapterPosition)) {
                Download.State.CHECKED
            } else {
                downloadInfo.status
            },
            downloadInfo.progress,
            chapter.read,
        )
    }

    private fun RecentSubChapterItemBinding.updateDivider() {
        divider.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            marginStart = if (isSmallUpdates) 64.dpToPx else 84.dpToPx
        }
    }

    override fun onLongClick(view: View?): Boolean {
        super.onLongClick(view)
        val item = adapter.getItem(flexibleAdapterPosition) as? RecentMangaItem ?: return false
        return item.mch.history.id != null
    }

    fun notifyStatus(
        status: Download.State,
        progress: Int,
        isRead: Boolean,
        animated: Boolean = false,
    ) {
        binding.downloadButton.downloadButton.setDownloadStatus(status, progress, animated)
        val isChapterRead =
            if (adapter.showDownloads == RecentMangaAdapter.ShowRecentsDLs.UnreadOrDownloaded) isRead else true
        binding.downloadButton.downloadButton.isVisible =
            when (adapter.showDownloads) {
                RecentMangaAdapter.ShowRecentsDLs.UnreadOrDownloaded,
                RecentMangaAdapter.ShowRecentsDLs.OnlyDownloaded,
                ->
                    status !in Download.State.CHECKED..Download.State.NOT_DOWNLOADED || !isChapterRead
                else -> binding.downloadButton.downloadButton.isVisible
            }
    }

    fun notifySubStatus(
        chapter: Chapter,
        status: Download.State,
        progress: Int,
        isRead: Boolean,
        animated: Boolean = false,
    ) {
        val downloadButton = binding.moreChaptersLayout.findViewWithTag<DownloadButton>(chapter.id) ?: return
        downloadButton.setDownloadStatus(status, progress, animated)
        val isChapterRead =
            if (adapter.showDownloads == RecentMangaAdapter.ShowRecentsDLs.UnreadOrDownloaded) isRead else true
        downloadButton.isVisible =
            when (adapter.showDownloads) {
                RecentMangaAdapter.ShowRecentsDLs.UnreadOrDownloaded,
                RecentMangaAdapter.ShowRecentsDLs.OnlyDownloaded,
                ->
                    status !in Download.State.CHECKED..Download.State.NOT_DOWNLOADED || !isChapterRead
                else -> downloadButton.isVisible
            }
    }

    override fun getFrontView(): View =
        if (chapterId == null) {
            binding.recentCard
        } else {
            binding.moreChaptersLayout.children.find { it.tag == "sub $chapterId" }
                ?: binding.recentCard
        }

    override fun getRearEndView(): View? = if (chapterId == -1L) null else binding.endView

    fun isContained(): Boolean = binding.recentCard.marginStart != 0

    fun useContainers(enabled: Boolean) {
        val cardList = mutableListOf(binding.recentCard)
        cardList.add(binding.endView)
        cardList.addAll(binding.moreChaptersLayout.children.map { it as MaterialCardView })
        if (!enabled) {
            cardList.forEach {
                it.radius = 0f
            }
        }
        val margins = if (enabled) 8.dpToPx else 0
        val bgColor by lazy { itemView.context.getResourceColor(R.attr.background) }
        val cardColor by lazy { itemView.context.getResourceColor(R.attr.colorSurfaceContainerLowest) }
        cardList.forEach {
            it.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                marginStart = margins
                marginEnd = margins
            }
            if (it != binding.endView) {
                it.setCardBackgroundColor(if (enabled) cardColor else bgColor)
            }
        }
        binding.card.setCardBackgroundColor(if (enabled) cardColor else bgColor)
    }

    fun setCorners(
        top: Boolean,
        bottom: Boolean,
    ) {
        useContainers(true)
        val finalCard = binding.moreChaptersLayout.children.lastOrNull() as? MaterialCardView
        val hasSubChapters = finalCard != null && binding.moreChaptersLayout.isVisible
        val mainCornerRadius = itemView.resources.getDimension(R.dimen.container_main_corner)
        val subCornerRadius = itemView.resources.getDimension(R.dimen.container_sub_corner)
        val topRadius = if (top) mainCornerRadius else subCornerRadius
        val bottomRadius = if (bottom) mainCornerRadius else subCornerRadius
        val shapeModel =
            binding.recentCard.shapeAppearanceModel
                .toBuilder()
                .apply {
                    setTopLeftCorner(CornerFamily.ROUNDED, topRadius)
                    setTopRightCorner(CornerFamily.ROUNDED, topRadius)
                    setBottomLeftCorner(
                        CornerFamily.ROUNDED,
                        if (hasSubChapters) 0f else bottomRadius,
                    )
                    setBottomRightCorner(
                        CornerFamily.ROUNDED,
                        if (hasSubChapters) 0f else bottomRadius,
                    )
                }.build()
        binding.recentCard.shapeAppearanceModel = shapeModel
        binding.endView.shapeAppearanceModel = shapeModel
        finalCard?.shapeAppearanceModel =
            binding.recentCard.shapeAppearanceModel
                .toBuilder()
                .apply {
                    setTopLeftCorner(CornerFamily.ROUNDED, 0f)
                    setTopRightCorner(CornerFamily.ROUNDED, 0f)
                    setBottomLeftCorner(CornerFamily.ROUNDED, bottomRadius)
                    setBottomRightCorner(CornerFamily.ROUNDED, bottomRadius)
                }.build()
    }
}
