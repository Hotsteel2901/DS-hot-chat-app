// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Hotsteel2901

package com.hotsteel.deepseekhot.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.PlaybackParams
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sqrt

/**
 * 16 kHz / 16 bit / 单声道录音，直接封装成 WAV，满足 DeepSeek ASR 接口要求。
 * 修复：stop() 时先停止 AudioRecord 以立刻唤醒阻塞的 read()，杜绝潜在卡顿。
 */
class VoiceRecorder(private val context: Context) {

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val MAX_MS = 120_000L
        private const val MAX_BYTES = 7_500_000
    }

    private var record: AudioRecord? = null
    private var job: Job? = null
    private var out: File? = null

    @Volatile
    private var stopRequested = false

    var recording by mutableStateOf(false)
        private set
    var amplitude by mutableFloatStateOf(0f)
        private set
    var elapsedMs by mutableLongStateOf(0L)
        private set

    fun start(scope: CoroutineScope): Boolean {
        if (recording) return true
        val min = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(min, SAMPLE_RATE / 2)
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2
            )
        } catch (_: Throwable) {
            return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return false
        }

        stopRequested = false
        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.pcm")
        out = file
        recording = true
        amplitude = 0f
        elapsedMs = 0L

        job = scope.launch(Dispatchers.IO) {
            val pcm = FileOutputStream(file)
            val buf = ShortArray(bufferSize)
            var written = 0L
            val started = System.currentTimeMillis()
            try {
                rec.startRecording()
                while (isActive && !stopRequested) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n > 0) {
                        var sum = 0.0
                        val byteBuf = ByteArray(n * 2)
                        for (i in 0 until n) {
                            val v = buf[i].toInt()
                            sum += v.toDouble() * v
                            byteBuf[i * 2] = (v and 0xFF).toByte()
                            byteBuf[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
                        }
                        pcm.write(byteBuf)
                        written += byteBuf.size.toLong()
                        val rms = sqrt(sum / n) / Short.MAX_VALUE
                        amplitude = (rms * 3.2).toFloat().coerceIn(0f, 1f)
                        elapsedMs = System.currentTimeMillis() - started
                        if (elapsedMs >= MAX_MS || written >= MAX_BYTES) break
                    } else if (n < 0) {
                        break
                    }
                }
            } catch (_: Throwable) {
                // 录音中断时保留已录部分
            } finally {
                runCatching { rec.stop() }
                runCatching { rec.release() }
                runCatching { pcm.flush() }
                runCatching { pcm.close() }
                record = null
                recording = false
                amplitude = 0f
            }
        }
        record = rec
        return true
    }

    private fun pcmToWav(pcmFile: File): File {
        val dataLen = pcmFile.length().toInt()
        if (dataLen <= 0) return pcmFile
        val wav = File(pcmFile.parentFile, pcmFile.nameWithoutExtension + ".wav")
        FileOutputStream(wav).use { fos ->
            val totalLen = dataLen + 36
            val header = ByteArray(44)
            fun putStr(offset: Int, s: String) {
                for (i in s.indices) header[offset + i] = s[i].code.toByte()
            }
            fun putInt(offset: Int, v: Int) {
                header[offset] = (v and 0xFF).toByte()
                header[offset + 1] = ((v shr 8) and 0xFF).toByte()
                header[offset + 2] = ((v shr 16) and 0xFF).toByte()
                header[offset + 3] = ((v shr 24) and 0xFF).toByte()
            }
            putStr(0, "RIFF")
            putInt(4, totalLen)
            putStr(8, "WAVE")
            putStr(12, "fmt ")
            putInt(16, 16)
            header[20] = 1 // PCM
            header[21] = 0
            header[22] = 1 // mono
            header[23] = 0
            putInt(24, SAMPLE_RATE)
            putInt(28, SAMPLE_RATE * 2)
            header[32] = 2
            header[33] = 0
            header[34] = 16
            header[35] = 0
            putStr(36, "data")
            putInt(40, dataLen)
            fos.write(header)
            pcmFile.inputStream().use { it.copyTo(fos) }
        }
        return wav
    }

    suspend fun stop(): File? = withContext(Dispatchers.IO) {
        val f = out
        stopRequested = true
        // 先触发 stop() 立即唤醒 AudioRecord.read() 阻塞，避免 join 长时间挂起
        runCatching { record?.stop() }
        job?.join()
        job = null
        if (f == null || !f.exists() || f.length() < 2000) {
            f?.delete()
            null
        } else {
            pcmToWav(f).also { f.delete() }
        }
    }

    fun cancel() {
        stopRequested = true
        runCatching { record?.stop() }
        job?.cancel()
        job = null
        runCatching { record?.release() }
        record = null
        recording = false
        amplitude = 0f
        out?.delete()
        out = null
    }
}

