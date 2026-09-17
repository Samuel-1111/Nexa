package com.nexa.domain

import com.nexa.core.model.Priority
import java.time.LocalDateTime

data class CommandInput(val rawText: String, val source: CommandSource)
enum class CommandSource { TEXT, VOICE }

sealed interface AssistantCommand { val rawInput: String }
data class CreateTaskCommand(override val rawInput: String, val title: String, val priority: Priority = Priority.NONE) : AssistantCommand
data class CreateNoteCommand(override val rawInput: String, val body: String) : AssistantCommand
data class CreateReminderCommand(override val rawInput: String, val title: String, val triggerAt: LocalDateTime) : AssistantCommand
data class QueryTodayCommand(override val rawInput: String) : AssistantCommand
data class ListOpenTasksCommand(override val rawInput: String) : AssistantCommand
data class SuggestMemoryCommand(override val rawInput: String, val content: String) : AssistantCommand
data class ClarificationRequired(override val rawInput: String, val reason: String) : AssistantCommand
data class AiAssistanceRequired(override val rawInput: String) : AssistantCommand
data class UnsupportedCommand(override val rawInput: String) : AssistantCommand

fun interface CommandInterpreter {
    fun interpret(input: CommandInput): AssistantCommand
}
