package eu.kanade.tachiyomi.network

import okhttp3.OkHttpClient

class DesktopPlatformHttpClientFactory(
    override val cookieStore: CookieStore = DesktopCookieStore(),
    private val cloudflareBypassEnabled: Boolean = false,
) : PlatformHttpClientFactory {

    override val webChallengeSolver: WebChallengeSolver? =
        if (cloudflareBypassEnabled) {
            WebChallengeSolver { _, _, response -> response }
        } else {
            null
        }

    override fun newBuilder(): OkHttpClient.Builder {
        return OkHttpClient.Builder().cookieJar(cookieStore)
    }
}
