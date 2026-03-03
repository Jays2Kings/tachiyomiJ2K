package eu.kanade.tachiyomi.network

import okhttp3.Cookie
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

class AndroidSharedCookieStore : CookieStore {

    private val byHost = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val bucket = byHost.getOrPut(host) { mutableListOf() }
        bucket.removeAll { existing -> cookies.any { it.name == existing.name } }
        bucket.addAll(cookies)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return byHost[url.host].orEmpty().filter { it.matches(url) }
    }

    override fun get(url: HttpUrl): List<Cookie> = loadForRequest(url)

    override fun remove(url: HttpUrl, cookieNames: List<String>?, maxAge: Int): Int {
        val host = url.host
        val cookies = byHost[host] ?: return 0
        val before = cookies.size
        cookies.removeAll { cookie -> cookieNames == null || cookie.name in cookieNames }
        return before - cookies.size
    }

    override fun removeAll() {
        byHost.clear()
    }
}
