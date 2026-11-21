// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Hotsteel2901

package com.hotsteel.deepseekhot.net

import com.hotsteel.deepseekhot.data.AppSettings
import com.hotsteel.deepseekhot.data.Block
import com.hotsteel.deepseekhot.data.ChatMsg
import com.hotsteel.deepseekhot.data.Citation
import com.hotsteel.deepseekhot.data.Conversation
import com.hotsteel.deepseekhot.data.Protocol
import com.hotsteel.deepseekhot.data.Reply
import com.hotsteel.deepseekhot.data.Source
import com.hotsteel.deepseekhot.data.Usage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONArray
import org.json.JSONObject

sealed interface StreamEvent {
    data class TextDelta(val text: String) : StreamEvent
    data class ThinkingDelta(val text: String) : StreamEvent
    data object ThinkingDone : StreamEvent
    data class ToolStart(
        val id: String,
        val name: String,
        val kind: String,
        val action: String,
        val queries: List<String> = emptyList(),
        val url: String? = null,
    ) : StreamEvent

    data class ToolDone(
        val id: String,
        val sources: List<Source> = emptyList(),
        val status: String = "completed",
        val error: String? = null,
        val url: String? = null,
        val title: String? = null,
        val kind: String? = null,
        val signature: String? = null,
    ) : StreamEvent

    data class CitationAdded(val citation: Citation) : StreamEvent
    data class Meta(val usage: Usage? = null, val title: String? = null, val stopReason: String? = null) : StreamEvent
    data object Done : StreamEvent
}

object Prompts {

    fun system(settings: AppSettings, conv: Conversation): String = buildString {
        val custom = settings.systemPrompt.trim()
        if (custom.isNotEmpty()) {
            append(custom)
        } else {
            append("你是 DeepSeek，由深度求索（DeepSeek）创造的 AI 助手。")
            append("请用准确、简洁、有条理的方式回答，先给结论再给细节；不确定时明确说明，不要编造。")
            append("使用 Markdown 组织内容，公式用 LaTeX，代码块标注语言。")
        }
        if (settings.includeMeta) {
            val now = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Shanghai"))
            val fmt = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE HH:mm"))
            append("\n\n[环境] 当前时间：$fmt（北京时间）。用户所在地：${settings.location}。")
            append("界面语言：${settings.language}，除非用户另有要求，请用${settings.language}回答。")
        }
    }
}

class ChatEngine(private val api: ApiClient) {

    private fun url(settings: AppSettings): String = Endpoints.chat(settings)

    fun stream(
        conv: Conversation,
        settings: AppSettings,
        continueText: String? = null,
    ): Flow<StreamEvent> = flow {
        val system = Prompts.system(settings, conv)
        val payload = when (settings.protocol) {
            Protocol.CHAT -> chatBody(conv, settings, system, continueText)
            Protocol.RESPONSES -> responsesBody(conv, settings, system, continueText)
            Protocol.ANTHROPIC -> anthropicBody(conv, settings, system, continueText)
        }
        val headers = api.headers(
            settings,
            beta = settings.protocol == Protocol.ANTHROPIC,
            conversationId = conv.id,
        )
        val sse = api.stream(url(settings), headers, payload.toString())
        when (settings.protocol) {
            Protocol.CHAT -> collectChat(sse)
            Protocol.RESPONSES -> collectResponses(sse)
            Protocol.ANTHROPIC -> collectAnthropic(sse)
        }
    }

    // ---------------------------------------------------------------- 请求构建

    private fun readyFiles(m: ChatMsg) = m.attachments.filter { it.status == "ready" && !it.fileId.isNullOrBlank() }

    private fun assistantText(m: ChatMsg): String {
        val r = m.reply ?: return ""
        if (r.status != "done" && r.status != "stopped") return ""
        return r.blocks.filterIsInstance<Block.Text>().joinToString("") { it.text }
    }

