package com.dataeater.app.chat

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Saving and restoring a conversation.
 *
 * THE FAILURE THIS GUARDS AGAINST
 * -------------------------------
 * A field that is written but not read back, or read back with the wrong
 * default, does not throw. It silently loses part of a user's conversation.
 *
 * That is why there is a test for **every** field, rather than one round trip
 * that would still pass if a single field were dropped:
 *
 *   - the database was switched off, and that must not come back switched on
 *   - the processor was CPU, and that must not come back GPU
 *   - an answer was given with no database, and that must not come back
 *     looking like one that was checked against the documents
 *
 * All three change what an answer *means*, so losing them is not a cosmetic
 * bug.
 */
class SessionCodecTest {

    private val messages = listOf(
        ChatMessage(
            id = "m1",
            fromUser = true,
            text = "what is the idle rail pressure?",
        ),
        ChatMessage(
            id = "m2",
            fromUser = false,
            text = "380 bar at 850 rpm.",
            sources = listOf("HF-4500 Service Manual \u2014 3.1 Pressure \u2014 page 11"),
        ),
        ChatMessage(
            id = "m3",
            fromUser = false,
            text = "The database does not contain this information.",
            databaseHadNoAnswer = true,
        ),
    )

    private val fullSession = Session(
        id = "1757000000000-abcd1234",
        title = "Session 3",
        messages = messages,
        modelName = "qwen3_4b_instruct_2507_mixed_int4",
        databaseName = "HF-4500",
        useDatabase = true,
        preferGpu = true,
    )

    private fun roundTrip(session: Session): Session? =
        SessionCodec.decodeSession(SessionCodec.encodeSession(session))

    // ------------------------------------------------------------------
    // Every field survives
    // ------------------------------------------------------------------

    @Test
    fun everyFieldSurvivesARoundTrip() {
        val back = roundTrip(fullSession)

        assertNotNull("the session came back as null", back)
        back!!
        assertEquals(fullSession.id, back.id)
        assertEquals(fullSession.title, back.title)
        assertEquals(fullSession.modelName, back.modelName)
        assertEquals(fullSession.databaseName, back.databaseName)
        assertEquals(fullSession.useDatabase, back.useDatabase)
        assertEquals(fullSession.preferGpu, back.preferGpu)
        assertEquals(fullSession.messages.size, back.messages.size)
    }

    // The three that change what an answer means, one at a time.

    @Test
    fun theDatabaseBeingSwitchedOffMustStayOff() {
        val switchedOff = fullSession.copy(useDatabase = false, databaseName = "")
        val back = roundTrip(switchedOff)!!

        assertFalse("the database came back switched ON", back.useDatabase)
        assertEquals("", back.databaseName)
        assertTrue(
            "the subtitle should say the database was off, was: ${back.subtitle}",
            back.subtitle.contains("Database off"),
        )
    }

    @Test
    fun choosingTheCpuMustStayOnTheCpu() {
        val onCpu = fullSession.copy(preferGpu = false)
        assertFalse("the processor came back as GPU", roundTrip(onCpu)!!.preferGpu)
    }

    @Test
    fun noModelChosenMustComeBackAsNoModel() {
        val noModel = fullSession.copy(modelName = null)
        assertNull(roundTrip(noModel)!!.modelName)
    }

    // ------------------------------------------------------------------
    // Message flags, which decide how an answer is presented
    // ------------------------------------------------------------------

    @Test
    fun messageFlagsSurvive() {
        val back = roundTrip(fullSession)!!
        val answer = back.messages[1]
        val refusal = back.messages[2]

        assertEquals(1, answer.sources.size)
        assertTrue(
            "the citation text must survive exactly",
            answer.sources.first().contains("page 11"),
        )
        assertFalse(answer.databaseHadNoAnswer)
        assertTrue(
            "a refusal must stay marked as one",
            refusal.databaseHadNoAnswer,
        )
        assertTrue(refusal.text.contains("does not contain"))
    }

