package eu.kanade.tachiyomi.extension.backend

import eu.kanade.tachiyomi.extension.contract.ExtensionPackageInstaller
import eu.kanade.tachiyomi.extension.model.ExtensionDistribution
import eu.kanade.tachiyomi.extension.model.ExtensionInstallProgress
import eu.kanade.tachiyomi.extension.model.ExtensionPackage
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.extension.model.LoadedExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.extension

class DesktopExtensionPackageInstaller(
    private val distributionRoot: Path,
    private val pluginDirectory: Path,
    private val sandboxMode: SandboxMode = SandboxMode.ClassLoaderIsolated,
) : ExtensionPackageInstaller {

    private val classLoaders = mutableMapOf<String, URLClassLoader>()

    override fun install(extensionPackage: ExtensionPackage): Flow<ExtensionInstallProgress> = flow {
        emit(ExtensionInstallProgress(extensionPackage.id, InstallStep.Pending))
        emit(ExtensionInstallProgress(extensionPackage.id, InstallStep.Installing))

        withContext(Dispatchers.IO) {
            Files.createDirectories(pluginDirectory)
            when (val distribution = extensionPackage.distribution) {
                is ExtensionDistribution.DesktopJar -> installJar(distribution.fileName)
                is ExtensionDistribution.DesktopZip -> installZip(distribution.fileName)
                is ExtensionDistribution.DesktopPluginFolder -> installFolder(distribution.folderName)
                is ExtensionDistribution.AndroidApk -> error("Android distribution is not supported in desktop")
            }
        }

        emit(ExtensionInstallProgress(extensionPackage.id, InstallStep.Installed))
        emit(ExtensionInstallProgress(extensionPackage.id, InstallStep.Done))
    }

    override suspend fun uninstall(extensionId: String) {
        withContext(Dispatchers.IO) {
            classLoaders.remove(extensionId)?.close()

            Files.list(pluginDirectory).use { paths ->
                paths.firstOrNull { it.fileName.toString().startsWith(extensionId) }?.let { path ->
                    if (Files.isDirectory(path)) {
                        Files.walk(path).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
                    } else {
                        Files.deleteIfExists(path)
                    }
                }
            }
        }
    }

    override suspend fun load(extensionId: String): LoadedExtension? = withContext(Dispatchers.IO) {
        if (sandboxMode == SandboxMode.ProcessIsolated) {
            return@withContext LoadedExtension(extensionId, "process://${pluginDirectory.absolutePathString()}/$extensionId")
        }

        val artifact = Files.list(pluginDirectory).use { paths ->
            paths.firstOrNull {
                it.fileName.toString().startsWith(extensionId) &&
                    (it.extension.equals("jar", ignoreCase = true) || Files.isDirectory(it))
            }
        } ?: return@withContext null

        val classLoader = URLClassLoader(arrayOf(artifact.toUri().toURL()), null)
        classLoaders[extensionId] = classLoader
        LoadedExtension(extensionId, "classloader://${artifact.fileName}")
    }

    private fun installJar(fileName: String) {
        val source = distributionRoot.resolve(fileName)
        val target = pluginDirectory.resolve(fileName)
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun installZip(fileName: String) {
        val zipPath = distributionRoot.resolve(fileName)
        val outputDir = pluginDirectory.resolve(fileName.removeSuffix(".zip"))
        Files.createDirectories(outputDir)

        ZipFile(zipPath.toFile()).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val targetPath = outputDir.resolve(entry.name)
                if (entry.isDirectory) {
                    Files.createDirectories(targetPath)
                } else {
                    Files.createDirectories(targetPath.parent)
                    zip.getInputStream(entry).use { input ->
                        Files.copy(input, targetPath, StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            }
        }
    }

    private fun installFolder(folderName: String) {
        val source = distributionRoot.resolve(folderName)
        val target = pluginDirectory.resolve(folderName)

        if (!source.exists()) {
            error("Plugin folder not found: $folderName")
        }

        Files.walk(source).forEach { from ->
            val relative = source.relativize(from)
            val to = target.resolve(relative)
            if (Files.isDirectory(from)) {
                Files.createDirectories(to)
            } else {
                Files.createDirectories(to.parent)
                Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}

enum class SandboxMode {
    ClassLoaderIsolated,
    ProcessIsolated,
}
