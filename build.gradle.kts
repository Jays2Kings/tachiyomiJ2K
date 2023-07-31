plugins {
    id(Plugins.kotlinter.name) version Plugins.kotlinter.version
    id(Plugins.gradleVersions.name) version Plugins.gradleVersions.version
    id(Plugins.jetbrainsKotlin) version AndroidVersions.kotlin apply false
}
allprojects {
    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
        maven { setUrl("https://plugins.gradle.org/m2/") }
    }
}

subprojects {
    apply(plugin = Plugins.kotlinter.name)

    kotlinter {
        experimentalRules = true

        // Doesn't play well with Android Studio
        disabledRules = arrayOf("experimental:argument-list-wrapping")
    }
}

buildscript {
    dependencies {
        classpath("com.android.tools.build:gradle:8.1.0")
        classpath("com.google.gms:google-services:4.3.15")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${AndroidVersions.kotlin}")
        classpath("com.google.android.gms:oss-licenses-plugin:0.10.6")
        classpath("org.jetbrains.kotlin:kotlin-serialization:${AndroidVersions.kotlin}")
        classpath("com.google.firebase:firebase-crashlytics-gradle:2.9.4")
    }
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

tasks.named("dependencyUpdates", com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask::class.java).configure {
    rejectVersionIf {
        isNonStable(candidate.version)
    }
    // optional parameters
    checkForGradleUpdate = true
    outputFormatter = "json"
    outputDir = "build/dependencyUpdates"
    reportfileName = "report"
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
