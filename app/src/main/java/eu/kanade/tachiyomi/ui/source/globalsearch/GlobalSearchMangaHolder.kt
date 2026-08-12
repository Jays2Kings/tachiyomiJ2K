package eu.kanade.tachiyomi.ui.source.globalsearch

import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.view.isVisible
import coil.Coil
import coil.dispose
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.google.android.material.animation.AnimationUtils.lerp
import com.google.android.material.shape.CornerFamily
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.image.coil.CoverViewTarget
import eu.kanade.tachiyomi.data.image.coil.MangaCoverFetcher
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.SourceGlobalSearchControllerCardItemBinding
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.util.system.isLTR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class GlobalSearchMangaHolder(
    view: View,
    adapter: GlobalSearchCardAdapter,
    val db: DatabaseHelper = Injekt.get(),
    val prefs: PreferencesHelper = Injekt.get(),
) : BaseFlexibleViewHolder(view, adapter) {
    private val binding = SourceGlobalSearchControllerCardItemBinding.bind(view)

    init {
        itemView.setOnClickListener {
            val item = adapter.getItem(flexibleAdapterPosition)
            if (item != null) {
                adapter.mangaClickListener.onMangaClick(item.manga)
            }
        }
        val radius = binding.favoriteButton.radius
        binding.favoriteButton.shapeAppearanceModel =
            binding.favoriteButton.shapeAppearanceModel
                .toBuilder()
                .apply {
                    if (itemView.context.resources.isLTR) {
                        setTopLeftCorner(CornerFamily.ROUNDED, 0f)
                        setBottomLeftCorner(CornerFamily.ROUNDED, 0f)
                        setBottomRightCorner(CornerFamily.ROUNDED, radius)
                        setTopRightCorner(CornerFamily.ROUNDED, 0f)
                    } else {
                        setTopLeftCorner(CornerFamily.ROUNDED, 0f)
                        setBottomLeftCorner(CornerFamily.ROUNDED, radius)
                        setBottomRightCorner(CornerFamily.ROUNDED, 0f)
                        setTopRightCorner(CornerFamily.ROUNDED, 0f)
                    }
                }.build()
        itemView.setOnLongClickListener {
            adapter.mangaClickListener.onMangaLongClick(flexibleAdapterPosition, adapter)
            true
        }
        binding.maskableLayout.setOnMaskChangedListener { maskRect ->
            // Any custom motion to run when mask size changes
            binding.title.translationX = maskRect.left
            binding.title.setAlpha(lerp(1F, 0F, 0F, 80F, maskRect.left))
            binding.favoriteButton.setAlpha(binding.title.alpha)
        }
    }

    fun bind(manga: Manga) {
        binding.title.text = manga.title
        binding.favoriteButton.isVisible = manga.favorite
        setImage(manga)
        binding.itemImage.alpha = if (manga.favorite) 0.34f else 1.0f

        binding.duplicateInLibraryButton.isVisible = false
        if (!manga.favorite && prefs.showDuplicateInLibraryItems().get()) {
            val duplicatedManga = db.getDuplicateLibraryManga(manga).executeAsBlocking()
            if (duplicatedManga != null) {
                binding.duplicateInLibraryButton.isVisible = true
                binding.itemImage.alpha = 0.34f
            }
        }
    }

    var drawable: Drawable? = null

    fun setImage(manga: Manga) {
        binding.itemImage.dispose()
        if (!manga.thumbnail_url.isNullOrEmpty()) {
            val request =
                ImageRequest
                    .Builder(itemView.context)
                    .data(manga)
                    .placeholder(android.R.color.transparent)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .target(CoverViewTarget(binding.itemImage, binding.progress))
                    .setParameter(MangaCoverFetcher.useCustomCover, false)
                    .crossfade(false)
                    .build()
            Coil.imageLoader(itemView.context).enqueue(request)
        }
    }
}
