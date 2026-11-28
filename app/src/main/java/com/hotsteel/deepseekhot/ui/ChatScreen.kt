// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Hotsteel2901

package com.hotsteel.deepseekhot.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hotsteel.deepseekhot.AppViewModel
import com.hotsteel.deepseekhot.LiveReply
import com.hotsteel.deepseekhot.StreamMode
import com.hotsteel.deepseekhot.data.Block
import com.hotsteel.deepseekhot.data.ChatMsg
import com.hotsteel.deepseekhot.data.PromptPresets
import com.hotsteel.deepseekhot.ui.liquid.LocalAmbientBackdrop
import com.hotsteel.deepseekhot.ui.liquid.liquidGlass
import com.hotsteel.deepseekhot.ui.theme.LocalPalette
import com.hotsteel.deepseekhot.util.Fmt
import com.hotsteel.deepseekhot.util.Sys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** 思考强度的简短中文名，顶栏与输入框胶囊共用。 */
fun effortLabel(level: String): String = when (level) {
    "none" -> "不思考"
    "low" -> "轻度思考"
    "high" -> "深度思考"
    "max" -> "极限思考"
    else -> level
}

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
    var confirmDelete by remember { mutableStateOf(false) }
    var showPresets by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchCursor by remember { mutableIntStateOf(0) }

    val searchMatches = remember(conv?.messages, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty() || conv == null) emptyList()
        else conv.messages.mapIndexedNotNull { idx, m ->
            if (m.plainText.contains(q, ignoreCase = true)) idx else null
        }
    }
    LaunchedEffect(searchMatches) {
        if (searchCursor >= searchMatches.size) searchCursor = 0
    }

    val pickFiles = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) vm.attach(uris) }

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> if (uris.isNotEmpty()) vm.attach(uris) }

    val messageCount = conv?.messages?.size ?: 0
    val currentModel = vm.models.firstOrNull { it.id == vm.settings.model }
    val modelName = currentModel?.displayName ?: vm.settings.model

    // 贴底判定直接用「还能不能继续往下滚」，并在自动跟随流式输出时抑制回底按钮闪烁
    val atBottom by remember {
        derivedStateOf {
            !listState.canScrollForward || remainingScrollToBottom(listState) <= 24
        }
    }
    val scrolled by remember { derivedStateOf { listState.canScrollBackward } }
    var autoFollow by remember { mutableStateOf(true) }
    var dragging by remember { mutableStateOf(false) }

    // 手指向上回看历史的第一像素就立刻关闭自动跟随，彻底避免与用户手势抢滚动
    val nestedScroll = remember(listState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
                    if (available.y > 0f) {
                        autoFollow = false
                    } else if (available.y < 0f && !listState.canScrollForward) {
                        autoFollow = true
                    }
                }
                return Offset.Zero
            }
        }
    }

    // 用户手指拖动时暂停跟随；松手后若已离开底部就保持暂停，回到最底部则恢复
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> dragging = true
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    dragging = false
                    withFrameNanos { }
                    if (!listState.canScrollForward || remainingScrollToBottom(listState) <= 24) {
                        autoFollow = true
                    } else if (autoFollow) {
                        autoFollow = false
                    }
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
                    if (!listState.canScrollForward || remainingScrollToBottom(listState) <= 24) {
                        autoFollow = true
                    }
                }
            }
    }

    // 发起新一轮流式生成（发送、编辑重发、重新生成、续写）时自动回到最底部开始跟随
    LaunchedEffect(vm.live) {
        if (vm.live != null) {
            autoFollow = true
            dragging = false
            pinToBottom(listState, forceJump = true)
        }
    }

    LaunchedEffect(listState, conv?.id) {
        autoFollow = true
        dragging = false
        withFrameNanos { }
        if ((vm.current?.messages?.size ?: 0) > 0) jumpToBottom(listState)

        var lastUserMsgId = vm.current?.messages?.lastOrNull { it.role == "user" }?.id
        snapshotFlow {
            val live = vm.live
            var textLen = 0
            var thinkingLen = 0
            var toolSig = 0
            var citationCount = 0
            if (live != null) {
                for (b in live.blocks) {
                    when (b) {
                        is Block.Text -> {
                            textLen += b.text.length
                            citationCount += b.citations.size
                        }
                        is Block.Thinking -> {
                            thinkingLen += b.text.length
                            if (b.endedAt != null) toolSig += 1
                        }
                        is Block.Tool -> {
                            toolSig += b.sources.size * 7 + b.queries.size * 3 + b.status.hashCode()
                        }
                        is Block.Refusal -> textLen += b.text.length
                    }
                }
            }
            val msgs = vm.current?.messages
            ScrollTick(
                text = textLen,
                thinking = thinkingLen,
                blocks = live?.blocks?.size ?: 0,
                toolSig = toolSig,
                citations = citationCount,
                liveActive = live != null,
                count = msgs?.size ?: 0,
                lastUserId = msgs?.lastOrNull { it.role == "user" }?.id,
                viewportHeight = listState.layoutInfo.viewportSize.height,
            )
        }.distinctUntilChanged().collectLatest { tick ->
            val userAppended = tick.lastUserId != null && tick.lastUserId != lastUserMsgId
            lastUserMsgId = tick.lastUserId
            if (userAppended) autoFollow = true
            if (dragging || !autoFollow) return@collectLatest
            pinToBottom(listState, forceJump = userAppended)
        }
    }

    fun send() {
        when {
            vm.settings.apiKey.isBlank() -> vm.showToast("请先在设置中填写 API Key")
            vm.pending.any { it.status == "uploading" } -> vm.showToast("文件还在上传解析中，请稍候")
            else -> {
                if (vm.settings.haptics) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                val text = vm.pendingText
                val atts = vm.pending.filter { it.status == "ready" }
                vm.pendingText = ""
                vm.clearPending()
                vm.send(text, atts)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = conv?.title?.ifBlank { null } ?: "新对话",
            model = modelName,
            effort = vm.settings.effort,
            canStartNew = conv != null,
            showDivider = scrolled && messageCount > 0,
            hasConversation = conv != null,
            pinned = conv?.pinned == true,
            onMenu = onOpenDrawer,
            onModels = onShowModels,
            onNewChat = { vm.newChat() },
            onSearch = { searchOpen = !searchOpen },
            onPresets = { showPresets = true },
            onBookmarks = { showBookmarks = true },
            onRename = { renaming = true },
            onPin = { conv?.let { vm.togglePin(it.id) } },
            onExportMarkdown = {
                vm.exportCurrentMarkdown()?.let { md -> Sys.copy(context, md, "已复制 Markdown 格式对话") }
            },
            onShareMarkdown = {
                vm.exportCurrentMarkdown()?.let { md -> Sys.share(context, md) }
            },
            onExport = { vm.exportJson { text -> Sys.copy(context, text, "会话 JSON 已复制") } },
            onDelete = { confirmDelete = true },
            onUsage = onShowUsage,
            onSettings = onOpenSettings,
        )

        TtsControlBar(
            visible = vm.tts.playingId != null || vm.ttsBusyId != null,
            preparing = vm.tts.preparing || (vm.ttsBusyId != null && vm.tts.playingId == null),
            paused = vm.tts.paused,
            voice = vm.settings.ttsVoice,
            speed = vm.tts.speed,
            onTogglePause = { vm.toggleTtsPause() },
            onCycleSpeed = { vm.cycleTtsSpeed() },
            onStop = { vm.stopSpeak() },
        )

        ChatSearchBar(
            visible = searchOpen && conv != null,
            query = searchQuery,
            onQueryChange = {
                searchQuery = it
                searchCursor = 0
            },
            matchCount = searchMatches.size,
            currentMatch = searchCursor,
            onPrev = {
                if (searchMatches.isNotEmpty()) {
                    searchCursor = (searchCursor - 1 + searchMatches.size) % searchMatches.size
                    scope.launch { listState.animateScrollToItem(searchMatches[searchCursor]) }
                }
            },
            onNext = {
                if (searchMatches.isNotEmpty()) {
                    searchCursor = (searchCursor + 1) % searchMatches.size
                    scope.launch { listState.animateScrollToItem(searchMatches[searchCursor]) }
                }
            },
            onClose = {
                searchOpen = false
                searchQuery = ""
            },
        )

        Box(Modifier.weight(1f)) {
            if (conv == null || conv.messages.isEmpty()) {
                Welcome(
                    hasKey = vm.settings.apiKey.isNotBlank(),
                    onPick = { text ->
                        if (vm.settings.apiKey.isBlank()) {
                            // 没有 Key 时先把示例放进输入框，避免点了没反应
                            vm.pendingText = text
                            vm.showToast("请先在设置中填写 API Key")
                        } else {
                            vm.send(text, emptyList())
                        }
                    },
                    onSettings = onOpenSettings,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(nestedScroll),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    itemsIndexed(conv.messages, key = { idx, m -> "${m.id}_$idx" }) { index, msg ->
                        val isLast = index == conv.messages.lastIndex
                        val live = vm.live
                        val hideForRegen = live != null &&
                            live.mode == StreamMode.REGENERATE && live.targetId == msg.id
                        if (!hideForRegen) {
                            val itemModifier = Modifier
                                .widthIn(max = 760.dp)
                                .animateItem()
                            if (msg.role == "user") {
                                UserMessageItem(
                                    msg = msg,
                                    onEdit = { editing = msg },
                                    modifier = itemModifier,
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
                                    speaking = vm.tts.playingId == msg.id || vm.ttsBusyId == msg.id,
                                    starred = msg.starred,
                                    onCopy = { Sys.copy(context, msg.plainText) },
                                    onRegenerate = { vm.regenerate(msg.id) },
                                    onContinue = { vm.continueReply(msg.id) },
                                    onSpeak = { vm.speak(msg.id, msg.plainText) },
                                    onSwitchVersion = { i -> vm.switchVersion(msg.id, i) },
                                    onToggleStar = { vm.toggleStar(msg.id) },
                                    onFork = { vm.forkConversation(msg.id) },
                                    onShare = { Sys.share(context, msg.plainText) },
                                    modifier = itemModifier,
                                )
                            }
                        }
                    }

                    vm.live?.let { live ->
                        item(key = "live") {
                            LiveMessageItem(vm, live, Modifier.widthIn(max = 760.dp))
                        }
                    }
                }
            }

            val fab by animateFloatAsState(
                targetValue = if (!atBottom && !(autoFollow && vm.streaming) && messageCount > 0) 1f else 0f,
                animationSpec = tween(180),
                label = "fab",
            )
            if (fab > 0.01f) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                        .graphicsLayer { alpha = fab; scaleX = 0.8f + 0.2f * fab; scaleY = 0.8f + 0.2f * fab }
                ) {
                    Tappable(
                        Modifier
                            .size(40.dp)
                            .liquidGlass(
                                backdrop = LocalAmbientBackdrop.current,
                                isCapsule = true,
                                blurRadius = 12.dp,
                                refractionHeight = 10.dp,
                                refractionAmount = 18.dp,
                            ),
                        onClick = {
                            autoFollow = true
                            scope.launch { animateToBottom(listState) }
                        },
                    ) {
                        Icon(
                            Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "回到底部",
                            tint = p.dim,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(22.dp),
                        )
                    }
                }
            }
        }

        if (vm.streaming) {
            vm.live?.let { StreamingStatus(it) }
        }

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
            thinkActive = vm.settings.effort != "none",
            thinkLabel = when (vm.settings.effort) {
                "low" -> "深度思考 · 轻度"
                "max" -> "深度思考 · 极限"
                else -> "深度思考"
            },
            onToggleThink = {
                if (vm.settings.effort != "none") {
                    vm.setEffort("none")
                } else {
                    val fallback = currentModel?.defaultEffort?.takeIf { it != "none" }
                        ?: currentModel?.efforts?.firstOrNull { it == "high" }
                        ?: currentModel?.efforts?.firstOrNull { it != "none" }
                        ?: "high"
                    vm.setEffort(fallback)
                }
            },
            webActive = vm.settings.webSearch,
            onToggleWeb = { vm.updateSettings { s -> s.copy(webSearch = !s.webSearch) } },
            presetBadge = PromptPresets.byId(conv?.presetId ?: vm.settings.activePresetId).badge,
            onOpenPresets = { showPresets = true },
            onSend = { send() },
            onStop = {
                if (vm.settings.haptics) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                vm.stopStreaming()
            },
            onTranscribe = { file -> vm.transcribe(file) { text -> vm.pendingText = text } },
            onHint = { vm.showToast(it) },
        )
    }

    editing?.let { msg ->
        TextInputDialog(
            title = "编辑消息",
            initial = msg.text,
            confirmText = "发送",
            onDismiss = { editing = null },
            onConfirm = { newText ->
                editing = null
                vm.editUserMessage(msg.id, newText)
            },
        )
    }

    if (renaming && conv != null) {
        TextInputDialog(
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

    if (confirmDelete && conv != null) {
        ConfirmDialog(
            title = "删除会话？",
            message = "「${conv.title.ifBlank { "新对话" }}」将被永久删除，无法恢复。",
            confirmText = "删除",
            onConfirm = {
                confirmDelete = false
                vm.deleteChat(conv.id)
            },
            onDismiss = { confirmDelete = false },
        )
    }

    if (showPresets) {
        PromptPresetSheet(
            activeId = conv?.presetId ?: vm.settings.activePresetId,
            onSelect = { preset ->
                vm.selectPreset(preset.id)
                vm.showToast("已切换角色：${preset.name}")
            },
            onDismiss = { showPresets = false },
        )
    }

    if (showBookmarks) {
        BookmarksSheet(
            items = vm.starredMessages(),
            onOpenConversation = { id -> vm.openChat(id) },
            onDismiss = { showBookmarks = false },
        )
    }
}

@Composable
private fun StreamingStatus(live: LiveReply) {
    val p = LocalPalette.current
    var now by remember(live) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(live) {
        while (true) {
            kotlinx.coroutines.delay(200)
            now = System.currentTimeMillis()
        }
    }
    val elapsed = (now - live.startedAt).coerceAtLeast(0)
    val tokens = (live.text.length / 2.2).toInt()
    val speed = if (elapsed > 0 && tokens > 0) tokens * 1000f / elapsed else 0f
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TypingDots(size = 4.dp, color = p.primary)
        Spacer(Modifier.width(8.dp))
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
            fontSize = 11.sp,
            maxLines = 1,
        )
    }
}