    private fun chatBody(
        conv: Conversation,
        s: AppSettings,
        system: String,
        continueText: String?,
    ): JSONObject {
        val messages = JSONArray()
        if (system.isNotBlank()) messages.put(JSONObject().put("role", "system").put("content", system))

        conv.messages.forEach { m ->
            if (m.role == "user") {
                val files = readyFiles(m)
                if (files.isEmpty()) {
                    messages.put(JSONObject().put("role", "user").put("content", m.text))
                } else {
                    val arr = JSONArray()
                    files.forEach { a -> arr.put(JSONObject().put("type", "file").put("file_id", a.fileId)) }
                    if (m.text.isNotBlank()) arr.put(JSONObject().put("type", "text").put("text", m.text))
                    messages.put(JSONObject().put("role", "user").put("content", arr))
                }
            } else {
                val text = assistantText(m)
                if (text.isBlank()) return@forEach
                val o = JSONObject().put("role", "assistant").put("content", text)
                if (s.thinking) {
                    val think = m.reply?.blocks?.filterIsInstance<Block.Thinking>()?.joinToString("") { it.text }
                    if (!think.isNullOrBlank()) o.put("reasoning_content", think)
                }
                messages.put(o)
            }
        }

        if (!continueText.isNullOrBlank()) {
            messages.put(JSONObject().put("role", "assistant").put("content", continueText))
        }

        return JSONObject().apply {
            put("model", s.model)
            put("messages", messages)
            put("stream", true)
            if (s.thinking) {
                put("thinking", JSONObject().put("type", "enabled"))
                put("reasoning_effort", s.effort)
            } else {
                put("thinking", JSONObject().put("type", "disabled"))
            }
            tools(s).takeIf { it.length() > 0 }?.let { put("tools", it) }
            if (s.safetyCheck) put("safety_check", JSONObject().put("level", "high"))
            if (s.autoTitle && conv.messages.count { it.role == "assistant" } == 0) {
                put("title", JSONObject().put("enabled", true))
            }
        }
    }

    private fun responsesBody(
        conv: Conversation,
        s: AppSettings,
        system: String,
        continueText: String?,
    ): JSONObject {
        val input = JSONArray()
        conv.messages.forEach { m ->
            if (m.role == "user") {
                val files = readyFiles(m)
                val content = JSONArray()
                files.forEach { a -> content.put(JSONObject().put("type", "input_file").put("file_id", a.fileId)) }
                if (m.text.isNotBlank() || content.length() == 0) {
                    content.put(JSONObject().put("type", "input_text").put("text", m.text))
                }
                input.put(JSONObject().put("role", "user").put("content", content))
            } else {
                val text = assistantText(m)
                if (text.isBlank()) return@forEach
                input.put(
                    JSONObject().put("role", "assistant").put(
                        "content", JSONArray().put(JSONObject().put("type", "output_text").put("text", text))
                    )
                )
            }
        }
        if (!continueText.isNullOrBlank()) {
            input.put(
                JSONObject().put("role", "assistant").put(
                    "content", JSONArray().put(JSONObject().put("type", "output_text").put("text", continueText))
                )
            )
        }
        return JSONObject().apply {
            put("model", s.model)
            if (system.isNotBlank()) put("instructions", system)
            put("input", input)
            put("stream", true)
            put("reasoning", JSONObject().put("effort", if (s.thinking) s.effort else "none"))
            tools(s).takeIf { it.length() > 0 }?.let { put("tools", it) }
            if (s.safetyCheck) put("safety_check", JSONObject().put("level", "high"))
            if (s.autoTitle && conv.messages.count { it.role == "assistant" } == 0) {
                put("title", JSONObject().put("enabled", true))
            }
        }
    }

