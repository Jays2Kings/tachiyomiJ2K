package eu.kanade.tachiyomi.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okhttp3.Cookie
import okhttp3.Headers
import okhttp3.OkHttpClient

class SharedHttpClientPolicyContractTest {
    @Test
    fun withSharedDefaults_addsDefaultHeadersWhenMissing() {
        val headers = Headers.headersOf()

        val merged = headers.withSharedDefaults(SharedHttpClientPolicy.fallbackUserAgent)

        assertEquals(SharedHttpClientPolicy.acceptHeaderValue, merged[SharedHttpClientPolicy.acceptHeader])
        assertEquals(SharedHttpClientPolicy.fallbackUserAgent, merged[SharedHttpClientPolicy.userAgentHeader])
    }

    @Test
    fun withSharedDefaults_keepsExplicitHeaders() {
        val customUserAgent = "Custom UA"
        val headers = Headers.headersOf(
            SharedHttpClientPolicy.acceptHeader,
            "application/json",
            SharedHttpClientPolicy.userAgentHeader,
            customUserAgent,
        )

        val merged = headers.withSharedDefaults(SharedHttpClientPolicy.fallbackUserAgent)

        assertEquals("application/json", merged[SharedHttpClientPolicy.acceptHeader])
        assertEquals(customUserAgent, merged[SharedHttpClientPolicy.userAgentHeader])
    }

    @Test
    fun applyTo_setsSharedTimeoutAndRedirectContracts() {
        val cookieStore = InMemoryCookieStore()
        val client = SharedHttpClientPolicy.applyTo(OkHttpClient.Builder(), cookieStore).build()

        assertEquals(cookieStore, client.cookieJar)
        assertEquals(SharedHttpClientPolicy.connectTimeout.inWholeMilliseconds.toInt(), client.connectTimeoutMillis)
        assertEquals(SharedHttpClientPolicy.readTimeout.inWholeMilliseconds.toInt(), client.readTimeoutMillis)
        assertEquals(SharedHttpClientPolicy.callTimeout.inWholeMilliseconds.toInt(), client.callTimeoutMillis)
        assertEquals(SharedHttpClientPolicy.followRedirects, client.followRedirects)
        assertEquals(SharedHttpClientPolicy.followSslRedirects, client.followSslRedirects)
        assertTrue(client.interceptors.isNotEmpty())
    }

    private class InMemoryCookieStore : CookieStore {
        private val cookiesByHost = mutableMapOf<String, MutableList<Cookie>>()

        override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<Cookie>) {
            val bucket = cookiesByHost.getOrPut(url.host) { mutableListOf() }
            bucket.removeAll { existing -> cookies.any { it.name == existing.name } }
            bucket.addAll(cookies)
        }

        override fun loadForRequest(url: okhttp3.HttpUrl): List<Cookie> = cookiesByHost[url.host].orEmpty()

        override fun get(url: okhttp3.HttpUrl): List<Cookie> = loadForRequest(url)

        override fun remove(url: okhttp3.HttpUrl, cookieNames: List<String>?, maxAge: Int): Int {
            val bucket = cookiesByHost[url.host] ?: return 0
            val before = bucket.size
            bucket.removeAll { cookie -> cookieNames == null || cookie.name in cookieNames }
            return before - bucket.size
        }

        override fun removeAll() {
            cookiesByHost.clear()
        }
    }
}
