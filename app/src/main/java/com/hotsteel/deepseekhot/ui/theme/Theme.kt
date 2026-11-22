// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Hotsteel2901

package com.hotsteel.deepseekhot.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
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

/** DeepSeek 品牌蓝，Logo 与强调色固定使用这一色值，不随动态取色变化。 */
val BrandBlue = Color(0xFF4D6BFE)

/**
 * 全局色板。
 *
 * 设计取向参照 DeepSeek 官方网页端 / chat-demo：大面积纯净底色、无渐变、无玻璃拟态，
 * 层级只靠极浅的灰阶面与 1px 描边区分，唯一的彩色是品牌蓝。
 */
data class Palette(
    val isDark: Boolean,
    /** 聊天主背景。 */
    val bg: Color,
    /** 次级背景：侧边栏、设置页等「列表 + 卡片」页面的底。 */
    val bgAlt: Color,
    /** 卡片、输入框、弹层等浮起的面。 */
    val surface: Color,
    /** 低一级的填充面：思考块、代码块头部、未选中的分段按钮。 */
    val surfaceAlt: Color,
    /** 再高一级的填充面：按压态、选中的分段按钮（深色）。 */
    val surfaceHigh: Color,
    val border: Color,
    val text: Color,
    val dim: Color,
    val faint: Color,
    val primary: Color,
    /** 品牌蓝的浅色底，用于激活中的胶囊按钮、选中行。 */
    val primarySoft: Color,
    val accent: Color,
    val danger: Color,
    val success: Color,
    /** 用户消息气泡。 */
    val bubble: Color,
    val codeBg: Color,
    /** 行内代码文字色。 */
    val inlineCode: Color,
)

val LightPalette = Palette(
    isDark = false,
    bg = Color(0xFFFFFFFF),
    bgAlt = Color(0xFFF6F7F9),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFF2F3F5),
    surfaceHigh = Color(0xFFE8EAEE),
    border = Color(0xFFE6E8EC),
    text = Color(0xFF1D1F24),
    dim = Color(0xFF555A64),
    faint = Color(0xFF8F949E),
    primary = BrandBlue,
    primarySoft = Color(0xFFE8EDFF),
    accent = Color(0xFF6E86FF),
    danger = Color(0xFFE5484D),
    success = Color(0xFF18A058),
    bubble = Color(0xFFEFF4FF),
    codeBg = Color(0xFFF6F7F9),
    inlineCode = Color(0xFF3A4CC4),
)

val DarkPalette = Palette(
    isDark = true,
    bg = Color(0xFF1B1C1F),
    bgAlt = Color(0xFF151618),
    surface = Color(0xFF242529),
    surfaceAlt = Color(0xFF2A2B30),
    surfaceHigh = Color(0xFF34353B),
    border = Color(0xFF34363C),
    text = Color(0xFFECEDF0),
    dim = Color(0xFFA6AAB3),
    faint = Color(0xFF6F737C),
    primary = Color(0xFF6A84FF),
    primarySoft = Color(0xFF2B3150),
    accent = Color(0xFF8FA2FF),
    danger = Color(0xFFF2777A),
    success = Color(0xFF4ACB85),
    bubble = Color(0xFF30334A),
    codeBg = Color(0xFF17181B),
    inlineCode = Color(0xFFB4C1FF),
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

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

private fun materialScheme(p: Palette): ColorScheme =
    if (p.isDark) {
        darkColorScheme(
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
            surfaceContainer = p.surface,
            surfaceContainerHigh = p.surfaceAlt,
            surfaceContainerHighest = p.surfaceHigh,
            outline = p.border,
            outlineVariant = p.border,
            error = p.danger,
        )
    } else {
        lightColorScheme(
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
            surfaceContainer = p.surface,
            surfaceContainerHigh = p.surface,
            surfaceContainerHighest = p.surfaceAlt,
            outline = p.border,
            outlineVariant = p.border,
            error = p.danger,
        )
    }

/**
 * 动态取色只替换「强调色」一族，底色与文字仍用手调灰阶：
 * 这样 Material 组件（对话框、菜单、输入框）与自绘组件始终是同一套颜色，不会出现两种蓝。
 */
private fun dynamicPalette(base: Palette, ctx: android.content.Context): Palette {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return base
    val s = if (base.isDark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    return base.copy(
        primary = s.primary,
        primarySoft = s.primaryContainer.copy(alpha = if (base.isDark) 0.55f else 0.7f),
        accent = s.secondary,
        bubble = s.primaryContainer.copy(alpha = if (base.isDark) 0.45f else 0.55f),
        inlineCode = s.primary,
    )
}

@Composable
fun DeepSeekHotTheme(
    dark: Boolean = isSystemInDarkTheme(),
    dynamic: Boolean = false,
    content: @Composable () -> Unit,
) {
    val ctx = LocalContext.current
    val base = if (dark) DarkPalette else LightPalette
    val palette = if (dynamic) dynamicPalette(base, ctx) else base
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = materialScheme(palette),
            typography = AppTypography,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(8.dp),
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(16.dp),
                large = RoundedCornerShape(20.dp),
                extraLarge = RoundedCornerShape(28.dp),
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
