package com.dataeater.app.builder

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import com.dataeater.app.MainActivity
import org.junit.Rule
import org.junit.Test

class BuilderFlowUiDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Before fun openApp() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val component = instrument.targetContext.packageName + "/" + MainActivity::class.java.name
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrument.uiAutomation.executeShellCommand("am start -W -f 0x10008000 -n $component")).use { it.readBytes() }
    }
    @Test fun downloadsAndSimplifiedBuilderAreReachable() {
        compose.waitUntil(timeoutMillis = 30_000) { compose.onAllNodesWithContentDescription("Settings", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Settings", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Download databases").assertDoesNotExist()
        compose.onNodeWithContentDescription("Change Database", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("Download databases").performScrollTo().performClick()
        // Keep UI verification independent of GitHub availability; no transfer is started.
        val instrument = InstrumentationRegistry.getInstrumentation()
        val downloads = com.dataeater.app.data.DatabaseDownloads.get(instrument.targetContext)
        compose.waitUntil(timeoutMillis = 45_000) { !downloads.catalog.value.loading }
        val description = "Synthetic catalogue description. " .repeat(40)
        val entry = com.dataeater.app.data.DatabaseCatalog.Entry("987654321", "Catalogue screen check", description,
            "0.1", "catalogue-screen-check-0.1.dataeater",
            "https://github.com/verum001/DataEater-Databases/releases/download/test/test.dataeater", 1024, "0".repeat(64))
        val field = downloads.javaClass.getDeclaredField("mutableCatalog").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val catalogue = field.get(downloads) as kotlinx.coroutines.flow.MutableStateFlow<com.dataeater.app.data.DatabaseDownloads.CatalogState>
        instrument.runOnMainSync { catalogue.value = com.dataeater.app.data.DatabaseDownloads.CatalogState(listOf(entry)) }
        compose.onNodeWithText(description).assertDoesNotExist()
        compose.onNodeWithText("Show description").performScrollTo().performClick()
        compose.onNodeWithText(description).assertExists()
        compose.onNodeWithText("Hide description").performScrollTo().performClick()
        compose.onNodeWithText("Hide description").assertDoesNotExist()
        compose.onNodeWithText(description).assertDoesNotExist()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Create database").performScrollTo().performClick()
        compose.onNodeWithText("Choose files").assertIsDisplayed()
        compose.onNodeWithContentDescription("Encrypt").assertIsDisplayed()
        compose.onNodeWithContentDescription("Optimise with LLM").performClick()
        compose.onNodeWithText("Continue after optimisation").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Secret key vault").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("More tools").assertDoesNotExist()
    }
}
