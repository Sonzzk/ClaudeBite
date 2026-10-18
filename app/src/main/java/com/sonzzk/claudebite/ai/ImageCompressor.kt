package com.sonzzk.claudebite.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Shrinks food photos before sending them to Claude.
 *
 * Image tokens grow with pixel count (roughly width x height / 750), so a 12-megapixel photo would
 * cost thousands of tokens. Scaling the long side to 1024 px keeps a photo around ~1,000 input
 * tokens while staying sharp enough to recognize food and judge portions.
 *
 * Call these off the main thread (decoding is slow).
 */
object ImageCompressor {

    /** Reads an image from a content/file [uri], fixes its rotation, shrinks and re-encodes it as JPEG. */
    fun fromUri(context: Context, uri: Uri, maxSide: Int = 1024, quality: Int = 80): ByteArray {
        val resolver = context.contentResolver
        fun open(): InputStream = resolver.openInputStream(uri) ?: throw IOException("Can't open image $uri")

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        val orientation = open().use { readOrientation(it) }
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSide)
        }
        val decoded = open().use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Unsupported image format")
        return encode(transform(decoded, maxSide, orientation), quality)
    }

    /** Same as [fromUri] for an image already in memory (e.g. from the camera). */
    fun fromBytes(bytes: ByteArray, maxSide: Int = 1024, quality: Int = 80): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val orientation = ByteArrayInputStream(bytes).use { readOrientation(it) }
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSide)
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: throw IOException("Unsupported image format")
        return encode(transform(decoded, maxSide, orientation), quality)
    }

    /**
     * Largest power-of-two sample size that still leaves the long side >= [maxSide].
     * Decoding at a smaller size first saves a lot of memory; the exact resize happens afterwards.
     */
    fun calculateInSampleSize(width: Int, height: Int, maxSide: Int): Int {
        var longSide = max(width, height)
        if (longSide <= 0 || maxSide <= 0) return 1
        var sample = 1
        while (longSide / 2 >= maxSide) {
            longSide /= 2
            sample *= 2
        }
        return sample
    }

    private fun readOrientation(stream: InputStream): Int = try {
        ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } catch (e: IOException) {
        ExifInterface.ORIENTATION_NORMAL
    }

    /** Scales so the long side is at most [maxSide] and applies the EXIF rotation/flip in one pass. */
    private fun transform(source: Bitmap, maxSide: Int, orientation: Int): Bitmap {
        val longSide = max(source.width, source.height)
        val scale = if (longSide > maxSide) maxSide.toFloat() / longSide else 1f
        val matrix = Matrix()
        if (scale < 1f) matrix.postScale(scale, scale)
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
        }
        if (matrix.isIdentity) return source
        val result = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (result != source) source.recycle()
        return result
    }

    private fun encode(bitmap: Bitmap, quality: Int): ByteArray {
        // JPEG has no transparency: paint transparent PNGs onto white instead of black.
        val opaque = if (bitmap.hasAlpha()) {
            val copy = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            Canvas(copy).apply {
                drawColor(Color.WHITE)
                drawBitmap(bitmap, 0f, 0f, null)
            }
            bitmap.recycle()
            copy
        } else {
            bitmap
        }
        val out = ByteArrayOutputStream()
        opaque.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), out)
        opaque.recycle()
        return out.toByteArray()
    }

    /** Rough input-token cost of an image of this size (for display/debugging). */
    fun estimateImageTokens(width: Int, height: Int): Int = (width.toLong() * height / 750.0).roundToInt()
}
