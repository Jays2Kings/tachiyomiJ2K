package eu.kanade.tachiyomi.network

/**
 * Minimal JavaScript runtime contract used by common networking code.
 *
 * Target implementations should provide deterministic `evaluate` semantics for plain scripts,
 * and release all underlying runtime resources in [close].
 */
interface JsRuntime : AutoCloseable {
    /**
     * Evaluates [script] and returns the converted runtime value.
     */
    fun evaluate(script: String): Any?
}

/**
 * Factory for the target JavaScript runtime implementation.
 *
 * Each platform must return an isolated [JsRuntime] instance suitable for short-lived execution.
 */
expect object JsRuntimeFactory {
    fun create(): JsRuntime
}
