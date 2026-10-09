package com.dataeater.app.chat
import com.dataeater.app.engine.*
import com.dataeater.app.model.*
import org.junit.Assert.*
import org.junit.Test

class ConversationMemoryTest {
    private fun turn(key:String, text:String="Fuel filter") = listOf(
        ChatMessage("u",true,text,contextKey=key),
        ChatMessage("a",false,"Replace every year.",sources=listOf("manual page 1"),contextKey=key))
    @Test fun memoryStaysWithinCurrentDatabaseAndBudget() {
        val history=turn("A")+turn("B")+turn("A","x".repeat(2000))
        assertTrue(ConversationMemory.select(history,true,"A",400).isEmpty())
        assertEquals(turn("A"),ConversationMemory.select(turn("B")+turn("A"),true,"A",400))
    }
    @Test fun incompleteAndRefusalTurnsAreExcluded() {
        val turn=turn("A")
        assertTrue(ConversationMemory.select(turn.dropLast(1)+turn.last().copy(incomplete=true),true,"A",400).isEmpty())
        assertTrue(ConversationMemory.select(turn.dropLast(1)+turn.last().copy(databaseHadNoAnswer=true),true,"A",400).isEmpty())
    }
    @Test fun followupSearchUsesPreviousSubjectAndFreshQuestionDoesNot() {
        val history=turn("A","Tell me about the fuel filter.")
        assertTrue(ConversationMemory.retrievalQuestion("How often should I replace it?",history).contains("fuel filter"))
        assertEquals("What is oil pressure?",ConversationMemory.retrievalQuestion("What is oil pressure?",history))
        val engine=RagEngine(listOf(Chunk("c","s","Fuel filter",1,"Replace the fuel filter every 12 months.")),listOf(SourceInfo("s","Manual","Jack",2026)))
        val decision=ChatResponder(engine,"Manual","A").decide("How often should I replace it?",history,true,true)
        assertTrue(decision is ChatResponder.Decision.FromDatabase)
        assertTrue((decision as ChatResponder.Decision.FromDatabase).prompt.contains("12 months"))
    }
    @Test fun shortUserDeclarationSurvivesRecentTurnWindow() {
        val remembered=listOf(ChatMessage("code-u",true,"My code is AZ-17.",contextKey="general"),ChatMessage("code-a",false,"OK.",answeredWithoutDatabase=true,contextKey="general"))
        val later=(1..12).flatMap { listOf(ChatMessage("u$it",true,"Question $it",contextKey="general"),ChatMessage("a$it",false,"Answer $it",answeredWithoutDatabase=true,contextKey="general")) }
        val memory=ConversationMemory.select(remembered+later,false,"general",800)
        assertTrue(memory.any { it.text.contains("AZ-17") })
        assertTrue(memory.sumOf { it.text.length + 24 } <= 800)
    }
    @Test fun scopeSurvivesSaveAndOldSessionsStillRead() {
        val original=Session("s","test",messages=turn("A"))
        assertEquals("A",SessionCodec.decodeSession(SessionCodec.encodeSession(original))!!.messages.first().contextKey)
        val old=SessionCodec.encodeSession(original).replace("\"context_key\":\"A\",","").replace(",\"context_key\":\"A\"","")
        assertNotNull(SessionCodec.decodeSession(old))
    }
}
