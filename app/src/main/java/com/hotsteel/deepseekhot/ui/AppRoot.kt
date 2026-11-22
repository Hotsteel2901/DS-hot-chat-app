// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Hotsteel2901

package com.hotsteel.deepseekhot.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hotsteel.deepseekhot.AppViewModel
import com.hotsteel.deepseekhot.BuildConfig
import com.hotsteel.deepseekhot.data.Conversation
import com.hotsteel.deepseekhot.ui.theme.DeepSeekHotTheme
import com.hotsteel.deepseekhot.ui.theme.LocalPalette
import kotlinx.coroutines.launch

private enum class Screen { Chat, Settings, About }

@Composable
fun AppRoot(vm: AppViewModel) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (vm.settings.themeMode) {
        1 -> false
        2 -> true
        else -> systemDark
    }
    DeepSeekHotTheme(dark = dark, dynamic = vm.settings.dynamicColor) {
        RootContent(vm)
    }
}

@Composable
private fun RootContent(vm: AppViewModel) {
    val p = LocalPalette.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf(Screen.Chat) }
    var showModels by remember { mutableStateOf(false) }
    var showUsage by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<Conversation?>(null) }
    var deleting by remember { mutableStateOf<Conversation?>(null) }
    val snackbar = remember { SnackbarHostState() }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }
    BackHandler(enabled = !drawerState.isOpen && screen != Screen.Chat) {
        screen = if (screen == Screen.About) Screen.Settings else Screen.Chat
    }

    LaunchedEffect(vm.toast) {
        vm.toast?.let {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(it)
            vm.toast = null
        }
    }

    LaunchedEffect(vm.settings.apiKey) {
        if (vm.settings.apiKey.isNotBlank()) {
            vm.refreshModels(silent = true)
            vm.refreshBalance(silent = true)
        }
    }

    AppBackground(Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            // 只在抽屉打开时允许手势，避免和消息列表、代码块的横向滑动冲突
            gesturesEnabled = drawerState.isOpen || screen == Screen.Chat,
            scrimColor = Color.Black.copy(alpha = if (p.isDark) 0.5f else 0.32f),
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = p.bgAlt,
                    drawerShape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
                    modifier = Modifier.width(300.dp),
                ) {
                    DrawerContent(
                        vm = vm,
                        query = query,
                        onQuery = { query = it },
                        onOpen = { id ->
                            vm.openChat(id)
                            screen = Screen.Chat
                            scope.launch { drawerState.close() }
                        },
                        onNew = {
                            vm.newChat()
                            screen = Screen.Chat
                            scope.launch { drawerState.close() }
                        },
                        onSettings = {
                            screen = Screen.Settings
                            scope.launch { drawerState.close() }
                        },
                        onAbout = {
                            screen = Screen.About
                            scope.launch { drawerState.close() }
                        },
                        onUsage = {
                            showUsage = true
                            scope.launch { drawerState.close() }
                        },
                        onRename = { renaming = it },
                        onDelete = { deleting = it },
                    )
                }
            },
        ) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    if (targetState.ordinal > initialState.ordinal) {
                        (slideInHorizontally(tween(260)) { it / 4 } + fadeIn(tween(200))) togetherWith
                            (slideOutHorizontally(tween(220)) { -it / 6 } + fadeOut(tween(140)))
                    } else {
                        (slideInHorizontally(tween(260)) { -it / 4 } + fadeIn(tween(200))) togetherWith
                            (slideOutHorizontally(tween(220)) { it / 6 } + fadeOut(tween(140)))
                    }
                },
                label = "screen",
            ) { target ->
                when (target) {
                    Screen.Chat -> ChatScreen(
                        vm = vm,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onOpenSettings = { screen = Screen.Settings },
                        onOpenAbout = { screen = Screen.About },
                        onShowModels = { showModels = true },
                        onShowUsage = { showUsage = true },
                    )

                    Screen.Settings -> SettingsScreen(
                        vm = vm,
                        onBack = { screen = Screen.Chat },
                        onAbout = { screen = Screen.About },
                        onModels = { showModels = true },
                        onUsage = { showUsage = true },
                    )

                    Screen.About -> AboutScreen(onBack = { screen = Screen.Settings })
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (screen == Screen.Chat) 150.dp else 20.dp, start = 20.dp, end = 20.dp),
        ) { data ->
            Snackbar(
                snackbarData = data,
                shape = RoundedCornerShape(14.dp),
                containerColor = if (p.isDark) p.surfaceHigh else Color(0xFF26282D),
                contentColor = Color.White,
            )
        }
    }

    if (showModels) ModelSheet(vm) { showModels = false }
    if (showUsage) UsageSheet(vm) { showUsage = false }

    renaming?.let { conv ->
        TextInputDialog(
            title = "重命名会话",
            initial = conv.title,
            singleLine = true,
            onDismiss = { renaming = null },
            onConfirm = { title ->
                renaming = null
                vm.renameChat(conv.id, title)
            },
        )
    }
    deleting?.let { conv ->
        ConfirmDialog(
            title = "删除会话？",
            message = "「${conv.title.ifBlank { "新对话" }}」将被永久删除，无法恢复。",
            confirmText = "删除",
            onConfirm = {
                deleting = null
                vm.deleteChat(conv.id)
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun DrawerContent(
    vm: AppViewModel,
    query: String,
    onQuery: (String) -> Unit,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onUsage: () -> Unit,
    onRename: (Conversation) -> Unit,
    onDelete: (Conversation) -> Unit,
) {
    val p = LocalPalette.current
    val filtered = vm.conversations.filter { it.title.contains(query, ignoreCase = true) }
    val pinned = filtered.filter { it.pinned }
    val normal = filtered.filterNot { it.pinned }
    val buckets = normal.groupBy { bucketOf(it.updatedAt) }

    Column(
        Modifier
            .fillMaxHeight()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 2.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandMark(30.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                "DeepSeek Hot",
                color = p.text,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp,
                modifier = Modifier.weight(1f),
            )
        }

        Tappable(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(p.primarySoft),
            onClick = onNew,
        ) {
            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AddComment, null, tint = p.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("开启新对话", color = p.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(p.surfaceAlt)
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, tint = p.faint, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(color = p.text, fontSize = 14.sp),
                cursorBrush = SolidColor(p.primary),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) {
                            Text("搜索历史对话", color = p.faint, fontSize = 14.sp)
                        }
                        inner()
                    }
                },
            )
            if (query.isNotEmpty()) {
                IconTap(Icons.Rounded.Close, "清除搜索", { onQuery("") }, tint = p.faint, size = 32.dp, iconSize = 16.dp)
            }
        }

        Spacer(Modifier.height(6.dp))

        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            if (pinned.isNotEmpty()) {
                item(key = "h-pinned") { DrawerSection("置顶") }
                items(pinned, key = { "p-${it.id}" }) { conv ->
                    ConversationRow(conv, vm.current?.id == conv.id, { onOpen(conv.id) }, { vm.togglePin(conv.id) }, { onRename(conv) }, { onDelete(conv) })
                }
            }
            Bucket.entries.forEach { bucket ->
                val list = buckets[bucket].orEmpty()
                if (list.isNotEmpty()) {
                    item(key = "h-${bucket.name}") { DrawerSection(bucket.label) }
                    items(list, key = { "c-${it.id}" }) { conv ->
                        ConversationRow(conv, vm.current?.id == conv.id, { onOpen(conv.id) }, { vm.togglePin(conv.id) }, { onRename(conv) }, { onDelete(conv) })
                    }
                }
            }
            if (filtered.isEmpty()) {
                item {
                    Text(
                        if (query.isBlank()) "还没有对话，开始聊点什么吧" else "没有匹配「$query」的对话",
                        color = p.faint,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 20.dp),
                    )
                }
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(p.border))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val balance = vm.balance?.infos?.firstOrNull()
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onUsage)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    balance?.let { "余额 ¥${it.total}" } ?: "用量与余额",
                    color = p.text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text("v${BuildConfig.VERSION_NAME}", color = p.faint, fontSize = 11.sp)
            }
            IconTap(Icons.Outlined.Info, "关于", onAbout)
            IconTap(Icons.Outlined.Settings, "设置", onSettings)
        }
    }
}

