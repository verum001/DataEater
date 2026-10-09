package com.dataeater.app.chat

import org.junit.Assert.*
import org.junit.Test

class StreamingAnswerTest {
    @Test fun switchingSessionsRetainsUnsavedChangesAfterStorageFailure() {
        val repository = InMemorySessionRepository()
        var fail = false
        val failing = object : SessionRepository by repository {
            override fun save(session: Session): Boolean = if (fail) false else repository.save(session)
        }
        val controller = SessionController(failing)
        val original = controller.start()
        fail = true
        controller.append(ChatMessage("question", true, "Keep this unsaved question"))
        controller.newSession()
        assertEquals("Keep this unsaved question", controller.switchTo(original.id).messages.single().text)
    }

    @Test fun lateCallbacksCannotOverwriteFinishedOrStoppedText() {
        val stream = StreamingAnswer()
        assertTrue(stream.append("Partial"))
        assertEquals("Partial", stream.pending())
        assertEquals("Partial\n[Stopped.]", stream.finish("\n[Stopped.]"))
        assertFalse(stream.append(" late response"))
        assertNull(stream.pending())
        assertEquals("Partial\n[Stopped.]", stream.finish("duplicate suffix"))
    }

    @Test fun streamingEditsWaitForExplicitCheckpointAndFinalSave() {
        val repository = InMemorySessionRepository()
        val controller = SessionController(repository)
        val session = controller.start()
        controller.append(ChatMessage("answer", false, "", incomplete = true))
        repeat(100) { controller.replaceMessage("answer", "word $it", incomplete = true, persist = false) }
        assertEquals("", repository.find(session.id)!!.messages.single().text)
        val checkpoint = controller.active!!
        assertTrue(controller.saveSnapshot(checkpoint))
        assertEquals("word 99", repository.find(session.id)!!.messages.single().text)
        controller.updateSettings(useDatabase = false, persist = false)
        assertTrue(repository.find(session.id)!!.useDatabase)
        controller.replaceMessage("answer", "Final", incomplete = false, final = true, persist = false)
        assertTrue(controller.saveSnapshot(controller.active!!))
        assertFalse(repository.find(session.id)!!.messages.single().incomplete)
        assertEquals("Final", repository.find(session.id)!!.messages.single().text)
        assertFalse(repository.find(session.id)!!.useDatabase)
        assertEquals("word 99", checkpoint.messages.single().text)
    }
}
