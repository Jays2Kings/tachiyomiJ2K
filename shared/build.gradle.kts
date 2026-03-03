plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

kotlin {
    androidTarget()
    jvm("desktop")

    sourceSets {
        val commonMain by getting
        val androidMain by getting
        val desktopMain by getting
    }
}

android {
    namespace = "eu.kanade.tachiyomi.shared"
    compileSdk = AndroidVersions.compileSdk

    defaultConfig {
        minSdk = AndroidVersions.minSdk
    }
}
