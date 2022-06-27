package eu.kanade.tachiyomi.util.lang

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.SpannedString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.TextAppearanceSpan
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import androidx.core.text.inSpans
import androidx.core.text.scale
import androidx.core.text.superscript
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.getResourceColor
import net.greypanther.natsort.CaseInsensitiveSimpleNaturalComparator
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale
import kotlin.math.floor

/**
 * Replaces the given string to have at most [count] characters using [replacement] at its end.
 * If [replacement] is longer than [count] an exception will be thrown when `length > count`.
 */
fun String.chop(count: Int, replacement: String = "…"): String {
    return if (length > count) {
        take(count - replacement.length) + replacement
    } else {
        this
    }
}

fun String.chopByWords(count: Int): String {
    return if (length > count) {
        val splitWords = split(" ")
        val iterator = splitWords.iterator()
        var newString = iterator.next()
        return if (newString.length > count) {
            chop(count)
        } else {
            var next = iterator.next()
            while ("$newString $next".length <= count) {
                newString = "$newString $next"
                next = iterator.next()
            }
            newString
        }
    } else {
        this
    }
}

fun String.removeArticles(): String {
    return when {
        startsWith("a ", true) -> substring(2)
        startsWith("an ", true) -> substring(3)
        startsWith("the ", true) -> substring(4)
        else -> this
    }
}

val String.sqLite: String
    get() = replace("'", "''")

fun String.trimOrNull(): String? {
    val trimmed = trim()
    return if (trimmed.isBlank()) null else trimmed
}

/**
 * Replaces the given string to have at most [count] characters using [replacement] near the center.
 * If [replacement] is longer than [count] an exception will be thrown when `length > count`.
 */
fun String.truncateCenter(count: Int, replacement: String = "..."): String {
    if (length <= count) {
        return this
    }

    val pieceLength: Int = floor((count - replacement.length).div(2.0)).toInt()

    return "${take(pieceLength)}$replacement${takeLast(pieceLength)}"
}

fun String.capitalizeWords(): String {
    val firstReplace = split(" ").joinToString(" ") {
        it.replaceFirstChar { text ->
            text.titlecase(Locale.getDefault())
        }
    }
    return firstReplace.split("-").joinToString("-") {
        it.replaceFirstChar { text ->
            text.titlecase(Locale.getDefault())
        }
    }
}

/**
 * Case-insensitive natural comparator for strings.
 */
fun String.compareToCaseInsensitiveNaturalOrder(other: String): Int {
    val comparator = CaseInsensitiveSimpleNaturalComparator.getInstance<String>()
    return comparator.compare(this, other)
}

fun CharSequence.tintText(@ColorInt color: Int): Spanned {
    val s = SpannableString(this)
    s.setSpan(ForegroundColorSpan(color), 0, this.length, 0)
    return s
}

fun String.highlightText(highlight: String, @ColorInt color: Int): Spanned {
    val wordToSpan: Spannable = SpannableString(this)
    if (highlight.isBlank()) return wordToSpan
    indexesOf(highlight).forEach {
        wordToSpan.setSpan(BackgroundColorSpan(color), it, it + highlight.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    return wordToSpan
}

fun String.asButton(context: Context, disabled: Boolean = false): SpannedString {
    return buildSpannedString {
        val buttonSpan: SpannableStringBuilder.() -> Unit = {
            inSpans(
                TextAppearanceSpan(context, R.style.TextAppearance_Tachiyomi_Button),
            ) { append(this@asButton) }
        }
        if (disabled) {
            color(context.getColor(R.color.material_on_surface_disabled), buttonSpan)
        } else buttonSpan()
    }
}

fun String.indexesOf(substr: String, ignoreCase: Boolean = true): List<Int> {
    val list = mutableListOf<Int>()
    if (substr.isBlank()) return list

    var i = -1
    while (true) {
        i = indexOf(substr, i + 1, ignoreCase)
        when (i) {
            -1 -> return list
            else -> list.add(i)
        }
    }
}

fun String.withSubtitle(context: Context, @StringRes subtitleRes: Int) =
    withSubtitle(context, context.getString(subtitleRes))

fun String.withSubtitle(subtitle: Spanned): Spanned =
    SpannableStringBuilder(this + "\n").append(subtitle)

fun String.withSubtitle(context: Context, subtitle: String): Spanned {
    val spannable = SpannableStringBuilder(this + "\n" + subtitle)
    spannable.setSpan(
        ForegroundColorSpan(context.getResourceColor(android.R.attr.textColorSecondary)),
        this.length + 1,
        spannable.length,
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
    )
    return spannable
}

fun String.addBetaTag(context: Context, useSuperScript: Boolean = true): Spanned {
    val betaText = context.getString(R.string.beta)
    val colorS = context.getResourceColor(R.attr.colorSecondary)
    return buildSpannedString {
        append(this@addBetaTag)
        val buttonSpan: SpannableStringBuilder.() -> Unit = {
            bold { scale(0.75f) { color(colorS) { append(betaText) } } }
        }
        if (useSuperScript) superscript(buttonSpan) else buttonSpan()
    }
}

fun String.toNormalized(): String = replace("[`’]".toRegex(), "'")

fun String.getUrlWithoutDomain(): String {
    return try {
        val uri = URI(this.replace(" ", "%20"))
        var out = uri.path
        if (uri.query != null) {
            out += "?" + uri.query
        }
        if (uri.fragment != null) {
            out += "#" + uri.fragment
        }
        out
    } catch (e: URISyntaxException) {
        this
    }
}
