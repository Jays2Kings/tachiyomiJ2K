package eu.kanade.tachiyomi.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Cookie storage contract shared by common networking code.
 *
 * Implementations must remain thread-safe and keep behavior equivalent to [CookieJar]
 * while also supporting targeted removals through [remove].
 */
interface CookieStore : CookieJar {
    fun get(url: HttpUrl): List<Cookie>

    fun remove(url: HttpUrl, cookieNames: List<String>? = null, maxAge: Int = -1): Int

    fun removeAll()
}

/**
 * Optional web challenge handler (e.g. Cloudflare flows) wired in the HTTP stack.
 */
fun interface WebChallengeSolver {
    fun intercept(chain: Interceptor.Chain, request: Request, response: Response): Response
}

/**
 * Factory for creating preconfigured [OkHttpClient.Builder] instances.
 *
 * Target implementations must expose a shared [cookieStore] and can optionally expose
 * [webChallengeSolver] when the platform supports challenge bypass handling.
 */
interface PlatformHttpClientFactory {
    val cookieStore: CookieStore
    val webChallengeSolver: WebChallengeSolver?

    fun newBuilder(): OkHttpClient.Builder
}
