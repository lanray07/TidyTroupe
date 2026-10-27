package com.tidytroupe.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var repo: TidyRepository
    private lateinit var context: Context
    private val pin = "482695"

    @Before fun reset() = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        repo = TidyRepository(context)
        // Run on a disposable debug installation: this resets its household database.
        if (!repo.guard.hasPin()) repo.guard.setupPin(pin) else assertTrue(repo.guard.unlock(pin))
        repo.deleteAll()
        assertTrue(repo.guard.unlock(pin))
    }

    @Test fun childCanOnlySubmitOwnChoreAndCannotManageHousehold() = runBlocking {
        val alex = Member(nickname = "Alex")
        val sam = Member(nickname = "Sam")
        repo.saveMember(alex); repo.saveMember(sam)
        val mine = Chore(title = "Make bed", memberId = alex.id)
        val theirs = Chore(title = "Feed cat", memberId = sam.id)
        repo.saveChore(mine); repo.saveChore(theirs)
        repo.selectChild(alex.id)
        expectFailure { repo.selectChild(sam.id) }
        expectFailure { repo.saveMember(Member(nickname = "Unexpected")) }
        expectFailure { repo.complete(theirs.id, actorId = alex.id) }
        expectFailure { repo.complete(theirs.id, actorId = sam.id) }
        repo.complete(mine.id, actorId = alex.id)
        assertEquals("PENDING", repo.completions.first().single().status)
        expectFailure { repo.approve(mine.id, LocalDate.now().toString()) }
        assertTrue(repo.guard.unlock(pin))
        repo.approve(mine.id, LocalDate.now().toString())
        assertEquals("APPROVED", repo.completions.first().single().status)
    }

    @Test fun duplicateCompletionCannotEarnTwiceAndSpentStarsCannotBeUndone() = runBlocking {
        val alex = Member(nickname = "Alex")
        repo.saveMember(alex)
        val chore = Chore(title = "Tidy desk", memberId = alex.id, points = 10)
        repo.saveChore(chore)
        repo.complete(chore.id); repo.complete(chore.id)
        assertEquals(1, repo.completions.first().size)
        val prize = Reward(title = "Choose a film", cost = 10)
        repo.saveReward(prize); repo.redeem(prize.id, alex.id)
        expectFailure { repo.redeem(prize.id, alex.id) }
        expectFailure { repo.undo(chore.id, LocalDate.now().toString()) }
        assertEquals(1, repo.redemptions.first().size)
        assertEquals(1, repo.completions.first().size)
    }

    @Test fun invalidBackupDoesNotDeleteExistingHousehold() = runBlocking {
        val alex = Member(nickname = "Alex")
        repo.saveMember(alex)
        repo.saveChore(Chore(title = "Water plant", memberId = alex.id))
        val backup = JSONObject(repo.exportJson())
        backup.getJSONArray("chores").getJSONObject(0).put("points", -100)
        expectFailure { repo.importJson(backup.toString()) }
        assertEquals("Alex", repo.members.first().single().nickname)
        assertEquals("Water plant", repo.chores.first().single().title)
    }

    @Test fun exportImportRetainsOrderedRoutinesAfterDeletingTask() = runBlocking {
        val first = Chore(title = "Pack bag")
        val second = Chore(title = "Fill bottle")
        repo.saveChore(first); repo.saveChore(second)
        repo.saveRoutine(Routine(title = "Morning", choreIdsJson = JSONArray().put(first.id).put(second.id).toString()))
        repo.deleteChore(first.id)
        val backup = repo.exportJson()
        repo.importJson(backup)
        val ids = JSONArray(repo.routines.first().single().choreIdsJson)
        assertEquals(1, ids.length())
        assertEquals(second.id, ids.getString(0))
        assertEquals("Fill bottle", repo.chores.first().single().title)
    }

    @Test fun selectingChildRevokesSessionAndPinHashIsNotPlaintext() = runBlocking {
        assertTrue(repo.guard.authorized())
        val child = Member(nickname = "Alex")
        repo.saveMember(child)
        repo.selectChild(child.id)
        assertFalse(repo.guard.authorized())
        assertFalse(repo.guard.unlock("111111"))
        assertFalse(repo.guard.authorized())
        assertTrue(repo.guard.unlock(pin))
        val prefs = context.tidyPreferences.data.first().asMap()
        assertFalse(prefs.values.any { it == pin })
    }

    @Test fun sixthPinAttemptIsBlockedPersistently() = runBlocking {
        try {
            repeat(5) { assertFalse(repo.guard.unlock("111111")) }
            val anotherGuard = ParentGuard(context)
            expectFailure { anotherGuard.unlock(pin) }
            assertFalse(anotherGuard.authorized())
        } finally {
            // Keep cleanup effective even if the assertion fails.
            context.tidyPreferences.edit { prefs ->
                prefs[androidx.datastore.preferences.core.longPreferencesKey("pin_blocked_until")] = 0L
                prefs[androidx.datastore.preferences.core.intPreferencesKey("pin_failures")] = 0
            }
        }
    }

    @Test fun remindersRespectQuietHoursAndOlderBackupsDefaultOff() = runBlocking {
        val chore = Chore(title = "Water plant", reminderMinutes = 18 * 60)
        repo.saveChore(chore)
        assertEquals(1080, repo.chores.first().single().reminderMinutes)
        expectFailure { repo.saveChore(chore.copy(reminderMinutes = 7 * 60)) }
        expectFailure { repo.saveChore(chore.copy(reminderMinutes = 20 * 60)) }
        val backup = JSONObject(repo.exportJson())
        assertEquals(1080, backup.getJSONArray("chores").getJSONObject(0).getInt("reminderMinutes"))
        backup.getJSONArray("chores").getJSONObject(0).remove("reminderMinutes")
        repo.importJson(backup.toString())
        assertNull(repo.chores.first().single().reminderMinutes)
    }

    private suspend fun expectFailure(block: suspend () -> Unit) {
        var thrown = false
        try { block() } catch (_: IllegalArgumentException) { thrown = true }
        catch (_: IllegalStateException) { thrown = true }
        assertTrue("Expected the protected or invalid operation to fail", thrown)
    }
}
