import java.io.ByteArrayOutputStream
import org.gradle.api.tasks.Copy
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id(Plugins.androidApplication)
    kotlin(Plugins.kotlinAndroid)
    kotlin(Plugins.kapt)
    id(Plugins.kotlinParcelize)
    id(Plugins.kotlinSerialization)
    id("com.google.android.gms.oss-licenses-plugin")
    id(Plugins.googleServices) apply false
    id("com.google.firebase.crashlytics") apply false
    id("org.jetbrains.kotlin.plugin.compose") version AndroidVersions.kotlin // this version matches your Kotlin version
}

if (gradle.startParameter.taskRequests.toString().contains("Standard")) {
    apply<com.google.gms.googleservices.GoogleServicesPlugin>()
    apply<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsPlugin>()
}

fun runCommand(command: String): String {
    return try {
        val process = ProcessBuilder(command.split(" "))
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        output.trim()
    } catch (e: Exception) {
        ""
    }
}

val supportedAbis = setOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")

android {
    compileSdk = AndroidVersions.compileSdk
    ndkVersion = AndroidVersions.ndk

    defaultConfig {
        minSdk = AndroidVersions.minSdk
        targetSdk = AndroidVersions.targetSdk
        applicationId = "eu.kanade.tachiyomi"
        versionCode = AndroidVersions.versionCode
        versionName = AndroidVersions.versionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        multiDexEnabled = true

        buildConfigField("String", "COMMIT_COUNT", "\"${getCommitCount()}\"")
        buildConfigField("String", "BETA_COUNT", "\"${getBetaCount()}\"")
        buildConfigField("String", "COMMIT_SHA", "\"${getGitSha()}\"")
        buildConfigField("String", "BUILD_TIME", "\"${getBuildTime()}\"")
        buildConfigField("Boolean", "INCLUDE_UPDATER", "false")
        buildConfigField("boolean", "BETA", "false")

        ndk {
            abiFilters += supportedAbis
        }
        externalNativeBuild {
            cmake {
                this.arguments("-DHAVE_LIBJXL=FALSE")
            }
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include(*supportedAbis.toTypedArray())
            isUniversalApk = true
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debugJ2K"
            versionNameSuffix = "-d${getCommitCount()}"
        }
        getByName("release") {
            applicationIdSuffix = ".j2k"
            isShrinkResources = true
            isMinifyEnabled = true
            proguardFiles("proguard-android-optimize.txt", "proguard-rules.pro")
        }
        create("beta") {
            initWith(getByName("release"))
            buildConfigField("boolean", "BETA", "true")
            versionNameSuffix = "-b${getBetaCount()}"
        }
    }

    buildFeatures {
        viewBinding = true
        compose = true

        // Disable some unused things
        aidl = false
        renderScript = false
        shaders = false
    }

    flavorDimensions.add("default")

    productFlavors {
        create("standard") {
            buildConfigField("Boolean", "INCLUDE_UPDATER", "true")
        }
        create("dev") {
            androidResources.localeFilters.clear()
            androidResources.localeFilters.add("en")
        }
    }

    lint {
        disable.addAll(listOf("MissingTranslation", "ExtraTranslation"))
        abortOnError = false
        checkReleaseBuilds = false
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.3"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    namespace = "eu.kanade.tachiyomi"
}

dependencies {
    // Compose
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.foundation:foundation:1.8.0")
    implementation("androidx.compose.animation:animation:1.8.0")
    implementation("androidx.compose.ui:ui:1.8.0")
    implementation("androidx.compose.material:material:1.8.0")
    implementation("androidx.compose.material3:material3:1.3.2")
    implementation("com.google.android.material:compose-theme-adapter-3:1.1.1")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.compose.ui:ui-tooling-preview:1.8.0")
    debugImplementation("androidx.compose.ui:ui-tooling:1.8.0")
    implementation("com.google.accompanist:accompanist-webview:0.30.1")
    implementation("androidx.glance:glance-appwidget:1.1.1")

    // Modified dependencies
    implementation("com.github.jays2kings:subsampling-scale-image-view:756849e") {
        exclude(module = "image-decoder")
    }
    implementation("com.github.tachiyomiorg:image-decoder:7879b45")

    // Android X libraries
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("com.google.android.material:material:1.14.0-alpha02")
    implementation("androidx.webkit:webkit:1.13.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.preference:preference:1.2.1")
    implementation("androidx.annotation:annotation:1.9.1")
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.palette:palette:1.0.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("com.google.android.flexbox:flexbox:3.0.0")
    implementation("androidx.window:window:1.3.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    implementation("androidx.constraintlayout:constraintlayout:2.2.1")

    implementation("androidx.multidex:multidex:2.0.1")

    val lifecycleVersion = "2.8.7"
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-common:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-process:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")

    // ReactiveX
    implementation("io.reactivex:rxandroid:1.2.1")
    implementation("io.reactivex:rxjava:1.3.8")
    implementation("com.jakewharton.rxrelay:rxrelay:1.2.0")

    // Coroutines
    implementation("com.fredporciuncula:flow-preferences:1.6.0")

    // Network client
    val okhttpVersion = "5.0.0-alpha.14"
    implementation("com.squareup.okhttp3:okhttp:$okhttpVersion")
    implementation("com.squareup.okhttp3:logging-interceptor:$okhttpVersion")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:$okhttpVersion")
    implementation("com.squareup.okhttp3:okhttp-brotli:$okhttpVersion")
    implementation("com.squareup.okio:okio:3.11.0")

    // Chucker
//    val chuckerVersion = "3.5.2"
//    debugImplementation("com.github.ChuckerTeam.Chucker:library:$chuckerVersion")
//    releaseImplementation("com.github.ChuckerTeam.Chucker:library-no-op:$chuckerVersion")
//    add("betaImplementation", "com.github.ChuckerTeam.Chucker:library-no-op:$chuckerVersion")

    implementation(kotlin("reflect", version = AndroidVersions.kotlin))

    // JSON
    val kotlinSerialization = "1.8.1"
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${kotlinSerialization}")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:${kotlinSerialization}")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-okio:${kotlinSerialization}")

    // JavaScript engine
    implementation("app.cash.quickjs:quickjs-android:0.9.2")

    // Disk
    implementation("com.jakewharton:disklrucache:2.0.2")
    implementation("com.github.tachiyomiorg:unifile:17bec43")
    implementation("com.github.junrar:junrar:7.5.5")

    // HTML parser
    implementation("org.jsoup:jsoup:1.19.1")

    // Job scheduling
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("com.google.guava:guava:32.0.1-jre")

    implementation("com.google.android.gms:play-services-gcm:17.0.0")

    // Database
    implementation("androidx.sqlite:sqlite-ktx:2.5.0")
    implementation("com.github.requery:sqlite-android:3.45.0")
    implementation("com.github.inorichi.storio:storio-common:8be19de@aar")
    implementation("com.github.inorichi.storio:storio-sqlite:8be19de@aar")

    // Model View Presenter
    val nucleusVersion = "3.0.0"
    implementation("info.android15.nucleus:nucleus:$nucleusVersion")
    implementation("info.android15.nucleus:nucleus-support-v7:$nucleusVersion")

    // Dependency injection
    implementation("com.github.mihonapp:injekt:91edab2317")

    // Image library
    val coilVersion = "2.4.0"
    implementation("io.coil-kt:coil:$coilVersion")
    implementation("io.coil-kt:coil-gif:$coilVersion")
    implementation("io.coil-kt:coil-svg:$coilVersion")

    // Logging
    implementation("com.jakewharton.timber:timber:4.7.1")

    // Sort
    implementation("com.github.gpanther:java-nat-sort:natural-comparator-1.1")

    // UI
    implementation("io.writeopia:loading-button:3.0.0")
    val fastAdapterVersion = "5.6.0"
    implementation("com.mikepenz:fastadapter:$fastAdapterVersion")
    implementation("com.mikepenz:fastadapter-extensions-binding:$fastAdapterVersion")
    implementation("com.github.arkon.FlexibleAdapter:flexible-adapter:c8013533")
    implementation("com.github.arkon.FlexibleAdapter:flexible-adapter-ui:c8013533")
    implementation("com.nightlynexus.viewstatepageradapter:viewstatepageradapter:1.1.0")
    implementation("com.github.mthli:Slice:v1.2")
    implementation("io.noties.markwon:core:4.6.2")

    implementation("com.github.chrisbanes:PhotoView:2.3.0")
    implementation("com.github.tachiyomiorg:DirectionalViewPager:1.0.0")
    implementation("com.github.florent37:ViewTooltip:f79a895")
    implementation("com.getkeepsafe.taptargetview:taptargetview:1.13.3")

    // Conductor
    val conductorVersion = "4.0.0-preview-3"
    implementation("com.bluelinelabs:conductor:$conductorVersion")
    implementation("com.github.tachiyomiorg:conductor-support-preference:3.0.0")

    // Shizuku
    val shizukuVersion = "12.1.0"
    implementation("dev.rikka.shizuku:api:$shizukuVersion")
    implementation("dev.rikka.shizuku:provider:$shizukuVersion")

    implementation(kotlin("stdlib"))

    val coroutines = "1.10.2"
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutines")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutines")

    // Text distance
    implementation("info.debatty:java-string-similarity:2.0.0")

    implementation("com.google.android.gms:play-services-oss-licenses:17.1.0")

    // TLS 1.3 support for Android < 10
    implementation("org.conscrypt:conscrypt-android:2.5.3")

    // Android Chart
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
}

