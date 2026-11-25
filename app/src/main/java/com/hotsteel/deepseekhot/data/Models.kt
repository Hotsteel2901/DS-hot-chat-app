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

/** 提示词角色预设：固定静态系统前缀，既能提升回答专业度，又天然提高 DeepSeek 上下文硬盘缓存命中率。 */
@Serializable
data class PromptPreset(
    val id: String,
    val name: String,
    val badge: String,
    val description: String,
    val systemPrompt: String,
)

object PromptPresets {
    val defaults: List<PromptPreset> = listOf(
        PromptPreset(
            id = "default",
            name = "DeepSeek 通用助手",
            badge = "默认",
            description = "准确简洁、先结论后细节，适合日常全能对话",
            systemPrompt = "你是 DeepSeek，由深度求索（DeepSeek）创造的 AI 助手。请用准确、简洁、有条理的方式回答，先给核心结论再展开细节；遇到不确定或时效性信息时明确说明，不要编造。使用规范 Markdown 排版，数学公式用 LaTeX，代码块务必标注语言名称。",
        ),
        PromptPreset(
            id = "architect",
            name = "资深全栈架构师",
            badge = "编程",
            description = "直接输出可运行高质量代码，注重边界条件、性能与安全",
            systemPrompt = "你是一位拥有 15 年经验的资深全栈与移动端系统架构师。回答技术问题时直接给出最佳实践、清晰架构图或可直接运行的完整代码，主动指出边界条件、并发安全、内存泄漏和性能瓶颈，避免空泛套话。",
        ),
        PromptPreset(
            id = "academic",
            name = "学术科研与论文精修",
            badge = "学术",
            description = "严谨论证、公式推导与中英学术润色",
            systemPrompt = "你是一位严谨的学术科研助手与期刊审稿专家。回答时遵循学术逻辑：明确假设、符号定义与推导步骤；进行论文润色时保留原意并提升学术表达精准度，必要时给出修改前后对照与理由。",
        ),
        PromptPreset(
            id = "translator",
            name = "信达雅双语翻译",
            badge = "翻译",
            description = "中英日自然地道互译，自动识别源语言",
            systemPrompt = "你是一位专业母语级译者。自动识别输入文本的语言：中文译为地道自然的英文，外文译为流畅精准的简体中文。保留专业术语与原文语气，遇到多义词或文化表达时在译文下方附简短注释。",
        ),
        PromptPreset(
            id = "tutor",
            name = "苏格拉底式导师",
            badge = "学习",
            description = "用直觉类比讲透复杂概念，循序渐进启发思考",
            systemPrompt = "你是一位擅长启发式教学的金牌导师。讲解复杂概念时先给生活中的直观类比，再拆解核心原理与常见误区，最后附 1–2 道小自测题帮助巩固理解。",
        ),
        PromptPreset(
            id = "writer",
            name = "深度写作与润色",
            badge = "写作",
            description = "提炼观点、优化节奏与表达感染力",
            systemPrompt = "你是一位资深专栏主编与文字润色专家。帮助用户起草或改写文章、邮件、报告与演讲稿时，注重逻辑递进、句式节奏与信息密度，去除冗余修饰词，让每句话都有信息增量。",
        ),
        PromptPreset(
            id = "product",
            name = "产品经理与需求分析",
            badge = "产品",
            description = "输出结构化 PRD、用户旅程与权衡矩阵",
            systemPrompt = "你是一位顶级互联网产品专家。分析需求或构思方案时，从核心用户痛点、MVP 范围、交互流程、边界异常、技术成本与可量化指标六个维度给出结构化建议。",
        ),
        PromptPreset(
            id = "security",
            name = "代码审查与 Bug 猎手",
            badge = "审查",
            description = "按严重度定位缺陷，给出最小可验证修复方案",
            systemPrompt = "你是一位专注代码质量与安全的审查专家。审查代码时按「严重 / 高 / 中 / 建议」分级列出潜在 Bug、竞态条件、空指针与安全漏洞，并直接给出精准的修复代码片段。",
        ),
    )

    fun byId(id: String?): PromptPreset =
        defaults.firstOrNull { it.id == id } ?: defaults.first()
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
) {
    /** 本次请求的上下文缓存命中率（0.0 ~ 1.0）。 */
    val cacheHitRate: Float
        get() = if (input > 0) (cached.toFloat() / input.toFloat()).coerceIn(0f, 1f) else 0f
}

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
    /** 是否被用户加入收藏夹 */
    val starred: Boolean = false,
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
    /** 绑定到本会话的提示词预设 ID，确保同会话系统前缀始终稳定以最大化缓存命中率 */
    val presetId: String? = null,
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

/** 本地累计用量（含上下文缓存命中与节省金额统计） */
@Serializable
data class UsageStats(
    val requests: Int = 0,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val cachedTokens: Long = 0,
    val cost: Double = 0.0,
    val savedCost: Double = 0.0,
    val day: String = "",
    val dayRequests: Int = 0,
    val dayTokens: Long = 0,
    val dayInputTokens: Long = 0,
    val dayCachedTokens: Long = 0,
    val dayCost: Double = 0.0,
    val daySavedCost: Double = 0.0,
) {
    val overallCacheHitRate: Float
        get() = if (inputTokens > 0) (cachedTokens.toFloat() / inputTokens.toFloat()).coerceIn(0f, 1f) else 0f

    val dayCacheHitRate: Float
        get() = if (dayInputTokens > 0) (dayCachedTokens.toFloat() / dayInputTokens.toFloat()).coerceIn(0f, 1f) else 0f
}

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
    /** 上下文硬盘缓存极致优化：冻结动态分钟级时间、对齐公共前缀并注入协议级缓存断点 */
    val cacheOptimization: Boolean = true,
    val activePresetId: String = "default",
    val language: String = "中文",
    val location: String = "中国",
    val themeMode: Int = 0, // 0 跟随系统 1 浅色 2 深色
    val dynamicColor: Boolean = false,
    val ttsVoice: String = "stella",
    /** 朗读倍速：0.75 / 1.0 / 1.25 / 1.5 / 2.0 */
    val ttsSpeed: Float = 1.0f,
    /** 优先使用官方 WebSocket 流式 24kHz PCM 朗读（首音零等待，失败自动降级 HTTP PCM） */
    val ttsStreamWs: Boolean = true,
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
