// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Hotsteel2901

package com.hotsteel.deepseekhot.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hotsteel.deepseekhot.AppViewModel
import com.hotsteel.deepseekhot.data.ChatMsg
import com.hotsteel.deepseekhot.data.Conversation
import com.hotsteel.deepseekhot.data.PromptPreset
import com.hotsteel.deepseekhot.data.PromptPresets
import com.hotsteel.deepseekhot.ui.theme.LocalPalette
import com.hotsteel.deepseekhot.util.Fmt
import com.hotsteel.deepseekhot.util.Sys

/** 快捷斜杠指令定义 */
data class SlashCommand(val trigger: String, val label: String, val template: String)

val DefaultSlashCommands = listOf(
    SlashCommand("/总结", "核心总结", "请用分点清单提炼以下内容的核心结论与关键信息：\n"),
    SlashCommand("/翻译", "地道翻译", "请将以下内容自然地道地翻译（中英自动互译）：\n"),
    SlashCommand("/润色", "专业润色", "请润色以下文字，提升表达清晰度与专业感，保留原意：\n"),
    SlashCommand("/代码", "代码解释", "请逐段解释以下代码的设计思路、边界条件与可优化点：\n"),
    SlashCommand("/审查", "Bug 审查", "请审查以下代码中的潜在 Bug、性能隐患与安全问题，并给出修复代码：\n"),
    SlashCommand("/大纲", "生成大纲", "请为以下主题拟定一份逻辑清晰的结构化大纲：\n"),
    SlashCommand("/对比", "方案对比", "请用 Markdown 表格从优缺点、适用场景与成本维度对比以下方案：\n"),
)

/**
 * 全局悬浮迷你朗读控制条：贴合 2026 DeepSeek 官方风格，
 * 支持音色展示、暂停/继续、倍速循环（0.75x–2.0x）与一键停止。
 */
@Composable
fun TtsControlBar(
    visible: Boolean,
    preparing: Boolean,
    paused: Boolean,
    voice: String,
    speed: Float,
    onTogglePause: () -> Unit,
    onCycleSpeed: () -> Unit,
    onStop: () -> Unit,
) {
    val p = LocalPalette.current
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(p.primarySoft)
                    .border(1.dp, p.primary.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = null,
                    tint = p.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = when {
                            preparing -> "正在流式合成语音…"
                            paused -> "朗读已暂停"
                            else -> "正在朗读回答"
                        },
                        color = p.text,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Text(
                        text = "音色：${if (voice == "tide") "暗潮" else "海星"} · 24kHz PCM 流式",
                        color = p.dim,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
                // 倍速胶囊
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(p.surface)
                        .border(1.dp, p.border, CircleShape)
                        .clickable(onClick = onCycleSpeed)
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "${speed}x",
                        color = p.primary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.width(6.dp))
                IconTap(
                    icon = if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    contentDescription = if (paused) "继续朗读" else "暂停朗读",
                    onClick = onTogglePause,
                    tint = p.primary,
                    size = 34.dp,
                    iconSize = 19.dp,
                )
                IconTap(
                    icon = Icons.Rounded.Stop,
                    contentDescription = "停止朗读",
                    onClick = onStop,
                    tint = p.danger,
                    size = 34.dp,
                    iconSize = 19.dp,
                )
            }
        }
    }
}

/** 会话内实时搜索与定位跳转栏。 */
@Composable
fun ChatSearchBar(
    visible: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    matchCount: Int,
    currentMatch: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    val p = LocalPalette.current
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(p.surfaceAlt)
                    .border(1.dp, p.border, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = p.dim, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text("在当前对话中搜索关键词…", color = p.faint, fontSize = 13.5.sp)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(color = p.text, fontSize = 13.5.sp),
                        cursorBrush = SolidColor(p.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) {
                    Text(
                        text = if (matchCount > 0) "${currentMatch + 1}/$matchCount" else "0/0",
                        color = p.dim,
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                    IconTap(Icons.Rounded.KeyboardArrowUp, "上一条", onPrev, size = 30.dp, iconSize = 18.dp, enabled = matchCount > 0)
                    IconTap(Icons.Rounded.KeyboardArrowDown, "下一条", onNext, size = 30.dp, iconSize = 18.dp, enabled = matchCount > 0)
                }
                IconTap(Icons.Rounded.Close, "关闭搜索", onClose, size = 30.dp, iconSize = 17.dp)
            }
        }
    }
}

