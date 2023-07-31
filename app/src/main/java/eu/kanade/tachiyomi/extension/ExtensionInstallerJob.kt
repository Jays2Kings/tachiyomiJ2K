package eu.kanade.tachiyomi.extension

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.util.system.jobIsRunning
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.localeContext
import eu.kanade.tachiyomi.util.system.notificationManager
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.tryToSetForeground
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.lang.ref.WeakReference
import kotlin.math.max

class ExtensionInstallerJob(val context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    private val notifier = ExtensionInstallNotifier(context.localeContext)

    private val preferences: PreferencesHelper = Injekt.get()

    private var activeInstalls = mutableListOf<String>()

    val extensionManager: ExtensionManager = Injekt.get()

    private var emitScope = CoroutineScope(Job() + Dispatchers.Default)

    private var job: Job? = null

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = notifier.progressNotificationBuilder.build()
        val id = Notifications.ID_EXTENSION_PROGRESS
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id, notification)
        }
    }

    override suspend fun doWork(): Result {
        tryToSetForeground()

        instance = WeakReference(this)

        context.notificationManager.cancel(Notifications.ID_UPDATES_TO_EXTS)
        val showUpdated = inputData.getInt(KEY_SHOW_UPDATED, -1)
        val showUpdatedNotification = showUpdated > -1
        val reRunUpdateCheck = showUpdated > 0

        if (!showUpdatedNotification && !preferences.hasPromptedBeforeUpdateAll().get()) {
            context.toast(R.string.some_extensions_may_prompt)
            preferences.hasPromptedBeforeUpdateAll().set(true)
        }

        val json = inputData.getString(KEY_EXTENSION) ?: return Result.failure()

        val infos = try {
            Json.decodeFromString<Array<ExtensionManager.ExtensionInfo>>(json)
        } catch (e: Exception) {
            Timber.e(e, "Cannot decode string")
            null
        } ?: return Result.failure()
        val list = infos.filter {
            val installedExt = extensionManager.installedExtensionsFlow.value.find { installed ->
                installed.pkgName == it.pkgName
            } ?: return@filter false
            installedExt.versionCode < it.versionCode || installedExt.libVersion < it.libVersion
        }

        activeInstalls = list.map { it.pkgName }.toMutableList()
        emitScope.launch { list.forEach { extensionManager.setPending(it.pkgName) } }
        var installed = 0
        val installedExtensions = mutableListOf<ExtensionManager.ExtensionInfo>()
        val requestSemaphore = Semaphore(3)
        coroutineScope {
            job = launchIO {
                list.map { extension ->
                    async {
                        requestSemaphore.withPermit {
                            extensionManager.installExtension(extension, this)
                                .collect {
                                    if (it.first.isCompleted()) {
                                        activeInstalls.remove(extension.pkgName)
                                        installedExtensions.add(extension)
                                        installed++
                                        val prefCount = preferences.extensionUpdatesCount().get()
                                        preferences.extensionUpdatesCount()
                                            .set(max(prefCount - 1, 0))
                                    }
                                    notifier.showProgressNotification(installed, list.size)
                                    if (activeInstalls.isEmpty() || isStopped) {
                                        cancel()
                                    }
                                }
                        }
                    }
                }.awaitAll()
            }
        }

        if (showUpdatedNotification && installedExtensions.size > 0) {
            notifier.showUpdatedNotification(installedExtensions, preferences.hideNotificationContent())
        }
        if (reRunUpdateCheck || installedExtensions.size != list.size) {
            ExtensionUpdateJob.runJobAgain(context, NetworkType.CONNECTED, false)
        }

        activeInstalls.forEach { extensionManager.cleanUpInstallation(it) }
        activeInstalls.clear()
        val hasChain = withContext(Dispatchers.IO) {
            WorkManager.getInstance(context).getWorkInfosByTag(TAG).get().any {
                it.state == WorkInfo.State.BLOCKED
            }
        }
        if (!hasChain) {
            extensionManager.emitToInstaller("Finished", (InstallStep.Installed to null))
        }
        if (instance?.get() == this) {
            instance = null
        }
        if (!hasChain) {
            context.notificationManager.cancel(Notifications.ID_EXTENSION_PROGRESS)
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "ExtensionInstaller"

        /**
         * Key that defines what should be updated.
         */
        const val KEY_EXTENSION = "extension"
        const val KEY_SHOW_UPDATED = "show_updated"

        private var instance: WeakReference<ExtensionInstallerJob>? = null

        fun start(context: Context, extensions: List<Extension.Available>, showUpdatedExtension: Int = -1) {
            startJob(context, extensions.map(ExtensionManager::ExtensionInfo), showUpdatedExtension)
        }

        fun startJob(context: Context, info: List<ExtensionManager.ExtensionInfo>, showUpdatedExtension: Int = -1) {
            // chunked to satisfy input limits
            val requests = info.chunked(32).map {
                OneTimeWorkRequestBuilder<ExtensionInstallerJob>()
                    .addTag(TAG)
                    .setInputData(
                        workDataOf(
                            KEY_EXTENSION to Json.encodeToString(it.toTypedArray()),
                            KEY_SHOW_UPDATED to showUpdatedExtension,
                        ),
                    )
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()
            }
            var workContinuation = WorkManager.getInstance(context)
                .beginUniqueWork(TAG, ExistingWorkPolicy.REPLACE, requests.first())
            for (i in 1 until requests.size) {
                workContinuation = workContinuation.then(requests[i])
            }
            workContinuation.enqueue()
        }

        fun activeInstalls(): List<String>? = instance?.get()?.activeInstalls
        fun removeActiveInstall(pkgName: String) = instance?.get()?.activeInstalls?.remove(pkgName)

        fun stop(context: Context) {
            instance?.get()?.job?.cancel()
            WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
        }

        fun isRunning(context: Context) = WorkManager.getInstance(context).jobIsRunning(TAG)
    }
}
