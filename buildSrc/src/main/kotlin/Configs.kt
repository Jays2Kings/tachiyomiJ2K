object Configs {
    const val applicationId = "eu.kanade.tachiyomi"
    const val buildToolsVersion = "29.0.3"
    const val compileSdkVersion = 29
    const val minSdkVersion = 23
    const val targetSdkVersion = 29
    const val testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    const val versionCode = 66
    const val versionName = "1.0.7"
}

object LegacyPluginClassPath {
    const val aboutLibraries = "com.mikepenz.aboutlibraries.plugin:aboutlibraries-plugin:${Versions.aboutLibraries}"
    const val androidGradlePlugin = "com.android.tools.build:gradle:${Versions.androidGradlePlugin}"
    const val googleServices = "com.google.gms:google-services:${Versions.googleServices}"
    const val kotlinExtensions = "org.jetbrains.kotlin:kotlin-android-extensions:${Versions.kotlin}"
    const val kotlinPlugin = "org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.kotlin}"
}

object Plugins {
    const val aboutLibraries = "com.mikepenz.aboutlibraries.plugin"
    const val androidApplication = "com.android.application"
    const val googleServices = "com.google.gms.google-services"
    const val kapt = "kapt"
    const val kotlinAndroid = "android"
    const val kotlinExtensions = "android.extensions"

    object GradleVersions {
        const val name = "com.github.ben-manes.versions"
        const val version = Versions.gradleVersions
    }

    object KtLint {
        const val name = "org.jlleitschuh.gradle.ktlint"
        const val version = Versions.ktlint
    }
}

