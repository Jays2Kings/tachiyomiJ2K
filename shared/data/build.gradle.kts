plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("app.cash.sqldelight") version "2.0.1"
}

kotlin {
    androidTarget()
    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("app.cash.sqldelight:runtime:2.0.1")
                implementation("app.cash.sqldelight:coroutines-extensions:2.0.1")
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("app.cash.sqldelight:android-driver:2.0.1")
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation("app.cash.sqldelight:sqlite-driver:2.0.1")
            }
        }
    }
}

android {
    namespace = "eu.kanade.tachiyomi.shared.data"
    compileSdk = AndroidVersions.compileSdk

    defaultConfig {
        minSdk = AndroidVersions.minSdk
    }
}

sqldelight {
    databases {
        create("SharedDataDatabase") {
            packageName.set("eu.kanade.tachiyomi.shared.data.db")
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}
