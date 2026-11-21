// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Hotsteel2901

package com.hotsteel.deepseekhot.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hotsteel.deepseekhot.AppViewModel
import com.hotsteel.deepseekhot.data.Protocol
import com.hotsteel.deepseekhot.ui.theme.LocalPalette
import com.hotsteel.deepseekhot.util.Fmt
import com.hotsteel.deepseekhot.util.Sys
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onAbout: () -> Unit,
    onModels: () -> Unit,
    onUsage: () -> Unit,
) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val s = vm.settings
    var key by remember { mutableStateOf(s.apiKey) }
    var base by remember { mutableStateOf(s.baseUrl) }
    var showKey by remember { mutableStateOf(false) }
    var systemPrompt by remember { mutableStateOf(s.systemPrompt) }

    androidx.compose.runtime.LaunchedEffect(s.apiKey) {
        if (key != s.apiKey) key = s.apiKey
    }
    androidx.compose.runtime.LaunchedEffect(s.baseUrl) {
        if (base != s.baseUrl) base = s.baseUrl
    }
    androidx.compose.runtime.LaunchedEffect(s.systemPrompt) {
        if (systemPrompt != s.systemPrompt) systemPrompt = s.systemPrompt
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            vm.exportJson { text ->
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                    vm.showToast("已导出到所选位置")
                }.onFailure { vm.showToast("导出失败：${it.message}") }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (text != null) vm.importJson(text)
            }.onFailure { vm.showToast("导入失败：${it.message}") }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, "返回", tint = p.text, modifier = Modifier.size(21.dp))
            }
            Text("设置", color = p.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingsCard(title = "账号", icon = Icons.Rounded.Key) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("DeepSeek API Key") },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Text(
                            if (showKey) "隐藏" else "显示",
                            color = p.primary,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clickable { showKey = !showKey }
                                .padding(6.dp),
                        )
                    },
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = base,
                    onValueChange = { base = it },
                    label = { Text("Base URL") },
                    singleLine = true,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("保存并测试", Modifier.weight(1f)) {
                        vm.updateSettings { it.copy(apiKey = key.trim(), baseUrl = base.trim()) }
                        vm.refreshModels()
                        vm.refreshBalance()
                    }
                    SecondaryButton("查询余额", Modifier.weight(1f)) { onUsage() }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Key 仅保存在本机 DataStore，不会上传到任何第三方服务。",
                    color = p.faint,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                )
            }

            SettingsCard(title = "网关兼容", icon = Icons.Rounded.Security) {
                Text(
                    "当前识别为：${vm.gateway.label}",
                    color = p.text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("自动识别", "始终发送", "关闭").forEachIndexed { index, label ->
                        SelectableTile(
                            label = label,
                            selected = s.opencodeSession == index,
                            modifier = Modifier.weight(1f),
                        ) { vm.updateSettings { st -> st.copy(opencodeSession = index) } }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "x-opencode-session：OpenCode v2 / Go / Zen 自 2026-09 起要求每个请求携带稳定的会话 id，" +
                        "缺失会直接 400 MissingSessionID。自动识别只在 Base URL 命中 opencode.ai 时发送，" +
                        "值由会话 id 派生；选「始终发送」可给自建反代补头。",
                    color = p.faint,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                )
                if (vm.gateway.isOpencode) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "该网关未提供 Files / 余额 / 语音接口，相关入口已隐藏或降级。",
                        color = p.faint,
                        fontSize = 10.5.sp,
                        lineHeight = 15.sp,
                    )
                }
            }

            SettingsCard(title = "模型与接口", icon = Icons.Rounded.Tune) {
                SettingRow(
                    label = "当前模型",
                    value = vm.models.firstOrNull { it.id == s.model }?.displayName ?: s.model,
                    onClick = onModels,
                )
                SettingRow(
                    label = "思考强度",
                    value = when (s.effort) {
                        "none" -> "关闭"
                        "low" -> "低"
                        "high" -> "高（默认）"
                        "max" -> "极限"
                        else -> s.effort
                    },
                    onClick = onModels,
                )
                SettingRow(
                    label = "接口格式",
                    value = s.protocol.label,
                    onClick = onModels,
                )
                Text(
                    "同一套能力在 Chat Completions / Responses / Anthropic Messages 三种格式下都可用，可随时切换。",
                    color = p.faint,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            SettingsCard(title = "对话能力", icon = Icons.Rounded.Language) {
                ToggleRow("联网搜索", "deepseek_web_search：自动搜索网页并附引用", s.webSearch) {
                    vm.updateSettings { st -> st.copy(webSearch = it) }
                }
                ToggleRow("阅读链接", "deepseek_read_url：自动抓取消息中的链接", s.readUrl) {
                    vm.updateSettings { st -> st.copy(readUrl = it) }
                }
                ToggleRow("内容安全", "safety_check: high，命中风险时拒答", s.safetyCheck) {
                    vm.updateSettings { st -> st.copy(safetyCheck = it) }
                }
                ToggleRow("自动生成标题", "首轮对话让模型顺带生成会话标题", s.autoTitle) {
                    vm.updateSettings { st -> st.copy(autoTitle = it) }
                }
                ToggleRow("注入时间与地点", "把当前时间、所在地、语言作为环境信息", s.includeMeta) {
                    vm.updateSettings { st -> st.copy(includeMeta = it) }
                }
            }

            SettingsCard(title = "用户信息", icon = Icons.Rounded.Palette) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChipSelect(
                        listOf("中文", "English", "日本語"),
                        s.language,
                        Modifier.weight(1f),
                    ) { vm.updateSettings { st -> st.copy(language = it) } }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChipSelect(
                        listOf("中国", "中国香港", "中国台湾", "新加坡"),
                        s.location,
                        Modifier.weight(1f),
                    ) { vm.updateSettings { st -> st.copy(location = it) } }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("自定义系统提示词（留空使用默认）") },
                    minLines = 2,
                    maxLines = 5,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                SecondaryButton("保存提示词") {
                    vm.updateSettings { st -> st.copy(systemPrompt = systemPrompt.trim()) }
                    vm.showToast("已保存")
                }
            }

            SettingsCard(title = "语音", icon = Icons.Rounded.RecordVoiceOver) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VoiceChip("stella", "海星 · 女声", s.ttsVoice, Modifier.weight(1f)) {
                        vm.updateSettings { st -> st.copy(ttsVoice = it) }
                    }
                    VoiceChip("tide", "暗潮 · 男声", s.ttsVoice, Modifier.weight(1f)) {
                        vm.updateSettings { st -> st.copy(ttsVoice = it) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                ToggleRow("自动朗读回答", "回答结束后自动合成语音", s.autoRead) {
                    vm.updateSettings { st -> st.copy(autoRead = it) }
                }
                Text(
                    "语音合成与识别使用 /v1/tools/tts 与 /v1/tools/asr，按字符与时长计费。",
                    color = p.faint,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                )
            }

            SettingsCard(title = "外观", icon = Icons.Rounded.DarkMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("跟随系统", "浅色", "深色").forEachIndexed { index, label ->
                        SelectableTile(
                            label = label,
                            selected = s.themeMode == index,
                            modifier = Modifier.weight(1f),
                        ) { vm.updateSettings { st -> st.copy(themeMode = index) } }
                    }
                }
                Spacer(Modifier.height(4.dp))
                ToggleRow("动态取色", "跟随系统壁纸（Android 12+）", s.dynamicColor) {
                    vm.updateSettings { st -> st.copy(dynamicColor = it) }
                }
            }

            SettingsCard(title = "数据", icon = Icons.Rounded.FileDownload) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("导出会话", Modifier.weight(1f)) {
                        exportLauncher.launch("deepseek-hot-${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}.json")
                    }
                    SecondaryButton("导入会话", Modifier.weight(1f)) {
                        importLauncher.launch(arrayOf("application/json", "text/plain"))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "本地统计：${vm.stats.requests} 次请求 · ${Fmt.tokens((vm.stats.inputTokens + vm.stats.outputTokens).toInt())} tokens · ${Fmt.money(vm.stats.cost)}",
                    color = p.faint,
                    fontSize = 11.sp,
                )
            }

            SettingsCard(title = "关于", icon = Icons.Rounded.Info) {
                SettingRow(label = "关于 DeepSeek Hot", value = "v1.1.0", onClick = onAbout)
                Text(
                    "为 DeepSeek 全家桶 API 打造的原生 Android 客户端，Kotlin + Jetpack Compose 实现。",
                    color = p.faint,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                )
            }
        }
    }
}

