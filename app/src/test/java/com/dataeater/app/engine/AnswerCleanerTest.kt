package com.dataeater.app.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests that reasoning tags are removed from the answer.
 */
class AnswerCleanerTest {

    @Test
    fun `removes a complete thinking block`() {
        val raw = "<think>the user asks about pressure</think>Idle pressure is 380 bar."
        assertEquals("Idle pressure is 380 bar.", AnswerCleaner.clean(raw))
    }

    @Test
    fun `removes a thinking block spread over several lines`() {
        val raw = "<think>\nline one\nline two\n</think>\nThe answer is 45 Nm."
        assertEquals("The answer is 45 Nm.", AnswerCleaner.clean(raw))
    }

    @Test
    fun `keeps a normal answer unchanged`() {
        val raw = "Idle pressure is 380 bar at 850 rpm."
        assertEquals(raw, AnswerCleaner.clean(raw))
    }

    @Test
    fun `removes a stray opening tag with no closing tag`() {
        val raw = "<think>Idle pressure is 380 bar."
        assertEquals("Idle pressure is 380 bar.", AnswerCleaner.clean(raw))
    }

    @Test
    fun `handles more than one thinking block`() {
        val raw = "<think>a</think>First. <think>b</think>Second."
        assertEquals("First. Second.", AnswerCleaner.clean(raw))
    }

    @Test
    fun `does not remove the word think inside a normal sentence`() {
        val raw = "I do not think the value is 380 bar."
        assertEquals(raw, AnswerCleaner.clean(raw))
    }

    @Test
    fun `empty answer stays empty`() {
        assertEquals("", AnswerCleaner.clean(""))
    }
}