// Firebase dependencies only for Standard variants
if (gradle.startParameter.taskRequests.toString().contains("Standard")) {
    dependencies {
        implementation(platform("com.google.firebase:firebase-bom:33.13.0"))
        implementation("com.google.firebase:firebase-analytics-ktx")
        implementation("com.google.firebase:firebase-crashlytics-ktx")
    }
}

tasks {
    // See https://kotlinlang.org/docs/reference/experimental.html#experimental-status-of-experimental-api(-markers)
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions.freeCompilerArgs.addAll(
            "-Xcontext-receivers",
            "-opt-in=kotlin.Experimental",
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlin.ExperimentalStdlibApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=androidx.compose.material.ExperimentalMaterialApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material.ExperimentalMaterialApi",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi",
            "-opt-in=coil.annotation.ExperimentalCoilApi",
            "-opt-in=com.google.accompanist.permissions.ExperimentalPermissionsApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.InternalCoroutinesApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )

//        if (project.findProperty("tachiyomi.enableComposeCompilerMetrics") == "true") {
//            compilerOptions.freeCompilerArgs.addAll(
//                "-P",
//                "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=" +
//                        project.layout.buildDirectory + "/compose_metrics",
//            )
//            compilerOptions.freeCompilerArgs.addAll(
//                "-P",
//                "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=" +
//                        project.layout.buildDirectory + "/compose_metrics",
//            )
//        }
    }

    // Duplicating Hebrew string assets due to some locale code issues on different devices
    val copyHebrewStrings = task("copyHebrewStrings", type = Copy::class) {
        from("./src/main/res/values-he")
        into("./src/main/res/values-iw")
        include("**/*")
    }

    preBuild {
        dependsOn(formatKotlin, copyHebrewStrings)
    }
}
