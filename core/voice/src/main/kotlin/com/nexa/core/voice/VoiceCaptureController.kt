package com.nexa.core.voice

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

sealed interface VoiceCaptureState {
    data object Idle : VoiceCaptureState
    data object Listening : VoiceCaptureState
    data class Captured(val audioBase64: String, val mimeType: String = "audio/wav") : VoiceCaptureState
    data class Error(val message: String) : VoiceCaptureState
}

/**
 * Records short microphone clips locally and returns the bytes to the caller.
 * Raw audio is never persisted to disk. The caller decides whether to send it
 * to the authenticated Gemini gateway.
 */
class VoiceCaptureController {
    private val mutableState = MutableStateFlow<VoiceCaptureState>(VoiceCaptureState.Idle)
    val state: StateFlow<VoiceCaptureState> = mutableState

    private val scope = CoroutineScope(Dispatchers.IO)
    private var recorder: AudioRecord? = null
    private var recordingJob: Job? = null
    private val sampleRate = 16_000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val encoding = AudioFormat.ENCODING_PCM_16BIT

    fun start() {
        if (recordingJob?.isActive == true) return
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding)
        if (minBuffer <= 0) {
            mutableState.value = VoiceCaptureState.Error("Microphone is unavailable")
            return
        }

        try {
            recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                encoding,
                maxOf(minBuffer * 2, 4096),
            )
            recorder?.startRecording()
            mutableState.value = VoiceCaptureState.Listening

            recordingJob = scope.launch {
                val pcm = ByteArrayOutputStream()
                val buffer = ByteArray(maxOf(minBuffer, 4096))
                while (true) {
                    val count = recorder?.read(buffer, 0, buffer.size) ?: break
                    if (count > 0) pcm.write(buffer, 0, count)
                    if (recordingJob?.isActive != true) break
                }
                val audio = wavBytes(pcm.toByteArray(), sampleRate)
                if (audio.isNotEmpty()) {
                    mutableState.value = VoiceCaptureState.Captured(
                        Base64.encodeToString(audio, Base64.NO_WRAP),
                    )
                } else {
                    mutableState.value = VoiceCaptureState.Error("No speech was captured")
                }
            }
        } catch (_: SecurityException) {
            mutableState.value = VoiceCaptureState.Error("Microphone permission is required")
            release()
        } catch (t: Throwable) {
            mutableState.value = VoiceCaptureState.Error(t.message ?: "Unable to start microphone")
            release()
        }
    }

    fun stop() {
        recordingJob?.cancel()
        recordingJob = null
        try { recorder?.stop() } catch (_: Throwable) { }
        recorder?.release()
        recorder = null
    }

    fun release() = stop()

    private fun wavBytes(pcm: ByteArray, rate: Int): ByteArray {
        if (pcm.isEmpty()) return ByteArray(0)
        val out = ByteArrayOutputStream(44 + pcm.size)
        fun writeInt(value: Int) {
            out.write(value and 0xff)
            out.write((value shr 8) and 0xff)
            out.write((value shr 16) and 0xff)
            out.write((value shr 24) and 0xff)
        }
        fun writeShort(value: Int) {
            out.write(value and 0xff)
            out.write((value shr 8) and 0xff)
        }
        out.write("RIFF".toByteArray())
        writeInt(36 + pcm.size)
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray())
        writeInt(16)
        writeShort(1)
        writeShort(1)
        writeInt(rate)
        writeInt(rate * 2)
        writeShort(2)
        writeShort(16)
        out.write("data".toByteArray())
        writeInt(pcm.size)
        out.write(pcm)
        return out.toByteArray()
    }
}
