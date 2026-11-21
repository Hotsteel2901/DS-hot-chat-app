// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Hotsteel2901

package com.hotsteel.deepseekhot.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.VolumeUp
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hotsteel.deepseekhot.LiveReply
import com.hotsteel.deepseekhot.data.Attachment
import com.hotsteel.deepseekhot.data.Block
import com.hotsteel.deepseekhot.data.ChatMsg
import com.hotsteel.deepseekhot.data.Source
import com.hotsteel.deepseekhot.data.Usage
import com.hotsteel.deepseekhot.ui.theme.LocalPalette
import com.hotsteel.deepseekhot.util.Fmt
import com.hotsteel.deepseekhot.util.Sys
import java.io.File

@Composable
fun UserMessageItem(
    msg: ChatMsg,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    val context = LocalContext.current
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = LocalConfiguration.current.screenWidthDp.dp * 0.82f)
                .clip(RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp))
                .background(
                    Brush.linearGradient(
                        listOf(p.primary.copy(alpha = if (p.isDark) 0.26f else 0.14f), p.primary.copy(alpha = if (p.isDark) 0.14f else 0.08f))
                    )
                )
                .border(1.dp, p.primary.copy(alpha = 0.22f), RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp))
                .pointerInput(msg.id) {
                    detectTapGestures(onLongPress = { onEdit() })
                }
                .padding(horizontal = 15.dp, vertical = 11.dp)
                .animateContentSize(),
        ) {
            val images = msg.attachments.filter { it.isImage && it.localPath != null }
            if (images.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    images.take(3).forEach { att ->
                        AsyncImage(
                            model = File(att.localPath!!),
                            contentDescription = att.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(74.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                    }
                }
                if (msg.text.isNotBlank()) Spacer(Modifier.height(8.dp))
            }
            val files = msg.attachments.filterNot { it.isImage }
            if (files.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    files.forEach { att -> FileChip(att) }
                }
                if (msg.text.isNotBlank()) Spacer(Modifier.height(8.dp))
            }
            if (msg.text.isNotBlank()) {
                Text(
                    text = msg.text,
                    color = p.text,
                    fontSize = 15.5.sp,
                    lineHeight = 24.sp,
                )
            }
        }
        Text(
            text = Fmt.clock(msg.createdAt),
            color = p.faint,
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 4.dp, end = 4.dp),
        )
    }
}

@Composable
private fun FileChip(att: Attachment) {
    val p = LocalPalette.current
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(p.surface.copy(alpha = 0.55f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(p.accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = att.name.substringAfterLast('.', "file").take(3).uppercase(),
                color = p.accent,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(7.dp))
        Text(
            text = att.name,
            color = p.text,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 150.dp),
        )
        if (att.status == "uploading") {
            Spacer(Modifier.width(6.dp))
            TypingDots(size = 4.dp)
        }
    }
}

@Composable
fun AssistantMessageItem(
    msg: ChatMsg,
    blocks: List<Block>,
    status: String,
    usage: Usage?,
    error: String?,
    stopReason: String?,
    ttftMs: Long,
    latencyMs: Long,
    model: String,
    streaming: Boolean,
    isLast: Boolean,
    versions: Int,
    activeVersion: Int,
    speaking: Boolean,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    onContinue: () -> Unit,
    onSpeak: () -> Unit,
    onSwitchVersion: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val text = remember(blocks) {
        blocks.filterIsInstance<Block.Text>().joinToString("") { it.text }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AssistantAvatar()
            Spacer(Modifier.width(9.dp))
            Text(
                text = model.ifBlank { "DeepSeek" },
                color = p.dim,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            if (status == "stopped") {
                Spacer(Modifier.width(7.dp))
                Tag("已停止", p.faint)
            }
            Spacer(Modifier.weight(1f))
            if (versions > 1) {
                VersionSwitcher(activeVersion, versions, onSwitchVersion)
            }
        }
        Spacer(Modifier.height(10.dp))

        blocks.forEach { block ->
            when (block) {
                is Block.Thinking -> ThinkingCard(block, streaming)
                is Block.Tool -> ToolCard(block)
                is Block.Text -> if (block.text.isNotBlank()) {
                    Column(Modifier.padding(bottom = 2.dp)) {
                        SelectionContainer {
                            MarkdownView(block.text, onLink = { Sys.openUrl(context, it) })
                        }
                        if (block.text.isNotBlank() && streaming && block == blocks.lastOrNull()) {
                            Box(Modifier.padding(top = 2.dp)) { BlinkingCursor() }
                        }
                    }
                    if (block.citations.isNotEmpty()) {
                        CitationRow(block.citations)
                    }
                }

                is Block.Refusal -> RefusalCard(block.text)
            }
        }

        if (streaming && blocks.none { it is Block.Text } && blocks.none { it is Block.Thinking }) {
            Row(
                Modifier
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(p.surfaceAlt)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TypingDots()
                Spacer(Modifier.width(10.dp))
                Text("正在生成…", color = p.dim, fontSize = 13.sp)
            }
        }

        if (error != null) {
            ErrorCard(error, onRetry = if (isLast) onRegenerate else null)
        }

        if (!streaming && text.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconAction(Icons.Rounded.ContentCopy, "复制", onCopy)
                IconAction(Icons.Rounded.VolumeUp, if (speaking) "停止朗读" else "朗读", onSpeak, active = speaking)
                if (isLast) {
                    IconAction(Icons.Rounded.Refresh, "重新生成", onRegenerate)
                }
                if (stopReason == "max_tokens" || status == "stopped") {
                    IconAction(Icons.Rounded.PlayArrow, "继续生成", onContinue)
                }
                Spacer(Modifier.weight(1f))
                if (usage != null) {
                    UsageLine(usage, ttftMs, latencyMs)
                }
            }
        }
    }
}

