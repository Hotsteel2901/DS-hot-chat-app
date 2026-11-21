// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Hotsteel2901

package com.hotsteel.deepseekhot.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hotsteel.deepseekhot.AppViewModel
import com.hotsteel.deepseekhot.data.ModelInfo
import com.hotsteel.deepseekhot.data.Protocol
import com.hotsteel.deepseekhot.ui.theme.LocalPalette
import com.hotsteel.deepseekhot.util.Fmt
import com.hotsteel.deepseekhot.util.Pricing
import com.hotsteel.deepseekhot.util.Sys

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = p.surface,
        dragHandle = { SheetHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("模型与接口", color = p.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "自动从 /models 获取 · 支持自由切换协议",
                        color = p.faint,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                Tappable(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(p.surfaceAlt),
                    onClick = { vm.refreshModels() },
                ) {
                    val transition = rememberInfiniteTransition(label = "spin")
                    val spin by transition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
                        label = "angle",
                    )
                    val angle = if (vm.modelsLoading) spin else 0f
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = "刷新模型",
                        tint = p.dim,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(17.dp)
                            .rotate(angle),
                    )
                }
            }

            if (vm.modelsLoading) {
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .shimmer()
                )
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel("模型")
            Spacer(Modifier.height(8.dp))

            vm.models.forEach { model ->
                ModelRow(
                    model = model,
                    selected = model.id == vm.settings.model,
                    onClick = { vm.setModel(model.id) },
                )
                Spacer(Modifier.height(8.dp))
            }
            if (vm.modelsError != null) {
                Text(
                    text = "获取模型失败：${vm.modelsError}",
                    color = p.danger,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("思考强度")
                Spacer(Modifier.weight(1f))
                Text(
                    text = "effort",
                    color = p.faint,
                    fontSize = 10.sp,
                )
            }
            Spacer(Modifier.height(8.dp))

            val model = vm.models.firstOrNull { it.id == vm.settings.model }
            val levels = (listOf("none") + (model?.efforts.orEmpty().ifEmpty { listOf("low", "high", "max") }))
                .distinct()
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                levels.forEach { level ->
                    EffortChip(
                        level = level,
                        selected = level == vm.settings.effort,
                        modifier = Modifier.weight(1f),
                        onClick = { vm.setEffort(level) },
                    )
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(
                text = effortHint(vm.settings.effort),
                color = p.faint,
                fontSize = 11.sp,
                lineHeight = 17.sp,
            )

            Spacer(Modifier.height(22.dp))
            SectionLabel("接口格式")
            Spacer(Modifier.height(8.dp))
            Protocol.entries.forEach { protocol ->
                ProtocolRow(
                    protocol = protocol,
                    selected = protocol == vm.settings.protocol,
                    onClick = { vm.setProtocol(protocol) },
                )
                Spacer(Modifier.height(7.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "切换协议只影响请求格式，模型、联网与文件能力保持一致；Anthropic 格式支持最完整的服务端工具块。",
                color = p.faint,
                fontSize = 11.sp,
                lineHeight = 17.sp,
            )
        }
    }
}

@Composable
private fun SheetHandle() {
    Box(
        Modifier
            .padding(vertical = 10.dp)
            .size(width = 38.dp, height = 4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(LocalPalette.current.faint.copy(alpha = 0.5f))
    )
}

private fun effortHint(level: String): String = when (level) {
    "none" -> "关闭思考，直接作答，速度最快、消耗最低。"
    "low" -> "轻度思考，适合日常问答与改写。"
    "high" -> "默认深度思考，兼顾质量与耗时。"
    "max" -> "极限思考，模型会花更多时间推演，适合难题与数学。"
    else -> ""
}

@Composable
private fun EffortChip(level: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val label = when (level) {
        "none" -> "关闭"
        "low" -> "低"
        "high" -> "高"
        "max" -> "极限"
        else -> level
    }
    Tappable(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) p.primary.copy(alpha = 0.18f) else p.surfaceAlt)
            .border(
                1.dp,
                if (selected) p.primary.copy(alpha = 0.45f) else p.border,
                RoundedCornerShape(12.dp),
            )
            .padding(vertical = 10.dp),
        onClick = onClick,
    ) {
        Text(
            text = label,
            color = if (selected) p.primary else p.dim,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun ModelRow(model: ModelInfo, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(if (selected) p.primary.copy(alpha = 0.10f) else p.surfaceAlt.copy(alpha = 0.6f))
            .border(
                1.dp,
                if (selected) p.primary.copy(alpha = 0.4f) else p.border,
                RoundedCornerShape(15.dp),
            )
            .clickable(onClick = onClick)
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(
                    if (selected) Brush.linearGradient(listOf(p.primary, p.accent))
                    else Brush.linearGradient(listOf(p.surfaceHigh, p.surfaceHigh))
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (model.id.contains("pro")) "PRO" else "F",
                color = if (selected) Color.White else p.dim,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(model.displayName, color = p.text, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
            Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(model.id, color = p.faint, fontSize = 10.sp)
                model.contextWindow?.let {
                    Spacer(Modifier.width(6.dp))
                    Tag("${Fmt.tokens(it)} 上下文", p.accent)
                }
                if (model.imageInput) {
                    Spacer(Modifier.width(5.dp))
                    Tag("视觉", p.success)
                }
            }
        }
        AnimatedVisibility(visible = selected) {
            Icon(Icons.Rounded.Check, null, tint = p.primary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ProtocolRow(protocol: Protocol, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val desc = when (protocol) {
        Protocol.CHAT -> "OpenAI 兼容 · /chat/completions"
        Protocol.RESPONSES -> "OpenAI 新接口 · /responses"
        Protocol.ANTHROPIC -> "Claude 兼容 · /anthropic/v1/messages"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(if (selected) p.primary.copy(alpha = 0.10f) else p.surfaceAlt.copy(alpha = 0.5f))
            .border(
                1.dp,
                if (selected) p.primary.copy(alpha = 0.4f) else p.border,
                RoundedCornerShape(13.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(protocol.label, color = p.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(desc, color = p.faint, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .border(1.5.dp, if (selected) p.primary else p.faint.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(p.primary)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = androidx.compose.ui.platform.LocalContext.current
    val stats = vm.stats
    val balance = vm.balance
    val info = balance?.infos?.firstOrNull()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = p.surface,
        dragHandle = { SheetHandle() },
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
                Column(Modifier.weight(1f)) {
                    Text("用量与余额", color = p.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("实时查询 · 本地统计", color = p.faint, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
                }
                if (vm.gateway.supportsBalance) {
                    Tappable(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(p.surfaceAlt)
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        onClick = { vm.refreshBalance() },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Refresh, null, tint = p.dim, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(if (vm.balanceLoading) "查询中" else "刷新", color = p.dim, fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(p.primary.copy(alpha = 0.22f), p.accent.copy(alpha = 0.12f))))
                    .border(1.dp, p.primary.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                    .padding(18.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (balance?.available == true) p.success else p.faint)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when {
                                !vm.gateway.supportsBalance -> "${vm.gateway.label} · 无余额接口"
                                balance == null -> "尚未查询余额"
                                balance.available -> "账户可用"
                                else -> "账户不可用"
                            },
                            color = p.dim,
                            fontSize = 11.sp,
                        )
                        Spacer(Modifier.weight(1f))
                        if (balance != null) {
                            Text(
                                "更新于 ${Fmt.clock(balance.fetchedAt)}",
                                color = p.faint,
                                fontSize = 10.sp,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = info?.let { "¥${it.total}" } ?: if (vm.gateway.supportsBalance) "—" else "—",
                        color = p.text,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.8).sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        MiniStat("赠送余额", info?.let { "¥${it.granted}" } ?: "—")
                        MiniStat("充值余额", info?.let { "¥${it.toppedUp}" } ?: "—")
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    title = "今日请求",
                    value = stats.dayRequests.toString(),
                    sub = "${Fmt.tokens(stats.dayTokens.toInt())} tokens",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    title = "今日花费",
                    value = Fmt.money(stats.dayCost),
                    sub = "估算",
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    title = "累计请求",
                    value = stats.requests.toString(),
                    sub = if (stats.inputTokens + stats.outputTokens > 0) {
                        "${Fmt.tokens((stats.inputTokens + stats.outputTokens).toInt())} tokens"
                    } else "—",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    title = "累计花费",
                    value = Fmt.money(stats.cost),
                    sub = "按官方价目估算",
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(18.dp))
            SectionLabel("计费参考")
            Spacer(Modifier.height(8.dp))
            Text(
                text = Pricing.priceLabel(vm.settings.model),
                color = p.dim,
                fontSize = 11.5.sp,
            )
            Text(
                text = "高峰时段：北京时间工作日 9:00–12:00、14:00–18:00，单价为空闲时段的 2 倍。联网搜索与网页抓取的工具调用自 2026-11-18 起不再单独计费，结果按输入 Token 计费。",
                color = p.faint,
                fontSize = 11.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 5.dp),
            )
            Spacer(Modifier.height(12.dp))
            Tappable(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(p.surfaceAlt)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                onClick = {
                    Sys.openUrl(
                        context,
                        if (vm.gateway.isOpencode) "https://opencode.ai/console"
                        else "https://platform.deepseek.com/top_up"
                    )
                },
            ) {
                Text(
                    if (vm.gateway.isOpencode) "前往 OpenCode Console 管理用量 →" else "前往开放平台充值 →",
                    color = p.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "余额与用量以服务端返回为准；本地统计仅覆盖本机请求，费用为估算值。",
                color = p.faint,
                fontSize = 10.sp,
                lineHeight = 15.sp,
            )
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    val p = LocalPalette.current
    Column {
        Text(label, color = p.faint, fontSize = 10.sp)
        Text(value, color = p.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun StatTile(title: String, value: String, sub: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(p.surfaceAlt.copy(alpha = 0.7f))
            .padding(14.dp),
    ) {
        Text(title, color = p.faint, fontSize = 10.5.sp)
        Spacer(Modifier.height(5.dp))
        Text(value, color = p.text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Text(sub, color = p.faint, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
    }
}
