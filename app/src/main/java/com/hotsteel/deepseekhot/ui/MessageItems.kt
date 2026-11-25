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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.TravelExplore
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hotsteel.deepseekhot.data.Attachment
import com.hotsteel.deepseekhot.data.Block
import com.hotsteel.deepseekhot.data.ChatMsg
import com.hotsteel.deepseekhot.data.Citation
import com.hotsteel.deepseekhot.data.Source
import com.hotsteel.deepseekhot.data.Usage
import com.hotsteel.deepseekhot.ui.theme.LocalPalette
import com.hotsteel.deepseekhot.util.Fmt
import com.hotsteel.deepseekhot.util.Sys
import kotlinx.coroutines.delay
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UserMessageItem(
    msg: ChatMsg,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        val bubbleShape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
        Column(
            modifier = Modifier
                .widthIn(max = LocalConfiguration.current.screenWidthDp.dp * 0.82f)
                .clip(bubbleShape)
                .background(p.bubble)
                .combinedClickable(
                    onClick = {},
                    onLongClick = onEdit,
                )
                .padding(horizontal = 15.dp, vertical = 11.dp)
                .animateContentSize(),
        ) {
            val images = msg.attachments.filter { it.isImage && it.localPath != null }
            if (images.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    images.take(4).forEach { att ->
                        AsyncImage(
                            model = File(att.localPath!!),
                            contentDescription = att.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(76.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, p.border, RoundedCornerShape(12.dp)),
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
                SelectionContainer {
                    Text(
                        text = msg.text,
                        color = p.text,
                        fontSize = 15.5.sp,
                        lineHeight = 23.sp,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.padding(top = 4.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (msg.text.isNotBlank()) {
                MessageActionIcon(
                    icon = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                    label = "复制消息",
                    active = copied,
                    activeColor = p.success,
                    onClick = {
                        Sys.copy(context, msg.text)
                        copied = true
                    },
                )
                MessageActionIcon(
                    icon = Icons.Rounded.Edit,
                    label = "编辑消息",
                    onClick = onEdit,
                )
            }
            Text(
                text = Fmt.clock(msg.createdAt),
                color = p.faint,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun FileChip(att: Attachment) {
    val p = LocalPalette.current
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(p.surface.copy(alpha = if (p.isDark) 0.5f else 0.85f))
            .border(1.dp, p.border, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(p.primarySoft),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = att.name.substringAfterLast('.', "file").take(4).uppercase(),
                color = p.primary,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(7.dp))
        Text(
            text = att.name,
            color = p.text,
            fontSize = 12.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 170.dp),
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
    starred: Boolean = false,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    onContinue: () -> Unit,
    onSpeak: () -> Unit,
    onSwitchVersion: (Int) -> Unit,
    onToggleStar: (() -> Unit)? = null,
    onFork: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val text = remember(blocks) {
        blocks.filterIsInstance<Block.Text>().joinToString("") { it.text }
    }
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AssistantAvatar(size = 24)
            Spacer(Modifier.width(9.dp))
            Text(
                text = model.ifBlank { "DeepSeek" },
                color = p.text,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
            )
            if (status == "stopped") {
                Spacer(Modifier.width(8.dp))
                Tag("已停止", p.faint)
            }
            Spacer(Modifier.weight(1f))
            if (versions > 1) {
                VersionSwitcher(activeVersion, versions, onSwitchVersion)
            }
        }
        Spacer(Modifier.height(10.dp))

        blocks.forEachIndexed { index, block ->
            when (block) {
                is Block.Thinking -> ThinkingCard(block, streaming)
                is Block.Tool -> ToolCard(block)
                is Block.Text -> if (block.text.isNotBlank()) {
                    Column(Modifier.padding(bottom = 4.dp)) {
                        SelectionContainer {
                            MarkdownView(block.text, onLink = { Sys.openUrl(context, it) })
                        }
                        if (streaming && index == blocks.lastIndex) {
                            BlinkingCursor()
                        }
                    }
                    if (block.citations.isNotEmpty()) {
                        CitationRow(block.citations)
                    }
                }

                is Block.Refusal -> RefusalCard(block.text)
            }
        }

        if (streaming && blocks.none { it is Block.Text } && blocks.none { it is Block.Thinking } && blocks.none { it is Block.Tool }) {
            Row(
                Modifier
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(p.surfaceAlt)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TypingDots(size = 5.dp)
                Spacer(Modifier.width(9.dp))
                Text("正在思考…", color = p.dim, fontSize = 13.sp)
            }
        }

        if (error != null) {
            ErrorCard(error, onRetry = if (isLast) onRegenerate else null)
        }

        if (!streaming && text.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                MessageActionIcon(
                    icon = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                    label = if (copied) "已复制" else "复制",
                    active = copied,
                    activeColor = p.success,
                    onClick = {
                        onCopy()
                        copied = true
                    },
                )
                MessageActionIcon(
                    icon = if (speaking) Icons.Rounded.StopCircle else Icons.AutoMirrored.Rounded.VolumeUp,
                    label = if (speaking) "停止朗读" else "朗读",
                    active = speaking,
                    onClick = onSpeak,
                )
                if (isLast) {
                    MessageActionIcon(Icons.Rounded.Refresh, "重新生成", onRegenerate)
                }
                if (stopReason == "max_tokens" || status == "stopped") {
                    MessageActionIcon(Icons.Rounded.PlayArrow, "继续生成", onContinue)
                }
                if (onToggleStar != null) {
                    MessageActionIcon(
                        icon = if (starred) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        label = if (starred) "取消收藏" else "收藏回答",
                        active = starred,
                        activeColor = p.primary,
                        onClick = onToggleStar,
                    )
                }
                if (onFork != null) {
                    MessageActionIcon(Icons.Rounded.CallSplit, "从此处分叉新会话", onFork)
                }
                if (onShare != null) {
                    MessageActionIcon(Icons.Rounded.Share, "分享回答", onShare)
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
    BrandMark(size.dp)
}

@Composable
private fun VersionSwitcher(active: Int, total: Int, onSwitch: (Int) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .clip(CircleShape)
            .background(p.surfaceAlt)
            .border(1.dp, p.border, CircleShape)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
            "上一个版本",
            tint = if (active > 0) p.dim else p.faint.copy(alpha = 0.35f),
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .clickable(enabled = active > 0) { onSwitch(active - 1) }
                .padding(1.dp),
        )
        Text(
            "${active + 1}/$total",
            color = p.dim,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            "下一个版本",
            tint = if (active < total - 1) p.dim else p.faint.copy(alpha = 0.35f),
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .clickable(enabled = active < total - 1) { onSwitch(active + 1) }
                .padding(1.dp),
        )
    }
}

/**
 * 深度思考块，采用 DeepSeek 官方网页端的经典视觉语言：
 * 顶部紧凑药丸头 + 展开后左侧竖向引导线配低对比度思考正文。
 */
@Composable
private fun ThinkingCard(block: Block.Thinking, streaming: Boolean) {
    val p = LocalPalette.current
    val open = block.endedAt == null
    var expanded by remember { mutableStateOf(open) }
    val seconds = if (block.endedAt != null && block.startedAt > 0) {
        ((block.endedAt!! - block.startedAt) / 1000f).coerceAtLeast(0.1f)
    } else 0f

    LaunchedEffect(open) {
        if (open) expanded = true
        else if (streaming) expanded = false
    }

    val durationText = when {
        open -> "深度思考中…"
        seconds <= 0f -> "已深度思考"
        seconds < 1f -> "已深度思考（用时 <1 秒）"
        else -> "已深度思考（用时 ${"%.0f".format(seconds)} 秒）"
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .animateContentSize(),
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(p.surfaceAlt)
                .border(1.dp, p.border, RoundedCornerShape(12.dp))
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (open) {
                TypingDots(size = 4.5.dp, color = p.primary)
            } else {
                Icon(
                    Icons.Rounded.Psychology,
                    contentDescription = null,
                    tint = p.dim,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(7.dp))
            Text(
                text = durationText,
                color = p.text,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(6.dp))
            val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(200), label = "rot")
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "收起思考过程" else "展开思考过程",
                tint = p.faint,
                modifier = Modifier
                    .size(17.dp)
                    .rotate(rotation),
            )
        }

        AnimatedVisibility(
            visible = expanded && block.text.isNotBlank(),
            enter = expandVertically(tween(200)) + fadeIn(tween(160)),
            exit = shrinkVertically(tween(160)) + fadeOut(tween(120)),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, start = 4.dp)
                    .height(IntrinsicSize.Min),
            ) {
                Box(
                    Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(1.dp))
                        .background(p.border)
                )
                Spacer(Modifier.width(12.dp))
                SelectionContainer {
                    Text(
                        text = block.text.trim(),
                        color = p.dim,
                        fontSize = 13.5.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolCard(block: Block.Tool) {
    val p = LocalPalette.current
    var expanded by remember(block.id) { mutableStateOf(false) }
    val running = block.status == "running"
    val isLink = block.kind == "read_link" || block.action == "read_link"
    val isImage = block.kind.contains("image")
    val title = when {
        running && isLink -> "正在阅读网页"
        running && isImage -> "正在搜索图片"
        running -> "正在联网搜索"
        isLink -> "已阅读网页"
        isImage -> "图片搜索"
        block.sources.isNotEmpty() -> "已搜索到 ${block.sources.size} 个网页"
        else -> "已联网搜索"
    }
    val subtitle = when {
        running -> block.queries.firstOrNull() ?: block.url ?: "请稍候…"
        block.error != null -> "失败 · ${block.error}"
        isLink -> block.url?.substringAfter("://")?.take(48) ?: "已获取网页内容"
        block.queries.isNotEmpty() -> block.queries.joinToString(" · ")
        block.sources.isEmpty() -> "未找到相关结果"
        else -> ""
    }
    val icon = when {
        isLink -> Icons.Rounded.Link
        isImage -> Icons.Rounded.ImageSearch
        else -> Icons.Rounded.TravelExplore
    }
    val shape = RoundedCornerShape(14.dp)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(shape)
            .background(p.surfaceAlt)
            .border(1.dp, p.border, shape)
            .clickable { expanded = !expanded }
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .animateContentSize(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(p.primarySoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = p.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = p.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        color = p.faint,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
            }
            if (running) {
                TypingDots(size = 4.5.dp, color = p.primary)
            } else {
                val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(200), label = "tool-rot")
                Icon(
                    Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "收起搜索结果" else "展开搜索结果",
                    tint = p.faint,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(rotation),
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(200)) + fadeIn(tween(150)),
            exit = shrinkVertically(tween(150)) + fadeOut(tween(100)),
        ) {
            Column(Modifier.padding(top = 10.dp)) {
                if (block.queries.isNotEmpty() || block.url != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        block.queries.forEach { q ->
                            Text(
                                q,
                                color = p.dim,
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(p.surface)
                                    .border(1.dp, p.border, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                        if (block.url != null) {
                            Text(
                                block.url.substringAfter("://").take(42),
                                color = p.dim,
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(p.surface)
                                    .border(1.dp, p.border, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
                if (block.sources.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    block.sources.take(10).forEachIndexed { idx, source ->
                        SourceRow(index = idx + 1, source = source)
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceRow(index: Int, source: Source) {
    val p = LocalPalette.current
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = !source.url.isNullOrBlank()) {
                source.url?.let { Sys.openUrl(context, it) }
            }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(18.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(p.surface)
                .border(1.dp, p.border, RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (!source.siteIcon.isNullOrBlank()) {
                AsyncImage(
                    model = source.siteIcon,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                )
            } else {
                Text(
                    index.toString(),
                    color = p.primary,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                source.title ?: source.url.orEmpty(),
                color = p.text,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!source.snippet.isNullOrBlank()) {
                Text(
                    source.snippet,
                    color = p.dim,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            val meta = listOfNotNull(source.siteName, source.publishedAt?.take(10)).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(meta, color = p.faint, fontSize = 10.5.sp, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
private fun CitationRow(citations: List<Citation>) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val scroll = rememberScrollState()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp)
            .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        citations.take(12).forEachIndexed { index, c ->
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(p.surfaceAlt)
                    .border(1.dp, p.border, CircleShape)
                    .clickable { Sys.openUrl(context, c.url) }
                    .padding(start = 5.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(p.primarySoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${index + 1}",
                        color = p.primary,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = c.title ?: c.url.substringAfter("://").take(26),
                    color = p.dim,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 150.dp),
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
        fontSize = 10.5.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 8.dp),
    )
}

@Composable
private fun ErrorCard(message: String, onRetry: (() -> Unit)? = null) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(shape)
            .background(p.danger.copy(alpha = 0.08f))
            .border(1.dp, p.danger.copy(alpha = 0.25f), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.ErrorOutline, null, tint = p.danger, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(9.dp))
        Text(
            text = message,
            color = p.danger,
            fontSize = 13.sp,
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
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(p.danger.copy(alpha = 0.07f))
            .border(1.dp, p.danger.copy(alpha = 0.22f), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.GppMaybe, null, tint = p.danger, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = p.danger, fontSize = 13.5.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun MessageActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    active: Boolean = false,
    activeColor: androidx.compose.ui.graphics.Color = LocalPalette.current.primary,
) {
    val p = LocalPalette.current
    IconTap(
        icon = icon,
        contentDescription = label,
        onClick = onClick,
        tint = if (active) activeColor else p.faint,
        size = 32.dp,
        iconSize = 17.dp,
    )
}