private fun displayName(vm: AppViewModel, id: String): String =
    vm.models.firstOrNull { it.id == id }?.displayName ?: id

@Composable
private fun TopBar(
    title: String,
    model: String,
    effort: String,
    canStartNew: Boolean,
    showDivider: Boolean,
    hasConversation: Boolean,
    pinned: Boolean,
    onMenu: () -> Unit,
    onModels: () -> Unit,
    onNewChat: () -> Unit,
    onSearch: () -> Unit,
    onPresets: () -> Unit,
    onBookmarks: () -> Unit,
    onRename: () -> Unit,
    onPin: () -> Unit,
    onExportMarkdown: () -> Unit,
    onShareMarkdown: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onUsage: () -> Unit,
    onSettings: () -> Unit,
) {
    val p = LocalPalette.current
    var menu by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .liquidGlass(
                backdrop = LocalAmbientBackdrop.current,
                cornerRadius = 22.dp,
                blurRadius = 14.dp,
                refractionHeight = 12.dp,
                refractionAmount = 20.dp,
                surfaceAlpha = if (p.isDark) 0.50f else 0.64f,
            ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
        ) {
            IconTap(
                Icons.Rounded.Menu,
                "会话列表",
                onMenu,
                tint = p.text,
                size = 44.dp,
                iconSize = 22.dp,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 92.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    color = p.text,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // 模型选择入口：点击打开模型 / 思考强度 / 协议面板
                Row(
                    Modifier
                        .padding(top = 1.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onModels)
                        .padding(start = 6.dp, end = 2.dp, top = 1.dp, bottom = 1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${model.removePrefix("DeepSeek ")} · ${effortLabel(effort)}",
                        color = p.faint,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 190.dp),
                    )
                    Icon(Icons.Rounded.KeyboardArrowDown, "切换模型", tint = p.faint, modifier = Modifier.size(15.dp))
                }
            }
            Row(
                Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTap(Icons.Rounded.AddComment, "开启新对话", onNewChat, tint = p.text, enabled = canStartNew, size = 44.dp)
                Box {
                    IconTap(Icons.Rounded.MoreVert, "更多", { menu = true }, tint = p.text, size = 44.dp)
                    DropdownMenu(
                        expanded = menu,
                        onDismissRequest = { menu = false },
                        containerColor = p.surface,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        if (hasConversation) {
                            DropdownMenuItem(
                                text = { Text("会话内搜索") },
                                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = p.dim) },
                                onClick = { menu = false; onSearch() },
                            )
                            DropdownMenuItem(
                                text = { Text("重命名") },
                                leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, null, tint = p.dim) },
                                onClick = { menu = false; onRename() },
                            )
                            DropdownMenuItem(
                                text = { Text(if (pinned) "取消置顶" else "置顶会话") },
                                leadingIcon = { Icon(Icons.Rounded.PushPin, null, tint = p.dim) },
                                onClick = { menu = false; onPin() },
                            )
                            DropdownMenuItem(
                                text = { Text("复制 Markdown") },
                                leadingIcon = { Icon(Icons.Rounded.Description, null, tint = p.dim) },
                                onClick = { menu = false; onExportMarkdown() },
                            )
                            DropdownMenuItem(
                                text = { Text("分享对话") },
                                leadingIcon = { Icon(Icons.Rounded.Share, null, tint = p.dim) },
                                onClick = { menu = false; onShareMarkdown() },
                            )
                            DropdownMenuItem(
                                text = { Text("复制为 JSON") },
                                leadingIcon = { Icon(Icons.Rounded.FileDownload, null, tint = p.dim) },
                                onClick = { menu = false; onExport() },
                            )
                            DropdownMenuItem(
                                text = { Text("删除会话", color = p.danger) },
                                leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = p.danger) },
                                onClick = { menu = false; onDelete() },
                            )
                            HorizontalDivider(color = p.border, modifier = Modifier.padding(vertical = 4.dp))
                        }
                        DropdownMenuItem(
                            text = { Text("角色与提示词工坊") },
                            leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null, tint = p.primary) },
                            onClick = { menu = false; onPresets() },
                        )
                        DropdownMenuItem(
                            text = { Text("我的收藏夹") },
                            leadingIcon = { Icon(Icons.Rounded.Bookmark, null, tint = p.dim) },
                            onClick = { menu = false; onBookmarks() },
                        )
                        DropdownMenuItem(
                            text = { Text("用量与余额") },
                            leadingIcon = { Icon(Icons.Rounded.AccountBalanceWallet, null, tint = p.dim) },
                            onClick = { menu = false; onUsage() },
                        )
                        DropdownMenuItem(
                            text = { Text("设置") },
                            leadingIcon = { Icon(Icons.Outlined.Settings, null, tint = p.dim) },
                            onClick = { menu = false; onSettings() },
                        )
                    }
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(if (showDivider) p.border else p.bg)
        )
    }
}

