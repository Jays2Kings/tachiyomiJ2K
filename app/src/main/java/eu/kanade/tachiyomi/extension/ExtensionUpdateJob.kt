package eu.kanade.tachiyomi.extension

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.extension.api.ExtensionGithubApi
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
            createUpdateNotification(pendingUpdates.map { it.name })
        }

        Result.success()
    }

    private fun createUpdateNotification(names: List<String>) {
        val preferences: PreferencesHelper by injectLazy()
        preferences.extensionUpdatesCount().set(names.size)
        NotificationManagerCompat.from(context).apply {
            notify(
                Notifications.ID_UPDATES_TO_EXTS,
                context.notification(Notifications.CHANNEL_UPDATES_TO_EXTS) {
                    setContentTitle(
                        context.resources.getQuantityString(
                            R.plurals.extension_updates_available,
                            names
                                .size,
                            names.size
                        )
                    )
                    val extNames = names.joinToString(", ")
                    setContentText(extNames)
                    setStyle(NotificationCompat.BigTextStyle().bigText(extNames))
                    setSmallIcon(R.drawable.ic_extension_update_24dp)
                    color = ContextCompat.getColor(context, R.color.colorAccent)
                    setContentIntent(
                        NotificationReceiver.openExtensionsPendingActivity(
                            context
                        )
                    )
                    setAutoCancel(true)
                }
            )
        }
    }

    companion object {
        private const val TAG = "ExtensionUpdate"

        fun setupTask(forceAutoUpdateJob: Boolean? = null) {
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

                WorkManager.getInstance().enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.REPLACE, request)
            } else {
                WorkManager.getInstance().cancelAllWorkByTag(TAG)
            }
        }
    }
}
