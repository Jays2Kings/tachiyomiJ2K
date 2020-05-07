package eu.kanade.tachiyomi.data.backup

import android.content.Context
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notificationManager
import uy.kohesive.injekt.injectLazy

internal class BackupNotifier(private val context: Context) {

    private val preferences: PreferencesHelper by injectLazy()

    private val progressNotificationBuilder = context.notificationBuilder(Notifications.CHANNEL_BACKUP_RESTORE) {
        setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
        setSmallIcon(R.drawable.ic_tachi)
        setAutoCancel(false)
        setOngoing(true)
    }

    private val completeNotificationBuilder = context.notificationBuilder(Notifications.CHANNEL_BACKUP_RESTORE) {
        setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
        setSmallIcon(R.drawable.ic_tachi)
        setAutoCancel(false)
    }

    private fun NotificationCompat.Builder.show(id: Int) {
        context.notificationManager.notify(id, build())
    }

    fun showBackupProgress(): NotificationCompat.Builder {
        val builder = with(progressNotificationBuilder) {
            setContentTitle(context.getString(R.string.creating_backup))

            setProgress(0, 0, true)
            setOnlyAlertOnce(true)
        }

        builder.show(Notifications.ID_BACKUP_PROGRESS)

        return builder
    }

    fun showBackupError(error: String?) {
        context.notificationManager.cancel(Notifications.ID_BACKUP_PROGRESS)

        with(completeNotificationBuilder) {
            setContentTitle(context.getString(R.string.backup_failed))
            setContentText(error)

            show(Notifications.ID_BACKUP_COMPLETE)
        }
    }

    fun showBackupComplete(unifile: UniFile) {
        context.notificationManager.cancel(Notifications.ID_BACKUP_PROGRESS)

        with(completeNotificationBuilder) {
            setContentTitle(context.getString(R.string.backup_created))

            if (unifile.filePath != null) {
                setContentText(unifile.filePath)
            }

            // Clear old actions if they exist
            if (mActions.isNotEmpty()) {
                mActions.clear()
            }

            addAction(
                R.drawable.ic_share_grey_24dp,
                context.getString(R.string.share),
                NotificationReceiver.shareBackupPendingBroadcast(context, unifile.uri, Notifications.ID_BACKUP_COMPLETE)
            )

            show(Notifications.ID_BACKUP_COMPLETE)
        }
    }
}