@Composable
private fun LiveMessageItem(vm: AppViewModel, live: LiveReply, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Column(modifier) {
        if (live.mode == StreamMode.CONTINUE) {
            Text("续写中…", color = p.faint, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
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

/** 通用文本输入对话框：编辑消息、重命名会话。打开即聚焦并把光标放到末尾。 */
@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    confirmText: String = "确定",
    singleLine: Boolean = false,
) {
    val p = LocalPalette.current
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surface,
        shape = RoundedCornerShape(20.dp),
        title = { Text(title, color = p.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = singleLine,
                maxLines = if (singleLine) 1 else 8,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = p.surfaceAlt,
                    unfocusedContainerColor = p.surfaceAlt,
                    focusedBorderColor = p.primary.copy(alpha = 0.6f),
                    unfocusedBorderColor = p.border,
                    focusedTextColor = p.text,
                    unfocusedTextColor = p.text,
                    cursorColor = p.primary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (value.text.isNotBlank()) onConfirm(value.text) },
                enabled = value.text.isNotBlank(),
            ) { Text(confirmText, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = p.dim) } },
    )
}

/** 流式输出时用于触发跟随滚动的信号：思考、正文、工具、引用、消息条数与视口高度任一变化都算。 */
private data class ScrollTick(
    val text: Int,
    val thinking: Int,
    val blocks: Int,
    val toolSig: Int,
    val citations: Int,
    val liveActive: Boolean,
    val count: Int,
    val lastUserId: String?,
    val viewportHeight: Int,
)

/**
 * 计算当前距离列表最底端（含底部 contentPadding）还差多少像素。
 * 若最后一项尚在视口之外，返回 Int.MAX_VALUE。
 */
private fun remainingScrollToBottom(state: LazyListState): Int {
    val info = state.layoutInfo
    val total = info.totalItemsCount
    if (total == 0) return 0
    val last = info.visibleItemsInfo.lastOrNull() ?: return Int.MAX_VALUE
    if (last.index < total - 1) return Int.MAX_VALUE
    return ((last.offset + last.size + info.afterContentPadding) - info.viewportEndOffset).coerceAtLeast(0)
}

/**
 * 安全的程序化滚动：当用户手指抢占滚动时，Compose 的 MutatorMutex 会抛出
 * MutationInterruptedException（CancellationException 的子类）。这里只在当前协程仍然
 * 存活时吞掉它，防止整个自动跟随的 LaunchedEffect 被意外终止。
 */
private suspend inline fun safeScroll(block: suspend () -> Unit) {
    try {
        block()
    } catch (_: CancellationException) {
        currentCoroutineContext().ensureActive()
    }
}

/**
 * 把列表钉在底部（含底部 contentPadding）。
 *
 * 不能只用 scrollToItem(lastIndex)——当最后一条消息比屏幕还高时，那只会把它的顶部对齐到
 * 屏幕上沿，流式追加的文字会一路掉到屏幕外。这里在最后一项可见时按剩余像素精准补滚，
 * 在最后一项不可见（如刚发新消息、之前停在顶部）时先跳到最后一项再补滚到底边。
 */
private suspend fun pinToBottom(state: LazyListState, forceJump: Boolean = false) {
    safeScroll {
        repeat(3) {
            withFrameNanos { }
            val info = state.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) return@safeScroll
            val last = info.visibleItemsInfo.lastOrNull()
            if (last == null || last.index < total - 1) {
                if (!forceJump && it > 0) return@safeScroll
                state.scrollToItem(total - 1, Int.MAX_VALUE / 2)
                return@repeat
            }
            val overflow = (last.offset + last.size + info.afterContentPadding) - info.viewportEndOffset
            if (overflow <= 1) return@safeScroll
            state.scrollBy(overflow.toFloat())
        }
    }
}