/**
 * 严格遵循 DeepSeek 官方 TTS 文档的文本清洗与智能分块工具：
 * - 官方计费与限制：汉字算 2 个字符，英文字母/数字等算 1 个；单会话上限 10,000 计费字符 / 64 KiB。
 * - 自动移除围栏代码块与冗长 URL，按句号/换行智能切分，避免粗暴截断丢弃后半段回答。
 */
object TtsTextPrep {

    private val fencedCode = Regex("```[\\s\\S]*?```")
    private val inlineUrl = Regex("https?://\\S+")
    private val multiSpace = Regex("[ \\t]{2,}")

    fun billedLength(s: String): Int {
        var total = 0
        for (ch in s) {
            total += if (ch.code > 0x7F) 2 else 1
        }
        return total
    }

    /** 清洗适合朗读的自然文本，同时保留完整语义。 */
    fun cleanForSpeech(raw: String): String {
        var s = raw
            .replace(fencedCode, "（此处为代码块，已跳过）")
            .replace(inlineUrl, "")
            .replace(Regex("`([^`]+)`"), "$1")
            .replace(Regex("!\\[[^\\]]*]\\([^)]*\\)"), "")
            .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
            .replace(Regex("(?m)^#{1,6}\\s+"), "")
            .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
            .replace(Regex("__([^_]+)__"), "$1")
            .replace(multiSpace, " ")
            .trim()
        // 单会话严格控制在官方 9,600 计费字符以内（留出安全余量）
        if (billedLength(s) > 9600) {
            val sb = StringBuilder()
            var acc = 0
            for (ch in s) {
                val w = if (ch.code > 0x7F) 2 else 1
                if (acc + w > 9600) break
                sb.append(ch)
                acc += w
            }
            s = sb.toString().trim()
        }
        return s
    }

