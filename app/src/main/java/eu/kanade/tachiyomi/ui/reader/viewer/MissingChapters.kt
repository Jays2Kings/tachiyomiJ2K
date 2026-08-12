package eu.kanade.tachiyomi.ui.reader.viewer

import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import kotlin.math.floor

private val pattern = Regex("""\d+""")

fun hasMissingChapters(
    higherReaderChapter: ReaderChapter?,
    lowerReaderChapter: ReaderChapter?,
): Boolean {
    if (higherReaderChapter == null || lowerReaderChapter == null) return false
    return hasMissingChapters(higherReaderChapter.chapter, lowerReaderChapter.chapter)
}

fun hasMissingChapters(
    higherChapter: Chapter?,
    lowerChapter: Chapter?,
): Boolean {
    if (higherChapter == null || lowerChapter == null) return false
    // Check if name contains a number that is potential chapter number
    if (!pattern.containsMatchIn(higherChapter.name) || !pattern.containsMatchIn(lowerChapter.name)) return false
    // Check if potential chapter number was recognized as chapter number
    if (!higherChapter.isRecognizedNumber || !lowerChapter.isRecognizedNumber) return false
    return hasMissingChapters(higherChapter.chapter_number, lowerChapter.chapter_number)
}

fun hasMissingChapters(
    higherChapterNumber: Float,
    lowerChapterNumber: Float,
): Boolean {
    if (higherChapterNumber < 0f || lowerChapterNumber < 0f) return false
    return calculateChapterDifference(higherChapterNumber, lowerChapterNumber) > 0f
}

fun calculateChapterDifference(
    higherReaderChapter: ReaderChapter?,
    lowerReaderChapter: ReaderChapter?,
): Float {
    if (higherReaderChapter == null || lowerReaderChapter == null) return 0f
    return calculateChapterDifference(higherReaderChapter.chapter, lowerReaderChapter.chapter)
}

fun calculateChapterDifference(
    higherChapter: Chapter?,
    lowerChapter: Chapter?,
): Float {
    if (higherChapter == null || lowerChapter == null) return 0f
    // Check if name contains a number that is potential chapter number
    if (!pattern.containsMatchIn(higherChapter.name) || !pattern.containsMatchIn(lowerChapter.name)) return 0f
    // Check if potential chapter number was recognized as chapter number
    if (!higherChapter.isRecognizedNumber || !lowerChapter.isRecognizedNumber) return 0f
    return calculateChapterDifference(higherChapter.chapter_number, lowerChapter.chapter_number)
}

fun calculateChapterDifference(
    higherChapterNumber: Float,
    lowerChapterNumber: Float,
): Float {
    if (higherChapterNumber < 0f || lowerChapterNumber < 0f) return 0f
    return floor(higherChapterNumber) - floor(lowerChapterNumber) - 1f
}

/**
 * True if [candidateChapter] looks like a mis-numbered bonus/extra chapter rather than a genuine
 * gap in the main numbering: [prevChapter] and [nextChapter] are themselves (near-)consecutive,
 * but [candidateChapter] sits far outside that pair's number range. Sources label these chapters
 * inconsistently (e.g. "ex - Bonus Chapter 38" between chapters 174 and 175), so this only catches
 * a single stray chapter wedged between two chapters that are truly adjacent to each other.
 */
fun isChapterNumberOutlier(
    prevChapter: Chapter,
    candidateChapter: Chapter,
    nextChapter: Chapter,
): Boolean {
    if (!prevChapter.isRecognizedNumber || !candidateChapter.isRecognizedNumber || !nextChapter.isRecognizedNumber) {
        return false
    }
    val outerHigher = maxOf(prevChapter.chapter_number, nextChapter.chapter_number)
    val outerLower = minOf(prevChapter.chapter_number, nextChapter.chapter_number)
    // prevChapter and nextChapter must themselves be (near-)consecutive for candidateChapter to be
    // considered a stray outlier rather than part of a genuinely large gap.
    if (floor(outerHigher) - floor(outerLower) > 1f) return false
    return candidateChapter.chapter_number < outerLower - 1f || candidateChapter.chapter_number > outerHigher + 1f
}
