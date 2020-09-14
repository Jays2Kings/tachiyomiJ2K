package eu.kanade.tachiyomi.ui.migration.manga.process

import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.constraintlayout.widget.ConstraintLayout
import coil.Coil
import coil.request.LoadRequest
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.invisible
import eu.kanade.tachiyomi.util.view.setVectorCompat
import eu.kanade.tachiyomi.util.view.visible
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import eu.kanade.tachiyomi.data.image.coil.CoverViewTarget
import kotlinx.android.synthetic.main.manga_grid_item.view.*
import kotlinx.android.synthetic.main.migration_process_item.*
import kotlinx.android.synthetic.main.unread_download_badge.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.injectLazy
import java.text.DecimalFormat

class MigrationProcessHolder(
    private val view: View,
    private val adapter: MigrationProcessAdapter
) : BaseFlexibleViewHolder(view, adapter) {

    private val db: DatabaseHelper by injectLazy()
    private val sourceManager: SourceManager by injectLazy()
    private var item: MigrationProcessItem? = null

    init {
        // We need to post a Runnable to show the popup to make sure that the PopupMenu is
        // correctly positioned. The reason being that the view may change position before the
        // PopupMenu is shown.
        migration_menu.setOnClickListener { it.post { showPopupMenu(it) } }
        skip_manga.setOnClickListener { it.post { adapter.removeManga(adapterPosition) } }
    }

    fun bind(item: MigrationProcessItem) {
        this.item = item
        launchUI {
            val manga = item.manga.manga()
            val source = item.manga.mangaSource()

            migration_menu.setVectorCompat(
                R.drawable.ic_more_vert_24dp,
                view.context.getResourceColor(android.R.attr.textColorPrimary)
            )
            skip_manga.setVectorCompat(
                R.drawable.ic_close_24dp,
                view.context.getResourceColor(
                    android.R.attr.textColorPrimary
                )
            )
            migration_menu.invisible()
            skip_manga.visible()
            migration_manga_card_to.resetManga()
            if (manga != null) {
                withContext(Dispatchers.Main) {
                    migration_manga_card_from.attachManga(manga, source, false)
                    migration_manga_card_from.setOnClickListener {
                        adapter.controller.router.pushController(
                            MangaDetailsController(
                                manga,
                                true
                            ).withFadeTransaction()
                        )
                    }
                }

                /*launchUI {
                    item.manga.progress.asFlow().collect { (max, progress) ->
                        withContext(Dispatchers.Main) {
                            migration_manga_card_to.search_progress.let { progressBar ->
                                progressBar.max = max
                                progressBar.progress = progress
                            }
                        }
                    }
                }*/

                val searchResult = item.manga.searchResult.get()?.let {
                    db.getManga(it).executeAsBlocking()
                }
                val resultSource = searchResult?.source?.let {
                    sourceManager.get(it)
                }
                withContext(Dispatchers.Main) {
                    if (item.manga.mangaId != this@MigrationProcessHolder.item?.manga?.mangaId || item.manga.migrationStatus == MigrationStatus.RUNNUNG) {
                        return@withContext
                    }
                    if (searchResult != null && resultSource != null) {
                        migration_manga_card_to.attachManga(searchResult, resultSource, true)
                        migration_manga_card_to.setOnClickListener {
                            adapter.controller.router.pushController(
                                MangaDetailsController(
                                    searchResult,
                                    true
                                ).withFadeTransaction()
                            )
                        }
                    } else {
                        migration_manga_card_to.progress.gone()
                        migration_manga_card_to.title.text =
                            view.context.getString(R.string.no_alternatives_found)
                    }
                    migration_menu.visible()
                    skip_manga.gone()
                    adapter.sourceFinished()
                }
            }
        }
    }

    private fun View.resetManga() {
        progress.visible()
        cover_thumbnail.setImageDrawable(null)
        compact_title.text = ""
        title.text = ""
        subtitle.text = ""
        badge_view.setChapters(null)
        (layoutParams as ConstraintLayout.LayoutParams).verticalBias = 0.5f
        subtitle.text = ""
        migration_manga_card_to.setOnClickListener(null)
    }

    private fun View.attachManga(manga: Manga, source: Source, isTo: Boolean) {
        (layoutParams as ConstraintLayout.LayoutParams).verticalBias = 1f
        progress.gone()

        val request = LoadRequest.Builder(view.context).data(manga)
            .target(CoverViewTarget(cover_thumbnail, progress)).build()
        Coil.imageLoader(view.context).execute(request)

        compact_title.visible()
        gradient.visible()
        compact_title.text = if (manga.title.isBlank()) {
            view.context.getString(R.string.unknown)
        } else {
            manga.title
        }

        gradient.visible()
        title.text = /*if (source.id == MERGED_SOURCE_ID) {
            MergedSource.MangaConfig.readFromUrl(gson, manga.url).children.map {
                sourceManager.getOrStub(it.source).toString()
            }.distinct().joinToString()
        } else {*/
            source.toString()
        // }

        val mangaChapters = db.getChapters(manga).executeAsBlocking()
        badge_view.setChapters(mangaChapters.size)
        val latestChapter = mangaChapters.maxBy { it.chapter_number }?.chapter_number ?: -1f

        if (latestChapter > 0f) {
            subtitle.text = context.getString(
                R.string.latest_,
                DecimalFormat("#.#").format(latestChapter)
            )
        } else {
            subtitle.text = context.getString(
                R.string.latest_,
                context.getString(R.string.unknown)
            )
        }
    }

    private fun showPopupMenu(view: View) {
        val item = adapter.getItem(adapterPosition) ?: return

        // Create a PopupMenu, giving it the clicked view for an anchor
        val popup = PopupMenu(view.context, view)

        // Inflate our menu resource into the PopupMenu's Menu
        popup.menuInflater.inflate(R.menu.migration_single, popup.menu)

        val mangas = item.manga

        popup.menu.findItem(R.id.action_search_manually).isVisible = true
        // Hide download and show delete if the chapter is downloaded
        if (mangas.searchResult.content != null) {
            popup.menu.findItem(R.id.action_migrate_now).isVisible = true
            popup.menu.findItem(R.id.action_copy_now).isVisible = true
        }

        // Set a listener so we are notified if a menu item is clicked
        popup.setOnMenuItemClickListener { menuItem ->
            adapter.menuItemListener.onMenuItemClick(adapterPosition, menuItem)
            true
        }

        // Finally show the PopupMenu
        popup.show()
    }
}
