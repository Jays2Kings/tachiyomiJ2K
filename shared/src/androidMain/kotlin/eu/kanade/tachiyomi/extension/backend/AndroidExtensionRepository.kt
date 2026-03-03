package eu.kanade.tachiyomi.extension.backend

import android.content.Context
import eu.kanade.tachiyomi.extension.contract.ExtensionRepository
import eu.kanade.tachiyomi.extension.model.ExtensionDistribution
import eu.kanade.tachiyomi.extension.model.ExtensionPackage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidExtensionRepository(
    private val context: Context,
    private val availableProvider: suspend () -> List<ExtensionPackage>,
    private val packagePrefix: String = "eu.kanade.tachiyomi.extension",
) : ExtensionRepository {

    private val installed = MutableStateFlow(emptyList<ExtensionPackage>())
    private val available = MutableStateFlow(emptyList<ExtensionPackage>())

    override suspend fun refresh() {
        val packageManager = context.packageManager
        val installedPackages = packageManager.getInstalledPackages(0)
            .filter { it.packageName.startsWith(packagePrefix) }
            .map {
                ExtensionPackage(
                    id = it.packageName,
                    name = packageManager.getApplicationLabel(it.applicationInfo).toString(),
                    versionName = it.versionName ?: "0",
                    distribution = ExtensionDistribution.AndroidApk(downloadUrl = ""),
                )
            }

        installed.value = installedPackages
        available.value = availableProvider()
    }

    override fun observeInstalled(): Flow<List<ExtensionPackage>> = installed.asStateFlow()

    override fun observeAvailable(): Flow<List<ExtensionPackage>> = available.asStateFlow()

    override suspend fun getById(extensionId: String): ExtensionPackage? {
        return installed.value.find { it.id == extensionId } ?: available.value.find { it.id == extensionId }
    }
}
