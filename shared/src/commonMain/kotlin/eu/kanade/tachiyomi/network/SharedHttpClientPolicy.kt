package eu.kanade.tachiyomi.network

import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

object SharedHttpClientPolicy {
    val connectTimeout = 30.seconds
    val readTimeout = 30.seconds
    val callTimeout = 2.minutes

    const val followRedirects = true
    const val followSslRedirects = true

    const val userAgentHeader = "User-Agent"
    const val acceptHeader = "Accept"
    const val acceptHeaderValue = "*/*"

    const val fallbackUserAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/118.0"

    /**
     * Shared semantic for cookie handling: every target must use a [CookieStore] as the
     * canonical persistence source for request/response cookies and targeted removals.
     */
    fun applyTo(
        builder: OkHttpClient.Builder,
        cookieStore: CookieStore,
        userAgentProvider: () -> String = { fallbackUserAgent },
    ): OkHttpClient.Builder {
        return builder
            .cookieJar(cookieStore)
            .connectTimeout(connectTimeout)
            .readTimeout(readTimeout)
            .callTimeout(callTimeout)
            .followRedirects(followRedirects)
            .followSslRedirects(followSslRedirects)
            .addInterceptor(baseHeadersInterceptor(userAgentProvider))
    }

    fun baseHeadersInterceptor(userAgentProvider: () -> String): Interceptor {
        return Interceptor { chain ->
            val request = chain.request()
            val defaultUserAgent = userAgentProvider().replace("\n", " ").trim().ifEmpty { fallbackUserAgent }
            val headers = request.headers.withSharedDefaults(defaultUserAgent)
            chain.proceed(request.newBuilder().headers(headers).build())
        }
    }

    internal fun Headers.withSharedDefaults(defaultUserAgent: String): Headers {
        return newBuilder()
            .apply {
                if (get(acceptHeader).isNullOrBlank()) {
                    set(acceptHeader, acceptHeaderValue)
                }
                if (get(userAgentHeader).isNullOrBlank()) {
                    set(userAgentHeader, defaultUserAgent)
                }
            }
            .build()
    }
}

