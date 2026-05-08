package dev.wolly.dsbwatch.presentation.theme

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme

private fun createTheme(
    primary: Color,
    onPrimary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    secondary: Color,
): ColorScheme {
    // Generate surfaces based on primary/background
    val background = Color(0xFF000000)
    val surface = Color(0xFF121212)
    
    // Subtle tint for containers based on primary
    val surfaceContainer = primary.copy(alpha = 0.15f).compositeOver(surface)
    
    return ColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onPrimary, // Fallback
        secondaryContainer = secondary.copy(alpha = 0.2f).compositeOver(surface),
        onSecondaryContainer = secondary,
        tertiary = primary, // Remove distinct tertiary by matching primary
        onTertiary = onPrimary,
        tertiaryContainer = primaryContainer,
        onTertiaryContainer = onPrimaryContainer,
        surfaceContainerLow = surface,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = primary.copy(alpha = 0.25f).compositeOver(surface),
        onSurface = Color(0xFFE0E0E0),
        onSurfaceVariant = Color(0xFFC0C0C0),
        outline = primary.copy(alpha = 0.5f).compositeOver(surface),
        outlineVariant = primary.copy(alpha = 0.3f).compositeOver(surface),
        background = background,
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6)
    )
}

// Extension to blend colors for surface tinting
private fun Color.compositeOver(background: Color): Color {
    val a = this.alpha
    val r = this.red * a + background.red * (1 - a)
    val g = this.green * a + background.green * (1 - a)
    val b = this.blue * a + background.blue * (1 - a)
    return Color(r, g, b, 1f)
}

private val GreenColorScheme = createTheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary
)

private val BlueColorScheme = createTheme(
    primary = blue_primary,
    onPrimary = blue_onPrimary,
    primaryContainer = blue_primaryContainer,
    onPrimaryContainer = blue_onPrimaryContainer,
    secondary = blue_secondary
)

private val PurpleColorScheme = createTheme(
    primary = purple_primary,
    onPrimary = purple_onPrimary,
    primaryContainer = purple_primaryContainer,
    onPrimaryContainer = purple_onPrimaryContainer,
    secondary = purple_secondary
)

private val RedColorScheme = createTheme(
    primary = red_primary,
    onPrimary = red_onPrimary,
    primaryContainer = red_primaryContainer,
    onPrimaryContainer = red_onPrimaryContainer,
    secondary = red_secondary
)

private val OrangeColorScheme = createTheme(
    primary = orange_primary,
    onPrimary = orange_onPrimary,
    primaryContainer = orange_primaryContainer,
    onPrimaryContainer = orange_onPrimaryContainer,
    secondary = orange_secondary
)

private val CyanColorScheme = createTheme(
    primary = cyan_primary,
    onPrimary = cyan_onPrimary,
    primaryContainer = cyan_primaryContainer,
    onPrimaryContainer = cyan_onPrimaryContainer,
    secondary = cyan_secondary
)

private val PinkColorScheme = createTheme(
    primary = pink_primary,
    onPrimary = pink_onPrimary,
    primaryContainer = pink_primaryContainer,
    onPrimaryContainer = pink_onPrimaryContainer,
    secondary = pink_secondary
)

val themePresets = listOf(
    GreenColorScheme,
    BlueColorScheme,
    PurpleColorScheme,
    RedColorScheme,
    OrangeColorScheme,
    CyanColorScheme,
    PinkColorScheme
)

@Composable
fun DSBwatchTheme(
    themeIndex: Int = 0,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val selectedColorScheme: ColorScheme = when {
        themeIndex == 0 && dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            dynamicColorScheme(context) ?: GreenColorScheme
        }
        themeIndex in themePresets.indices -> themePresets[themeIndex]
        else -> GreenColorScheme
    }

    MaterialTheme(
        colorScheme = selectedColorScheme,
        content = content
    )
}
