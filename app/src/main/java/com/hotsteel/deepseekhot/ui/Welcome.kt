// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Hotsteel2901

package com.hotsteel.deepseekhot.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.NorthEast
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hotsteel.deepseekhot.ui.liquid.LocalAmbientBackdrop
import com.hotsteel.deepseekhot.ui.liquid.liquidGlass
import com.hotsteel.deepseekhot.ui.theme.LocalPalette
import com.hotsteel.deepseekhot.util.Fmt
import kotlinx.coroutines.delay

private data class Suggestion(val icon: ImageVector, val title: String, val prompt: String)

private val suggestions = listOf(
    Suggestion(Icons.Rounded.Public, "DeepSeek V4.1 有什么新变化？", "DeepSeek V4.1 有什么新变化？帮我总结最近的更新"),
    Suggestion(Icons.Rounded.Lightbulb, "用三个类比解释量子纠缠", "用三个类比向高中生解释量子纠缠"),
    Suggestion(Icons.Rounded.Description, "总结一份文档的要点", "帮我总结一份文档的要点：先说明结构，再列出三个关键结论"),
    Suggestion(Icons.Rounded.Terminal, "写一个带超时重试的协程请求", "用 Kotlin 写一个带超时重试的协程请求示例"),
)

/** 空会话页：品牌标识 + 问候 + 示例问题，内容超出时可滚动（横屏、弹出键盘时不会被截断）。 */
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
        delay(30)
        shown = true
    }

    BoxWithConstraints(modifier) {
        val minHeight = maxHeight
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = minHeight),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
        Column(
            Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(
                visible = shown,
                enter = fadeIn(tween(420)) + slideInVertically(tween(420)) { it / 4 },
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BrandMark(52.dp)
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = "${Fmt.greeting()}，我是 DeepSeek",
                        color = p.text,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.3).sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "我可以帮你搜索、答疑、写作、读文件，请把你的任务交给我吧～",
                        color = p.dim,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            if (!hasKey) {
                Spacer(Modifier.height(22.dp))
                AnimatedVisibility(visible = shown, enter = fadeIn(tween(400, delayMillis = 120))) {
                    KeyBanner(onSettings)
                }
            }

            Spacer(Modifier.height(28.dp))

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                suggestions.forEachIndexed { index, item ->
                    AnimatedVisibility(
                        visible = shown,
                        enter = fadeIn(tween(360, delayMillis = 140 + index * 60)) +
                            slideInVertically(tween(380, delayMillis = 140 + index * 60)) { it / 2 },
                    ) {
                        SuggestionRow(item) { onPick(item.prompt) }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun KeyBanner(onSettings: () -> Unit) {
    val p = LocalPalette.current
    Tappable(
        Modifier
            .fillMaxWidth()
            .liquidGlass(
                backdrop = LocalAmbientBackdrop.current,
                cornerRadius = 18.dp,
                tint = p.primary,
            ),
        scaleDown = 0.98f,
        onClick = onSettings,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(p.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Key, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("尚未配置 API Key", color = p.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "前往设置填入 API Key 后即可开始对话",
                    color = p.dim,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, "去设置", tint = p.primary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SuggestionRow(item: Suggestion, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(16.dp)
    Tappable(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .liquidGlass(
                backdrop = LocalAmbientBackdrop.current,
                cornerRadius = 18.dp,
            ),
        scaleDown = 0.98f,
        onClick = onClick,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(item.icon, null, tint = p.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                text = item.title,
                color = p.text,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.NorthEast, null, tint = p.faint, modifier = Modifier.size(15.dp))
        }
    }
}