    private fun anthropicBody(
        conv: Conversation,
        s: AppSettings,
        system: String,
        continueText: String?,
    ): JSONObject {
        val messages = JSONArray()
        conv.messages.forEach { m ->
            if (m.role == "user") {
                val files = readyFiles(m)
                if (files.isEmpty()) {
                    messages.put(JSONObject().put("role", "user").put("content", m.text))
                } else {
                    val arr = JSONArray()
                    files.forEach { a ->
                        arr.put(
                            JSONObject().put("type", if (a.isImage) "image" else "document")
                                .put("source", JSONObject().put("type", "file").put("file_id", a.fileId))
                        )
                    }
                    if (m.text.isNotBlank()) arr.put(JSONObject().put("type", "text").put("text", m.text))
                    messages.put(JSONObject().put("role", "user").put("content", arr))
                }
            } else {
                val reply = m.reply
                if (reply == null || (reply.status != "done" && reply.status != "stopped")) return@forEach
                val content = JSONArray()
                reply.blocks.forEach { block ->
                    when (block) {
                        is Block.Text -> if (block.text.isNotBlank()) {
                            content.put(JSONObject().put("type", "text").put("text", block.text))
                        }

                        is Block.Tool -> if (block.kind == "read_link" &&
                            block.status == "completed" &&
                            !block.signature.isNullOrBlank() &&
                            !block.url.isNullOrBlank()
                        ) {
                            content.put(
                                JSONObject().put("type", "server_tool_use")
                                    .put("id", block.id)
                                    .put("name", "deepseek_read_url")
                                    .put("input", JSONObject().put("action", "read_link").put("url", block.url))
                                    .put("kind", "read_link")
                            )
                            val result = JSONObject()
                                .put("type", "web_fetch_result")
                                .put("url", block.url)
                                .put(
                                    "content", JSONObject()
                                        .put("type", "document")
                                        .put(
                                            "source", JSONObject()
                                                .put("type", "text")
                                                .put("media_type", "text/plain")
                                                .put("data", "")
                                        )
                                        .put("title", block.sources.firstOrNull()?.title ?: block.url)
                                )
                                .put("published_at", org.json.JSONObject.NULL)
                            content.put(
                                JSONObject().put("type", "web_fetch_tool_result")
                                    .put("tool_use_id", block.id)
                                    .put("content", result)
                                    .put("kind", "read_link")
                                    .put("signature", block.signature)
                            )
                        }

                        else -> Unit
                    }
                }
                if (content.length() == 0) return@forEach
                messages.put(JSONObject().put("role", "assistant").put("content", content))
            }
        }
        if (!continueText.isNullOrBlank()) {
            messages.put(
                JSONObject().put("role", "assistant").put(
                    "content", JSONArray().put(JSONObject().put("type", "text").put("text", continueText))
                )
            )
        }
        return JSONObject().apply {
            put("model", s.model)
            if (system.isNotBlank()) put("system", system)
            put("messages", messages)
            put("stream", true)
            put("thinking", JSONObject().put("type", if (s.thinking) "enabled" else "disabled"))
            if (s.thinking) put("output_config", JSONObject().put("effort", s.effort))
            tools(s).takeIf { it.length() > 0 }?.let { put("tools", it) }
            if (s.safetyCheck) put("safety_check", JSONObject().put("level", "high"))
            if (s.autoTitle && conv.messages.count { it.role == "assistant" } == 0) {
                put("title", JSONObject().put("enabled", true))
            }
        }
    }

    private fun tools(s: AppSettings): JSONArray {
        val arr = JSONArray()
        if (s.webSearch) {
            arr.put(
                if (s.protocol == Protocol.ANTHROPIC)
                    JSONObject().put("type", "deepseek_web_search").put("name", "deepseek_web_search")
                else JSONObject().put("type", "deepseek_web_search")
            )
        }
        if (s.readUrl) {
            arr.put(
                if (s.protocol == Protocol.ANTHROPIC)
                    JSONObject().put("type", "deepseek_read_url").put("name", "deepseek_read_url")
                else JSONObject().put("type", "deepseek_read_url")
            )
        }
        return arr
    }

    // ---------------------------------------------------------------- 流式解析

