package eu.kanade.tachiyomi.extension.backend

import eu.kanade.tachiyomi.extension.contract.ExtensionRepository
import eu.kanade.tachiyomi.extension.model.ExtensionDistribution
import eu.kanade.tachiyomi.extension.model.ExtensionPackage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.fileName
import kotlin.io.path.isDirectory
import kotlin.io.path.name

class DesktopExtensionRepository(
    private val pluginsDir: Path,
    private val availableProvider: suspend () -> List<ExtensionPackage>,
) : ExtensionRepository {

    private val installed = MutableStateFlow(emptyList<ExtensionPackage>())
    private val available = MutableStateFlow(emptyList<ExtensionPackage>())

    override suspend fun refresh() {
        if (!Files.exists(pluginsDir)) {
            Files.createDirectories(pluginsDir)
        }

        installed.value = Files.list(pluginsDir).use { paths ->
            paths.mapNotNull { path ->
                when {
                    path.isDirectory() -> ExtensionPackage(
                        id = path.name,
                        name = path.fileName.toString(),
                        versionName = "local",
                        distribution = ExtensionDistribution.DesktopPluginFolder(path.fileName.toString()),
                    )

                    path.extension.equals("jar", ignoreCase = true) -> ExtensionPackage(
                        id = path.name,
                        name = path.fileName.toString(),
                        versionName = "local",
                        distribution = ExtensionDistribution.DesktopJar(path.fileName.toString()),
                    )

                    else -> null
                }
            }.toList()
        }

        available.value = availableProvider()
    }

    override fun observeInstalled(): Flow<List<ExtensionPackage>> = installed.asStateFlow()

    override fun observeAvailable(): Flow<List<ExtensionPackage>> = available.asStateFlow()

    override suspend fun getById(extensionId: String): ExtensionPackage? {
        return installed.value.find { it.id == extensionId } ?: available.value.find { it.id == extensionId }
    }
}
