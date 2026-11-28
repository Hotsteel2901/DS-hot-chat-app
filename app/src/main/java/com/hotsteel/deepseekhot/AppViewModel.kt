// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Hotsteel2901

package com.hotsteel.deepseekhot

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hotsteel.deepseekhot.audio.TtsPlayer
import com.hotsteel.deepseekhot.audio.TtsTextPrep
import com.hotsteel.deepseekhot.data.AppSettings
import com.hotsteel.deepseekhot.data.Attachment
import com.hotsteel.deepseekhot.data.Balance
import com.hotsteel.deepseekhot.data.Block
import com.hotsteel.deepseekhot.data.ChatMsg
import com.hotsteel.deepseekhot.data.Conversation
import com.hotsteel.deepseekhot.data.ModelInfo
import com.hotsteel.deepseekhot.data.Protocol
import com.hotsteel.deepseekhot.data.Reply
import com.hotsteel.deepseekhot.data.Source
import com.hotsteel.deepseekhot.data.Usage
import com.hotsteel.deepseekhot.data.UsageStats
import com.hotsteel.deepseekhot.data.ConversationRepository
import com.hotsteel.deepseekhot.data.SettingsStore
import com.hotsteel.deepseekhot.data.StatsStore
import com.hotsteel.deepseekhot.net.ApiClient
import com.hotsteel.deepseekhot.net.ApiException
import com.hotsteel.deepseekhot.net.ChatEngine
import com.hotsteel.deepseekhot.net.Gateways
import com.hotsteel.deepseekhot.net.ApiGateway
import com.hotsteel.deepseekhot.net.StreamEvent
import com.hotsteel.deepseekhot.util.MarkdownExporter
import com.hotsteel.deepseekhot.util.Pricing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

enum class StreamMode { NEW, REGENERATE, CONTINUE }