/** 立即落到底部（切换会话、打开历史会话时用）。 */
private suspend fun jumpToBottom(state: LazyListState) {
    safeScroll {
        repeat(3) {
            withFrameNanos { }
            val total = state.layoutInfo.totalItemsCount
            if (total == 0) return@repeat
            state.scrollToItem(total - 1, Int.MAX_VALUE / 2)
            var guard = 0
            while (state.canScrollForward && guard++ < 8) {
                val rem = remainingScrollToBottom(state)
                if (rem <= 1) break
                state.scrollBy(if (rem == Int.MAX_VALUE) 4_000f else rem.toFloat())
            }
            if (!state.canScrollForward) return@safeScroll
        }
    }
}

/** 平滑滚到底部；超长消息一屏放不下时分段追平。 */
private suspend fun animateToBottom(state: LazyListState) {
    safeScroll {
        val total = state.layoutInfo.totalItemsCount
        if (total == 0) return@safeScroll
        val last = total - 1
        state.animateScrollToItem(last, Int.MAX_VALUE / 2)
        var guard = 0
        while (state.canScrollForward && guard++ < 8) {
            val rem = remainingScrollToBottom(state)
            if (rem <= 1) break
            state.animateScrollBy(if (rem == Int.MAX_VALUE) 3_000f else rem.toFloat())
        }
    }
}