    private suspend fun kotlinx.coroutines.flow.FlowCollector<StreamEvent>.collectChat(sse: Flow<Sse>) {
        sse.collect { item ->
            if (item.data == "[DONE]") return@collect
            val o = runCatching { JSONObject(item.data) }.getOrNull() ?: return@collect
            if (o.has("error")) {
                throw ApiException(o.optJSONObject("error")?.optString("message") ?: "请求失败")
            }
            o.optJSONObject("server_tool_event")?.let { ev -> emitChatToolEvent(ev) }
            o.optJSONObject("usage")?.let { u -> emit(StreamEvent.Meta(usage = parseChatUsage(u))) }

            val choices = o.optJSONArray("choices") ?: return@collect
            val choice = choices.optJSONObject(0) ?: return@collect
            val delta = choice.optJSONObject("delta") ?: JSONObject()

            delta.optString("reasoning_content").takeIf { it.isNotEmpty() }?.let {
                emit(StreamEvent.ThinkingDelta(it))
            }
            delta.optString("content").takeIf { it.isNotEmpty() }?.let {
                emit(StreamEvent.TextDelta(it))
            }
            delta.optString("title").takeIf { it.isNotBlank() }?.let {
                emit(StreamEvent.Meta(title = it))
            }
            delta.optJSONArray("annotations")?.let { arr ->
                for (i in 0 until arr.length()) emitAnnotation(arr.optJSONObject(i))
            }
            delta.optJSONArray("server_tool_calls")?.let { steps ->
                for (i in 0 until steps.length()) {
                    val step = steps.optJSONObject(i) ?: continue
                    val calls = step.optJSONArray("calls") ?: continue
                    for (j in 0 until calls.length()) {
                        val c = calls.optJSONObject(j) ?: continue
                        emitChatToolCall(c)
                    }
                }
            }
            choice.optString("finish_reason").takeIf { it.isNotBlank() && it != "null" }?.let {
                emit(StreamEvent.Meta(stopReason = it))
            }
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<StreamEvent>.emitChatToolEvent(ev: JSONObject) {
        when (ev.optString("type")) {
            "call_started" -> ev.optJSONObject("call")?.let { emitChatToolCall(it) }
            "call_completed" -> ev.optJSONObject("call")?.let { emitChatToolCall(it) }
            "step_reasoning_done" -> emit(StreamEvent.ThinkingDone)
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<StreamEvent>.emitChatToolCall(c: JSONObject) {
        val action = c.optJSONObject("action")
        val actionType = action?.optString("type") ?: "search"
        val queries = action?.optJSONArray("queries")?.let { a ->
            (0 until a.length()).map { a.optString(it) }
        }.orEmpty()
        val url = action?.optString("url")?.takeIf { it.isNotBlank() }
        val status = c.optString("status", "running")
        if (status == "running" || status.isBlank()) {
            emit(
                StreamEvent.ToolStart(
                    id = c.optString("id"),
                    name = c.optString("type", "deepseek_web_search"),
                    kind = c.optString("kind", "search"),
                    action = actionType,
                    queries = queries,
                    url = url,
                )
            )
        } else {
            emit(
                StreamEvent.ToolDone(
                    id = c.optString("id"),
                    sources = parseSources(c.optJSONArray("sources")),
                    status = status,
                    error = c.optJSONObject("error")?.optString("code"),
                    url = c.optJSONObject("page")?.optString("url") ?: url,
                    title = c.optJSONObject("page")?.optString("title"),
                    kind = c.optString("kind", "search"),
                    signature = c.optString("signature").takeIf { it.isNotBlank() },
                )
            )
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<StreamEvent>.emitAnnotation(o: JSONObject?) {
        if (o == null) return
        val c = o.optJSONObject("url_citation") ?: return
        val url = c.optString("url")
        if (url.isBlank()) return
        emit(StreamEvent.CitationAdded(Citation(url = url, title = c.optString("title").takeIf { it.isNotBlank() })))
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<StreamEvent>.collectResponses(sse: Flow<Sse>) {
        sse.collect { item ->
            if (item.data == "[DONE]") return@collect
            val o = runCatching { JSONObject(item.data) }.getOrNull() ?: return@collect
            val type = o.optString("type").ifBlank { item.event.orEmpty() }
            when (type) {
                "response.output_text.delta" -> {
                    val d = o.optString("delta")
                    if (d.isNotEmpty()) emit(StreamEvent.TextDelta(d))
                }

                "response.reasoning_text.delta" -> {
                    val d = o.optString("delta")
                    if (d.isNotEmpty()) emit(StreamEvent.ThinkingDelta(d))
                }

                "response.reasoning_text.done" -> emit(StreamEvent.ThinkingDone)

                "response.output_item.added" -> {
                    val it0 = o.optJSONObject("item") ?: return@collect
                    if (it0.optString("type") == "web_search_call") {
                        val action = it0.optJSONObject("action")
                        emit(
                            StreamEvent.ToolStart(
                                id = it0.optString("id"),
                                name = "deepseek_web_search",
                                kind = it0.optString("kind", "search"),
                                action = action?.optString("type") ?: "search",
                                queries = action?.optJSONArray("queries")?.let { a ->
                                    (0 until a.length()).map { a.optString(it) }
                                }.orEmpty(),
                                url = action?.optString("url")?.takeIf { s -> s.isNotBlank() }
                                    ?: action?.optJSONObject("page")?.optString("url"),
                            )
                        )
                    }
                }

                "response.output_item.done" -> {
                    val it0 = o.optJSONObject("item") ?: return@collect
                    if (it0.optString("type") == "web_search_call") {
                        val action = it0.optJSONObject("action")
                        val page = action?.optJSONObject("page")
                        emit(
                            StreamEvent.ToolDone(
                                id = it0.optString("id"),
                                sources = parseSources(action?.optJSONArray("sources")),
                                status = it0.optString("status", "completed"),
                                error = it0.optJSONObject("error")?.optString("code"),
                                url = page?.optString("url"),
                                title = page?.optString("title"),
                                kind = it0.optString("kind", "search"),
                                signature = it0.optString("signature").takeIf { it.isNotBlank() },
                            )
                        )
                    }
                }

                "response.output_text.annotation.added" -> {
                    val a = o.optJSONObject("annotation")
                    if (a != null && a.optString("type") == "url_citation") {
                        val url = a.optString("url")
                        if (url.isNotBlank()) {
                            emit(
                                StreamEvent.CitationAdded(
                                    Citation(url = url, title = a.optString("title").takeIf { t -> t.isNotBlank() })
                                )
                            )
                        }
                    }
                }

                "response.completed", "response.incomplete", "response.failed" -> {
                    val resp = o.optJSONObject("response")
                    val usage = resp?.optJSONObject("usage")?.let { parseResponsesUsage(it) }
                    val title = resp?.optString("title")?.takeIf { it.isNotBlank() }
                    val stop = when (type) {
                        "response.incomplete" -> resp?.optJSONObject("incomplete_details")?.optString("reason") ?: "incomplete"
                        "response.failed" -> "failed"
                        else -> "stop"
                    }
                    emit(StreamEvent.Meta(usage = usage, title = title, stopReason = stop))
                }
            }
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<StreamEvent>.collectAnthropic(sse: Flow<Sse>) {
        val blockTypes = mutableMapOf<Int, String>()
        sse.collect { item ->
            if (item.data == "[DONE]") return@collect
            val o = runCatching { JSONObject(item.data) }.getOrNull() ?: return@collect
            if (o.has("error")) {
                throw ApiException(o.optJSONObject("error")?.optString("message") ?: item.data)
            }
            when (o.optString("type").ifBlank { item.event.orEmpty() }) {
                "message_start" -> {
                    val usage = o.optJSONObject("message")?.optJSONObject("usage")?.let { parseAnthropicUsage(it) }
                    emit(StreamEvent.Meta(usage = usage))
                }

                "content_block_start" -> {
                    val idx = o.optInt("index")
                    val block = o.optJSONObject("content_block") ?: return@collect
                    val btype = block.optString("type")
                    blockTypes[idx] = btype
                    when (btype) {
                        "thinking" -> {
                            val t = block.optString("thinking")
                            emit(StreamEvent.ThinkingDelta(t))
                        }

                        "text" -> {
                            val t = block.optString("text")
                            if (t.isNotEmpty()) emit(StreamEvent.TextDelta(t))
                        }

                        "server_tool_use" -> emit(
                            StreamEvent.ToolStart(
                                id = block.optString("id"),
                                name = block.optString("name"),
                                kind = block.optString("kind", "search"),
                                action = block.optJSONObject("input")?.optString("action") ?: "search",
                                queries = block.optJSONObject("input")?.optJSONArray("queries")?.let { a ->
                                    (0 until a.length()).map { a.optString(it) }
                                }.orEmpty(),
                                url = block.optJSONObject("input")?.optString("url")?.takeIf { it.isNotBlank() },
                            )
                        )

                        "web_search_tool_result" -> {
                            val content = block.optJSONArray("content")
                            emit(
                                StreamEvent.ToolDone(
                                    id = block.optString("tool_use_id"),
                                    sources = parseSearchContent(content),
                                    kind = block.optString("kind", "search"),
                                )
                            )
                        }

                        "web_fetch_tool_result" -> {
                            val c = block.optJSONObject("content")
                            val ok = c?.optString("type") == "web_fetch_result"
                            emit(
                                StreamEvent.ToolDone(
                                    id = block.optString("tool_use_id"),
                                    status = if (ok) "completed" else "failed",
                                    error = if (!ok) c?.optString("error_code") else null,
                                    url = c?.optString("url"),
                                    title = c?.optJSONObject("content")?.optString("title"),
                                    kind = block.optString("kind", "read_link"),
                                )
                            )
                        }
                    }
                }

                "content_block_delta" -> {
                    val idx = o.optInt("index")
                    val delta = o.optJSONObject("delta") ?: return@collect
                    when (delta.optString("type")) {
                        "text_delta" -> emit(StreamEvent.TextDelta(delta.optString("text")))
                        "thinking_delta" -> emit(StreamEvent.ThinkingDelta(delta.optString("thinking")))
                        "citations_delta" -> {
                            val c = delta.optJSONObject("citation") ?: return@collect
                            val url = c.optString("url")
                            if (url.isNotBlank()) {
                                emit(
                                    StreamEvent.CitationAdded(
                                        Citation(
                                            url = url,
                                            title = c.optString("title").takeIf { it.isNotBlank() },
                                            citedText = c.optString("cited_text").takeIf { it.isNotBlank() },
                                        )
                                    )
                                )
                            }
                        }
                    }
                    if (blockTypes[idx] == "thinking" && delta.optString("type") == "signature_delta") {
                        emit(StreamEvent.ThinkingDone)
                    }
                }

                "content_block_stop" -> {
                    val idx = o.optInt("index")
                    if (blockTypes[idx] == "thinking") emit(StreamEvent.ThinkingDone)
                }

                "message_delta" -> {
                    val delta = o.optJSONObject("delta")
                    val usage = o.optJSONObject("usage")?.let { parseAnthropicUsage(it) }
                    emit(
                        StreamEvent.Meta(
                            usage = usage,
                            title = delta?.optString("title")?.takeIf { it.isNotBlank() },
                            stopReason = delta?.optString("stop_reason")?.takeIf { it.isNotBlank() },
                        )
                    )
                }

                "error" -> throw ApiException(o.optJSONObject("error")?.optString("message") ?: "请求中断")
            }
        }
    }

    // ---------------------------------------------------------------- 解析辅助

    private fun parseSources(arr: JSONArray?): List<Source> {
        if (arr == null) return emptyList()
        val list = mutableListOf<Source>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("type") != "url" && !o.has("url")) continue
            val url = o.optString("url")
            if (url.isBlank()) continue
            list += Source(
                title = o.optString("title").takeIf { it.isNotBlank() },
                url = url,
                siteName = o.optString("site_name").takeIf { it.isNotBlank() },
                siteIcon = o.optString("site_icon").takeIf { it.isNotBlank() },
                snippet = o.optString("snippet").takeIf { it.isNotBlank() },
                publishedAt = o.optString("published_at").takeIf { it.isNotBlank() },
            )
        }
        return list
    }

    private fun parseSearchContent(arr: JSONArray?): List<Source> {
        if (arr == null) return emptyList()
        val list = mutableListOf<Source>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("type") != "web_search_result") continue
            val url = o.optString("url")
            if (url.isBlank()) continue
            list += Source(
                title = o.optString("title").takeIf { it.isNotBlank() },
                url = url,
                siteName = o.optString("site_name").takeIf { it.isNotBlank() },
                siteIcon = o.optString("site_icon").takeIf { it.isNotBlank() },
                snippet = o.optString("snippet").takeIf { it.isNotBlank() },
                publishedAt = o.optString("page_age").takeIf { it.isNotBlank() },
            )
        }
        return list
    }

    private fun parseChatUsage(u: JSONObject): Usage {
        val input = u.optInt("prompt_tokens", 0)
        val output = u.optInt("completion_tokens", 0)
        val cached = u.optInt("prompt_cache_hit_tokens", 0)
            .takeIf { it > 0 }
            ?: u.optJSONObject("prompt_tokens_details")?.optInt("cached_tokens", 0) ?: 0
        val reasoning = u.optJSONObject("completion_tokens_details")?.optInt("reasoning_tokens", 0) ?: 0
        val tool = u.optJSONObject("server_tool_use")
        return Usage(
            input = input,
            output = output,
            cached = cached,
            reasoning = reasoning,
            total = u.optInt("total_tokens", input + output),
            webSearch = tool?.optInt("tool_search", tool.optInt("web_search_requests", 0)) ?: 0,
            webFetch = tool?.optInt("tool_fetch", tool.optInt("web_fetch_requests", 0)) ?: 0,
            imageSearch = tool?.optInt("tool_image_search", 0) ?: 0,
        )
    }

    private fun parseResponsesUsage(u: JSONObject): Usage {
        val input = u.optInt("input_tokens", 0)
        val output = u.optInt("output_tokens", 0)
        val tool = u.optJSONObject("server_tool_use")
        return Usage(
            input = input,
            output = output,
            cached = u.optJSONObject("input_tokens_details")?.optInt("cached_tokens", 0) ?: 0,
            reasoning = u.optJSONObject("output_tokens_details")?.optInt("reasoning_tokens", 0) ?: 0,
            total = u.optInt("total_tokens", input + output),
            webSearch = tool?.optInt("tool_search", 0) ?: 0,
            webFetch = tool?.optInt("tool_fetch", 0) ?: 0,
            imageSearch = tool?.optInt("tool_image_search", 0) ?: 0,
        )
    }

    private fun parseAnthropicUsage(u: JSONObject): Usage {
        val cached = u.optInt("cache_read_input_tokens", 0)
        val created = u.optInt("cache_creation_input_tokens", 0)
        val input = u.optInt("input_tokens", 0) + cached + created
        val output = u.optInt("output_tokens", 0)
        val tool = u.optJSONObject("server_tool_use")
        return Usage(
            input = input,
            output = output,
            cached = cached,
            reasoning = u.optJSONObject("output_tokens_details")?.optInt("thinking_tokens", 0) ?: 0,
            total = input + output,
            webSearch = tool?.optInt("tool_search", tool.optInt("web_search_requests", 0)) ?: 0,
            webFetch = tool?.optInt("tool_fetch", tool.optInt("web_fetch_requests", 0)) ?: 0,
            imageSearch = tool?.optInt("tool_image_search", 0) ?: 0,
        )
    }
}
