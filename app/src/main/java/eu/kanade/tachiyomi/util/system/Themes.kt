package eu.kanade.tachiyomi.util.system

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.ColorUtils
import eu.kanade.tachiyomi.R
import kotlin.math.roundToInt

@Suppress("unused")
enum class Themes(@StyleRes val styleRes: Int, val nightMode: Int, @StringRes val nameRes: Int, @StringRes altNameRes: Int? = null) {
    MONET(
        R.style.Theme_Tachiyomi_Monet,
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
        R.string.a_brighter_you,
        R.string.a_calmer_you
    ),
    DEFAULT(
        R.style.Theme_Tachiyomi,
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
        R.string.white_theme,
        R.string.dark
    ),
    SPRING_AND_DUSK(
        R.style.Theme_Tachiyomi_MidnightDusk,
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
        R.string.spring_blossom,
        R.string.midnight_dusk
    ),
    STRAWBERRIES(
        R.style.Theme_Tachiyomi_Strawberries,
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
        R.string.strawberry_daiquiri,
        R.string.chocolate_strawberries
    ),
    TAKO(
        R.style.Theme_Tachiyomi_Tako,
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
        R.string.tako
    ),
    YIN_AND_YANG(
        R.style.Theme_Tachiyomi_YinYang,
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
        R.string.yang,
        R.string.yin
    ),
    LIME(
        R.style.Theme_Tachiyomi_FlatLime,
        AppCompatDelegate.MODE_NIGHT_YES,
        R.string.flat_lime
    ),
    YOTSUBA(
        R.style.Theme_Tachiyomi_Yotsuba,
        AppCompatDelegate.MODE_NIGHT_NO,
        R.string.yotsuba
    ),
    CLASSIC_BLUE(
        R.style.Theme_Tachiyomi_AllBlue,
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
        R.string.light_blue,
        R.string.dark_blue
    ),
    ;

    val isDarkTheme = nightMode == AppCompatDelegate.MODE_NIGHT_YES
    val followsSystem = nightMode == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM

    @StringRes
    val darkNameRes: Int = altNameRes ?: nameRes

    fun getColors(mode: Int = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM): Colors {
        return when (nightMode) {
            AppCompatDelegate.MODE_NIGHT_YES -> darkColors()
            AppCompatDelegate.MODE_NIGHT_NO -> lightColors()
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> when (mode) {
                AppCompatDelegate.MODE_NIGHT_YES -> darkColors()
                else -> lightColors()
            }
            else -> lightColors()
        }
    }

    private fun lightColors(): Colors {
        return Colors(
            lightPrimaryText,
            lightSecondaryText,
            lightBackground,
            lightAccent,
            lightAppBar,
            lightAppBarText,
            lightBottomBar,
            lightInactiveTab,
            lightActiveTab,
        )
    }

    private fun darkColors(): Colors {
        return Colors(
            darkPrimaryText,
            darkSecondaryText,
            darkBackground,
            darkAccent,
            darkAppBar,
            darkAppBarText,
            darkBottomBar,
            darkInactiveTab,
            darkActiveTab,
        )
    }

    /** Complies with textColorPrimary (probably night) */
    @ColorInt
    val lightPrimaryText: Int = Color.parseColor(
        when (styleRes) {
            R.style.Theme_Tachiyomi_MidnightDusk -> "#DE240728"
            else -> "#DE000000"
        }
    )

    /** Complies with textColorPrimary (probably night) */
    @ColorInt
    val darkPrimaryText: Int = Color.parseColor("#FFFFFFFF")

    /** Complies with textColorSecondary (primary with alpha) */
    @ColorInt
    val lightSecondaryText: Int = ColorUtils.setAlphaComponent(lightPrimaryText, (0.54f * 255f).roundToInt())

    /** Complies with textColorSecondary (primary with alpha) */
    @ColorInt
    val darkSecondaryText: Int = ColorUtils.setAlphaComponent(darkPrimaryText, (0.54f * 255f).roundToInt())

    /** Complies with background */
    @ColorInt
    val lightBackground: Int = Color.parseColor(
        when (styleRes) {
            R.style.Theme_Tachiyomi_Tako -> "#F2EDF7"
            R.style.Theme_Tachiyomi_MidnightDusk -> "#f6f0f8"
            else -> "#FAFAFA"
        }
    )

    /** Complies with background (probably night) */
    @ColorInt
    val darkBackground: Int = Color.parseColor(
        when (styleRes) {
            R.style.Theme_Tachiyomi_Tako -> "#21212E"
            R.style.Theme_Tachiyomi_Strawberries -> "#1a1716"
            R.style.Theme_Tachiyomi_MidnightDusk -> "#16151D"
            R.style.Theme_Tachiyomi_FlatLime -> "#202125"
            else -> "#1C1C1D"
        }
    )

    /** Complies with colorSecondary */
    @ColorInt
    val lightAccent: Int = Color.parseColor(
        when (styleRes) {
            R.style.Theme_Tachiyomi_Tako -> "#66577E"
            R.style.Theme_Tachiyomi_YinYang -> "#000000"
            R.style.Theme_Tachiyomi_MidnightDusk -> "#c43c97"
            R.style.Theme_Tachiyomi_Strawberries -> "#ED4A65"
            R.style.Theme_Tachiyomi_Yotsuba -> "#dc6d3d"
            else -> "#2979FF"
        }
    )

