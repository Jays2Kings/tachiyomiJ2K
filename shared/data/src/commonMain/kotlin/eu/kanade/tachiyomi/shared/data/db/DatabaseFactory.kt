package eu.kanade.tachiyomi.shared.data.db

import app.cash.sqldelight.db.SqlDriver

private val REQUIRED_PRAGMAS = listOf(
    "foreign_keys = ON",
    "journal_mode = WAL",
    "synchronous = NORMAL",
)

expect class DataDriverFactory(platformContext: Any? = null) {
    fun createDriver(dbName: String = "shared_data.db"): SqlDriver
}

class SharedDatabaseFactory(
    private val dataDriverFactory: DataDriverFactory,
) {
    fun create(dbName: String = "shared_data.db"): SharedDataDatabase {
        val driver = dataDriverFactory.createDriver(dbName)
        REQUIRED_PRAGMAS.forEach { pragma ->
            driver.execute(
                identifier = null,
                sql = "PRAGMA $pragma",
                parameters = 0,
            )
        }
        return SharedDataDatabase(driver)
    }
}
