package eu.kanade.tachiyomi.ui.manga

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import coil.api.loadAny
import coil.request.CachePolicy
import com.google.android.material.button.MaterialButton
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.isLTR
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.invisible
import eu.kanade.tachiyomi.util.view.isVisible
import eu.kanade.tachiyomi.util.view.resetStrokeColor
import eu.kanade.tachiyomi.util.view.updateLayoutParams
import eu.kanade.tachiyomi.util.view.visible
import eu.kanade.tachiyomi.util.view.visibleIf
import kotlinx.android.synthetic.main.manga_details_controller.*
import kotlinx.android.synthetic.main.manga_header_item.*

class MangaHeaderHolder(
    private val view: View,
    private val adapter: MangaDetailsAdapter,
    startExpanded: Boolean
) : BaseFlexibleViewHolder(view, adapter) {

    private var showReadingButton = true
    private var showMoreButton = true

    init {
        chapter_layout.setOnClickListener { adapter.delegate.showChapterFilter() }
        if (start_reading_button != null) {
            start_reading_button.setOnClickListener { adapter.delegate.readNextChapter() }
            top_view.updateLayoutParams<ConstraintLayout.LayoutParams> {
                height = adapter.delegate.topCoverHeight()
            }
            more_button.setOnClickListener { expandDesc() }
            manga_summary.setOnClickListener { expandDesc() }
            manga_summary.setOnLongClickListener {
                if (manga_summary.isTextSelectable && !adapter.recyclerView.canScrollVertically(-1)) {
                    (adapter.delegate as MangaDetailsController).swipe_refresh.isEnabled = false
                }
                false
            }
            manga_summary.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_UP) (adapter.delegate as MangaDetailsController).swipe_refresh.isEnabled =
                    true
                false
            }
            if (!itemView.resources.isLTR) {
                more_bg_gradient.rotation = 180f
            }
            less_button.setOnClickListener { collapseDesc() }
            manga_genres_tags.setOnTagClickListener {
                adapter.delegate.tagClicked(it)
            }
            webview_button.setOnClickListener { adapter.delegate.openInWebView() }
            share_button.setOnClickListener { adapter.delegate.prepareToShareManga() }
            favorite_button.setOnClickListener {
                adapter.delegate.favoriteManga(false)
            }
            title.setOnClickListener {
                title.text?.let { adapter.delegate.globalSearch(it.toString()) }
            }
            title.setOnLongClickListener {
                adapter.delegate.copyToClipboard(title.text.toString(), R.string.title)
                true
            }
            manga_author.setOnClickListener {
                manga_author.text?.let { adapter.delegate.globalSearch(it.toString()) }
            }
            manga_author.setOnLongClickListener {
                adapter.delegate.copyToClipboard(manga_author.text.toString(), R.string.author)
                true
            }
            manga_cover.setOnClickListener { adapter.delegate.zoomImageFromThumb(cover_card) }
            track_button.setOnClickListener { adapter.delegate.showTrackingSheet() }
            if (startExpanded) expandDesc()
            else collapseDesc()
        } else {
            filter_button.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                marginEnd = 12.dpToPx
            }
        }
    }

    private fun expandDesc() {
        if (more_button.visibility == View.VISIBLE) {
            manga_summary.maxLines = Integer.MAX_VALUE
            manga_summary.setTextIsSelectable(true)
            manga_genres_tags.visible()
            less_button.visible()
            more_button_group.gone()
            title.maxLines = Integer.MAX_VALUE
        }
    }

    private fun collapseDesc() {
        manga_summary.setTextIsSelectable(false)
        manga_summary.maxLines = 3
        manga_summary.setOnClickListener { expandDesc() }
        manga_genres_tags.gone()
        less_button.gone()
        more_button_group.visible()
        title.maxLines = 4
        adapter.recyclerView.post {
            adapter.delegate.updateScroll()
        }
    }

    fun bindChapters() {
        val presenter = adapter.delegate.mangaPresenter()
        val count = presenter.chapters.size
        chapters_title.text = itemView.resources.getQuantityString(R.plurals.chapters, count, count)
        filters_text.text = presenter.currentFilters()
    }

    @SuppressLint("SetTextI18n")
    fun bind(item: MangaHeaderItem, manga: Manga) {
        val presenter = adapter.delegate.mangaPresenter()
        title.text = manga.title

        if (manga.genre.isNullOrBlank().not()) manga_genres_tags.setTags(
            manga.genre?.split(",")?.map(String::trim)
        )
        else manga_genres_tags.setTags(emptyList())

        if (manga.author == manga.artist || manga.artist.isNullOrBlank()) {
            manga_author.text = manga.author?.trim()
        } else {
            manga_author.text = listOfNotNull(manga.author?.trim(), manga.artist?.trim()).joinToString(", ")
        }
        manga_summary.text =
            if (manga.description.isNullOrBlank()) itemView.context.getString(R.string.no_description)
            else manga.description?.trim()

        manga_summary.post {
            if (sub_item_group.visibility != View.GONE) {
                if ((manga_summary.lineCount < 3 && manga.genre.isNullOrBlank()) || less_button.isVisible()) {
                    manga_summary.setTextIsSelectable(true)
                    more_button_group.gone()
                    showMoreButton = less_button.isVisible()
                } else {
                    more_button_group.visible()
                }
            }
            if (adapter.hasFilter()) collapse()
            else expand()
        }
        manga_summary_label.text = itemView.context.getString(
            R.string.about_this_,
            manga.mangaType(itemView.context)
        )
        with(favorite_button) {
            icon = ContextCompat.getDrawable(
                itemView.context,
                when {
                    item.isLocked -> R.drawable.ic_lock_24dp
                    manga.favorite -> R.drawable.ic_heart_24dp
                    else -> R.drawable.ic_heart_outline_24dp
                }
            )
            text = itemView.resources.getString(
                when {
                    item.isLocked -> R.string.unlock
                    manga.favorite -> R.string.in_library
                    else -> R.string.add_to_library
                }
            )
            checked(!item.isLocked && manga.favorite)
            adapter.delegate.setFavButtonPopup(this)
        }
        true_backdrop.setBackgroundColor(
            adapter.delegate.coverColor()
                ?: itemView.context.getResourceColor(android.R.attr.colorBackground)
        )

        val tracked = presenter.isTracked() && !item.isLocked

        with(track_button) {
            visibleIf(presenter.hasTrackers())
            text = itemView.context.getString(
                if (tracked) R.string.tracked
                else R.string.tracking
            )

            icon = ContextCompat.getDrawable(
                itemView.context,
                if (tracked) R.drawable.ic_check_24dp else R.drawable.ic_sync_24dp
            )
            checked(tracked)
        }

        with(start_reading_button) {
            val nextChapter = presenter.getNextUnreadChapter()
            visibleIf(presenter.chapters.isNotEmpty() && !item.isLocked && !adapter.hasFilter())
            showReadingButton = isVisible()
            isEnabled = (nextChapter != null)
            text = if (nextChapter != null) {
                val number = adapter.decimalFormat.format(nextChapter.chapter_number.toDouble())
                if (nextChapter.chapter_number > 0) resources.getString(
                    if (nextChapter.last_page_read > 0) R.string.continue_reading_chapter_
                    else R.string.start_reading_chapter_,
                    number
                )
                else {
                    resources.getString(
                        if (nextChapter.last_page_read > 0) R.string.continue_reading
                        else R.string.start_reading
                    )
                }
            } else {
                resources.getString(R.string.all_chapters_read)
            }
        }

        val count = presenter.chapters.size
        chapters_title.text = itemView.resources.getQuantityString(R.plurals.chapters, count, count)

        top_view.updateLayoutParams<ConstraintLayout.LayoutParams> {
            height = adapter.delegate.topCoverHeight()
        }

        manga_status.visibleIf(manga.status != 0)
        manga_status.text = (
            itemView.context.getString(
                when (manga.status) {
                    SManga.ONGOING -> R.string.ongoing
                    SManga.COMPLETED -> R.string.completed
                    SManga.LICENSED -> R.string.licensed
                    else -> R.string.unknown_status
                }
            )
            )
        manga_source.text = presenter.source.toString()

        filters_text.text = presenter.currentFilters()

        if (manga.source == LocalSource.ID) {
            webview_button.gone()
            share_button.gone()
        }

        if (!manga.initialized) return
        updateCover(manga)
    }

    private fun MaterialButton.checked(checked: Boolean) {
        if (checked) {
            backgroundTintList = ColorStateList.valueOf(
                ColorUtils.setAlphaComponent(
                    context.getResourceColor(R.attr.colorAccent),
                    75
                )
            )
            strokeColor = ColorStateList.valueOf(Color.TRANSPARENT)
        } else {
            resetStrokeColor()
            backgroundTintList =
                ContextCompat.getColorStateList(context, android.R.color.transparent)
        }
    }

    fun setTopHeight(newHeight: Int) {
        top_view.updateLayoutParams<ConstraintLayout.LayoutParams> {
            height = newHeight
        }
    }

    fun setBackDrop(color: Int) {
        true_backdrop.setBackgroundColor(color)
    }

    fun updateTracking() {
        val presenter = adapter.delegate.mangaPresenter()
        val tracked = presenter.isTracked()
        with(track_button) {
            text = itemView.context.getString(
                if (tracked) R.string.tracked
                else R.string.tracking
            )

            icon = ContextCompat.getDrawable(
                itemView.context,
                if (tracked) R.drawable
                    .ic_check_24dp else R.drawable.ic_sync_24dp
            )
            checked(tracked)
        }
    }

    fun collapse() {
        sub_item_group.gone()
        start_reading_button.gone()
        if (more_button.visibility == View.VISIBLE || more_button.visibility == View.INVISIBLE)
            more_button_group.invisible()
        else {
            less_button.gone()
            manga_genres_tags.gone()
        }
    }

    fun updateCover(manga: Manga) {
        if (!manga.initialized) return
        val drawable = adapter.controller.manga_cover_full?.drawable
        manga_cover.loadAny(
            manga,
            builder = {
                placeholder(drawable)
                error(drawable)
                if (manga.favorite) networkCachePolicy(CachePolicy.DISABLED)
            }
        )
        backdrop.loadAny(
            manga,
            builder = {
                placeholder(drawable)
                error(drawable)
                if (manga.favorite) networkCachePolicy(CachePolicy.DISABLED)
            }
        )
    }

    fun expand() {
        sub_item_group.visible()
        if (!showMoreButton) more_button_group.gone()
        else {
            if (manga_summary.maxLines != Integer.MAX_VALUE) more_button_group.visible()
            else {
                less_button.visible()
                manga_genres_tags.visible()
            }
        }
        start_reading_button.visibleIf(showReadingButton)
    }

    override fun onLongClick(view: View?): Boolean {
        super.onLongClick(view)
        return false
    }
}
