// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Hotsteel2901

package com.hotsteel.deepseekhot.ui.liquid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.hotsteel.deepseekhot.ui.theme.LocalPalette
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/**
 * 液态玻璃渲染档位：
 * 0 = 完整液态玻璃（vibrancy + blur + lens 折射 + 色散 + 高光）
 * 1 = 轻量性能模式（vibrancy + 轻量 blur + 高光，适合中低端机省电）
 * 2 = 纯净平面模式（关闭光学折射，零额外 GPU 开销）
 */
object LiquidGlassMode {
    const val FULL = 0
    const val LITE = 1
    const val OFF = 2
}

/** 供页面内滚动卡片采样的静态极光背景 Backdrop（零滚动重绘开销，120fps 流畅）。 */
val LocalAmbientBackdrop = staticCompositionLocalOf<Backdrop> { emptyBackdrop() }

/** 供悬浮顶栏、底栏输入框、悬浮按钮采样的实时内容 Backdrop（滚动消息穿过镜片实时折射）。 */
val LocalChromeBackdrop = staticCompositionLocalOf<Backdrop> { emptyBackdrop() }

/** 当前启用的液态玻璃模式（0 完整 / 1 轻量 / 2 关闭）。 */
val LocalLiquidMode = staticCompositionLocalOf { LiquidGlassMode.FULL }

/**
 * DeepSeek 专属静态极光流光壁纸。
 * 采用静态 Canvas 一次绘制 + Hardware Layer 缓存（无 60fps 无限重绘），
 * 既为所有液态玻璃镜片提供丰富深邃的色彩折射源，又将闲置 GPU 占用压到接近 0%。
 */
@Composable
fun DeepSeekLiquidWallpaper(
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    val mode = LocalLiquidMode.current
    if (mode == LiquidGlassMode.OFF) {
        Box(modifier.fillMaxSize().background(p.bg))
        return
    }

    val dark = p.isDark
    val primary = p.primary
    val accent = p.accent
    val baseTop = if (dark) Color(0xFF0A0D18) else Color(0xFFF0F4FF)
    val baseMid = if (dark) Color(0xFF0E1220) else Color(0xFFF6F8FE)
    val baseBot = if (dark) Color(0xFF0B111B) else Color(0xFFEEF3FC)
    val cyanOrb = if (dark) Color(0xFF1F7AE0) else Color(0xFF5FA8FF)
    val violetOrb = if (dark) Color(0xFF6B4CF6) else Color(0xFF8C74FF)

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(brush = Brush.verticalGradient(listOf(baseTop, baseMid, baseBot)))

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primary.copy(alpha = if (dark) 0.28f else 0.18f), Color.Transparent),
                center = Offset(size.width * 0.15f, size.height * 0.10f),
                radius = size.maxDimension * 0.48f,
            ),
            center = Offset(size.width * 0.15f, size.height * 0.10f),
            radius = size.maxDimension * 0.48f,
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(violetOrb.copy(alpha = if (dark) 0.22f else 0.14f), Color.Transparent),
                center = Offset(size.width * 0.88f, size.height * 0.28f),
                radius = size.maxDimension * 0.46f,
            ),
            center = Offset(size.width * 0.88f, size.height * 0.28f),
            radius = size.maxDimension * 0.46f,
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(cyanOrb.copy(alpha = if (dark) 0.20f else 0.15f), Color.Transparent),
                center = Offset(size.width * 0.22f, size.height * 0.84f),
                radius = size.maxDimension * 0.50f,
            ),
            center = Offset(size.width * 0.22f, size.height * 0.84f),
            radius = size.maxDimension * 0.50f,
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = if (dark) 0.16f else 0.12f), Color.Transparent),
                center = Offset(size.width * 0.82f, size.height * 0.78f),
                radius = size.maxDimension * 0.42f,
            ),
            center = Offset(size.width * 0.82f, size.height * 0.78f),
            radius = size.maxDimension * 0.42f,
        )
    }
}

/**
 * 核心液态玻璃修饰符：按当前 [LocalLiquidMode] 自动在
 * 「完整光学折射」「轻量性能模糊」「纯净平面」间无缝切换。
 */
