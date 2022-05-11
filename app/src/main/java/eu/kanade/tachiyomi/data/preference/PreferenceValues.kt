package eu.kanade.tachiyomi.data.preference

import eu.kanade.tachiyomi.R

// Library
const val MANGA_NON_COMPLETED = "manga_ongoing"
const val MANGA_HAS_UNREAD = "manga_fully_read"
const val MANGA_NON_READ = "manga_started"

// Device
const val DEVICE_ONLY_ON_WIFI = "wifi"
const val DEVICE_CHARGING = "ac"
const val DEVICE_BATTERY_NOT_LOW = "battery_not_low"

object PreferenceValues {
    enum class SecureScreenMode(val titleResId: Int) {
        ALWAYS(R.string.always),
        INCOGNITO(R.string.incognito_mode),
        NEVER(R.string.never),
    }
}
