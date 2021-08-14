package eu.kanade.tachiyomi.extension

import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.data.updater.AutoUpdaterJob
import eu.kanade.tachiyomi.extension.api.ExtensionGithubApi
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.util.system.connectivityManager
import eu.kanade.tachiyomi.util.system.notification
import kotlinx.coroutines.coroutineScope
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.TimeUnit

class ExtensionUpdateJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = coroutineScope {
        val pendingUpdates = try {
            ExtensionGithubApi().checkForUpdates(context)
        } catch (e: Exception) {
            return@coroutineScope Result.failure()
        }

        if (pendingUpdates.isNotEmpty()) {
            createUpdateNotification(pendingUpdates)
        }

        Result.success()
    }

    private fun createUpdateNotification(extensionsList: List<Extension.Available>) {
        val extensions = extensionsList.toMutableList()
        val preferences: PreferencesHelper by injectLazy()
        preferences.extensionUpdatesCount().set(extensions.size)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && preferences.autoUpdateExtensions() != AutoUpdaterJob.NEVER) {
            val cm = context.connectivityManager
            if (
                preferences.autoUpdateExtensions() == AutoUpdaterJob.ALWAYS ||
                !cm.isActiveNetworkMetered
            ) {
                val extensionManager = Injekt.get<ExtensionManager>()
                val extensionsInstalledByApp =
                    extensions.filter { extensionManager.isInstalledByApp(it) }
                if (extensionsInstalledByApp.isNotEmpty()) {
                    val intent =
                        ExtensionInstallService.jobIntent(
                            context,
                            extensionsInstalledByApp,
                            // Re run this job if not all the extensions can be auto updated
                            if (extensionsInstalledByApp.size == extensions.size) {
                                1
                            } else {
                                2
                            }
                        )
                    context.startForegroundService(intent)
                    if (extensionsInstalledByApp.size == extensions.size) {
                        return
                    } else {
                        extensions.removeAll(extensionsInstalledByApp)
                    }
                }
            } else {
                runJobAgain(context, NetworkType.UNMETERED)
            }
        }
        NotificationManagerCompat.from(context).apply {
            notify(
                Notifications.ID_UPDATES_TO_EXTS,
                context.notification(Notifications.CHANNEL_UPDATES_TO_EXTS) {
                    setContentTitle(
                        context.resources.getQuantityString(
                            R.plurals.extension_updates_available,
                            extensions.size,
                            extensions.size
                        )
                    )
                    val extNames = extensions.joinToString(", ") { it.name }
                    setContentText(extNames)
                    setStyle(NotificationCompat.BigTextStyle().bigText(extNames))
                    setSmallIcon(R.drawable.ic_extension_update_24dp)
                    color = ContextCompat.getColor(context, R.color.secondaryTachiyomi)
                    setContentIntent(
                        NotificationReceiver.openExtensionsPendingActivity(
                            context
                        )
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        extensions.size == extensionsList.size
                    ) {
                        val intent = ExtensionInstallService.jobIntent(context, extensions)
                        val pendingIntent =
                            PendingIntent.getForegroundService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
                        addAction(
                            R.drawable.ic_file_download_24dp,
                            context.getString(R.string.update_all),
                            pendingIntent
                        )
                    }
                    setAutoCancel(true)
                }
            )
        }
    }

    companion object {
        private const val TAG = "ExtensionUpdate"
        private const val AUTO_TAG = "AutoExtensionUpdate"

        fun runJobAgain(context: Context, networkType: NetworkType) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .build()
            val request = OneTimeWorkRequestBuilder<ExtensionUpdateJob>()
                .setConstraints(constraints)
                .addTag(AUTO_TAG)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(AUTO_TAG, ExistingWorkPolicy.REPLACE, request)
        }

        fun setupTask(context: Context, forceAutoUpdateJob: Boolean? = null) {
            val preferences = Injekt.get<PreferencesHelper>()
            val autoUpdateJob = forceAutoUpdateJob ?: preferences.automaticExtUpdates().getOrDefault()
            if (autoUpdateJob) {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val request = PeriodicWorkRequestBuilder<ExtensionUpdateJob>(
                    12,
                    TimeUnit.HOURS,
                    1,
                    TimeUnit.HOURS
                )
                    .addTag(TAG)
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.REPLACE, request)
            } else {
                WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
            }
        }
    }
}
