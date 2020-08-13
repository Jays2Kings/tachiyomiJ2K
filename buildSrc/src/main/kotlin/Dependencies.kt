object Libs {

    object Android {
        const val annotations = "androidx.annotation:annotation:1.1.0"
        const val appCompat = "androidx.appcompat:appcompat:${Versions.androidAppCompat}"
        const val biometric = "androidx.biometric:biometric:1.0.1"
        const val browser = "androidx.browser:browser:1.2.0"
        const val cardView = "androidx.cardview:cardview:1.0.0"
        const val constraintLayout = "androidx.constraintlayout:constraintlayout:1.1.3"
        const val coreKtx = "androidx.core:core-ktx:\$1.3.1"
        const val lifecycleCommonJava8 = "androidx.lifecycle:lifecycle-common-java8:${Versions.androidLifecycle}"
        const val lifecycleExtensions = "androidx.lifecycle:lifecycle-extensions:${Versions.androidLifecycle}"
        const val lifecycleRuntimeKtx = "androidx.lifecycle:lifecycle-runtime-ktx:${Versions.androidLifecycle}"
        const val material = "com.google.android.material:material:1.1.0"
        const val multiDex = "androidx.multidex:multidex:2.0.1"
        const val palette = "androidx.palette:palette:1.0.0"
        const val preference = "androidx.preference:preference:1.1.1"
        const val recyclerView = "androidx.recyclerview:recyclerview:1.1.0"
        const val workManager = "androidx.work:work-runtime:${Versions.androidWorkManager}"
        const val workManagerKtx = "androidx.work:work-runtime-ktx:${Versions.androidWorkManager}"
    }

    object Database {
        const val requerySqlite = "io.requery:sqlite-android:3.31.0"
        const val sqlite = "androidx.sqlite:sqlite:2.1.0"
        const val storioCommon = "com.github.inorichi.storio:storio-common:8be19de@aar"
        const val storioSqlite = "com.github.inorichi.storio:storio-sqlite:8be19de@aar"
    }

    object Disk {
        const val lrucache = "com.jakewharton:disklrucache:2.0.2"
        const val unifile = "com.github.inorichi:unifile:e9ee588"
    }

    object Google {
        const val firebase = "com.google.firebase:firebase-core:17.4.4"
        const val playServices = "com.google.android.gms:play-services-gcm:17.0.0"
    }

    object Hyperion {
        const val attr = "com.willowtreeapps.hyperion:hyperion-attr:${Versions.hyperion}"
        const val buildConfig = "com.willowtreeapps.hyperion:hyperion-build-config:${Versions.hyperion}"
        const val core = "com.willowtreeapps.hyperion:hyperion-core:${Versions.hyperion}"
        const val crash = "com.willowtreeapps.hyperion:hyperion-crash:${Versions.hyperion}"
        const val disk = "com.willowtreeapps.hyperion:hyperion-disk:${Versions.hyperion}"
        const val geigerCounter = "com.willowtreeapps.hyperion:hyperion-geiger-counter:${Versions.hyperion}"
        const val measurement = "com.willowtreeapps.hyperion:hyperion-measurement:${Versions.hyperion}"
        const val phoenix = "com.willowtreeapps.hyperion:hyperion-phoenix:${Versions.hyperion}"
        const val recorder = "com.willowtreeapps.hyperion:hyperion-recorder:${Versions.hyperion}"
        const val sharedPreferences = "com.willowtreeapps.hyperion:hyperion-shared-preferences:${Versions.hyperion}"
        const val timber = "com.willowtreeapps.hyperion:hyperion-timber:${Versions.hyperion}"
    }

    object Image {
        const val coil = "io.coil-kt:coil:${Versions.coil}"
        const val coilGif = "io.coil-kt:coil-gif:${Versions.coil}"
        const val coilSvg = "io.coil-kt:coil-svg:${Versions.coil}"
    }

    object Io {
        const val gson = "com.google.code.gson:gson:2.8.6"
        const val kotson = "com.github.salomonbrys.kotson:kotson:2.5.0"
        const val okio = "com.squareup.okio:okio:2.6.0"
    }

    object Kotlin {
        const val coroutines = "org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.kotlinCoroutines}"
        const val flowPreferences = "com.github.tfcporciuncula:flow-preferences:1.1.1"
        const val stdLib = "org.jetbrains.kotlin:kotlin-stdlib:${Versions.kotlin}"
    }

    object Navigation {
        const val conductor = "com.bluelinelabs:conductor:2.1.5"
        const val conductorSupport = "com.bluelinelabs:conductor-support:2.1.5"
        const val nucleus = "info.android15.nucleus:nucleus:${Versions.nucleus}"
        const val nucleusSupport = "info.android15.nucleus:nucleus-support-v7:${Versions.nucleus}"
    }

    object Network {
        const val chucker = "com.github.ChuckerTeam.Chucker:library:${Versions.chucker}"
        const val conscrypt = "org.conscrypt:conscrypt-android:2.4.0"
        const val okhttp = "com.squareup.okhttp3:okhttp:${Versions.okhttp}"
        const val okhttpDns = "com.squareup.okhttp3:okhttp-dnsoverhttps:${Versions.okhttp}"
        const val okhttpLoggingInterceptor = "com.squareup.okhttp3:logging-interceptor:${Versions.okhttp}"
        const val retrofit = "com.squareup.retrofit2:retrofit:${Versions.retrofit}"
        const val retrofitGsonConverter = "com.squareup.retrofit2:converter-gson:${Versions.retrofit}"
    }

    object Parsing {
        const val duktape = "com.squareup.duktape:duktape-android:1.3.0"
        const val jsoup = "org.jsoup:jsoup:1.13.1"
    }

    object Rx {
        const val android = "io.reactivex:rxandroid:1.2.1"
        const val bindingAppcompat = "com.jakewharton.rxbinding:rxbinding-appcompat-v7-kotlin:${Versions.rxBinding}"
        const val bindingKotlin = "com.jakewharton.rxbinding:rxbinding-kotlin:${Versions.rxBinding}"
        const val bindingRecycler = "com.jakewharton.rxbinding:rxbinding-recyclerview-v7-kotlin:${Versions.rxBinding}"
        const val bindingSupport = "com.jakewharton.rxbinding:rxbinding-support-v4-kotlin:${Versions.rxBinding}"
        const val java = "io.reactivex:rxjava:1.3.8"
        const val network = "com.github.pwittchen:reactivenetwork:0.13.0"
        const val preferences = "com.f2prateek.rx.preferences:rx-preferences:1.0.2"
        const val relay = "com.jakewharton.rxrelay:rxrelay:1.2.0"
    }

    object Tachiyomi {
        const val conductorSupportPreferences = "com.github.inorichi:conductor-support-preference:a32c357"
        const val junrar = "com.github.inorichi:junrar-android:${Versions.tachiyomiJunrar}"
        const val subsamplingScaleImageView = "com.github.inorichi:subsampling-scale-image-view:${Versions.tachiyomiSubsamplingImageScale}"
    }

    // Tests
    object Test {
        const val assertJCore = "org.assertj:assertj-core:3.12.2"
        const val junit4 = "junit:junit:4.13"
        const val mockito = "org.mockito:mockito-core:1.10.19"
        const val roboElectric = "org.robolectric:robolectric:${Versions.roboElectric}"
        const val roboElectricMultidex = "org.robolectric:shadows-multidex:${Versions.roboElectric}"
        const val roboElectricShadowPlayServices = "org.robolectric:shadows-play-services:${Versions.roboElectric}"
    }

    object Ui {
        const val androidTagGroup = "com.github.kizitonwose:AndroidTagGroup:1.6.0"
        const val directionalPageView = "com.github.carlosesco:DirectionalViewPager:a844dbca0a"
        const val fastAdapter = "com.mikepenz:fastadapter:${Versions.fastAdapter}"
        const val fastAdapterBinding = "com.mikepenz:fastadapter-extensions-binding:${Versions.fastAdapter}"
        const val filePicker = "com.nononsenseapps:filepicker:2.5.2"
        const val flexibleAdapter = "eu.davidea:flexible-adapter:5.1.0"
        const val flexibleAdapterUi = "eu.davidea:flexible-adapter-ui:1.0.0"
        const val loadingButton = "br.com.simplepass:loading-button-android:2.2.0"
        const val materalDesignDimens = "com.dmitrymalkovich.android:material-design-dimens:1.4"
        const val materialDialogsCore = "com.afollestad.material-dialogs:core:3.1.1"
        const val materialDialogsInput = "com.afollestad.material-dialogs:input:3.1.1"
        const val photoView = "com.github.chrisbanes:PhotoView:2.3.0"
        const val slice = "com.github.mthli:Slice:v1.2"
        const val systemUiHelper = "me.zhanghai.android.systemuihelper:library:1.0.0"
        const val tapTargetView = "com.getkeepsafe.taptargetview:taptargetview:1.13.0"
        const val viewStatePager = "com.nightlynexus.viewstatepageradapter:viewstatepageradapter:1.1.0"
        const val viewToolTip = "com.github.florent37:viewtooltip:1.2.2"
    }

    object Util {
        const val aboutLibraries = "com.mikepenz:aboutlibraries:${Versions.aboutLibraries}"
        const val acra = "ch.acra:acra:${Versions.acra}"
        const val changelog = "com.github.gabrielemariotti.changeloglib:changelog:2.1.0"
        const val injekt = "com.github.inorichi.injekt:injekt-core:65b0440"
        const val textDistance = "info.debatty:java-string-similarity:1.2.1"
        const val timber = "com.jakewharton.timber:timber:${Versions.timber}"
    }
}

object Versions {
    const val aboutLibraries = "8.3.0"
    const val acra = "4.9.2"
    const val androidAppCompat = "1.1.0"
    const val androidGradlePlugin = "4.0.1"
    const val androidLifecycle = "2.2.0"
    const val androidWorkManager = "2.4.0"
    const val chucker = "3.2.0"
    const val coil = "0.11.0"
    const val fastAdapter = "5.0.0"
    const val googleServices = "4.3.3"
    const val gradleVersions = "0.29.0"
    const val hyperion = "0.9.27"
    const val kotlin = "1.3.72"
    const val kotlinCoroutines = "1.3.5"
    const val ktlint = "9.3.0"
    const val nucleus = "3.0.0"
    const val okhttp = "4.8.1"
    const val retrofit = "2.7.2"
    const val roboElectric = "3.1.4"
    const val rxBinding = "1.0.1"
    const val tachiyomiJunrar = "634c1f5"
    const val tachiyomiSubsamplingImageScale = "ac0dae7"
    const val timber = "4.7.1"
}

