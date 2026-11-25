// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Hotsteel2901

package com.hotsteel.deepseekhot.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

private val Context.settingsDataStore by preferencesDataStore(name = "deepseek_hot_settings")

private object K {
    val apiKey = stringPreferencesKey("api_key")
    val baseUrl = stringPreferencesKey("base_url")
    val protocol = stringPreferencesKey("protocol")
    val model = stringPreferencesKey("model")
    val effort = stringPreferencesKey("effort")
    val webSearch = booleanPreferencesKey("web_search")
    val readUrl = booleanPreferencesKey("read_url")
    val safety = booleanPreferencesKey("safety")
    val autoTitle = booleanPreferencesKey("auto_title")
    val systemPrompt = stringPreferencesKey("system_prompt")
    val includeMeta = booleanPreferencesKey("include_meta")
    val cacheOptimization = booleanPreferencesKey("cache_optimization")
    val activePresetId = stringPreferencesKey("active_preset_id")
    val language = stringPreferencesKey("language")
    val location = stringPreferencesKey("location")
    val themeMode = intPreferencesKey("theme_mode")
    val dynamicColor = booleanPreferencesKey("dynamic_color")
    val ttsVoice = stringPreferencesKey("tts_voice")
    val ttsSpeed = floatPreferencesKey("tts_speed")
    val ttsStreamWs = booleanPreferencesKey("tts_stream_ws")
    val autoRead = booleanPreferencesKey("auto_read")
    val haptics = booleanPreferencesKey("haptics")
    val opencodeSession = intPreferencesKey("opencode_session")
}

class SettingsStore(private val context: Context) {

    val flow: Flow<AppSettings> = context.settingsDataStore.data.map { p ->
        val defaults = AppSettings()
        AppSettings(
            apiKey = p[K.apiKey] ?: defaults.apiKey,
            baseUrl = p[K.baseUrl] ?: defaults.baseUrl,
            protocol = Protocol.of(p[K.protocol]),
            model = p[K.model] ?: defaults.model,
            effort = p[K.effort] ?: defaults.effort,
            webSearch = p[K.webSearch] ?: defaults.webSearch,
            readUrl = p[K.readUrl] ?: defaults.readUrl,
            safetyCheck = p[K.safety] ?: defaults.safetyCheck,
            autoTitle = p[K.autoTitle] ?: defaults.autoTitle,
            systemPrompt = p[K.systemPrompt] ?: defaults.systemPrompt,
            includeMeta = p[K.includeMeta] ?: defaults.includeMeta,
            cacheOptimization = p[K.cacheOptimization] ?: defaults.cacheOptimization,
            activePresetId = p[K.activePresetId] ?: defaults.activePresetId,
            language = p[K.language] ?: defaults.language,
            location = p[K.location] ?: defaults.location,
            themeMode = p[K.themeMode] ?: defaults.themeMode,
            dynamicColor = p[K.dynamicColor] ?: defaults.dynamicColor,
            ttsVoice = p[K.ttsVoice] ?: defaults.ttsVoice,
            ttsSpeed = (p[K.ttsSpeed] ?: defaults.ttsSpeed).coerceIn(0.75f, 2.0f),
            ttsStreamWs = p[K.ttsStreamWs] ?: defaults.ttsStreamWs,
            autoRead = p[K.autoRead] ?: defaults.autoRead,
            haptics = p[K.haptics] ?: defaults.haptics,
            opencodeSession = p[K.opencodeSession] ?: defaults.opencodeSession,
        )
    }

    suspend fun save(s: AppSettings) {
        context.settingsDataStore.edit { p ->
            p[K.apiKey] = s.apiKey
            p[K.baseUrl] = s.baseUrl
            p[K.protocol] = s.protocol.key
            p[K.model] = s.model
            p[K.effort] = s.effort
            p[K.webSearch] = s.webSearch
            p[K.readUrl] = s.readUrl
            p[K.safety] = s.safetyCheck
            p[K.autoTitle] = s.autoTitle
            p[K.systemPrompt] = s.systemPrompt
            p[K.includeMeta] = s.includeMeta
            p[K.cacheOptimization] = s.cacheOptimization
            p[K.activePresetId] = s.activePresetId
            p[K.language] = s.language
            p[K.location] = s.location
            p[K.themeMode] = s.themeMode
            p[K.dynamicColor] = s.dynamicColor
            p[K.ttsVoice] = s.ttsVoice
            p[K.ttsSpeed] = s.ttsSpeed
            p[K.ttsStreamWs] = s.ttsStreamWs
            p[K.autoRead] = s.autoRead
            p[K.haptics] = s.haptics
            p[K.opencodeSession] = s.opencodeSession
        }
    }
}

class ConversationRepository(context: Context) {

    private val dir = File(context.filesDir, "conversations").apply { mkdirs() }
    private val attachmentDir = File(context.filesDir, "attachments").apply { mkdirs() }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    fun attachmentFile(name: String): File = File(attachmentDir, name)

    suspend fun loadAll(): List<Conversation> = withContext(Dispatchers.IO) {
        dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.mapNotNull { f ->
                runCatching { json.decodeFromString<Conversation>(f.readText()) }.getOrNull()
            }
            ?.sortedWith(compareByDescending<Conversation> { it.pinned }.thenByDescending { it.updatedAt })
            .orEmpty()
    }

    suspend fun save(conv: Conversation) = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(dir, "${conv.id}.json")
            val tmp = File(dir, "${conv.id}.json.tmp")
            tmp.writeText(json.encodeToString(Conversation.serializer(), conv))
            if (target.exists()) target.delete()
            tmp.renameTo(target)
        }
        Unit
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        File(dir, "$id.json").delete()
        Unit
    }

    suspend fun exportAll(): String = withContext(Dispatchers.IO) {
        val all = loadAll()
        json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(Conversation.serializer()),
            all
        )
    }

    /** 支持本应用导出格式，也尽量兼容 DeepSeek 网页端导出的对话 JSON。 */
    suspend fun importJson(text: String): Int = withContext(Dispatchers.IO) {
        val convs = runCatching {
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(Conversation.serializer()),
                text
            )
        }.getOrElse { emptyList() }

        var count = 0
        convs.forEach { conv ->
            if (conv.messages.isNotEmpty()) {
                save(conv)
                count++
            }
        }
        count
    }
}

class StatsStore(context: Context) {

    private val file = File(context.filesDir, "usage_stats.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(): UsageStats = runCatching {
        json.decodeFromString<UsageStats>(file.readText())
    }.getOrNull() ?: UsageStats()

    fun save(stats: UsageStats) {
        runCatching { file.writeText(json.encodeToString(UsageStats.serializer(), stats)) }
    }
}