@Composable
fun Modifier.liquidGlass(
    backdrop: Backdrop = LocalAmbientBackdrop.current,
    cornerRadius: Dp = 20.dp,
    isCapsule: Boolean = false,
    tint: Color = Color.Unspecified,
    blurRadius: Dp = 12.dp,
    refractionHeight: Dp = 12.dp,
    refractionAmount: Dp = 20.dp,
    chromaticAberration: Boolean = false,
    surfaceAlpha: Float? = null,
    showShadow: Boolean = true,
): Modifier {
    val p = LocalPalette.current
    val mode = LocalLiquidMode.current
    val composeShape = if (isCapsule) CircleShape else RoundedCornerShape(cornerRadius)

    if (mode == LiquidGlassMode.OFF) {
        val bg = if (tint.isSpecified) tint.copy(alpha = if (p.isDark) 0.22f else 0.14f) else p.surface
        return this
            .clip(composeShape)
            .background(bg)
            .border(1.dp, p.border, composeShape)
    }

    val defaultAlpha = surfaceAlpha ?: if (p.isDark) 0.46f else 0.56f
    val surfaceColor = if (p.isDark) Color(0xFF161B29).copy(alpha = defaultAlpha) else Color.White.copy(alpha = defaultAlpha)
    val rimColor = if (p.isDark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.72f)

    return this
        .drawBackdrop(
            backdrop = backdrop,
            shape = { if (isCapsule) Capsule() else RoundedRectangle(cornerRadius) },
            effects = {
                vibrancy()
                val effectiveBlur = if (mode == LiquidGlassMode.LITE) (blurRadius.toPx() * 0.65f) else blurRadius.toPx()
                blur(effectiveBlur)
                if (mode == LiquidGlassMode.FULL) {
                    lens(
                        refractionHeight = refractionHeight.toPx(),
                        refractionAmount = refractionAmount.toPx(),
                        chromaticAberration = chromaticAberration,
                    )
                }
            },
            highlight = {
                Highlight.Default.copy(alpha = if (p.isDark) 0.58f else 0.88f)
            },
            shadow = if (showShadow) {
                {
                    Shadow(
                        radius = if (mode == LiquidGlassMode.FULL) 12.dp else 6.dp,
                        color = Color.Black.copy(alpha = if (p.isDark) 0.28f else 0.08f),
                    )
                }
            } else null,
            onDrawSurface = {
                if (tint.isSpecified) {
                    drawRect(tint.copy(alpha = if (p.isDark) 0.24f else 0.16f))
                }
                drawRect(surfaceColor)
            },
        )
        .border(0.75.dp, rimColor, composeShape)
}

