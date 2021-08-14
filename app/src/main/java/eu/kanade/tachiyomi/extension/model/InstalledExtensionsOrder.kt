package eu.kanade.tachiyomi.extension.model

import androidx.annotation.StringRes
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper

enum class InstalledExtensionsOrder(val value: Int, @StringRes val nameRes: Int) {
    Name(0, R.string.name),
    RecentlyUpdated(1, R.string.recently_updated),
    RecentlyInstalled(2, R.string.recently_installed),
    Language(3, R.string.language),
    ;

    companion object {
        fun fromValue(preference: Int) = values().find { it.value == preference } ?: Name
        fun fromPreference(pref: PreferencesHelper) = fromValue(pref.installedExtensionsOrder().get())
    }
}