/** 输入框 `/` 快捷指令条。 */
@Composable
fun SlashCommandBar(
    visible: Boolean,
    filter: String,
    onPick: (SlashCommand) -> Unit,
) {
    val p = LocalPalette.current
    val matches = DefaultSlashCommands.filter {
        filter == "/" || it.trigger.contains(filter, ignoreCase = true) || it.label.contains(filter.removePrefix("/"), ignoreCase = true)
    }
    AnimatedVisibility(
        visible = visible && matches.isNotEmpty(),
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Row(
            Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            matches.forEach { cmd ->
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(p.surface)
                        .border(1.dp, p.primary.copy(alpha = 0.35f), CircleShape)
                        .clickable { onPick(cmd) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(cmd.trigger, color = p.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(5.dp))
                    Text(cmd.label, color = p.dim, fontSize = 12.sp)
                }
            }
        }
    }
}

/** 提示词与角色工坊底栏。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptPresetSheet(
    activeId: String,
    onSelect: (PromptPreset) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalPalette.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = p.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 26.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = p.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("角色与提示词工坊", color = p.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(p.primarySoft)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Bolt, null, tint = p.primary, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "每套角色均采用固定静态系统前缀，新会话也可直接复用 DeepSeek 硬盘缓存",
                    color = p.primary,
                    fontSize = 11.5.sp,
                )
            }
            Spacer(Modifier.height(14.dp))
            PromptPresets.defaults.forEach { preset ->
                val selected = preset.id == activeId
                val shape = RoundedCornerShape(14.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 9.dp)
                        .clip(shape)
                        .background(if (selected) p.primarySoft else p.surfaceAlt)
                        .border(1.dp, if (selected) p.primary.copy(alpha = 0.45f) else p.border, shape)
                        .clickable {
                            onSelect(preset)
                            onDismiss()
                        }
                        .padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(preset.name, color = p.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(6.dp))
                            Tag(preset.badge, p.primary)
                        }
                        Text(
                            preset.description,
                            color = p.dim,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    if (selected) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Rounded.Check, null, tint = p.primary, modifier = Modifier.size(19.dp))
                    }
                }
            }
        }
    }
}

/** 收藏夹底栏：集中管理跨会话收藏的精彩回答。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksSheet(
    items: List<Pair<Conversation, ChatMsg>>,
    onOpenConversation: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = p.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 26.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Bookmark, null, tint = p.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("我的收藏（${items.size}）", color = p.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(14.dp))
            if (items.isEmpty()) {
                Text(
                    "还没有收藏任何回答。点击任意 AI 回答底部的星标按钮即可一键收藏。",
                    color = p.faint,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                items.forEach { (conv, msg) ->
                    val shape = RoundedCornerShape(14.dp)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .clip(shape)
                            .background(p.surfaceAlt)
                            .border(1.dp, p.border, shape)
                            .clickable {
                                onOpenConversation(conv.id)
                                onDismiss()
                            }
                            .padding(13.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                conv.title.ifBlank { "未命名对话" },
                                color = p.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(Fmt.relative(msg.createdAt), color = p.faint, fontSize = 11.sp)
                            Spacer(Modifier.width(6.dp))
                            IconTap(
                                icon = Icons.Rounded.ContentCopy,
                                contentDescription = "复制收藏内容",
                                onClick = { Sys.copy(context, msg.plainText, "已复制收藏内容") },
                                size = 28.dp,
                                iconSize = 15.dp,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = msg.plainText.trim(),
                            color = p.text,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
