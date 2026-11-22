// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Hotsteel2901

package com.hotsteel.deepseekhot.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/** 协议格式：Chat Completions / Responses / Anthropic Messages */
enum class Protocol(val key: String, val label: String, val short: String) {
    CHAT("chat", "Chat Completions", "Chat"),
    RESPONSES("responses", "Responses", "Responses"),
    ANTHROPIC("anthropic", "Anthropic Messages", "Messages");

    companion object {
        fun of(key: String?): Protocol = entries.firstOrNull { it.key == key } ?: ANTHROPIC
    }
}

@Serializable
data class Citation(
    val url: String,
    val title: String? = null,
    val citedText: String? = null,
)

@Serializable
data class Source(
    val title: String? = null,
    val url: String? = null,
    val siteName: String? = null,
    val siteIcon: String? = null,
    val snippet: String? = null,
    val publishedAt: String? = null,
)

/** 回答中的一个内容块。思考、正文、服务端工具调用都独立成块。 */
@Serializable
sealed class Block {

    @Serializable
    @SerialName("text")
    data class Text(
        val text: String = "",
        val citations: List<Citation> = emptyList(),
    ) : Block()

    @Serializable
    @SerialName("thinking")
    data class Thinking(
        val text: String = "",
        val startedAt: Long = 0L,
        val endedAt: Long? = null,
    ) : Block()

    @Serializable
    @SerialName("tool")
    data class Tool(
        val id: String = "",
        val name: String = "",
        /** search / deep_search / read_link / image_search / deep_image_search */
        val kind: String = "search",
        /** search / open_page / find_in_page / read_link */
        val action: String = "search",
        val queries: List<String> = emptyList(),
        val url: String? = null,
        val sources: List<Source> = emptyList(),
        val status: String = "running",
        val error: String? = null,
        /** 阅读链接成功时服务端返回的加密网页，多轮回传可还原页面内容 */
        val signature: String? = null,
    ) : Block()

    @Serializable
    @SerialName("refusal")
    data class Refusal(val text: String = "") : Block()
}

@Serializable
data class Usage(
    val input: Int = 0,
    val output: Int = 0,
    val cached: Int = 0,
    val reasoning: Int = 0,
    val total: Int = 0,
    val webSearch: Int = 0,
    val webFetch: Int = 0,
    val imageSearch: Int = 0,
    val cost: Double = 0.0,
)

@Serializable
data class Attachment(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val mime: String = "application/octet-stream",
    val size: Long = 0L,
    val localPath: String? = null,
    val fileId: String? = null,
    val isImage: Boolean = false,
    /** pending / uploading / ready / failed */
    val status: String = "pending",
    val error: String? = null,
)

/** 一次生成的结果，一条 assistant 消息可以保存多个版本（分支）。 */
@Serializable
data class Reply(
    val blocks: List<Block> = emptyList(),
    val usage: Usage? = null,
    /** streaming / done / stopped / error */
    val status: String = "done",
    val stopReason: String? = null,
    val error: String? = null,
    val model: String? = null,
    val protocol: String? = null,
    val ttftMs: Long = 0L,
    val latencyMs: Long = 0L,
    val createdAt: Long = 0L,
)

@Serializable
data class ChatMsg(
    val id: String = UUID.randomUUID().toString(),
    /** user / assistant */
    val role: String,
    val text: String = "",
    val attachments: List<Attachment> = emptyList(),
    val replies: List<Reply> = emptyList(),
    val active: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val reply: Reply? get() = replies.getOrNull(active)

    val plainText: String
        get() = if (role == "user") text
        else reply?.blocks?.filterIsInstance<Block.Text>()?.joinToString("") { it.text }.orEmpty()
}

@Serializable
data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val messages: List<ChatMsg> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val pinned: Boolean = false,
)

/** GET /models 返回的模型元数据 */
data class ModelInfo(
    val id: String,
    val name: String,
    val contextWindow: Int? = null,
    val maxOutput: Int? = null,
    val efforts: List<String> = emptyList(),
    val defaultEffort: String? = null,
    val imageInput: Boolean = false,
) {
    val displayName: String get() = name.ifBlank { id }
    val supportsEffort: Boolean get() = efforts.isNotEmpty()
}

data class BalanceInfo(
    val currency: String,
    val total: String,
    val granted: String,
    val toppedUp: String,
)

data class Balance(
    val available: Boolean,
    val infos: List<BalanceInfo>,
    val fetchedAt: Long = System.currentTimeMillis(),
)

/** 本地累计用量（只统计本机发起且成功返回 usage 的请求） */
@Serializable
data class UsageStats(
    val requests: Int = 0,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val cost: Double = 0.0,
    val day: String = "",
    val dayRequests: Int = 0,
    val dayTokens: Long = 0,
    val dayCost: Double = 0.0,
)

data class AppSettings(
    val apiKey: String = "",
    val baseUrl: String = "https://api.deepseek.com",
    val protocol: Protocol = Protocol.ANTHROPIC,
    val model: String = "deepseek-flash",
    val effort: String = "high",
    val webSearch: Boolean = true,
    val readUrl: Boolean = true,
    val safetyCheck: Boolean = true,
    val autoTitle: Boolean = true,
    val systemPrompt: String = "",
    val includeMeta: Boolean = true,
    val language: String = "中文",
    val location: String = "中国",
    val themeMode: Int = 0, // 0 跟随系统 1 浅色 2 深色
    val dynamicColor: Boolean = false,
    val ttsVoice: String = "stella",
    val autoRead: Boolean = false,
    val asrEnabled: Boolean = true,
    val haptics: Boolean = true,
    /** x-opencode-session：0 自动识别（按 Base URL） 1 始终发送 2 关闭 */
    val opencodeSession: Int = 0,
) {
    val normalizedBase: String get() = baseUrl.trim().trimEnd('/').ifBlank { "https://api.deepseek.com" }
    val thinking: Boolean get() = effort != "none"

    val opencodeSessionLabel: String
        get() = when (opencodeSession) {
            1 -> "始终发送"
            2 -> "关闭"
            else -> "自动识别"
        }
}
