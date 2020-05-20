package eu.kanade.tachiyomi.data.backup.serializer

import com.github.salomonbrys.kotson.typeAdapter
import com.google.gson.TypeAdapter
import eu.kanade.tachiyomi.data.database.models.MangaImpl
import kotlin.math.max

/**
 * JSON Serializer used to write / read [MangaImpl] to / from json
 */
object MangaTypeAdapter {

    fun build(): TypeAdapter<MangaImpl> {
        return typeAdapter {
            write {
                beginArray()
                value(it.url)
                value(it.originalTitle)
                value(it.source)
                value(max(0, it.viewer))
                value(it.chapter_flags)
                endArray()
            }

            read {
                beginArray()
                val manga = MangaImpl()
                manga.url = nextString()
                manga.title = nextString()
                manga.source = nextLong()
                manga.viewer = nextInt()
                manga.chapter_flags = nextInt()
                endArray()
                manga
            }
        }
    }
}
