package eu.kanade.tachiyomi.network

import android.content.Context
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import okhttp3.OkHttpClient

class AndroidPlatformHttpClientFactory(
    private val context: Context,
    private val defaultUserAgentProvider: () -> String,
) : PlatformHttpClientFactory {

    override val cookieStore: CookieStore = AndroidCookieJar()

    override val webChallengeSolver = WebChallengeSolver { chain, request, response ->
        CloudflareInterceptor(context, cookieStore, defaultUserAgentProvider).intercept(chain, request, response)
    }

    override fun newBuilder(): OkHttpClient.Builder {
        return SharedHttpClientPolicy.applyTo(
            builder = OkHttpClient.Builder(),
            cookieStore = cookieStore,
            userAgentProvider = defaultUserAgentProvider,
        )
    }
}
