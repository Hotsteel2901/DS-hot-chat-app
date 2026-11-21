// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Hotsteel2901

package com.hotsteel.deepseekhot.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hotsteel.deepseekhot.ui.theme.LocalPalette
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val p = LocalPalette.current
    val transition = rememberInfiniteTransition(label = "aurora")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(26_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "t",
    )
    val strong = if (p.isDark) 0.30f else 0.24f

    Box(
        modifier = modifier.background(
            Brush.verticalGradient(listOf(p.bg, p.bgAlt, p.bg))
        )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val c1 = Offset(w * (0.12f + 0.18f * t), h * (0.06f + 0.10f * t))
            val r1 = w * 0.95f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(p.glowA.copy(alpha = strong), Color.Transparent),
                    center = c1,
                    radius = r1,
                ),
                radius = r1,
                center = c1,
            )
            val c2 = Offset(w * (0.95f - 0.20f * t), h * (0.22f + 0.12f * t))
            val r2 = w * 0.75f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(p.glowB.copy(alpha = strong * 0.75f), Color.Transparent),
                    center = c2,
                    radius = r2,
                ),
                radius = r2,
                center = c2,
            )
            val angle = t * 6.2832f
            val c3 = Offset(w * 0.5f + cos(angle) * w * 0.3f, h * 0.85f + sin(angle) * h * 0.08f)
            val r3 = w * 0.85f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(p.primary.copy(alpha = strong * 0.35f), Color.Transparent),
                    center = c3,
                    radius = r3,
                ),
                radius = r3,
                center = c3,
            )
        }
        content()
    }
}

@Composable
fun glassModifier(shape: RoundedCornerShape = RoundedCornerShape(22.dp)): Modifier {
    val p = LocalPalette.current
    return Modifier
        .clip(shape)
        .background(p.surface.copy(alpha = if (p.isDark) 0.72f else 0.86f))
        .border(1.dp, p.border, shape)
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(22.dp),
    padding: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(LocalPalette.current.surface.copy(alpha = 0.78f))
            .border(1.dp, LocalPalette.current.border, shape)
            .padding(padding),
        content = content,
    )
}

@Composable
fun Modifier.shimmer(): Modifier {
    val p = LocalPalette.current
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -400f,
        targetValue = 1400f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "x",
    )
    return this.background(
        Brush.linearGradient(
            colors = listOf(p.surfaceAlt, p.surfaceHigh, p.surfaceAlt),
            start = Offset(x, 0f),
            end = Offset(x + 400f, 400f),
        )
    )
}

@Composable
fun TypingDots(color: Color = LocalPalette.current.accent, size: Dp = 6.dp) {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val a by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(700, delayMillis = i * 160, easing = LinearEasing),
                    RepeatMode.Reverse,
                ),
                label = "a$i",
            )
            val scale by transition.animateFloat(
                initialValue = 0.7f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(700, delayMillis = i * 160, easing = LinearEasing),
                    RepeatMode.Reverse,
                ),
                label = "s$i",
            )
            Box(
                Modifier
                    .size(size)
                    .graphicsLayer { scaleX = scale; scaleY = scale; alpha = a }
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
fun BlinkingCursor(color: Color = LocalPalette.current.primary) {
    val transition = rememberInfiniteTransition(label = "cursor")
    val a by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(560, easing = LinearEasing), RepeatMode.Reverse),
        label = "a",
    )
    Box(
        Modifier
            .padding(start = 2.dp)
            .size(width = 2.dp, height = 16.dp)
            .graphicsLayer { alpha = a }
            .background(color, RoundedCornerShape(1.dp))
    )
}

/** 按压缩放的可点击容器，给卡片和按钮加入轻微的弹性反馈。 */
@Composable
fun Tappable(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    scaleDown: Float = 0.97f,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) scaleDown else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "scale",
    )
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        content = content,
    )
}

@Composable
fun Tag(
    text: String,
    color: Color = LocalPalette.current.primary,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(color.copy(alpha = if (p.isDark) 0.14f else 0.10f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
fun DotDivider(color: Color = LocalPalette.current.faint) {
    Text("·", color = color, fontSize = 12.sp)
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = LocalPalette.current.faint,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
    )
}
