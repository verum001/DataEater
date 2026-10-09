package com.dataeater.app.chat

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class SessionStoreTest {
    private fun inFolder(test: (File) -> Unit) {
        val folder = Files.createTempDirectory("session-store").toFile()
        try { test(folder) } finally { folder.deleteRecursively() }
    }

    @Test fun replacementFailureRetainsLastSavedConversation() = inFolder { folder ->
        val original = Session(id = "one", title = "Original")
        assertTrue(SessionStore(folder).save(original))
        val failing = SessionStore(folder) { temporary, target ->
            assertTrue(temporary.exists())
            assertTrue("previous save must exist until replacement", target.exists())
            throw IOException("Replacement failed")
        }
        assertFalse(failing.save(original.copy(title = "Changed")))
        assertEquals(original, SessionStore(folder).find("one"))
        assertEquals(listOf("one.json"), folder.list()!!.toList())
    }

    @Test fun interruptedStagingDoesNotHidePreviousConversation() = inFolder { folder ->
        val original = Session(id = "one", title = "Original")
        assertTrue(SessionStore(folder).save(original))
        File(folder, ".one-interrupted.tmp").writeText("partial JSON")
        assertEquals(listOf(original), SessionStore(folder).all())
    }

    @Test fun atomicReplacementAndDeletionSurviveReload() = inFolder { folder ->
        val original = Session(id = "one", title = "Original")
        val updated = original.copy(title = "Changed")
        val store = SessionStore(folder)
        assertTrue(store.save(original))
        assertTrue(store.save(updated))
        assertEquals(updated, SessionStore(folder).find("one"))
        assertTrue(store.delete("one"))
        assertTrue(SessionStore(folder).all().isEmpty())
    }

    @Test fun failedFirstSaveAndUnsafeIdsLeaveNoSession() = inFolder { folder ->
        val store = SessionStore(folder) { _, _ -> throw IOException("Unavailable") }
        assertFalse(store.save(Session(id = "one", title = "New")))
        assertFalse(store.save(Session(id = "../outside", title = "Unsafe")))
        assertTrue(folder.list()!!.isEmpty())
    }
}
