package eu.kanade.tachiyomi.data.database.tables

/**
 * Table containing extension repository information.
 */
object ExtensionRepoTable {
    /**
     * Table name.
     */
    const val TABLE = "extension_repos"

    /**
     * Base URL of the repository, used as the primary key.
     * Must be unique.
     * Text column.
     */
    const val COL_BASE_URL = "base_url"

    /**
     * Display name of the repository.
     * Text column.
     */
    const val COL_NAME = "name"

    /**
     * Optional short name for the repository.
     * Text column.
     */
    const val COL_SHORT_NAME = "short_name"

    /**
     * Website URL for the repository.
     * Text column.
     */
    const val COL_WEBSITE = "website"

    /**
     * Signing key fingerprint for the repository.
     * Must be unique.
     * Text column.
     */
    const val COL_SIGNING_KEY_FINGERPRINT = "signing_key_fingerprint"

    /**
     * SQL query to create the table.
     */
    val createTableQuery: String
        get() =
            """CREATE TABLE $TABLE(
            $COL_BASE_URL TEXT NOT NULL PRIMARY KEY,
            $COL_NAME TEXT NOT NULL,
            $COL_SHORT_NAME TEXT,
            $COL_WEBSITE TEXT NOT NULL,
            $COL_SIGNING_KEY_FINGERPRINT TEXT UNIQUE NOT NULL
            )"""
}
