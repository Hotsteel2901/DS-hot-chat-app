// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Hotsteel2901

package com.hotsteel.deepseekhot.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hotsteel.deepseekhot.ui.theme.LocalPalette
import com.hotsteel.deepseekhot.util.Fmt
import kotlinx.coroutines.delay

private data class Suggestion(val icon: ImageVector, val title: String, val prompt: String)

private val suggestions = listOf(
    Suggestion(Icons.Rounded.Public, "DeepSeek V4.1 有什么新变化？", "DeepSeek V4.1 有什么新变化？帮我总结最近的更新"),
    Suggestion(Icons.Rounded.Bolt, "解释一个概念", "用三个类比向高中生解释量子纠缠"),
    Suggestion(Icons.Rounded.Description, "总结一份文档", "帮我总结一份文档的要点：先说明结构，再列出三个关键结论"),
    Suggestion(Icons.Rounded.Terminal, "写一段代码", "用 Kotlin 写一个带超时重试的协程请求示例"),
)

@Composable
fun Welcome(
    hasKey: Boolean,
    onPick: (String) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(40)
        shown = true
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedVisibility(
            visible = shown,
            enter = fadeIn(tween(500)) + scaleIn(initialScale = 0.8f, animationSpec = spring(Spring.DampingRatioMediumBouncy)),
        ) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(p.primary, p.accent))),
                contentAlignment = Alignment.Center,
            ) {
                Text("D", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(18.dp))
        AnimatedVisibility(
            visible = shown,
            enter = fadeIn(tween(500, delayMillis = 80)) + slideInVertically(tween(500, delayMillis = 80)) { it / 3 },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${Fmt.greeting()}，我是 DeepSeek",
                    color = p.text,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "联网搜索 · 深度思考 · 文件解析 · 语音对话",
                    color = p.dim,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }

        if (!hasKey) {
            Spacer(Modifier.height(22.dp))
            AnimatedVisibility(visible = shown, enter = fadeIn(tween(400, delayMillis = 160))) {
                Tappable(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(p.primary)
                        .padding(horizontal = 18.dp, vertical = 11.dp),
                    onClick = onSettings,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Key, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("填入 API Key 开始使用", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        suggestions.chunked(2).forEachIndexed { rowIndex, row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEachIndexed { colIndex, item ->
                    val order = rowIndex * 2 + colIndex
                    AnimatedVisibility(
                        visible = shown,
                        enter = fadeIn(tween(380, delayMillis = 200 + order * 80)) +
                            slideInVertically(tween(420, delayMillis = 200 + order * 80)) { it / 2 },
                        modifier = Modifier.weight(1f),
                    ) {
                        SuggestionCard(item) { onPick(item.prompt) }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun SuggestionCard(item: Suggestion, onClick: () -> Unit) {
    val p = LocalPalette.current
    Tappable(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 116.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(p.surface.copy(alpha = 0.66f))
            .border(1.dp, p.border, RoundedCornerShape(18.dp))
            .padding(14.dp),
        onClick = onClick,
    ) {
        Column {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(p.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(item.icon, null, tint = p.primary, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = item.title,
                color = p.text,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