@Composable
private fun fieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = LocalPalette.current.surfaceAlt,
    unfocusedContainerColor = LocalPalette.current.surfaceAlt,
    focusedIndicatorColor = LocalPalette.current.primary.copy(alpha = 0.6f),
    unfocusedIndicatorColor = LocalPalette.current.border,
    focusedTextColor = LocalPalette.current.text,
    unfocusedTextColor = LocalPalette.current.text,
    focusedLabelColor = LocalPalette.current.dim,
    unfocusedLabelColor = LocalPalette.current.faint,
    cursorColor = LocalPalette.current.primary,
)

@Composable
fun SettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(p.surface.copy(alpha = 0.72f))
            .border(1.dp, p.border, RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = p.primary, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(7.dp))
            Text(title, color = p.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
fun SettingRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = p.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = p.dim, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
fun ToggleRow(label: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = p.text, fontSize = 13.sp)
            Text(desc, color = p.faint, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = p.primary,
                uncheckedThumbColor = p.faint,
                uncheckedTrackColor = p.surfaceHigh,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    Tappable(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(p.primary),
        onClick = onClick,
    ) {
        Text(
            text,
            color = Color.White,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    Tappable(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(p.surfaceAlt)
            .border(1.dp, p.border, RoundedCornerShape(12.dp)),
        onClick = onClick,
    ) {
        Text(
            text,
            color = p.dim,
            fontSize = 12.5.sp,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun SelectableTile(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    Tappable(
        modifier
            .height(38.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) p.primary.copy(alpha = 0.16f) else p.surfaceAlt)
            .border(
                1.dp,
                if (selected) p.primary.copy(alpha = 0.4f) else p.border,
                RoundedCornerShape(11.dp),
            ),
        onClick = onClick,
    ) {
        Text(
            label,
            color = if (selected) p.primary else p.dim,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun VoiceChip(id: String, label: String, selected: String, modifier: Modifier = Modifier, onPick: (String) -> Unit) {
    val p = LocalPalette.current
    val active = selected == id
    Tappable(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) p.primary.copy(alpha = 0.14f) else p.surfaceAlt)
            .border(1.dp, if (active) p.primary.copy(alpha = 0.4f) else p.border, RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp),
        onClick = { onPick(id) },
    ) {
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = if (active) p.primary else p.dim, fontSize = 12.sp)
            if (active) {
                Spacer(Modifier.width(5.dp))
                Icon(Icons.Rounded.Check, null, tint = p.primary, modifier = Modifier.size(13.dp))
            }
        }
    }
}

@Composable
private fun ChipSelect(options: List<String>, selected: String, modifier: Modifier = Modifier, onPick: (String) -> Unit) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option ->
            SelectableTile(
                label = option,
                selected = option == selected,
                modifier = Modifier.weight(1f),
            ) { onPick(option) }
        }
    }
}
