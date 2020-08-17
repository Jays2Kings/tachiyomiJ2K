import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

plugins {
    id(Plugins.androidApplication)
    kotlin(Plugins.kotlinAndroid)
    kotlin(Plugins.kotlinExtensions)
    kotlin(Plugins.kapt)
    id(Plugins.aboutLibraries)
    id(Plugins.googleServices) apply false
}

fun getBuildTime() = DateTimeFormatter.ISO_DATE_TIME.format(LocalDateTime.now(ZoneOffset.UTC))
fun getCommitCount() = runCommand("git rev-list --count HEAD")
fun getGitSha() = runCommand("git rev-parse --short HEAD")

fun runCommand(command: String): String {
    val byteOut = ByteArrayOutputStream()
    project.exec {
        commandLine = command.split(" ")
        standardOutput = byteOut
    }
    return String(byteOut.toByteArray()).trim()
}

android {
    compileSdkVersion(Configs.compileSdkVersion)
    buildToolsVersion(Configs.buildToolsVersion)

    defaultConfig {
        minSdkVersion(Configs.minSdkVersion)
        targetSdkVersion(Configs.targetSdkVersion)
        applicationId = Configs.applicationId
        versionCode = Configs.versionCode
        versionName = Configs.versionName
        testInstrumentationRunner = Configs.testInstrumentationRunner
        multiDexEnabled = true

        buildConfigField("String", "COMMIT_COUNT", "\"${getCommitCount()}\"")
        buildConfigField("String", "COMMIT_SHA", "\"${getGitSha()}\"")
        buildConfigField("String", "BUILD_TIME", "\"${getBuildTime()}\"")
        buildConfigField("Boolean", "INCLUDE_UPDATER", "false")

        ndk {
            abiFilters("armeabi-v7a", "arm64-v8a", "x86")
        }
    }
    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debugJ2K"
        }
        getByName("release") {
            applicationIdSuffix = ".j2k"
        }
    }

    flavorDimensions("default")

    productFlavors {
        create("standard") {
            buildConfigField("Boolean", "INCLUDE_UPDATER", "true")
        }
        create("dev") {
            resConfig("en")
        }
    }

    lintOptions {
        isAbortOnError = false
        isCheckReleaseBuilds = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }
}
androidExtensions {
    isExperimental = true
}

