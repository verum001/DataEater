package com.dataeater.app.chat

import com.dataeater.app.engine.RagEngine
import com.dataeater.app.model.Chunk
import com.dataeater.app.model.SourceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deciding what to do with a typed question.
 *
 * THE CASES THAT MATTER MOST
 * --------------------------
 * Two of these tests exist to stop a specific lie:
 *
 *  1. When the database is on and holds nothing, the model must **not** be
 *     asked. Asking it would produce a confident invented answer that is
 *     indistinguishable from a sourced one. This is the failure the whole app
 *     is built to prevent.
 *
 *  2. When the database is off, the answer must be marked as unchecked. A user
 *     relying on a torque value has to be able to see, on the answer itself,
 *     that nobody checked it.
 *
 * These are cheap to test and expensive to get wrong, so they are tested here
 * rather than on a phone.
 */
class ChatResponderTest {

    // --- a small database, so retrieval is predictable -------------------

    private val sources = listOf(
        SourceInfo(
            id = "manual",
            title = "HF-4500 Service Manual",
            publisher = "DataEater",
            year = 2026,
        )
    )

    private val chunks = listOf(
        Chunk(
            id = "c1",
            sourceId = "manual",
            section = "3.1 Fuel Pressure",
            page = 11,
            text = "The idle rail pressure is 380 bar at 850 rpm.",
        ),
        Chunk(
            id = "c2",
            sourceId = "manual",
            section = "4.2 Coolant",
            page = 19,
            text = "Drain the coolant at the radiator cap.",
        ),
    )

    private val engine = RagEngine(chunks, sources)

    private fun responder(dbName: String = "HF-4500") =
        ChatResponder(engine, dbName)

    private fun decide(
        question: String,
        useDatabase: Boolean = true,
        hasModel: Boolean = true,
        history: List<ChatMessage> = emptyList(),
        dbName: String = "HF-4500",
    ) = responder(dbName).decide(question, history, useDatabase, hasModel)

    // ------------------------------------------------------------------
    // Nothing loaded
    // ------------------------------------------------------------------

    @Test
    fun withNoModelTheQuestionIsNotSent() {
        val decision = decide("what is the idle rail pressure?", hasModel = false)

        assertTrue(
            "expected NoModel, got $decision",
            decision is ChatResponder.Decision.NoModel,
        )
    }

    @Test
    fun anEmptyQuestionIsRefusedEvenWithAModelLoaded() {
        for (blank in listOf("", "   ", "\n\t ")) {
            assertTrue(
                "'$blank' should be refused",
                decide(blank) is ChatResponder.Decision.NoModel,
            )
        }
    }

    /** A leading space from a copy-and-paste must not change the answer. */
    @Test
    fun surroundingSpaceDoesNotChangeTheResult() {
        val decision = decide("   idle rail pressure   ")
        assertTrue(
            "expected a sourced answer, got $decision",
            decision is ChatResponder.Decision.FromDatabase,
        )
    }

    // ------------------------------------------------------------------
    // The case the whole app is built around
    // ------------------------------------------------------------------

    @Test
    fun aMatchingQuestionIsAnsweredFromTheDatabaseWithCitations() {
        val decision = decide("idle rail pressure")

        assertTrue(
            "expected FromDatabase, got $decision",
            decision is ChatResponder.Decision.FromDatabase,
        )
        decision as ChatResponder.Decision.FromDatabase

        assertEquals("one citation expected", 1, decision.sources.size)
        assertTrue(
            "the citation should name the page, was: ${decision.sources.first()}",
            decision.sources.first().contains("page 11"),
        )
        assertTrue(
            "the prompt must contain the retrieved text",
            decision.prompt.contains("380 bar"),
        )
    }

    /**
     * The important one.
     *
     * If the database is on and has nothing, the model must not be asked,
     * because whatever it says would come from memory while looking exactly
     * like a sourced answer.
     */
    @Test
    fun withTheDatabaseOnAndNoPassagesTheModelIsNotAsked() {
        val decision = decide("what is the price of a helicopter")

        assertTrue(
            "the model must not be asked, got $decision",
            decision is ChatResponder.Decision.NoPassages,
        )
        decision as ChatResponder.Decision.NoPassages

        assertEquals("HF-4500", decision.databaseName)
        assertEquals(2, decision.passageCount)
        assertEquals(1, decision.documentCount)
    }

    /** Which database was searched must be named, or "no match" is unhelpful. */
    @Test
    fun theUnmatchedMessageNamesTheDatabaseThatWasSearched() {
        val decision = decide(
            "what is the price of a helicopter",
            dbName = "Kobelco Manuals",
        ) as ChatResponder.Decision.NoPassages

        assertEquals("Kobelco Manuals", decision.databaseName)
    }

