package com.sushanth.poplauncher.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache

object IconUtils {

    private const val MONO_FG_COLOR = 0xFFF5F5F5.toInt()
    private const val MONO_BG_COLOR = 0xFF262626.toInt()

    private val grayscaleFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })

    // Size-bounded cache in kilobytes (1/16th of max heap, clamped between 4 MB and 16 MB)
    private val maxCacheKb: Int = ((Runtime.getRuntime().maxMemory() / 1024) / 16).toInt().coerceIn(4 * 1024, 16 * 1024)
    private val bitmapCache = object : LruCache<String, Bitmap>(maxCacheKb) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return (value.allocationByteCount / 1024).coerceAtLeast(1)
        }
    }

    fun clearCache() {
        bitmapCache.evictAll()
    }

    /**
     * Converts a Drawable to a monochrome version.
     * On Android 13+ (API 33+), if the icon is an [AdaptiveIconDrawable] with a themed monochrome layer,
     * that vector layer is extracted, tinted with light neutral grey, and composited onto a dark neutral surface.
     * Otherwise, a high-contrast grayscale color matrix filter is applied to the drawable.
     */
    fun toMonochromeDrawable(drawable: Drawable): Drawable {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && drawable is AdaptiveIconDrawable) {
            val mono = drawable.monochrome
            if (mono != null) {
                val monoCopy = mono.constantState?.newDrawable()?.mutate() ?: mono.mutate()
                monoCopy.setTint(MONO_FG_COLOR)
                val bg = ColorDrawable(MONO_BG_COLOR)
                return AdaptiveIconDrawable(bg, monoCopy)
            }
        }
        val mutated = drawable.constantState?.newDrawable()?.mutate() ?: drawable.mutate()
        mutated.colorFilter = grayscaleFilter
        return mutated
    }

    /**
     * Converts a Drawable to a Bitmap, with optional monochrome conversion.
     * Caches bitmaps by their visual footprint.
     */
    fun drawableToBitmap(
        drawable: Drawable,
        isMonochrome: Boolean = false,
        targetWidth: Int = 0,
        targetHeight: Int = 0
    ): Bitmap? {
        val stateKey = System.identityHashCode(drawable.constantState ?: drawable)
        val cacheKey = "${stateKey}_${isMonochrome}_${targetWidth}x$targetHeight"
        val cached = bitmapCache.get(cacheKey)
        if (cached != null && !cached.isRecycled) {
            return cached
        }

        return try {
            val bmp = if (isMonochrome) {
                getMonochromeBitmap(drawable, targetWidth, targetHeight)
            } else {
                getStandardBitmap(drawable, targetWidth, targetHeight)
            }
            if (bmp != null && !bmp.isRecycled) {
                bitmapCache.put(cacheKey, bmp)
            }
            bmp
        } catch (_: Throwable) {
            null
        }
    }

    private fun getMonochromeBitmap(
        drawable: Drawable,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? {
        val width = if (targetWidth > 0) targetWidth else if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth.coerceIn(48, 192) else 96
        val height = if (targetHeight > 0) targetHeight else if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight.coerceIn(48, 192) else 96

        // 1. Android 13+ official themed monochrome icon layer from AdaptiveIconDrawable
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && drawable is AdaptiveIconDrawable) {
            val mono = drawable.monochrome
            if (mono != null) {
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)

                // Background: elegant dark circular surface
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = MONO_BG_COLOR
                    style = Paint.Style.FILL
                }
                val radius = minOf(width, height) / 2f
                canvas.drawCircle(width / 2f, height / 2f, radius, bgPaint)

                // Monochrome foreground layer tinted light neutral white
                val monoForeground = mono.constantState?.newDrawable()?.mutate() ?: mono.mutate()
                monoForeground.setTint(MONO_FG_COLOR)
                val padding = (minOf(width, height) * 0.18f).toInt()
                val oldBounds = monoForeground.copyBounds()
                try {
                    monoForeground.setBounds(padding, padding, width - padding, height - padding)
                    monoForeground.draw(canvas)
                } finally {
                    monoForeground.bounds = oldBounds
                }
                return bitmap
            }
        }

        // 2. Fallback for non-adaptive icons or adaptive icons without a monochrome layer:
        // Render directly onto a single Bitmap using a grayscale layer paint
        val finalBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val finalCanvas = Canvas(finalBitmap)
        val layerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = grayscaleFilter
        }
        val saveCount = finalCanvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), layerPaint)
        val oldBounds = drawable.copyBounds()
        try {
            drawable.setBounds(0, 0, width, height)
            drawable.draw(finalCanvas)
        } finally {
            drawable.bounds = oldBounds
            finalCanvas.restoreToCount(saveCount)
        }
        return finalBitmap
    }

    private fun getStandardBitmap(
        drawable: Drawable,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? {
        if (drawable is BitmapDrawable && drawable.bitmap != null && !drawable.bitmap.isRecycled) {
            if ((targetWidth == 0 || drawable.bitmap.width == targetWidth) &&
                (targetHeight == 0 || drawable.bitmap.height == targetHeight)) {
                return drawable.bitmap
            }
        }
        val width = if (targetWidth > 0) targetWidth else if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth.coerceIn(48, 192) else 96
        val height = if (targetHeight > 0) targetHeight else if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight.coerceIn(48, 192) else 96
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val oldBounds = drawable.copyBounds()
        try {
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)
        } finally {
            drawable.bounds = oldBounds
        }
        return bitmap
    }
}

/** Convenience top-level function for converting drawables to bitmaps */
fun drawableToBitmap(
    drawable: Drawable,
    isMonochrome: Boolean = false,
    targetWidth: Int = 0,
    targetHeight: Int = 0
): Bitmap? = IconUtils.drawableToBitmap(drawable, isMonochrome, targetWidth, targetHeight)
