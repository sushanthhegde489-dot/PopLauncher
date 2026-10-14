package com.sushanth.poplauncher.ui.theme

import androidx.compose.ui.graphics.Color

data class ThemePalette(
    val id: String,
    val displayName: String,
    val primary: Color,
    val onPrimary: Color,
    val accent: Color,
    val previewDot: Color,
    val isMonochrome: Boolean = false
) {
    companion object {
        val DEFAULT_MAROON = ThemePalette(
            id = "default_maroon",
            displayName = "Pop Maroon",
            primary = MaroonLight,
            onPrimary = Color.White,
            accent = Rose,
            previewDot = MaroonLight
        )

        val CYAN = ThemePalette(
            id = "cyan",
            displayName = "Cyan Pop",
            primary = CyanPrimary,
            onPrimary = Color.Black,
            accent = CyanSecondary,
            previewDot = CyanPrimary
        )

        val AMBER = ThemePalette(
            id = "amber",
            displayName = "Amber Gold",
            primary = AmberPrimary,
            onPrimary = Color.Black,
            accent = AmberSecondary,
            previewDot = AmberPrimary
        )

        val EMERALD = ThemePalette(
            id = "emerald",
            displayName = "Emerald",
            primary = EmeraldPrimary,
            onPrimary = Color.Black,
            accent = EmeraldSecondary,
            previewDot = EmeraldPrimary
        )

        val VIOLET = ThemePalette(
            id = "violet",
            displayName = "Electric Violet",
            primary = VioletPrimary,
            onPrimary = Color.White,
            accent = VioletSecondary,
            previewDot = VioletPrimary
        )

        val CORAL = ThemePalette(
            id = "coral",
            displayName = "Coral Flame",
            primary = CoralPrimary,
            onPrimary = Color.White,
            accent = CoralSecondary,
            previewDot = CoralPrimary
        )

        val MONOCHROME = ThemePalette(
            id = "monochrome",
            displayName = "Monochrome",
            primary = Color(0xFFE0E0E0),
            onPrimary = Color.Black,
            accent = Color(0xFFB0B0B0),
            previewDot = Color(0xFFD0D0D0),
            isMonochrome = true
        )

        fun createCustom(primary: Color): ThemePalette {
            val safePrimary = Color(primary.red, primary.green, primary.blue, 1f)
            return ThemePalette(
                id = "custom",
                displayName = "Custom",
                primary = safePrimary,
                onPrimary = bestOnColor(safePrimary),
                accent = safePrimary,
                previewDot = safePrimary
            )
        }

        val entries: List<ThemePalette> = listOf(
            DEFAULT_MAROON,
            CYAN,
            AMBER,
            EMERALD,
            VIOLET,
            CORAL,
            MONOCHROME
        )

        fun fromId(id: String?, customHex: String? = null): ThemePalette {
            if (id == null) return DEFAULT_MAROON
            if (id.equals("custom", ignoreCase = true)) {
                return createCustom(parseHexColor(customHex ?: "#8B1E3F"))
            }
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEFAULT_MAROON
        }
    }
}
