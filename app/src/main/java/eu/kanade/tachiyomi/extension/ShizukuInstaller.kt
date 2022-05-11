package eu.kanade.tachiyomi.extension

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.extension.util.ExtensionInstaller.Companion.EXTRA_DOWNLOAD_ID
import eu.kanade.tachiyomi.util.system.getUriSize
import eu.kanade.tachiyomi.util.system.isPackageInstalled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import rikka.sui.Sui
import timber.log.Timber
import uy.kohesive.injekt.injectLazy
import java.io.BufferedReader
import java.io.InputStream
import java.util.Collections
import java.util.concurrent.atomic.AtomicReference

class ShizukuInstaller(private val context: Context, val finishedQueue: (ShizukuInstaller) -> Unit) {

    private val extensionManager: ExtensionManager by injectLazy()

    private var waitingInstall = AtomicReference<Entry>(null)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val cancelReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1).takeIf { it >= 0 } ?: return
            cancelQueue(downloadId)
        }
    }

    data class Entry(val downloadId: Long, val pkgName: String, val uri: Uri)
    private val queue = Collections.synchronizedList(mutableListOf<Entry>())

    private val shizukuDeadListener = Shizuku.OnBinderDeadListener {
        Timber.d("Shizuku was killed prematurely")
        finishedQueue(this)
    }

    fun isInQueue(pkgName: String) = queue.any { it.pkgName == pkgName }

    private val shizukuPermissionListener = object : Shizuku.OnRequestPermissionResultListener {
        override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
            if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    ready = true
                    checkQueue()
                } else {
                    finishedQueue(this@ShizukuInstaller)
                }
                Shizuku.removeRequestPermissionResultListener(this)
            }
        }
    }

    var ready = false

    init {
        Shizuku.addBinderDeadListener(shizukuDeadListener)
        require(Shizuku.pingBinder() && (context.isPackageInstalled(shizukuPkgName) || Sui.isSui())) {
            finishedQueue(this)
            context.getString(R.string.ext_installer_shizuku_stopped)
        }
        ready = if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            true
        } else {
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
            false
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    fun processEntry(entry: Entry) {
        extensionManager.setInstalling(entry.downloadId, entry.uri.hashCode())
        ioScope.launch {
            var sessionId: String? = null
            try {
                val size = context.getUriSize(entry.uri) ?: throw IllegalStateException()
                context.contentResolver.openInputStream(entry.uri)!!.use {
                    val createCommand = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        "pm install-create --user current -r -i ${context.packageName} -S $size"
                    } else {
                        "pm install-create -r -i ${context.packageName} -S $size"
                    }
                    val createResult = exec(createCommand)
                    sessionId = SESSION_ID_REGEX.find(createResult.out)?.value
                        ?: throw RuntimeException("Failed to create install session")

                    val writeResult = exec("pm install-write -S $size $sessionId base -", it)
                    if (writeResult.resultCode != 0) {
                        throw RuntimeException("Failed to write APK to session $sessionId")
                    }

                    val commitResult = exec("pm install-commit $sessionId")
                    if (commitResult.resultCode != 0) {
                        throw RuntimeException("Failed to commit install session $sessionId")
                    }

                    continueQueue(true)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to install extension ${entry.downloadId} ${entry.uri}")
                if (sessionId != null) {
                    exec("pm install-abandon $sessionId")
                }
                continueQueue(false)
            }
        }
    }

    /**
     * Checks the queue. The provided service will be stopped if the queue is empty.
     * Will not be run when not ready.
     *
     * @see ready
     */
    fun checkQueue() {
        if (!ready) {
            return
        }
        if (queue.isEmpty()) {
            finishedQueue(this)
            return
        }
        val nextEntry = queue.first()
        if (waitingInstall.compareAndSet(null, nextEntry)) {
            queue.removeFirst()
            processEntry(nextEntry)
        }
    }

    /**
     * Tells the queue to continue processing the next entry and updates the install step
     * of the completed entry ([waitingInstall]) to [ExtensionManager].
     *
     * @param resultStep new install step for the processed entry.
     * @see waitingInstall
     */
    fun continueQueue(succeeded: Boolean) {
        val completedEntry = waitingInstall.getAndSet(null)
        if (completedEntry != null) {
            extensionManager.setInstallationResult(completedEntry.downloadId, succeeded)
            checkQueue()
        }
    }

    /**
     * Add an item to install queue.
     *
     * @param downloadId Download ID as known by [ExtensionManager]
     * @param uri Uri of APK to install
     */
    fun addToQueue(downloadId: Long, pkgName: String, uri: Uri) {
        queue.add(Entry(downloadId, pkgName, uri))
        checkQueue()
    }

    /**
     * Cancels queue for the provided download ID if exists.
     *
     * @param downloadId Download ID as known by [ExtensionManager]
     */
    private fun cancelQueue(downloadId: Long) {
        val waitingInstall = this.waitingInstall.get()
        val toCancel = queue.find { it.downloadId == downloadId } ?: waitingInstall ?: return
        if (cancelEntry(toCancel)) {
            queue.remove(toCancel)
            if (waitingInstall == toCancel) {
                // Currently processing removed entry, continue queue
                this.waitingInstall.set(null)
                checkQueue()
            }
            queue.forEach { extensionManager.setInstallationResult(it.downloadId, false) }
//            extensionManager.up(downloadId, InstallStep.Idle)
        }
    }

    // Don't cancel if entry is already started installing
    fun cancelEntry(entry: Entry): Boolean = getActiveEntry() != entry
    fun getActiveEntry(): Entry? = waitingInstall.get()

    fun onDestroy() {
        Shizuku.removeBinderDeadListener(shizukuDeadListener)
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        ioScope.cancel()
        LocalBroadcastManager.getInstance(context).unregisterReceiver(cancelReceiver)
        queue.forEach { extensionManager.setInstallationResult(it.pkgName, false) }
        queue.clear()
        waitingInstall.set(null)
    }

    private fun exec(command: String, stdin: InputStream? = null): ShellResult {
        @Suppress("DEPRECATION")
        val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
        if (stdin != null) {
            process.outputStream.use { stdin.copyTo(it) }
        }
        val output = process.inputStream.bufferedReader().use(BufferedReader::readText)
        val resultCode = process.waitFor()
        return ShellResult(resultCode, output)
    }

    private data class ShellResult(val resultCode: Int, val out: String)

    companion object {
        const val shizukuPkgName = "moe.shizuku.privileged.api"
        const val downloadLink = "https://shizuku.rikka.app/download"
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 14045
        private val SESSION_ID_REGEX = Regex("(?<=\\[).+?(?=])")
        fun isShizukuRunning(): Boolean {
            return Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }
    }
}
