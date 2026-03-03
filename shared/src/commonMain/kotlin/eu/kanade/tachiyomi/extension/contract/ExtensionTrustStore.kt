package eu.kanade.tachiyomi.extension.contract

/**
 * Contract for persisting extension trust decisions.
 *
 * Implementations must store trust by stable extension id + fingerprint so the same extension
 * is consistently accepted/rejected between app launches.
 */
interface ExtensionTrustStore {
    /**
     * Returns true when [fingerprint] is currently trusted for [extensionId].
     */
    suspend fun isTrusted(extensionId: String, fingerprint: String): Boolean

    /**
     * Persists [fingerprint] as trusted for [extensionId].
     */
    suspend fun trust(extensionId: String, fingerprint: String)

    /**
     * Clears any trust entry for [extensionId].
     */
    suspend fun revoke(extensionId: String)
}
