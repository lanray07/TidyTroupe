package com.tidytroupe

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tidytroupe.data.TidyRepository
import com.tidytroupe.data.tidyPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Destructive end-to-end test: run ONLY on a disposable debug installation with
 * -Pandroid.testInstrumentationRunnerArguments.tidyDestructiveUi=true.
 * It exercises actual onboarding and Compose semantics, not a second model of the UI.
 */
@RunWith(AndroidJUnit4::class)
class UiFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val pin = "482695"

    @Test fun onboardingChorePersistenceAndChildApproval() {
        assumeTrue(
            "Requires explicit disposable-installation opt-in",
            InstrumentationRegistry.getArguments().getString("tidyDestructiveUi") == "true"
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = TidyRepository(context)
        runBlocking {
            // Refuse to reset an unrelated installation whose PIN is not this test's PIN.
            if (repository.guard.hasPin()) check(repository.guard.unlock(pin))
            else repository.guard.setupPin(pin)
            repository.deleteAll()
            context.tidyPreferences.edit { it.clear() }
        }
        context.getSharedPreferences("appearance", 0).edit().clear().commit()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitFor("A home that works together.")
            input("Household name", "Willow House")
            input("Your nickname", "Parent")
            input("Parent PIN", pin)
            input("Confirm parent PIN", pin)
            click("Create our household")
            waitFor("Willow House")

            navigate("Family")
            click("Add a family member")
            input("Your nickname", "Alex")
            click("Save")
            waitFor("Alex")

            navigate("Today")
            click("Add a chore")
            input("Chore name", "Water the little fern")
            click("Unassigned")
            // The dashboard's member filter also says Alex; the menu is the last match.
            compose.onAllNodesWithText("Alex").onLast().performClick()
            input("Stars", "7")
            click("Save")
            waitFor("Water the little fern")

            // Recreating the actual activity verifies its dashboard rehydrates from Room.
            scenario.recreate()
            waitFor("Water the little fern")
            navigate("Family")
            click("Open child view")
            input("Parent PIN", pin)
            click("Unlock")
            waitFor("Your little part. A big difference.")
            compose.onAllNodesWithText("Done").onFirst().performScrollTo().performClick()
            waitFor("Awaiting approval")
            runBlocking {
                assertEquals("PENDING", repository.completions.first().single().status)
            }

            click("Return to parent area")
            input("Parent PIN", pin)
            click("Unlock")
            waitFor("Approve")
            click("Approve")
            compose.waitUntil(10_000) {
                runBlocking { repository.completions.first().singleOrNull()?.status == "APPROVED" }
            }
            runBlocking {
                assertEquals(7, repository.completions.first().single().points)
                assertEquals(1, repository.completions.first().size)
            }
        }
    }

    private fun input(label: String, value: String) {
        compose.onNode(hasText(label) and hasSetTextAction()).performScrollTo().performTextReplacement(value)
    }
    private fun click(text: String) {
        compose.onNodeWithText(text).performScrollTo().performClick()
    }
    private fun navigate(text: String) {
        compose.onAllNodesWithText(text).onFirst().performClick()
    }
    private fun waitFor(text: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
