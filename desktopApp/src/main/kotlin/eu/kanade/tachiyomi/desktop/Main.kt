package eu.kanade.tachiyomi.desktop

import eu.kanade.tachiyomi.bootstrap.initializeDesktopAppBootstrap

fun main() {
    val contracts = initializeDesktopAppBootstrap()
    contracts.platformHttpClientFactory.newBuilder()
    println("TachiyomiJ2K desktop launcher (migration scaffold)")
}
