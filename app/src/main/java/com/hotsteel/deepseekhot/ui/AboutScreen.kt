// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Hotsteel2901

package com.hotsteel.deepseekhot.ui

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hotsteel.deepseekhot.ui.theme.LocalPalette
import com.hotsteel.deepseekhot.util.Sys

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current

    Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
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
            Text("关于", color = p.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .size(78.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Brush.linearGradient(listOf(p.primary, p.accent))),
                contentAlignment = Alignment.Center,
            ) {
                Text("D", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(14.dp))
            Text("DeepSeek Hot", color = p.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
            Text("v1.1.0 · Android · Kotlin / Compose", color = p.faint, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))

            Spacer(Modifier.height(18.dp))
            Text(
                text = "为 DeepSeek 全家桶应用 API 打造的第三方原生客户端。联网搜索、深度思考搜索、文件解析、阅读链接、语音输入与朗读，三种接口格式随意切换。",
                color = p.dim,
                fontSize = 12.5.sp,
                lineHeight = 20.sp,
            )

            Spacer(Modifier.height(22.dp))
            AuthorCard(
                onGithub = { Sys.openUrl(context, "https://github.com/Hotsteel2901") },
                onMail = { Sys.openUrl(context, "mailto:i.r.hotsteel@gmail.com") },
                onCopyGithub = { Sys.copy(context, "Hotsteel2901", "GitHub 用户名已复制") },
                onCopyMail = { Sys.copy(context, "i.r.hotsteel@gmail.com", "邮箱已复制") },
            )

            Spacer(Modifier.height(20.dp))
            InfoCard(
                title = "能力清单",
                lines = listOf(
                    "联网搜索与深度思考搜索（deepseek_web_search）",
                    "阅读链接（deepseek_read_url）/ 文件解析（PDF、Office、图片）",
                    "Chat Completions / Responses / Anthropic Messages 自由切换",
                    "自动获取模型列表，可调思考强度（none / low / high / max）",
                    "实时余额、Token 用量、首字延迟与生成速度",
                    "语音输入（ASR）与朗读回答（TTS）",
                    "会话分支、编辑重发、续写、导入导出",
                ),
            )

            Spacer(Modifier.height(12.dp))
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

            Spacer(Modifier.height(16.dp))
            Text(
                text = "本应用与深度求索（DeepSeek）无隶属关系，API Key 仅保存在本机。生成内容由 AI 产生，请自行甄别并遵守所在地法律法规与开放平台服务协议。",
                color = p.faint,
                fontSize = 10.5.sp,
                lineHeight = 16.sp,
            )
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
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(p.surface.copy(alpha = 0.75f))
            .border(1.dp, p.border, RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(p.primary.copy(alpha = 0.9f), p.accent.copy(alpha = 0.8f)))),
                contentAlignment = Alignment.Center,
            ) {
                Text("H", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Hotsteel2901", color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("独立开发者 · ROM / Android", color = p.faint, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        LinkRow(
            icon = Icons.Rounded.OpenInNew,
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    onCopy: () -> Unit,
) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(p.surfaceAlt)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = p.primary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(9.dp))
        Text(
            label,
            color = p.text,
            fontSize = 12.5.sp,
            modifier = Modifier.weight(1f),
        )
        Tappable(
            Modifier
                .size(28.dp)
                .clip(CircleShape),
            onClick = onCopy,
        ) {
            Icon(
                Icons.Rounded.ContentCopy,
                "复制",
                tint = p.faint,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(14.dp),
            )
        }
        Spacer(Modifier.width(2.dp))
        Text(
            "打开",
            color = p.primary,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
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
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(p.surface.copy(alpha = 0.6f))
            .border(1.dp, p.border, RoundedCornerShape(18.dp))
            .padding(15.dp),
    ) {
        Text(title, color = p.text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(9.dp))
        lines.forEach { line ->
            Row(Modifier.padding(vertical = 3.dp)) {
                Box(
                    Modifier
                        .padding(top = 7.dp, end = 8.dp)
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(p.primary.copy(alpha = 0.7f))
                )
                Text(line, color = p.dim, fontSize = 11.5.sp, lineHeight = 17.sp)
            }
        }
        links.forEach { (url, label) ->
            Text(
                text = "$label →",
                color = p.primary,
                fontSize = 11.5.sp,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clickable { Sys.openUrl(context, url) },
            )
        }
    }
}
