package com.example.audiary

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.util.UUID

class UiWorkflowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun librarySearchNavigationAndDiaryEditFlow() {
        // Stop decorative motion before normal idling-based UI assertions.
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(500)
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Pause moving rows").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Pause moving rows").performClick()
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("Library", useUnmergedTree = true).performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("The library.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction()).performTextInput("Nights")
        compose.onNode(hasText("Nights") and hasClickAction() and !hasSetTextAction()).performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("THE SONG & THE STORY").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Write a memory"))
        compose.onNodeWithText("Write a memory").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Save memory").fetchSemanticsNodes().isNotEmpty() }
        val text = "UI verification " + UUID.randomUUID()
        compose.onNode(hasSetTextAction()).performTextInput(text)
        compose.onNodeWithText("Save memory").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntil(10000) { compose.onAllNodes(hasText("Nights") and hasClickAction() and !hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Nights") and hasClickAction() and !hasSetTextAction()).assertExists() // Library search state survives the round-trip.
        compose.onNodeWithText("Diary", useUnmergedTree = true).performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithContentDescription("Memory options")[0].performClick()
        compose.onNodeWithText("Edit memory").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Save memory").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction()).performTextReplacement(text + " edited")
        compose.onNodeWithText("Save memory").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText(text + " edited").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithContentDescription("Memory options")[0].performClick()
        compose.onNodeWithText("Delete memory").performClick()
        compose.onNodeWithText("Delete", useUnmergedTree = true).performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText(text + " edited").fetchSemanticsNodes().isEmpty() }
    }
}
