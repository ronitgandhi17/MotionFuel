package com.ronitgandhi.motionfuel.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val FuelGreen = Color(0xFF56E39F)
val FuelGreenDark = Color(0xFF0C8C5E)
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
    primary = FuelGreenDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9F5D8),
    onPrimaryContainer = Color(0xFF002116),
    secondary = Color(0xFF00639A),
    tertiary = Color(0xFF8C4B00),
    background = Mist,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE4ECEA),
    onSurfaceVariant = Color(0xFF45524F),
    error = Color(0xFFBA1A1A),
)

@Composable
fun MotionFuelTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
