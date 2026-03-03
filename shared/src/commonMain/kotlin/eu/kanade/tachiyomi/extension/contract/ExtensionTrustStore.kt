package eu.kanade.tachiyomi.extension.contract

interface ExtensionTrustStore {
    suspend fun isTrusted(extensionId: String, fingerprint: String): Boolean

    suspend fun trust(extensionId: String, fingerprint: String)

    suspend fun revoke(extensionId: String)
}
