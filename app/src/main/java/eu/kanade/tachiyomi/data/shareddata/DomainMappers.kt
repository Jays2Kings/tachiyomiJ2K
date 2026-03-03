package eu.kanade.tachiyomi.data.shareddata

import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.ChapterImpl
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaImpl
import eu.kanade.tachiyomi.shared.data.domain.DomainChapter
import eu.kanade.tachiyomi.shared.data.domain.DomainManga
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy

fun SManga.toDomain(sourceId: Long): DomainManga = DomainManga(
    source = sourceId,
    url = url,
    title = title,
    artist = artist,
    author = author,
    description = description,
    genre = genre,
    status = status.toLong(),
    thumbnailUrl = thumbnail_url,
    initialized = initialized,
    updateStrategy = update_strategy.ordinal.toLong(),
)

fun Manga.toDomain(): DomainManga = DomainManga(
    id = id,
    source = source,
    url = url,
    title = title,
    artist = artist,
    author = author,
    description = description,
    genre = genre,
    status = status.toLong(),
    thumbnailUrl = thumbnail_url,
    initialized = initialized,
    updateStrategy = update_strategy.ordinal.toLong(),
)

fun DomainManga.toManga(): Manga = MangaImpl().apply {
    id = this@toManga.id
    source = this@toManga.source
    url = this@toManga.url
    title = this@toManga.title
    artist = this@toManga.artist
    author = this@toManga.author
    description = this@toManga.description
    genre = this@toManga.genre
    status = this@toManga.status.toInt()
    thumbnail_url = this@toManga.thumbnailUrl
    initialized = this@toManga.initialized
    update_strategy = UpdateStrategy.entries.getOrElse(this@toManga.updateStrategy.toInt()) { UpdateStrategy.ALWAYS_UPDATE }
}

fun SChapter.toDomain(mangaId: Long): DomainChapter = DomainChapter(
    mangaId = mangaId,
    url = url,
    name = name,
    scanlator = scanlator,
    dateUpload = date_upload,
    chapterNumber = chapter_number.toDouble(),
)

fun Chapter.toDomain(): DomainChapter = DomainChapter(
    id = id,
    mangaId = manga_id ?: -1L,
    url = url,
    name = name,
    scanlator = scanlator,
    read = read,
    bookmark = bookmark,
    dateFetch = date_fetch,
    dateUpload = date_upload,
    chapterNumber = chapter_number.toDouble(),
    sourceOrder = source_order.toLong(),
)

fun DomainChapter.toChapter(): Chapter = ChapterImpl().apply {
    id = this@toChapter.id
    manga_id = this@toChapter.mangaId
    url = this@toChapter.url
    name = this@toChapter.name
    scanlator = this@toChapter.scanlator
    read = this@toChapter.read
    bookmark = this@toChapter.bookmark
    date_fetch = this@toChapter.dateFetch
    date_upload = this@toChapter.dateUpload
    chapter_number = this@toChapter.chapterNumber.toFloat()
    source_order = this@toChapter.sourceOrder.toInt()
}
