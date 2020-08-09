package eu.kanade.tachiyomi.ui.setting

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.core.net.toUri
import androidx.preference.PreferenceScreen
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItemsMultiChoice
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.backup.BackupCreateService
import eu.kanade.tachiyomi.data.backup.BackupCreatorJob
import eu.kanade.tachiyomi.data.backup.BackupRestoreService
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.util.system.getFilePicker
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.requestPermissionsSafe
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys

class SettingsBackupController : SettingsController() {

    /**
     * Flags containing information of what to backup.
     */
    private var backupFlags = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requestPermissionsSafe(arrayOf(WRITE_EXTERNAL_STORAGE), 500)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) = with(screen) {
        titleRes = R.string.backup

        preference {
            titleRes = R.string.create_backup
            summaryRes = R.string.can_be_used_to_restore

            onClick {
                val ctrl = CreateBackupDialog()
                ctrl.targetController = this@SettingsBackupController
                ctrl.showDialog(router)
            }
        }
        preference {
            titleRes = R.string.restore_backup
            summaryRes = R.string.restore_from_backup_file

            onClick {
                val intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                intent.type = "application/*"
                val title = resources?.getString(R.string.select_backup_file)
                val chooser = Intent.createChooser(intent, title)
                startActivityForResult(chooser, CODE_BACKUP_RESTORE)
            }
        }
        preferenceCategory {
            titleRes = R.string.service

            intListPreference(activity) {
                key = Keys.backupInterval
                titleRes = R.string.backup_frequency
                entriesRes = arrayOf(R.string.manual, R.string.every_6_hours,
                        R.string.every_12_hours, R.string.daily,
                        R.string.every_2_days, R.string.weekly)
                entryValues = listOf(0, 6, 12, 24, 48, 168)
                defaultValue = 0

                onChange { newValue ->
                    // Always cancel the previous task, it seems that sometimes they are not updated
                    BackupCreatorJob.setupTask(0)

                    val interval = newValue as Int
                    if (interval > 0) {
                        BackupCreatorJob.setupTask(interval)
                    }
                    true
                }
            }
            val backupDir = preference {
                key = Keys.backupDirectory
                titleRes = R.string.backup_location

                onClick {
                    val currentDir = preferences.backupsDirectory().getOrDefault()
                    try {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                        startActivityForResult(intent, CODE_BACKUP_DIR)
                    } catch (e: ActivityNotFoundException) {
                        // Fall back to custom picker on error
                        startActivityForResult(preferences.context.getFilePicker(currentDir), CODE_BACKUP_DIR)
                    }
                }

                preferences.backupsDirectory().asObservable()
                        .subscribeUntilDestroy { path ->
                            val dir = UniFile.fromUri(context, path.toUri())
                            summary = dir.filePath + "/automatic"
                        }
            }
            val backupNumber = intListPreference(activity) {
                key = Keys.numberOfBackups
                titleRes = R.string.max_auto_backups
                entries = listOf("1", "2", "3", "4", "5")
                entryRange = 1..5
                defaultValue = 1
            }

            preferences.backupInterval().asObservable()
                    .subscribeUntilDestroy {
                        backupDir.isVisible = it > 0
                        backupNumber.isVisible = it > 0
                    }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            CODE_BACKUP_DIR -> if (data != null && resultCode == Activity.RESULT_OK) {
                val activity = activity ?: return
                // Get uri of backup folder.
                val uri = data.data

                // Get UriPermission so it's possible to write files
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                if (uri != null) {
                    activity.contentResolver.takePersistableUriPermission(uri, flags)
                }

                // Set backup Uri
                preferences.backupsDirectory().set(uri.toString())
            }
            CODE_BACKUP_CREATE -> if (data != null && resultCode == Activity.RESULT_OK) {
                val activity = activity ?: return

                val uri = data.data
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                if (uri != null) {
                    activity.contentResolver.takePersistableUriPermission(uri, flags)
                }

                val file = UniFile.fromUri(activity, uri)

                activity.toast(R.string.creating_backup)

                BackupCreateService.start(activity, file.uri, backupFlags)
            }
            CODE_BACKUP_RESTORE -> if (data != null && resultCode == Activity.RESULT_OK) {
                val uri = data.data
                if (uri != null)
                    RestoreBackupDialog(uri).showDialog(router)
            }
        }
    }

    fun createBackup(flags: Int) {
        backupFlags = flags

        // Setup custom file picker intent
        // Get dirs
        val currentDir = preferences.backupsDirectory().getOrDefault()

        try {
            // Use Android's built-in file creator
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/*")
                    .putExtra(Intent.EXTRA_TITLE, Backup.getDefaultFilename())

            startActivityForResult(intent, CODE_BACKUP_CREATE)
        } catch (e: ActivityNotFoundException) {
            // Handle errors where the android ROM doesn't support the built in picker
            startActivityForResult(preferences.context.getFilePicker(currentDir), CODE_BACKUP_CREATE)
        }
    }

    class CreateBackupDialog : DialogController() {
        override fun onCreateDialog(savedViewState: Bundle?): Dialog {
            val activity = activity!!
            val options = arrayOf(R.string.manga, R.string.categories, R.string.chapters,
                    R.string.tracking, R.string.history)
                    .map { activity.getString(it) }

            return MaterialDialog(activity)
                    .title(R.string.create_backup)
                    .message(R.string.what_should_backup)
                .listItemsMultiChoice(items = options, disabledIndices = intArrayOf(0),
                    initialSelection = intArrayOf(0, 1, 2, 3, 4)) { _, positions, _ ->
                        var flags = 0
                        for (i in 1 until positions.size) {
                            when (positions[i]) {
                                1 -> flags = flags or BackupCreateService.BACKUP_CATEGORY
                                2 -> flags = flags or BackupCreateService.BACKUP_CHAPTER
                                3 -> flags = flags or BackupCreateService.BACKUP_TRACK
                                4 -> flags = flags or BackupCreateService.BACKUP_HISTORY
                            }
                        }

                        (targetController as? SettingsBackupController)?.createBackup(flags)
                    }
                    .positiveButton(R.string.create)
                    .negativeButton(android.R.string.cancel)
        }
    }

    class RestoreBackupDialog(bundle: Bundle? = null) : DialogController(bundle) {
        constructor(uri: Uri) : this(Bundle().apply {
            putParcelable(KEY_URI, uri)
        })

        override fun onCreateDialog(savedViewState: Bundle?): Dialog {
            return MaterialDialog(activity!!)
                    .title(R.string.restore_backup)
                    .message(R.string.restore_message)
                    .positiveButton(R.string.restore) {
                        val context = applicationContext
                        if (context != null) {
                            activity?.toast(R.string.restoring_backup)
                            BackupRestoreService.start(context, args.getParcelable(KEY_URI)!!)
                        }
                    }
        }

        private companion object {
            const val KEY_URI = "RestoreBackupDialog.uri"
        }
    }

    private companion object {
        const val CODE_BACKUP_CREATE = 501
        const val CODE_BACKUP_RESTORE = 502
        const val CODE_BACKUP_DIR = 503
    }
}