    /**
     * The distinction the whole app rests on: an answer from the database is
     * not the same as one from the model's memory.
     */
    @Test
    fun anAnswerGivenWithNoDatabaseStaysMarkedAsSuch() {
        val session = Session(
            id = "s1",
            title = "Session 1",
            messages = listOf(
                ChatMessage(id = "m1", fromUser = true, text = "hello"),
                ChatMessage(
                    id = "m2",
                    fromUser = false,
                    text = "Kotlin is a programming language.",
                    answeredWithoutDatabase = true,
                ),
            ),
            useDatabase = false,
        )

        val answer = roundTrip(session)!!.messages[1]

        assertTrue(
            "an unsourced answer must stay marked as unsourced",
            answer.answeredWithoutDatabase,
        )
        assertTrue(
            "and must have no citations, or it looks sourced",
            answer.sources.isEmpty(),
        )
    }

    @Test
    fun aStoppedAnswerStaysMarkedIncomplete() {
        val session = fullSession.copy(
            messages = listOf(
                ChatMessage(id = "m1", fromUser = false, text = "half an ans", incomplete = true),
            ),
        )
        assertTrue(roundTrip(session)!!.messages[0].incomplete)
    }

    // ------------------------------------------------------------------
    // An empty session, which is what a new one looks like
    // ------------------------------------------------------------------

    @Test
    fun anEmptySessionRoundTrips() {
        val empty = Session(id = "s2", title = "Session 1")
        val back = roundTrip(empty)!!

        assertEquals("Session 1", back.title)
        assertTrue(back.isEmpty)
        assertNull(back.modelName)
        assertTrue("a new session defaults to using the database", back.useDatabase)
    }

    // ------------------------------------------------------------------
    // Damaged and hostile input
    // ------------------------------------------------------------------

    @Test
    fun rubbishIsRefusedRatherThanHalfRead() {
        for (junk in listOf("", "   ", "not json", "{", "[]", "null")) {
            assertNull("'$junk' should not decode", SessionCodec.decodeSession(junk))
        }
    }

    /**
     * A file from a future version must be refused, not guessed at. Guessing
     * would mean reading fields whose meaning may have changed.
     */
    @Test
    fun aFileFromAFutureVersionIsRefused() {
        val future = JSONObject(SessionCodec.encodeSession(fullSession))
            .put("version", SessionCodec.VERSION + 1)

        assertNull(SessionCodec.decodeSession(future.toString()))
    }

    @Test
    fun aMessageWithoutTextDoesNotBreakTheFile() {
        val json = """
            {"version":1,"id":"s1","title":"Session 1","messages":[{"id":"m1"}]}
        """.trimIndent()

        val session = SessionCodec.decodeSession(json)
        assertNotNull(session)
        assertEquals(1, session!!.messages.size)
        assertEquals("", session.messages[0].text)
    }

    @Test
    fun unicodeSurvives() {
        val session = fullSession.copy(
            messages = listOf(ChatMessage(id = "m1", fromUser = true, text = "давление в рампе")),
        )
        assertEquals("давление в рампе", roundTrip(session)!!.messages[0].text)
    }

    // ------------------------------------------------------------------
    // The subtitle, which is what makes the drawer useful
    // ------------------------------------------------------------------

    @Test
    fun theSubtitleNamesTheModelAndSaysWhetherTheDatabaseIsOn() {
        val withBoth = fullSession.subtitle
        assertTrue("model missing: $withBoth", withBoth.contains("qwen3_4b"))
        assertTrue("database state missing: $withBoth", withBoth.contains("HF-4500"))

        val withoutModel = fullSession.copy(modelName = null).subtitle
        assertTrue("should say no model: $withoutModel", withoutModel.contains("No model"))

        val noDatabase = fullSession.copy(databaseName = "", useDatabase = true).subtitle
        assertTrue(noDatabase.contains("No database"))

        val databaseOff = fullSession.copy(useDatabase = false).subtitle
        assertTrue("should say database off: $databaseOff",
            databaseOff.contains("Database off"))
    }
}