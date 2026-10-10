package com.example.luacompose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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

@Composable
fun ReadingTheme(content: @Composable () -> Unit) {
    val colors = readingColors(isSystemInDarkTheme())
    val typography = Typography(
        headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 30.sp, lineHeight = 38.sp),
        titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
        labelMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    )
    CompositionLocalProvider(
        LocalReadingStyle provides true,
        androidx.compose.material3.LocalContentColor provides colors.onSurface,
    ) {
        MaterialTheme(colorScheme = colors, typography = typography, content = content)
    }
}
