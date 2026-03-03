package eu.kanade.tachiyomi.shared.data.db

import app.cash.sqldelight.db.SqlDriver

expect class DataDriverFactory(platformContext: Any? = null) {
    fun createDriver(dbName: String = "shared_data.db"): SqlDriver
}

class SharedDatabaseFactory(
    private val dataDriverFactory: DataDriverFactory,
) {
    fun create(dbName: String = "shared_data.db"): SharedDataDatabase {
        val driver = dataDriverFactory.createDriver(dbName)
        return SharedDataDatabase(driver)
    }
}
