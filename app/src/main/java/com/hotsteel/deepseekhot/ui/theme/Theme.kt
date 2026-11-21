// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Hotsteel2901

package com.hotsteel.deepseekhot.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

data class Palette(
    val isDark: Boolean,
    val bg: Color,
    val bgAlt: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val surfaceHigh: Color,
    val border: Color,
    val text: Color,
    val dim: Color,
    val faint: Color,
    val primary: Color,
    val primarySoft: Color,
    val accent: Color,
    val danger: Color,
    val success: Color,
    val bubble: Color,
    val codeBg: Color,
    val glowA: Color,
    val glowB: Color,
)

val DarkPalette = Palette(
    isDark = true,
    bg = Color(0xFF080B14),
    bgAlt = Color(0xFF0C111F),
    surface = Color(0xFF111726),
    surfaceAlt = Color(0xFF161E31),
    surfaceHigh = Color(0xFF1D2740),
    border = Color(0x1FFFFFFF),
    text = Color(0xFFE9EDF9),
    dim = Color(0xFF9BA6C2),
    faint = Color(0xFF6A7590),
    primary = Color(0xFF5B7BFF),
    primarySoft = Color(0xFF2A3763),
    accent = Color(0xFF2FD6E8),
    danger = Color(0xFFFF6B81),
    success = Color(0xFF3DDC97),
    bubble = Color(0xFF1A233A),
    codeBg = Color(0xFF0B1020),
    glowA = Color(0xFF3D5BFF),
    glowB = Color(0xFF12B8C8),
)

val LightPalette = Palette(
    isDark = false,
    bg = Color(0xFFF5F7FC),
    bgAlt = Color(0xFFEFF3FB),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFF3F6FD),
    surfaceHigh = Color(0xFFE9EEFA),
    border = Color(0x14000000),
    text = Color(0xFF0E1424),
    dim = Color(0xFF566079),
    faint = Color(0xFF8791A8),
    primary = Color(0xFF3457F0),
    primarySoft = Color(0xFFE2E8FF),
    accent = Color(0xFF0891B2),
    danger = Color(0xFFE5484D),
    success = Color(0xFF12A150),
    bubble = Color(0xFFEBF0FF),
    codeBg = Color(0xFFF3F5FB),
    glowA = Color(0xFF7C9BFF),
    glowB = Color(0xFF7FE3EC),
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

private val AppTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(lineHeight = 26.sp),
        bodyMedium = bodyMedium.copy(lineHeight = 23.sp),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
    )
}

private fun scheme(p: Palette, dynamic: Boolean, dark: Boolean, ctx: android.content.Context) = when {
    dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)

    dark -> darkColorScheme(
        primary = p.primary,
        onPrimary = Color.White,
        primaryContainer = p.primarySoft,
        onPrimaryContainer = p.text,
        secondary = p.accent,
        onSecondary = Color(0xFF04252A),
        background = p.bg,
        onBackground = p.text,
        surface = p.surface,
        onSurface = p.text,
        surfaceVariant = p.surfaceAlt,
        onSurfaceVariant = p.dim,
        outline = p.border,
        error = p.danger,
    )

    else -> lightColorScheme(
        primary = p.primary,
        onPrimary = Color.White,
        primaryContainer = p.primarySoft,
        onPrimaryContainer = p.text,
        secondary = p.accent,
        onSecondary = Color.White,
        background = p.bg,
        onBackground = p.text,
        surface = p.surface,
        onSurface = p.text,
        surfaceVariant = p.surfaceAlt,
        onSurfaceVariant = p.dim,
        outline = p.border,
        error = p.danger,
    )
}

@Composable
fun DeepSeekHotTheme(
    dark: Boolean = isSystemInDarkTheme(),
    dynamic: Boolean = true,
    content: @Composable () -> Unit,
) {
    val ctx = LocalContext.current
    val palette = if (dark) DarkPalette else LightPalette
    val colors = scheme(palette, dynamic, dark, ctx)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !dark
        }
    }
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = colors,
            typography = AppTypography,
            shapes = androidx.compose.material3.Shapes(
                extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                small = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(30.dp),
            ),
            content = content,
        )
    }
}

val MonoStyle = TextStyle(
    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
    fontSize = 13.sp,
    lineHeight = 20.sp,
)