@Composable
fun AssistantAvatar(size: Int = 26) {
    val p = LocalPalette.current
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(p.primary, p.accent.copy(alpha = 0.9f))
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "D",
            color = Color.White,
            fontSize = (size * 0.52f).sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun VersionSwitcher(active: Int, total: Int, onSwitch: (Int) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(p.surfaceAlt)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.KeyboardArrowLeft,
            "上一个版本",
            tint = if (active > 0) p.dim else p.faint.copy(alpha = 0.4f),
            modifier = Modifier
                .size(18.dp)
                .clickable(enabled = active > 0) { onSwitch(active - 1) },
        )
        Text("${active + 1}/$total", color = p.dim, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 2.dp))
        Icon(
            Icons.Rounded.KeyboardArrowRight,
            "下一个版本",
            tint = if (active < total - 1) p.dim else p.faint.copy(alpha = 0.4f),
            modifier = Modifier
                .size(18.dp)
                .clickable(enabled = active < total - 1) { onSwitch(active + 1) },
        )
    }
}

@Composable
private fun ThinkingCard(block: Block.Thinking, streaming: Boolean) {
    val p = LocalPalette.current
    var expanded by remember { mutableStateOf(false) }
    val open = block.endedAt == null
    val seconds = if (block.endedAt != null && block.startedAt > 0) {
        ((block.endedAt!! - block.startedAt) / 1000f).coerceAtLeast(0.1f)
    } else 0f

    LaunchedEffect(open) {
        if (open) expanded = true
        if (!open && expanded) expanded = false
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(p.surfaceAlt.copy(alpha = 0.72f))
            .border(1.dp, p.border, RoundedCornerShape(14.dp))
            .clickable { expanded = !expanded }
            .padding(horizontal = 13.dp, vertical = 10.dp)
            .animateContentSize(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (open) {
                TypingDots(size = 5.dp)
            } else {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(p.accent.copy(alpha = 0.8f))
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (open) "正在深度思考" else "已深度思考 · 用时 ${"%.0f".format(seconds)} 秒",
                color = p.dim,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))
            val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(220), label = "rot")
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = p.faint,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(rotation),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(220)) + fadeIn(tween(180)),
            exit = shrinkVertically(tween(180)) + fadeOut(tween(120)),
        ) {
            Column {
                Spacer(Modifier.height(8.dp))
                SelectionContainer {
                    Text(
                        text = block.text.trim(),
                        color = p.dim,
                        fontSize = 13.sp,
                        lineHeight = 21.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolCard(block: Block.Tool) {
    val p = LocalPalette.current
    var expanded by remember(block.status) { mutableStateOf(false) }
    val running = block.status == "running"
    val isLink = block.kind == "read_link" || block.action == "read_link"
    val title = when {
        running && isLink -> "正在阅读链接"
        running -> if (block.kind.contains("image")) "正在搜索图片" else "正在搜索"
        isLink -> "已阅读链接"
        block.kind.contains("image") -> "图片搜索"
        else -> "已联网搜索"
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(p.surfaceAlt.copy(alpha = 0.65f))
            .border(1.dp, p.border, RoundedCornerShape(14.dp))
            .clickable { expanded = !expanded }
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .animateContentSize(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(p.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isLink) Icons.Rounded.Public else Icons.Rounded.TravelExplore,
                    contentDescription = null,
                    tint = p.primary,
                    modifier = Modifier.size(15.dp),
                )
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = p.text, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                val subtitle = when {
                    running -> block.queries.firstOrNull() ?: block.url ?: "请稍候"
                    block.error != null -> "失败 · ${block.error}"
                    isLink -> block.url?.substringAfter("://")?.take(40) ?: "已获取网页内容"
                    block.sources.isNotEmpty() -> "${block.sources.size} 个来源"
                    else -> "无结果"
                }
                Text(
                    subtitle,
                    color = p.faint,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (running) TypingDots(size = 5.dp)
            else Icon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                null,
                tint = p.faint,
                modifier = Modifier.size(18.dp),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(220)) + fadeIn(tween(160)),
            exit = shrinkVertically(tween(160)) + fadeOut(tween(100)),
        ) {
            Column {
                if (block.queries.isNotEmpty() || block.url != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        block.queries.take(3).forEach { q ->
                            Text(
                                q,
                                color = p.dim,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(p.surface)
                                    .padding(horizontal = 7.dp, vertical = 3.dp),
                            )
                        }
                        if (block.url != null) {
                            Text(
                                block.url.substringAfter("://").take(32),
                                color = p.dim,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(p.surface)
                                    .padding(horizontal = 7.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
                block.sources.take(6).forEach { source ->
                    Spacer(Modifier.height(8.dp))
                    SourceRow(source)
                }
            }
        }
    }
}

@Composable
private fun SourceRow(source: Source) {
    val p = LocalPalette.current
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { source.url?.let { Sys.openUrl(context, it) } }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(p.surface),
            contentAlignment = Alignment.Center,
        ) {
            if (!source.siteIcon.isNullOrBlank()) {
                AsyncImage(
                    model = source.siteIcon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
            } else {
                Text(
                    (source.siteName ?: source.title ?: "?").take(1),
                    color = p.faint,
                    fontSize = 9.sp,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                source.title ?: source.url.orEmpty(),
                color = p.text,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(source.siteName, source.publishedAt?.take(10)).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(meta, color = p.faint, fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun CitationRow(citations: List<com.hotsteel.deepseekhot.data.Citation>) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val scroll = rememberScrollState()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp)
            .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        citations.take(5).forEachIndexed { index, c ->
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(p.surfaceAlt)
                    .border(1.dp, p.border, RoundedCornerShape(8.dp))
                    .clickable { Sys.openUrl(context, c.url) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${index + 1}",
                    color = p.primary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = c.title ?: c.url.substringAfter("://").take(24),
                    color = p.dim,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 130.dp),
                )
            }
        }
    }
}

@Composable
private fun UsageLine(usage: Usage, ttftMs: Long, latencyMs: Long) {
    val p = LocalPalette.current
    val parts = buildList {
        add("↑${Fmt.tokens(usage.input)} ↓${Fmt.tokens(usage.output)}")
        if (usage.cost > 0) add(Fmt.money(usage.cost))
        if (ttftMs > 0) add("首字 ${Fmt.duration(ttftMs)}")
        if (latencyMs > 0 && usage.output > 0) add(Fmt.speed(usage.output, latencyMs))
    }
    Text(
        text = parts.joinToString(" · "),
        color = p.faint,
        fontSize = 10.sp,
        maxLines = 1,
    )
}

@Composable
private fun ErrorCard(message: String, onRetry: (() -> Unit)? = null) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(p.danger.copy(alpha = 0.10f))
            .border(1.dp, p.danger.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            color = p.danger,
            fontSize = 12.5.sp,
            lineHeight = 19.sp,
            modifier = Modifier.weight(1f),
        )
        if (onRetry != null) {
            Text(
                text = "重试",
                color = p.danger,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(p.danger.copy(alpha = 0.14f))
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun RefusalCard(text: String) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(p.danger.copy(alpha = 0.08f))
            .border(1.dp, p.danger.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.GraphicEq, null, tint = p.danger, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = p.danger, fontSize = 13.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun IconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    val p = LocalPalette.current
    Tappable(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        onClick = onClick,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (active) p.primary else p.faint,
            modifier = Modifier.size(17.dp),
        )
    }
}
