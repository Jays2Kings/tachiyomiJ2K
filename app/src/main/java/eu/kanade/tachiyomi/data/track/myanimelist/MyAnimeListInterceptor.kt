package eu.kanade.tachiyomi.data.track.myanimelist

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import org.json.JSONObject

class MyAnimeListInterceptor(private val myanimelist: MyAnimeList) : Interceptor {

    val scope = CoroutineScope(Job() + Dispatchers.Main)

    override fun intercept(chain: Interceptor.Chain): Response {
        scope.launch {
            myanimelist.ensureLoggedIn()
        }
        val request = chain.request()
        var response = chain.proceed(updateRequest(request))

        if (response.code == 400) {
            scope.launch {
                myanimelist.refreshLogin()
            }
            response.close()
            response = chain.proceed(updateRequest(request))
        }

        return response
    }

    private fun updateRequest(request: Request): Request {
        return request.body?.let {
            val contentType = it.contentType().toString()
            val updatedBody = when {
                contentType.contains("x-www-form-urlencoded") -> updateFormBody(it)
                contentType.contains("json") -> updateJsonBody(it)
                else -> it
            }
            request.newBuilder().post(updatedBody).build()
        } ?: request
    }

    private fun bodyToString(requestBody: RequestBody): String {
        Buffer().use {
            requestBody.writeTo(it)
            return it.readUtf8()
        }
    }

    private fun updateFormBody(requestBody: RequestBody): RequestBody {
        val formString = bodyToString(requestBody)

        return "$formString${if (formString.isNotEmpty()) "&" else ""}${MyAnimeListApi.CSRF}=${myanimelist.getCSRF()}".toRequestBody(
            requestBody.contentType()
        )
    }

    private fun updateJsonBody(requestBody: RequestBody): RequestBody {
        val jsonString = bodyToString(requestBody)
        val newBody = JSONObject(jsonString)
            .put(MyAnimeListApi.CSRF, myanimelist.getCSRF())

        return newBody.toString().toRequestBody(requestBody.contentType())
    }
}
