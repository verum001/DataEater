package com.dataeater.app.chat

import org.junit.Assert.*
import org.junit.Test

class SessionOptionsTest {
    @Test fun pinRenamePersistWithoutSwitchingOrLosingMessages() {
        val repo = InMemorySessionRepository()
        val c = SessionController(repo) { 10L }
        val first = c.start()
        c.append(ChatMessage(id = "m1", fromUser = true, text = "Keep this message"))
        val second = c.newSession()
        assertTrue(c.rename(first.id, "  Workshop  "))
        assertTrue(c.setPinned(first.id, true))
        assertEquals(second.id, c.active!!.id)
        assertEquals(first.id, c.sessions.first().id)
        val restored = SessionCodec.decodeSession(SessionCodec.encodeSession(repo.find(first.id)!!))!!
        assertEquals("Workshop", restored.title)
        assertTrue(restored.pinned)
        assertEquals("Keep this message", restored.messages.single().text)
        assertTrue(c.setPinned(first.id, false))
        assertFalse(repo.find(first.id)!!.pinned)
        assertFalse(c.rename(first.id, "  "))
        assertFalse(c.rename(first.id, "x".repeat(101)))
    }
    @Test fun legacyAndStartupRemainCompatible() {
        assertFalse(SessionCodec.decodeSession("""{"version":1,"id":"old","title":"Legacy"}""")!!.pinned)
        val repo = InMemorySessionRepository()
        repo.save(Session("p", "Pinned", pinned = true, updatedAt = 1))
        repo.save(Session("r", "Recent", updatedAt = 2))
        val c = SessionController(repo)
        assertEquals("r", c.start().id)
        assertEquals("p", c.sessions.first().id)
    }
    @Test fun failedWritesLeaveMetadataUnchanged() {
        val memory = InMemorySessionRepository()
        val repo = object : SessionRepository by memory {
            var fail = false
            override fun save(session: Session): Boolean = if (fail) false else memory.save(session)
        }
        val c = SessionController(repo)
        val first = c.start()
        repo.fail = true
        assertFalse(c.rename(first.id, "Lost name"))
        assertFalse(c.setPinned(first.id, true))
        assertEquals(first, c.active)
        assertEquals(first, c.sessions.single())
        assertEquals(first, memory.find(first.id))
    }
}
