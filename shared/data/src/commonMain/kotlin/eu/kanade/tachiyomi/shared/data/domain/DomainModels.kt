package eu.kanade.tachiyomi.shared.data.domain

data class DomainManga(
    val id: Long? = null,
    val source: Long,
    val url: String,
    val title: String,
    val artist: String? = null,
    val author: String? = null,
    val description: String? = null,
    val genre: String? = null,
    val status: Long = 0,
    val thumbnailUrl: String? = null,
    val initialized: Boolean = false,
    val updateStrategy: Long = 0,
)

data class DomainChapter(
    val id: Long? = null,
    val mangaId: Long,
    val url: String,
    val name: String,
    val scanlator: String? = null,
    val read: Boolean = false,
    val bookmark: Boolean = false,
    val dateFetch: Long = 0,
    val dateUpload: Long = 0,
    val chapterNumber: Double = -1.0,
    val sourceOrder: Long = 0,
)
