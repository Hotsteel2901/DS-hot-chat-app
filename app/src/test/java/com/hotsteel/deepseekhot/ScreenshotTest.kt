// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Hotsteel2901

package com.hotsteel.deepseekhot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.hotsteel.deepseekhot.data.Block
import com.hotsteel.deepseekhot.data.ChatMsg
import com.hotsteel.deepseekhot.data.Citation
import com.hotsteel.deepseekhot.data.Source
import com.hotsteel.deepseekhot.data.Usage
import com.hotsteel.deepseekhot.ui.AssistantMessageItem
import com.hotsteel.deepseekhot.ui.AuroraBackground
import com.hotsteel.deepseekhot.ui.UserMessageItem
import com.hotsteel.deepseekhot.ui.theme.DeepSeekHotTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@org.junit.Ignore("视觉校验用：需要时移除本注解，运行后截图输出到 app/build/screenshots/")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {

    @Test
    fun welcome() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        settle(activity.window.decorView)
        capture(activity.window.decorView, "preview_welcome.png")
    }

    @Test
    fun messages() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val view = ComposeView(activity).apply {
            setContent {
                DeepSeekHotTheme(dark = true, dynamic = false) {
                    AuroraBackground(Modifier.fillMaxSize()) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        ) {
                            SampleThread()
                        }
                    }
                }
            }
        }
        activity.setContentView(view)
        settle(view, height = 3500)
        capture(view, "preview_messages_full.png")
    }

    @Test
    fun settings() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val vm = AppViewModel(activity.application)
        val view = ComposeView(activity).apply {
            setContent {
                DeepSeekHotTheme(dark = true, dynamic = false) {
                    AuroraBackground(Modifier.fillMaxSize()) {
                        com.hotsteel.deepseekhot.ui.SettingsScreen(
                            vm = vm,
                            onBack = {},
                            onAbout = {},
                            onModels = {},
                            onUsage = {},
                        )
                    }
                }
            }
        }
        activity.setContentView(view)
        settle(view, height = 3400)
        capture(view, "preview_settings.png")
    }

    private fun settle(view: View, height: Int = 0) {
        repeat(8) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(30)
        }
        val w = if (view.width > 0) view.width else 1233
        val h = if (height > 0) height else if (view.height > 0) view.height else 2673
        view.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, w, h)
        repeat(4) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(30)
        }
    }

    private fun capture(view: View, name: String) {
        val w = view.width.coerceAtLeast(1)
        val h = view.height.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.BLACK)
        view.draw(canvas)
        File("/workspace/$name").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}

@Composable
private fun SampleThread() {
    val now = System.currentTimeMillis()
    UserMessageItem(
        msg = ChatMsg(role = "user", text = "帮我看看 DeepSeek V4.1 有什么更新，并用表格对比一下 Flash 和 Pro 的价格。", createdAt = now - 60_000),
        onEdit = {},
    )
    AssistantMessageItem(
        msg = ChatMsg(role = "assistant"),
        blocks = listOf(
            Block.Thinking(
                text = "用户想了解 V4.1 的更新，并需要一张价格对比表。先确认官方文档里 Flash 与 Pro 的差异，再把价格整理成表格。",
                startedAt = now - 40_000,
                endedAt = now - 30_000,
            ),
            Block.Tool(
                id = "t1",
                name = "deepseek_web_search",
                kind = "search",
                action = "search",
                queries = listOf("DeepSeek V4.1 更新", "DeepSeek API 价格"),
                sources = listOf(
                    Source(
                        title = "DeepSeek 发布全家桶应用 API",
                        url = "https://api-docs.deepseek.com/news/news261113/",
                        siteName = "DeepSeek",
                        snippet = "今天，我们正式开放这套全家桶应用 API……",
                    ),
                    Source(
                        title = "模型 & 价格 - DeepSeek API Docs",
                        url = "https://api-docs.deepseek.com/zh-cn/quick_start/pricing",
                        siteName = "api-docs.deepseek.com",
                        snippet = "deepseek-flash 支持非思考与思考模式，上下文长度 1M……",
                    ),
                ),
                status = "completed",
            ),
            Block.Text(
                text = "## 主要更新\n\n- 开放**全家桶应用 API**：联网搜索、文件解析、阅读链接均可通过参数开启\n- 新增 `deepseek_web_search` 与 `deepseek_read_url` 两个服务端工具\n- 支持思考模式下的深度搜索，最多约 20 次工具调用\n\n### 价格对比\n\n| 项目 | Flash | V4 Pro |\n| --- | --- | --- |\n| 上下文 | 1M | 1M |\n| 输入（未命中） | ¥1 / 2 | ¥4.5 / 9 |\n| 输出 | ¥4 / 8 | ¥13.5 / 27 |\n\n> 以上为空闲 / 高峰时段单价，单位为元每百万 token。\n\n```kotlin\nval client = OkHttpClient.Builder()\n    .readTimeout(0, TimeUnit.MILLISECONDS)\n    .build()\n```\n\n整体来看，Flash 更适合日常对话与长文档，Pro 更适合复杂推理。",
                citations = listOf(
                    Citation("https://api-docs.deepseek.com/news/news261113/", "DeepSeek 发布全家桶应用 API"),
                    Citation("https://api-docs.deepseek.com/zh-cn/quick_start/pricing", "模型 & 价格"),
                ),
            ),
        ),
        status = "done",
        usage = Usage(input = 9078, output = 1686, cached = 4096, total = 10764, webSearch = 1, cost = 0.024),
        error = null,
        stopReason = "stop",
        ttftMs = 820,
        latencyMs = 9400,
        model = "deepseek-flash",
        streaming = false,
        isLast = true,
        versions = 2,
        activeVersion = 1,
        speaking = false,
        onCopy = {},
        onRegenerate = {},
        onContinue = {},
        onSpeak = {},
        onSwitchVersion = {},
    )
}
