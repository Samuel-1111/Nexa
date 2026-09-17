package com.nexa.domain

import com.nexa.core.model.Priority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertIs
import org.junit.Test

class DeterministicCommandInterpreterTest {
    private val interpreter = DeterministicCommandInterpreter()

    @Test
    fun `high priority task is interpreted locally`() {
        val result = interpreter.interpret(CommandInput("Add submit proposal as high priority", CommandSource.TEXT))

        assertIs<CreateTaskCommand>(result)
        result as CreateTaskCommand
        assertEquals("submit proposal", result.title)
        assertEquals(Priority.HIGH, result.priority)
    }

    @Test
    fun `voice note uses the same deterministic command path`() {
        val result = interpreter.interpret(CommandInput("Note client prefers short updates", CommandSource.VOICE))

        assertIs<CreateNoteCommand>(result)
        assertEquals("client prefers short updates", (result as CreateNoteCommand).body)
    }

    @Test
    fun `planning is deferred to AI`() {
        val result = interpreter.interpret(CommandInput("Plan my afternoon", CommandSource.TEXT))

        assertIs<AiAssistanceRequired>(result)
    }
}
