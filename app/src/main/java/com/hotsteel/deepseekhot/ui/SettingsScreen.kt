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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hotsteel.deepseekhot.AppViewModel
import com.hotsteel.deepseekhot.BuildConfig
import com.hotsteel.deepseekhot.ui.theme.LocalPalette
import com.hotsteel.deepseekhot.util.Fmt
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

    LaunchedEffect(s.apiKey) {
        if (key != s.apiKey) key = s.apiKey
    }
    LaunchedEffect(s.baseUrl) {
        if (base != s.baseUrl) base = s.baseUrl
    }
    LaunchedEffect(s.systemPrompt) {
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

    AppBackground(Modifier.fillMaxSize(), color = p.bgAlt) {
        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ScreenHeader(title = "设置", onBack = onBack)

            Column(
                Modifier
                    .widthIn(max = 700.dp)
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SettingsCard(
                    title = "账号与接口",
                    icon = Icons.Rounded.Key,
                    badge = if (s.apiKey.isNotBlank()) "已配置" else "未配置",
                    badgeColor = if (s.apiKey.isNotBlank()) p.success else p.danger,
                ) {
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it },
                        label = { Text("DeepSeek API Key") },
                        placeholder = { Text("sk-...", color = p.faint) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Text(
                                if (showKey) "隐藏" else "显示",
                                color = p.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showKey = !showKey }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                            )
                        },
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = base,
                        onValueChange = { base = it },
                        label = { Text("Base URL") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = if (base.trim() != "https://api.deepseek.com") {
                            {
                                Text(
                                    "默认",
                                    color = p.dim,
                                    fontSize = 11.5.sp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { base = "https://api.deepseek.com" }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                )
                            }
                        } else null,
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryButton("保存并测试", Modifier.weight(1f)) {
                            val cleanBase = base.trim().ifBlank { "https://api.deepseek.com" }
                            base = cleanBase
                            vm.updateSettings { it.copy(apiKey = key.trim(), baseUrl = cleanBase) }
                            vm.refreshModels()
                            vm.refreshBalance()
                        }
                        SecondaryButton("查询余额", Modifier.weight(1f)) { onUsage() }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "API Key 仅保存在本机 DataStore，不会上传到任何第三方服务。",
                        color = p.faint,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                    )
                }

                SettingsCard(
                    title = "网关兼容",
                    icon = Icons.Rounded.Security,
                    badge = vm.gateway.label,
                    badgeColor = p.primary,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("自动识别", "始终发送", "关闭").forEachIndexed { index, label ->
                            SelectableTile(
                                label = label,
                                selected = s.opencodeSession == index,
                                modifier = Modifier.weight(1f),
                            ) { vm.updateSettings { st -> st.copy(opencodeSession = index) } }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "x-opencode-session：OpenCode v2 / Go / Zen 要求请求携带稳定的会话 ID。" +
                            "自动识别仅在 Base URL 命中 opencode.ai 时发送；选「始终发送」可给自建反代补头。",
                        color = p.faint,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                    )
                    if (vm.gateway.isOpencode) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "该网关未提供 Files / 余额 / 语音接口，相关入口已自动隐藏或降级。",
                            color = p.dim,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                        )
                    }
                }

                SettingsCard(title = "模型与协议", icon = Icons.Rounded.Tune) {
                    SettingRow(
                        label = "当前模型",
                        value = vm.models.firstOrNull { it.id == s.model }?.displayName ?: s.model,
                        onClick = onModels,
                    )
                    SettingsDivider()
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
                    SettingsDivider()
                    SettingRow(
                        label = "接口格式",
                        value = s.protocol.label,
                        onClick = onModels,
                    )
                    Text(
                        "同一套能力在 Chat Completions / Responses / Anthropic Messages 三种格式下均可用，可随时切换。",
                        color = p.faint,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                SettingsCard(title = "对话能力", icon = Icons.Rounded.Language) {
                    ToggleRow("联网搜索", "deepseek_web_search · 自动搜索网页并附来源引用", s.webSearch) {
                        vm.updateSettings { st -> st.copy(webSearch = it) }
                    }
                    SettingsDivider()
                    ToggleRow("阅读链接", "deepseek_read_url · 自动抓取消息中的网页链接", s.readUrl) {
                        vm.updateSettings { st -> st.copy(readUrl = it) }
                    }
                    SettingsDivider()
                    ToggleRow("内容安全", "safety_check: high · 命中风险时自动拦截", s.safetyCheck) {
                        vm.updateSettings { st -> st.copy(safetyCheck = it) }
                    }
                    SettingsDivider()
                    ToggleRow("自动生成标题", "首轮对话结束后自动总结会话标题", s.autoTitle) {
                        vm.updateSettings { st -> st.copy(autoTitle = it) }
                    }
                    SettingsDivider()
                    ToggleRow("注入时间与地点", "将当前日期、所在地与语言作为上下文元信息", s.includeMeta) {
                        vm.updateSettings { st -> st.copy(includeMeta = it) }
                    }
                }

                SettingsCard(title = "个性化与提示词", icon = Icons.Rounded.Palette) {
                    SectionLabel("回复语言")
                    Spacer(Modifier.height(8.dp))
                    ChipSelect(
                        listOf("中文", "English", "日本語"),
                        s.language,
                        Modifier.fillMaxWidth(),
                    ) { vm.updateSettings { st -> st.copy(language = it) } }

                    Spacer(Modifier.height(12.dp))
                    SectionLabel("所在地区")
                    Spacer(Modifier.height(8.dp))
                    ChipSelect(
                        listOf("中国", "中国香港", "中国台湾", "新加坡"),
                        s.location,
                        Modifier.fillMaxWidth(),
                    ) { vm.updateSettings { st -> st.copy(location = it) } }

                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = systemPrompt,
                        onValueChange = { systemPrompt = it },
                        label = { Text("自定义系统提示词（留空使用默认）") },
                        minLines = 2,
                        maxLines = 5,
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    SecondaryButton("保存系统提示词", Modifier.fillMaxWidth()) {
                        vm.updateSettings { st -> st.copy(systemPrompt = systemPrompt.trim()) }
                        vm.showToast("系统提示词已保存")
                    }
                }

                SettingsCard(title = "语音交互", icon = Icons.Rounded.RecordVoiceOver) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VoiceChip("stella", "海星 · 女声", s.ttsVoice, Modifier.weight(1f)) {
                            vm.updateSettings { st -> st.copy(ttsVoice = it) }
                        }
                        VoiceChip("tide", "暗潮 · 男声", s.ttsVoice, Modifier.weight(1f)) {
                            vm.updateSettings { st -> st.copy(ttsVoice = it) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    ToggleRow("自动朗读回答", "回答完成后自动合成并播放语音", s.autoRead) {
                        vm.updateSettings { st -> st.copy(autoRead = it) }
                    }
                    Text(
                        "语音合成与识别使用 /v1/tools/tts 与 /v1/tools/asr，按字符与时长计费。",
                        color = p.faint,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                SettingsCard(title = "外观与体验", icon = Icons.Rounded.DarkMode) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("跟随系统", "浅色", "深色").forEachIndexed { index, label ->
                            SelectableTile(
                                label = label,
                                selected = s.themeMode == index,
                                modifier = Modifier.weight(1f),
                            ) { vm.updateSettings { st -> st.copy(themeMode = index) } }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    ToggleRow("动态取色", "跟随系统壁纸调整强调色（Android 12+）", s.dynamicColor) {
                        vm.updateSettings { st -> st.copy(dynamicColor = it) }
                    }
                    SettingsDivider()
                    ToggleRow("触感反馈", "发送或停止生成时提供轻微震动反馈", s.haptics) {
                        vm.updateSettings { st -> st.copy(haptics = it) }
                    }
                }

                SettingsCard(title = "数据与统计", icon = Icons.Rounded.FileDownload) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton("导出会话", Modifier.weight(1f)) {
                            exportLauncher.launch("deepseek-hot-${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}.json")
                        }
                        SecondaryButton("导入会话", Modifier.weight(1f)) {
                            importLauncher.launch(arrayOf("application/json", "text/plain"))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "本地累计：${vm.stats.requests} 次请求 · ${Fmt.tokens((vm.stats.inputTokens + vm.stats.outputTokens).toInt())} tokens · ${Fmt.money(vm.stats.cost)}",
                        color = p.faint,
                        fontSize = 11.5.sp,
                    )
                }

                SettingsCard(title = "关于", icon = Icons.Rounded.Info) {
                    SettingRow(label = "关于 DeepSeek Hot", value = "v${BuildConfig.VERSION_NAME}", onClick = onAbout)
                    Text(
                        "为 DeepSeek 全家桶应用 API 打造的第三方原生 Android 客户端。",
                        color = p.faint,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = LocalPalette.current.surfaceAlt,
    unfocusedContainerColor = LocalPalette.current.surfaceAlt,
    focusedBorderColor = LocalPalette.current.primary,
    unfocusedBorderColor = LocalPalette.current.border,
    focusedTextColor = LocalPalette.current.text,
    unfocusedTextColor = LocalPalette.current.text,
    focusedLabelColor = LocalPalette.current.primary,
    unfocusedLabelColor = LocalPalette.current.faint,
    cursorColor = LocalPalette.current.primary,
)

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        color = LocalPalette.current.border,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

@Composable
fun SettingsCard(
    title: String,
    icon: ImageVector,
    badge: String? = null,
    badgeColor: Color = LocalPalette.current.primary,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(p.surface)
            .border(1.dp, p.border, shape)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = p.primary, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                color = p.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (badge != null) {
                Tag(badge, badgeColor)
            }
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
            .clip(RoundedCornerShape(10.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 11.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = p.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = p.dim, fontSize = 13.sp, maxLines = 1)
        if (onClick != null) {
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Rounded.ChevronRight, null, tint = p.faint, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun ToggleRow(label: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = p.text, fontSize = 14.sp)
            Text(desc, color = p.faint, fontSize = 11.5.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = p.primary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = p.surfaceHigh,
                uncheckedBorderColor = p.border,
            ),
        )
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    Tappable(
        modifier
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(p.primary),
        onClick = onClick,
    ) {
        Text(
            text,
            color = Color.White,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(12.dp)
    Tappable(
        modifier
            .height(42.dp)
            .clip(shape)
            .background(p.surfaceAlt)
            .border(1.dp, p.border, shape),
        onClick = onClick,
    ) {
        Text(
            text,
            color = p.text,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun SelectableTile(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(11.dp)
    Tappable(
        modifier
            .height(38.dp)
            .clip(shape)
            .background(if (selected) p.primarySoft else p.surfaceAlt)
            .border(
                1.dp,
                if (selected) p.primary.copy(alpha = 0.45f) else p.border,
                shape,
            ),
        onClick = onClick,
    ) {
        Text(
            label,
            color = if (selected) p.primary else p.dim,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun VoiceChip(id: String, label: String, selected: String, modifier: Modifier = Modifier, onPick: (String) -> Unit) {
    val p = LocalPalette.current
    val active = selected == id
    val shape = RoundedCornerShape(12.dp)
    Tappable(
        modifier
            .clip(shape)
            .background(if (active) p.primarySoft else p.surfaceAlt)
            .border(1.dp, if (active) p.primary.copy(alpha = 0.45f) else p.border, shape)
            .padding(vertical = 10.dp),
        onClick = { onPick(id) },
    ) {
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                color = if (active) p.primary else p.dim,
                fontSize = 13.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (active) {
                Spacer(Modifier.width(5.dp))
                Icon(Icons.Rounded.Check, null, tint = p.primary, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun ChipSelect(options: List<String>, selected: String, modifier: Modifier = Modifier, onPick: (String) -> Unit) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            SelectableTile(
                label = option,
                selected = option == selected,
                modifier = Modifier.weight(1f),
            ) { onPick(option) }
        }
    }
}
