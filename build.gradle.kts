plugins {
    id(Plugins.kotlinter.name) version Plugins.kotlinter.version
    id(Plugins.gradleVersions.name) version Plugins.gradleVersions.version
    id(Plugins.jetbrainsKotlin) version Versions.kotlin apply false
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
    }
}

buildscript {
    dependencies {
        classpath(LegacyPluginClassPath.fireBaseCrashlytics)
        classpath("com.android.tools.build:gradle:7.0.1")
        classpath(LegacyPluginClassPath.googleServices)
        classpath(LegacyPluginClassPath.kotlinPlugin)
        classpath("com.google.android.gms:oss-licenses-plugin:0.10.4")
        classpath(LegacyPluginClassPath.kotlinSerializations)
    }
    repositories {
        gradlePluginPortal()
        google()
        jcenter()
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
