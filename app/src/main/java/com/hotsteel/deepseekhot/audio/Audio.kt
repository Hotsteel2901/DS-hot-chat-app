// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Hotsteel2901

package com.hotsteel.deepseekhot.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
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
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 16 kHz / 16 bit / 单声道录音，直接封装成 WAV，满足 ASR 接口要求。
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
        } catch (t: Throwable) {
            return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return false
        }

        stopRequested = false
        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.wav")
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
                        for (i in 0 until n) {
                            val v = buf[i]
                            sum += v.toDouble() * v
                            pcm.write(v.toInt() and 0xFF)
                            pcm.write((v.toInt() shr 8) and 0xFF)
                        }
                        written += n * 2L
                        val rms = sqrt(sum / n) / Short.MAX_VALUE
                        amplitude = (rms * 3.2).toFloat().coerceIn(0f, 1f)
                        elapsedMs = System.currentTimeMillis() - started
                        if (elapsedMs >= MAX_MS || written >= MAX_BYTES) break
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
        val wav = File(pcmFile.parentFile, pcmFile.nameWithoutExtension + "_wav.wav")
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
        job?.cancel()
        job = null
        runCatching { record?.stop() }
        runCatching { record?.release() }
        record = null
        recording = false
        amplitude = 0f
        out?.delete()
        out = null
    }
}

class TtsPlayer {

    private var player: MediaPlayer? = null

    var playingId by mutableStateOf<String?>(null)
        private set
    var preparing by mutableStateOf(false)
        private set

    fun play(file: File, messageId: String, onDone: () -> Unit = {}) {
        stop()
        preparing = true
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            mp.setDataSource(file.absolutePath)
            mp.setOnPreparedListener {
                preparing = false
                playingId = messageId
                it.start()
            }
            mp.setOnCompletionListener {
                playingId = null
                onDone()
                runCatching { it.release() }
                if (player === it) player = null
            }
            mp.setOnErrorListener { p, _, _ ->
                playingId = null
                preparing = false
                runCatching { p.release() }
                if (player === p) player = null
                true
            }
            mp.prepareAsync()
            player = mp
        } catch (t: Throwable) {
            preparing = false
            runCatching { mp.release() }
            player = null
        }
    }

    fun stop() {
        preparing = false
        playingId = null
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
    }
}
