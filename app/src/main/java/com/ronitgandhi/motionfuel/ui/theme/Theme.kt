package com.ronitgandhi.motionfuel.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val FuelGreen = Color(0xFF56E39F)
val FuelGreenDark = Color(0xFF0C8C5E)
val FuelBlue = Color(0xFF0066EE)
val FuelNavy = Color(0xFF0C1B33)
val FuelNavySoft = Color(0xFF152947)
val FuelSky = Color(0xFF62B6FF)
val FuelOrange = Color(0xFFFFB45C)
val FuelRose = Color(0xFFFF6B7A)
val Ink = Color(0xFF142033)
val Mist = Color(0xFFF3F7F6)

private val DarkColors = darkColorScheme(
    primary = FuelGreen,
    onPrimary = Color(0xFF002116),
    primaryContainer = Color(0xFF0A4F38),
    onPrimaryContainer = Color(0xFFC6FFE2),
    secondary = FuelSky,
    tertiary = FuelOrange,
    background = Color(0xFF081322),
    onBackground = Color(0xFFE4ECF8),
    surface = FuelNavy,
    onSurface = Color(0xFFE4ECF8),
    surfaceVariant = FuelNavySoft,
    onSurfaceVariant = Color(0xFFB9C6D8),
    error = FuelRose,
)

private val LightColors = lightColorScheme(
    primary = FuelBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E6FF),
    onPrimaryContainer = Color(0xFF001B3F),
    secondary = Color(0xFF00639A),
    tertiary = Color(0xFF8C4B00),
    background = Color(0xFFF6F7F9),
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE4ECEA),
    onSurfaceVariant = Color(0xFF45524F),
    error = Color(0xFFBA1A1A),
)

@Composable
fun MotionFuelTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, content = content)
    }
}
