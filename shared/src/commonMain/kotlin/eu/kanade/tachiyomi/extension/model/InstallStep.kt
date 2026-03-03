package eu.kanade.tachiyomi.extension.model

enum class InstallStep {
    Pending, Downloading, Loading, Installing, Installed, Error, Done;

    fun isCompleted(): Boolean {
        return this == Installed || this == Error || this == Done
    }
}
