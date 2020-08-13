object ClassPaths {
    const val androidGradlePlugin = "com.android.tools.build:gradle:${Versions.androidGradlePlugin}"
    const val kotlinPlugin = "org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.kotlin}"
    const val ktlint = "org.jlleitschuh.gradle:ktlint-gradle:${Versions.ktlint}"
    const val kotlinExtensions = "org.jetbrains.kotlin:kotlin-android-extensions:${Versions.kotlin}"
    const val googleServices = "com.google.gms:google-services:${Versions.googleServices}"
    const val aboutLibraries = "${Plugins.aboutLibraries}:aboutlibraries-plugin:${Versions.aboutLibraries}"
    const val gradleVersion = "com.github.ben-manes:gradle-versions-plugin:${Versions.gradleVersions}"
}

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

object Plugins {
    const val androidApplication = "com.android.application"
    const val kotlinAndroid = "android"
    const val kotlinExtensions = "android.extensions"
    const val kapt = "kapt"
    const val gradleVersions = "com.github.ben-manes.versions"
    const val aboutLibraries = "com.mikepenz.aboutlibraries.plugin"
    const val googleServices = "com.google.gms.google-services"
    const val ktlint = "org.jlleitschuh.gradle.ktlint"
}
