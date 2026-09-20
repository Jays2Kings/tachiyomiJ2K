package eu.kanade.tachiyomi.ui.source.globalsearch

import android.annotation.SuppressLint
import android.view.View
import androidx.core.view.isVisible
import com.google.android.material.carousel.CarouselLayoutManager
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.databinding.SourceGlobalSearchControllerCardBinding
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.ui.migration.SearchController
import eu.kanade.tachiyomi.ui.source.searchhistory.FilterApplyResult
import eu.kanade.tachiyomi.util.system.LocaleHelper

/**
 * Holder that binds the [GlobalSearchItem] containing catalogue cards.
 *
 * @param view view of [GlobalSearchItem]
 * @param adapter instance of [GlobalSearchAdapter]
 */
class GlobalSearchHolder(
    view: View,
    val adapter: GlobalSearchAdapter,
) : BaseFlexibleViewHolder(view, adapter) {
    /**
     * Adapter containing manga from search results.
     */
    private val mangaAdapter = GlobalSearchCardAdapter(adapter.controller)

    private var lastBoundResults: List<GlobalSearchMangaItem>? = null

    private val binding = SourceGlobalSearchControllerCardBinding.bind(view)

    init {
        // Set layout horizontal.
        binding.recycler.layoutManager = CarouselLayoutManager()
        binding.recycler.adapter = mangaAdapter
        binding.titleMoreIcon.isVisible = adapter.controller !is SearchController && adapter.controller.extensionFilter == null
        if (binding.titleMoreIcon.isVisible) {
            binding.titleWrapper.setOnClickListener {
                adapter.titleClickListener.onTitleClick(bindingAdapterPosition)
            }
        }
    }

    /**
     * Show the loading of source search result.
     *
     * @param item item of card.
     */
    fun bind(item: GlobalSearchItem) {
        val source = item.source
        val results = item.results

        val title = (if (item.highlighted) "▶" else "") + source.name
        binding.title.text = title
        val filterNote =
            when {
                item.filtersOnlySkipped -> null
                item.filterResult == FilterApplyResult.SOME -> itemView.context.getString(R.string.some_filters_not_applied)
                item.filterResult == FilterApplyResult.NONE -> itemView.context.getString(R.string.no_filters_applied)
                else -> null
            }
        val subtitle =
            listOfNotNull(
                LocaleHelper.getDisplayName(source.lang).takeIf { source !is LocalSource },
                filterNote,
            ).joinToString(" • ")
        binding.subtitle.isVisible = subtitle.isNotBlank()
        binding.subtitle.text = subtitle

        when {
            results == null -> {
                binding.progress.isVisible = true
                showHolder()
            }
            results.isEmpty() -> {
                binding.progress.isVisible = false
                binding.noResults.setText(
                    if (item.filtersOnlySkipped) R.string.no_filters_applied else R.string.no_results_found,
                )
                binding.noResults.isVisible = true
                binding.sourceCard.isVisible = false
            }
            else -> {
                binding.progress.isVisible = false
                showHolder()
            }
        }
        if (results !== lastBoundResults) {
            mangaAdapter.updateDataSet(results)
            lastBoundResults = results
        }
    }

    fun updateManga(position: Int) = mangaAdapter.notifyItemChanged(position)

    @SuppressLint("NotifyDataSetChanged")
    fun updateAll() = mangaAdapter.notifyDataSetChanged()

    /**
     * Called from the presenter when a manga is initialized.
     *
     * @param manga the initialized manga.
     */
    fun setImage(manga: Manga) {
        getHolder(manga)?.setImage(manga)
    }

    /**
     * Returns the view holder for the given manga.
     *
     * @param manga the manga to find.
     * @return the holder of the manga or null if it's not bound.
     */
    private fun getHolder(manga: Manga): GlobalSearchMangaHolder? {
        mangaAdapter.allBoundViewHolders.forEach { holder ->
            val item = mangaAdapter.getItem(holder.flexibleAdapterPosition)
            if (item != null && item.manga.id!! == manga.id!!) {
                return holder as GlobalSearchMangaHolder
            }
        }

        return null
    }

    private fun showHolder() {
        binding.sourceCard.isVisible = true
        binding.noResults.isVisible = false
    }
}
