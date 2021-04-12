package eu.kanade.tachiyomi.util.system

import android.app.Activity
import android.content.Context
import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.ColorUtils
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import kotlin.math.roundToInt

object ThemeUtil {

    /** Migration method */
    fun convertTheme(preferences: PreferencesHelper, theme: Int) {
        preferences.nightMode().set(
            when (theme) {
                0, 1 -> AppCompatDelegate.MODE_NIGHT_NO
                2, 3, 4 -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
        preferences.lightTheme().set(
            when (theme) {
                1 -> Themes.LIGHT_BLUE
                else -> Themes.PURE_WHITE
            }
        )
        preferences.darkTheme().set(
            when (theme) {
                3 -> Themes.AMOLED
                4 -> Themes.DARK_BLUE
                else -> Themes.DARK
            }
        )
    }

    fun isColoredTheme(theme: Themes): Boolean {
        return theme.styleRes == R.style.Theme_Tachiyomi_AllBlue
    }

    fun isPitchBlack(context: Context, theme: Themes): Boolean {
        return context.isInNightMode() && theme.styleRes == R.style.Theme_Tachiyomi_Amoled
    }

    fun hasDarkActionBarInLight(context: Context, theme: Themes): Boolean {
        return !context.isInNightMode() && isColoredTheme(theme)
    }

    fun readerBackgroundColor(theme: Int): Int {
        return when (theme) {
            1 -> Color.BLACK
            else -> Color.WHITE
        }
    }

    @Suppress("unused")
    enum class Themes(@StyleRes val styleRes: Int, val nightMode: Int, @StringRes val nameRes: Int) {
        PURE_WHITE(R.style.Theme_Tachiyomi, AppCompatDelegate.MODE_NIGHT_NO, R.string.white_theme),
        DARK(R.style.Theme_Tachiyomi, AppCompatDelegate.MODE_NIGHT_YES, R.string.dark),
        SPRING(
            R.style.Theme_Tachiyomi_MidnightDusk,
            AppCompatDelegate.MODE_NIGHT_NO,
            R.string.spring_blossom
        ),
        DUSK(
            R.style.Theme_Tachiyomi_MidnightDusk,
            AppCompatDelegate.MODE_NIGHT_YES,
            R.string.midnight_dusk
        ),
        AMOLED(
            R.style.Theme_Tachiyomi_Amoled,
            AppCompatDelegate.MODE_NIGHT_YES,
            R.string.amoled_black
        ),
        BLACK_N_RED(
            R.style.Theme_Tachiyomi_BlackAndRed,
            AppCompatDelegate.MODE_NIGHT_YES,
            R.string.black_and_red
        ),
        HOT_PINK(
            R.style.Theme_Tachiyomi_HotPink,
            AppCompatDelegate.MODE_NIGHT_YES,
            R.string.hot_pink
        ),
        LIME(
            R.style.Theme_Tachiyomi_FlatLime,
            AppCompatDelegate.MODE_NIGHT_YES,
            R.string.flat_lime
        ),
        LIGHT_BLUE(
            R.style.Theme_Tachiyomi_AllBlue,
            AppCompatDelegate.MODE_NIGHT_NO,
            R.string.light_blue
        ),
        DARK_BLUE(
            R.style.Theme_Tachiyomi_AllBlue,
            AppCompatDelegate.MODE_NIGHT_YES,
            R.string.dark_blue
        );

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

        @ColorInt
        val lightPrimaryText: Int = Color.parseColor(
            when (styleRes) {
                R.style.Theme_Tachiyomi_MidnightDusk -> "#DE240728"
                else -> "#DE000000"
            }
        )

        @ColorInt
        val darkPrimaryText: Int = Color.parseColor("#FFFFFFFF")

        @ColorInt
        val lightSecondaryText: Int = ColorUtils.setAlphaComponent(lightPrimaryText, (0.54f * 255f).roundToInt())

        @ColorInt
        val darkSecondaryText: Int = ColorUtils.setAlphaComponent(darkPrimaryText, (0.54f * 255f).roundToInt())

        @ColorInt
        val lightBackground: Int = Color.parseColor(
            when (styleRes) {
                R.style.Theme_Tachiyomi_MidnightDusk -> "#f6f0f8"
                else -> "#FAFAFA"
            }
        )

        @ColorInt
        val darkBackground: Int = Color.parseColor(
            when (styleRes) {
                R.style.Theme_Tachiyomi_Amoled, R.style.Theme_Tachiyomi_BlackAndRed, R.style.Theme_Tachiyomi_HotPink -> "#000000"
                R.style.Theme_Tachiyomi_MidnightDusk -> "#16151D"
                R.style.Theme_Tachiyomi_FlatLime -> "#16151D"
                else -> "#1C1C1D"
            }
        )

        @ColorInt
        val lightAccent: Int = Color.parseColor(
            when (styleRes) {
                R.style.Theme_Tachiyomi_MidnightDusk -> "#c43c97"
                else -> "#2979FF"
            }
        )

        @ColorInt
        val darkAccent: Int = Color.parseColor(
            when (styleRes) {
                R.style.Theme_Tachiyomi_MidnightDusk -> "#F02475"
                R.style.Theme_Tachiyomi_BlackAndRed -> "#AA2200"
                R.style.Theme_Tachiyomi_HotPink -> "#FF3399"
                R.style.Theme_Tachiyomi_FlatLime -> "#4AF88A"
                else -> "#3399FF"
            }
        )

        @ColorInt
        val lightAppBar: Int = when (styleRes) {
            R.style.Theme_Tachiyomi_AllBlue -> Color.parseColor("#54759E")
            else -> lightBackground
        }

        @ColorInt
        val darkAppBar: Int = when (styleRes) {
            R.style.Theme_Tachiyomi_AllBlue -> Color.parseColor("#54759E")
            else -> darkBackground
        }

        @ColorInt
        val lightAppBarText: Int = when (styleRes) {
            R.style.Theme_Tachiyomi_AllBlue -> Color.parseColor("#FFFFFF")
            R.style.Theme_Tachiyomi_MidnightDusk -> Color.parseColor("#DE4c0d4b")
            else -> lightPrimaryText
        }

        @ColorInt
        val darkAppBarText: Int = when (styleRes) {
            R.style.Theme_Tachiyomi_AllBlue -> Color.parseColor("#FFFFFF")
            else -> darkPrimaryText
        }

        @ColorInt
        val lightBottomBar: Int = Color.parseColor(
            when (styleRes) {
                R.style.Theme_Tachiyomi_AllBlue -> "#54759E"
                R.style.Theme_Tachiyomi_MidnightDusk -> "#efe3f3"
                else -> "#FFFFFF"
            }
        )

        @ColorInt
        val darkBottomBar: Int = Color.parseColor(
            when (styleRes) {
                R.style.Theme_Tachiyomi_AllBlue -> "#54759E"
                R.style.Theme_Tachiyomi_Amoled, R.style.Theme_Tachiyomi_BlackAndRed, R.style.Theme_Tachiyomi_HotPink -> "#000000"
                R.style.Theme_Tachiyomi_MidnightDusk -> "#201F27"
                else -> "#212121"
            }
        )

        @ColorInt
        val lightInactiveTab: Int = when (styleRes) {
            R.style.Theme_Tachiyomi_AllBlue -> Color.parseColor("#80FFFFFF")
            else -> Color.parseColor("#C2424242")
        }

        @ColorInt
        val darkInactiveTab: Int = when (styleRes) {
            R.style.Theme_Tachiyomi_AllBlue -> Color.parseColor("#80FFFFFF")
            else -> Color.parseColor("#C2FFFFFF")
        }

        @ColorInt
        val lightActiveTab: Int = when (styleRes) {
            R.style.Theme_Tachiyomi_AllBlue -> lightAppBarText
            else -> lightAccent
        }

        @ColorInt
        val darkActiveTab: Int = when (styleRes) {
            R.style.Theme_Tachiyomi_AllBlue -> darkAppBarText
            else -> darkAccent
        }
    }

    data class Colors(
        @ColorInt val primaryText: Int,
        @ColorInt val secondaryText: Int,
        @ColorInt val colorBackground: Int,
        @ColorInt val colorAccent: Int,
        @ColorInt val appBar: Int,
        @ColorInt val appBarText: Int,
        @ColorInt val bottomBar: Int,
        @ColorInt val inactiveTab: Int,
        @ColorInt val activeTab: Int,
    )
}

fun Activity.setThemeAndNight(preferences: PreferencesHelper) {
    if (preferences.nightMode().isNotSet()) {
        ThemeUtil.convertTheme(preferences, preferences.oldTheme())
    }
    AppCompatDelegate.setDefaultNightMode(preferences.nightMode().get())
    val theme = getPrefTheme(preferences)
    setTheme(theme.styleRes)
}

fun Context.getPrefTheme(preferences: PreferencesHelper): ThemeUtil.Themes {
    // Using a try catch in case I start to remove themes
    return try {
        (
            if ((applicationContext.isInNightMode() || preferences.nightMode().get() == AppCompatDelegate.MODE_NIGHT_YES) &&
                preferences.nightMode().get() != AppCompatDelegate.MODE_NIGHT_NO
            ) preferences.darkTheme() else preferences.lightTheme()
            ).get()
    } catch (e: Exception) {
        preferences.lightTheme().set(ThemeUtil.Themes.PURE_WHITE)
        preferences.darkTheme().set(ThemeUtil.Themes.DARK)
        ThemeUtil.Themes.PURE_WHITE
    }
}
