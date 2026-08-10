package eu.kanade.tachiyomi.ui.source.browse

import android.app.Activity
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import coil.Coil
import coil.dispose
import coil.request.ImageRequest
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.image.coil.CoverViewTarget
import eu.kanade.tachiyomi.data.image.coil.MangaCoverFetcher
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.MangaGridItemBinding
import eu.kanade.tachiyomi.ui.library.LibraryCategoryAdapter
import eu.kanade.tachiyomi.util.view.setCards
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Class used to hold the displayed data of a manga in the library, like the cover or the title.
 * All the elements from the layout file "item_catalogue_grid" are available in this class.
 *
 * @param view the inflated view for this holder.
 * @param adapter the adapter handling this holder.
 * @constructor creates a new library holder.
 */
class BrowseSourceGridHolder(
    private val view: View,
    private val adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
    compact: Boolean,
    showOutline: Boolean,
    val db: DatabaseHelper = Injekt.get(),
    val prefs: PreferencesHelper = Injekt.get(),
) : BrowseSourceHolder(view, adapter) {
    private val binding = MangaGridItemBinding.bind(view)

    init {
        if (compact) {
            binding.textLayout.isVisible = false
        } else {
            binding.compactTitle.isVisible = false
            binding.gradient.isVisible = false
        }
        setCards(showOutline, binding.card, binding.unreadDownloadBadge.badgeView)
    }

    /**
     * Method called from [LibraryCategoryAdapter.onBindViewHolder]. It updates the data for this
     * holder with the given manga.
     *
     * @param manga the manga item to bind.
     */
    override fun onSetValues(manga: Manga) {
        // Update the title of the manga.
        binding.title.text = manga.title
        binding.compactTitle.text = binding.title.text
        binding.unreadDownloadBadge.root.setInLibrary(manga.favorite)

        // Update the cover.
        setImage(manga)
    }

    override fun setImage(manga: Manga) {
        if ((view.context as? Activity)?.isDestroyed == true) return
        if (manga.thumbnail_url == null) {
            binding.coverThumbnail.dispose()
        } else {
            manga.id ?: return
            val request =
                ImageRequest
                    .Builder(view.context)
                    .data(manga)
                    .target(CoverViewTarget(binding.coverThumbnail, binding.progress))
                    .setParameter(MangaCoverFetcher.useCustomCover, false)
                    .build()
            Coil.imageLoader(view.context).enqueue(request)

            binding.coverThumbnail.alpha = if (manga.favorite) 0.34f else 1.0f
            binding.card.strokeColorStateList?.defaultColor?.let { color ->
                binding.card.strokeColor = ColorUtils.setAlphaComponent(color, if (manga.favorite) 87 else 255)
            }

            if (prefs.showDuplicatedInLibraryItems().get()) {
                val duplicatedManga = db.getDuplicateLibraryManga(manga).executeAsBlocking()

                if (duplicatedManga != null && !manga.favorite) {
                    binding.coverThumbnail.alpha = 0.34f
                    binding.unreadDownloadBadge.root.setDuplicatedInLibrary(true)
                }
            }
        }
    }
}
