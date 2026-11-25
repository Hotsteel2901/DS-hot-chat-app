// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Hotsteel2901

package com.hotsteel.deepseekhot.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hotsteel.deepseekhot.R
import com.hotsteel.deepseekhot.ui.theme.BrandBlue
import com.hotsteel.deepseekhot.ui.theme.LocalPalette

/** 纯色背景。旧版的极光渐变动画会持续重绘整屏，既耗电也和简洁的视觉方向冲突，已移除。 */
@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    color: Color = LocalPalette.current.bg,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.background(color), content = content)
}

/** 品牌标识：全新深海极夜渐变圆底 + 跃动鲸尾极光脉冲图腾。 */
@Composable
fun BrandMark(size: Dp = 28.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF1D358C), Color(0xFF2E54DF), BrandBlue, Color(0xFF6E8BFF))
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.requiredSize(size * 1.42f),
        )
    }
}

/** 圆形图标按钮：带水波纹，触控区域默认 40dp（满足无障碍最小点击面积）。 */
@Composable
fun IconTap(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalPalette.current.dim,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.35f),
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * 胶囊开关，对应官方网页端输入框里的「深度思考」「联网搜索」按钮：
 * 关闭时是描边灰字，开启时是浅品牌蓝底 + 品牌蓝字。
 */
@Composable
fun PillToggle(
    text: String,
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    val bg by animateColorAsState(if (active) p.primarySoft else Color.Transparent, tween(180), label = "pill-bg")
    val fg by animateColorAsState(if (active) p.primary else p.dim, tween(180), label = "pill-fg")
    val stroke by animateColorAsState(
        if (active) p.primary.copy(alpha = 0.35f) else p.border,
        tween(180),
        label = "pill-stroke",
    )
    Row(
        modifier
            .height(34.dp)
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, stroke, CircleShape)
            .clickable(role = Role.Switch, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, color = fg, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** 二级页面（设置、关于）的统一顶栏。 */
@Composable
fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTap(Icons.AutoMirrored.Rounded.ArrowBack, "返回", onBack, tint = p.text, size = 44.dp, iconSize = 22.dp)
        Spacer(Modifier.width(4.dp))
        Text(
            title,
            color = p.text,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

/** 二次确认对话框：删除会话等不可撤销操作使用。 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    val p = LocalPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surface,
        shape = RoundedCornerShape(20.dp),
        title = { Text(title, color = p.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
        text = { Text(message, color = p.dim, fontSize = 14.sp, lineHeight = 21.sp) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = if (destructive) p.danger else p.primary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = p.dim) } },
    )
}

@Composable
fun Modifier.shimmer(): Modifier {
    val p = LocalPalette.current
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -400f,
        targetValue = 1400f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "x",
    )
    return this.background(
        Brush.linearGradient(
            colors = listOf(p.surfaceAlt, p.primary.copy(alpha = 0.35f), p.surfaceAlt),
            start = Offset(x, 0f),
            end = Offset(x + 400f, 0f),
        )
    )
}

@Composable
fun TypingDots(color: Color = LocalPalette.current.primary, size: Dp = 6.dp) {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(size * 0.7f), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val a by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(600, delayMillis = i * 150, easing = LinearEasing),
                    RepeatMode.Reverse,
                ),
                label = "a$i",
            )
            Box(
                Modifier
                    .size(size)
                    .graphicsLayer { alpha = a }
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

/** 流式输出末尾的圆点光标（与官方网页端一致的实心圆点，而不是竖线）。 */
@Composable
fun BlinkingCursor(color: Color = LocalPalette.current.text) {
    val transition = rememberInfiniteTransition(label = "cursor")
    val a by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse),
        label = "a",
    )
    Box(
        Modifier
            .padding(start = 2.dp, top = 4.dp)
            .size(10.dp)
            .graphicsLayer { alpha = a }
            .background(color, CircleShape)
    )
}

/** 按压缩放的可点击容器，给卡片和按钮加入轻微的弹性反馈与水波纹。 */
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
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "scale",
    )
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                enabled = enabled,
                role = Role.Button,
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
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = if (p.isDark) 0.16f else 0.10f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = LocalPalette.current.faint,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.3.sp,
    )
}
