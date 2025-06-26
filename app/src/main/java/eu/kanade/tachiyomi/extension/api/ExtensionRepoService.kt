package eu.kanade.tachiyomi.extension.api

import androidx.core.net.toUri
import eu.kanade.tachiyomi.extension.model.ExtensionRepo
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.system.withIOContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import uy.kohesive.injekt.injectLazy

/**
 * Service class for handling Extension Repository API interactions.
 * Fetches repository details from the repo.json file.
 */
class ExtensionRepoService {
    private val networkHelper: NetworkHelper by injectLazy()
    private val json: Json by injectLazy()

    private val client: OkHttpClient
        get() = networkHelper.client // Assuming NetworkHelper provides the client

    /**
     * Fetches the repository details from the repo.json file at the given base URL.
     *
     * @param repoBaseUrl The base URL of the repository.
     * @return ExtensionRepo details or null if fetching/parsing fails.
     */
    suspend fun fetchRepoDetails(repoBaseUrl: String): ExtensionRepo? =
        withIOContext {
            val url = "$repoBaseUrl/repo.json".toUri()

            try {
                val response =
                    with(json) {
                        client
                            .newCall(GET(url.toString()))
                            .awaitSuccess()
                            .parseAs<JsonObject>()
                    }
                response["meta"]
                    ?.jsonObject
                    ?.let { jsonToExtensionRepo(baseUrl = repoBaseUrl, it) }
            } catch (_: HttpException) {
                null
            } catch (_: Exception) {
                null
            }
        }

    /**
     * Parses the JSON metadata object into an ExtensionRepo object.
     *
     * @param baseUrl The base URL of the repository.
     * @param obj The JSON object containing the metadata.
     * @return ExtensionRepo or null if parsing fails due to missing fields.
     */
    private fun jsonToExtensionRepo(
        baseUrl: String,
        obj: JsonObject,
    ): ExtensionRepo? =
        try {
            ExtensionRepo(
                baseUrl = baseUrl,
                name = obj["name"]!!.jsonPrimitive.content,
                shortName = obj["shortName"]?.jsonPrimitive?.content,
                website = obj["website"]!!.jsonPrimitive.content,
                signingKeyFingerprint = obj["signingKeyFingerprint"]!!.jsonPrimitive.content,
            )
        } catch (_: NullPointerException) {
            null
        }
}
