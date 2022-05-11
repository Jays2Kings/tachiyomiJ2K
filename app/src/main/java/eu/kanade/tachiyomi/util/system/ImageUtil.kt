package eu.kanade.tachiyomi.util.system

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import androidx.annotation.ColorInt
import eu.kanade.tachiyomi.R
import tachiyomi.decoder.Format
import tachiyomi.decoder.ImageDecoder
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URLConnection
import kotlin.math.abs
import kotlin.math.max

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
        try {
            return when (getImageType(stream)?.format) {
                Format.Avif -> ImageType.AVIF
                Format.Gif -> ImageType.GIF
                Format.Heif -> ImageType.HEIF
                Format.Jpeg -> ImageType.JPEG
//                Format.Jxl -> ImageType.JXL
                Format.Png -> ImageType.PNG
                Format.Webp -> ImageType.WEBP
                else -> null
            }
        } catch (e: Exception) {
        }
        return null
    }

    fun resizeBitMapDrawable(drawable: Drawable, resources: Resources?, size: Int): Drawable? {
        val b = (drawable as? BitmapDrawable)?.bitmap
        val bitmapResized: Bitmap? = if (b != null) {
            Bitmap.createScaledBitmap(b, size, size, false)
        } else {
            null
        }
        return if (bitmapResized != null) BitmapDrawable(resources, bitmapResized)
        else null
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
        } catch (e: Exception) {
        }
        return false
    }

    enum class ImageType(val mime: String, val extension: String) {
        AVIF("image/avif", "avif"),
        GIF("image/gif", "gif"),
        HEIF("image/heif", "heif"),
        JPEG("image/jpeg", "jpg"),

//        JXL("image/jxl", "jxl"),
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
        val backgroundColor = if (alwaysUseWhite) Color.WHITE else {
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
        val topLeftIsDark = isDark(image.getPixel(left, top))
        val topRightIsDark = isDark(image.getPixel(right, top))
        val midLeftIsDark = isDark(image.getPixel(left, midY))
        val midRightIsDark = isDark(image.getPixel(right, midY))
        val topMidIsDark = isDark(image.getPixel(midX, top))
        val botLeftIsDark = isDark(image.getPixel(left, bot))
        val botRightIsDark = isDark(image.getPixel(right, bot))

        var darkBG = (topLeftIsDark && (botLeftIsDark || botRightIsDark || topRightIsDark || midLeftIsDark || topMidIsDark)) ||
            (topRightIsDark && (botRightIsDark || botLeftIsDark || midRightIsDark || topMidIsDark))

        if (!isWhite(image.getPixel(left, top)) && pixelIsClose(image.getPixel(left, top), image.getPixel(midX, top)) &&
            !isWhite(image.getPixel(midX, top)) && pixelIsClose(image.getPixel(midX, top), image.getPixel(right, top)) &&
            !isWhite(image.getPixel(right, top)) && pixelIsClose(image.getPixel(right, top), image.getPixel(right, bot)) &&
            !isWhite(image.getPixel(right, bot)) && pixelIsClose(image.getPixel(right, bot), image.getPixel(midX, bot)) &&
            !isWhite(image.getPixel(midX, bot)) && pixelIsClose(image.getPixel(midX, bot), image.getPixel(left, bot)) &&
            !isWhite(image.getPixel(left, bot)) && pixelIsClose(image.getPixel(left, bot), image.getPixel(left, top))
        ) {
            return ColorDrawable(image.getPixel(left, top))
        }

        if (isWhite(image.getPixel(left, top)).toInt() +
            isWhite(image.getPixel(right, top)).toInt() +
            isWhite(image.getPixel(left, bot)).toInt() +
            isWhite(image.getPixel(right, bot)).toInt() > 2
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
                if (isWhite(pixel)) {
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
                    if (isDark(pixel) && isDark(pixelOff)) {
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
            return if (!isLandscape && isWhite(image.getPixel(left, bot)) && isWhite(image.getPixel(right, bot))) GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(blackPixel, blackPixel, backgroundColor, backgroundColor),
            )
            else if (!isLandscape && isWhite(image.getPixel(left, top)) && isWhite(image.getPixel(right, top))) GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(backgroundColor, backgroundColor, blackPixel, blackPixel),
            )
            else ColorDrawable(blackPixel)
        }
        if (!isLandscape && (
            topIsBlackStreak || (
                topLeftIsDark && topRightIsDark &&
                    isDark(image.getPixel(left - offsetX, top)) && isDark(image.getPixel(right + offsetX, top)) &&
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
                    isDark(image.getPixel(left - offsetX, bot)) && isDark(image.getPixel(right + offsetX, bot)) &&
                    (isDark(image.getPixel(midX, bot)) || overallBlackPixels > 9)
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

    /**
     * Check whether the image is a double-page spread
     * @return true if the width is greater than the height
     */
    fun isDoublePage(imageStream: InputStream): Boolean {
        imageStream.mark(imageStream.available() + 1)

        val imageBytes = imageStream.readBytes()

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)

        imageStream.reset()

        return options.outWidth > options.outHeight
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
        imageBitmap: Bitmap,
        imageBitmap2: Bitmap,
        isLTR: Boolean,
        @ColorInt background: Int = Color.WHITE,
        progressCallback: ((Int) -> Unit)? = null,
    ): ByteArrayInputStream {
        val height = imageBitmap.height
        val width = imageBitmap.width
        val height2 = imageBitmap2.height
        val width2 = imageBitmap2.width
        val maxHeight = max(height, height2)
        val result = Bitmap.createBitmap(width + width2, max(height, height2), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(background)
        val upperPart = Rect(
            if (isLTR) 0 else width2,
            (maxHeight - imageBitmap.height) / 2,
            (if (isLTR) 0 else width2) + imageBitmap.width,
            imageBitmap.height + (maxHeight - imageBitmap.height) / 2,
        )
        canvas.drawBitmap(imageBitmap, imageBitmap.rect, upperPart, null)
        progressCallback?.invoke(98)
        val bottomPart = Rect(
            if (!isLTR) 0 else width,
            (maxHeight - imageBitmap2.height) / 2,
            (if (!isLTR) 0 else width) + imageBitmap2.width,
            imageBitmap2.height + (maxHeight - imageBitmap2.height) / 2,
        )
        canvas.drawBitmap(imageBitmap2, imageBitmap2.rect, bottomPart, null)
        progressCallback?.invoke(99)

        val output = ByteArrayOutputStream()
        result.compress(Bitmap.CompressFormat.JPEG, 100, output)
        progressCallback?.invoke(100)
        return ByteArrayInputStream(output.toByteArray())
    }

    private val Bitmap.rect: Rect
        get() = Rect(0, 0, width, height)

    private fun isDark(color: Int): Boolean {
        return Color.red(color) < 40 && Color.blue(color) < 40 && Color.green(color) < 40 &&
            Color.alpha(color) > 200
    }

    fun isDarkish(color: Int): Boolean {
        return Color.red(color) < 80 && Color.blue(color) < 80 && Color.green(color) < 80 &&
            Color.alpha(color) > 150
    }

    private fun pixelIsClose(color1: Int, color2: Int): Boolean {
        return abs(Color.red(color1) - Color.red(color2)) < 30 &&
            abs(Color.green(color1) - Color.green(color2)) < 30 &&
            abs(Color.blue(color1) - Color.blue(color2)) < 30
    }

    private fun isWhite(color: Int): Boolean {
        return Color.red(color) + Color.blue(color) + Color.green(color) > 740
    }

    private fun ByteArray.compareWith(magic: ByteArray): Boolean {
        for (i in magic.indices) {
            if (this[i] != magic[i]) return false
        }
        return true
    }

    private fun charByteArrayOf(vararg bytes: Int): ByteArray {
        return ByteArray(bytes.size).apply {
            for (i in bytes.indices) {
                set(i, bytes[i].toByte())
            }
        }
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
    private fun extractImageOptions(imageStream: InputStream): BitmapFactory.Options {
        imageStream.mark(imageStream.available() + 1)

        val imageBytes = imageStream.readBytes()
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)
        return options
    }
}
