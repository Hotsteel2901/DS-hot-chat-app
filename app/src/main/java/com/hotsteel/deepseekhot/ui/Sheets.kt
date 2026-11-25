// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Hotsteel2901

package com.hotsteel.deepseekhot.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
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
                    Text("模型与接口", color = p.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "自动从 /models 获取 · 支持随时切换协议",
                        color = p.faint,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Tappable(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(p.surfaceAlt)
                        .border(1.dp, p.border, CircleShape),
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
                            .size(18.dp)
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

            Spacer(Modifier.height(18.dp))
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
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("思考强度")
                Spacer(Modifier.weight(1f))
                Text(
                    text = "effort",
                    color = p.faint,
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.height(8.dp))

            val model = vm.models.firstOrNull { it.id == vm.settings.model }
            val levels = (listOf("none") + (model?.efforts.orEmpty().ifEmpty { listOf("low", "high", "max") }))
                .distinct()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(p.surfaceAlt)
                    .border(1.dp, p.border, RoundedCornerShape(12.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                levels.forEach { level ->
                    EffortSegment(
                        level = level,
                        selected = level == vm.settings.effort,
                        modifier = Modifier.weight(1f),
                        onClick = { vm.setEffort(level) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = effortHint(vm.settings.effort),
                color = p.faint,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )

            Spacer(Modifier.height(20.dp))
            SectionLabel("接口格式")
            Spacer(Modifier.height(8.dp))
            Protocol.entries.forEach { protocol ->
                ProtocolRow(
                    protocol = protocol,
                    selected = protocol == vm.settings.protocol,
                    onClick = { vm.setProtocol(protocol) },
                )
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "切换协议仅影响请求结构，模型、联网与文件能力保持一致；Anthropic 格式支持最完整的服务端工具块。",
                color = p.faint,
                fontSize = 11.5.sp,
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
            .size(width = 36.dp, height = 4.dp)
            .clip(CircleShape)
            .background(LocalPalette.current.border)
    )
}

private fun effortHint(level: String): String = when (level) {
    "none" -> "关闭思考，直接作答，响应最快、消耗最低。"
    "low" -> "轻度思考，适合日常问答、翻译与改写。"
    "high" -> "默认深度思考，兼顾回答质量与思考耗时。"
    "max" -> "极限思考，模型会投入更多推理步骤，适合复杂编程与数学。"
    else -> ""
}

@Composable
private fun EffortSegment(level: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) p.surface else Color.Transparent)
            .then(if (selected) Modifier.border(1.dp, p.primary.copy(alpha = 0.35f), RoundedCornerShape(9.dp)) else Modifier)
            .padding(vertical = 9.dp),
        onClick = onClick,
    ) {
        Text(
            text = label,
            color = if (selected) p.primary else p.dim,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun ModelRow(model: ModelInfo, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) p.primarySoft else p.surfaceAlt)
            .border(
                1.dp,
                if (selected) p.primary.copy(alpha = 0.45f) else p.border,
                shape,
            )
            .clickable(onClick = onClick)
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (selected) p.primary else p.surfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (model.id.contains("pro")) "PRO" else "DS",
                color = if (selected) Color.White else p.dim,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(model.displayName, color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(model.id, color = p.faint, fontSize = 11.sp)
                model.contextWindow?.let {
                    Spacer(Modifier.width(6.dp))
                    Tag("${Fmt.tokens(it)} 上下文", p.primary)
                }
                if (model.imageInput) {
                    Spacer(Modifier.width(5.dp))
                    Tag("视觉", p.success)
                }
            }
        }
        AnimatedVisibility(visible = selected) {
            Icon(Icons.Rounded.Check, null, tint = p.primary, modifier = Modifier.size(19.dp))
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
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) p.primarySoft else p.surfaceAlt)
            .border(
                1.dp,
                if (selected) p.primary.copy(alpha = 0.45f) else p.border,
                shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(protocol.label, color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(desc, color = p.faint, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Box(
            Modifier
                .size(19.dp)
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
    val context = LocalContext.current
    val stats = vm.stats
    val balance = vm.balance
    val info = balance?.infos?.firstOrNull()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = p.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
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
                    Text("用量与余额", color = p.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("实时余额 · 本地请求统计", color = p.faint, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
                if (vm.gateway.supportsBalance) {
                    Tappable(
                        Modifier
                            .clip(CircleShape)
                            .background(p.surfaceAlt)
                            .border(1.dp, p.border, CircleShape)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        onClick = { vm.refreshBalance() },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Refresh, null, tint = p.dim, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(if (vm.balanceLoading) "查询中" else "刷新", color = p.dim, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            val heroShape = RoundedCornerShape(18.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(heroShape)
                    .background(p.primarySoft)
                    .border(1.dp, p.primary.copy(alpha = 0.25f), heroShape)
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
                        Spacer(Modifier.width(7.dp))
                        Text(
                            when {
                                !vm.gateway.supportsBalance -> "${vm.gateway.label} · 无余额接口"
                                balance == null -> "尚未查询余额"
                                balance.available -> "账户可用"
                                else -> "账户不可用"
                            },
                            color = p.dim,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.weight(1f))
                        if (balance != null) {
                            Text(
                                "更新于 ${Fmt.clock(balance.fetchedAt)}",
                                color = p.faint,
                                fontSize = 11.sp,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = info?.let { "¥${it.total}" } ?: "—",
                        color = p.text,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.8).sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        MiniStat("赠送余额", info?.let { "¥${it.granted}" } ?: "—")
                        MiniStat("充值余额", info?.let { "¥${it.toppedUp}" } ?: "—")
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
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
                    sub = "按官方价目估算",
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            // 上下文硬盘缓存命中率与节省费用专属卡片
            val cacheShape = RoundedCornerShape(14.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(cacheShape)
                    .background(p.surfaceAlt)
                    .border(1.dp, p.success.copy(alpha = 0.32f), cacheShape)
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("上下文硬盘缓存命中率", color = p.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Tag(
                        if (vm.settings.cacheOptimization) "极致优化中" else "已关闭优化",
                        if (vm.settings.cacheOptimization) p.success else p.faint,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = Fmt.percent(stats.overallCacheHitRate),
                        color = p.success,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Column(Modifier.padding(bottom = 3.dp)) {
                        Text(
                            "累计命中 ${Fmt.tokensLong(stats.cachedTokens)} tokens · 已省 ${Fmt.money(stats.savedCost)}",
                            color = p.dim,
                            fontSize = 11.5.sp,
                        )
                        Text(
                            "今日命中率 ${Fmt.percent(stats.dayCacheHitRate)} · 今日省 ${Fmt.money(stats.daySavedCost)}",
                            color = p.faint,
                            fontSize = 11.sp,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(p.surfaceHigh)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(stats.overallCacheHitRate.coerceAtLeast(if (stats.cachedTokens > 0) 0.03f else 0f))
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(p.success)
                    )
                }
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
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(p.surfaceAlt)
                    .border(1.dp, p.border, RoundedCornerShape(14.dp))
                    .padding(14.dp),
            ) {
                Text(
                    text = Pricing.priceLabel(vm.settings.model),
                    color = p.text,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "高峰时段：北京时间工作日 9:00–12:00、14:00–18:00，单价为空闲时段的 2 倍。联网搜索与网页抓取工具调用结果按输入 Token 计费。",
                    color = p.faint,
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Spacer(Modifier.height(14.dp))
            Tappable(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(p.primarySoft)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                onClick = {
                    Sys.openUrl(
                        context,
                        if (vm.gateway.isOpencode) "https://opencode.ai/console"
                        else "https://platform.deepseek.com/top_up"
                    )
                },
            ) {
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (vm.gateway.isOpencode) "前往 OpenCode Console 管理用量" else "前往 DeepSeek 开放平台充值",
                        color = p.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = p.primary, modifier = Modifier.size(15.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "余额与用量以服务端返回为准；本地统计仅覆盖本机请求，费用为估算值。",
                color = p.faint,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    val p = LocalPalette.current
    Column {
        Text(label, color = p.dim, fontSize = 11.sp)
        Text(value, color = p.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun StatTile(title: String, value: String, sub: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(shape)
            .background(p.surfaceAlt)
            .border(1.dp, p.border, shape)
            .padding(14.dp),
    ) {
        Text(title, color = p.faint, fontSize = 11.5.sp)
        Spacer(Modifier.height(6.dp))
        Text(value, color = p.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(sub, color = p.faint, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
    }
}
