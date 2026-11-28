// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Hotsteel2901

package com.hotsteel.deepseekhot.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.hotsteel.deepseekhot.audio.VoiceRecorder
import com.hotsteel.deepseekhot.data.Attachment
import com.hotsteel.deepseekhot.ui.liquid.LocalAmbientBackdrop
import com.hotsteel.deepseekhot.ui.liquid.liquidGlass
import com.hotsteel.deepseekhot.ui.theme.LocalPalette
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.sin

/**
 * 输入区，版式对齐官方网页端：
 * 一个大圆角输入卡片，上半部分是多行文本框，下半部分是工具栏——
 * 左侧「深度思考」「联网搜索」两个胶囊开关，右侧附件、语音与发送按钮。
 */
@Composable
fun Composer(
    text: String,
    onTextChange: (String) -> Unit,
    attachments: List<Attachment>,
    onRemove: (String) -> Unit,
    onPickFile: () -> Unit,
    onPickImage: () -> Unit,
    streaming: Boolean,
    uploading: Int,
    asrBusy: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onTranscribe: (File) -> Unit,
    allowAttach: Boolean = true,
    allowVoice: Boolean = true,
    thinkActive: Boolean = true,
    thinkLabel: String = "深度思考",
    onToggleThink: () -> Unit = {},
    webActive: Boolean = true,
    onToggleWeb: () -> Unit = {},
    presetBadge: String? = null,
    onOpenPresets: (() -> Unit)? = null,
    onHint: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { VoiceRecorder(context) }
    DisposableEffect(recorder) {
        onDispose { recorder.cancel() }
    }
    var field by remember { mutableStateOf(TextFieldValue(text)) }
    var attachMenu by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onHint("已获得麦克风权限，按住麦克风说话")
        else showPermissionDialog = true
    }

    LaunchedEffect(text) {
        if (text != field.text) {
            field = TextFieldValue(text, TextRange(text.length))
        }
    }

    fun beginRecording() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        recorder.start(scope)
    }

    fun endRecording() {
        if (!recorder.recording) return
        // 轻点一下就松开时录到的只是噪声，直接丢弃并提示用法，而不是把空音频送去识别
        val tooShort = recorder.elapsedMs < 700
        scope.launch {
            val file = recorder.stop()
            when {
                tooShort -> {
                    file?.delete()
                    onHint("按住麦克风说话，松开后自动识别")
                }
                file != null -> onTranscribe(file)
            }
        }
    }

    val canSend = !streaming && (field.text.isNotBlank() || attachments.any { it.status == "ready" })

    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.navigationBars
                    .union(WindowInsets.ime)
                    .only(WindowInsetsSides.Bottom)
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedVisibility(
            visible = recorder.recording,
            enter = expandVertically(tween(180)) + fadeIn(tween(140)),
            exit = shrinkVertically(tween(140)) + fadeOut(tween(100)),
        ) {
            RecordingPanel(recorder.amplitude, recorder.elapsedMs)
        }

        SlashCommandBar(
            visible = field.text.startsWith("/") && !field.text.contains("\n") && field.text.length <= 12,
            filter = field.text.trim(),
            onPick = { cmd ->
                field = TextFieldValue(cmd.template, TextRange(cmd.template.length))
                onTextChange(cmd.template)
            },
        )

        Column(
            Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .liquidGlass(
                    backdrop = LocalAmbientBackdrop.current,
                    cornerRadius = 26.dp,
                    blurRadius = 16.dp,
                    refractionHeight = 14.dp,
                    refractionAmount = 24.dp,
                    chromaticAberration = true,
                    surfaceAlpha = if (p.isDark) 0.54f else 0.68f,
                ),
        ) {
            AnimatedVisibility(
                visible = attachments.isNotEmpty(),
                enter = expandVertically(tween(200)) + fadeIn(tween(160)),
                exit = shrinkVertically(tween(160)) + fadeOut(tween(120)),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 12.dp, end = 12.dp, top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    attachments.forEach { att ->
                        AttachmentChip(att) { onRemove(att.id) }
                    }
                }
            }

            BasicTextField(
                value = field,
                onValueChange = {
                    field = it
                    onTextChange(it.text)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp, max = 168.dp)
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
                textStyle = TextStyle(color = p.text, fontSize = 16.sp, lineHeight = 23.sp),
                cursorBrush = SolidColor(p.primary),
                maxLines = 7,
                decorationBox = { inner ->
                    Box {
                        if (field.text.isEmpty()) {
                            Text(
                                text = when {
                                    recorder.recording -> "正在聆听…"
                                    asrBusy -> "正在识别语音…"
                                    else -> "给 DeepSeek 发送消息"
                                },
                                color = p.faint,
                                fontSize = 16.sp,
                                lineHeight = 23.sp,
                            )
                        }
                        inner()
                    }
                },
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 8.dp, bottom = 8.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PillToggle(thinkLabel, Icons.Rounded.Psychology, thinkActive, onToggleThink)
                    PillToggle("联网搜索", Icons.Rounded.Language, webActive, onToggleWeb)
                    if (onOpenPresets != null && !presetBadge.isNullOrBlank()) {
                        PillToggle(
                            text = presetBadge,
                            icon = Icons.Rounded.AutoAwesome,
                            active = presetBadge != "默认",
                            onClick = onOpenPresets,
                        )
                    }
                }
                Spacer(Modifier.width(4.dp))

                if (allowAttach) Box {
                    IconTap(
                        Icons.Rounded.AttachFile,
                        "添加附件",
                        onClick = { attachMenu = true },
                        tint = p.dim,
                        iconSize = 21.dp,
                    )
                    DropdownMenu(
                        expanded = attachMenu,
                        onDismissRequest = { attachMenu = false },
                        containerColor = p.surface,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        DropdownMenuItem(
                            text = { Text("上传文件") },
                            leadingIcon = { Icon(Icons.Rounded.AttachFile, null, tint = p.dim) },
                            onClick = {
                                attachMenu = false
                                onPickFile()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("上传图片") },
                            leadingIcon = { Icon(Icons.Rounded.Image, null, tint = p.dim) },
                            onClick = {
                                attachMenu = false
                                onPickImage()
                            },
                        )
                    }
                }

                if (allowVoice && field.text.isEmpty() && !streaming) {
                    MicButton(
                        recording = recorder.recording,
                        enabled = !streaming,
                        asrBusy = asrBusy,
                        onPress = { beginRecording() },
                        onRelease = { endRecording() },
                    )
                }

                Spacer(Modifier.width(4.dp))
                SendButton(
                    streaming = streaming,
                    enabled = canSend,
                    onClick = {
                        if (streaming) onStop() else if (canSend) {
                            field = TextFieldValue("")
                            onSend()
                        }
                    },
                )
            }
        }

        Text(
            text = when {
                uploading > 0 -> "正在上传 $uploading 个文件…"
                asrBusy -> "语音识别中…"
                field.text.length > 40 -> "约 ${(field.text.length / 2.2).toInt()} tokens · 输入 / 呼出快捷指令"
                else -> "内容由 AI 生成，请仔细甄别 · 输入 / 可用快捷指令"
            },
            color = p.faint,
            fontSize = 11.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            textAlign = TextAlign.Center,
        )
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            containerColor = p.surface,
            shape = RoundedCornerShape(20.dp),
            title = { Text("需要麦克风权限", color = p.text, fontWeight = FontWeight.SemiBold) },
            text = { Text("语音输入需要录音权限，仅用于将你的语音发送到 DeepSeek 语音识别接口。", color = p.dim) },
            confirmButton = {
                TextButton(onClick = { showPermissionDialog = false }) { Text("知道了") }
            },
        )
    }
}

