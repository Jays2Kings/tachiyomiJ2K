package eu.kanade.tachiyomi.extension

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.work.NetworkType
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.extension.ExtensionManager.ExtensionInfo
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.util.system.notificationManager
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.ArrayList
import java.util.concurrent.TimeUnit
import kotlin.math.max

class ExtensionInstallService(
    val extensionManager: ExtensionManager = Injekt.get(),
) : Service() {

    /**
     * Wake lock that will be held until the service is destroyed.
     */
    private lateinit var wakeLock: PowerManager.WakeLock

    private lateinit var notifier: ExtensionInstallNotifier

    private var job: Job? = null

    private var serviceScope = CoroutineScope(Job() + Dispatchers.Default)

    private val requestSemaphore = Semaphore(3)

    private val preferences: PreferencesHelper = Injekt.get()

    /**
     * This method needs to be implemented, but it's not used/needed.
     */
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    /**
     * Method called when the service receives an intent.
     *
     * @param intent the start intent from.
     * @param flags the flags of the command.
     * @param startId the start id of this command.
     * @return the start value of the command.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY
        val showUpdated = intent.getIntExtra(KEY_SHOW_UPDATED, 0)
        val showUpdatedNotification = showUpdated > 0
        val reRunUpdateCheck = showUpdated > 1

        if (!showUpdatedNotification && !preferences.hasPromptedBeforeUpdateAll().get()) {
            toast(R.string.some_extensions_may_prompt)
            preferences.hasPromptedBeforeUpdateAll().set(true)
        }

        instance = this

        val list = intent.getParcelableArrayListExtra<ExtensionInfo>(KEY_EXTENSION)?.filter {
            (
                extensionManager.installedExtensions.find { installed ->
                    installed.pkgName == it.pkgName
                }?.versionCode ?: 0
                ) < it.versionCode
        }
            ?: return START_NOT_STICKY
        var installed = 0
        val installedExtensions = mutableListOf<ExtensionInfo>()
        job = serviceScope.launch {
            val results = list.map { extension ->
                async {
                    requestSemaphore.withPermit {
                        extensionManager.installExtension(extension, serviceScope)
                            .collect {
                                if (it.first.isCompleted()) {
                                    installedExtensions.add(extension)
                                    installed++
                                    val prefCount =
                                        preferences.extensionUpdatesCount().getOrDefault()
                                    preferences.extensionUpdatesCount().set(max(prefCount - 1, 0))
                                }
                                notifier.showProgressNotification(installed, list.size)
                            }
                    }
                }
            }
            results.awaitAll()
        }

        job?.invokeOnCompletion {
            if (showUpdatedNotification && installedExtensions.size > 0) {
                notifier.showUpdatedNotification(installedExtensions, preferences.hideNotificationContent())
            }
            if (reRunUpdateCheck || installedExtensions.size != list.size) {
                ExtensionUpdateJob.runJobAgain(this, NetworkType.CONNECTED, false)
            }
            stopSelf(startId)
        }

        return START_NOT_STICKY
    }

    /**
     * Method called when the service is created. It injects dagger dependencies and acquire
     * the wake lock.
     */
    override fun onCreate() {
        super.onCreate()
        notificationManager.cancel(Notifications.ID_UPDATES_TO_EXTS)
        notifier = ExtensionInstallNotifier(this)
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ExtensionInstallService:WakeLock"
        )
        wakeLock.acquire(TimeUnit.MINUTES.toMillis(30))
        startForeground(Notifications.ID_EXTENSION_PROGRESS, notifier.progressNotificationBuilder.build())
    }

    /**
     * Method called when the service is destroyed. It cancels jobs and releases the wake lock.
     */
    override fun onDestroy() {
        job?.cancel()
        serviceScope.cancel()
        if (instance == this) {
            instance = null
        }
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
        super.onDestroy()
    }

    companion object {

        private var instance: ExtensionInstallService? = null

        /**
         * Stops the service.
         *
         * @param context the application context.
         */
        fun stop(context: Context) {
            instance?.serviceScope?.cancel()
            context.stopService(Intent(context, ExtensionUpdateJob::class.java))
        }

        /**
         * Key that defines what should be updated.
         */
        private const val KEY_EXTENSION = "extension"
        private const val KEY_SHOW_UPDATED = "show_updated"

        fun jobIntent(context: Context, extensions: List<Extension.Available>, showUpdatedExtension: Int = 0): Intent {
            return Intent(context, ExtensionInstallService::class.java).apply {
                val info = extensions.map(::ExtensionInfo)
                putParcelableArrayListExtra(KEY_EXTENSION, ArrayList(info))
                putExtra(KEY_SHOW_UPDATED, showUpdatedExtension)
            }
        }
    }
}
