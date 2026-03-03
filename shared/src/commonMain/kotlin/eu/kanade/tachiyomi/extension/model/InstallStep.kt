package eu.kanade.tachiyomi.extension.model

enum class InstallStep {
    Pending,
    Discovering,
    VerifyingTrust,
    Downloading,
    Loading,
    Installing,
    Installed,
    Refreshing,
    Error,
    Done;

    fun isCompleted(): Boolean {
        return this == Installed || this == Error || this == Done
    }
}
