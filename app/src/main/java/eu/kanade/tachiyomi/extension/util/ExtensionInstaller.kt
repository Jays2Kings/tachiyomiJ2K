package eu.kanade.tachiyomi.extension.util

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.net.toUri
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.ui.extension.ExtensionIntallInfo
import eu.kanade.tachiyomi.util.storage.getUriCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flattenMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

/**
 * The installer which installs, updates and uninstalls the extensions.
 *
 * @param context The application context.
 */
internal class ExtensionInstaller(private val context: Context) {

    /**
     * The system's download manager
     */
    private val downloadManager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    /**
     * The broadcast receiver which listens to download completion events.
     */
    private val downloadReceiver = DownloadCompletionReceiver()

    /**
     * The currently requested downloads, with the package name (unique id) as key, and the id
     * returned by the download manager.
     */
    val activeDownloads = hashMapOf<String, Long>()

    /**
     * StateFlow used to notify the installation step of every download.
     */
    val downloadsStateFlow = MutableStateFlow("" to ExtensionIntallInfo(InstallStep.Pending, null))

    /** Map of download id to installer session id */
    val downloadInstallerMap = hashMapOf<String, Int>()

    /**
     * Adds the given extension to the downloads queue and returns a flow containing its
     * step in the installation process.
     *
     * @param url The url of the apk.
     * @param extension The extension to install.
     */
    suspend fun downloadAndInstall(url: String, extension: ExtensionManager.ExtensionInfo, scope: CoroutineScope): Flow<ExtensionIntallInfo> {
        val pkgName = extension.pkgName

        val oldDownload = activeDownloads[pkgName]
        if (oldDownload != null) {
            deleteDownload(pkgName)
        }

        // Register the receiver after removing (and unregistering) the previous download
        downloadReceiver.register()

        val downloadUri = url.toUri()
        val request = DownloadManager.Request(downloadUri)
            .setTitle(extension.name)
            .setMimeType(APK_MIME)
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                downloadUri.lastPathSegment
            )
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        val id = downloadManager.enqueue(request)
        activeDownloads[pkgName] = id

        scope.launch {
            flowOf(
                pollStatus(id),
                pollInstallStatus(pkgName)
            ).flattenMerge()
                .transformWhile {
                    emit(it)
                    !it.first.isCompleted()
                }
                .flowOn(Dispatchers.IO)
                .catch { e ->
                    Timber.e(e)
                    emit(InstallStep.Error to null)
                }
                .onCompletion {
                    deleteDownload(pkgName)
                }
                .collect {
                    downloadsStateFlow.emit(extension.pkgName to it)
                }
        }

