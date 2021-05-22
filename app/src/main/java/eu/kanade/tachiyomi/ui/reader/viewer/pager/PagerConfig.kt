package eu.kanade.tachiyomi.ui.reader.viewer.pager

import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.reader.settings.PageLayout
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerConfig
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.EdgeNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.KindlishNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.LNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.RightAndLeftNavigation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Configuration used by pager viewers.
 */
class PagerConfig(
    scope: CoroutineScope,
    private val viewer: PagerViewer,
    preferences: PreferencesHelper = Injekt.get()
) :
    ViewerConfig(preferences, scope) {

    var usePageTransitions = false
        private set

    var imageScaleType = 1
        private set

    var imageZoomType = ZoomType.Left
        private set

    var imageCropBorders = false
        private set

    var readerTheme = 0
        private set

    var cutoutBehavior = 0
        private set

    var shiftDoublePage = false

    var doublePages = preferences.pageLayout().get() == PageLayout.DOUBLE_PAGES.value
        set(value) {
            field = value
            if (!value) {
                shiftDoublePage = false
            }
        }

    var invertDoublePages = false

    var autoDoublePages = preferences.pageLayout().get() == PageLayout.AUTOMATIC.value

    var splitPages = preferences.pageLayout().get() == PageLayout.SPLIT_PAGES.value
    var autoSplitPages = preferences.automaticSplitsPage().get()

    init {
        preferences.pageTransitions()
            .register({ usePageTransitions = it })

        preferences.imageScaleType()
            .register({ imageScaleType = it }, { imagePropertyChangedListener?.invoke() })

        preferences.navigationModePager()
            .register({ navigationMode = it }, { updateNavigation(navigationMode) })

        preferences.pagerNavInverted()
            .register(
                { tappingInverted = it },
                {
                    navigator.invertMode = it
                }
            )

        preferences.pagerNavInverted().asFlow()
            .drop(1)
            .onEach {
                navigationModeInvertedListener?.invoke()
            }
            .launchIn(scope)

        preferences.pagerCutoutBehavior()
            .register({ cutoutBehavior = it }, { imagePropertyChangedListener?.invoke() })

        preferences.zoomStart()
            .register({ zoomTypeFromPreference(it) }, { imagePropertyChangedListener?.invoke() })

        preferences.cropBorders()
            .register({ imageCropBorders = it }, { imagePropertyChangedListener?.invoke() })

        preferences.readerTheme()
            .register({ readerTheme = it }, { imagePropertyChangedListener?.invoke() })

        preferences.invertDoublePages()
            .register({ invertDoublePages = it }, { imagePropertyChangedListener?.invoke() })

        preferences.pageLayout()
            .asFlow()
            .drop(1)
            .onEach {
                autoDoublePages = it == PageLayout.AUTOMATIC.value
                splitPages = it == PageLayout.SPLIT_PAGES.value
                if (!autoDoublePages) {
                    doublePages = it == PageLayout.DOUBLE_PAGES.value
                }
                reloadChapterListener?.invoke(doublePages)
            }
            .launchIn(scope)
        preferences.pageLayout()
            .register({
                autoDoublePages = it == PageLayout.AUTOMATIC.value
                if (!autoDoublePages) {
                    doublePages = it == PageLayout.DOUBLE_PAGES.value
                    splitPages = it == PageLayout.SPLIT_PAGES.value
                }
            })

        preferences.automaticSplitsPage()
            .register({ autoSplitPages = it })
        navigationOverlayForNewUser = preferences.showNavigationOverlayNewUser().get()
        if (navigationOverlayForNewUser) {
            preferences.showNavigationOverlayNewUser().set(false)
        }
    }

    private fun zoomTypeFromPreference(value: Int) {
        imageZoomType = when (value) {
            // Auto
            1 -> when (viewer) {
                is L2RPagerViewer -> ZoomType.Left
                is R2LPagerViewer -> ZoomType.Right
                else -> ZoomType.Center
            }
            // Left
            2 -> ZoomType.Left
            // Right
            3 -> ZoomType.Right
            // Center
            else -> ZoomType.Center
        }
    }

    override var navigator: ViewerNavigation = defaultNavigation()
        set(value) {
            field = value.also { it.invertMode = this.tappingInverted }
        }

    override fun defaultNavigation(): ViewerNavigation {
        return when (viewer) {
            is VerticalPagerViewer -> LNavigation()
            else -> RightAndLeftNavigation()
        }
    }

    fun scaleTypeIsFullFit(): Boolean {
        return when (imageScaleType) {
            SubsamplingScaleImageView.SCALE_TYPE_FIT_HEIGHT,
            SubsamplingScaleImageView.SCALE_TYPE_SMART_FIT,
            SubsamplingScaleImageView.SCALE_TYPE_CENTER_CROP -> true
            else -> false
        }
    }

    override fun updateNavigation(navigationMode: Int) {
        navigator = when (navigationMode) {
            0 -> defaultNavigation()
            1 -> LNavigation()
            2 -> KindlishNavigation()
            3 -> EdgeNavigation()
            4 -> RightAndLeftNavigation()
            else -> defaultNavigation()
        }
        navigationModeChangedListener?.invoke()
    }

    enum class ZoomType {
        Left, Center, Right
    }

    companion object {
        const val CUTOUT_PAD = 0
        const val CUTOUT_START_EXTENDED = 1
        const val CUTOUT_IGNORE = 2
    }
}
