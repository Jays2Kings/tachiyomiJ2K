package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import eu.kanade.tachiyomi.data.preference.PreferenceValues
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.reader.settings.PageLayout
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerConfig
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.DisabledNavigation
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
 * Configuration used by webtoon viewers.
 */
class WebtoonConfig(
    scope: CoroutineScope,
    preferences: PreferencesHelper = Injekt.get(),
) : ViewerConfig(preferences, scope) {
    var webtoonCropBorders = false
        private set

    var verticalCropBorders = true
        private set

    var sidePadding = 0
        private set

    var enableZoomOut = false
        private set

    var doubleTapZoom = 0
        private set

    var zoomPropertyChangedListener: ((Boolean) -> Unit)? = null
    var doubleTapZoomChangedListener: ((Int) -> Unit)? = null

    var splitPages = preferences.webtoonPageLayout().get() == PageLayout.SPLIT_PAGES.webtoonValue

    var invertDoublePages = false

    var menuThreshold = PreferenceValues.ReaderHideThreshold.LOW.threshold

    init {
        preferences
            .navigationModeWebtoon()
            .register({ navigationMode = it }, { updateNavigation(it) })

        preferences
            .webtoonNavInverted()
            .register(
                { tappingInverted = it },
                {
                    navigator.invertMode = it
                },
            )

        preferences
            .webtoonNavInverted()
            .asFlow()
            .drop(1)
            .onEach {
                navigationModeInvertedListener?.invoke()
            }.launchIn(scope)

        preferences
            .cropBordersWebtoon()
            .register({ webtoonCropBorders = it }, { imagePropertyChangedListener?.invoke() })

        preferences
            .cropBorders()
            .register({ verticalCropBorders = it }, { imagePropertyChangedListener?.invoke() })

        preferences
            .webtoonSidePadding()
            .register({ sidePadding = it }, { imagePropertyChangedListener?.invoke() })

        preferences
            .webtoonEnableZoomOut()
            .register({ enableZoomOut = it }, { zoomPropertyChangedListener?.invoke(it) })

        preferences
            .webtoonDoubleTapZoom()
            .register({ doubleTapZoom = it }, { doubleTapZoomChangedListener?.invoke(it) })

        preferences
            .webtoonPageLayout()
            .register(
                { splitPages = it == PageLayout.SPLIT_PAGES.webtoonValue },
                { imagePropertyChangedListener?.invoke() },
            )
        preferences.webtoonReaderHideThreshold().register({ menuThreshold = it.threshold })
        preferences
            .webtoonInvertDoublePages()
            .register({ invertDoublePages = it }, { imagePropertyChangedListener?.invoke() })

        navigationOverlayForNewUser = preferences.showNavigationOverlayNewUserWebtoon().get()
        if (navigationOverlayForNewUser) {
            preferences.showNavigationOverlayNewUserWebtoon().set(false)
        }
    }

    override var navigator: ViewerNavigation = defaultNavigation()
        set(value) {
            field = value.also { it.invertMode = tappingInverted }
        }

    override fun defaultNavigation(): ViewerNavigation = LNavigation()

    override fun updateNavigation(navigationMode: Int) {
        this.navigator =
            when (navigationMode) {
                0 -> defaultNavigation()
                1 -> LNavigation()
                2 -> KindlishNavigation()
                3 -> EdgeNavigation()
                4 -> RightAndLeftNavigation()
                5 -> DisabledNavigation()
                else -> defaultNavigation()
            }
        navigationModeChangedListener?.invoke()
    }
}
