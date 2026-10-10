package com.example.luacompose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal fun readingColors(dark: Boolean) = if (dark) darkColorScheme(
    primary = Color(0xFF9BD5B4), onPrimary = Color(0xFF101A16),
    tertiary = Color(0xFFDFC58B), onTertiary = Color(0xFF101A16),
    tertiaryContainer = Color(0xFF192820), onTertiaryContainer = Color(0xFFDFC58B),
    background = Color(0xFF101A16), onBackground = Color(0xFFEDF4EE),
    surface = Color(0xFF192820), onSurface = Color(0xFFEDF4EE),
    surfaceContainerLowest = Color(0xFF101A16), surfaceContainerLow = Color(0xFF192820),
    surfaceContainerHighest = Color(0xFF192820), onSurfaceVariant = Color(0xFFB4C5BA),
) else lightColorScheme(
    primary = Color(0xFF176047), onPrimary = Color.White,
    tertiary = Color(0xFF806219), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF7F5EF), onTertiaryContainer = Color(0xFF806219),
    background = Color(0xFFF7F5EF), onBackground = Color(0xFF182D26),
    surface = Color.White, onSurface = Color(0xFF182D26),
    surfaceContainerLowest = Color(0xFFF7F5EF), surfaceContainerLow = Color(0xFFF7F5EF),
    surfaceContainerHighest = Color.White, onSurfaceVariant = Color(0xFF52645C),
)

val LocalToolbarColor = compositionLocalOf<Color?> { null }

val FEATURE_COLORS = setOf(
    "toolbar",
    "primary", "onPrimary", "primaryContainer", "onPrimaryContainer",
    "inversePrimary", "secondary", "onSecondary", "secondaryContainer",
    "onSecondaryContainer", "tertiary", "onTertiary", "tertiaryContainer",
    "onTertiaryContainer", "background", "onBackground", "surface",
    "onSurface", "surfaceVariant", "onSurfaceVariant", "surfaceTint",
    "inverseSurface", "inverseOnSurface", "error", "onError",
    "errorContainer", "onErrorContainer", "outline", "outlineVariant",
    "scrim", "surfaceBright", "surfaceDim", "surfaceContainer",
    "surfaceContainerLowest", "surfaceContainerLow", "surfaceContainerHigh", "surfaceContainerHighest",
)

internal fun androidx.compose.material3.ColorScheme.withFeatureColors(overrides: Map<String, Long>) = copy(
    primary = overrides["primary"]?.let { Color(it) } ?: primary,
    onPrimary = overrides["onPrimary"]?.let { Color(it) } ?: onPrimary,
    primaryContainer = overrides["primaryContainer"]?.let { Color(it) } ?: primaryContainer,
    onPrimaryContainer = overrides["onPrimaryContainer"]?.let { Color(it) } ?: onPrimaryContainer,
    inversePrimary = overrides["inversePrimary"]?.let { Color(it) } ?: inversePrimary,
    secondary = overrides["secondary"]?.let { Color(it) } ?: secondary,
    onSecondary = overrides["onSecondary"]?.let { Color(it) } ?: onSecondary,
    secondaryContainer = overrides["secondaryContainer"]?.let { Color(it) } ?: secondaryContainer,
    onSecondaryContainer = overrides["onSecondaryContainer"]?.let { Color(it) } ?: onSecondaryContainer,
    tertiary = overrides["tertiary"]?.let { Color(it) } ?: tertiary,
    onTertiary = overrides["onTertiary"]?.let { Color(it) } ?: onTertiary,
    tertiaryContainer = overrides["tertiaryContainer"]?.let { Color(it) } ?: tertiaryContainer,
    onTertiaryContainer = overrides["onTertiaryContainer"]?.let { Color(it) } ?: onTertiaryContainer,
    background = overrides["background"]?.let { Color(it) } ?: background,
    onBackground = overrides["onBackground"]?.let { Color(it) } ?: onBackground,
    surface = overrides["surface"]?.let { Color(it) } ?: surface,
    onSurface = overrides["onSurface"]?.let { Color(it) } ?: onSurface,
    surfaceVariant = overrides["surfaceVariant"]?.let { Color(it) } ?: surfaceVariant,
    onSurfaceVariant = overrides["onSurfaceVariant"]?.let { Color(it) } ?: onSurfaceVariant,
    surfaceTint = overrides["surfaceTint"]?.let { Color(it) } ?: surfaceTint,
    inverseSurface = overrides["inverseSurface"]?.let { Color(it) } ?: inverseSurface,
    inverseOnSurface = overrides["inverseOnSurface"]?.let { Color(it) } ?: inverseOnSurface,
    error = overrides["error"]?.let { Color(it) } ?: error,
    onError = overrides["onError"]?.let { Color(it) } ?: onError,
    errorContainer = overrides["errorContainer"]?.let { Color(it) } ?: errorContainer,
    onErrorContainer = overrides["onErrorContainer"]?.let { Color(it) } ?: onErrorContainer,
    outline = overrides["outline"]?.let { Color(it) } ?: outline,
    outlineVariant = overrides["outlineVariant"]?.let { Color(it) } ?: outlineVariant,
    scrim = overrides["scrim"]?.let { Color(it) } ?: scrim,
    surfaceBright = overrides["surfaceBright"]?.let { Color(it) } ?: surfaceBright,
    surfaceDim = overrides["surfaceDim"]?.let { Color(it) } ?: surfaceDim,
    surfaceContainer = overrides["surfaceContainer"]?.let { Color(it) } ?: surfaceContainer,
    surfaceContainerLowest = overrides["surfaceContainerLowest"]?.let { Color(it) } ?: surfaceContainerLowest,
    surfaceContainerLow = overrides["surfaceContainerLow"]?.let { Color(it) } ?: surfaceContainerLow,
    surfaceContainerHigh = overrides["surfaceContainerHigh"]?.let { Color(it) } ?: surfaceContainerHigh,
    surfaceContainerHighest = overrides["surfaceContainerHighest"]?.let { Color(it) } ?: surfaceContainerHighest,
)

@Composable
fun FeatureTheme(overrides: Map<String, Long>, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalToolbarColor provides overrides["toolbar"]?.let { Color(it) }) {
        MaterialTheme(colorScheme = MaterialTheme.colorScheme.withFeatureColors(overrides), content = content)
    }
}

@Composable
fun ReadingTheme(overrides: Map<String, Long> = emptyMap(), content: @Composable () -> Unit) {
    val colors = readingColors(isSystemInDarkTheme()).withFeatureColors(overrides)
    val typography = Typography(
        headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 30.sp, lineHeight = 38.sp),
        titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
        labelMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    )
    CompositionLocalProvider(
        LocalReadingStyle provides true,
        LocalToolbarColor provides overrides["toolbar"]?.let { Color(it) },
        androidx.compose.material3.LocalContentColor provides colors.onSurface,
    ) {
        MaterialTheme(colorScheme = colors, typography = typography, content = content)
    }
}
