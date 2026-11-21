// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Hotsteel2901

package com.hotsteel.deepseekhot.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import com.hotsteel.deepseekhot.AppViewModel
import com.hotsteel.deepseekhot.LiveReply
import com.hotsteel.deepseekhot.StreamMode
import com.hotsteel.deepseekhot.data.Block
import com.hotsteel.deepseekhot.data.ChatMsg
import com.hotsteel.deepseekhot.data.Protocol
import com.hotsteel.deepseekhot.ui.theme.LocalPalette
import com.hotsteel.deepseekhot.util.Fmt
import com.hotsteel.deepseekhot.util.Sys
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    vm: AppViewModel,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onShowModels: () -> Unit,
    onShowUsage: () -> Unit,
) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val conv = vm.current
    val listState = rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    var editing by remember { mutableStateOf<ChatMsg?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    val pickFiles = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) vm.attach(uris) }

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> if (uris.isNotEmpty()) vm.attach(uris) }

    val messageCount = conv?.messages?.size ?: 0

    // 贴底判定直接用「还能不能继续往下滚」，比数最后一项的下标可靠
    val atBottom by remember { derivedStateOf { !listState.canScrollForward } }
    var autoFollow by remember { mutableStateOf(true) }
    var dragging by remember { mutableStateOf(false) }

    // 用户手指拖动时暂停跟随；松手后若已离开底部就停止跟随
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> dragging = true
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    dragging = false
                    withFrameNanos { }
                    autoFollow = !listState.canScrollForward
                }
            }
        }
    }

    // 惯性滑动停下后，如果已经回到最底部就恢复跟随
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (!scrolling && !dragging) {
                    withFrameNanos { }
                    if (!listState.canScrollForward) autoFollow = true
                }
            }
    }

    LaunchedEffect(listState, conv?.id) {
        autoFollow = true
        dragging = false
        withFrameNanos { }
        if ((vm.current?.messages?.size ?: 0) > 0) jumpToBottom(listState)

        var lastCount = vm.current?.messages?.size ?: 0
        snapshotFlow {
            ScrollTick(
                text = vm.live?.text?.length ?: 0,
                blocks = vm.live?.blocks?.size ?: 0,
                count = vm.current?.messages?.size ?: 0,
            )
        }.collect { tick ->
            val appended = tick.count > lastCount
            lastCount = tick.count
            // 新消息（发送、重生成落地）总是回到底部
            if (appended) autoFollow = true
            if (dragging || !autoFollow) return@collect
            pinToBottom(listState)
        }
    }

    Column(
        Modifier.fillMaxSize()
    ) {
        TopBar(
            title = conv?.title?.ifBlank { "新对话" } ?: "DeepSeek Hot",
            model = vm.models.firstOrNull { it.id == vm.settings.model }?.displayName ?: vm.settings.model,
            protocol = vm.settings.protocol,
            hasKey = vm.settings.apiKey.isNotBlank(),
            balanceText = vm.balance?.infos?.firstOrNull()?.let { "¥${it.total}" },
            onMenu = onOpenDrawer,
            onSettings = onOpenSettings,
            onModels = onShowModels,
            onUsage = onShowUsage,
            onChatMenu = { menuOpen = true },
            showChatMenu = conv != null,
        )

        Box(Modifier.weight(1f)) {
            if (conv == null || conv.messages.isEmpty()) {
                Welcome(
                    hasKey = vm.settings.apiKey.isNotBlank(),
                    onPick = { text ->
                        vm.send(text, emptyList())
                    },
                    onSettings = onOpenSettings,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 18.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    itemsIndexed(conv.messages, key = { _, m -> m.id }) { index, msg ->
                        val isLast = index == conv.messages.lastIndex
                        val live = vm.live
                        val hideForRegen = live != null &&
                            live.mode == StreamMode.REGENERATE && live.targetId == msg.id
                        if (!hideForRegen) {
                            if (msg.role == "user") {
                                UserMessageItem(
                                    msg = msg,
                                    onEdit = { editing = msg },
                                    modifier = Modifier.animateItem(),
                                )
                            } else {
                                val reply = msg.reply
                                AssistantMessageItem(
                                    msg = msg,
                                    blocks = reply?.blocks.orEmpty(),
                                    status = reply?.status ?: "done",
                                    usage = reply?.usage,
                                    error = reply?.error,
                                    stopReason = reply?.stopReason,
                                    ttftMs = reply?.ttftMs ?: 0,
                                    latencyMs = reply?.latencyMs ?: 0,
                                    model = displayName(vm, reply?.model ?: vm.settings.model),
                                    streaming = false,
                                    isLast = isLast && vm.live == null,
                                    versions = msg.replies.size,
                                    activeVersion = msg.active,
                                    speaking = vm.tts.playingId == msg.id,
                                    onCopy = {
                                        Sys.copy(context, msg.plainText)
                                    },
                                    onRegenerate = { vm.regenerate(msg.id) },
                                    onContinue = { vm.continueReply(msg.id) },
                                    onSpeak = { vm.speak(msg.id, msg.plainText) },
                                    onSwitchVersion = { i ->
                                        vm.switchVersion(msg.id, i)
                                    },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }

                    vm.live?.let { live ->
                        item(key = "live") {
                            LiveMessageItem(vm, live)
                        }
                    }
                }
            }

            val fab by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (!atBottom && messageCount > 2) 1f else 0f,
                animationSpec = tween(180),
                label = "fab",
            )
            if (fab > 0.01f) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 12.dp)
                        .graphicsLayer { alpha = fab; scaleX = fab; scaleY = fab }
                ) {
                    Tappable(
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(p.surfaceHigh)
                            .border(1.dp, p.border, CircleShape),
                        onClick = {
                            autoFollow = true
                            scope.launch { animateToBottom(listState) }
                        },
                    ) {
                        Icon(
                            Icons.Rounded.ArrowDownward,
                            contentDescription = "回到底部",
                            tint = p.dim,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(18.dp),
                        )
                    }
                }
            }
        }

        if (vm.streaming) {
            vm.live?.let { StreamingStatus(it) }
        }

        QuickBar(
            modelName = vm.models.firstOrNull { it.id == vm.settings.model }?.displayName ?: vm.settings.model,
            effort = vm.settings.effort,
            webSearch = vm.settings.webSearch,
            readUrl = vm.settings.readUrl,
            onModel = onShowModels,
            onEffort = { onShowModels() },
            onToggleWeb = { vm.updateSettings { s -> s.copy(webSearch = !s.webSearch) } },
            onToggleRead = { vm.updateSettings { s -> s.copy(readUrl = !s.readUrl) } },
        )

        Composer(
            text = vm.pendingText,
            onTextChange = { vm.pendingText = it },
            attachments = vm.pending,
            onRemove = { vm.removePending(it) },
            onPickFile = { pickFiles.launch(arrayOf("*/*")) },
            onPickImage = { pickImages.launch("image/*") },
            streaming = vm.streaming,
            uploading = vm.uploading,
            asrBusy = vm.asrBusy,
            allowAttach = vm.gateway.supportsFiles,
            allowVoice = vm.gateway.supportsVoice,
            onSend = {
                when {
                    vm.settings.apiKey.isBlank() -> vm.showToast("请先在设置中填写 API Key")
                    vm.pending.any { it.status == "uploading" } -> vm.showToast("文件还在上传解析中，请稍候")
                    else -> {
                        if (vm.settings.haptics) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        val text = vm.pendingText
                        val atts = vm.pending.filter { it.status == "ready" }
                        vm.pendingText = ""
                        vm.clearPending()
                        vm.send(text, atts)
                    }
                }
            },
            onStop = {
                if (vm.settings.haptics) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                vm.stopStreaming()
            },
            onTranscribe = { file -> vm.transcribe(file) { text -> vm.pendingText = text } },
        )
    }

    if (menuOpen && conv != null) {
        ConversationMenu(
            expanded = true,
            pinned = conv.pinned,
            onDismiss = { menuOpen = false },
            onRename = {
                menuOpen = false
                renaming = true
            },
            onPin = {
                menuOpen = false
                vm.togglePin(conv.id)
            },
            onExport = {
                menuOpen = false
                vm.exportJson { text -> Sys.copy(context, text, "会话 JSON 已复制") }
            },
            onDelete = {
                menuOpen = false
                vm.deleteChat(conv.id)
            },
        )
    }

    editing?.let { msg ->
        EditDialog(
            initial = msg.text,
            onDismiss = { editing = null },
            onConfirm = { newText ->
                editing = null
                vm.editUserMessage(msg.id, newText)
            },
        )
    }

    if (renaming && conv != null) {
        EditDialog(
            title = "重命名会话",
            initial = conv.title,
            singleLine = true,
            onDismiss = { renaming = false },
            onConfirm = { newTitle ->
                renaming = false
                vm.renameChat(conv.id, newTitle)
            },
        )
    }

}

