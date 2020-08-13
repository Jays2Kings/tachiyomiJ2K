package eu.kanade.tachiyomi.network

import android.content.Context
import com.chuckerteam.chucker.api.ChuckerInterceptor
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.net.InetAddress

class NetworkHelper(context: Context) {

    private val preferences: PreferencesHelper by injectLazy()

    private val cacheDir = File(context.cacheDir, "network_cache")

    private val cacheSize = 5L * 1024 * 1024 // 5 MiB

    val cookieManager = AndroidCookieJar()

    val client by lazy {
        OkHttpClient.Builder()
            .cookieJar(cookieManager)
            .cache(Cache(cacheDir, cacheSize))
            .apply {
                if(BuildConfig.DEBUG){
                    addInterceptor(ChuckerInterceptor(context))
                }
                if (preferences.enableDoh()) {
                    dns(
                        DnsOverHttps.Builder().client(build())
                            .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
                            .bootstrapDnsHosts(
                                listOf(
                                    InetAddress.getByName("162.159.36.1"),
                                    InetAddress.getByName("162.159.46.1"),
                                    InetAddress.getByName("1.1.1.1"),
                                    InetAddress.getByName("1.0.0.1"),
                                    InetAddress.getByName("162.159.132.53"),
                                    InetAddress.getByName("2606:4700:4700::1111"),
                                    InetAddress.getByName("2606:4700:4700::1001"),
                                    InetAddress.getByName("2606:4700:4700::0064"),
                                    InetAddress.getByName("2606:4700:4700::6400")
                                )
                            ).build()
                    )
                }
            }.build()
    }

    val cloudflareClient = client.newBuilder()
        .addInterceptor(UserAgentInterceptor())
        .addInterceptor(CloudflareInterceptor(context))
        .build()
}
