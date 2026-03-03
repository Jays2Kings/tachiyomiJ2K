package eu.kanade.tachiyomi.network

import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable

private class DesktopRhinoRuntime : JsRuntime {
    private val context: Context = Context.enter()
    private val scope: Scriptable = context.initStandardObjects()

    override fun evaluate(script: String): Any? {
        val result = context.evaluateString(scope, script, "<script>", 1, null)
        return Context.jsToJava(result, Any::class.java)
    }

    override fun close() {
        Context.exit()
    }
}

actual object JsRuntimeFactory {
    actual fun create(): JsRuntime = DesktopRhinoRuntime()
}
