plugins {
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("eu.kanade.tachiyomi.desktop.MainKt")
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":shared:data"))
}
