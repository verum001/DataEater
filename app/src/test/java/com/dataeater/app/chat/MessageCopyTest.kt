package com.dataeater.app.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a copied message says when it is pasted somewhere else.
 *
 * THE REASON THIS FILE EXISTS
 * --------------------------
 * The copied text almost always leaves the phone — into a message to a
 * colleague, a work order, a fault report. Nobody reading it can see the small
 * "3 sources" line that sat under the answer.
 *
 * So each test below is about a way the app could quietly produce a **lie in
 * someone else's chat**:
 *
 *   - an answer copied without its citations, which arrives looking like an
 *     unsourced claim
 *   - an answer copied from a database-off conversation, which arrives
 *     looking like it was checked against a manual
 *   - a refusal copied as a blank, which arrives looking like a broken app
 *
 * None of these would fail a test that only checked "the answer text is
 * copied".
 */
class MessageCopyTest {

    private fun answer(
        text: String,
        sources: List<String> = emptyList(),
        withoutDatabase: Boolean = false,
        noAnswer: Boolean = false,
    ) = ChatMessage(
        id = "m2",
        fromUser = false,
        text = text,
        sources = sources,
        answeredWithoutDatabase = withoutDatabase,
        databaseHadNoAnswer = noAnswer,
    )

    // ------------------------------------------------------------------
    // The question
    // ------------------------------------------------------------------

    /** A question is copied on its own. It makes no claim to qualify. */
    @Test
    fun aQuestionIsCopiedOnItsOwn() {
        val question = ChatMessage(
            id = "m1",
            fromUser = true,
            text = "what is the idle rail pressure?",
        )

        assertEquals("what is the idle rail pressure?", MessageCopy.forMessage(question))
    }

    // ------------------------------------------------------------------
    // A sourced answer
    // ------------------------------------------------------------------

    /**
     * The most important test here.
     *
     * An answer that leaves without its citations is the failure this whole app
     * exists to prevent — and it would be invisible, because the citation was
     * one tap away on the screen it was copied from.
     */
    @Test
    fun aSourcedAnswerIsCopiedWithItsCitations() {
        val text = MessageCopy.forMessage(
            answer(
                "380 bar at 850 rpm.",
                sources = listOf(
                    "HF-4500 Service Manual — 3.1 Fuel Pressure — page 11",
                    "HF-4500 DTC Reference — P0088 — page 5",
                ),
            )
        )

        assertTrue("the answer itself is missing: $text", text.contains("380 bar at 850 rpm."))
        assertTrue("the citations are missing: $text", text.contains("page 11"))
        assertTrue("the second citation is missing: $text", text.contains("page 5"))
        assertTrue("there is no heading: $text", text.contains(MessageCopy.SOURCES_HEADING))
        assertTrue(
            "citations should be listed one per line: $text",
            text.lines().count { it.trimStart().startsWith("•") } == 2,
        )
    }

    /** A single citation must still be listed, and not labelled "sources". */
    @Test
    fun aSingleCitationIsCopiedToo() {
        val text = MessageCopy.forMessage(
            answer("380 bar.", sources = listOf("Manual — page 11"))
        )

        assertTrue(text.contains("page 11"))
        assertTrue(text.contains(MessageCopy.SOURCES_HEADING))
    }

    // ------------------------------------------------------------------
    // An answer given with the database off
    // ------------------------------------------------------------------

    /**
     * The dangerous one.
     *
     * With the database off the answer carries no citations, so nothing in the
     * pasted text would distinguish it from a sourced one. The footer is the
     * only thing that survives the copy, so it must be there.
     */
    @Test
    fun anUnsourcedAnswerSaysSoInTheCopiedText() {
        val text = MessageCopy.forMessage(
            answer("The capital of France is Paris.", withoutDatabase = true)
        )

        assertTrue("the answer is missing: $text", text.contains("Paris"))
        assertTrue(
            "the pasted text must say it was not checked, was: $text",
            text.contains("Not checked against any document"),
        )
    }

    /** And it must not carry citations it never had. */
    @Test
    fun anUnsourcedAnswerDoesNotClaimSources() {
        val text = MessageCopy.forMessage(
            answer("I am not sure.", withoutDatabase = true)
        )

        assertFalse(
            "a database-off answer must not show the sources heading: $text",
            text.contains(MessageCopy.SOURCES_HEADING),
        )
    }

    // ------------------------------------------------------------------
    // A refusal
    // ------------------------------------------------------------------

    /**
     * "Nothing in the database matches" is a real finding.
     *
     * Copied on its own it arrives as a blank, and a colleague reads that as a
     * broken app rather than as an honest "we do not know".
     */
    @Test
    fun aRefusalIsCopiedWithItsExplanation() {
        val text = MessageCopy.forMessage(
            answer(
                "Nothing in \"HF-4500\" matches that question.",
                noAnswer = true,
            )
        )

        assertTrue("the explanation is missing: $text", text.contains("does not contain this"))
        assertTrue(
            "the refusal text itself is missing: $text",
            text.contains("Nothing in"),
        )
    }

    // ------------------------------------------------------------------
    // An interrupted answer
    // ------------------------------------------------------------------

    /**
     * A stopped answer must not travel looking finished.
     *
     * The `incomplete` flag lives in the session record, so a copy made later
     * would otherwise produce a half sentence that reads as a whole one.
     */
    @Test
    fun aStoppedAnswerSaysItWasStopped() {
        val stopped = ChatMessage(
            id = "m3",
            fromUser = false,
            text = "The idle rail",
            incomplete = true,
        )

        val text = MessageCopy.forMessage(stopped)

        assertTrue("the partial answer is missing: $text", text.contains("idle rail"))
        assertTrue(
            "a half answer must not look finished, was: $text",
            text.contains("stopped"),
        )
    }

    // ------------------------------------------------------------------
    // Ordinary cases
    // ------------------------------------------------------------------

    @Test
    fun aPlainAnswerIsCopiedWithoutExtraDecoration() {
        val text = MessageCopy.forMessage(answer("380 bar at 850 rpm."))

        assertEquals("380 bar at 850 rpm.", text)
    }

    /** Trailing spaces from a stream must not end up in the pasted text. */
    @Test
    fun trailingWhitespaceIsNotCopied() {
        val text = MessageCopy.forMessage(answer("380 bar.   \n"))
        assertEquals("380 bar.", text)
    }

    /** An answer still being written must copy whatever exists, not nothing. */
    @Test
    fun anAnswerStillBeingWrittenCopiesWhatThereIs() {
        val streaming = ChatMessage(id = "m4", fromUser = false, text = "The idle")
        assertEquals("The idle", MessageCopy.forMessage(streaming))
    }
}
