package eu.kanade.tachiyomi.ui.reader.settings

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.AttributeSet
import android.view.Display
import androidx.core.content.getSystemService
import androidx.core.view.isVisible
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.ReaderPagedLayoutBinding
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.util.bindToPreference
import eu.kanade.tachiyomi.util.lang.addBetaTag
import eu.kanade.tachiyomi.widget.BaseReaderSettingsView

class ReaderPagedView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : BaseReaderSettingsView<ReaderPagedLayoutBinding>(context, attrs) {
        var needsActivityRecreate = false

        override fun inflateBinding() = ReaderPagedLayoutBinding.bind(this)

        override fun initGeneralPreferences() {
            with(binding) {
                scaleType.bindToPreference(preferences.imageScaleType(), 1) {
                    val mangaViewer = (context as? ReaderActivity)?.viewModel?.getMangaReadingMode() ?: 0
                    val isWebtoonView = ReadingModeType.isWebtoonType(mangaViewer)
                    updatePagedGroup(!isWebtoonView)
                    landscapeZoom.isVisible = it == SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE - 1
                }
                binding.navigatePan.bindToPreference(preferences.navigateToPan())
                binding.landscapeZoom.bindToPreference(preferences.landscapeZoom())
                zoomStart.bindToPreference(preferences.zoomStart(), 1)
                cropBorders.bindToPreference(preferences.cropBorders())
                pageTransitions.bindToPreference(preferences.pageTransitions()) {
                    // Update visibility of page transition type selector when page transitions toggle
                    val mangaViewer = (context as? ReaderActivity)?.viewModel?.getMangaReadingMode() ?: 0
                    binding.pageTransitionType.isVisible = it && !ReadingModeType.isWebtoonType(mangaViewer)
                }

                // Setup page transition type selector
                pageTransitionType.bindToIntPreference(
                    preferences.pageTransitionType(),
                    R.array.page_transition_types,
                )

                // Get manga viewer mode once and reuse it
                val mangaViewer = (context as? ReaderActivity)?.viewModel?.getMangaReadingMode() ?: 0
                val isWebtoonView = ReadingModeType.isWebtoonType(mangaViewer)
                val hasMargins = mangaViewer == ReadingModeType.CONTINUOUS_VERTICAL.flagValue

                // Set initial visibility
                pageTransitionType.isVisible = preferences.pageTransitions().get() && !isWebtoonView

                pagerNav.bindToPreference(preferences.navigationModePager())
                pagerInvert.bindToPreference(preferences.pagerNavInverted())
                extendPastCutout.bindToPreference(preferences.pagerCutoutBehavior())
                extendPastCutoutLandscape.bindToPreference(preferences.landscapeCutoutBehavior()) {
                    needsActivityRecreate = true
                }
                pageLayout.bindToPreference(preferences.pageLayout()) {
                    val currentMangaViewer = (context as? ReaderActivity)?.viewModel?.getMangaReadingMode() ?: 0
                    val currentIsWebtoonView = ReadingModeType.isWebtoonType(currentMangaViewer)
                    updatePagedGroup(!currentIsWebtoonView)
                }

                invertDoublePages.bindToPreference(preferences.invertDoublePages())

                pageLayout.title = pageLayout.title.toString().addBetaTag(context)

                cropBordersWebtoon.bindToPreference(if (hasMargins) preferences.cropBorders() else preferences.cropBordersWebtoon())
                webtoonSidePadding.bindToIntPreference(
                    preferences.webtoonSidePadding(),
                    R.array.webtoon_side_padding_values,
                )
                webtoonEnableZoomOut.bindToPreference(preferences.webtoonEnableZoomOut())
                webtoonNav.bindToPreference(preferences.navigationModeWebtoon())
                webtoonInvert.bindToPreference(preferences.webtoonNavInverted())
                webtoonPageLayout.bindToPreference(preferences.webtoonPageLayout())
                webtoonInvertDoublePages.bindToPreference(preferences.webtoonInvertDoublePages())

                updatePagedGroup(!isWebtoonView)
            }
        }

        fun updatePrefs() {
            val mangaViewer = activity.viewModel.getMangaReadingMode()
            val isWebtoonView = ReadingModeType.isWebtoonType(mangaViewer)
            val hasMargins = mangaViewer == ReadingModeType.CONTINUOUS_VERTICAL.flagValue
            binding.cropBordersWebtoon.bindToPreference(if (hasMargins) preferences.cropBorders() else preferences.cropBordersWebtoon())
            updatePagedGroup(!isWebtoonView)
        }

        private fun updatePagedGroup(show: Boolean) {
            listOf(
                binding.scaleType,
                binding.zoomStart,
                binding.cropBorders,
                binding.pageTransitions,
                binding.pagerNav,
                binding.pagerInvert,
                binding.pageLayout,
                binding.landscapeZoom,
                binding.navigatePan,
            ).forEach { it.isVisible = show }

            // Page transition type is only visible for paged viewers AND when page transitions are enabled
            binding.pageTransitionType.isVisible = show && preferences.pageTransitions().get()

            listOf(
                binding.cropBordersWebtoon,
                binding.webtoonSidePadding,
                binding.webtoonEnableZoomOut,
                binding.webtoonNav,
                binding.webtoonInvert,
                binding.webtoonPageLayout,
                binding.webtoonInvertDoublePages,
            ).forEach { it.isVisible = !show }
            val isFullFit =
                when (preferences.imageScaleType().get()) {
                    SubsamplingScaleImageView.SCALE_TYPE_FIT_HEIGHT,
                    SubsamplingScaleImageView.SCALE_TYPE_SMART_FIT,
                    SubsamplingScaleImageView.SCALE_TYPE_CENTER_CROP,
                    -> true
                    else -> false
                }
            val ogView = (context as? Activity)?.window?.decorView
            val hasCutout =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ogView?.rootWindowInsets?.displayCutout?.safeInsetTop != null ||
                        ogView?.rootWindowInsets?.displayCutout?.safeInsetBottom != null
                } else {
                    false
                }
            val hasAnyCutout =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context
                        .getSystemService<DisplayManager>()
                        ?.getDisplay(Display.DEFAULT_DISPLAY)
                        ?.cutout != null
                } else {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                }
            binding.landscapeZoom.isVisible =
                show &&
                preferences.imageScaleType().get() == SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
            binding.extendPastCutout.isVisible = show && isFullFit && hasCutout && preferences.fullscreen().get()
            binding.extendPastCutoutLandscape.isVisible = hasAnyCutout &&
                preferences.fullscreen().get() &&
                ogView?.resources?.configuration?.orientation == Configuration.ORIENTATION_LANDSCAPE
            if (binding.extendPastCutoutLandscape.isVisible) {
                binding.filterLinearLayout.removeView(binding.extendPastCutoutLandscape)
                binding.filterLinearLayout.addView(
                    binding.extendPastCutoutLandscape,
                    binding.filterLinearLayout.indexOfChild(if (show) binding.extendPastCutout else binding.webtoonPageLayout) + 1,
                )
            }
            binding.invertDoublePages.isVisible = show && preferences.pageLayout().get() != PageLayout.SINGLE_PAGE.value
            // Handle page transition type visibility separately
            binding.pageTransitionType.isVisible = show && preferences.pageTransitions().get()
        }
    }