    /** Complies with colorSecondary (probably night) */
    @ColorInt
    val darkAccent: Int = Color.parseColor(
        when (styleRes) {
            R.style.Theme_Tachiyomi_Tako -> "#F3B375"
            R.style.Theme_Tachiyomi_YinYang -> "#FFFFFF"
            R.style.Theme_Tachiyomi_MidnightDusk -> "#F02475"
            R.style.Theme_Tachiyomi_Strawberries -> "#AA2200"
            R.style.Theme_Tachiyomi_FlatLime -> "#4AF88A"
            else -> "#3399FF"
        }
    )

    /** Complies with colorSecondary */
    @ColorInt
    val lightAppBar: Int = when (styleRes) {
        R.style.Theme_Tachiyomi_AllBlue -> Color.parseColor("#54759E")
        else -> lightBackground
    }

    /** Complies with colorSecondary (probably night) */
    @ColorInt
    val darkAppBar: Int = when (styleRes) {
        R.style.Theme_Tachiyomi_AllBlue -> Color.parseColor("#54759E")
        else -> darkBackground
    }

    /** Complies with actionBarTintColor */
    @ColorInt
    val lightAppBarText: Int = when (styleRes) {
        R.style.Theme_Tachiyomi_Tako -> Color.parseColor("#221b28")
        R.style.Theme_Tachiyomi_AllBlue -> Color.parseColor("#FFFFFF")
        R.style.Theme_Tachiyomi_MidnightDusk -> Color.parseColor("#DE4c0d4b")
        else -> lightPrimaryText
    }

    /** Complies with actionBarTintColor (probably night) */
    @ColorInt
    val darkAppBarText: Int = when (styleRes) {
        R.style.Theme_Tachiyomi_Tako -> Color.parseColor("#f4ece5")
        R.style.Theme_Tachiyomi_AllBlue -> Color.parseColor("#FFFFFF")
        else -> darkPrimaryText
    }

    /** Complies with colorPrimaryVariant */
    @ColorInt
    val lightBottomBar: Int = Color.parseColor(
        when (styleRes) {
            R.style.Theme_Tachiyomi_Tako -> "#F7F5FF"
            R.style.Theme_Tachiyomi_AllBlue -> "#54759E"
            R.style.Theme_Tachiyomi_MidnightDusk -> "#efe3f3"
            else -> "#FFFFFF"
        }
    )

    /** Complies with colorPrimaryVariant (probably night) */
    @ColorInt
    val darkBottomBar: Int = Color.parseColor(
        when (styleRes) {
            R.style.Theme_Tachiyomi_Tako -> "#2A2A3C"
            R.style.Theme_Tachiyomi_Strawberries -> "#211b19"
            R.style.Theme_Tachiyomi_AllBlue -> "#54759E"
            R.style.Theme_Tachiyomi_MidnightDusk -> "#201F27"
            R.style.Theme_Tachiyomi_FlatLime -> "#282A2E"
            else -> "#212121"
        }
    )

    /** Complies with tabBarIconInactive */
    @ColorInt
    val lightInactiveTab: Int = when (styleRes) {
        R.style.Theme_Tachiyomi_Tako -> Color.parseColor("#C2221b28")
        R.style.Theme_Tachiyomi_AllBlue -> Color.parseColor("#80FFFFFF")
        else -> Color.parseColor("#C2424242")
    }

    /** Complies with tabBarIconInactive (probably night) */
    @ColorInt
    val darkInactiveTab: Int = when (styleRes) {
        R.style.Theme_Tachiyomi_Tako -> Color.parseColor("#C2f4ece5")
        R.style.Theme_Tachiyomi_AllBlue -> Color.parseColor("#80FFFFFF")
        else -> Color.parseColor("#C2FFFFFF")
    }

    /** Complies with tabBarIconColor or colorSecondary */
    @ColorInt
    val lightActiveTab: Int = when (styleRes) {
        R.style.Theme_Tachiyomi_AllBlue -> lightAppBarText
        else -> lightAccent
    }

    /** Complies with tabBarIconColor or colorAccent (probably night) */
    @ColorInt
    val darkActiveTab: Int = when (styleRes) {
        R.style.Theme_Tachiyomi_AllBlue -> darkAppBarText
        else -> darkAccent
    }

    data class Colors(
        @ColorInt val primaryText: Int,
        @ColorInt val secondaryText: Int,
        @ColorInt val background: Int,
        @ColorInt val colorSecondary: Int,
        /** Complies with colorSecondary */
        @ColorInt val appBar: Int,
        /** Complies with actionBarTintColor */
        @ColorInt val appBarText: Int,
        /** Complies with colorPrimaryVariant */
        @ColorInt val bottomBar: Int,
        /** Complies with tabBarIconInactive */
        @ColorInt val inactiveTab: Int,
        /** Complies with tabBarIconColor */
        @ColorInt val activeTab: Int,
    )
}
