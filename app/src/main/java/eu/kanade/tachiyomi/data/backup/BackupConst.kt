package eu.kanade.tachiyomi.data.backup
import eu.kanade.tachiyomi.BuildConfig.APPLICATION_ID as ID

object BackupConst {

    private const val NAME = "BackupRestoreServices"
    const val EXTRA_FLAGS = "$ID.$NAME.EXTRA_FLAGS"
    const val INTENT_FILTER = "SettingsBackupFragment"
    const val ACTION_BACKUP_COMPLETED_DIALOG = "$ID.$INTENT_FILTER.ACTION_BACKUP_COMPLETED_DIALOG"
    const val ACTION_ERROR_BACKUP_DIALOG = "$ID.$INTENT_FILTER.ACTION_ERROR_BACKUP_DIALOG"
    const val ACTION = "$ID.$INTENT_FILTER.ACTION"
    const val EXTRA_ERROR_MESSAGE = "$ID.$INTENT_FILTER.EXTRA_ERROR_MESSAGE"
    const val EXTRA_URI = "$ID.$INTENT_FILTER.EXTRA_URI"
}
