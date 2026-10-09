package com.dataeater.app.builder

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.dataeater.app.MainActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BuilderUiDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun settingsButtonOpensEveryBuilderTaskAndOfflineHelp() {
        assertTrue(InstrumentationRegistry.getInstrumentation().targetContext.packageName.endsWith(".research"))
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Create database").performScrollTo().assertIsDisplayed()
        val bounds = compose.onNodeWithText("Create database").fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.width > root.width * .8f)
        compose.onNodeWithText("Create database").performClick()
        compose.onNodeWithText("Add documents").assertIsDisplayed()
        for (task in BuilderViewModel.Task.entries.drop(1)) {
            compose.onNodeWithContentDescription("Choose task").performScrollTo().performClick()
            compose.onNodeWithText(task.title).performScrollTo().performClick()
            compose.onNodeWithContentDescription("Choose task").assertTextEquals(task.title)
            compose.onAllNodesWithText(task.action).onLast().performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("Help").performClick()
        compose.onNodeWithText("Getting started").performClick()
        compose.onNodeWithText("Getting started", substring = true).assertIsDisplayed()
        Espresso.pressBack(); Espresso.pressBack()
        compose.onNodeWithText("Help").assertIsDisplayed()
        Espresso.pressBack()
        compose.onNodeWithText("Settings").assertIsDisplayed()
    }
}
