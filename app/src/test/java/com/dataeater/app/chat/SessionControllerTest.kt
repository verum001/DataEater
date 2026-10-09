package com.dataeater.app.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules that keep conversations from being lost or mixed up.
 *
 * WHAT EACH TEST IS REALLY DEFENDING
 * ---------------------------------
 * A chat app loses a user's work in a handful of predictable ways, and each
 * one gets a test:
 *
 *  - a message appears on screen but was never written down
 *  - reopening the app starts a blank conversation instead of yesterday's
 *  - switching sessions leaves the previous session's database setting behind,
 *    so a conversation about engine oil suddenly answers about fuel pressure
 *  - deleting a session leaves the app with nothing to show
 */
class SessionControllerTest {

    /** A clock the test moves by hand, so ordering is decided, not slept for. */
    private var clock = 1_000L
    private fun tick(millis: Long = 1_000L) { clock += millis }

    private val repository = InMemorySessionRepository()
    private val controller = SessionController(repository) { clock }

    private fun userMessage(text: String, id: String) =
        ChatMessage(id = id, fromUser = true, text = text)

    private fun answerMessage(text: String, id: String) =
        ChatMessage(id = id, fromUser = false, text = text, sources = listOf("manual - page 11"))

    // ------------------------------------------------------------------
    // A first launch
    // ------------------------------------------------------------------

    @Test
    fun onAFreshInstallThereIsExactlyOneSession() {
        controller.start()

        assertEquals("one session expected", 1, controller.sessions.size)
        assertEquals("Session 1", controller.active!!.title)
        assertTrue(controller.active!!.isEmpty)
    }

    /** The chat screen has nothing to draw without a session, so this must never be null. */
    @Test
    fun thereIsAlwaysAnActiveSession() {
        controller.start()
        assertNotNull(controller.active)
    }

    @Test
    fun aSecondAndThirdSessionAreNumbered() {
        controller.start()
        tick()
        controller.newSession()
        tick()
        controller.newSession()

        val titles = controller.sessions.map { it.title }.sorted()
        assertEquals(listOf("Session 1", "Session 2", "Session 3"), titles)
    }

    /**
     * Deleting Session 2 and making a new one must not reuse the name "Session 2".
     *
     * Two live conversations both called "Session 2" would be genuinely
     * confusing when switching between them.
     */
    @Test
    fun aDeletedNameIsNotReused() {
        controller.start()
        tick()
        val second = controller.newSession()
        tick()
        controller.newSession()

        controller.delete(second.id)
        tick()
        val replacement = controller.newSession()

        assertEquals("the number should carry on, not restart", "Session 4", replacement.title)
        val titles = controller.sessions.map { it.title }
        assertEquals("no name may appear twice", titles.size, titles.toSet().size)
    }

    // ------------------------------------------------------------------
    // Messages are saved
    // ------------------------------------------------------------------

    /**
     * The single most important test here: a message must reach the disk.
     *
     * If a message only reached the interface, the conversation would look fine
     * right up until the app was closed, and then be gone.
     */
    @Test
    fun aMessageIsWrittenToDiskImmediately() {
        controller.start()
        controller.append(userMessage("idle rail pressure?", "m1"))

        val onDisk = repository.find(controller.active!!.id)!!
        assertEquals(1, onDisk.messages.size)
        assertEquals("idle rail pressure?", onDisk.messages[0].text)
    }

    @Test
    fun anAnswerIsWrittenToDiskWithItsCitations() {
        controller.start()
        controller.append(userMessage("q", "m1"))
        tick()
        controller.append(answerMessage("380 bar at 850 rpm.", "m2"))

        val messages = repository.find(controller.active!!.id)!!.messages
        assertEquals(2, messages.size)
        assertEquals(listOf("manual - page 11"), messages[1].sources)
    }

    /** The in-progress answer must be kept as well, so a killed app loses less. */
    @Test
    fun aPartlyWrittenAnswerIsKeptOnDisk() {
        controller.start()
        val started = controller.append(answerMessage("", "m1"))!!
        controller.replaceMessage("m1", "The idle rail", incomplete = true)

        val stored = repository.find(started.id)!!.messages.single()
        assertEquals("The idle rail", stored.text)
        assertTrue("a half-written answer must say so", stored.incomplete)
    }

    /** A finished answer is no longer incomplete. */
    @Test
    fun aFinishedAnswerIsMarkedComplete() {
        controller.start()
        val started = controller.append(answerMessage("", "m1"))!!
        controller.replaceMessage("m1", "half an ans", incomplete = true)
        assertTrue(
            "the half-written answer must say so",
            repository.find(started.id)!!.messages.single().incomplete,
        )

        controller.replaceMessage("m1", "380 bar at 850 rpm.", incomplete = false, final = true)

        val finished = repository.find(started.id)!!.messages.single()
        assertFalse("a finished answer must not look unfinished", finished.incomplete)
        assertEquals("380 bar at 850 rpm.", finished.text)
    }