@Composable
private fun DrawerSection(title: String) {
    Text(
        title,
        color = LocalPalette.current.faint,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 10.dp, top = 14.dp, bottom = 6.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    conv: Conversation,
    active: Boolean,
    onClick: () -> Unit,
    onPin: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val p = LocalPalette.current
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (active) p.primarySoft else Color.Transparent)
                .combinedClickable(onClick = onClick, onLongClick = { menu = true })
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = conv.title.ifBlank { "新对话" },
                color = p.text,
                fontSize = 14.sp,
                fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (conv.pinned) {
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Rounded.PushPin, "已置顶", tint = p.faint, modifier = Modifier.size(13.dp))
            }
        }
        DropdownMenu(
            expanded = menu,
            onDismissRequest = { menu = false },
            containerColor = p.surface,
            shape = RoundedCornerShape(14.dp),
        ) {
            DropdownMenuItem(
                text = { Text(if (conv.pinned) "取消置顶" else "置顶") },
                leadingIcon = { Icon(Icons.Rounded.PushPin, null, tint = p.dim) },
                onClick = { menu = false; onPin() },
            )
            DropdownMenuItem(
                text = { Text("重命名") },
                leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, null, tint = p.dim) },
                onClick = { menu = false; onRename() },
            )
            DropdownMenuItem(
                text = { Text("删除", color = p.danger) },
                leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = p.danger) },
                onClick = { menu = false; onDelete() },
            )
        }
    }
}

private enum class Bucket(val label: String) {
    Today("今天"), Yesterday("昨天"), Week("7 天内"), Month("30 天内"), Older("更早")
}

private fun bucketOf(ts: Long): Bucket {
    val zone = java.time.ZoneId.systemDefault()
    val d = java.time.Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
    val days = java.time.temporal.ChronoUnit.DAYS.between(d, java.time.LocalDate.now(zone))
    return when {
        days <= 0 -> Bucket.Today
        days == 1L -> Bucket.Yesterday
        days < 7 -> Bucket.Week
        days < 30 -> Bucket.Month
        else -> Bucket.Older
    }
}
