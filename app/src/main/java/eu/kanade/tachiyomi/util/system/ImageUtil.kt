package eu.kanade.tachiyomi.util.system

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.alpha
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.core.graphics.scale
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.R
import tachiyomi.decoder.Format
import tachiyomi.decoder.ImageDecoder
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URLConnection
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ImageUtil {

    fun isImage(name: String, openStream: (() -> InputStream)? = null): Boolean {
        val contentType = try {
            URLConnection.guessContentTypeFromName(name)
        } catch (e: Exception) {
            null
        } ?: openStream?.let { findImageType(it)?.mime }
        return contentType?.startsWith("image/") ?: false
    }

    fun findImageType(openStream: () -> InputStream): ImageType? {
        return openStream().use { findImageType(it) }
    }

    fun findImageType(stream: InputStream): ImageType? {
        return try {
            when (getImageType(stream)?.format) {
                Format.Avif -> ImageType.AVIF
                Format.Gif -> ImageType.GIF
                Format.Heif -> ImageType.HEIF
                Format.Jpeg -> ImageType.JPEG
                Format.Jxl -> ImageType.JXL
                Format.Png -> ImageType.PNG
                Format.Webp -> ImageType.WEBP
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun resizeBitMapDrawable(drawable: Drawable, resources: Resources?, size: Int): Drawable? {
        val b = (drawable as? BitmapDrawable)?.bitmap
        val bitmapResized: Bitmap? = if (b != null) {
            Bitmap.createScaledBitmap(b, size, size, false)
        } else {
            null
        }
        return if (bitmapResized != null) {
            BitmapDrawable(resources, bitmapResized)
        } else {
            null
        }
    }

    fun getExtensionFromMimeType(mime: String?): String {
        return MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            ?: SUPPLEMENTARY_MIMETYPE_MAPPING[mime]
            ?: "jpg"
    }

    fun isAnimatedAndSupported(stream: InputStream): Boolean {
        try {
            val type = getImageType(stream) ?: return false
            return when (type.format) {
                Format.Gif -> true
                // Coil supports animated WebP on Android 9.0+
                // https://coil-kt.github.io/coil/getting_started/#supported-image-formats
                Format.Webp -> type.isAnimated && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                else -> false
            }
        } catch (_: Exception) {
        }
        return false
    }

    enum class ImageType(val mime: String, val extension: String) {
        AVIF("image/avif", "avif"),
        GIF("image/gif", "gif"),
        HEIF("image/heif", "heif"),
        JPEG("image/jpeg", "jpg"),

        JXL("image/jxl", "jxl"),
        PNG("image/png", "png"),
        WEBP("image/webp", "webp"),
    }

    private fun getImageType(stream: InputStream): tachiyomi.decoder.ImageType? {
        val bytes = ByteArray(32)

        val length = if (stream.markSupported()) {
            stream.mark(bytes.size)
            stream.read(bytes, 0, bytes.size).also { stream.reset() }
        } else {
            stream.read(bytes, 0, bytes.size)
        }

        if (length == -1) {
            return null
        }

        return ImageDecoder.findType(bytes)
    }

    fun autoSetBackground(image: Bitmap?, alwaysUseWhite: Boolean, context: Context): Drawable {
        val backgroundColor = if (alwaysUseWhite) {
            Color.WHITE
        } else {
            context.getResourceColor(R.attr.readerBackground)
        }
        if (image == null) return ColorDrawable(backgroundColor)
        if (image.width < 50 || image.height < 50) {
            return ColorDrawable(backgroundColor)
        }
        val top = 5
        val bot = image.height - 5
        val left = (image.width * 0.0275).toInt()
        val right = image.width - left
        val midX = image.width / 2
        val midY = image.height / 2
        val offsetX = (image.width * 0.01).toInt()
        val topLeftIsDark = image.getPixel(left, top).isDark
        val topRightIsDark = image.getPixel(right, top).isDark
        val midLeftIsDark = image.getPixel(left, midY).isDark
        val midRightIsDark = image.getPixel(right, midY).isDark
        val topMidIsDark = image.getPixel(midX, top).isDark
        val botLeftIsDark = image.getPixel(left, bot).isDark
        val botRightIsDark = image.getPixel(right, bot).isDark

        var darkBG = (topLeftIsDark && (botLeftIsDark || botRightIsDark || topRightIsDark || midLeftIsDark || topMidIsDark)) ||
            (topRightIsDark && (botRightIsDark || botLeftIsDark || midRightIsDark || topMidIsDark))

        if (!image.getPixel(left, top).isWhite && pixelIsClose(image.getPixel(left, top), image.getPixel(midX, top)) &&
            !image.getPixel(right, top).isWhite && pixelIsClose(image.getPixel(right, top), image.getPixel(right, bot)) &&
            !image.getPixel(right, bot).isWhite && pixelIsClose(image.getPixel(right, bot), image.getPixel(midX, bot)) &&
            !image.getPixel(midX, top).isWhite && pixelIsClose(image.getPixel(midX, top), image.getPixel(right, top)) &&
            !image.getPixel(midX, bot).isWhite && pixelIsClose(image.getPixel(midX, bot), image.getPixel(left, bot)) &&
            !image.getPixel(left, bot).isWhite && pixelIsClose(image.getPixel(left, bot), image.getPixel(left, top))
        ) {
            return ColorDrawable(image.getPixel(left, top))
        }

        if (image.getPixel(left, top).isWhite.toInt() +
            image.getPixel(right, top).isWhite.toInt() +
            image.getPixel(left, bot).isWhite.toInt() +
            image.getPixel(right, bot).isWhite.toInt() > 2
        ) {
            darkBG = false
        }

        var blackPixel = when {
            topLeftIsDark -> image.getPixel(left, top)
            topRightIsDark -> image.getPixel(right, top)
            botLeftIsDark -> image.getPixel(left, bot)
            botRightIsDark -> image.getPixel(right, bot)
            else -> backgroundColor
        }

        var overallWhitePixels = 0
        var overallBlackPixels = 0
        var topBlackStreak = 0
        var topWhiteStreak = 0
        var botBlackStreak = 0
        var botWhiteStreak = 0
        outer@ for (x in intArrayOf(left, right, left - offsetX, right + offsetX)) {
            var whitePixelsStreak = 0
            var whitePixels = 0
            var blackPixelsStreak = 0
            var blackPixels = 0
            var blackStreak = false
            var whiteStrak = false
            val notOffset = x == left || x == right
            for ((index, y) in (0 until image.height step image.height / 25).withIndex()) {
                val pixel = image.getPixel(x, y)
                val pixelOff = image.getPixel(x + (if (x < image.width / 2) -offsetX else offsetX), y)
                if (pixel.isWhite) {
                    whitePixelsStreak++
                    whitePixels++
                    if (notOffset) {
                        overallWhitePixels++
                    }
                    if (whitePixelsStreak > 14) {
                        whiteStrak = true
                    }
                    if (whitePixelsStreak > 6 && whitePixelsStreak >= index - 1) {
                        topWhiteStreak = whitePixelsStreak
                    }
                } else {
                    whitePixelsStreak = 0
                    if (pixel.isDark && pixelOff.isDark) {
                        blackPixels++
                        if (notOffset) {
                            overallBlackPixels++
                        }
                        blackPixelsStreak++
                        if (blackPixelsStreak >= 14) {
                            blackStreak = true
                        }
                        continue
                    }
                }
                if (blackPixelsStreak > 6 && blackPixelsStreak >= index - 1) {
                    topBlackStreak = blackPixelsStreak
                }
                blackPixelsStreak = 0
            }
            if (blackPixelsStreak > 6) {
                botBlackStreak = blackPixelsStreak
            } else if (whitePixelsStreak > 6) {
                botWhiteStreak = whitePixelsStreak
            }
            when {
                blackPixels > 22 -> {
                    if (x == right || x == right + offsetX) {
                        blackPixel = when {
                            topRightIsDark -> image.getPixel(right, top)
                            botRightIsDark -> image.getPixel(right, bot)
                            else -> blackPixel
                        }
                    }
                    darkBG = true
                    overallWhitePixels = 0
                    break@outer
                }
                blackStreak -> {
                    darkBG = true
                    if (x == right || x == right + offsetX) {
                        blackPixel = when {
                            topRightIsDark -> image.getPixel(right, top)
                            botRightIsDark -> image.getPixel(right, bot)
                            else -> blackPixel
                        }
                    }
                    if (blackPixels > 18) {
                        overallWhitePixels = 0
                        break@outer
                    }
                }
                whiteStrak || whitePixels > 22 -> darkBG = false
            }
        }

        val topIsBlackStreak = topBlackStreak > topWhiteStreak
        val bottomIsBlackStreak = botBlackStreak > botWhiteStreak
        if (overallWhitePixels > 9 && overallWhitePixels > overallBlackPixels) {
            darkBG = false
        }
        if (topIsBlackStreak && bottomIsBlackStreak) {
            darkBG = true
        }
        val isLandscape = context.resources.configuration?.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (darkBG) {
            return if (!isLandscape && image.getPixel(left, bot).isWhite &&
                image.getPixel(right, bot).isWhite
            ) {
                GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(blackPixel, blackPixel, backgroundColor, backgroundColor),
                )
            } else if (!isLandscape && image.getPixel(left, top).isWhite &&
                image.getPixel(right, top).isWhite
            ) {
                GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(backgroundColor, backgroundColor, blackPixel, blackPixel),
                )
            } else {
                ColorDrawable(blackPixel)
            }
        }
        if (!isLandscape && (
            topIsBlackStreak || (
                topLeftIsDark && topRightIsDark &&
                    image.getPixel(left - offsetX, top).isDark && image.getPixel(right + offsetX, top).isDark &&
                    (topMidIsDark || overallBlackPixels > 9)
                )
            )
        ) {
            return GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(blackPixel, blackPixel, backgroundColor, backgroundColor),
            )
        } else if (!isLandscape && (
            bottomIsBlackStreak || (
                botLeftIsDark && botRightIsDark &&
                    image.getPixel(left - offsetX, bot).isDark && image.getPixel(right + offsetX, bot).isDark &&
                    (image.getPixel(midX, bot).isDark || overallBlackPixels > 9)
                )
            )
        ) {
            return GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(backgroundColor, backgroundColor, blackPixel, blackPixel),
            )
        }
        return ColorDrawable(backgroundColor)
    }

    fun splitBitmap(
        imageBitmap: Bitmap,
        secondHalf: Boolean,
        progressCallback: ((Int) -> Unit)? = null,
    ): ByteArrayInputStream {
        val height = imageBitmap.height
        val width = imageBitmap.width
        val result = Bitmap.createBitmap(width / 2, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        progressCallback?.invoke(98)
        canvas.drawBitmap(imageBitmap, Rect(if (!secondHalf) 0 else width / 2, 0, if (secondHalf) width else width / 2, height), result.rect, null)
        progressCallback?.invoke(99)
        val output = ByteArrayOutputStream()
        result.compress(Bitmap.CompressFormat.JPEG, 100, output)
        progressCallback?.invoke(100)
        return ByteArrayInputStream(output.toByteArray())
    }

    /**
     * Check whether the image is wide (which we consider a double-page spread).
     *
     * @return true if the width is greater than the height
     */
    fun isWideImage(imageStream: BufferedInputStream): Boolean {
        val options = extractImageOptions(imageStream)
        imageStream.reset()
        return options.outWidth > options.outHeight
    }

    fun splitAndStackBitmap(
        imageStream: InputStream,
        rightSideOnTop: Boolean,
        hasMargins: Boolean,
        progressCallback: ((Int) -> Unit)? = null,
    ): ByteArrayInputStream {
        val imageBytes = imageStream.readBytes()
        val imageBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

        val height = imageBitmap.height
        val width = imageBitmap.width
        val gap = if (hasMargins) 15.dpToPx else 0
        val result = Bitmap.createBitmap(width / 2, height * 2 + gap, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.BLACK)
        progressCallback?.invoke(98)
        val upperPart = Rect(
            0,
            0,
            result.width,
            result.height / 2,
        )
        val lowerPart = Rect(
            0,
            result.height / 2 + gap,
            result.width,
            result.height,
        )
        canvas.drawBitmap(
            imageBitmap,
            Rect(
                if (!rightSideOnTop) 0 else width / 2,
                0,
                if (!rightSideOnTop) width / 2 else width,
                height,
            ),
            upperPart,
            null,
        )
        canvas.drawBitmap(
            imageBitmap,
            Rect(
                if (rightSideOnTop) 0 else width / 2,
                0,
                if (rightSideOnTop) width / 2 else width,
                height,
            ),
            lowerPart,
            null,
        )
        progressCallback?.invoke(99)
        val output = ByteArrayOutputStream()
        result.compress(Bitmap.CompressFormat.JPEG, 100, output)
        progressCallback?.invoke(100)
        return ByteArrayInputStream(output.toByteArray())
    }

    fun mergeBitmaps(
        iBitmap: Bitmap,
        iBitmap2: Bitmap,
        isLTR: Boolean,
        @ColorInt background: Int = Color.WHITE,
        hingeGap: Int = 0,
        context: Context? = null,
        progressCallback: ((Int) -> Unit)? = null,
    ): ByteArrayInputStream {
        var imageBitmap = iBitmap
        var imageBitmap2 = iBitmap2
        var height = imageBitmap.height
        var width = imageBitmap.width
        var height2 = imageBitmap2.height
        var width2 = imageBitmap2.width
        val maxHeight = max(height, height2)
        val maxWidth = max(width, width2)
        val adjustedHingeGap = context?.let {
            val fullHeight = (context as? Activity)?.window?.decorView?.height
                ?: context.resources.displayMetrics.heightPixels
            (maxHeight.toFloat() / fullHeight * hingeGap).toInt()
        } ?: hingeGap
        val result = Bitmap.createBitmap((maxWidth * 2) + adjustedHingeGap, maxHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(background)
        val widthAndHinge = maxWidth + adjustedHingeGap
        if (imageBitmap.height != maxHeight && imageBitmap.width != maxWidth) {
            val minRatio = min(maxHeight / height.toFloat(), maxWidth / width.toFloat())
            imageBitmap = imageBitmap.scale((width * minRatio).toInt(), (height * minRatio).toInt())
        }
        height = imageBitmap.height
        width = imageBitmap.width
        val upperPart = Rect(
            if (isLTR) max(maxWidth - width, 0) else widthAndHinge,
            (maxHeight - height) / 2,
            (if (isLTR) max(maxWidth - width, 0) else widthAndHinge) + width,
            height + (maxHeight - height) / 2,
        )
        canvas.drawBitmap(imageBitmap, imageBitmap.rect, upperPart, null)
        progressCallback?.invoke(98)
        if (imageBitmap2.height != maxHeight && imageBitmap2.width != maxWidth) {
            val minRatio = min(maxHeight / height2.toFloat(), maxWidth / width2.toFloat())
            imageBitmap2 =
                imageBitmap2.scale((width2 * minRatio).toInt(), (height2 * minRatio).toInt())
        }
        height2 = imageBitmap2.height
        width2 = imageBitmap2.width
        val bottomPart = Rect(
            if (!isLTR) max(maxWidth - width2, 0) else widthAndHinge,
            (maxHeight - height2) / 2,
            (if (!isLTR) max(maxWidth - width2, 0) else widthAndHinge) + width2,
            height2 + (maxHeight - height2) / 2,
        )
        canvas.drawBitmap(imageBitmap2, imageBitmap2.rect, bottomPart, null)
        progressCallback?.invoke(99)

        val output = ByteArrayOutputStream()
        result.compress(Bitmap.CompressFormat.JPEG, 100, output)
        progressCallback?.invoke(100)
        return ByteArrayInputStream(output.toByteArray())
    }

    fun padSingleImage(
        imageBitmap: Bitmap,
        isLTR: Boolean,
        atBeginning: Boolean?,
        @ColorInt background: Int,
        hingeGap: Int,
        context: Context,
        progressCallback: ((Int) -> Unit)? = null,
    ): ByteArrayInputStream {
        val height = imageBitmap.height
        val width = imageBitmap.width
        val isFullPageSpread = height < width
        val fullHeight = (context as? Activity)?.window?.decorView?.height
            ?: context.resources.displayMetrics.heightPixels
        val adjustedHingeGap = (height.toFloat() / fullHeight * hingeGap).toInt()
        val result = Bitmap.createBitmap(
            (if (isFullPageSpread) width else (width * 2)) + adjustedHingeGap,
            height,
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(result)
        canvas.drawColor(background)
        if (isFullPageSpread) {
            val leftPart = Rect(0, 0, width / 2, height)
            canvas.drawBitmap(imageBitmap, imageBitmap.rect.also { it.right /= 2 }, leftPart, null)
            progressCallback?.invoke(98)
            val rightPart = Rect(
                width / 2 + adjustedHingeGap,
                0,
                width + adjustedHingeGap,
                height,
            )
            canvas.drawBitmap(imageBitmap, imageBitmap.rect.also { it.left = width / 2 }, rightPart, null)
        } else {
            val placeOnLeft = if (atBeginning == null) true else isLTR.xor(atBeginning)
            val upperPart = Rect(
                if (placeOnLeft) 0 else width + adjustedHingeGap,
                0,
                (if (placeOnLeft) 0 else width + adjustedHingeGap) + width,
                height,
            )
            canvas.drawBitmap(imageBitmap, imageBitmap.rect, upperPart, null)
        }
        progressCallback?.invoke(99)
        val output = ByteArrayOutputStream()
        result.compress(Bitmap.CompressFormat.JPEG, 100, output)
        progressCallback?.invoke(100)
        return ByteArrayInputStream(output.toByteArray())
    }

    /**
     * Check whether the image is considered a tall image.
     *
     * @return true if the height:width ratio is greater than 3.
     */
    private fun isTallImage(imageStream: InputStream): Boolean {
        val options = extractImageOptions(imageStream, false)
        return (options.outHeight / options.outWidth) > 3
    }

    /**
     * Splits tall images to improve performance of reader
     */
    fun splitTallImage(imageFile: UniFile, imageFilePath: String): Boolean {
        if (isAnimatedAndSupported(imageFile.openInputStream()) || !isTallImage(imageFile.openInputStream())) {
            return true
        }

        val bitmapRegionDecoder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            BitmapRegionDecoder.newInstance(imageFile.openInputStream())
        } else {
            @Suppress("DEPRECATION")
            BitmapRegionDecoder.newInstance(imageFile.openInputStream(), false)
        }

        if (bitmapRegionDecoder == null) {
            Timber.d("Failed to create new instance of BitmapRegionDecoder")
            return false
        }

        val options = extractImageOptions(imageFile.openInputStream(), resetAfterExtraction = false).apply {
            inJustDecodeBounds = false
        }
        val imageWidth = options.outWidth

        val splitDataList = options.splitData

        return try {
            splitDataList.forEach { splitData ->
                val splitPath = splitImagePath(imageFilePath, splitData.index)

                val region = Rect(0, splitData.topOffset, imageWidth, splitData.bottomOffset)

                FileOutputStream(splitPath).use { outputStream ->
                    val splitBitmap = bitmapRegionDecoder.decodeRegion(region, options)
                    splitBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                    splitBitmap.recycle()
                }
            }
            imageFile.delete()
            true
        } catch (e: Exception) {
            // Image splits were not successfully saved so delete them and keep the original image
            splitDataList
                .map { splitImagePath(imageFilePath, it.index) }
                .forEach { File(it).delete() }
            Timber.e(e)
            false
        } finally {
            bitmapRegionDecoder.recycle()
        }
    }

    private fun splitImagePath(imageFilePath: String, index: Int) =
        imageFilePath.substringBeforeLast(".") + "__${"%03d".format(index + 1)}.jpg"

    private val BitmapFactory.Options.splitData
        get(): List<SplitData> {
            val imageHeight = outHeight
            val imageWidth = outWidth

            val optimalImageHeight = displayMaxHeightInPx * 2

            // -1 so it doesn't try to split when imageHeight = optimalImageHeight
            val partCount = (imageHeight - 1) / optimalImageHeight + 1
            val optimalSplitHeight = imageHeight / partCount

            return mutableListOf<SplitData>().apply {
                val range = 0 until partCount
                for (index in range) {
                    // Only continue if the list is empty or there is image remaining
                    if (isNotEmpty() && imageHeight <= last().bottomOffset) break

                    val topOffset = index * optimalSplitHeight
                    var splitHeight = min(optimalSplitHeight, imageHeight - topOffset)

                    if (index == range.last) {
                        val remainingHeight = imageHeight - (topOffset + splitHeight)
                        splitHeight += remainingHeight
                    }

                    add(SplitData(index, topOffset, splitHeight, imageWidth))
                }
            }
        }

    data class SplitData(
        val index: Int,
        val topOffset: Int,
        val splitHeight: Int,
        val splitWidth: Int,
    ) {
        val bottomOffset = topOffset + splitHeight
    }

    private val Bitmap.rect: Rect
        get() = Rect(0, 0, width, height)

    private fun pixelIsClose(color1: Int, color2: Int): Boolean {
        return abs(color1.red - color2.red) < 30 &&
            abs(color1.green - color2.green) < 30 &&
            abs(color1.blue - color2.blue) < 30
    }

    /**
     * Returns if this bitmap matches what would be (if rightSide param is true)
     * the single left side page, or the second page to read in an RTL book, first in an LTR book.
     *
     * @return An int based on confidence, 0 meaning not padded, 1 meaning barely padded,
     * 2 meaning likely padded, 3 meaining definitely padded
     * @param rightSide: When true, check if it's a single left side page, else right side
     */
    fun Bitmap.isPagePadded(rightSide: Boolean): Int {
        val booleans = listOf(true, false)
        return when {
            booleans.any { isSidePadded(!rightSide, checkWhite = it) > 1 } -> 0
            booleans.any {
                when (isSidePadded(rightSide, checkWhite = it)) {
                    2 -> true
                    1 -> isSideLonger(rightSide, checkWhite = it)
                    else -> false
                }
            } -> 3
            booleans.any { isSideLonger(rightSide, checkWhite = it) } -> 2
            booleans.any { isOneSideMorePadded(rightSide, checkWhite = it) } -> 1
            else -> 0
        }
    }

    /**
     * Returns if one side has a vertical padding and the other side does not,
     * 2 for def, 1 for maybe, 0 if not at all
     */
    private fun Bitmap.isSidePadded(rightSide: Boolean, checkWhite: Boolean, halfCheck: Boolean = false): Int {
        val left = (width * 0.0275).toInt()
        val right = width - left
        val paddedSide = if (rightSide) right else left
        val unPaddedSide = if (!rightSide) right else left
        val paddedCount = (1 until 50).count {
            // if all of a side is padded (the left page usually has a white padding on the right when scanned)
            getPixel(paddedSide, (height * (it / 50f)).roundToInt()).isWhiteOrDark(checkWhite)
        }
        val isNotFullyUnPadded = !(1 until 50).all {
            // and if all of the other side isn't padded
            getPixel(unPaddedSide, (height * (it / 50f)).roundToInt()).isWhiteOrDark(checkWhite)
        }
        return if (isNotFullyUnPadded) {
            if (paddedCount == 49) 2 else if (paddedCount >= (if (halfCheck) 25 else 47)) 1 else 0
        } else {
            0
        }
    }

    /** Returns if one side has a longer streak (of white or black) than the other */
    private fun Bitmap.isSideLonger(rightSide: Boolean, checkWhite: Boolean): Boolean {
        if (isSidePadded(rightSide, checkWhite, true) == 0) return false
        val left = (width * 0.0275).toInt()
        val right = width - left
        val step = 70
        val list = listOf((1 until step), (1 until step).reversed())
        val streakFunc: (Int) -> Int = { side ->
            list.maxOf { range ->
                var count = 0
                for (it in range) {
                    val pixel = getPixel(side, (height * (it / step.toFloat())).roundToInt())
                    if (pixel.isWhiteOrDark(checkWhite)) ++count else return@maxOf count
                }
                count
            }
        }
        val paddedSide = if (rightSide) right else left
        val unPaddedSide = if (!rightSide) right else left
        return streakFunc(paddedSide) > streakFunc(unPaddedSide)
    }

    /** Returns if one side is more horizontally padded than the other */
    private fun Bitmap.isOneSideMorePadded(rightSide: Boolean, checkWhite: Boolean): Boolean {
        val middle = (height * 0.475).roundToInt()
        val middle2 = (height * 0.525).roundToInt()
        val widthFactor = max(1, (width / 400f).roundToInt())
        val paddedSide: (Int) -> Int = { if (!rightSide) width - it * widthFactor else it * widthFactor }
        val unPaddedSide: (Int) -> Int = { if (rightSide) width - it * widthFactor else it * widthFactor }
        return run stop@{
            (1 until 37).any {
                if (!getPixel(paddedSide(it), middle).isWhiteOrDark(checkWhite)) return@stop false
                if (!getPixel(paddedSide(it), middle2).isWhiteOrDark(checkWhite)) return@stop false
                !getPixel(unPaddedSide(it), middle).isWhiteOrDark(checkWhite) ||
                    !getPixel(unPaddedSide(it), middle2).isWhiteOrDark(checkWhite)
            }
        }
    }

    private fun Int.isWhiteOrDark(checkWhite: Boolean): Boolean =
        if (checkWhite) isWhite else isDark

    private val Int.isWhite: Boolean
        get() = red + blue + green > 740

    private val Int.isDark: Boolean
        get() {
            val bgArray = FloatArray(3)
            ColorUtils.colorToHSL(this, bgArray)
            return red < 40 && blue < 40 && green < 40 && alpha > 200 && bgArray[1] <= 0.2f
        }

    fun getPercentOfColor(
        @ColorInt color: Int,
        @ColorInt colorFrom: Int,
        @ColorInt colorTo: Int,
    ): Float {
        val reds = arrayOf(Color.red(color), Color.red(colorFrom), Color.red(colorTo))
        val blues = arrayOf(Color.blue(color), Color.blue(colorFrom), Color.blue(colorTo))
        val greens = arrayOf(Color.green(color), Color.green(colorFrom), Color.green(colorTo))
        val alphas = arrayOf(Color.alpha(color), Color.alpha(colorFrom), Color.alpha(colorTo))
        val rPercent = (reds[0] - reds[1]).toFloat() / (reds[2] - reds[1]).toFloat()
        val bPercent = (blues[0] - blues[1]).toFloat() / (blues[2] - blues[1]).toFloat()
        val gPercent = (greens[0] - greens[1]).toFloat() / (greens[2] - greens[1]).toFloat()
        val aPercent = (alphas[0] - alphas[1]).toFloat() / (alphas[2] - alphas[1]).toFloat()
        return arrayOf(
            rPercent.takeIf { reds[2] != reds[1] },
            bPercent.takeIf { blues[2] != blues[1] },
            gPercent.takeIf { greens[2] != greens[1] },
            aPercent.takeIf { alphas[2] != alphas[1] },
        ).filterNotNull().average().toFloat().takeIf { it in 0f..1f } ?: 0f
    }

    /**
     * Used to check an image's dimensions without loading it in the memory.
     */
    private fun extractImageOptions(
        imageStream: InputStream,
        resetAfterExtraction: Boolean = true,
    ): BitmapFactory.Options {
        imageStream.mark(imageStream.available() + 1)

        val imageBytes = imageStream.readBytes()
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)
        if (resetAfterExtraction) imageStream.reset()
        return options
    }

    // Android doesn't include some mappings
    private val SUPPLEMENTARY_MIMETYPE_MAPPING = mapOf(
        // https://issuetracker.google.com/issues/182703810
        "image/jxl" to "jxl",
    )
}
