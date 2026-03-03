package eu.kanade.tachiyomi.shared.data.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

actual class DataDriverFactory actual constructor(
    platformContext: Any?,
) {
    actual fun createDriver(dbName: String): JdbcSqliteDriver {
        val driver = JdbcSqliteDriver("jdbc:sqlite:$dbName")
        // Desktop bootstrap: create schema for first database open.
        runCatching { SharedDataDatabase.Schema.create(driver) }
        return driver
    }
}