    // ------------------------------------------------------------------
    // The database switched off
    // ------------------------------------------------------------------

    @Test
    fun withTheDatabaseOffTheModelIsAskedButTheAnswerIsMarkedUnchecked() {
        val decision = decide(
            "what is the capital of France",
            useDatabase = false,
        )

        assertTrue(
            "expected WithoutDatabase, got $decision",
            decision is ChatResponder.Decision.WithoutDatabase,
        )
        decision as ChatResponder.Decision.WithoutDatabase

        assertTrue(decision.prompt.contains("capital of France"))
        assertTrue(decision.history.isEmpty())
        assertTrue(
            "the warning must say it was not checked",
            decision.warning.contains("may be wrong"),
        )
    }

    /**
     * With the database off there is nothing to cite, and a citation must never
     * appear. This is what stops an unchecked answer wearing a citation.
     */
    @Test
    fun withoutTheDatabaseThereAreNeverAnySources() {
        val decision = decide(
            "what is the idle rail pressure",
            useDatabase = false,
        ) as ChatResponder.Decision.WithoutDatabase

        assertFalse(
            "the prompt must not quote a document",
            decision.prompt.contains("380 bar"),
        )
        assertFalse(
            "the prompt must not mention REFERENCE TEXT",
            decision.prompt.contains("REFERENCE TEXT"),
        )
    }

    /** The toggle must genuinely change the behaviour, not just the label. */
    @Test
    fun theToggleChangesWhatIsActuallySent() {
        val on = decide("idle rail pressure")
        val off = decide("idle rail pressure", useDatabase = false)

        assertTrue(on is ChatResponder.Decision.FromDatabase)
        assertTrue(off is ChatResponder.Decision.WithoutDatabase)
    }

    // ------------------------------------------------------------------
    // Conversation history, without which follow-ups are meaningless
    // ------------------------------------------------------------------

    @Test fun generalChatReplaysCompleteGeneralTurns() {
        val history = listOf(ChatMessage("u",true,"My code is AZ-17."),
            ChatMessage("a",false,"Your code is AZ-17.",answeredWithoutDatabase=true))
        val decision = decide("What code?", useDatabase=false, history=history) as ChatResponder.Decision.WithoutDatabase
        assertEquals(history, decision.history)
        assertFalse(decision.prompt.contains("AZ-17"))
    }

    @Test
    fun theFirstQuestionCarriesNoHistory() {
        val decision = decide("hello", useDatabase = false)
                as ChatResponder.Decision.WithoutDatabase

        assertFalse(
            "an empty history should not add a heading",
            decision.prompt.contains("EARLIER IN THIS CONVERSATION"),
        )
    }

    /** One enormous answer must not push the real question out of the prompt. */
    @Test
    fun aVeryLongEarlierAnswerIsTruncated() {
        val history = listOf(
            ChatMessage(id = "m1", fromUser = false, text = "x".repeat(20_000)),
        )

        val decision = decide("and then?", useDatabase = false, history = history)
                as ChatResponder.Decision.WithoutDatabase

        assertTrue(
            "history should be capped at " +
                "${ChatResponder.MAX_HISTORY_CHARACTERS} characters",
            decision.prompt.length < 20_000,
        )
        assertTrue(
            "the real question must still be there",
            decision.prompt.contains("and then?"),
        )
    }

    // ------------------------------------------------------------------
    // A database that is not open
    // ------------------------------------------------------------------

    @Test
    fun withNoDatabaseOpenButTheToggleOnItSaysSoRatherThanPretending() {
        val decision = ChatResponder(null, "None")
            .decide("anything", emptyList(), useDatabase = true, hasModel = true)

        assertTrue(
            "expected a clear refusal, got $decision",
            decision is ChatResponder.Decision.NoModel,
        )
        assertTrue(
            "the reason should mention the database, was: ${(decision as ChatResponder.Decision.NoModel).reason}",
            decision.reason.contains("database"),
        )
    }

    /** With no database open but the toggle off, the model can still answer. */
    @Test
    fun withNoDatabaseOpenAndTheToggleOffItAnswersFromMemory() {
        val decision = ChatResponder(null, "None")
            .decide("hello", emptyList(), useDatabase = false, hasModel = true)

        assertTrue(
            "expected WithoutDatabase, got $decision",
            decision is ChatResponder.Decision.WithoutDatabase,
        )
    }
}
