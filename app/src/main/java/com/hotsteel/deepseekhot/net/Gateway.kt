// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Hotsteel2901

package com.hotsteel.deepseekhot.net

import com.hotsteel.deepseekhot.data.AppSettings
import com.hotsteel.deepseekhot.data.Protocol
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.UUID

/** OpenCode 的三条线路。v2 走新的 Console 网关，Go 是订阅制，Zen 是旧版按量付费。 */
enum class OpencodeLane(val label: String) {
    NONE(""),
    CONSOLE("OpenCode v2"),
    GO("OpenCode Go"),
    ZEN("OpenCode Zen"),
}

/**
 * 由 Base URL 推断出的网关形态。
 *
 * - [base] 永远是「根地址」：已剥掉 `/chat/completions`、`/responses` 之类的端点后缀，
 *   这样用户既可以直接粘根地址，也可以粘完整端点。
 * - [consoleStyle] 表示是否是 `/inference` 形态（路径为 `/openai/v1/...` 与 `/anthropic/v1/...`）。
 *   旧版 Zen/Go 则是 `/chat/completions`、`/responses`、`/messages`。
 */
data class ApiGateway(
    val lane: OpencodeLane,
    val base: String,
    val consoleStyle: Boolean,
) {
    val isOpencode: Boolean get() = lane != OpencodeLane.NONE

    /** Files API、余额、语音工具目前由 DeepSeek 官方提供，OpenCode 网关没有对应实现。 */
    val supportsFiles: Boolean get() = !isOpencode
    val supportsBalance: Boolean get() = !isOpencode
    val supportsVoice: Boolean get() = !isOpencode

    val label: String get() = if (isOpencode) lane.label else "DeepSeek 官方"
}

object Gateways {

    private val SUFFIXES = listOf(
        "/openai/v1/chat/completions",
        "/openai/v1/responses",
        "/anthropic/v1/messages",
        "/chat/completions",
        "/responses",
        "/messages",
    )

    fun detect(rawBase: String): ApiGateway {
        val cleaned = rawBase.trim().trimEnd('/').ifBlank { "https://api.deepseek.com" }
        val url = cleaned.toHttpUrlOrNull() ?: return ApiGateway(OpencodeLane.NONE, cleaned, false)
        val host = url.host.lowercase()
        if (host != "opencode.ai" && !host.endsWith(".opencode.ai")) {
            return ApiGateway(OpencodeLane.NONE, cleaned, false)
        }

        val path = url.encodedPath.lowercase()
        val suffix = SUFFIXES.firstOrNull { path.endsWith(it) }
        val root = if (suffix != null) cleaned.dropLast(suffix.length).trimEnd('/') else cleaned
        val rootPath = root.toHttpUrlOrNull()?.encodedPath?.lowercase().orEmpty()

        val lane = when {
            rootPath.contains("/zen/go") || rootPath.contains("/inference/go") -> OpencodeLane.GO
            rootPath.contains("/zen") -> OpencodeLane.ZEN
            else -> OpencodeLane.CONSOLE
        }
        val consoleStyle = rootPath.contains("/inference")
        return ApiGateway(lane, root, consoleStyle)
    }

    fun of(settings: AppSettings): ApiGateway = detect(settings.normalizedBase)
}

/** 三种协议在不同网关下的实际 URL。集中在这里，避免各处拼字符串。 */
object Endpoints {

    fun chat(settings: AppSettings): String {
        val g = Gateways.of(settings)
        if (!g.isOpencode) {
            return when (settings.protocol) {
                Protocol.CHAT -> "${g.base}/chat/completions"
                Protocol.RESPONSES -> "${g.base}/responses"
                Protocol.ANTHROPIC -> "${g.base}/anthropic/v1/messages"
            }
        }
        return if (g.consoleStyle) {
            when (settings.protocol) {
                Protocol.CHAT -> "${g.base}/openai/v1/chat/completions"
                Protocol.RESPONSES -> "${g.base}/openai/v1/responses"
                Protocol.ANTHROPIC -> "${g.base}/anthropic/v1/messages"
            }
        } else {
            when (settings.protocol) {
                Protocol.CHAT -> "${g.base}/chat/completions"
                Protocol.RESPONSES -> "${g.base}/responses"
                Protocol.ANTHROPIC -> "${g.base}/messages"
            }
        }
    }

    fun models(settings: AppSettings): String {
        val g = Gateways.of(settings)
        return if (g.isOpencode && g.consoleStyle) "${g.base}/v1/models" else "${g.base}/models"
    }

    fun balance(settings: AppSettings): String? =
        if (Gateways.of(settings).supportsBalance) "${Gateways.of(settings).base}/user/balance" else null

    fun files(settings: AppSettings): String? {
        val g = Gateways.of(settings)
        if (!g.supportsFiles) return null
        return if (settings.protocol == Protocol.ANTHROPIC) "${g.base}/anthropic/v1/files"
        else "${g.base}/files"
    }

    fun voice(settings: AppSettings, tool: String): String? {
        val g = Gateways.of(settings)
        return if (g.supportsVoice) "${g.base}/v1/tools/$tool" else null
    }
}

/**
 * OpenCode 会话头。
 *
 * 自 2026-09 起 opencode.ai 的 Go 网关要求每个请求带一个稳定的 `x-opencode-session`，
 * 缺失会直接 400 MissingSessionID；Console（v2）也靠它做路由与提示词缓存。
 * 值本身不需要注册，只要同一会话稳定即可，所以直接用会话 id 派生。
 */
object OpencodeSession {

    const val HEADER = "x-opencode-session"
    const val USER_AGENT = "deepseek-hot/1.1.0 (Android)"

    /** 非会话调用（拉模型、探测）用的进程内稳定 id。 */
    private val fallbackId: String by lazy {
        "ses_" + UUID.randomUUID().toString().replace("-", "")
    }

    fun enabled(settings: AppSettings): Boolean = when (settings.opencodeSession) {
        1 -> true   // 始终发送
        2 -> false  // 关闭
        else -> Gateways.of(settings).isOpencode
    }

    fun id(convId: String?): String {
        if (convId.isNullOrBlank()) return fallbackId
        val clean = convId.replace("-", "").lowercase().filter { it.isLetterOrDigit() }
        return "ses_" + clean.take(56)
    }

    fun headers(settings: AppSettings, convId: String? = null): Map<String, String> =
        if (enabled(settings)) mapOf(HEADER to id(convId)) else emptyMap()
}
