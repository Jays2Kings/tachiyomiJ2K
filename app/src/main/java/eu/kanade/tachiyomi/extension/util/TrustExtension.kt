package eu.kanade.tachiyomi.extension.util

import android.content.pm.PackageInfo
import androidx.core.content.pm.PackageInfoCompat
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.extension.repository.ExtensionRepoRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TrustExtension(
    private val preferences: PreferencesHelper = Injekt.get(),
    private val extensionRepoRepository: ExtensionRepoRepository = Injekt.get(),
) {
    suspend fun isTrusted(
        pkgInfo: PackageInfo,
        fingerprints: List<String>,
    ): Boolean {
        val repos = extensionRepoRepository.getAll()
        val trustedRepoFingerprints = repos.map { it.signingKeyFingerprint }.toSet()

        val isRepoTrusted = fingerprints.any { it in trustedRepoFingerprints }

        val key = "${pkgInfo.packageName}:${PackageInfoCompat.getLongVersionCode(pkgInfo)}:${fingerprints.last()}"
        val isIndividuallyTrusted = key in preferences.trustedExtensions().get()

        return isRepoTrusted || isIndividuallyTrusted
    }

    suspend fun trust(
        pkgName: String,
        versionCode: Long,
        signatureHash: String,
    ) {
        preferences.trustedExtensions().let { exts ->
            // Remove previously trusted versions
            val removed = exts.get().filterNot { it.startsWith("$pkgName:") }.toMutableSet()

            removed += "$pkgName:$versionCode:$signatureHash"
            exts.set(removed)
        }
    }

    fun revokeAll() {
        preferences.trustedExtensions().delete()
    }
}
