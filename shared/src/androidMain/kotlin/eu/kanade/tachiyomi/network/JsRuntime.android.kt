package eu.kanade.tachiyomi.network

import app.cash.quickjs.QuickJs

private class AndroidQuickJsRuntime : JsRuntime {
    private val quickJs = QuickJs.create()

    override fun evaluate(script: String): Any? {
        return quickJs.evaluate(script)
    }

    override fun close() {
        quickJs.close()
    }
}

actual object JsRuntimeFactory {
    actual fun create(): JsRuntime = AndroidQuickJsRuntime()
}
