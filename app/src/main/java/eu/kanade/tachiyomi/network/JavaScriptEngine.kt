package eu.kanade.tachiyomi.network

import eu.kanade.tachiyomi.util.system.withIOContext

/**
 * Util for evaluating JavaScript in sources.
 */
class JavaScriptEngine {

    /**
     * Evaluate arbitrary JavaScript code and get the result as a primtive type
     * (e.g., String, Int).
     *
     * @since extensions-lib 1.4
     * @param script JavaScript to execute.
     * @return Result of JavaScript code as a primitive type.
     */
    @Suppress("UNUSED", "UNCHECKED_CAST")
    suspend fun <T> evaluate(script: String): T = withIOContext {
        JsRuntimeFactory.create().use {
            it.evaluate(script) as T
        }
    }
}
