package com.example.dailybloom.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val LightColorScheme = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    secondary = GreenLight,
    onSecondary = ForegroundDark,
    tertiary = GreenLightest,
    onTertiary = ForegroundDark,
    background = BackgroundLight,
    onBackground = ForegroundDark,
    surface = Color.White,
    onSurface = ForegroundDark,
    surfaceVariant = SurfaceLight,
    onSurfaceVariant = MutedForeground,
    error = ErrorRed,
    onError = Color.White,
    outline = Green.copy(alpha = 0.15f)
)

val DarkColorScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = ForegroundDark,
    secondary = SurfaceDark,
    onSecondary = Color.White,
    tertiary = SurfaceDark,
    onTertiary = Color.White,
    background = BackgroundDark,
    onBackground = Color.White,
    surface = BackgroundDark,
    onSurface = Color.White,
    surfaceVariant = SurfaceDark,
    onSurfaceVariant = MutedForegroundDark,
    error = ErrorRedDark,
    onError = ErrorRed,
    outline = SurfaceDark
)

@Composable
fun DailyBloomTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}