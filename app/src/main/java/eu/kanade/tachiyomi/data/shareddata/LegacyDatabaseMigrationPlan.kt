package eu.kanade.tachiyomi.data.shareddata

/**
 * SQL steps for migrating the legacy StorIO tables to the shared:data SQLDelight schema.
 *
 * The rollout keeps both schemas alive; old tables are copied into `legacy_*` once, and then
 * mirrored by [SharedDataMigrationAdapter].
 */
object LegacyDatabaseMigrationPlan {
    val statements = listOf(
        "ALTER TABLE mangas RENAME TO legacy_mangas",
        "ALTER TABLE chapters RENAME TO legacy_chapters",
        "CREATE TABLE IF NOT EXISTS mangas(\n" +
            "  _id INTEGER PRIMARY KEY, source INTEGER NOT NULL, url TEXT NOT NULL, artist TEXT, author TEXT,\n" +
            "  description TEXT, genre TEXT, title TEXT NOT NULL, status INTEGER NOT NULL, thumbnail_url TEXT,\n" +
            "  favorite INTEGER NOT NULL DEFAULT 0, last_update INTEGER, initialized INTEGER NOT NULL,\n" +
            "  viewer INTEGER NOT NULL DEFAULT 0, hideTitle INTEGER NOT NULL DEFAULT 0, chapter_flags INTEGER NOT NULL DEFAULT 0,\n" +
            "  date_added INTEGER, filtered_scanlators TEXT, update_strategy INTEGER NOT NULL DEFAULT 0\n" +
            ")",
        "CREATE TABLE IF NOT EXISTS chapters(\n" +
            "  _id INTEGER PRIMARY KEY, manga_id INTEGER NOT NULL REFERENCES mangas(_id) ON DELETE CASCADE,\n" +
            "  url TEXT NOT NULL, name TEXT NOT NULL, scanlator TEXT, read INTEGER NOT NULL, bookmark INTEGER NOT NULL,\n" +
            "  last_page_read INTEGER NOT NULL DEFAULT 0, pages_left INTEGER NOT NULL DEFAULT 0, chapter_number REAL NOT NULL,\n" +
            "  source_order INTEGER NOT NULL DEFAULT 0, date_fetch INTEGER NOT NULL, date_upload INTEGER NOT NULL\n" +
            ")",
        "INSERT OR IGNORE INTO mangas SELECT * FROM legacy_mangas",
        "INSERT OR IGNORE INTO chapters SELECT * FROM legacy_chapters",
    )
}
