package com.example.myapplication

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.myapplication.data.GameSessionRecord
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PictureRecallRoundTest {
    @get:Rule val compose = createComposeRule()
    @Test fun completedRoundKeepsItsDenominatorAndOnlyPerfectRoundsAdvance() {
        val records = mutableListOf<GameSessionRecord>()
        val names = listOf("Tea", "Rice", "Basket", "Flower", "Mango", "Bird", "Umbrella", "Apple", "Sunflower", "Fish")
        compose.mainClock.autoAdvance = false
        compose.setContent { MemoryGameScreen(onBack = {}, onSessionCompleted = { records.add(it) }) }
        compose.mainClock.advanceTimeByFrame()
        fun shown() = names.filter { compose.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() }
        val first = shown()
        assertEquals(3, first.size)
        compose.mainClock.advanceTimeBy(7200)
        first.forEach { compose.onNodeWithText(it).performScrollTo().performClick() }
        compose.onNodeWithText("Check Answer").performScrollTo().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("3 / 3").assertExists()
        assertEquals(3, records.single().totalObjects)
        compose.onNodeWithText("Play Next Round").performScrollTo().performClick()
        compose.mainClock.advanceTimeByFrame()
        val second = shown()
        assertEquals(4, second.size)
        compose.mainClock.advanceTimeBy(5700)
        compose.onNodeWithText(second.first()).performScrollTo().performClick()
        compose.onNodeWithText("Check Answer").performScrollTo().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("1 / 4").assertExists()
        assertEquals(4, records.last().totalObjects)
        compose.onNodeWithText("Play Next Round").performScrollTo().performClick()
        compose.mainClock.advanceTimeByFrame()
        assertEquals(4, shown().size)
    }
}
