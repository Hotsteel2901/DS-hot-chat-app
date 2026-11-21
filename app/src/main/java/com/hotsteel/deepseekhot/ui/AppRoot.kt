// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Hotsteel2901

package com.hotsteel.deepseekhot.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hotsteel.deepseekhot.AppViewModel
import com.hotsteel.deepseekhot.data.Conversation
import com.hotsteel.deepseekhot.ui.theme.DeepSeekHotTheme
import com.hotsteel.deepseekhot.ui.theme.LocalPalette
import com.hotsteel.deepseekhot.util.Fmt
import kotlinx.coroutines.launch
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.activity.compose.BackHandler

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
    val snackbar = remember { SnackbarHostState() }

    BackHandler(enabled = screen != Screen.Chat) {
        screen = if (screen == Screen.About) Screen.Settings else Screen.Chat
    }

    LaunchedEffect(vm.toast) {
        vm.toast?.let {
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

    AuroraBackground(Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            scrimColor = Color.Black.copy(alpha = 0.45f),
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = p.surface,
                    drawerShape = RoundedCornerShape(topEnd = 26.dp, bottomEnd = 26.dp),
                    modifier = Modifier.width(304.dp),
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
                    )
                }
            },
        ) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    if (targetState.ordinal > initialState.ordinal) {
                        (slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(220)) { -it / 4 } + fadeOut(tween(160)))
                    } else {
                        (slideInHorizontally(tween(260)) { -it / 3 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(220)) { it / 4 } + fadeOut(tween(160)))
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
                .padding(bottom = 96.dp, start = 20.dp, end = 20.dp),
        )
    }

    if (showModels) ModelSheet(vm) { showModels = false }
    if (showUsage) UsageSheet(vm) { showUsage = false }
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
) {
    val p = LocalPalette.current
    val filtered = vm.conversations.filter { it.title.contains(query, ignoreCase = true) }
    val pinned = filtered.filter { it.pinned }
    val normal = filtered.filterNot { it.pinned }
    val today = normal.filter { isToday(it.updatedAt) }
    val older = normal.filterNot { isToday(it.updatedAt) }

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
                .padding(horizontal = 6.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Brush.linearGradient(listOf(p.primary, p.accent))),
                contentAlignment = Alignment.Center,
            ) {
                Text("D", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("DeepSeek Hot", color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    vm.balance?.infos?.firstOrNull()?.let { "余额 ¥${it.total}" } ?: "Kotlin · Compose",
                    color = p.faint,
                    fontSize = 10.sp,
                    modifier = Modifier.clickable(onClick = onUsage),
                )
            }
        }

        Tappable(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(p.primary)
                .padding(vertical = 11.dp),
            onClick = onNew,
        ) {
            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("新对话", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(11.dp))
                .background(p.surfaceAlt)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, tint = p.faint, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(7.dp))
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(color = p.text, fontSize = 12.sp),
                cursorBrush = SolidColor(p.primary),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) {
                            Text("搜索会话", color = p.faint, fontSize = 12.sp)
                        }
                        inner()
                    }
                },
            )
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (pinned.isNotEmpty()) {
                item { DrawerSection("置顶") }
                items(pinned, key = { "p-${it.id}" }) { conv ->
                    ConversationRow(conv, vm.current?.id == conv.id) { onOpen(conv.id) }
                }
            }
            if (today.isNotEmpty()) {
                item { DrawerSection("今天") }
                items(today, key = { "t-${it.id}" }) { conv ->
                    ConversationRow(conv, vm.current?.id == conv.id) { onOpen(conv.id) }
                }
            }
            if (older.isNotEmpty()) {
                item { DrawerSection("更早") }
                items(older, key = { "o-${it.id}" }) { conv ->
                    ConversationRow(conv, vm.current?.id == conv.id) { onOpen(conv.id) }
                }
            }
            if (filtered.isEmpty()) {
                item {
                    Text(
                        "还没有会话",
                        color = p.faint,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 16.dp),
                    )
                }
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(p.border))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            DrawerAction("设置", Icons.Rounded.Settings, Modifier.weight(1f), onSettings)
            DrawerAction("关于", Icons.Rounded.Info, Modifier.weight(1f), onAbout)
        }
        Text(
            "v1.0.0 · github@Hotsteel2901",
            color = p.faint,
            fontSize = 9.5.sp,
            modifier = Modifier.padding(start = 10.dp, bottom = 10.dp),
        )
    }
}

@Composable
private fun DrawerSection(title: String) {
    Text(
        title,
        color = LocalPalette.current.faint,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
        modifier = Modifier.padding(start = 10.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun ConversationRow(conv: Conversation, active: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(if (active) p.primary.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = conv.title.ifBlank { "新对话" },
            color = if (active) p.primary else p.dim,
            fontSize = 12.5.sp,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (conv.pinned) {
            Icon(Icons.Rounded.PushPin, null, tint = p.faint, modifier = Modifier.size(11.dp))
        } else {
            Text(Fmt.relative(conv.updatedAt), color = p.faint, fontSize = 9.5.sp, maxLines = 1)
        }
    }
}

@Composable
private fun DrawerAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    Tappable(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .padding(vertical = 9.dp),
        onClick = onClick,
    ) {
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = p.dim, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, color = p.dim, fontSize = 11.5.sp)
        }
    }
}

private fun isToday(ts: Long): Boolean {
    val zone = java.time.ZoneId.systemDefault()
    val d = java.time.Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
    return d == java.time.LocalDate.now(zone)
}
