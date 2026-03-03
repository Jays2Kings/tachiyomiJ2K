package eu.kanade.tachiyomi.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

interface CookieStore : CookieJar {
    fun get(url: HttpUrl): List<Cookie>

    fun remove(url: HttpUrl, cookieNames: List<String>? = null, maxAge: Int = -1): Int

    fun removeAll()
}

fun interface WebChallengeSolver {
    fun intercept(chain: Interceptor.Chain, request: Request, response: Response): Response
}

interface PlatformHttpClientFactory {
    val cookieStore: CookieStore
    val webChallengeSolver: WebChallengeSolver?

    fun newBuilder(): OkHttpClient.Builder
}
