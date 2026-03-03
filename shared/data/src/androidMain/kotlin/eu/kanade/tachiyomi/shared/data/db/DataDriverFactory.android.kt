package eu.kanade.tachiyomi.shared.data.db

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

actual class DataDriverFactory actual constructor(
    platformContext: Any?,
) {
    private val context = requireNotNull(platformContext as? Context) {
        "Android DataDriverFactory requires an android.content.Context"
    }

    actual fun createDriver(dbName: String) =
        AndroidSqliteDriver(SharedDataDatabase.Schema, context, dbName)
}
