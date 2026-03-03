package eu.kanade.tachiyomi.extension.backend

import eu.kanade.tachiyomi.extension.contract.ExtensionTrustStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

class DesktopExtensionTrustStore(
    private val trustStorePath: Path,
    private val json: Json = Json { prettyPrint = true },
) : ExtensionTrustStore {

    override suspend fun isTrusted(extensionId: String, fingerprint: String): Boolean {
        return readStore()[extensionId] == fingerprint
    }

    override suspend fun trust(extensionId: String, fingerprint: String) {
        val state = readStore().toMutableMap()
        state[extensionId] = fingerprint
        writeStore(state)
    }

    override suspend fun revoke(extensionId: String) {
        val state = readStore().toMutableMap()
        state.remove(extensionId)
        writeStore(state)
    }

    private fun readStore(): Map<String, String> {
        if (!Files.exists(trustStorePath)) {
            return emptyMap()
        }

        return trustStorePath.inputStream().use { json.decodeFromStream(it) }
    }

    private fun writeStore(store: Map<String, String>) {
        trustStorePath.parent?.let { Files.createDirectories(it) }
        trustStorePath.outputStream().use { json.encodeToStream(store, it) }
    }
}
