// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Hotsteel2901

package com.hotsteel.deepseekhot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hotsteel.deepseekhot.BuildConfig
import com.hotsteel.deepseekhot.ui.liquid.LocalAmbientBackdrop
import com.hotsteel.deepseekhot.ui.liquid.liquidGlass
import com.hotsteel.deepseekhot.ui.theme.LocalPalette
import com.hotsteel.deepseekhot.util.Sys

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current

    AppBackground(Modifier.fillMaxSize(), color = p.bgAlt) {
        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ScreenHeader(title = "关于", onBack = onBack)

            Column(
                Modifier
                    .widthIn(max = 700.dp)
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(16.dp))
                BrandMark(68.dp)
                Spacer(Modifier.height(14.dp))
                Text(
                    "DeepSeek Hot",
                    color = p.text,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp,
                )
                Text(
                    "v${BuildConfig.VERSION_NAME} · Liquid Glass · Kotlin / Compose",
                    color = p.faint,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )

                Spacer(Modifier.height(16.dp))
                Text(
                    text = "遵循 DeepSeek 构建 Chatbot 指南打造的第三方原生 Android 客户端。采用极简现代视觉与流畅的流式交互体验，完整支持联网搜索、深度思考、文件解析、阅读链接、语音输入与朗读，并可在三种接口协议间自由切换。",
                    color = p.dim,
                    fontSize = 13.5.sp,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(22.dp))
                AuthorCard(
                    onGithub = { Sys.openUrl(context, "https://github.com/Hotsteel2901") },
                    onMail = { Sys.openUrl(context, "mailto:i.r.hotsteel@gmail.com") },
                    onCopyGithub = { Sys.copy(context, "Hotsteel2901", "GitHub 用户名已复制") },
                    onCopyMail = { Sys.copy(context, "i.r.hotsteel@gmail.com", "邮箱已复制") },
                )

                Spacer(Modifier.height(14.dp))
                InfoCard(
                    title = "能力清单",
                    lines = listOf(
                        "联网搜索与深度思考搜索（deepseek_web_search）",
                        "阅读链接（deepseek_read_url）/ 文件解析（PDF、Office、图片）",
                        "Chat Completions / Responses / Anthropic Messages 自由切换",
                        "自动获取模型列表，可调思考强度（none / low / high / max）",
                        "实时余额、Token 用量、首字延迟与生成速度",
                        "语音输入（ASR）与朗读回答（TTS）",
                        "完整 Liquid Glass 液态玻璃折射（支持完整/轻量/关闭三档性能调节）",
                        "全能富文本渲染：KaTeX 数学与化学公式、Mermaid/SVG/HTML/Chart/DOT/ASCII 图表与语法高亮",
                        "会话分支、编辑重发、续写、导入导出",
                    ),
                )

                Spacer(Modifier.height(14.dp))
                InfoCard(
                    title = "参考与致谢",
                    lines = listOf(
                        "DeepSeek API 文档 · api-docs.deepseek.com",
                        "官方示例 chat-demo · github.com/deepseek-ai/chat-demo",
                    ),
                    links = listOf(
                        "https://api-docs.deepseek.com/zh-cn/guides/build_a_chatbot" to "API 指南 - 构建 Chatbot",
                        "https://github.com/deepseek-ai/chat-demo" to "deepseek-ai/chat-demo",
                    ),
                )

                Spacer(Modifier.height(18.dp))
                Text(
                    text = "本应用与深度求索（DeepSeek）无隶属关系，API Key 仅保存在本机。生成内容由 AI 产生，请自行甄别并遵守所在地法律法规与开放平台服务协议。",
                    color = p.faint,
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun AuthorCard(
    onGithub: () -> Unit,
    onMail: () -> Unit,
    onCopyGithub: () -> Unit,
    onCopyMail: () -> Unit,
) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .liquidGlass(backdrop = LocalAmbientBackdrop.current, cornerRadius = 20.dp)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(p.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text("H", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Hotsteel2901", color = p.text, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold)
                Text("独立开发者 · ROM / Android", color = p.faint, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        LinkRow(
            icon = Icons.AutoMirrored.Rounded.OpenInNew,
            label = "github.com/Hotsteel2901",
            onClick = onGithub,
            onCopy = onCopyGithub,
        )
        Spacer(Modifier.height(8.dp))
        LinkRow(
            icon = Icons.Rounded.Email,
            label = "i.r.hotsteel@gmail.com",
            onClick = onMail,
            onCopy = onCopyMail,
        )
    }
}

@Composable
private fun LinkRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    onCopy: () -> Unit,
) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(p.surfaceAlt)
            .border(1.dp, p.border, shape)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = p.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            color = p.text,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        IconTap(Icons.Rounded.ContentCopy, "复制", onCopy, tint = p.faint, size = 32.dp, iconSize = 15.dp)
        Text(
            "打开",
            color = p.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun InfoCard(
    title: String,
    lines: List<String>,
    links: List<Pair<String, String>> = emptyList(),
) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .liquidGlass(backdrop = LocalAmbientBackdrop.current, cornerRadius = 20.dp)
            .padding(16.dp),
    ) {
        Text(title, color = p.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        lines.forEach { line ->
            Row(Modifier.padding(vertical = 3.dp)) {
                Box(
                    Modifier
                        .padding(top = 8.dp, end = 9.dp)
                        .size(4.5.dp)
                        .clip(CircleShape)
                        .background(p.primary.copy(alpha = 0.7f))
                )
                Text(line, color = p.dim, fontSize = 12.5.sp, lineHeight = 19.sp)
            }
        }
        if (links.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            links.forEach { (url, label) ->
                Row(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { Sys.openUrl(context, url) }
                        .padding(vertical = 5.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        color = p.primary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.width(5.dp))
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = p.primary, modifier = Modifier.size(13.dp))
                }
            }
        }
    }
}
