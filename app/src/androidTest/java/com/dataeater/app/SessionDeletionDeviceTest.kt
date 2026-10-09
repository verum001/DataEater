package com.dataeater.app

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.dataeater.app.chat.*
import com.dataeater.app.ui.SessionDrawer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class SessionDeletionDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun cancellationDeletionAndRestartPreserveTheCorrectSessions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.packageName.endsWith(".research"))
        val folder = File(context.cacheDir, "session-deletion-test-${System.nanoTime()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) { override fun getFilesDir(): File = folder }
        try {
            val store = SessionStore(isolated)
            val controller = SessionController(store)
            val first = controller.start()
            controller.append(ChatMessage(id = "invented-message", fromUser = true, text = "Invented session test"))
            val second = controller.newSession()
            val third = controller.newSession()
            val visible = mutableStateOf(controller.sessions)
            val active = mutableStateOf(third.id)
            compose.activity.setContent {
                MaterialTheme {
                    SessionDrawer(visible = true, sessions = visible.value, activeId = active.value,
                        onSelect = {}, onNewSession = {}, deletionEnabled = true,
                        onDelete = {
                            active.value = controller.delete(it).id
                            visible.value = controller.sessions
                            true
                        }, onOpen = {}, onClose = {}, onOpenHelp = {}) { Text("Test chat") }
                }
            }
            compose.onNodeWithTag("delete-session-${first.id}").performClick()
            compose.onNodeWithText("Cancel").performClick()
            assertNotNull(store.find(first.id))
            compose.onNodeWithTag("delete-session-${second.id}").performClick()
            compose.onNodeWithText("Delete", useUnmergedTree = true).performClick()
            compose.onNodeWithTag("delete-session-${second.id}").assertDoesNotExist()
            assertEquals(third.id, controller.active!!.id)
            assertNull(store.find(second.id))
            compose.onNodeWithTag("delete-session-${third.id}").performClick()
            compose.onNodeWithText("Delete", useUnmergedTree = true).performClick()
            compose.onNodeWithTag("delete-session-${third.id}").assertDoesNotExist()
            assertEquals(first.id, controller.active!!.id)
            compose.onNodeWithTag("delete-session-${first.id}").performClick()
            compose.onNodeWithText("Delete", useUnmergedTree = true).performClick()
            compose.onNodeWithTag("delete-session-${first.id}").assertDoesNotExist()
            val restarted = SessionController(store)
            assertTrue(restarted.start().messages.isEmpty())
            assertEquals(1, restarted.sessions.size)
            assertNull(store.find(first.id))
        } finally { folder.deleteRecursively() }
    }
    @Test fun mainScreenRefreshesWhenABackgroundSessionIsDeleted() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.packageName.endsWith(".research"))
        val store = SessionStore(context)
        val before = store.all().map { it.id }.toSet()
        try {
            compose.onNodeWithContentDescription("Sessions").performClick()
            compose.onNodeWithText("New session").performClick()
            compose.waitUntil { store.all().any { it.id !in before } }
            val background = store.all().first { it.id !in before }
            compose.onNodeWithContentDescription("Sessions").performClick()
            compose.onNodeWithText("New session").performClick()
            compose.waitUntil { store.all().count { it.id !in before } == 2 }
            val current = store.all().first { it.id !in before && it.id != background.id }
            compose.onNodeWithContentDescription("Sessions").performClick()
            compose.onNodeWithTag("delete-session-${background.id}").performClick()
            compose.onNodeWithText("Cancel").performClick()
            compose.onNodeWithTag("delete-session-${background.id}").assertExists()
            compose.onNodeWithTag("delete-session-${background.id}").performClick()
            compose.onNodeWithText("Delete", useUnmergedTree = true).performClick()
            compose.onNodeWithTag("delete-session-${background.id}").assertDoesNotExist()
            compose.onNodeWithTag("delete-session-${current.id}").assertExists()
            assertNull(store.find(background.id))
            compose.onNodeWithTag("delete-session-${current.id}").performClick()
            compose.onNodeWithText("Delete", useUnmergedTree = true).performClick()
            compose.onNodeWithTag("delete-session-${current.id}").assertDoesNotExist()
            assertNull(store.find(current.id))
        } finally {
            store.all().filter { it.id !in before }.forEach { store.delete(it.id) }
        }
    }

}