    @Test
    fun retriedAnswerPersistsOnlyThePassagesActuallySent() {
        controller.start()
        val started = controller.append(ChatMessage("m1", false, "", sources = listOf("page 1", "page 2"), incomplete = true))!!
        controller.replaceMessage("m1", "", incomplete = true, sources = listOf("page 1"))
        controller.replaceMessage("m1", "380 bar.", incomplete = false, final = true)
        val saved = repository.find(started.id)!!.messages.single()
        assertEquals(listOf("page 1"), saved.sources)
        assertEquals("380 bar.", saved.text)
    }

    @Test
    fun replacingOneMessageLeavesTheOthersAlone() {
        controller.start()
        controller.append(userMessage("first", "m1"))
        tick()
        controller.append(answerMessage("second", "m2"))
        controller.replaceMessage("m2", "second, corrected", incomplete = false, final = true)

        val messages = repository.find(controller.active!!.id)!!.messages
        assertEquals("first", messages[0].text)
        assertEquals("second, corrected", messages[1].text)
    }

    // ------------------------------------------------------------------
    // Reopening the app
    // ------------------------------------------------------------------

    /** Relaunching must return the user to their conversation, not a blank screen. */
    @Test
    fun reopeningTheAppReturnsToTheMostRecentConversation() {
        controller.start()
        tick()
        val second = controller.newSession()
        tick()
        controller.append(userMessage("remember me", "m1"))

        // A completely new controller over the same repository: this is a
        // restart, not a recomposition.
        val afterRestart = SessionController(repository) { clock }
        val restored = afterRestart.start()

        assertEquals(second.id, restored.id)
        assertEquals("remember me", restored.messages.single().text)
    }

    @Test
    fun allSavedSessionsComeBackAfterARestart() {
        controller.start()
        tick()
        controller.newSession()
        tick()
        controller.newSession()

        val afterRestart = SessionController(repository) { clock }
        afterRestart.start()

        assertEquals(3, afterRestart.sessions.size)
    }

    /**
     * The most recently used conversation goes to the top of the drawer.
     *
     * Sorting by message count would pass this if the newest session were also
     * the longest, so the test deliberately makes them disagree.
     */
    @Test
    fun theMostRecentlyUsedSessionIsAtTheTop() {
        controller.start()
        val first = controller.active!!

        // Give the first session more messages, so it is also the longer one.
        controller.append(userMessage("a", "m1"))
        tick()
        controller.append(userMessage("b", "m2"))
        tick()
        controller.append(userMessage("c", "m3"))

        tick()
        val second = controller.newSession()
        controller.append(userMessage("short", "m1"))

        assertTrue(
            "the session just used should be first, was: " +
                controller.sessions.map { it.title },
            controller.sessions.first().id == second.id,
        )
        assertEquals(
            "the longer session really is longer, so length is not why it sorted first",
            3,
            controller.sessions.first { it.id == first.id }.messages.size,
        )
        assertEquals(1, controller.sessions.first().messages.size)
    }

    /** A streaming answer must not make the drawer jump about mid-answer. */
    @Test
    fun aStreamingAnswerDoesNotReshuffleTheDrawer() {
        controller.start()
        tick()
        val second = controller.newSession()

        val others = controller.sessions.map { it.id }
        repeat(5) { controller.replaceMessage("m1", "word $it", incomplete = true) }

        assertEquals(
            "order must hold steady while an answer arrives",
            others.filter { it != second.id }.size,
            controller.sessions.count { it.id != second.id },
        )
        assertEquals(others.size, controller.sessions.size)
    }

    // ------------------------------------------------------------------
    // Sessions carry their own settings
    // ------------------------------------------------------------------

    /**
     * The requirement from the mockups: switching to a session restores its
     * saved model, database and database on/off state.
     *
     * If this leaked, a conversation recorded with the database off could be
     * read later as one that was checked against documents.
     */
    @Test
    fun switchingSessionsRestoresThatSessionsOwnSettings() {
        controller.start()
        controller.updateSettings(
            modelName = "qwen3_4b",
            databaseName = "Kobelco",
            useDatabase = true,
            preferGpu = false,
        )

        tick()
        val other = controller.newSession()
        controller.updateSettings(
            modelName = "smollm2",
            databaseName = "HF-4500",
            useDatabase = false,
            preferGpu = true,
        )

        val backAgain = controller.switchTo(controller.sessions.first { it.id != other.id }.id)
        assertEquals("qwen3_4b", backAgain.modelName)
        assertEquals("Kobelco", backAgain.databaseName)
        assertTrue(backAgain.useDatabase)
        assertFalse(backAgain.preferGpu)

        val forward = controller.switchTo(other.id)
        assertEquals("smollm2", forward.modelName)
        assertEquals("HF-4500", forward.databaseName)
        assertFalse(
            "the database-off state must come back too",
            forward.useDatabase,
        )
        assertTrue(forward.preferGpu)
    }

