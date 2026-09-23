package com.cundalf.wavelink.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Tokens del design system WaveLink (OLED dark-first).
private val Background = Color(0xFF0B0C10)
private val SurfaceElevated = Color(0xFF1F2833)
private val Accent = Color(0xFF66FCF1)
private val OnAccent = Color(0xFF0B0C10)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFC5C6C7)
private val AccentMuted = Color(0xFF45A29E)
private val AccentContainer = Color(0xFF123B3B)
private val OnAccentContainer = Color(0xFFA7FBF4)

private val WaveLinkColors =
    darkColorScheme(
        primary = Accent,
        onPrimary = OnAccent,
        primaryContainer = AccentContainer,
        onPrimaryContainer = OnAccentContainer,
        secondary = AccentMuted,
        onSecondary = OnAccent,
        secondaryContainer = SurfaceElevated,
        onSecondaryContainer = Accent,
        tertiary = AccentMuted,
        onTertiary = OnAccent,
        background = Background,
        onBackground = TextPrimary,
        surface = Background,
        onSurface = TextPrimary,
        surfaceVariant = SurfaceElevated,
        onSurfaceVariant = TextSecondary,
        surfaceContainerLow = SurfaceElevated,
        surfaceContainer = SurfaceElevated,
        surfaceContainerHigh = Color(0xFF232C38),
        outline = Color(0xFF2E3A47),
    )

// Escala del design system: 32sp bold (títulos de reproductor), 20sp
// semi-bold (emisoras), 16sp cuerpo, 12sp mono (metadatos técnicos).
private val WaveLinkTypography =
    Typography(
        headlineLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold),
        headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
        bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
        bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
        labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
        labelSmall = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace),
    )

@Composable
fun SubWaveCastTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WaveLinkColors,
        typography = WaveLinkTypography,
        content = content,
    )
}
