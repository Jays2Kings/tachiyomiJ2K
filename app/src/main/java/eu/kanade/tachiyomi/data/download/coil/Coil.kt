package eu.kanade.tachiyomi.data.download.coil

import android.content.Context
import coil.ImageLoader
import coil.map.Mapper
import coil.request.CachePolicy
import eu.kanade.tachiyomi.data.database.models.Manga

class CoilMangaMapper() : Mapper<Manga, String> {
override fun map(data: Manga): String = data.thumbnail_url?:""
}
