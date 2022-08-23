package eu.kanade.tachiyomi.ui.more.stats

import android.graphics.Color
import android.text.SpannableStringBuilder
import eu.kanade.tachiyomi.source.model.SManga
import java.util.concurrent.TimeUnit

object StatsHelper {

    private val STATUS_COLOR_LIST = arrayListOf(
        Color.parseColor("#3BAEEA"),
        Color.parseColor("#7BD555"),
        Color.parseColor("#F79A63"),
        Color.parseColor("#d29d2f"),
        Color.parseColor("#E85D75"),
        Color.parseColor("#F17575"),
        Color.parseColor("#5b5b5b"),
    )

    val STATUS_COLOR_MAP = hashMapOf(
        Pair(SManga.ONGOING, STATUS_COLOR_LIST[0]),
        Pair(SManga.COMPLETED, STATUS_COLOR_LIST[1]),
        Pair(SManga.LICENSED, STATUS_COLOR_LIST[2]),
        Pair(SManga.PUBLISHING_FINISHED, STATUS_COLOR_LIST[3]),
        Pair(SManga.CANCELLED, STATUS_COLOR_LIST[4]),
        Pair(SManga.ON_HIATUS, STATUS_COLOR_LIST[5]),
        Pair(SManga.UNKNOWN, STATUS_COLOR_LIST[6]),
    )

    // from 1 - 10
    val SCORE_COLOR_LIST = arrayListOf(
        Color.parseColor("#d2492d"),
        Color.parseColor("#d2642c"),
        Color.parseColor("#d2802e"),
        Color.parseColor("#d29d2f"),
        Color.parseColor("#d2b72e"),
        Color.parseColor("#d3d22e"),
        Color.parseColor("#b8d22c"),
        Color.parseColor("#9cd42e"),
        Color.parseColor("#81d12d"),
        Color.parseColor("#63d42e"),
    )

    val SCORE_COLOR_MAP = hashMapOf(
        Pair(1, SCORE_COLOR_LIST[0]),
        Pair(2, SCORE_COLOR_LIST[1]),
        Pair(3, SCORE_COLOR_LIST[2]),
        Pair(4, SCORE_COLOR_LIST[3]),
        Pair(5, SCORE_COLOR_LIST[4]),
        Pair(6, SCORE_COLOR_LIST[5]),
        Pair(7, SCORE_COLOR_LIST[6]),
        Pair(8, SCORE_COLOR_LIST[7]),
        Pair(9, SCORE_COLOR_LIST[8]),
        Pair(10, SCORE_COLOR_LIST[9]),
    )

    val PIE_CHART_COLOR_LIST = arrayListOf(
        Color.parseColor("#55e2cf"),
        Color.parseColor("#57aee2"),
        Color.parseColor("#5668e2"),
        Color.parseColor("#8a56e2"),
        Color.parseColor("#ce56e2"),
        Color.parseColor("#e256ae"),
        Color.parseColor("#e25768"),
        Color.parseColor("#e28956"),
        Color.parseColor("#e3cf56"),
        Color.parseColor("#aee256"),
        Color.parseColor("#68e257"),
        Color.parseColor("#56e28a"),
    )

    val STATS_LENGTH = arrayListOf(
        0 to 0,
        1 to 1,
        2 to 10,
        11 to 25,
        26 to 50,
        51 to 100,
        101 to 200,
        201 to null,
    )

    fun Long.getReadDuration(blankValue: String = "0"): String {
        val days = TimeUnit.MILLISECONDS.toDays(this)
        val hours = TimeUnit.MILLISECONDS.toHours(this) % 24
        val minutes = TimeUnit.MILLISECONDS.toMinutes(this) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(this) % 60

        return SpannableStringBuilder().apply {
            if (days != 0L) append("${days}d")
            if (hours != 0L) append("${hours}h")
            if (minutes != 0L && days == 0L) append("${minutes}min")
            if (seconds != 0L && days == 0L && hours == 0L) append("${seconds}s")
        }.replace(Regex("(\\D)(?=\\d)"), "$0 ").ifBlank { blankValue }
    }
}
