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
import com.nexa.core.model.NoteSource
import com.nexa.core.model.Priority
import com.nexa.domain.CalendarEventRepository
import com.nexa.domain.NoteRepository
import com.nexa.domain.ReminderRepository
import com.nexa.domain.TaskRepository
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.first
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
    private val taskRepository: TaskRepository,
    private val reminderRepository: ReminderRepository,
    private val noteRepository: NoteRepository,
    private val eventRepository: CalendarEventRepository,
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
                val localReply = tryLocalAction(clean)
                if (localReply != null) {
                    appendLocalConversation(clean, localReply)
                } else {
                    val reply = "I can work with the commands built into NEXA. Try “add task…”, “take a note…”, “remind me to… at 6 pm”, “what are my tasks”, or open Settings → Automations to create a recurring rule."
                    appendLocalConversation(clean, reply)
                }
            } catch (e: Exception) {
                _error.value = friendlyError(e.message)
            } finally {
                _busy.value = false
            }
        }
    }


    private suspend fun tryLocalAction(input: String): String? {
        val value = input.trim()
        val normalized = value.lowercase(Locale.ROOT)
        if (normalized == "what time is it" || normalized == "what is the time" || normalized == "time") {
            return "It’s " + LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a")) + "."
        }
        if (normalized == "what is today's date" || normalized == "what's today's date" || normalized == "date") {
            return "Today is " + java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")) + "."
        }
        if (normalized.contains("what are my tasks") || normalized.contains("show my tasks") || normalized == "my tasks") {
            val tasks = taskRepository.observeAllTasks().first()
            return if (tasks.isEmpty()) "You don’t have any tasks yet." else
                tasks.take(8).joinToString("\n") { task ->
                    "• " + task.title + if (task.status.name == "COMPLETED") " — completed" else ""
                }
        }
        if (normalized.startsWith("add task ") || normalized.startsWith("create task ")) {
            val title = value.substringAfter("task ", "").trim()
            if (title.isBlank()) return "Tell me what task you want to add."
            taskRepository.create(title, Priority.NONE, null)
            return "Done — I added “" + title + "” to your tasks."
        }
        if (normalized.startsWith("take a note ") || normalized.startsWith("add note ") || normalized.startsWith("note ")) {
            val body = when {
                normalized.startsWith("take a note ") -> value.substring(12).trim()
                normalized.startsWith("add note ") -> value.substring(9).trim()
                else -> value.substring(5).trim()
            }
            if (body.isBlank()) return "Tell me what you want me to write in the note."
            noteRepository.create(null, body, NoteSource.TEXT, null)
            return "Done — I saved that as a note."
        }
        if (normalized.startsWith("remind me to ") || normalized.startsWith("set a reminder ")) {
            val prefix = if (normalized.startsWith("remind me to ")) "remind me to " else "set a reminder "
            val raw = value.substring(prefix.length).trim()
            val atMatch = Regex("(.+?)\\s+at\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?$", RegexOption.IGNORE_CASE).matchEntire(raw)
            if (atMatch == null) return "I can set that reminder, but include a time like “at 6:30 pm”."
            val title = atMatch.groupValues[1].trim()
            var hour = atMatch.groupValues[2].toInt()
            val minute = atMatch.groupValues[3].ifBlank { "0" }.toInt()
            val meridiem = atMatch.groupValues[4]
            if (meridiem.isNotBlank()) {
                if (meridiem.equals("pm", true) && hour < 12) hour += 12
                if (meridiem.equals("am", true) && hour == 12) hour = 0
            }
            val now = LocalDateTime.now()
            var target = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
            if (!target.isAfter(now)) target = target.plusDays(1)
            reminderRepository.create(title, target.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault().id, null)
            return "Done — I set “" + title + "” for " + target.format(DateTimeFormatter.ofPattern("EEE, MMM d • h:mm a")) + "."
        }
        if (normalized == "plan my day") {
            val tasks = taskRepository.observeAllTasks().first().filter { it.status.name == "OPEN" }.take(5)
            return if (tasks.isEmpty()) "Your day is clear — you have no open tasks yet." else "Here are your open tasks:\n" + tasks.joinToString("\n") { "• " + it.title }
        }
        if (normalized.startsWith("add event ")) {
            return "I can add events from the Organizer right now. Open Organizer → Events and tap Event to choose the title, time and location."
        }
        if (normalized == "help" || normalized == "what can you do") {
            return "I can manage your tasks, reminders, notes and events with built-in commands — no AI model is required for those actions."
        }
        return null
    }

    private fun appendLocalConversation(userText: String, reply: String) {
        _reply.value = reply
        _transcript.value = null
        _messages.value = _messages.value +
            AiMessage("local-user-" + System.nanoTime(), "user", userText) +
            AiMessage("local-assistant-" + System.nanoTime(), "assistant", reply)
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
