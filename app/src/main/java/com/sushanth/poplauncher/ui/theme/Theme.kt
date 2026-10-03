package com.sushanth.poplauncher.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val PopLauncherDarkColorScheme = darkColorScheme(
    primary = MaroonLight,
    onPrimary = Color.White,
    primaryContainer = MaroonContainer,
    onPrimaryContainer = MaroonContainerLight,
    secondary = RoseLight,
    onSecondary = Color(0xFF3B0718),
    secondaryContainer = Color(0xFF5A1C30),
    onSecondaryContainer = Color(0xFFFFD9E2),
    tertiary = PeachLight,
    onTertiary = Color(0xFF3A0E06),
    tertiaryContainer = Color(0xFF633021),
    onTertiaryContainer = Color(0xFFFFDBD0),
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainer = DarkSurfaceContainer,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

val PopLauncherLightColorScheme = lightColorScheme(
    primary = Maroon,
    onPrimary = Color.White,
    primaryContainer = MaroonContainerLight,
    onPrimaryContainer = MaroonDark,
    secondary = RoseDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9E2),
    onSecondaryContainer = Color(0xFF4A0B20),
    tertiary = PeachDark,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBD0),
    onTertiaryContainer = Color(0xFF4A160D),
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainer = LightSurfaceContainer,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

val LocalIsMonochromeTheme = staticCompositionLocalOf { false }

@Composable
fun PopLauncherTheme(
    palette: ThemePalette = ThemePalette.DEFAULT_MAROON,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val baseScheme = if (darkTheme) PopLauncherDarkColorScheme else PopLauncherLightColorScheme
    val colorScheme = if (palette.isMonochrome) {
        val monoPrimary = if (darkTheme) Color(0xFFEEEEEE) else Color(0xFF1E1E1E)
        val monoOnPrimary = if (darkTheme) Color(0xFF121212) else Color(0xFFFFFFFF)
        val monoBackground = if (darkTheme) Color(0xFF121212) else Color(0xFFF8F8F8)
        val monoSurface = if (darkTheme) Color(0xFF1C1C1C) else Color(0xFFEFEFEF)
        val monoSurfaceVariant = if (darkTheme) Color(0xFF282828) else Color(0xFFE2E2E2)
        baseScheme.copy(
            primary = monoPrimary,
            onPrimary = monoOnPrimary,
            primaryContainer = if (darkTheme) Color(0xFF333333) else Color(0xFFDDDDDD),
            onPrimaryContainer = monoPrimary,
            secondary = if (darkTheme) Color(0xFFCCCCCC) else Color(0xFF333333),
            onSecondary = monoOnPrimary,
            secondaryContainer = if (darkTheme) Color(0xFF2A2A2A) else Color(0xFFE8E8E8),
            onSecondaryContainer = if (darkTheme) Color(0xFFEEEEEE) else Color(0xFF111111),
            tertiary = if (darkTheme) Color(0xFFAAAAAA) else Color(0xFF555555),
            onTertiary = monoOnPrimary,
            background = monoBackground,
            onBackground = if (darkTheme) Color(0xFFF5F5F5) else Color(0xFF151515),
            surface = monoSurface,
            onSurface = if (darkTheme) Color(0xFFF5F5F5) else Color(0xFF151515),
            surfaceVariant = monoSurfaceVariant,
            onSurfaceVariant = if (darkTheme) Color(0xFFCCCCCC) else Color(0xFF444444)
        )
    } else if (palette != ThemePalette.DEFAULT_MAROON) {
        val background = baseScheme.background
        val primary = palette.primary
        val accent = palette.accent
        val primaryContainer = blend(primary, background, if (darkTheme) 0.28f else 0.12f)
        val secondaryContainer = blend(accent, background, if (darkTheme) 0.22f else 0.10f)
        baseScheme.copy(
            primary = primary,
            onPrimary = palette.onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = bestOnColor(primaryContainer),
            secondary = accent,
            onSecondary = bestOnColor(accent),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = bestOnColor(secondaryContainer),
            tertiary = accent,
            onTertiary = bestOnColor(accent),
            tertiaryContainer = blend(accent, background, if (darkTheme) 0.20f else 0.10f),
            onTertiaryContainer = bestOnColor(blend(accent, background, if (darkTheme) 0.20f else 0.10f))
        )
    } else {
        // DEFAULT_MAROON palette: PopLauncherDarkColorScheme and PopLauncherLightColorScheme
        // already define the correct maroon color tokens for dark and light mode respectively.
        // No override needed — use the base scheme directly.
        baseScheme
    }

    CompositionLocalProvider(
        LocalIsMonochromeTheme provides palette.isMonochrome
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PopLauncherTypography,
            content = content
        )
    }
}
