package eu.kanade.tachiyomi.data.track.kitsu

import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.int
import com.github.salomonbrys.kotson.jsonObject
import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.string
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.POST
import okhttp3.FormBody
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import timber.log.Timber

class KitsuApi(private val client: OkHttpClient, interceptor: KitsuInterceptor) {

    private val authClient = client.newBuilder().addInterceptor(interceptor).build()

    private val rest = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(authClient)
        .addConverterFactory(GsonConverterFactory.create(GsonBuilder().serializeNulls().create()))
        .build()
        .create(KitsuApi.Rest::class.java)

    private val searchRest = Retrofit.Builder()
        .baseUrl(algoliaKeyUrl)
        .client(authClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(KitsuApi.SearchKeyRest::class.java)

    private val algoliaRest = Retrofit.Builder()
        .baseUrl(algoliaUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(KitsuApi.AgoliaSearchRest::class.java)

    suspend fun addLibManga(track: Track, userId: String): Track {
        // @formatter:off
        val data = jsonObject(
            "type" to "libraryEntries",
            "attributes" to jsonObject(
                "status" to track.toKitsuStatus(),
                "progress" to track.last_chapter_read
            ),
            "relationships" to jsonObject(
                "user" to jsonObject(
                    "data" to jsonObject(
                        "id" to userId,
                        "type" to "users"
                    )
                ),
                "media" to jsonObject(
                    "data" to jsonObject(
                        "id" to track.media_id,
                        "type" to "manga"
                    )
                )
            )
        )

        val json = rest.addLibManga(jsonObject("data" to data))
        track.media_id = json["data"]["id"].int
        return track
    }

    suspend fun updateLibManga(track: Track): Track {
        // @formatter:off
        val data = jsonObject(
            "type" to "libraryEntries",
            "id" to track.media_id,
            "attributes" to jsonObject(
                "status" to track.toKitsuStatus(),
                "progress" to track.last_chapter_read,
                "ratingTwenty" to track.toKitsuScore()
            )
        )
        // @formatter:on

        rest.updateLibManga(track.media_id, jsonObject("data" to data))
        return track
    }

    suspend fun remove(track: Track): Boolean {
        try {
            rest.deleteLibManga(track.media_id)
            return true
        } catch (e: Exception) {
            Timber.w(e)
        }
        return false
    }

    suspend fun search(query: String): List<TrackSearch> {
        val key = searchRest.getKey()["media"].asJsonObject["key"].string
        return algoliaSearch(key, query)
    }

    private suspend fun algoliaSearch(key: String, query: String): List<TrackSearch> {
        val jsonObject = jsonObject("params" to "query=$query$algoliaFilter")
        val json = algoliaRest.getSearchQuery(algoliaAppId, key, jsonObject)
        val data = json["hits"].array
        return data.map { KitsuSearchManga(it.obj) }
            .filter { it.subType != "novel" }
            .map { it.toTrack() }
    }

    suspend fun findLibManga(track: Track, userId: String): Track? {
        val json = rest.findLibManga(track.media_id, userId)
        val data = json["data"].array
        return if (data.size() > 0) {
            val manga = json["included"].array[0].obj
            KitsuLibManga(data[0].obj, manga).toTrack()
        } else {
            null
        }
    }

    suspend fun getLibManga(track: Track): Track {
        val json = rest.getLibManga(track.media_id)
        val data = json["data"].array
        if (data.size() > 0) {
            val manga = json["included"].array[0].obj
            return KitsuLibManga(data[0].obj, manga).toTrack()
        } else {
            throw Exception("Could not find manga")
        }
    }

    suspend fun login(username: String, password: String): OAuth {
        return Retrofit.Builder()
            .baseUrl(loginUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(KitsuApi.LoginRest::class.java)
            .requestAccessToken(username, password)
    }

    suspend fun getCurrentUser(): String {
        val currentUser = rest.getCurrentUser()
        return currentUser["data"].array[0]["id"].string
    }

    private interface Rest {

        @Headers("Content-Type: application/vnd.api+json")
        @POST("library-entries")
        suspend fun addLibManga(
            @Body data: JsonObject
        ): JsonObject

        @Headers("Content-Type: application/vnd.api+json")
        @DELETE("library-entries/{id}")
        suspend fun deleteLibManga(
            @Path("id") remoteId: Int
        ): JsonObject

        @Headers("Content-Type: application/vnd.api+json")
        @PATCH("library-entries/{id}")
        suspend fun updateLibManga(
            @Path("id") remoteId: Int,
            @Body data: JsonObject
        ): JsonObject

        @GET("library-entries")
        suspend fun findLibManga(
            @Query("filter[manga_id]", encoded = true) remoteId: Int,
            @Query("filter[user_id]", encoded = true) userId: String,
            @Query("include") includes: String = "manga"
        ): JsonObject

        @GET("library-entries")
        suspend fun getLibManga(
            @Query("filter[id]", encoded = true) remoteId: Int,
            @Query("include") includes: String = "manga"
        ): JsonObject

        @GET("users")
        suspend fun getCurrentUser(
            @Query("filter[self]", encoded = true) self: Boolean = true
        ): JsonObject
    }

    private interface SearchKeyRest {
        @GET("media/")
        suspend fun getKey(): JsonObject
    }

    private interface AgoliaSearchRest {
        @POST("query/")
        suspend fun getSearchQuery(
            @Header("X-Algolia-Application-Id") appid: String,
            @Header("X-Algolia-API-Key") key: String,
            @Body json: JsonObject
        ): JsonObject
    }

    private interface LoginRest {

        @FormUrlEncoded
        @POST("oauth/token")
        suspend fun requestAccessToken(
            @Field("username") username: String,
            @Field("password") password: String,
            @Field("grant_type") grantType: String = "password",
            @Field("client_id") client_id: String = clientId,
            @Field("client_secret") client_secret: String = clientSecret
        ): OAuth
    }

    companion object {
        private const val clientId =
            "dd031b32d2f56c990b1425efe6c42ad847e7fe3ab46bf1299f05ecd856bdb7dd"
        private const val clientSecret =
            "54d7307928f63414defd96399fc31ba847961ceaecef3a5fd93144e960c0e151"
        private const val baseUrl = "https://kitsu.io/api/edge/"
        private const val loginUrl = "https://kitsu.io/api/"
        private const val baseMangaUrl = "https://kitsu.io/manga/"
        private const val algoliaKeyUrl = "https://kitsu.io/api/edge/algolia-keys/"
        private const val algoliaUrl =
            "https://AWQO5J657S-dsn.algolia.net/1/indexes/production_media/"
        private const val algoliaAppId = "AWQO5J657S"
        private const val algoliaFilter =
            "&facetFilters=%5B%22kind%3Amanga%22%5D&attributesToRetrieve=%5B%22synopsis%22%2C%22canonicalTitle%22%2C%22chapterCount%22%2C%22posterImage%22%2C%22startDate%22%2C%22subtype%22%2C%22endDate%22%2C%20%22id%22%5D"

        fun mangaUrl(remoteId: Int): String {
            return baseMangaUrl + remoteId
        }

        fun refreshTokenRequest(token: String) = POST(
            "${loginUrl}oauth/token",
            body = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("refresh_token", token)
                .build()
        )
    }
}
