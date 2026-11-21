// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Hotsteel2901

package com.hotsteel.deepseekhot.net

import com.hotsteel.deepseekhot.data.AppSettings
import com.hotsteel.deepseekhot.data.Balance
import com.hotsteel.deepseekhot.data.BalanceInfo
import com.hotsteel.deepseekhot.data.ModelInfo
import com.hotsteel.deepseekhot.data.Protocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class ApiException(message: String, val status: Int = 0) : Exception(message)

data class Sse(val event: String?, val data: String)

class ApiClient {

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            // OpenCode 明确要求客户端自报身份，而不是 okhttp/4.12.0 这类通用库名
            val req = chain.request()
            chain.proceed(
                if (req.header("User-Agent") == null) {
                    req.newBuilder().header("User-Agent", OpencodeSession.USER_AGENT).build()
                } else req
            )
        }
        .build()

    @Volatile
    private var activeCall: Call? = null

    fun stop() {
        runCatching { activeCall?.cancel() }
        activeCall = null
    }

    fun headers(
        settings: AppSettings,
        beta: Boolean = false,
        conversationId: String? = null,
    ): Map<String, String> = buildMap {
        when (settings.protocol) {
            Protocol.ANTHROPIC -> {
                put("x-api-key", settings.apiKey)
                if (beta) put("anthropic-beta", "files-api-2025-04-14")
            }

            else -> put("Authorization", "Bearer ${settings.apiKey}")
        }
        put("Accept", "application/json")
        putAll(OpencodeSession.headers(settings, conversationId))
    }

    private fun errorMessage(body: String, status: Int): String {
        val fallback = "HTTP $status"
        if (body.isBlank()) return fallback
        return runCatching {
            val obj = JSONObject(body)
            obj.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                ?: obj.optString("message").takeIf { it.isNotBlank() }
                ?: body.take(400)
        }.getOrElse { body.take(400).ifBlank { fallback } }
    }

    private fun execute(request: Request): String {
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiException(errorMessage(text, resp.code), resp.code)
            return text
        }
    }

    suspend fun listModels(settings: AppSettings): List<ModelInfo> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(Endpoints.models(settings))
            .get()
            .apply { headers(settings).forEach { (k, v) -> addHeader(k, v) } }
            .build()
        val text = execute(req)
        val arr = JSONObject(text).optJSONArray("data") ?: JSONArray()
        val result = mutableListOf<ModelInfo>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (id.isBlank()) continue
            val effort = o.optJSONObject("effort")
            val levels = mutableListOf<String>()
            val levelsArr = effort?.optJSONArray("supported_levels")
            if (levelsArr != null) {
                for (j in 0 until levelsArr.length()) {
                    val lv = levelsArr.optString(j)
                    if (lv.isNotBlank()) levels.add(lv)
                }
            }
            val modalities = mutableListOf<String>()
            val modArr = o.optJSONArray("input_modalities")
            if (modArr != null) {
                for (j in 0 until modArr.length()) modalities.add(modArr.optString(j))
            }
            result.add(
                ModelInfo(
                    id = id,
                    name = o.optString("name").ifBlank { id },
                    contextWindow = o.optInt("context_window").takeIf { it > 0 },
                    maxOutput = o.optInt("max_output_tokens").takeIf { it > 0 },
                    efforts = levels,
                    defaultEffort = effort?.optString("default_level")?.takeIf { it.isNotBlank() },
                    imageInput = modalities.contains("image"),
                )
            )
        }
        result
    }

    suspend fun balance(settings: AppSettings): Balance = withContext(Dispatchers.IO) {
        val url = Endpoints.balance(settings)
            ?: throw ApiException("当前网关不提供余额接口，请在 OpenCode Console 查看用量")
        val req = Request.Builder()
            .url(url)
            .get()
            .apply { headers(settings).forEach { (k, v) -> addHeader(k, v) } }
            .build()
        val o = JSONObject(execute(req))
        val infos = mutableListOf<BalanceInfo>()
        val arr = o.optJSONArray("balance_infos") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val b = arr.optJSONObject(i) ?: continue
            infos += BalanceInfo(
                currency = b.optString("currency", "CNY"),
                total = b.optString("total_balance", "0"),
                granted = b.optString("granted_balance", "0"),
                toppedUp = b.optString("topped_up_balance", "0"),
            )
        }
        Balance(available = o.optBoolean("is_available", false), infos = infos)
    }

    suspend fun uploadFile(
        settings: AppSettings,
        file: File,
        mime: String,
        purpose: String?,
    ): String = withContext(Dispatchers.IO) {
        val url = Endpoints.files(settings)
            ?: throw ApiException("当前网关不支持文件上传（Files API 由 DeepSeek 官方提供）")
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
        if (!purpose.isNullOrBlank()) builder.addFormDataPart("purpose", purpose)
        builder.addFormDataPart(
            "file",
            file.name,
            file.asRequestBody((mime.ifBlank { "application/octet-stream" }).toMediaType())
        )
        val req = Request.Builder()
            .url(url)
            .post(builder.build())
            .apply {
                headers(settings, beta = true).forEach { (k, v) -> addHeader(k, v) }
                header("Accept", "application/json")
            }
            .build()
        val text = execute(req)
        JSONObject(text).optString("id").ifBlank {
            throw ApiException("上传成功但未返回 file_id")
        }
    }

    suspend fun tts(settings: AppSettings, text: String, voice: String, target: File): File =
        withContext(Dispatchers.IO) {
            val url = Endpoints.voice(settings, "tts")
                ?: throw ApiException("当前网关不提供语音合成，请切换到 DeepSeek 官方接口")
            val body = JSONObject()
                .put("text", text)
                .put("voice_id", voice)
                .put("format", "wav")
                .put("filter_markdown", true)
                .toString()
            val req = Request.Builder()
                .url(url)
                .post(body.toRequestBody(jsonMedia))
                .header("Authorization", "Bearer ${settings.apiKey}")
                .header("Content-Type", "application/json")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string().orEmpty()
                    throw ApiException(errorMessage(err, resp.code), resp.code)
                }
                val bytes = resp.body?.bytes() ?: throw ApiException("语音合成返回为空")
                target.parentFile?.mkdirs()
                target.writeBytes(bytes)
            }
            target
        }

    suspend fun asr(settings: AppSettings, wav: File): String = withContext(Dispatchers.IO) {
        val url = Endpoints.voice(settings, "asr")
            ?: throw ApiException("当前网关不提供语音识别，请切换到 DeepSeek 官方接口")
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", wav.name, wav.asRequestBody("audio/wav".toMediaType()))
            .build()
        val req = Request.Builder()
            .url(url)
            .post(body)
            .header("Authorization", "Bearer ${settings.apiKey}")
            .build()
        JSONObject(execute(req)).optString("text")
    }

    /**
     * 流式请求。返回逐条 SSE 事件；[build] 在收到响应后由协议适配层逐事件解析。
     * 网络中断、非 2xx、协议异常都以 ApiException 抛出。
     */
    fun stream(url: String, headers: Map<String, String>, payload: String): Flow<Sse> = flow {
        val req = Request.Builder()
            .url(url)
            .post(payload.toRequestBody(jsonMedia))
            .apply {
                headers.forEach { (k, v) -> addHeader(k, v) }
                header("Accept", "text/event-stream")
                header("Cache-Control", "no-cache")
            }
            .build()

        val call = client.newCall(req)
        activeCall = call
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) {
                    val text = resp.body?.string().orEmpty()
                    throw ApiException(errorMessage(text, resp.code), resp.code)
                }
                val source = resp.body?.source() ?: throw ApiException("响应为空")
                var event: String? = null
                val data = StringBuilder()
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    when {
                        line.isEmpty() -> {
                            if (data.isNotEmpty()) {
                                emit(Sse(event, data.toString()))
                                data.clear()
                                event = null
                            }
                        }

                        line.startsWith("event:") -> event = line.substring(6).trim()
                        line.startsWith("data:") -> {
                            val v = line.substring(5).trimStart()
                            if (data.isNotEmpty()) data.append('\n')
                            data.append(v)
                        }

                        line.startsWith(":") -> Unit // 注释 / 保活
                    }
                }
                if (data.isNotEmpty()) emit(Sse(event, data.toString()))
            }
        } finally {
            if (activeCall === call) activeCall = null
        }
    }.flowOn(Dispatchers.IO)
}