    /**
     * Each session's settings must be independent of the others.
     *
     * This is what "restores its saved database on/off state" depends on. If
     * two sessions shared one flag, switching to a conversation recorded with
     * the database on could silently answer from memory, or the reverse.
     */
    @Test
    fun changingOneSessionsSettingsLeavesTheOthersAlone() {
        controller.start()
        val firstId = controller.active!!.id
        controller.updateSettings(databaseName = "HF-4500", useDatabase = true)
        tick()
        val second = controller.newSession()

        controller.updateSettings(databaseName = "Kobelco", useDatabase = false)
        val afterSecond = controller.active!!

        val backAgain = controller.switchTo(firstId)
        assertEquals("HF-4500", backAgain.databaseName)
        assertTrue("the first session's database must still be on", backAgain.useDatabase)

        assertEquals("Kobelco", afterSecond.databaseName)
        assertFalse(afterSecond.useDatabase)
        assertEquals(
            "and it must be what was written to disk, not just what is on screen",
            "Kobelco",
            repository.find(second.id)!!.databaseName,
        )
    }

    @Test
    fun settingsAreSavedNotJustHeldInMemory() {
        controller.start()
        val id = controller.active!!.id
        controller.updateSettings(modelName = "qwen3_4b", useDatabase = false, preferGpu = false)

        val stored = repository.find(id)!!
        assertEquals("qwen3_4b", stored.modelName)
        assertFalse(stored.useDatabase)
        assertFalse(stored.preferGpu)
    }

    // ------------------------------------------------------------------
    // A new session starts from the current one
    // ------------------------------------------------------------------

    /** A new conversation should not silently drop back to no model and no database. */
    @Test
    fun aNewSessionStartsFromTheCurrentSetup() {
        controller.start()
        controller.updateSettings(modelName = "qwen3_4b", databaseName = "HF-4500")
        tick()

        val created = controller.newSession()

        assertEquals("qwen3_4b", created.modelName)
        assertEquals("HF-4500", created.databaseName)
        assertTrue(
            "a new session must not inherit the conversation",
            created.isEmpty,
        )
    }

    // ------------------------------------------------------------------
    // Deleting
    // ------------------------------------------------------------------

    @Test
    fun deletingTheOnlySessionLeavesOneToShow() {
        controller.start()
        val only = controller.active!!

        val replacement = controller.delete(only.id)

        assertNotNull("the chat screen needs a session", replacement)
        assertEquals(1, controller.sessions.size)
        assertTrue(controller.sessions.none { it.id == only.id })
    }

    @Test
    fun deletingMovesToAnotherSessionRatherThanNothing() {
        controller.start()
        controller.append(userMessage("keep me", "m1"))
        tick()
        val second = controller.newSession()

        val replacement = controller.delete(second.id)

        assertEquals(1, replacement.messages.size)
        assertEquals("keep me", replacement.messages[0].text)
        assertNull(repository.find(second.id))
    }

    /** Deleting a session that is not on screen must not move the view. */
    @Test
    fun deletingABackgroundSessionKeepsTheCurrentOne() {
        controller.start()
        controller.append(userMessage("current", "m1"))
        tick()
        val second = controller.newSession()
        tick()
        controller.newSession()
        val current = controller.active!!

        val returned = controller.delete(second.id)

        assertEquals(current.id, returned.id)
        assertEquals(current.id, controller.active!!.id)
    }

    // ------------------------------------------------------------------
    // Broken input
    // ------------------------------------------------------------------

    @Test
    fun switchingToAnUnknownSessionDoesNothingRatherThanFailing() {
        controller.start()
        val before = controller.active!!.id

        val result = controller.switchTo("no-such-session")

        assertEquals(before, result.id)
        assertNotNull("the user must never be left with nothing", controller.active)
    }

    /** A repository that refuses to write must not make the app unusable. */
    @Test
    fun aFailedSaveStillLeavesAUsableConversation() {
        val failing = object : SessionRepository by repository {
            override fun save(session: Session) = false
        }
        val fragile = SessionController(failing) { clock }
        fragile.start()
        fragile.append(userMessage("typed anyway", "m1"))

        assertEquals(
            "the message must still be on screen",
            "typed anyway",
            fragile.active!!.messages.single().text,
        )
    }
    @Test fun failedDeletionKeepsTheSavedSessionAndSelection() {
        val refusing = object : SessionRepository by repository {
            override fun delete(id: String) = false
        }
        val c = SessionController(refusing)
        val original = c.start()
        try { c.delete(original.id); throw AssertionError("Expected storage failure") }
        catch (_: IllegalStateException) { }
        assertEquals(original.id, c.active!!.id)
        assertEquals(listOf(original), c.sessions)
        assertNotNull(repository.find(original.id))
    }

    @Test fun deletedMessagesStayDeletedAfterRestart() {
        val original = controller.start()
        controller.append(userMessage("invented content", "delete-message"))
        controller.delete(original.id)
        val restarted = SessionController(repository).start()
        assertTrue(restarted.messages.isEmpty())
        assertNull(repository.find(original.id))
    }

}
