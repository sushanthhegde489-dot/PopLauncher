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
                monoCopy.setTint(android.graphics.Color.parseColor("#F5F5F5"))
                val bg = ColorDrawable(android.graphics.Color.parseColor("#262626"))
                return AdaptiveIconDrawable(bg, monoCopy)
            }
        }
        val mutated = drawable.constantState?.newDrawable()?.mutate() ?: drawable.mutate()
        val matrix = ColorMatrix()
        matrix.setSaturation(0f)
        mutated.colorFilter = ColorMatrixColorFilter(matrix)
        return mutated
    }

    private val cacheSize: Int = ((Runtime.getRuntime().maxMemory() / 1024 / 1024).toInt() * 2).coerceIn(100, 300)
    private val bitmapCache = LruCache<String, Bitmap>(cacheSize)

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
        val stateKey = drawable.constantState?.hashCode() ?: System.identityHashCode(drawable)
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
            if (bmp != null) {
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
                    color = android.graphics.Color.parseColor("#262626")
                    style = Paint.Style.FILL
                }
                val radius = minOf(width, height) / 2f
                canvas.drawCircle(width / 2f, height / 2f, radius, bgPaint)

                // Monochrome foreground layer tinted light neutral white
                val monoForeground = mono.constantState?.newDrawable()?.mutate() ?: mono.mutate()
                monoForeground.setTint(android.graphics.Color.parseColor("#F5F5F5"))
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
        // Render onto an intermediate canvas and convert with grayscale color matrix
        val tempBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val tempCanvas = Canvas(tempBitmap)
        val oldBounds = drawable.copyBounds()
        try {
            drawable.setBounds(0, 0, width, height)
            drawable.draw(tempCanvas)
        } finally {
            drawable.bounds = oldBounds
        }

        val finalBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val finalCanvas = Canvas(finalBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            val matrix = ColorMatrix()
            matrix.setSaturation(0f)
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        finalCanvas.drawBitmap(tempBitmap, 0f, 0f, paint)
        tempBitmap.recycle()
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