class LiveReply {
    val blocks = mutableStateListOf<Block>()
    var status by mutableStateOf("streaming")
    var usage by mutableStateOf<Usage?>(null)
    var title by mutableStateOf<String?>(null)
    var stopReason by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)
    var ttftMs by mutableStateOf(0L)
    var startedAt: Long = System.currentTimeMillis()
    var finishedAt: Long = 0L
    var mode: StreamMode = StreamMode.NEW
    var targetId: String? = null
    var model: String = ""
    var protocol: String = ""

    val text: String
        get() = blocks.filterIsInstance<Block.Text>().joinToString("") { it.text }

    val elapsedMs: Long
        get() = if (startedAt > 0) ((if (finishedAt > 0) finishedAt else System.currentTimeMillis()) - startedAt) else 0
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val api = ApiClient()
    private val engine = ChatEngine(api)
    private val repo = ConversationRepository(app)
    private val settingsStore = SettingsStore(app)
    private val statsStore = StatsStore(app)
    val tts = TtsPlayer(app)

    var settings by mutableStateOf(AppSettings())
        private set

    /** 由 Base URL 推断的网关能力（OpenCode v2 / Go / Zen、DeepSeek 官方） */
    val gateway: ApiGateway get() = Gateways.of(settings)
    var conversations by mutableStateOf<List<Conversation>>(emptyList())
        private set
    var current by mutableStateOf<Conversation?>(null)
        private set
    var live by mutableStateOf<LiveReply?>(null)
        private set
    var streaming by mutableStateOf(false)
        private set
    var stopping by mutableStateOf(false)
        private set

    var models by mutableStateOf(defaultModels())
        private set
    var modelsLoading by mutableStateOf(false)
        private set
    var modelsError by mutableStateOf<String?>(null)
        private set

    var balance by mutableStateOf<Balance?>(null)
        private set
    var balanceLoading by mutableStateOf(false)
        private set

    var stats by mutableStateOf(UsageStats())
        private set

    var toast by mutableStateOf<String?>(null)
    var ttsBusyId by mutableStateOf<String?>(null)
        private set
    var asrBusy by mutableStateOf(false)
        private set
    var uploading by mutableStateOf(0)
        private set

    private var streamJob: Job? = null
    private val deletedIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private var ttsJob: Job? = null

    init {
        viewModelScope.launch {
            settings = runCatching { settingsStore.flow.first() }.getOrDefault(AppSettings())
            conversations = runCatching { repo.loadAll() }.getOrDefault(emptyList())
            stats = runCatching { statsStore.load() }.getOrDefault(UsageStats())
            if (conversations.isEmpty()) {
                current = null
            } else {
                current = conversations.firstOrNull()
            }
        }
    }

    // ------------------------------------------------------------ 设置

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val old = settings
        var next = transform(old)
        if (next.model != old.model) {
            val model = models.firstOrNull { it.id == next.model }
            if (model != null && model.efforts.isNotEmpty() && next.effort != "none" && next.effort !in model.efforts) {
                val fallback = model.defaultEffort ?: model.efforts.first()
                next = next.copy(effort = fallback)
            }
        }
        settings = next
        viewModelScope.launch { settingsStore.save(next) }
        if (next.apiKey.isNotBlank() && next.apiKey != old.apiKey) {
            refreshModels(silent = true)
            refreshBalance(silent = true)
        }
    }

    fun setApiKey(key: String) = updateSettings { it.copy(apiKey = key.trim()) }
    fun setProtocol(p: Protocol) = updateSettings { it.copy(protocol = p) }
    fun setModel(id: String) = updateSettings { it.copy(model = id) }
    fun setEffort(level: String) = updateSettings { it.copy(effort = level) }

    // ------------------------------------------------------------ 会话

    fun newChat() {
        stopStreaming()
        current = null
        live = null
    }

    fun openChat(id: String) {
        stopStreaming()
        current = conversations.firstOrNull { it.id == id }
        live = null
    }

    fun deleteChat(id: String) {
        deletedIds.add(id)
        if (current?.id == id) {
            stopStreaming()
            live = null
        }
        viewModelScope.launch {
            repo.delete(id)
            conversations = conversations.filterNot { it.id == id }
            if (current?.id == id) current = conversations.firstOrNull()
        }
    }

    /** 收藏 / 取消收藏单条消息 */
    fun toggleStar(msgId: String) {
        val conv = current ?: return
        var nowStarred = false
        val next = conv.copy(
            messages = conv.messages.map { m ->
                if (m.id == msgId) {
                    nowStarred = !m.starred
                    m.copy(starred = nowStarred)
                } else m
            },
        )
        applyConversation(next)
        toast = if (nowStarred) "已加入收藏夹" else "已取消收藏"
    }

    /** 获取全部会话中已收藏的消息列表（按时间倒序） */
    fun starredMessages(): List<Pair<Conversation, ChatMsg>> =
        conversations.flatMap { conv ->
            conv.messages.filter { it.starred }.map { conv to it }
        }.sortedByDescending { it.second.createdAt }

    /** 从指定消息处分叉出一条新会话，完整保留历史上下文与角色预设以复用缓存前缀 */
    fun forkConversation(upToMsgId: String) {
        if (streaming) return
        val conv = current ?: return
        val idx = conv.messages.indexOfFirst { it.id == upToMsgId }
        if (idx < 0) return
        val forked = Conversation(
            title = "${conv.title.ifBlank { "对话" }} · 分支",
            messages = conv.messages.take(idx + 1),
            createdAt = conv.createdAt,
            updatedAt = System.currentTimeMillis(),
            presetId = conv.presetId ?: settings.activePresetId,
        )
        current = forked
        applyConversation(forked)
        toast = "已从此处创建分支会话"
    }

    /** 切换当前与默认提示词角色预设 */
    fun selectPreset(presetId: String) {
        updateSettings { it.copy(activePresetId = presetId) }
        val conv = current
        if (conv != null && conv.messages.isEmpty()) {
            applyConversation(conv.copy(presetId = presetId))
        }
    }

    /** 将当前会话导出为排版精美的 Markdown 文本 */
    fun exportCurrentMarkdown(): String? =
        current?.takeIf { it.messages.isNotEmpty() }?.let { MarkdownExporter.exportConversation(it) }

    fun renameChat(id: String, title: String) {
        val conv = conversations.firstOrNull { it.id == id } ?: return
        val next = conv.copy(title = title.trim(), updatedAt = System.currentTimeMillis())
        applyConversation(next)
    }

    fun switchVersion(msgId: String, index: Int) {
        val conv = current ?: return
        val next = conv.copy(
            messages = conv.messages.map { m ->
                if (m.id != msgId || m.replies.isEmpty()) m
                else m.copy(active = index.coerceIn(0, m.replies.lastIndex))
            },
            updatedAt = conv.updatedAt
        )
        current = next
        applyConversation(next)
    }

    fun togglePin(id: String) {
        val conv = conversations.firstOrNull { it.id == id } ?: return
        applyConversation(conv.copy(pinned = !conv.pinned))
    }

    private fun applyConversation(conv: Conversation) {
        conversations = (conversations.filterNot { it.id == conv.id } + conv)
            .sortedWith(compareByDescending<Conversation> { it.pinned }.thenByDescending { it.updatedAt })
        if (current?.id == conv.id) current = conv
        viewModelScope.launch { repo.save(conv) }
    }

    fun exportJson(onReady: (String) -> Unit) {
        viewModelScope.launch {
            val text = repo.exportAll()
            withContext(Dispatchers.Main) { onReady(text) }
        }
    }

    fun importJson(text: String) {
        viewModelScope.launch {
            val n = repo.importJson(text)
            conversations = repo.loadAll()
            current = conversations.firstOrNull()
            toast = if (n > 0) "已导入 $n 个会话" else "没有可导入的会话"
        }
    }

    // ------------------------------------------------------------ 发送 / 生成

    fun send(text: String, attachments: List<Attachment>) {
        if (settings.apiKey.isBlank()) {
            toast = "请先在设置中填写 API Key"
            return
        }
        if (streaming) return
        val trimmed = text.trim()
        if (trimmed.isEmpty() && attachments.isEmpty()) return

        val base = current ?: Conversation(presetId = settings.activePresetId)
        val msg = ChatMsg(role = "user", text = trimmed, attachments = attachments)
        val conv = base.copy(
            messages = base.messages + msg,
            title = base.title.ifBlank { titleOf(trimmed, attachments) },
            updatedAt = System.currentTimeMillis(),
        )
        current = conv
        persist(conv)
        startStream(conv, StreamMode.NEW)
    }

    private fun titleOf(text: String, attachments: List<Attachment>): String {
        if (text.isBlank()) return attachments.firstOrNull()?.name ?: "新对话"
        return text.lineSequence().firstOrNull()?.trim()?.take(24) ?: "新对话"
    }

    fun regenerate(msgId: String) {
        if (streaming) return
        val conv = current ?: return
        val idx = conv.messages.indexOfFirst { it.id == msgId }
        if (idx <= 0) return
        val context = conv.copy(messages = conv.messages.take(idx))
        startStream(context, StreamMode.REGENERATE, targetId = msgId)
    }

    fun continueReply(msgId: String) {
        if (streaming) return
        val conv = current ?: return
        val idx = conv.messages.indexOfFirst { it.id == msgId }
        if (idx <= 0) return
        val text = conv.messages[idx].reply?.blocks
            ?.filterIsInstance<Block.Text>()?.joinToString("") { it.text }.orEmpty()
        if (text.isBlank()) {
            toast = "没有可续写的内容"
            return
        }
        val context = conv.copy(messages = conv.messages.take(idx))
        startStream(context, StreamMode.CONTINUE, targetId = msgId, continueText = text)
    }

    fun editUserMessage(msgId: String, newText: String) {
        if (streaming) return
        val conv = current ?: return
        val idx = conv.messages.indexOfFirst { it.id == msgId }
        if (idx < 0) return
        val edited = conv.messages[idx].copy(text = newText.trim())
        val next = conv.copy(
            messages = conv.messages.take(idx) + edited,
            updatedAt = System.currentTimeMillis(),
        )
        current = next
        persist(next)
        startStream(next, StreamMode.NEW)
    }

    fun stopStreaming() {
        if (!streaming) return
        stopping = true
        api.stop()
        streamJob?.cancel()
    }

    private fun startStream(
        conv: Conversation,
        mode: StreamMode,
        targetId: String? = null,
        continueText: String? = null,
    ) {
        if (settings.apiKey.isBlank()) {
            toast = "请先在设置中填写 API Key"
            return
        }
        val lr = LiveReply().apply {
            this.mode = mode
            this.targetId = targetId
            model = settings.model
            protocol = settings.protocol.label
        }
        live = lr
        streaming = true
        stopping = false
        val start = SystemClock.elapsedRealtime()

        streamJob = viewModelScope.launch {
            var failed: String? = null
            try {
                engine.stream(conv, settings, continueText).collect { ev ->
                    applyEvent(lr, ev, start)
                    if (ev is StreamEvent.Done) return@collect
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: ApiException) {
                failed = e.message ?: "请求失败"
            } catch (e: Throwable) {
                failed = e.message ?: e.javaClass.simpleName
            } finally {
                val stopped = stopping
                stopping = false
                streaming = false
                lr.finishedAt = System.currentTimeMillis()
                when {
                    stopped -> lr.status = "stopped"
                    failed != null -> {
                        lr.error = failed
                        lr.status = "error"
                    }

                    else -> lr.status = "done"
                }
                if (lr.usage == null) {
                    lr.usage = Usage(output = estimateTokens(lr.text))
                }
                commit(lr, conv)
                live = null
            }
        }
    }

    private fun estimateTokens(text: String): Int = (text.length / 2.2).toInt()

    private fun applyEvent(lr: LiveReply, ev: StreamEvent, start: Long) {
        when (ev) {
            is StreamEvent.TextDelta -> {
                if (lr.ttftMs == 0L) lr.ttftMs = SystemClock.elapsedRealtime() - start
                val last = lr.blocks.lastOrNull()
                if (last is Block.Text) {
                    lr.blocks[lr.blocks.lastIndex] = last.copy(text = last.text + ev.text)
                } else {
                    lr.blocks.add(Block.Text(text = ev.text))
                }
            }

            is StreamEvent.ThinkingDelta -> {
                if (lr.ttftMs == 0L) lr.ttftMs = SystemClock.elapsedRealtime() - start
                val last = lr.blocks.lastOrNull()
                if (last is Block.Thinking && last.endedAt == null) {
                    lr.blocks[lr.blocks.lastIndex] = last.copy(text = last.text + ev.text)
                } else {
                    lr.blocks.add(Block.Thinking(text = ev.text, startedAt = System.currentTimeMillis()))
                }
            }

            StreamEvent.ThinkingDone -> {
                val last = lr.blocks.lastOrNull()
                if (last is Block.Thinking && last.endedAt == null) {
                    lr.blocks[lr.blocks.lastIndex] = last.copy(endedAt = System.currentTimeMillis())
                }
            }

            is StreamEvent.ToolStart -> {
                val idx = lr.blocks.indexOfFirst { it is Block.Tool && it.id == ev.id && ev.id.isNotEmpty() }
                if (idx >= 0) {
                    val old = lr.blocks[idx] as Block.Tool
                    lr.blocks[idx] = old.copy(
                        status = "running",
                        action = ev.action,
                        queries = if (ev.queries.isEmpty()) old.queries else ev.queries,
                        url = ev.url ?: old.url,
                        name = ev.name.ifBlank { old.name },
                        kind = ev.kind.ifBlank { old.kind },
                    )
                } else {
                    lr.blocks.add(
                        Block.Tool(
                            id = ev.id,
                            name = ev.name,
                            kind = ev.kind,
                            action = ev.action,
                            queries = ev.queries,
                            url = ev.url,
                        )
                    )
                }
            }

            is StreamEvent.ToolDone -> {
                val idx = lr.blocks.indexOfFirst { it is Block.Tool && (it.id == ev.id || (ev.id.isBlank() && it.status == "running")) }
                if (idx >= 0) {
                    val old = lr.blocks[idx] as Block.Tool
                    lr.blocks[idx] = old.copy(
                        status = ev.status,
                        error = ev.error,
                        url = ev.url ?: old.url,
                        kind = ev.kind ?: old.kind,
                        sources = if (ev.sources.isEmpty()) old.sources else ev.sources,
                        signature = ev.signature ?: old.signature,
                    )
                } else {
                    lr.blocks.add(
                        Block.Tool(
                            id = ev.id,
                            name = "deepseek_read_url",
                            kind = ev.kind ?: "read_link",
                            action = "read_link",
                            url = ev.url,
                            sources = ev.sources,
                            status = ev.status,
                            error = ev.error,
                        )
                    )
                }
            }

            is StreamEvent.CitationAdded -> {
                val idx = lr.blocks.indexOfLast { it is Block.Text }
                if (idx >= 0) {
                    val old = lr.blocks[idx] as Block.Text
                    if (old.citations.none { it.url == ev.citation.url }) {
                        lr.blocks[idx] = old.copy(citations = old.citations + ev.citation)
                    }
                }
            }

            is StreamEvent.Meta -> {
                ev.usage?.let { u -> lr.usage = mergeUsage(lr.usage, u) }
                ev.title?.let { if (it.isNotBlank()) lr.title = it }
                ev.stopReason?.let { lr.stopReason = it }
            }

            StreamEvent.Done -> lr.status = "done"
        }
    }

    private fun mergeUsage(old: Usage?, new: Usage): Usage {
        if (old == null) return new
        return old.copy(
            input = maxOf(old.input, new.input),
            output = maxOf(old.output, new.output),
            cached = maxOf(old.cached, new.cached),
            reasoning = maxOf(old.reasoning, new.reasoning),
            total = maxOf(old.total, new.total),
            webSearch = maxOf(old.webSearch, new.webSearch),
            webFetch = maxOf(old.webFetch, new.webFetch),
            imageSearch = maxOf(old.imageSearch, new.imageSearch),
        )
    }

    private fun commit(lr: LiveReply, source: Conversation) {
        if (source.id in deletedIds) return
        val conv = conversations.firstOrNull { it.id == source.id }
            ?: current?.takeIf { it.id == source.id }
            ?: source
        val refusal = lr.stopReason == "refusal" || lr.stopReason == "content_filter"
        val blocks = lr.blocks.toList().filter { b ->
            !(refusal && (b is Block.Text || b is Block.Thinking))
        }.let { list ->
            if (refusal) list + Block.Refusal("该内容触发了模型内容安全策略，回答已被拦截。") else list
        }
        val usage = lr.usage?.let { u ->
            u.copy(cost = Pricing.estimate(lr.model.ifBlank { settings.model }, u, lr.startedAt))
        }
        val reply = Reply(
            blocks = blocks,
            usage = usage,
            status = lr.status,
            stopReason = lr.stopReason,
            error = lr.error,
            model = lr.model,
            protocol = lr.protocol,
            ttftMs = lr.ttftMs,
            latencyMs = lr.finishedAt - lr.startedAt,
            createdAt = System.currentTimeMillis(),
        )

        var messages = conv.messages
        when (lr.mode) {
            StreamMode.NEW -> {
                messages = messages + ChatMsg(role = "assistant", replies = listOf(reply))
            }

            StreamMode.REGENERATE -> {
                val id = lr.targetId
                messages = messages.map { m ->
                    if (m.id == id) m.copy(replies = m.replies + reply, active = m.replies.size) else m
                }
            }

            StreamMode.CONTINUE -> {
                val id = lr.targetId
                messages = messages.map { m ->
                    if (m.id != id) m else {
                        val base = m.reply ?: return@map m
                        val merged = reply.copy(blocks = mergeBlocks(base.blocks, reply.blocks))
                        m.copy(replies = m.replies + merged, active = m.replies.size)
                    }
                }
            }
        }
        val generatedTitle = lr.title
        val keepGenerated = !generatedTitle.isNullOrBlank() &&
            conv.messages.count { it.role == "assistant" } <= 1
        val next = conv.copy(
            messages = messages,
            title = if (keepGenerated) generatedTitle!! else conv.title.ifBlank { generatedTitle.orEmpty() },
            updatedAt = System.currentTimeMillis(),
        )
        applyConversation(next)

        usage?.let { u ->
            val today = LocalDate.now().toString()
            val s = stats
            val dayReset = s.day != today
            val saved = Pricing.savedByCache(lr.model.ifBlank { settings.model }, u, lr.startedAt)
            val cached = u.cached.coerceAtMost(u.input).toLong()
            stats = s.copy(
                requests = s.requests + 1,
                inputTokens = s.inputTokens + u.input,
                outputTokens = s.outputTokens + u.output,
                cachedTokens = s.cachedTokens + cached,
                cost = s.cost + u.cost,
                savedCost = s.savedCost + saved,
                day = today,
                dayRequests = (if (dayReset) 0 else s.dayRequests) + 1,
                dayTokens = (if (dayReset) 0 else s.dayTokens) + u.input + u.output,
                dayInputTokens = (if (dayReset) 0L else s.dayInputTokens) + u.input,
                dayCachedTokens = (if (dayReset) 0L else s.dayCachedTokens) + cached,
                dayCost = (if (dayReset) 0.0 else s.dayCost) + u.cost,
                daySavedCost = (if (dayReset) 0.0 else s.daySavedCost) + saved,
            )
            val snapshot = stats
            viewModelScope.launch(Dispatchers.IO) { statsStore.save(snapshot) }
        }
        if (settings.autoRead && !refusal) {
            val text = reply.blocks.filterIsInstance<Block.Text>().joinToString("") { it.text }
            if (text.isNotBlank()) speak(messages.lastOrNull()?.id ?: "", text)
        }
    }

    private fun mergeBlocks(base: List<Block>, extra: List<Block>): List<Block> {
        val out = base.toMutableList()
        extra.forEach { b ->
            val last = out.lastOrNull()
            if (b is Block.Text && last is Block.Text) {
                out[out.lastIndex] = last.copy(text = last.text + b.text, citations = last.citations + b.citations)
            } else {
                out.add(b)
            }
        }
        return out
    }

    private fun persist(conv: Conversation) {
        conversations = (conversations.filterNot { it.id == conv.id } + conv)
            .sortedWith(compareByDescending<Conversation> { it.pinned }.thenByDescending { it.updatedAt })
        viewModelScope.launch { repo.save(conv) }
    }

    // ------------------------------------------------------------ 模型 / 余额

    fun refreshModels(silent: Boolean = false) {
        if (settings.apiKey.isBlank()) {
            if (!silent) toast = "请先填写 API Key"
            return
        }
        viewModelScope.launch {
            modelsLoading = true
            modelsError = null
            try {
                val list = api.listModels(settings)
                if (list.isNotEmpty()) {
                    models = list
                    if (list.none { it.id == settings.model }) {
                        updateSettings { s -> s.copy(model = list.first().id) }
                    }
                }
            } catch (e: Throwable) {
                modelsError = e.message
                if (!silent) toast = "获取模型失败：${e.message}"
            } finally {
                modelsLoading = false
            }
        }
    }

    fun refreshBalance(silent: Boolean = false) {
        if (settings.apiKey.isBlank()) {
            if (!silent) toast = "请先填写 API Key"
            return
        }
        if (!gateway.supportsBalance) {
            if (!silent) toast = "OpenCode 网关不提供余额接口，请在 Console 查看用量"
            return
        }
        viewModelScope.launch {
            balanceLoading = true
            try {
                balance = api.balance(settings)
            } catch (e: Throwable) {
                if (!silent) toast = "查询余额失败：${e.message}"
            } finally {
                balanceLoading = false
            }
        }
    }

    // ------------------------------------------------------------ 附件

    fun attach(uris: List<Uri>) {
        if (!gateway.supportsFiles) {
            toast = "当前网关不支持文件输入，请切换到 DeepSeek 官方接口"
            return
        }
        uris.forEach { uri ->
            viewModelScope.launch(Dispatchers.IO) {
                val resolver = getApplication<Application>().contentResolver
                var name = "file_${System.currentTimeMillis()}"
                var size = 0L
                runCatching {
                    resolver.query(uri, null, null, null, null)?.use { c ->
                        val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val si = c.getColumnIndex(OpenableColumns.SIZE)
                        if (c.moveToFirst()) {
                            if (ni >= 0) c.getString(ni)?.let { name = it }
                            if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                        }
                    }
                }
                val mime = resolver.getType(uri) ?: guessMime(name)
                val isImage = mime.startsWith("image/")
                val local = repo.attachmentFile("${System.currentTimeMillis()}_${name.replace('/', '_')}")
                val att = Attachment(
                    name = name,
                    mime = mime,
                    size = size,
                    localPath = local.absolutePath,
                    isImage = isImage,
                    status = "uploading",
                )
                withContext(Dispatchers.Main) {
                    pending.add(att)
                    uploading += 1
                }
                try {
                    resolver.openInputStream(uri)?.use { input ->
                        local.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("无法读取文件")
                    val purpose = purposeOf(name, mime)
                    val id = api.uploadFile(settings, local, mime, purpose)
                    withContext(Dispatchers.Main) { replacePending(att.id, att.copy(fileId = id, status = "ready")) }
                } catch (e: Throwable) {
                    withContext(Dispatchers.Main) {
                        replacePending(att.id, att.copy(status = "failed", error = e.message))
                    }
                } finally {
                    withContext(Dispatchers.Main) { uploading -= 1 }
                }
            }
        }
    }

    private fun replacePending(id: String, att: Attachment) {
        val idx = pending.indexOfFirst { it.id == id }
        if (idx >= 0) pending[idx] = att else pending.add(att)
    }

    private fun purposeOf(name: String, mime: String): String? {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            mime.startsWith("image/") -> if (settings.protocol == Protocol.ANTHROPIC) null else "text_recognition"
            ext in setOf("txt", "md", "csv", "json", "log", "xml", "yaml", "yml", "html", "htm", "epub", "mobi", "xlsx", "xls") -> "user_data"
            ext in setOf("pdf", "doc", "docx", "ppt", "pptx") -> "rich_layout_recognition"
            mime.startsWith("text/") -> "user_data"
            else -> "rich_layout_recognition"
        }
    }

    private fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "txt", "md", "csv" -> "text/plain"
        "json" -> "application/json"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        else -> "application/octet-stream"
    }

    private fun addAttachment(att: Attachment) = replacePending(att.id, att)

    var pendingText by mutableStateOf("")

    /** 输入框里待发送的附件（还没成为消息的一部分） */
    val pending = mutableStateListOf<Attachment>()

    fun removePending(id: String) {
        pending.removeAll { it.id == id }
    }

    fun clearPending() {
        pending.clear()
    }

    // ------------------------------------------------------------ 语音

    fun speak(messageId: String, text: String) {
        if (text.isBlank()) return
        if (settings.apiKey.isBlank()) {
            toast = "请先填写 API Key"
            return
        }
        if (tts.playingId == messageId) {
            stopSpeak()
            return
        }
        val chunks = TtsTextPrep.splitChunks(text)
        if (chunks.isEmpty()) {
            toast = "当前消息没有可朗读的文字内容"
            return
        }
        stopSpeak()
        ttsBusyId = messageId
        ttsJob = viewModelScope.launch {
            val token = tts.beginStream(messageId, settings.ttsSpeed)
            var receivedAnyAudio = false
            try {
                val sink: (ByteArray) -> Boolean = { pcm ->
                    receivedAnyAudio = true
                    tts.writePcm(token, pcm)
                }
                if (settings.ttsStreamWs) {
                    try {
                        api.ttsStreamWebSocket(settings, chunks, settings.ttsVoice, sink)
                    } catch (wsErr: Throwable) {
                        if (wsErr is kotlinx.coroutines.CancellationException) throw wsErr
                        // 若 WebSocket 在未下发音频前失败，无缝降级为 HTTP 24kHz PCM 流式合成
                        if (!receivedAnyAudio) {
                            api.ttsStreamHttpPcm(settings, chunks, settings.ttsVoice, sink)
                        } else {
                            throw wsErr
                        }
                    }
                } else {
                    api.ttsStreamHttpPcm(settings, chunks, settings.ttsVoice, sink)
                }
                withContext(Dispatchers.IO) { tts.finishStream(token) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                tts.stop()
                throw e
            } catch (e: Throwable) {
                tts.stop()
                toast = "朗读失败：${e.message}"
            } finally {
                if (ttsBusyId == messageId) ttsBusyId = null
            }
        }
    }

    fun stopSpeak() {
        ttsJob?.cancel()
        ttsJob = null
        api.stopTts()
        tts.stop()
        ttsBusyId = null
    }

    fun toggleTtsPause() {
        tts.togglePause()
    }

    fun cycleTtsSpeed() {
        val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        val curIdx = speeds.indexOfFirst { kotlin.math.abs(it - settings.ttsSpeed) < 0.05f }
        val next = speeds[(curIdx + 1) % speeds.size]
        tts.setPlaybackSpeed(next)
        updateSettings { it.copy(ttsSpeed = next) }
    }

    override fun onCleared() {
        stopStreaming()
        stopSpeak()
        super.onCleared()
    }

    fun transcribe(file: File, onResult: (String) -> Unit) {
        if (settings.apiKey.isBlank()) {
            toast = "请先填写 API Key"
            return
        }
        viewModelScope.launch {
            asrBusy = true
            try {
                val text = api.asr(settings, file)
                if (text.isBlank()) toast = "没有识别到语音" else onResult(text)
            } catch (e: Throwable) {
                toast = "语音识别失败：${e.message}"
            } finally {
                asrBusy = false
                file.delete()
            }
        }
    }

    fun showToast(msg: String) {
        toast = msg
    }

    companion object {
        fun defaultModels(): List<ModelInfo> = listOf(
            ModelInfo(
                id = "deepseek-flash",
                name = "DeepSeek V4.1 Flash",
                contextWindow = 1_000_000,
                maxOutput = 384_000,
                efforts = listOf("low", "high", "max"),
                defaultEffort = "high",
                imageInput = true,
            ),
            ModelInfo(
                id = "deepseek-v4-pro",
                name = "DeepSeek V4 Pro",
                contextWindow = 1_000_000,
                maxOutput = 384_000,
                efforts = listOf("low", "high", "max"),
                defaultEffort = "high",
            ),
        )
    }
}
