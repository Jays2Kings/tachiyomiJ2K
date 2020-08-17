object Libs {

    object Android {
        const val annotations = "androidx.annotation:annotation:${Versions.androidAnnotations}"
        const val appCompat = "androidx.appcompat:appcompat:${Versions.androidAppCompat}"
        const val biometric = "androidx.biometric:biometric:${Versions.androidBiometrics}"
        const val browser = "androidx.browser:browser:${Versions.androidBrowser}"
        const val cardView = "androidx.cardview:cardview:${Versions.androidCardView}"
        const val constraintLayout = "androidx.constraintlayout:constraintlayout:${Versions.androidConstraintLayout}"
        const val coreKtx = "androidx.core:core-ktx:${Versions.androidCoreKtx}"
        const val lifecycleCommonJava8 = "androidx.lifecycle:lifecycle-common-java8:${Versions.androidLifecycle}"
        const val lifecycleExtensions = "androidx.lifecycle:lifecycle-extensions:${Versions.androidLifecycle}"
        const val lifecycleRuntimeKtx = "androidx.lifecycle:lifecycle-runtime-ktx:${Versions.androidLifecycle}"
        const val material = "com.google.android.material:material:${Versions.androidMaterial}"
        const val multiDex = "androidx.multidex:multidex:${Versions.androidMultiDex}"
        const val palette = "androidx.palette:palette:${Versions.androidPalette}"
        const val preference = "androidx.preference:preference:${Versions.androidPreferences}"
        const val recyclerView = "androidx.recyclerview:recyclerview:${Versions.androidRecyclerView}"
        const val workManager = "androidx.work:work-runtime:${Versions.androidWorkManager}"
        const val workManagerKtx = "androidx.work:work-runtime-ktx:${Versions.androidWorkManager}"
    }

    object Database {
        const val requerySqlite = "io.requery:sqlite-android:${Versions.requerySqlite}"
        const val sqlite = "androidx.sqlite:sqlite:${Versions.androidSqlite}"
        const val storioCommon = "com.github.inorichi.storio:storio-common:${Versions.storioCommon}"
        const val storioSqlite = "com.github.inorichi.storio:storio-sqlite:${Versions.storioSqlite}"
    }

    object Disk {
        const val lrucache = "com.jakewharton:disklrucache:${Versions.diskLruCache}"
        const val unifile = "com.github.inorichi:unifile:${Versions.unifile}"
    }

    object Google {
        const val firebase = "com.google.firebase:firebase-core:${Versions.firebaseCore}"
        const val playServices = "com.google.android.gms:play-services-gcm:${Versions.googlePlayServices}"
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

    object IO {
        const val gson = "com.google.code.gson:gson:${Versions.gson}"
        const val kotson = "com.github.salomonbrys.kotson:kotson:${Versions.kotson}"
        const val okio = "com.squareup.okio:okio:${Versions.okio}"
    }

    object Kotlin {
        const val coroutines = "org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.kotlinCoroutines}"
        const val flowPreferences = "com.github.tfcporciuncula:flow-preferences:${Versions.flowPreferences}"
        const val stdLib = "org.jetbrains.kotlin:kotlin-stdlib:${Versions.kotlin}"
    }

    object Navigation {
        const val conductor = "com.bluelinelabs:conductor:${Versions.conductor}"
        const val conductorSupport = "com.bluelinelabs:conductor-support:${Versions.conductor}"
        const val conductorSupportPreferences = "com.github.inorichi:conductor-support-preference:a32c357"
        const val nucleus = "info.android15.nucleus:nucleus:${Versions.nucleus}"
        const val nucleusSupport = "info.android15.nucleus:nucleus-support-v7:${Versions.nucleus}"
    }

    object Network {
        const val chucker = "com.github.ChuckerTeam.Chucker:library:${Versions.chucker}"
        const val chuckerNoOp = "com.github.ChuckerTeam.Chucker:library-no-op:${Versions.chucker}"
        const val conscrypt = "org.conscrypt:conscrypt-android:${Versions.conscrypt}"
        const val okhttp = "com.squareup.okhttp3:okhttp:${Versions.okhttp}"
        const val okhttpDns = "com.squareup.okhttp3:okhttp-dnsoverhttps:${Versions.okhttp}"
        const val okhttpLoggingInterceptor = "com.squareup.okhttp3:logging-interceptor:${Versions.okhttp}"
        const val retrofit = "com.squareup.retrofit2:retrofit:${Versions.retrofit}"
        const val retrofitGsonConverter = "com.squareup.retrofit2:converter-gson:${Versions.retrofit}"
    }

    object Parsing {
        const val duktape = "com.squareup.duktape:duktape-android:${Versions.duktape}"
        const val jsoup = "org.jsoup:jsoup:${Versions.jsoup}"
    }

    object Rx {
        const val android = "io.reactivex:rxandroid:${Versions.rxAndroid}"
        const val bindingAppcompat = "com.jakewharton.rxbinding:rxbinding-appcompat-v7-kotlin:${Versions.rxBinding}"
        const val bindingKotlin = "com.jakewharton.rxbinding:rxbinding-kotlin:${Versions.rxBinding}"
        const val bindingRecycler = "com.jakewharton.rxbinding:rxbinding-recyclerview-v7-kotlin:${Versions.rxBinding}"
        const val bindingSupport = "com.jakewharton.rxbinding:rxbinding-support-v4-kotlin:${Versions.rxBinding}"
        const val java = "io.reactivex:rxjava:${Versions.rxJava}"
        const val network = "com.github.pwittchen:reactivenetwork:${Versions.reactiveNetwork}"
        const val preferences = "com.f2prateek.rx.preferences:rx-preferences:${Versions.rxPreferences}"
        const val relay = "com.jakewharton.rxrelay:rxrelay:${Versions.rxRelay}"
    }

    // Tests
    object Test {
        const val assertJCore = "org.assertj:assertj-core:${Versions.assertJ}"
        const val junit4 = "junit:junit:${Versions.junit }"
        const val mockito = "org.mockito:mockito-core:${Versions.mockito}"
        const val roboElectric = "org.robolectric:robolectric:${Versions.roboElectric}"
        const val roboElectricMultidex = "org.robolectric:shadows-multidex:${Versions.roboElectric}"
        const val roboElectricShadowPlayServices = "org.robolectric:shadows-play-services:${Versions.roboElectric}"
    }

    object UI {
        const val androidTagGroup = "com.github.kizitonwose:AndroidTagGroup:${Versions.tagGroup}"
        const val directionalPageView = "com.github.carlosesco:DirectionalViewPager:${Versions.directionalViewPager}"
        const val fastAdapter = "com.mikepenz:fastadapter:${Versions.fastAdapter}"
        const val fastAdapterBinding = "com.mikepenz:fastadapter-extensions-binding:${Versions.fastAdapter}"
        const val filePicker = "com.nononsenseapps:filepicker:${Versions.filePicker}"
        const val flexibleAdapter = "eu.davidea:flexible-adapter:${Versions.flexibleAdapter}"
        const val flexibleAdapterUi = "eu.davidea:flexible-adapter-ui:${Versions.flexibleAdapterUi}"
        const val loadingButton = "br.com.simplepass:loading-button-android:${Versions.loadingButton}"
        const val materalDesignDimens = "com.dmitrymalkovich.android:material-design-dimens:${Versions.materialDesignDimens}"
        const val materialDialogsCore = "com.afollestad.material-dialogs:core:${Versions.materialDialogs}"
        const val materialDialogsInput = "com.afollestad.material-dialogs:input:${Versions.materialDialogs}"
        const val photoView = "com.github.chrisbanes:PhotoView:${Versions.photoView}"
        const val slice = "com.github.mthli:Slice:${Versions.slice}"
        const val subsamplingScaleImageView = "com.github.inorichi:subsampling-scale-image-view:${Versions.subsamplingImageScale}"
        const val systemUiHelper = "me.zhanghai.android.systemuihelper:library:${Versions.systemUiHelper}"
        const val tapTargetView = "com.getkeepsafe.taptargetview:taptargetview:${Versions.tapTargetView}"
        const val viewStatePager = "com.nightlynexus.viewstatepageradapter:viewstatepageradapter:${Versions.viewStatePagerAdapter}"
        const val viewToolTip = "com.github.florent37:viewtooltip:${Versions.viewToolTip}"
    }

    object Util {
        const val aboutLibraries = "com.mikepenz:aboutlibraries:${Versions.aboutLibraries}"
        const val acra = "ch.acra:acra:${Versions.acra}"
        const val changelog = "com.github.gabrielemariotti.changeloglib:changelog:${Versions.changelog}"
        const val injekt = "com.github.inorichi.injekt:injekt-core:${Versions.injekt}"
        const val junrar = "com.github.inorichi:junrar-android:${Versions.junrar}"
        const val stringSimilarity = "info.debatty:java-string-similarity:${Versions.stringSimilarity}"
        const val timber = "com.jakewharton.timber:timber:${Versions.timber}"

    }
}

object Versions {
    const val aboutLibraries = "8.3.0"
    const val acra = "4.9.2"
    const val androidAnnotations = "1.1.0"
    const val androidAppCompat = "1.1.0"
    const val androidBiometrics= "1.0.1"
    const val androidBrowser= "1.2.0"
    const val androidCardView= "1.0.0"
    const val androidConstraintLayout= "1.1.3"
    const val androidCoreKtx= "1.3.1"
    const val androidGradlePlugin = "4.0.1"
    const val androidLifecycle = "2.2.0"
    const val androidMaterial = "1.1.0"
    const val androidMultiDex= "2.0.1"
    const val androidPalette = "1.0.0"
    const val androidPreferences= "1.1.1"
    const val androidRecyclerView = "1.1.0"
    const val androidSqlite = "2.1.0"
    const val androidWorkManager = "2.4.0"
    const val assertJ= "3.12.2"
    const val changelog= "2.1.0"
    const val chucker = "3.2.0"
    const val coil = "0.11.0"
    const val conductor = "2.1.5"
    const val conscrypt = "2.4.0"
    const val directionalViewPager= "a844dbca0a"
    const val diskLruCache= "2.0.2"
    const val duktape = "1.3.0"
    const val fastAdapter = "5.0.0"
    const val filePicker= "2.5.2"
    const val firebaseCore= "17.4.4"
    const val flexibleAdapter= "5.1.0"
    const val flexibleAdapterUi= "1.0.0"
    const val flowPreferences = "1.1.1"
    const val googlePlayServices= "17.0.0"
    const val googleServices = "4.3.3"
    const val gradleVersions = "0.29.0"
    const val gson = "2.8.6"
    const val hyperion = "0.9.27"
    const val injekt= "65b0440"
    const val jsoup = "1.13.1"
    const val junit= "4.13"
    const val junrar = "634c1f5"
    const val kotlin = "1.3.72"
    const val kotlinCoroutines = "1.3.5"
    const val kotson = "2.5.0"
    const val ktlint = "9.3.0"
    const val loadingButton= "2.2.0"
    const val materialDesignDimens= "1.4"
    const val materialDialogs= "3.1.1"
    const val mockito = "1.10.19"
    const val nucleus = "3.0.0"
    const val okhttp = "4.8.1"
    const val okio = "2.6.0"
    const val photoView= "2.3.0"
    const val reactiveNetwork = "0.13.0"
    const val requerySqlite= "3.31.0"
    const val retrofit = "2.7.2"
    const val roboElectric = "3.1.4"
    const val rxAndroid = "1.2.1"
    const val rxBinding = "1.0.1"
    const val rxJava= "1.3.8"
    const val rxPreferences = "1.0.2"
    const val rxRelay = "1.2.0"
    const val slice = "v1.2"
    const val storioCommon = "8be19de@aar"
    const val storioSqlite = "8be19de@aar"
    const val stringSimilarity= "1.2.1"
    const val subsamplingImageScale = "ac0dae7"
    const val systemUiHelper= "1.0.0"
    const val tagGroup= "1.6.0"
    const val tapTargetView= "1.13.0"
    const val timber = "4.7.1"
    const val unifile= "e9ee588"
    const val viewStatePagerAdapter= "1.1.0"
    const val viewToolTip= "1.2.2"
}

