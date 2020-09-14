package eu.kanade.tachiyomi.data.updater

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.notificationManager
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.TimeUnit

class UpdaterJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = coroutineScope {
        val result = try {
            UpdateChecker.getUpdateChecker().checkForUpdate()
        } catch (e: Exception) {
            Result.failure()
        }
        if (result is UpdateResult.NewUpdate<*>) {
            val url = result.release.downloadLink

            val intent = Intent(context, UpdaterService::class.java).apply {
                putExtra(UpdaterService.EXTRA_DOWNLOAD_URL, url)
            }

            NotificationCompat.Builder(context, Notifications.CHANNEL_COMMON).update {
                setContentTitle(context.getString(R.string.app_name))
                setContentText(context.getString(R.string.update_available))
                setSmallIcon(android.R.drawable.stat_sys_download_done)
                color = ContextCompat.getColor(context, R.color.colorAccent)
                // Download action
                addAction(
                    android.R.drawable.stat_sys_download_done,
                    context.getString(R.string.download),
                    PendingIntent.getService(
                        context,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
            }
        }
        Result.success()
    }

    fun NotificationCompat.Builder.update(block: NotificationCompat.Builder.() -> Unit) {
        block()
        context.notificationManager.notify(Notifications.ID_UPDATER, build())
    }

    companion object {
        private const val TAG = "UpdateChecker"

        fun setupTask() {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<UpdaterJob>(
                1,
                TimeUnit.DAYS,
                1,
                TimeUnit.HOURS
            )
                .addTag(TAG)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance().enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.REPLACE, request)
        }

        fun cancelTask() {
            WorkManager.getInstance().cancelAllWorkByTag(TAG)
        }
    }
}
