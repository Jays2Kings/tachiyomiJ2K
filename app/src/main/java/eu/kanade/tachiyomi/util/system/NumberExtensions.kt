package eu.kanade.tachiyomi.util.system

import kotlin.math.roundToLong

fun Double.roundToTwoDecimal(): Double {
    return (this * 100.0).roundToLong() / 100.0
}