@Composable
private fun MicButton(
    recording: Boolean,
    enabled: Boolean,
    asrBusy: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
) {
    val p = LocalPalette.current
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .semantics {
                role = Role.Button
                contentDescription = "按住说话"
            }
            .pointerInput(enabled, asrBusy) {
                if (!enabled || asrBusy) return@pointerInput
                detectTapGestures(
                    onPress = {
                        onPress()
                        tryAwaitRelease()
                        onRelease()
                    }
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (recording) {
            val transition = rememberInfiniteTransition(label = "pulse")
            val pulse by transition.animateFloat(
                initialValue = 0.75f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
                label = "p",
            )
            Box(
                Modifier
                    .size(40.dp * pulse)
                    .clip(CircleShape)
                    .background(p.danger.copy(alpha = 0.16f))
            )
        }
        if (asrBusy) {
            TypingDots(size = 4.dp, color = p.primary)
        } else {
            Icon(
                Icons.Rounded.Mic,
                contentDescription = null,
                tint = when {
                    recording -> p.danger
                    enabled -> p.dim
                    else -> p.faint.copy(alpha = 0.5f)
                },
                modifier = Modifier.size(21.dp),
            )
        }
    }
}

@Composable
private fun SendButton(streaming: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val active = streaming || enabled
    val bg by animateColorAsState(
        if (active) p.primary else p.surfaceHigh,
        tween(160),
        label = "send-bg",
    )
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(
                enabled = active,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = streaming,
            transitionSpec = {
                (fadeIn(tween(120)) + scaleIn(initialScale = 0.7f)) togetherWith
                    (fadeOut(tween(120)) + scaleOut(targetScale = 0.7f))
            },
            label = "send-icon",
        ) { isStreaming ->
            if (isStreaming) {
                // 停止：白色圆角方块，与官方网页端一致
                Box(
                    Modifier
                        .semantics { contentDescription = "停止生成" }
                        .size(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White)
                )
            } else {
                Icon(
                    Icons.Rounded.ArrowUpward,
                    contentDescription = "发送",
                    tint = if (active) Color.White else p.faint,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun AttachmentChip(att: Attachment, onRemove: () -> Unit) {
    val p = LocalPalette.current
    val failed = att.status == "failed"
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (failed) p.danger.copy(alpha = 0.08f) else p.surfaceAlt)
            .padding(start = 8.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(p.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            if (att.status == "uploading") {
                TypingDots(size = 3.dp, color = p.primary)
            } else {
                Text(
                    att.name.substringAfterLast('.', "file").take(4).uppercase(),
                    color = p.primary,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.widthIn(max = 140.dp)) {
            Text(
                att.name,
                color = p.text,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when (att.status) {
                    "uploading" -> "上传解析中…"
                    "failed" -> att.error?.take(24) ?: "上传失败"
                    "ready" -> com.hotsteel.deepseekhot.util.Fmt.bytes(att.size)
                    else -> "待上传"
                },
                color = if (failed) p.danger else p.faint,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconTap(Icons.Rounded.Close, "移除附件", onRemove, tint = p.faint, size = 32.dp, iconSize = 15.dp)
    }
}

@Composable
private fun RecordingPanel(amplitude: Float, elapsedMs: Long) {
    val p = LocalPalette.current
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.2832f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "phase",
    )
    Row(
        Modifier
            .widthIn(max = 760.dp)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(p.danger.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(p.danger)
        )
        Spacer(Modifier.width(8.dp))
        Text("松开结束", color = p.danger, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(12.dp))
        Row(
            Modifier.weight(1f).height(26.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(24) { i ->
                val wave = (sin(phase + i * 0.55f) * 0.5f + 0.5f)
                val h = (4 + wave * 20 * (0.25f + amplitude)).coerceAtMost(26f).dp
                Box(
                    Modifier
                        .width(2.5.dp)
                        .height(h)
                        .clip(RoundedCornerShape(2.dp))
                        .background(p.danger.copy(alpha = 0.35f + 0.5f * wave))
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = "%d:%02d".format(elapsedMs / 1000 / 60, (elapsedMs / 1000) % 60),
            color = p.danger,
            fontSize = 12.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        )
    }
}
