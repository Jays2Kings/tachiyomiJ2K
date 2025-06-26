package eu.kanade.tachiyomi.extension.model

/**
 * Represents an extension repository containing extensions.
 */

data class ExtensionRepo(
    val baseUrl: String,
    val name: String,
    val shortName: String?,
    val website: String,
    val signingKeyFingerprint: String,
)
