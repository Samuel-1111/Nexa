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

/** Short, memory-only microphone capture. Raw audio is never written to disk. */
class VoiceCaptureController {
    private val mutableState = MutableStateFlow<VoiceCaptureState>(VoiceCaptureState.Idle)
    val state: StateFlow<VoiceCaptureState> = mutableState

    private val scope = CoroutineScope(Dispatchers.IO)
    private var recorder: AudioRecord? = null
    private var recordingJob: Job? = null
    @Volatile private var stopRequested = false
    @Volatile private var discardRequested = false

    private val sampleRate = 16_000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val encoding = AudioFormat.ENCODING_PCM_16BIT
    private val maxRecordingBytes = sampleRate * 2 * 60 // 60 seconds, mono 16-bit

    fun start() {
        if (recordingJob?.isActive == true) return
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding)
        if (minBuffer <= 0) {
            mutableState.value = VoiceCaptureState.Error("Microphone is unavailable")
            return
        }

        stopRequested = false
        discardRequested = false
        try {
            val created = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                encoding,
                maxOf(minBuffer * 2, 4096),
            )
            if (created.state != AudioRecord.STATE_INITIALIZED) {
                created.release()
                mutableState.value = VoiceCaptureState.Error("Microphone is unavailable")
                return
            }
            recorder = created
            created.startRecording()
            mutableState.value = VoiceCaptureState.Listening

            recordingJob = scope.launch {
                val pcm = ByteArrayOutputStream()
                val buffer = ByteArray(maxOf(minBuffer, 4096))
                try {
                    while (!stopRequested && pcm.size() < maxRecordingBytes) {
                        val count = created.read(buffer, 0, buffer.size)
                        if (count > 0) pcm.write(buffer, 0, count)
                        else if (count < 0) break
                    }
                    val audio = wavBytes(pcm.toByteArray(), sampleRate)
                    if (!discardRequested && audio.isNotEmpty()) {
                        mutableState.value = VoiceCaptureState.Captured(
                            Base64.encodeToString(audio, Base64.NO_WRAP),
                        )
                    } else {
                        mutableState.value = VoiceCaptureState.Error("No speech was captured")
                    }
                } catch (_: Throwable) {
                    if (!stopRequested) mutableState.value = VoiceCaptureState.Error("Unable to capture audio")
                } finally {
                    runCatching { created.stop() }
                    runCatching { created.release() }
                    if (recorder === created) recorder = null
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
        stopRequested = true
        val active = recorder
        if (active != null) {
            runCatching { active.stop() }
        }
        // Do not cancel the capture job here: it must finish packaging the
        // captured PCM into WAV and emit Captured for the transcription flow.
    }

    fun clear() {
        discardRequested = true
        stopRequested = true
        recorder?.let { runCatching { it.stop() } }
        mutableState.value = VoiceCaptureState.Idle
    }

    fun release() {
        discardRequested = true
        stopRequested = true
        val active = recorder
        if (active != null) {
            runCatching { active.stop() }
            runCatching { active.release() }
        }
        recorder = null
        recordingJob?.cancel()
        recordingJob = null
    }

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