package com.sushanth.poplauncher.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

// ============================================================
// MAROON PALETTE
// ============================================================

val Maroon = Color(0xFF8B1E3F)
val MaroonLight = Color(0xFFB83A5C)
val MaroonDark = Color(0xFF5E1029)

val MaroonContainer = Color(0xFF64182F)
val MaroonContainerLight = Color(0xFFFFD9E2)


// ============================================================
// WARM ACCENT COLORS
// ============================================================

val Rose = Color(0xFFC94F70)
val RoseLight = Color(0xFFE9829D)
val RoseDark = Color(0xFF8F2948)

val Peach = Color(0xFFD9795B)
val PeachLight = Color(0xFFF0A58E)
val PeachDark = Color(0xFF99452F)


// ============================================================
// PREDEFINED THEME PALETTE COLORS
// ============================================================

val CyanPrimary = Color(0xFF00E5FF)
val CyanSecondary = Color(0xFF80F0FF)

val AmberPrimary = Color(0xFFFFB703)
val AmberSecondary = Color(0xFFFFD166)

val EmeraldPrimary = Color(0xFF00F5D4)
val EmeraldSecondary = Color(0xFF7DF8E7)

val VioletPrimary = Color(0xFFC77DFF)
val VioletSecondary = Color(0xFFE0AAFF)

val CoralPrimary = Color(0xFFFF6B6B)
val CoralSecondary = Color(0xFFFF9E9E)

fun parseHexColor(hex: String, fallback: Color = Color(0xFFFF007F)): Color {
    return try {
        val cleanHex = hex.trim().removePrefix("#")
        val colorInt = when (cleanHex.length) {
            6, 8 -> "#$cleanHex".toColorInt()
            else -> return fallback
        }
        Color(colorInt)
    } catch (_: Throwable) {
        fallback
    }
}

fun colorToHex(color: Color): String {
    val r = (color.red * 255f).roundToInt().coerceIn(0, 255)
    val g = (color.green * 255f).roundToInt().coerceIn(0, 255)
    val b = (color.blue * 255f).roundToInt().coerceIn(0, 255)
    return String.format(Locale.US, "#%02X%02X%02X", r, g, b)
}


// ============================================================
// DARK MODE
// ============================================================

val DarkBackground = Color(0xFF10090C)
val DarkSurface = Color(0xFF180F12)
val DarkSurfaceVariant = Color(0xFF26171C)
val DarkSurfaceContainer = Color(0xFF211419)

val DarkOnBackground = Color(0xFFFFF0F3)
val DarkOnSurface = Color(0xFFFFF0F3)
val DarkOnSurfaceVariant = Color(0xFFD8BEC5)

val DarkOutline = Color(0xFF76545E)
val DarkOutlineVariant = Color(0xFF49343A)


// ============================================================
// LIGHT MODE
// ============================================================

val LightBackground = Color(0xFFFFF8F9)
val LightSurface = Color(0xFFFFFBFC)
val LightSurfaceVariant = Color(0xFFF3E1E5)
val LightSurfaceContainer = Color(0xFFF8ECEF)

val LightOnBackground = Color(0xFF21181B)
val LightOnSurface = Color(0xFF21181B)
val LightOnSurfaceVariant = Color(0xFF59454B)

val LightOutline = Color(0xFF8D747B)
val LightOutlineVariant = Color(0xFFD9C2C8)


// ============================================================
// ACCENT & PARTICLE COLORS
// ============================================================

val ChartCoral = Color(0xFFE97856)
val ChartAmber = Color(0xFFE5A83B)

private val DarkContrastCandidate = Color(0xFF181516)

fun bestOnColor(background: Color): Color {
    val whiteContrast = contrastRatio(background, Color.White)
    val darkContrast = contrastRatio(background, DarkContrastCandidate)
    return if (whiteContrast >= darkContrast) Color.White else DarkContrastCandidate
}

fun contrastRatio(first: Color, second: Color): Float {
    val l1 = relativeLuminance(first)
    val l2 = relativeLuminance(second)
    val lighter = max(l1, l2)
    val darker = min(l1, l2)
    return (lighter + 0.05f) / (darker + 0.05f)
}

fun blend(foreground: Color, background: Color, amount: Float): Color {
    val a = amount.coerceIn(0f, 1f)
    val inv = 1f - a
    return Color(
        red = foreground.red * a + background.red * inv,
        green = foreground.green * a + background.green * inv,
        blue = foreground.blue * a + background.blue * inv,
        alpha = 1f
    )
}

private fun relativeLuminance(color: Color): Float {
    fun linear(channel: Float): Float =
        if (channel <= 0.03928f) channel / 12.92f else ((channel + 0.055f) / 1.055f).pow(2.4f)
    return 0.2126f * linear(color.red) + 0.7152f * linear(color.green) + 0.0722f * linear(color.blue)
}
