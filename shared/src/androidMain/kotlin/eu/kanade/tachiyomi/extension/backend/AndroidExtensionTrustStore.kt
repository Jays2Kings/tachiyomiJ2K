package eu.kanade.tachiyomi.extension.backend

import android.content.Context
import eu.kanade.tachiyomi.extension.contract.ExtensionTrustStore

class AndroidExtensionTrustStore(context: Context) : ExtensionTrustStore {

    private val preferences = context.getSharedPreferences("extension_trust_store", Context.MODE_PRIVATE)

    override suspend fun isTrusted(extensionId: String, fingerprint: String): Boolean {
        return preferences.getString(extensionId, null) == fingerprint
    }

    override suspend fun trust(extensionId: String, fingerprint: String) {
        preferences.edit().putString(extensionId, fingerprint).apply()
    }

    override suspend fun revoke(extensionId: String) {
        preferences.edit().remove(extensionId).apply()
    }
}
