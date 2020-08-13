// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    repositories {
        gradlePluginPortal()
        google()
        jcenter()
    }
    dependencies {
        classpath(ClassPaths.androidGradlePlugin)
        classpath(ClassPaths.aboutLibraries)
        classpath(ClassPaths.googleServices)
        classpath(ClassPaths.kotlinExtensions)
        classpath(ClassPaths.kotlinPlugin)
        classpath(ClassPaths.gradleVersion)
        classpath(ClassPaths.ktlint)
    }
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
allprojects {
    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
        maven { setUrl("https://plugins.gradle.org/m2/") }
        jcenter()
    }
}


tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}