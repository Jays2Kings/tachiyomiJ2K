import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

plugins {
    id("com.android.application") version BuildPluginsVersion.AGP
    id("com.google.android.gms.oss-licenses-plugin") version BuildPluginsVersion.OSS_LICENSE
    kotlin("android") version BuildPluginsVersion.KOTLIN
    kotlin("android.extensions") version BuildPluginsVersion.KOTLIN
    kotlin("kapt") version BuildPluginsVersion.KOTLIN
    id("com.google.gms.google-services") version BuildPluginsVersion.GOOGLE_SERVICES apply false
    id("org.jmailen.kotlinter") version BuildPluginsVersion.KOTLINTER
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
    compileSdkVersion(AndroidVersions.COMPILE_SDK)
    buildToolsVersion(AndroidVersions.BUILD_TOOL)

    defaultConfig {
        minSdkVersion(AndroidVersions.MIN_SDK)
        targetSdkVersion(AndroidVersions.TARGET_SDK)
        applicationId = "eu.kanade.tachiyomi"
        versionCode = AndroidVersions.VERSION_CODE
        versionName = AndroidVersions.VERSION_NAME
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        jvmTarget = "1.8"
    }
}
androidExtensions {
    isExperimental = true
}

dependencies {
    // Modified dependencies
    implementation("com.github.inorichi:subsampling-scale-image-view:ac0dae7")
    implementation("com.github.inorichi:junrar-android:634c1f5")

    // Android support library
    implementation("androidx.appcompat:appcompat:1.1.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("com.google.android.material:material:1.1.0")
    implementation("androidx.recyclerview:recyclerview:1.1.0")
    implementation("androidx.preference:preference:1.1.1")
    implementation("androidx.annotation:annotation:1.1.0")
    implementation("androidx.browser:browser:1.2.0")
    implementation("androidx.biometric:biometric:1.0.1")
    implementation("androidx.palette:palette:1.0.0")

    implementation("androidx.constraintlayout:constraintlayout:1.1.3")

    implementation("androidx.multidex:multidex:2.0.1")

    implementation("com.google.firebase:firebase-core:17.3.0")

    val lifecycleVersion = "2.2.0"
    implementation("androidx.lifecycle:lifecycle-extensions:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-common-java8:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")

    // ReactiveX
    implementation("io.reactivex:rxandroid:1.2.1")
    implementation("io.reactivex:rxjava:1.3.8")
    implementation("com.jakewharton.rxrelay:rxrelay:1.2.0")
    implementation("com.f2prateek.rx.preferences:rx-preferences:1.0.2")
    implementation("com.github.pwittchen:reactivenetwork:0.13.0")

    // Coroutines
    implementation("com.github.tfcporciuncula:flow-preferences:1.1.1")

    // Network client
    implementation("com.squareup.okhttp3:okhttp:${Versions.OKHTTP}")
    implementation("com.squareup.okhttp3:logging-interceptor:${Versions.OKHTTP}")
    implementation("com.squareup.okio:okio:2.6.0")

    // Chucker
    val chuckerVersion = "3.2.0"
    debugImplementation("com.github.ChuckerTeam.Chucker:library:${Versions.CHUCKER}")
    releaseImplementation("com.github.ChuckerTeam.Chucker:library-no-op:${Versions.CHUCKER}")

    // hyperion
    debugImplementation("com.willowtreeapps.hyperion:hyperion-core:${Versions.HYPERION}")
    debugImplementation("com.willowtreeapps.hyperion:hyperion-timber:${Versions.HYPERION}")
    debugImplementation("com.willowtreeapps.hyperion:hyperion-core:${Versions.HYPERION}")
    debugImplementation("com.willowtreeapps.hyperion:hyperion-attr:${Versions.HYPERION}")
    debugImplementation("com.willowtreeapps.hyperion:hyperion-build-config:${Versions.HYPERION}")
    debugImplementation("com.willowtreeapps.hyperion:hyperion-crash:${Versions.HYPERION}")
    debugImplementation("com.willowtreeapps.hyperion:hyperion-disk:${Versions.HYPERION}")
    debugImplementation("com.willowtreeapps.hyperion:hyperion-geiger-counter:${Versions.HYPERION}")
    debugImplementation("com.willowtreeapps.hyperion:hyperion-measurement:${Versions.HYPERION}")
    debugImplementation("com.willowtreeapps.hyperion:hyperion-phoenix:${Versions.HYPERION}")
    debugImplementation("com.willowtreeapps.hyperion:hyperion-recorder:${Versions.HYPERION}")
    debugImplementation("com.willowtreeapps.hyperion:hyperion-shared-preferences:${Versions.HYPERION}")

    // REST
    implementation("com.squareup.retrofit2:retrofit:${Versions.RETROFIT}")
    implementation("com.squareup.retrofit2:converter-gson:${Versions.RETROFIT}")

    // JSON
    implementation("com.google.code.gson:gson:2.8.6")
    implementation("com.github.salomonbrys.kotson:kotson:2.5.0")

    // JavaScript engine
    implementation("com.squareup.duktape:duktape-android:1.3.0")

    // Disk
    implementation("com.jakewharton:disklrucache:2.0.2")
    implementation("com.github.inorichi:unifile:e9ee588")

    // HTML parser
    implementation("org.jsoup:jsoup:1.13.1")

    // Job scheduling
    implementation("android.arch.work:work-runtime:${Versions.WORKMANAGER}")
    implementation("android.arch.work:work-runtime-ktx:${Versions.WORKMANAGER}")
    implementation("com.google.android.gms:play-services-gcm:17.0.0")

    // Changelog
    implementation("com.github.gabrielemariotti.changeloglib:changelog:2.1.0")

    // Database
    implementation("androidx.sqlite:sqlite:2.1.0")
    implementation("com.github.inorichi.storio:storio-common:8be19de@aar")
    implementation("com.github.inorichi.storio:storio-sqlite:8be19de@aar")
    implementation("io.requery:sqlite-android:3.31.0")

    // Model View Presenter
    implementation("info.android15.nucleus:nucleus:${Versions.NUCLEUS}")
    implementation("info.android15.nucleus:nucleus-support-v7:${Versions.NUCLEUS}")

    // Dependency injection
    implementation("com.github.inorichi.injekt:injekt-core:65b0440")

    // Image library
    val coilVersion = "0.10.1"
    implementation("io.coil-kt:coil:${Versions.COIL}")
    implementation("io.coil-kt:coil-gif:${Versions.COIL}")
    implementation("io.coil-kt:coil-svg:${Versions.COIL}")

    // Logging
    implementation("com.jakewharton.timber:timber:${Versions.TIMBER}")

    // UI
    implementation("com.dmitrymalkovich.android:material-design-dimens:1.4")
    implementation("com.github.dmytrodanylyk.android-process-button:library:1.0.4")
    implementation("com.mikepenz:fastadapter:${Versions.FASTADAPTER}")
    implementation("com.mikepenz:fastadapter-extensions-binding:${Versions.FASTADAPTER}")
    implementation("eu.davidea:flexible-adapter:5.1.0")
    implementation("eu.davidea:flexible-adapter-ui:1.0.0")
    implementation("com.nononsenseapps:filepicker:2.5.2")
    implementation("com.afollestad.material-dialogs:core:3.1.1")
    implementation("com.afollestad.material-dialogs:input:3.1.1")
    implementation("me.zhanghai.android.systemuihelper:library:1.0.0")
    implementation("com.nightlynexus.viewstatepageradapter:viewstatepageradapter:1.1.0")
    implementation("com.github.mthli:Slice:v1.2")

    implementation("com.github.kizitonwose:AndroidTagGroup:1.6.0")
    implementation("com.github.chrisbanes:PhotoView:2.3.0")
    implementation("com.github.carlosesco:DirectionalViewPager:a844dbca0a")
    implementation("com.github.florent37:viewtooltip:1.2.2")
    implementation("com.getkeepsafe.taptargetview:taptargetview:1.13.0")

    // Conductor
    implementation("com.bluelinelabs:conductor:2.1.5")
    implementation("com.bluelinelabs:conductor-support:2.1.5") {
        exclude("group", "com.android.support")
    }
    implementation("com.github.inorichi:conductor-support-preference:a32c357")

    // RxBindings
    implementation("com.jakewharton.rxbinding:rxbinding-kotlin:${Versions.RX_BINDING}")
    implementation("com.jakewharton.rxbinding:rxbinding-appcompat-v7-kotlin:${Versions.RX_BINDING}")
    implementation("com.jakewharton.rxbinding:rxbinding-support-v4-kotlin:${Versions.RX_BINDING}")
    implementation("com.jakewharton.rxbinding:rxbinding-recyclerview-v7-kotlin:${Versions.RX_BINDING}")

    // Tests
    testImplementation("junit:junit:4.13")
    testImplementation("org.assertj:assertj-core:3.12.2")
    testImplementation("org.mockito:mockito-core:1.10.19")

    testImplementation("org.robolectric:robolectric:${Versions.ROBO_ELECTRIC}")
    testImplementation("org.robolectric:shadows-multidex:${Versions.ROBO_ELECTRIC}")
    testImplementation("org.robolectric:shadows-play-services:${Versions.ROBO_ELECTRIC}")

    implementation(kotlin("stdlib", org.jetbrains.kotlin.config.KotlinCompilerVersion.VERSION))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.COROUTINES}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.COROUTINES}")

    // Crash reports
    implementation("ch.acra:acra:${Versions.ACRA}")

    // Text distance
    implementation("info.debatty:java-string-similarity:1.2.1")

    implementation("com.google.android.gms:play-services-oss-licenses:${Versions.OSS_LICENSE}")
}


tasks.preBuild {
    dependsOn(tasks.lintKotlin)
}
tasks.lintKotlin {
    dependsOn(tasks.formatKotlin)
}

if (gradle.startParameter.taskRequests.toString().contains("Standard")) {
    apply(mapOf("plugin" to "com.google.gms.google-services"))
}
