package com.nexa.feature.assistant

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexa.core.network.AiGatewayClient
import com.nexa.core.network.AiChatSummary
import com.nexa.core.network.AiMessage
import com.nexa.core.network.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val aiGateway: AiGatewayClient,
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _assistantName = MutableStateFlow("NEXA")
    val assistantName: StateFlow<String> = _assistantName.asStateFlow()
    private val _reply = MutableStateFlow<String?>(null)
    private val _messages = MutableStateFlow<List<AiMessage>>(emptyList())
    val messages: StateFlow<List<AiMessage>> = _messages.asStateFlow()
    private val _chats = MutableStateFlow<List<AiChatSummary>>(emptyList())
    val chats: StateFlow<List<AiChatSummary>> = _chats.asStateFlow()
    val reply: StateFlow<String?> = _reply.asStateFlow()
    private val _transcript = MutableStateFlow<String?>(null)
    val transcript: StateFlow<String?> = _transcript.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private var chatId: String? = null

    init { viewModelScope.launch { _assistantName.value = authRepository.currentAssistantName() ?: "NEXA"; _chats.value = aiGateway.listChats(); _chats.value.firstOrNull()?.let { selectChat(it.id) } } }

    fun ask(message: String) {
        val clean = message.trim()
        if (clean.isEmpty() || _busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            try {
                val result = aiGateway.sendMessage(clean, chatId, speak = true)
                chatId = result.chat_id ?: chatId
                if (!result.error.isNullOrBlank()) _error.value = friendlyError(result.error)
                else {
                    _reply.value = result.reply.ifBlank { "I’m here. Tell me what you need." }
                    _transcript.value = null
                    _messages.value = _messages.value + AiMessage("local-user-" + System.nanoTime(), "user", clean) + AiMessage("local-assistant-" + System.nanoTime(), "assistant", result.reply.ifBlank { "I’m here. Tell me what you need." })
                    _chats.value = aiGateway.listChats()
                    result.audio_base64?.let { encoded -> viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { playPcm(encoded) } }
                }
            } catch (e: Exception) {
                _error.value = friendlyError(e.message)
            } finally {
                _busy.value = false
            }
        }
    }

    fun transcribe(audioBase64: String, mimeType: String) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            try {
                val result = aiGateway.sendVoice(audioBase64 = audioBase64, mimeType = mimeType, speak = false)
                if (!result.error.isNullOrBlank()) _error.value = friendlyError(result.error)
                else {
                    val text = result.transcript.orEmpty().trim()
                    if (text.isBlank()) _error.value = "I couldn’t hear that. Please try again."
                    else _transcript.value = text
                }
            } catch (e: Exception) {
                _error.value = friendlyError(e.message)
            } finally {
                _busy.value = false
            }
        }
    }

    private fun playPcm(encoded: String) {
        runCatching {
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            val min = AudioTrack.getMinBufferSize(24000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(24000).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(maxOf(min, bytes.size))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(bytes, 0, bytes.size)
            track.play()
            Thread.sleep((bytes.size / 48L).coerceAtLeast(300L))
            track.stop()
            track.release()
        }
    }

    fun newChat() {
        chatId = null
        _reply.value = null
        _messages.value = emptyList()
        _transcript.value = null
        _error.value = null
    }

    fun selectChat(id: String) {
        chatId = id
        viewModelScope.launch {
            _messages.value = aiGateway.loadMessages(id)
            _reply.value = _messages.value.lastOrNull { it.role == "assistant" }?.content
        }
    }

    fun clearTranscript() { _transcript.value = null }
    fun clearError() { _error.value = null }

    private fun friendlyError(raw: String?): String {
        val value = raw.orEmpty().lowercase()
        return when {
            "unauthorized" in value || "unauthenticated" in value -> "Your session has expired. Please log in again."
            "usage_limit" in value || "monthly_limit" in value -> "You’ve reached your current NEXA usage limit."
            "subscription" in value -> "Your NEXA plan needs attention before I can do that."
            "network" in value || "timeout" in value || "connection" in value -> "Check your internet connection and try again."
            "transcription" in value || "empty_transcript" in value -> "I couldn’t understand the voice recording. Please try again."
            else -> "NEXA couldn’t complete that. Please try again."
        }
    }
}
