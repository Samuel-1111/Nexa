package com.nexa.domain

import com.nexa.core.model.Priority
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

class DeterministicCommandInterpreter : CommandInterpreter {
    override fun interpret(input: CommandInput): AssistantCommand {
        val raw = input.rawText.trim()
        val normalized = raw.lowercase(Locale.ROOT)
        if (normalized in setOf("what do i need to do today?", "what do i need to do today", "show today")) return QueryTodayCommand(raw)
        if (normalized in setOf("show my open tasks", "show open tasks")) return ListOpenTasksCommand(raw)
        if (normalized.startsWith("note ")) return CreateNoteCommand(raw, raw.drop(5).trim())
        if (normalized.startsWith("remember ")) return SuggestMemoryCommand(raw, raw.drop(9).trim())
        if (normalized.startsWith("add ")) {
            val title = raw.drop(4).trim()
            if (title.isBlank()) return ClarificationRequired(raw, "Tell me what task to add.")
            val priority = if (normalized.contains("high priority")) Priority.HIGH else Priority.NONE
            return CreateTaskCommand(raw, title.replace(Regex("(?i) as high priority"), "").trim(), priority)
        }
        reminder(raw)?.let { return it }
        return if (normalized.startsWith("plan ") || normalized.startsWith("draft ") || normalized.startsWith("help me ")) AiAssistanceRequired(raw) else UnsupportedCommand(raw)
    }

    private fun reminder(raw: String): AssistantCommand? {
        val match = Regex("(?i)^remind me to (.+) at (\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)$").matchEntire(raw) ?: return null
        val title = match.groupValues[1].trim()
        if (title.isBlank()) return ClarificationRequired(raw, "Tell me what to remind you about.")
        val hour12 = match.groupValues[2].toInt()
        val minute = match.groupValues[3].ifBlank { "0" }.toInt()
        if (hour12 !in 1..12 || minute !in 0..59) return ClarificationRequired(raw, "Use a valid time.")
        val hour24 = when (match.groupValues[4].lowercase(Locale.ROOT)) {
            "am" -> if (hour12 == 12) 0 else hour12
            else -> if (hour12 == 12) 12 else hour12 + 12
        }
        return CreateReminderCommand(raw, title, LocalDateTime.of(LocalDate.now(), LocalTime.of(hour24, minute)))
    }
}
