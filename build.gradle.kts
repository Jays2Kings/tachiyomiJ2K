plugins {
    id(Plugins.KtLint.name) version Plugins.KtLint.version
    id(Plugins.GradleVersions.name) version Plugins.GradleVersions.version
}
allprojects {
    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
        maven { setUrl("https://plugins.gradle.org/m2/") }
        jcenter()
    }
}

subprojects {
    apply(plugin = Plugins.KtLint.name)
    ktlint {
        debug.set(true)
        verbose.set(true)
        android.set(false)
        outputToConsole.set(true)
        ignoreFailures.set(false)
        enableExperimentalRules.set(false)
        filter {
            exclude("**/generated/**")
            include("**/kotlin/**")
        }
    }
}


buildscript {

    dependencies {
        classpath(LegacyPluginClassPath.androidGradlePlugin)
        classpath(LegacyPluginClassPath.googleServices)
        classpath(LegacyPluginClassPath.kotlinExtensions)
        classpath(LegacyPluginClassPath.kotlinPlugin)
        classpath(LegacyPluginClassPath.aboutLibraries)
    }
    repositories {
        gradlePluginPortal()
        google()
        jcenter()
    }
}


tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}