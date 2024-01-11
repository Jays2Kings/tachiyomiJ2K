package eu.kanade.tachiyomi.extension.api

import android.content.Context
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.LoadResult
import eu.kanade.tachiyomi.extension.util.ExtensionLoader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.system.withIOContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

internal class ExtensionGithubApi {

    private val json: Json by injectLazy()
    private val networkService: NetworkHelper by injectLazy()

    private var requiresFallbackSource = false

    suspend fun findExtensions(): List<Extension.Available> {
        return withIOContext {
            val githubResponse = if (requiresFallbackSource) {
                null
            } else {
                try {
                    networkService.client
                        .newCall(GET("${REPO_URL_PREFIX}index.min.json"))
                        .await()
                } catch (e: Throwable) {
                    Timber.e(e, "Failed to get extensions from GitHub")
                    requiresFallbackSource = true
                    null
                }
            }

            val response = githubResponse ?: run {
                networkService.client
                    .newCall(GET("${FALLBACK_REPO_URL_PREFIX}index.min.json"))
                    .await()
            }

            val extensions = with(json) {
                response
                    .parseAs<List<ExtensionJsonObject>>()
                    .toExtensions()
            }

            // Sanity check - a small number of extensions probably means something broke
            // with the repo generator
            if (extensions.size < 100) {
                throw Exception()
            }

            extensions
        }
    }

    suspend fun checkForUpdates(context: Context, prefetchedExtensions: List<Extension.Available>? = null): List<Extension.Available> {
        return withIOContext {
            val extensions = prefetchedExtensions ?: findExtensions()

            val extensionManager: ExtensionManager = Injekt.get()
            val installedExtensions = extensionManager.installedExtensionsFlow.value.ifEmpty {
                ExtensionLoader.loadExtensionAsync(context)
                    .filterIsInstance<LoadResult.Success>()
                    .map { it.extension }
            }

            val extensionsWithUpdate = mutableListOf<Extension.Available>()
            for (installedExt in installedExtensions) {
                val pkgName = installedExt.pkgName
                val availableExt = extensions.find { it.pkgName == pkgName } ?: continue
                val hasUpdatedVer = availableExt.versionCode > installedExt.versionCode
                val hasUpdatedLib = availableExt.libVersion > installedExt.libVersion
                val hasUpdate = installedExt.isUnofficial.not() && (hasUpdatedVer || hasUpdatedLib)
                if (hasUpdate) {
                    extensionsWithUpdate.add(availableExt)
                }
            }

            extensionsWithUpdate
        }
    }

    private fun List<ExtensionJsonObject>.toExtensions(): List<Extension.Available> {
        return this
            .filter {
                val libVersion = it.extractLibVersion()
                libVersion >= ExtensionLoader.LIB_VERSION_MIN && libVersion <= ExtensionLoader.LIB_VERSION_MAX
            }
            .map {
                Extension.Available(
                    name = it.name.substringAfter("Tachiyomi: "),
                    pkgName = it.pkg,
                    versionName = it.version,
                    versionCode = it.code,
                    libVersion = it.extractLibVersion(),
                    lang = it.lang,
                    isNsfw = it.nsfw == 1,
                    hasReadme = it.hasReadme == 1,
                    hasChangelog = it.hasChangelog == 1,
                    sources = it.sources ?: emptyList(),
                    apkName = it.apk,
                    iconUrl = "${getUrlPrefix()}icon/${it.pkg}.png",
                )
            }
    }

    fun getApkUrl(extension: ExtensionManager.ExtensionInfo): String {
        return "${getUrlPrefix()}apk/${extension.apkName}"
    }

    private fun getUrlPrefix(): String {
        return if (requiresFallbackSource) {
            FALLBACK_REPO_URL_PREFIX
        } else {
            REPO_URL_PREFIX
        }
    }

    private fun ExtensionJsonObject.extractLibVersion(): Double {
        return version.substringBeforeLast('.').toDouble()
    }
}

private const val REPO_URL_PREFIX = "https://raw.githubusercontent.com/keiyoushi/extensions/repo/"
private const val FALLBACK_REPO_URL_PREFIX = "https://gcore.jsdelivr.net/gh/keiyoushi/extensions@repo/"

@Serializable
private data class ExtensionJsonObject(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String,
    val code: Long,
    val version: String,
    val nsfw: Int,
    val hasReadme: Int = 0,
    val hasChangelog: Int = 0,
    val sources: List<Extension.AvailableSource>?,
)
