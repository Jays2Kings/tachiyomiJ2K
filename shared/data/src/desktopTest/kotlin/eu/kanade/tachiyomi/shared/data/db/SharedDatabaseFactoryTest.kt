package eu.kanade.tachiyomi.shared.data.db

import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SharedDatabaseFactoryTest {

    @Test
    fun setsRequiredPragmasOnOpen() {
        val dbPath = Files.createTempFile("shared-data-pragmas", ".db")
        val database = SharedDatabaseFactory(DataDriverFactory()).create(dbPath.toString())
        database.close()

        DriverManager.getConnection("jdbc:sqlite:$dbPath").use { connection ->
            assertEquals(1L, queryPragmaLong(connection, "foreign_keys"))
            assertEquals("wal", queryPragmaText(connection, "journal_mode").lowercase())
            assertEquals(1L, queryPragmaLong(connection, "synchronous"))
        }
    }

    @Test
    fun copiesLegacyRowsWithAndroidCompatibleSchema() {
        val dbPath = Files.createTempFile("shared-data-legacy", ".db")

        DriverManager.getConnection("jdbc:sqlite:$dbPath").use { connection ->
            connection.createStatement().use { stmt ->
                stmt.execute(
                    """
                    CREATE TABLE legacy_mangas(
                      _id INTEGER NOT NULL PRIMARY KEY,
                      source INTEGER NOT NULL,
                      url TEXT NOT NULL,
                      artist TEXT,
                      author TEXT,
                      description TEXT,
                      genre TEXT,
                      title TEXT NOT NULL,
                      status INTEGER NOT NULL,
                      thumbnail_url TEXT,
                      favorite INTEGER NOT NULL,
                      last_update LONG,
                      initialized BOOLEAN NOT NULL,
                      viewer INTEGER NOT NULL,
                      hideTitle INTEGER NOT NULL,
                      chapter_flags INTEGER NOT NULL,
                      date_added LONG,
                      filtered_scanlators TEXT,
                      update_strategy INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent(),
                )
                stmt.execute(
                    """
                    CREATE TABLE legacy_chapters(
                      _id INTEGER NOT NULL PRIMARY KEY,
                      manga_id INTEGER NOT NULL,
                      url TEXT NOT NULL,
                      name TEXT NOT NULL,
                      scanlator TEXT,
                      read BOOLEAN NOT NULL,
                      bookmark BOOLEAN NOT NULL,
                      last_page_read INTEGER NOT NULL,
                      pages_left INTEGER NOT NULL,
                      chapter_number FLOAT NOT NULL,
                      source_order INTEGER NOT NULL,
                      date_fetch LONG NOT NULL,
                      date_upload LONG NOT NULL
                    )
                    """.trimIndent(),
                )
                stmt.execute(
                    """
                    INSERT INTO legacy_mangas(
                      _id, source, url, artist, author, description, genre, title, status,
                      thumbnail_url, favorite, last_update, initialized, viewer, hideTitle,
                      chapter_flags, date_added, filtered_scanlators, update_strategy
                    ) VALUES (
                      1, 100, 'manga-url', 'artist', 'author', 'description', 'genre', 'title', 2,
                      'thumb', 1, 1700000000, 1, 0, 0,
                      0, 1700000001, 'scanlator', 0
                    )
                    """.trimIndent(),
                )
                stmt.execute(
                    """
                    INSERT INTO legacy_chapters(
                      _id, manga_id, url, name, scanlator, read, bookmark, last_page_read,
                      pages_left, chapter_number, source_order, date_fetch, date_upload
                    ) VALUES (
                      10, 1, 'chapter-url', 'chapter-name', 'scanlator', 0, 1, 3,
                      1, 12.5, 99, 1700000002, 1700000003
                    )
                    """.trimIndent(),
                )
            }
        }

        val database = SharedDatabaseFactory(DataDriverFactory()).create(dbPath.toString())
        database.sharedDataDatabaseQueries.copyMangaFromLegacy()
        database.sharedDataDatabaseQueries.copyChapterFromLegacy()

        val manga = database.sharedDataDatabaseQueries.selectMangaById(1).executeAsOneOrNull()
        assertNotNull(manga)
        assertEquals("title", manga.title)

        val chapter = database.sharedDataDatabaseQueries
            .selectChaptersByMangaId(1)
            .executeAsOneOrNull()
        assertNotNull(chapter)
        assertEquals("chapter-name", chapter.name)

        database.close()
    }

    private fun queryPragmaLong(connection: java.sql.Connection, pragma: String): Long {
        connection.createStatement().use { statement ->
            statement.executeQuery("PRAGMA $pragma").use { rs ->
                rs.next()
                return rs.getLong(1)
            }
        }
    }

    private fun queryPragmaText(connection: java.sql.Connection, pragma: String): String {
        connection.createStatement().use { statement ->
            statement.executeQuery("PRAGMA $pragma").use { rs ->
                rs.next()
                return rs.getString(1)
            }
        }
    }
}