@Composable
private fun StreamingStatus(live: LiveReply) {
    val p = LocalPalette.current
    var now by remember(live) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(live) {
        while (true) {
            kotlinx.coroutines.delay(120)
            now = System.currentTimeMillis()
        }
    }
    val elapsed = (now - live.startedAt).coerceAtLeast(0)
    val tokens = (live.text.length / 2.2).toInt()
    val speed = if (elapsed > 0 && tokens > 0) tokens * 1000f / elapsed else 0f
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TypingDots(size = 4.dp, color = p.accent)
        Spacer(Modifier.width(7.dp))
        Text(
            text = buildString {
                append(if (live.blocks.any { it is Block.Thinking && it.endedAt == null }) "正在思考" else "正在生成")
                append(" · ")
                append(Fmt.duration(elapsed))
                if (tokens > 0) {
                    append(" · ~")
                    append(tokens)
                    append(" tok")
                }
                if (speed > 0) {
                    append(" · ")
                    append("%.1f".format(speed))
                    append(" tok/s")
                }
            },
            color = p.faint,
            fontSize = 10.sp,
        )
    }
}

private fun displayName(vm: AppViewModel, id: String): String =
    vm.models.firstOrNull { it.id == id }?.displayName ?: id

@Composable
private fun TopBar(
    title: String,
    model: String,
    protocol: Protocol,
    hasKey: Boolean,
    balanceText: String?,
    onMenu: () -> Unit,
    onSettings: () -> Unit,
    onModels: () -> Unit,
    onUsage: () -> Unit,
    onChatMenu: () -> Unit,
    showChatMenu: Boolean,
) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tappable(
            Modifier
                .size(38.dp)
                .clip(CircleShape),
            onClick = onMenu,
        ) {
            Icon(
                Icons.Rounded.Menu,
                "会话列表",
                tint = p.text,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(21.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = p.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(model, color = p.faint, fontSize = 10.5.sp, maxLines = 1)
                Spacer(Modifier.width(5.dp))
                Text("·", color = p.faint, fontSize = 10.sp)
                Spacer(Modifier.width(5.dp))
                Text(protocol.short, color = p.faint, fontSize = 10.5.sp)
                if (!hasKey) {
                    Spacer(Modifier.width(6.dp))
                    Text("未配置 Key", color = p.danger, fontSize = 10.5.sp)
                }
            }
        }
        if (balanceText != null) {
            Tappable(
                Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(p.surface.copy(alpha = 0.6f))
                    .border(1.dp, p.border, RoundedCornerShape(9.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                onClick = onUsage,
            ) {
                Text(balanceText, color = p.success, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.width(4.dp))
        }
        TopBarAction(Icons.Rounded.AutoAwesome, "选择模型", onModels)
        if (showChatMenu) {
            TopBarAction(Icons.Rounded.MoreHoriz, "更多", onChatMenu)
        }
        TopBarAction(Icons.Rounded.Settings, "设置", onSettings)
    }
}

@Composable
private fun TopBarAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    Tappable(
        Modifier
            .size(36.dp)
            .clip(CircleShape),
        onClick = onClick,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = p.dim,
            modifier = Modifier
                .align(Alignment.Center)
                .size(19.dp),
        )
    }
}

