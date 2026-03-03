plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.library")
}

kotlin {
    androidTarget()
    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.6.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-okio:1.6.0")
                implementation("com.squareup.okio:okio:3.4.0")
                implementation("io.ktor:ktor-client-core:2.3.6")
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("app.cash.quickjs:quickjs-android:0.9.2")
                implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.11")
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation("org.mozilla:rhino:1.7.15")
                implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.11")
            }
        }
    }
}

android {
    namespace = "eu.kanade.tachiyomi.shared"
    compileSdk = AndroidVersions.compileSdk

    defaultConfig {
        minSdk = AndroidVersions.minSdk
    }
}