dependencies {
    // Modified dependencies
    implementation(Libs.UI.subsamplingScaleImageView)
    implementation(Libs.Util.junrar)

    // Android X libraries
    implementation(Libs.Android.appCompat)
    implementation(Libs.Android.cardView)
    implementation(Libs.Android.material)
    implementation(Libs.Android.recyclerView)
    implementation(Libs.Android.preference)
    implementation(Libs.Android.annotations)
    implementation(Libs.Android.browser)
    implementation(Libs.Android.biometric)
    implementation(Libs.Android.palette)
    implementation(Libs.Android.coreKtx)
    implementation(Libs.Android.constraintLayout)
    implementation(Libs.Android.multiDex)

    implementation(Libs.Google.firebase)

    implementation(Libs.Android.lifecycleExtensions)
    implementation(Libs.Android.lifecycleCommonJava8)
    implementation(Libs.Android.lifecycleRuntimeKtx)

    // ReactiveX
    implementation(Libs.Rx.android)
    implementation(Libs.Rx.java)
    implementation(Libs.Rx.relay)
    implementation(Libs.Rx.preferences)
    implementation(Libs.Rx.network)

    // Coroutines
    implementation(Libs.Kotlin.flowPreferences)

    // Network client
    implementation(Libs.Network.okhttp)
    implementation(Libs.Network.okhttpDns)
    implementation(Libs.Network.okhttpLoggingInterceptor)
    implementation(Libs.IO.okio)

    // Chucker
    debugImplementation(Libs.Network.chucker)
    releaseImplementation(Libs.Network.chuckerNoOp)

    // hyperion
    debugImplementation(Libs.Hyperion.attr)
    debugImplementation(Libs.Hyperion.buildConfig)
    debugImplementation(Libs.Hyperion.core)
    debugImplementation(Libs.Hyperion.crash)
    debugImplementation(Libs.Hyperion.disk)
    debugImplementation(Libs.Hyperion.geigerCounter)
    debugImplementation(Libs.Hyperion.measurement)
    debugImplementation(Libs.Hyperion.phoenix)
    debugImplementation(Libs.Hyperion.recorder)
    debugImplementation(Libs.Hyperion.sharedPreferences)
    debugImplementation(Libs.Hyperion.timber)

    // REST
    implementation(Libs.Network.retrofit)
    implementation(Libs.Network.retrofitGsonConverter)

    // JSON
    implementation(Libs.IO.gson)
    implementation(Libs.IO.kotson)

    // JavaScript engine
    implementation(Libs.Parsing.duktape)

    // Disk
    implementation(Libs.Disk.lrucache)
    implementation(Libs.Disk.unifile)

    // HTML parser
    implementation(Libs.Parsing.jsoup)

    // Job scheduling
    implementation(Libs.Android.workManager)
    implementation(Libs.Android.workManagerKtx)
    implementation(Libs.Google.playServices)

    // Changelog
    implementation(Libs.Util.changelog)

    // Database
    implementation(Libs.Database.sqlite)
    implementation(Libs.Database.storioCommon)
    implementation(Libs.Database.storioSqlite)
    implementation(Libs.Database.requerySqlite)

    // Model View Presenter
    implementation(Libs.Navigation.nucleus)
    implementation(Libs.Navigation.nucleusSupport)

    // Dependency injection
    implementation(Libs.Util.injekt)

    // Image library
    implementation(Libs.Image.coil)
    implementation(Libs.Image.coilGif)
    implementation(Libs.Image.coilSvg)

    // Logging
    implementation(Libs.Util.timber)

    // UI
    implementation(Libs.UI.materalDesignDimens)
    implementation(Libs.UI.loadingButton)
    implementation(Libs.UI.fastAdapter)
    implementation(Libs.UI.fastAdapterBinding)
    implementation(Libs.UI.flexibleAdapter)
    implementation(Libs.UI.flexibleAdapterUi)
    implementation(Libs.UI.filePicker)
    implementation(Libs.UI.materialDialogsCore)
    implementation(Libs.UI.materialDialogsInput)
    implementation(Libs.UI.systemUiHelper)
    implementation(Libs.UI.viewStatePager)
    implementation(Libs.UI.slice)

    implementation(Libs.UI.androidTagGroup)
    implementation(Libs.UI.photoView)
    implementation(Libs.UI.directionalPageView)
    implementation(Libs.UI.viewToolTip)
    implementation(Libs.UI.tapTargetView)

    // Conductor
    implementation(Libs.Navigation.conductor)
    implementation(Libs.Navigation.conductorSupport) {
        exclude("group", "com.android.support")
    }
    implementation(Libs.Navigation.conductorSupportPreferences)

    // RxBindings
    implementation(Libs.Rx.bindingAppcompat)
    implementation(Libs.Rx.bindingKotlin)
    implementation(Libs.Rx.bindingSupport)
    implementation(Libs.Rx.bindingRecycler)

    // Tests
    testImplementation(Libs.Test.junit4)
    testImplementation(Libs.Test.assertJCore)
    testImplementation(Libs.Test.mockito)

    testImplementation(Libs.Test.roboElectric)
    testImplementation(Libs.Test.roboElectricMultidex)
    testImplementation(Libs.Test.roboElectricShadowPlayServices)

    implementation(Libs.Kotlin.stdLib)
    implementation(Libs.Kotlin.coroutines)

    // Crash reports
    implementation(Libs.Util.acra)

    // Text distance
    implementation(Libs.Util.stringSimilarity)

    implementation(Libs.Util.aboutLibraries)

    // TLS 1.3 support for Android < 10
    implementation(Libs.Network.conscrypt)
}

// See https://kotlinlang.org/docs/reference/experimental.html#experimental-status-of-experimental-api-markers
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    kotlinOptions.freeCompilerArgs += "-Xopt-in=org.mylibrary.OptInAnnotation"
}

tasks.preBuild {
    dependsOn(tasks.ktlintFormat)
}

if (gradle.startParameter.taskRequests.toString().contains("Standard")) {
    apply(mapOf("plugin" to Plugins.googleServices))
}
