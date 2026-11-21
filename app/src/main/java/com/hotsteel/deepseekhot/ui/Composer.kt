// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Hotsteel2901

package com.hotsteel.deepseekhot.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.hotsteel.deepseekhot.audio.VoiceRecorder
import com.hotsteel.deepseekhot.data.Attachment
import com.hotsteel.deepseekhot.ui.theme.LocalPalette
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.sin

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
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { VoiceRecorder(context) }
    var field by remember { mutableStateOf(TextFieldValue(text)) }
    var attachMenu by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) showPermissionDialog = true
    }

    LaunchedEffect(text) {
        if (text != field.text) {
            field = TextFieldValue(text, androidx.compose.ui.text.TextRange(text.length))
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
        scope.launch {
            val file = recorder.stop()
            if (file != null) onTranscribe(file)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.navigationBars
                    .union(WindowInsets.ime)
                    .only(WindowInsetsSides.Bottom)
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
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                attachments.take(4).forEach { att ->
                    AttachmentChip(att) { onRemove(att.id) }
                }
                if (attachments.size > 4) {
                    Text("+${attachments.size - 4}", color = p.dim, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                }
            }
        }

        AnimatedVisibility(
            visible = recorder.recording,
            enter = expandVertically(tween(180)) + fadeIn(tween(140)),
            exit = shrinkVertically(tween(140)) + fadeOut(tween(100)),
        ) {
            RecordingPanel(recorder.amplitude, recorder.elapsedMs)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(p.surface.copy(alpha = 0.94f))
                .border(1.dp, p.border, RoundedCornerShape(26.dp))
                .padding(start = 4.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (allowAttach) Box {
                Tappable(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape),
                    onClick = { attachMenu = true },
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = "添加附件",
                        tint = p.dim,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(22.dp),
                    )
                }
                DropdownMenu(
                    expanded = attachMenu,
                    onDismissRequest = { attachMenu = false },
                    containerColor = p.surfaceHigh,
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

            BasicTextField(
                value = field,
                onValueChange = {
                    field = it
                    onTextChange(it.text)
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 38.dp, max = 148.dp)
                    .padding(horizontal = 6.dp, vertical = 9.dp),
                textStyle = TextStyle(color = p.text, fontSize = 15.5.sp, lineHeight = 22.sp),
                cursorBrush = SolidColor(p.primary),
                maxLines = 6,
                decorationBox = { inner ->
                    Box {
                        if (field.text.isEmpty()) {
                            Text(
                                text = if (asrBusy) "正在识别语音…" else "给 DeepSeek 发消息…",
                                color = p.faint,
                                fontSize = 15.5.sp,
                                modifier = Modifier.padding(top = 1.dp),
                            )
                        }
                        inner()
                    }
                },
            )

            if (field.text.isEmpty()) {
                if (allowVoice) {
                    MicButton(
                        recording = recorder.recording,
                        enabled = !streaming,
                        asrBusy = asrBusy,
                        onPress = { beginRecording() },
                        onRelease = { endRecording() },
                    )
                }
            }

            val canSend = !streaming && (field.text.isNotBlank() || attachments.any { it.status == "ready" })
            SendButton(
                streaming = streaming,
                enabled = canSend,
                onClick = {
                    if (streaming) onStop() else {
                        field = TextFieldValue("")
                        onSend()
                    }
                },
            )
        }
        Text(
            text = when {
                uploading > 0 -> "正在上传 $uploading 个文件…"
                asrBusy -> "语音识别中…"
                else -> "内容由 AI 生成，请自行甄别"
            },
            color = p.faint,
            fontSize = 10.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("需要麦克风权限") },
            text = { Text("语音输入需要录音权限，仅用于将你的语音发送到 DeepSeek 语音识别接口。") },
            confirmButton = {
                TextButton(onClick = { showPermissionDialog = false }) { Text("知道了") }
            },
            containerColor = p.surfaceHigh,
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
    val scale by animateFloatAsState(if (recording) 1.15f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "mic")
    Box(
        Modifier
            .padding(end = 2.dp)
            .size(38.dp)
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
                initialValue = 0.7f,
                targetValue = 1.5f,
                animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
                label = "p",
            )
            Box(
                Modifier
                    .size(38.dp * pulse)
                    .clip(CircleShape)
                    .background(p.danger.copy(alpha = 0.22f))
            )
        }
        Icon(
            Icons.Rounded.Mic,
            contentDescription = "语音输入",
            tint = when {
                recording -> p.danger
                asrBusy -> p.primary
                enabled -> p.dim
                else -> p.faint.copy(alpha = 0.5f)
            },
            modifier = Modifier
                .size(21.dp)
                .then(
                    Modifier.androidxScale(scale)
                ),
        )
    }
}

private fun Modifier.androidxScale(s: Float): Modifier =
    this.then(Modifier.graphicsLayer { scaleX = s; scaleY = s })

@Composable
private fun SendButton(streaming: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val active = streaming || enabled
    val scale by animateFloatAsState(if (active) 1f else 0.92f, spring(Spring.DampingRatioMediumBouncy), label = "send")

    Box(
        Modifier
            .padding(start = 2.dp)
            .size(40.dp)
            .androidxScale(scale)
            .clip(CircleShape)
            .background(
                if (active) Brush.linearGradient(listOf(p.primary, p.accent.copy(alpha = 0.85f)))
                else Brush.linearGradient(listOf(p.surfaceHigh, p.surfaceHigh))
            )
            .then(if (active) Modifier else Modifier.border(1.dp, p.border, CircleShape))
            .pointerInput(active) {
                if (active) detectTapGestures { onClick() }
            },
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
            Icon(
                if (isStreaming) Icons.Rounded.Stop else Icons.Rounded.ArrowUpward,
                contentDescription = if (isStreaming) "停止生成" else "发送",
                tint = if (active) Color.White else p.faint,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun AttachmentChip(att: Attachment, onRemove: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(p.surfaceAlt)
            .border(1.dp, if (att.status == "failed") p.danger.copy(alpha = 0.4f) else p.border, RoundedCornerShape(12.dp))
            .padding(start = 8.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(p.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                att.name.substringAfterLast('.', "file").take(3).uppercase(),
                color = p.primary,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(7.dp))
        Column(Modifier.widthIn(max = 120.dp)) {
            Text(
                att.name,
                color = p.text,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when (att.status) {
                    "uploading" -> "上传解析中…"
                    "failed" -> att.error?.take(20) ?: "上传失败"
                    "ready" -> com.hotsteel.deepseekhot.util.Fmt.bytes(att.size)
                    else -> "待上传"
                },
                color = if (att.status == "failed") p.danger else p.faint,
                fontSize = 9.5.sp,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(4.dp))
        Tappable(
            Modifier
                .size(22.dp)
                .clip(CircleShape),
            onClick = onRemove,
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "移除",
                tint = p.faint,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(13.dp),
            )
        }
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
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(p.danger.copy(alpha = 0.10f))
            .border(1.dp, p.danger.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
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
        Text("正在录音", color = p.danger, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(10.dp))
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(24) { i ->
                val wave = (sin(phase + i * 0.55f) * 0.5f + 0.5f)
                val h = (4 + wave * 22 * (0.25f + amplitude)).dp
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
            fontSize = 11.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        )
    }
}