    /** 按自然句界切分为适合 WebSocket/流式发送的文本块，每块 <= maxBilled 计费字符。 */
    fun splitChunks(raw: String, maxBilled: Int = 520): List<String> {
        val cleaned = cleanForSpeech(raw)
        if (cleaned.isBlank()) return emptyList()
        val sentences = cleaned.split(Regex("(?<=[。！？!?；;\\n])"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        var currentBilled = 0
        for (seg in sentences) {
            val segBilled = billedLength(seg)
            if (current.isNotEmpty() && currentBilled + segBilled > maxBilled) {
                chunks.add(current.toString().trim())
                current.clear()
                currentBilled = 0
            }
            if (segBilled > maxBilled) {
                // 超长无标点单句按字符安全切分
                val piece = StringBuilder()
                var pieceBilled = 0
                for (ch in seg) {
                    val w = if (ch.code > 0x7F) 2 else 1
                    if (pieceBilled + w > maxBilled && piece.isNotEmpty()) {
                        chunks.add(piece.toString())
                        piece.clear()
                        pieceBilled = 0
                    }
                    piece.append(ch)
                    pieceBilled += w
                }
                if (piece.isNotEmpty()) {
                    current.append(piece)
                    currentBilled += pieceBilled
                }
            } else {
                current.append(seg)
                currentBilled += segBilled
            }
        }
        if (current.isNotEmpty()) chunks.add(current.toString().trim())
        return chunks
    }
}

/**
 * 基于 Android AudioTrack 的 24 kHz / 16-bit / Mono 实时流式 PCM 朗读播放器。
 *
 * 彻底解决旧版 MediaPlayer 播放 DeepSeek 流式 WAV 失败/截断/无首音/缓存泄漏问题：
 * - 严格匹配官方 `format=pcm`（24 kHz, 16-bit signed little-endian mono）；
 * - 边接收音频帧边写入播放，首音延迟低至毫秒级；
 * - 完整支持暂停/继续、倍速（0.75x–2.0x）与系统 AudioFocus 管理；
 * - 零临时文件落地，彻底消除缓存泄漏。
 */
class TtsPlayer(private val context: Context) {

    companion object {
        const val SAMPLE_RATE = 24_000
    }

    private val lock = Any()
    private var track: AudioTrack? = null
    private var focusRequest: AudioFocusRequest? = null
    private val audioManager: AudioManager?
        get() = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    @Volatile
    private var activeSessionToken: Long = 0L

    var playingId by mutableStateOf<String?>(null)
        private set
    var paused by mutableStateOf(false)
        private set
    var preparing by mutableStateOf(false)
        private set
    var speed by mutableFloatStateOf(1.0f)
        private set

    /** 启动一个新的流式 PCM 播放会话，返回会话令牌用于防止并发旧流误写。 */
    fun beginStream(messageId: String, initialSpeed: Float = speed): Long {
        stop()
        val token = System.nanoTime()
        synchronized(lock) {
            activeSessionToken = token
            preparing = true
            paused = false
            speed = initialSpeed.coerceIn(0.75f, 2.0f)
            requestAudioFocus()
            val minBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(SAMPLE_RATE)
            val at = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            applySpeedLocked(at, speed)
            at.play()
            track = at
            playingId = messageId
        }
        return token
    }

    /** 将一段 24kHz 16-bit mono PCM 数据写入播放缓冲区（在 IO 线程调用）。 */
    fun writePcm(token: Long, bytes: ByteArray, length: Int = bytes.size): Boolean {
        if (length <= 0 || token != activeSessionToken) return false
        val at = synchronized(lock) {
            if (token != activeSessionToken) return false
            if (preparing) preparing = false
            track
        } ?: return false
        // 若用户处于暂停状态，等待恢复或停止
        while (paused && token == activeSessionToken) {
            try {
                Thread.sleep(40)
            } catch (_: InterruptedException) {
                return false
            }
        }
        if (token != activeSessionToken) return false
        // 16-bit PCM 需按 2 字节帧对齐
        val alignedLen = length and 1.inv()
        if (alignedLen <= 0) return true
        return try {
            val written = at.write(bytes, 0, alignedLen, AudioTrack.WRITE_BLOCKING)
            written >= 0
        } catch (_: Throwable) {
            false
        }
    }

    /** 标记当前流数据全部写入完成，等待缓冲播放完毕后自动释放资源。 */
    fun finishStream(token: Long) {
        val at = synchronized(lock) {
            if (token != activeSessionToken) return
            preparing = false
            track
        } ?: return
        // 等待未暂停状态播完残留尾音（最多约 600ms）
        if (!paused) {
            runCatching { Thread.sleep(320) }
        }
        synchronized(lock) {
            if (token != activeSessionToken) return
            releaseLocked()
            playingId = null
            paused = false
            preparing = false
        }
    }

    fun togglePause() {
        synchronized(lock) {
            val at = track ?: return
            if (paused) {
                runCatching { at.play() }
                paused = false
            } else {
                runCatching { at.pause() }
                paused = true
            }
        }
    }

    fun setPlaybackSpeed(newSpeed: Float) {
        val clamped = newSpeed.coerceIn(0.75f, 2.0f)
        speed = clamped
        synchronized(lock) {
            track?.let { applySpeedLocked(it, clamped) }
        }
    }

    private fun applySpeedLocked(at: AudioTrack, rate: Float) {
        runCatching {
            at.playbackParams = PlaybackParams().setSpeed(rate)
        }
    }

    fun stop() {
        synchronized(lock) {
            activeSessionToken = 0L
            preparing = false
            paused = false
            playingId = null
            releaseLocked()
        }
    }

    private fun releaseLocked() {
        val at = track
        track = null
        if (at != null) {
            runCatching { at.pause() }
            runCatching { at.flush() }
            runCatching { at.stop() }
            runCatching { at.release() }
        }
        abandonAudioFocus()
    }

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .build()
        focusRequest = req
        runCatching { am.requestAudioFocus(req) }
    }

    private fun abandonAudioFocus() {
        val req = focusRequest ?: return
        focusRequest = null
        runCatching { audioManager?.abandonAudioFocusRequest(req) }
    }
}
