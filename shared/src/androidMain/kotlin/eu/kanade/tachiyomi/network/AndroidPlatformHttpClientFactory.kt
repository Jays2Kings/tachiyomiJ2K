package eu.kanade.tachiyomi.network

import okhttp3.OkHttpClient

class AndroidSharedPlatformHttpClientFactory(
    override val cookieStore: CookieStore = AndroidSharedCookieStore(),
) : PlatformHttpClientFactory {

    override val webChallengeSolver: WebChallengeSolver? = null

    override fun newBuilder(): OkHttpClient.Builder {
        return SharedHttpClientPolicy.applyTo(OkHttpClient.Builder(), cookieStore)
    }
}
