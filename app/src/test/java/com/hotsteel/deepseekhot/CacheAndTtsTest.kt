// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Hotsteel2901

package com.hotsteel.deepseekhot

import com.hotsteel.deepseekhot.audio.TtsTextPrep
import com.hotsteel.deepseekhot.data.AppSettings
import com.hotsteel.deepseekhot.data.Block
import com.hotsteel.deepseekhot.data.ChatMsg
import com.hotsteel.deepseekhot.data.Citation
import com.hotsteel.deepseekhot.data.Conversation
import com.hotsteel.deepseekhot.data.Reply
import com.hotsteel.deepseekhot.data.Usage
import com.hotsteel.deepseekhot.net.Prompts
import com.hotsteel.deepseekhot.util.MarkdownExporter
import com.hotsteel.deepseekhot.util.Pricing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheAndTtsTest {

    @Test
    fun systemPromptPrefixStaysByteIdenticalWhenCacheOptimizationEnabled() {
        val settings = AppSettings(includeMeta = true, cacheOptimization = true)
        val conv = Conversation(createdAt = 1_764_000_000_000L)
        val first = Prompts.system(settings, conv)
        val second = Prompts.system(settings, conv)
        assertEquals(first, second)
        // Must NOT contain minute-level HH:mm clock which would bust KV cache every minute
        assertFalse(Regex("\\d{2}:\\d{2}").containsMatchIn(first))
    }

    @Test
    fun ttsTextPrepCleansCodeAndSplitsByBilledChars() {
        assertEquals(6, TtsTextPrep.billedLength("你好ab"))
        val raw = "第一句话。\n```kotlin\nfun main() {}\n```\n第二句话！https://example.com/a/b"
        val cleaned = TtsTextPrep.cleanForSpeech(raw)
        assertFalse(cleaned.contains("fun main"))
        assertFalse(cleaned.contains("https://"))
        assertTrue(cleaned.contains("第一句话"))
        assertTrue(cleaned.contains("第二句话"))

        val longText = "这是一段用于测试流式语音分块的长文本。".repeat(80)
        val chunks = TtsTextPrep.splitChunks(longText, maxBilled = 500)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { TtsTextPrep.billedLength(it) <= 500 })
    }

    @Test
    fun pricingCalculatesCacheSavingsAccurately() {
        val usage = Usage(input = 100_000, output = 1_000, cached = 80_000)
        val saved = Pricing.savedByCache("deepseek-flash", usage, at = 1_764_000_000_000L)
        assertTrue(saved > 0.0)
        assertEquals(0.8f, usage.cacheHitRate, 0.001f)
    }

    @Test
    fun markdownExporterIncludesThinkingAndCitations() {
        val conv = Conversation(
            title = "测试对话",
            messages = listOf(
                ChatMsg(role = "user", text = "你好"),
                ChatMsg(
                    role = "assistant",
                    replies = listOf(
                        Reply(
                            model = "deepseek-flash",
                            blocks = listOf(
                                Block.Thinking(text = "分析问题"),
                                Block.Text(
                                    text = "这是回答正文",
                                    citations = listOf(Citation("https://deepseek.com", "DeepSeek")),
                                ),
                            ),
                        )
                    ),
                ),
            ),
        )
        val md = MarkdownExporter.exportConversation(conv)
        assertTrue(md.contains("# 测试对话"))
        assertTrue(md.contains("<summary>深度思考过程</summary>"))
        assertTrue(md.contains("[DeepSeek](https://deepseek.com)"))
    }
}