@Composable
private fun ConversationMenu(
    expanded: Boolean,
    pinned: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onPin: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val p = LocalPalette.current
    Box {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
            containerColor = p.surfaceHigh,
        ) {
            DropdownMenuItem(
                text = { Text("重命名") },
                leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, null, tint = p.dim) },
                onClick = onRename,
            )
            DropdownMenuItem(
                text = { Text(if (pinned) "取消置顶" else "置顶会话") },
                leadingIcon = { Icon(Icons.Rounded.PushPin, null, tint = p.dim) },
                onClick = onPin,
            )
            DropdownMenuItem(
                text = { Text("导出 JSON") },
                leadingIcon = { Icon(Icons.Rounded.GridView, null, tint = p.dim) },
                onClick = onExport,
            )
            DropdownMenuItem(
                text = { Text("删除会话", color = p.danger) },
                leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = p.danger) },
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun QuickBar(
    modelName: String,
    effort: String,
    webSearch: Boolean,
    readUrl: Boolean,
    onModel: () -> Unit,
    onEffort: () -> Unit,
    onToggleWeb: () -> Unit,
    onToggleRead: () -> Unit,
) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QuickChip(
            text = modelName.removePrefix("DeepSeek ").take(18),
            active = true,
            icon = Icons.Rounded.AutoAwesome,
            onClick = onModel,
        )
        QuickChip(
            text = when (effort) {
                "none" -> "不思考"
                "low" -> "低思考"
                "high" -> "高思考"
                "max" -> "极限思考"
                else -> effort
            },
            active = effort != "none",
            icon = Icons.Rounded.Language,
            onClick = onEffort,
        )
        Spacer(Modifier.weight(1f))
        QuickChip(
            text = "联网",
            active = webSearch,
            icon = Icons.Rounded.TravelExplore,
            onClick = onToggleWeb,
            compact = true,
        )
        QuickChip(
            text = "链接",
            active = readUrl,
            icon = Icons.Rounded.Check,
            onClick = onToggleRead,
            compact = true,
        )
    }
}

