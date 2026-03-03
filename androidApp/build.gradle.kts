plugins {
    id(Plugins.androidApplication)
    kotlin(Plugins.kotlinAndroid)
}

android {
    namespace = "eu.kanade.tachiyomi.androidapp"
    compileSdk = AndroidVersions.compileSdk

    defaultConfig {
        applicationId = "eu.kanade.tachiyomi.androidapp"
        minSdk = AndroidVersions.minSdk
        targetSdk = AndroidVersions.targetSdk
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":shared"))
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
}