/** 通用液态玻璃卡片容器。 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    backdrop: Backdrop = LocalAmbientBackdrop.current,
    cornerRadius: Dp = 20.dp,
    tint: Color = Color.Unspecified,
    blurRadius: Dp = 12.dp,
    refractionHeight: Dp = 12.dp,
    refractionAmount: Dp = 20.dp,
    surfaceAlpha: Float? = null,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .liquidGlass(
                backdrop = backdrop,
                cornerRadius = cornerRadius,
                tint = tint,
                blurRadius = blurRadius,
                refractionHeight = refractionHeight,
                refractionAmount = refractionAmount,
                surfaceAlpha = surfaceAlpha,
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

/** 液态玻璃交互胶囊按钮（带弹性挤压形变、AGSL 追光高光与镜片折射）。 */
@Composable
fun LiquidGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop = LocalChromeBackdrop.current,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    height: Dp = 42.dp,
    horizontalPadding: Dp = 16.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val p = LocalPalette.current
    val mode = LocalLiquidMode.current
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) { InteractiveHighlight(animationScope) }
    val defaultSurface = if (p.isDark) Color(0xFF1D2436).copy(alpha = 0.56f) else Color.White.copy(alpha = 0.64f)

    if (mode == LiquidGlassMode.OFF) {
        Row(
            modifier = modifier
                .alpha(if (enabled) 1f else 0.45f)
                .clip(CircleShape)
                .background(if (tint.isSpecified) tint else p.surfaceAlt)
                .border(1.dp, if (tint.isSpecified) tint else p.border, CircleShape)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .height(height)
                .padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        return
    }

    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.45f)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(if (mode == LiquidGlassMode.LITE) 4.dp.toPx() else 6.dp.toPx())
                    if (mode == LiquidGlassMode.FULL) {
                        lens(12.dp.toPx(), 20.dp.toPx())
                    }
                },
                highlight = { Highlight.Default.copy(alpha = if (p.isDark) 0.62f else 0.90f) },
                layerBlock = if (enabled && mode == LiquidGlassMode.FULL) {
                    {
                        val w = size.width
                        val h = size.height
                        if (w > 0f && h > 0f) {
                            val progress = interactiveHighlight.pressProgress
                            val s = lerp(1f, 1f + 3f.dp.toPx() / h, progress)
                            val maxOffset = size.minDimension.coerceAtLeast(1f)
                            val off = interactiveHighlight.offset
                            translationX = maxOffset * tanh(0.05f * off.x / maxOffset)
                            translationY = maxOffset * tanh(0.05f * off.y / maxOffset)
                            val maxDragScale = 3f.dp.toPx() / h
                            val angle = atan2(off.y, off.x)
                            val maxDim = size.maxDimension.coerceAtLeast(1f)
                            scaleX = s + maxDragScale * abs(cos(angle) * off.x / maxDim) * (w / h).fastCoerceAtMost(1f)
                            scaleY = s + maxDragScale * abs(sin(angle) * off.y / maxDim) * (h / w).fastCoerceAtMost(1f)
                        }
                    }
                } else null,
                onDrawSurface = {
                    if (tint.isSpecified) {
                        drawRect(tint, blendMode = BlendMode.Hue)
                        drawRect(tint.copy(alpha = if (p.isDark) 0.76f else 0.84f))
                    } else {
                        drawRect(defaultSurface)
                    }
                },
            )
            .clickable(
                enabled = enabled,
                interactionSource = null,
                indication = if (enabled && mode == LiquidGlassMode.FULL) null else LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
            .then(
                if (enabled && mode == LiquidGlassMode.FULL) {
                    Modifier.then(interactiveHighlight.modifier).then(interactiveHighlight.gestureModifier)
                } else Modifier,
            )
            .height(height)
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** 完整 Kyant0 风格可拖拽液态玻璃开关（折射镜片滑块 + 色散 + 速度形变）。 */
@Composable
fun LiquidGlassToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop = LocalAmbientBackdrop.current,
) {
    val p = LocalPalette.current
    val mode = LocalLiquidMode.current
    val accentColor = p.primary
    val trackColor = if (p.isDark) Color(0xFF787886).copy(0.38f) else Color(0xFF787882).copy(0.24f)

    if (mode == LiquidGlassMode.OFF) {
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = p.primary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = p.surfaceHigh,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
        return
    }

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val dragWidth = with(density) { 20.dp.toPx() }
    val animationScope = rememberCoroutineScope()
    var didDrag by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(if (checked) 1f else 0f) }

    val dampedDragAnimation = remember(animationScope) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = fraction,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.42f,
            onDragStarted = {},
            onDragStopped = {
                if (didDrag) {
                    val next = targetValue >= 0.5f
                    fraction = if (next) 1f else 0f
                    onCheckedChange(next)
                    didDrag = false
                } else {
                    val next = !checked
                    fraction = if (next) 1f else 0f
                    onCheckedChange(next)
                }
            },
            onDrag = { _, dragAmount ->
                if (!didDrag) didDrag = dragAmount.x != 0f
                val delta = dragAmount.x / dragWidth
                fraction = if (isLtr) (fraction + delta).fastCoerceIn(0f, 1f)
                else (fraction - delta).fastCoerceIn(0f, 1f)
            },
        )
    }

    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { fraction }.collectLatest { dampedDragAnimation.updateValue(it) }
    }
    LaunchedEffect(checked) {
        val target = if (checked) 1f else 0f
        if (target != fraction) {
            fraction = target
            dampedDragAnimation.animateToValue(target)
        }
    }

    val trackBackdrop = rememberLayerBackdrop()

    Box(modifier, contentAlignment = Alignment.CenterStart) {
        Box(
            Modifier
                .layerBackdrop(trackBackdrop)
                .clip(Capsule())
                .drawBehind {
                    drawRect(lerp(trackColor, accentColor, dampedDragAnimation.value))
                }
                .size(58.dp, 28.dp),
        )

        Box(
            Modifier
                .graphicsLayer {
                    val pad = 2.dp.toPx()
                    val f = dampedDragAnimation.value
                    translationX = if (isLtr) lerp(pad, pad + dragWidth, f) else lerp(-pad, -(pad + dragWidth), f)
                }
                .semantics { role = Role.Switch }
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        backdrop,
                        rememberBackdrop(trackBackdrop) { drawBackdrop ->
                            val pr = dampedDragAnimation.pressProgress
                            scale(lerp(2f / 3f, 0.75f, pr), lerp(0f, 0.75f, pr)) { drawBackdrop() }
                        },
                    ),
                    shape = { Capsule() },
                    effects = {
                        val pr = dampedDragAnimation.pressProgress
                        blur(8.dp.toPx() * (1f - pr))
                        if (mode == LiquidGlassMode.FULL) {
                            lens(5.dp.toPx() * pr, 10.dp.toPx() * pr, chromaticAberration = true)
                        }
                    },
                    highlight = {
                        val pr = dampedDragAnimation.pressProgress
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = pr,
                        )
                    },
                    shadow = { Shadow(radius = 4.dp, color = Color.Black.copy(alpha = 0.08f)) },
                    innerShadow = {
                        val pr = dampedDragAnimation.pressProgress
                        InnerShadow(radius = 4.dp * pr, alpha = pr)
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val v = dampedDragAnimation.velocity / 50f
                        scaleX /= 1f - (v * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (v * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val pr = dampedDragAnimation.pressProgress
                        drawRect(Color.White.copy(alpha = 1f - pr))
                    },
                )
                .size(34.dp, 24.dp),
        )
    }
}

/** 液态玻璃浮动容器（用于 TopBar、Composer、FAB、Snackbar 等覆盖在滚动内容之上的组件）。 */
@Composable
fun LiquidChromeBox(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    isCapsule: Boolean = false,
    tint: Color = Color.Unspecified,
    blurRadius: Dp = 14.dp,
    refractionHeight: Dp = 14.dp,
    refractionAmount: Dp = 24.dp,
    chromaticAberration: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val chromeBackdrop = LocalChromeBackdrop.current
    Box(
        modifier = modifier.liquidGlass(
            backdrop = chromeBackdrop,
            cornerRadius = cornerRadius,
            isCapsule = isCapsule,
            tint = tint,
            blurRadius = blurRadius,
            refractionHeight = refractionHeight,
            refractionAmount = refractionAmount,
            chromaticAberration = chromaticAberration,
        ),
        content = content,
    )
}
