package eu.kanade.tachiyomi.network

import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.JavaNetCookieJar
import java.net.CookieManager
import java.net.HttpCookie

class DesktopCookieStore : CookieStore {

    private val manager = CookieManager()
    private val cookieJar = JavaNetCookieJar(manager)

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookieJar.saveFromResponse(url, cookies)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return cookieJar.loadForRequest(url)
    }

    override fun get(url: HttpUrl): List<Cookie> {
        return loadForRequest(url)
    }

    override fun remove(url: HttpUrl, cookieNames: List<String>?, maxAge: Int): Int {
        val uri = url.toUri()
        val store = manager.cookieStore
        val toRemove = store.get(uri)
            .filter { cookieNames == null || it.name in cookieNames }

        toRemove.forEach { cookie ->
            val expired = HttpCookie(cookie.name, "").apply {
                domain = cookie.domain
                path = cookie.path
                maxAge = maxAge.toLong()
            }
            store.remove(uri, cookie)
            if (maxAge >= 0) {
                store.add(uri, expired)
            }
        }

        return toRemove.size
    }

    override fun removeAll() {
        manager.cookieStore.removeAll()
    }
}
