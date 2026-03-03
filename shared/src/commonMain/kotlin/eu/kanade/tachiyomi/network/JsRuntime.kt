package eu.kanade.tachiyomi.network

interface JsRuntime : AutoCloseable {
    fun evaluate(script: String): Any?
}

expect object JsRuntimeFactory {
    fun create(): JsRuntime
}