@Composable
private fun QuickChip(
    text: String,
    active: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    compact: Boolean = false,
) {
    val p = LocalPalette.current
    val bg by androidx.compose.animation.animateColorAsState(
        targetValue = if (active) p.primary.copy(alpha = 0.16f) else p.surface.copy(alpha = 0.55f),
        animationSpec = tween(220),
        label = "bg",
    )
    val fg by androidx.compose.animation.animateColorAsState(
        targetValue = if (active) p.primary else p.faint,
        animationSpec = tween(220),
        label = "fg",
    )
    Tappable(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .border(
                1.dp,
                (if (active) p.primary.copy(alpha = 0.3f) else p.border),
                RoundedCornerShape(9.dp),
            )
            .padding(horizontal = if (compact) 8.dp else 9.dp, vertical = 5.dp),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(text, color = fg, fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
private fun LiveMessageItem(vm: AppViewModel, live: LiveReply) {
    val p = LocalPalette.current
    val context = LocalContext.current
    Column {
        if (live.mode == StreamMode.CONTINUE) {
            Text("续写中…", color = p.faint, fontSize = 11.sp, modifier = Modifier.padding(start = 36.dp, bottom = 6.dp))
        }
        AssistantMessageItem(
            msg = ChatMsg(role = "assistant", id = "live"),
            blocks = live.blocks.toList(),
            status = live.status,
            usage = live.usage,
            error = live.error,
            stopReason = live.stopReason,
            ttftMs = live.ttftMs,
            latencyMs = live.elapsedMs,
            model = displayName(vm, live.model.ifBlank { vm.settings.model }),
            streaming = true,
            isLast = false,
            versions = 1,
            activeVersion = 0,
            speaking = false,
            onCopy = {},
            onRegenerate = {},
            onContinue = {},
            onSpeak = {},
            onSwitchVersion = {},
        )
    }
}

@Composable
private fun EditDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    title: String = "编辑消息",
    singleLine: Boolean = false,
) {
    val p = LocalPalette.current
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surfaceHigh,
        title = { Text(title, color = p.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            TextField(
                value = value,
                onValueChange = { value = it },
                singleLine = singleLine,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = p.surfaceAlt,
                    unfocusedContainerColor = p.surfaceAlt,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = p.text,
                    unfocusedTextColor = p.text,
                    cursorColor = p.primary,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (value.isNotBlank()) onConfirm(value) }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = p.dim) } },
    )
}

/** 流式输出时用于触发跟随滚动的信号：正文长度、块数量、消息条数任一变化都算。 */
private data class ScrollTick(val text: Int, val blocks: Int, val count: Int)

/**
 * 把列表钉在底部。
 *
 * 不能简单 scrollToItem(lastIndex)——当最后一条消息比屏幕还高时，那只会把它的顶部对齐到
 * 屏幕上沿，流式追加的文字会一路掉到屏幕外。这里比较最后一项底边与视口底边的差值，
 * 多出来多少就补滚多少，等价于「始终盯着最新一行」。
 */
private suspend fun pinToBottom(state: LazyListState) {
    repeat(2) {
        withFrameNanos { }
        val info = state.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull() ?: return
        val overflow = (last.offset + last.size) - info.viewportEndOffset
        if (overflow <= 0) return
        state.scrollBy(overflow.toFloat())
    }
}

/** 立即落到底部（切换会话、重生成隐藏旧消息时用）。 */
private suspend fun jumpToBottom(state: LazyListState) {
    val last = (state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
    state.scrollToItem(last, 200_000)
    var guard = 0
    while (state.canScrollForward && guard++ < 8) {
        state.scrollBy(4_000f)
    }
}

/** 平滑滚到底部；超长消息一屏放不下时分段追平。 */
private suspend fun animateToBottom(state: LazyListState) {
    val last = (state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
    state.animateScrollToItem(last, 200_000)
    var guard = 0
    while (state.canScrollForward && guard++ < 8) {
        state.animateScrollBy(3_000f)
    }
}
