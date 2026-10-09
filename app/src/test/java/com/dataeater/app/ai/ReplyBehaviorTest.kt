package com.dataeater.app.ai

import com.dataeater.app.chat.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReplyBehaviorTest {
    @Test fun everyLengthChangesApiBudgetAndInstructionWithoutLosingSafety() {
        for (length in ReplyLength.entries) {
            val behavior = ReplyBehavior(length = length)
            val body = JSONObject(OpenRouterProtocol.request("test/free", "Question", AnswerMode.DATABASE, emptyList(), behavior))
            assertEquals(length.maxTokens, body.getInt("max_tokens"))
            val instruction = body.getJSONArray("messages").getJSONObject(0).getString("content")
            assertTrue(instruction.contains(length.instruction))
            assertTrue(instruction.contains("only the current REFERENCE TEXT"))
            assertTrue(instruction.contains("Never invent technical values"))
        }
        assertTrue(ReplyLength.SHORT.maxTokens < ReplyLength.NORMAL.maxTokens)
        assertTrue(ReplyLength.NORMAL.maxTokens < ReplyLength.DETAILED.maxTokens)
    }
    @Test fun groundingAndLengthAreIndependent() {
        for (strictness in DatabaseStrictness.entries) for (length in ReplyLength.entries) {
            val b = ReplyBehavior(strictness, length)
            val db = b.instruction(AnswerMode.DATABASE)
            assertTrue(db.contains(length.instruction)); assertTrue(db.contains("Never invent technical values"))
            val general = b.instruction(AnswerMode.GENERAL_KNOWLEDGE)
            assertFalse(general.contains("only the current REFERENCE TEXT"))
            assertTrue(general.contains("general knowledge"))
        }
        assertTrue(ReplyBehavior(DatabaseStrictness.BALANCED).instruction(AnswerMode.DATABASE).contains("Do not add outside facts"))
        assertTrue(ReplyBehavior(DatabaseStrictness.FLEXIBLE).instruction(AnswerMode.DATABASE).contains("General knowledge (not in the database)"))
    }
    @Test fun relaxedHistoryCannotBecomeStrictEvidence() {
        val strict = ReplyBehavior().memoryContext("online:db")
        val flexible = ReplyBehavior(DatabaseStrictness.FLEXIBLE).memoryContext("online:db")
        val history = listOf(ChatMessage("u", true, "Question", contextKey = flexible), ChatMessage("a", false, "Answer", sources = listOf("s"), mayIncludeGeneralKnowledge = true, contextKey = flexible))
        assertTrue(ConversationMemory.select(history, true, strict, 1000).isEmpty())
        assertEquals(2, ConversationMemory.select(history, true, flexible, 1000).size)
    }
    @Test fun supplementWarningSurvivesPersistenceAndCopy() {
        val message = ChatMessage("a", false, "Database fact and background", sources = listOf("Manual page 1"), mayIncludeGeneralKnowledge = true)
        val session = Session("s", "test", messages = listOf(message))
        val decoded = SessionCodec.decodeSession(SessionCodec.encodeSession(session))!!
        assertTrue(decoded.messages.single().mayIncludeGeneralKnowledge)
        assertTrue(MessageCopy.forMessage(decoded.messages.single()).contains("outside the database"))
        assertTrue(MessageCopy.forMessage(decoded.messages.single()).contains("Manual page 1"))
    }
}