        return downloadsStateFlow.filter { it.first == extension.pkgName }.map { it.second }
            .flowOn(Dispatchers.IO)
            .transformWhile {
                emit(it)
                !it.first.isCompleted()
            }
            .onCompletion {
                deleteDownload(pkgName)
            }
    }

    /**
     * Returns a flow that polls the given download id for its status every second, as the
     * manager doesn't have any notification system. It'll stop once the download finishes.
     *
     * @param id The id of the download to poll.
     */
    @SuppressLint("Range")
    private fun pollStatus(id: Long): Flow<ExtensionIntallInfo> {
        val query = DownloadManager.Query().setFilterById(id)

        return flow {
            while (true) {
                val newDownloadState = try {
                    downloadManager.query(query)?.use { cursor ->
                        cursor.moveToFirst()
                        cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
                    }
                } catch (_: Exception) {
                }
                if (newDownloadState != null) {
                    emit(newDownloadState)
                }
                delay(1000)
            }
        }
            .distinctUntilChanged()
            .transformWhile {
                emit(it)
                !(it == DownloadManager.STATUS_SUCCESSFUL || it == DownloadManager.STATUS_FAILED)
            }
            .flatMapConcat { downloadState ->
                val step = when (downloadState) {
                    DownloadManager.STATUS_PENDING -> InstallStep.Pending
                    DownloadManager.STATUS_RUNNING -> InstallStep.Downloading
                    else -> return@flatMapConcat emptyFlow()
                }
                flowOf(ExtensionIntallInfo(step, null))
            }
    }

    /**
     * Returns a flow that polls the given installer session for its status every half second, as the
     * manager doesn't have any notification system. This will only stop once
     *
     * @param pkgName The pkgName of the download mapped to the session to poll.
     */
    private fun pollInstallStatus(pkgName: String): Flow<ExtensionIntallInfo> {
        return flow {
            while (true) {
                val sessionId = downloadInstallerMap[pkgName]
                if (sessionId != null) {
                    val session =
                        context.packageManager.packageInstaller.getSessionInfo(sessionId)
                    emit(InstallStep.Installing to session)
                }
                delay(500)
            }
        }
            .takeWhile { info ->
                val sessionId = downloadInstallerMap[pkgName]
                if (sessionId != null) {
                    info.second != null
                } else {
                    true
                }
            }
            .catch {
                Timber.e(it)
            }
            .onCompletion {
                emit(InstallStep.Done to null)
            }
    }

    /**
     * Starts an intent to install the extension at the given uri.
     *
     * @param uri The uri of the extension to install.
     */
    fun installApk(downloadId: Long, uri: Uri) {
        val pkgName = activeDownloads.entries.find { it.value == downloadId }?.key
        val useActivity =
            pkgName?.let { !ExtensionLoader.isExtensionInstalledByApp(context, pkgName) } ?: true ||
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S
        val intent =
            if (useActivity) {
                Intent(context, ExtensionInstallActivity::class.java)
            } else {
                Intent(context, ExtensionInstallBroadcast::class.java)
            }
                .setDataAndType(uri, APK_MIME)
                .putExtra(EXTRA_DOWNLOAD_ID, downloadId)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (useActivity) {
            context.startActivity(intent)
        } else {
            context.sendBroadcast(intent)
        }
    }

    /**
     * Starts an intent to uninstall the extension by the given package name.
     *
     * @param pkgName The package name of the extension to uninstall
     */
    fun uninstallApk(pkgName: String) {
        val packageUri = "package:$pkgName".toUri()
        val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE, packageUri)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(intent)
    }

    /**
     * Sets the result of the installation of an extension.
     *
     * @param downloadId The id of the download.
     */
    fun setInstalling(pkgName: String, sessionId: Int) {
        downloadsStateFlow.tryEmit(pkgName to ExtensionIntallInfo(InstallStep.Installing, null))
        downloadInstallerMap[pkgName] = sessionId
    }

    suspend fun setPending(pkgName: String) {
        downloadsStateFlow.emit(pkgName to ExtensionIntallInfo(InstallStep.Pending, null))
    }

    fun cancelInstallation(sessionId: Int) {
        val downloadId = downloadInstallerMap.entries.find { it.value == sessionId }?.key ?: return
        setInstallationResult(downloadId, false)
        try {
            context.packageManager.packageInstaller.abandonSession(sessionId)
        } catch (_: Exception) { }
    }

    fun cleanUpInstallation(pkgName: String) {
        val sessionId = downloadInstallerMap[pkgName] ?: return
        downloadInstallerMap.remove(pkgName)
        try {
            context.packageManager.packageInstaller.abandonSession(sessionId)
        } catch (_: Exception) { }
    }

    /**
     * Sets the result of the installation of an extension.
     *
     * @param downloadId The id of the download.
     * @param result Whether the extension was installed or not.
     */
    fun setInstallationResult(pkgName: String, result: Boolean) {
        val step = if (result) InstallStep.Installed else InstallStep.Error
        downloadInstallerMap.remove(pkgName)
        downloadsStateFlow.tryEmit(pkgName to ExtensionIntallInfo(step, null))
    }

    fun softDeleteDownload(downloadId: Long) {
        downloadManager.remove(downloadId)
    }

    /**
     * Deletes the download for the given package name.
     *
     * @param pkgName The package name of the download to delete.
     */
    private fun deleteDownload(pkgName: String) {
        val downloadId = activeDownloads.remove(pkgName)
        if (downloadId != null) {
            downloadManager.remove(downloadId)
        }
        if (activeDownloads.isEmpty()) {
            downloadReceiver.unregister()
        }
    }

    /**
     * Receiver that listens to download status events.
     */
    private inner class DownloadCompletionReceiver : BroadcastReceiver() {

        /**
         * Whether this receiver is currently registered.
         */
        private var isRegistered = false

        /**
         * Registers this receiver if it's not already.
         */
        fun register() {
            if (isRegistered) return
            isRegistered = true

            val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            context.registerReceiver(this, filter)
        }

        /**
         * Unregisters this receiver if it's not already.
         */
        fun unregister() {
            if (!isRegistered) return
            isRegistered = false

            context.unregisterReceiver(this)
        }

        /**
         * Called when a download event is received. It looks for the download in the current active
         * downloads and notifies its installation step.
         */
        @SuppressLint("Range")
        override fun onReceive(context: Context, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0) ?: return

            // Avoid events for downloads we didn't request
            if (id !in activeDownloads.values) return

            val uri = downloadManager.getUriForDownloadedFile(id)

            val pkgName = activeDownloads.entries.find { id == it.value }?.key
            // Set next installation step
            if (uri != null && pkgName != null) {
                downloadsStateFlow.tryEmit(pkgName to ExtensionIntallInfo(InstallStep.Loading, null))
            } else if (pkgName != null) {
                Timber.e("Couldn't locate downloaded APK")
                downloadsStateFlow.tryEmit(pkgName to ExtensionIntallInfo(InstallStep.Error, null))
                return
            }

            val query = DownloadManager.Query().setFilterById(id)
            downloadManager.query(query).use { cursor ->
                if (cursor.moveToFirst()) {
                    val localUri = cursor.getString(
                        cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                    ).removePrefix(FILE_SCHEME)

                    installApk(id, File(localUri).getUriCompat(context))
                }
            }
        }
    }

    companion object {
        const val APK_MIME = "application/vnd.android.package-archive"
        const val EXTRA_DOWNLOAD_ID = "ExtensionInstaller.extra.DOWNLOAD_ID"
        const val FILE_SCHEME = "file://"
    }
}
