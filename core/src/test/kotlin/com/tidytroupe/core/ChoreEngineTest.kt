package com.tidytroupe.core

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ChoreEngineTest {
    private val date = LocalDate.of(2026, 10, 27)
    private fun chore(schedule: Schedule = Schedule()) = Chore("c", "Laundry", "alex", points = 5, dueDate = date, schedule = schedule)

    @Test fun monthlyRecurrenceClampsWithoutDrifting() {
        val chore = chore(Schedule(Frequency.MONTHLY)).copy(dueDate = LocalDate.of(2028, 1, 31))
        assertTrue(ChoreEngine.isDue(chore, LocalDate.of(2028, 2, 29)))
        assertFalse(ChoreEngine.isDue(chore, LocalDate.of(2028, 3, 29)))
        assertTrue(ChoreEngine.isDue(chore, LocalDate.of(2028, 3, 31)))
        assertEquals(LocalDate.of(2028, 3, 31), ChoreEngine.nextDue(chore, LocalDate.of(2028, 3, 1)))
    }

    @Test fun dailyCustomIntervalAndPausedStateAreRespected() {
        val chore = chore(Schedule(Frequency.DAILY, interval = 3))
        assertFalse(ChoreEngine.isDue(chore, date.minusDays(3)))
        assertTrue(ChoreEngine.isDue(chore, date.plusDays(3)))
        assertFalse(ChoreEngine.isDue(chore, date.plusDays(1)))
        assertNull(ChoreEngine.nextDue(chore.copy(paused = true), date))
        assertFalse(ChoreEngine.isDue(chore.copy(archived = true), date))
    }

    @Test fun biweeklyWeekdayScheduleUsesCalendarWeeks() {
        val chore = chore(Schedule(Frequency.SELECTED_WEEKDAYS, 2, setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)))
        // Anchor Tuesday 27 October, first Friday still belongs to its anchored week.
        assertTrue(ChoreEngine.isDue(chore, LocalDate.of(2026, 10, 30)))
        assertFalse(ChoreEngine.isDue(chore, LocalDate.of(2026, 11, 2)))
        assertTrue(ChoreEngine.isDue(chore, LocalDate.of(2026, 11, 9)))
    }

    @Test fun oneOffHasNoNextOccurrenceAfterDueDate() {
        assertEquals(date, ChoreEngine.nextDue(chore(), date.minusDays(2)))
        assertNull(ChoreEngine.nextDue(chore(), date.plusDays(1)))
    }

    @Test fun monthlyCustomIntervalSpansYearBoundaries() {
        val chore = chore(Schedule(Frequency.MONTHLY, interval = 3)).copy(dueDate = LocalDate.of(2026, 11, 30))
        assertFalse(ChoreEngine.isDue(chore, LocalDate.of(2026, 12, 30)))
        assertTrue(ChoreEngine.isDue(chore, LocalDate.of(2027, 2, 28)))
        assertTrue(ChoreEngine.isDue(chore, LocalDate.of(2027, 5, 30)))
        assertFalse(ChoreEngine.isDue(chore, LocalDate.of(2027, 5, 28)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun selectedWeekdaysRequiresASelection() {
        Schedule(Frequency.SELECTED_WEEKDAYS)
    }

    @Test(expected = IllegalArgumentException::class)
    fun customIntervalCannotBeZero() {
        Schedule(Frequency.DAILY, interval = 0)
    }

    @Test fun childSubmissionAwardsNothingUntilAdultApprovalAndCannotDuplicate() {
        val chore = chore().copy(requiresApproval = true)
        val submitted = ChoreEngine.complete(chore, date, false, emptyList())
        assertEquals(CompletionStatus.PENDING, submitted.single().status)
        assertEquals(0, ChoreEngine.balance("alex", submitted, emptyList()))
        assertEquals(submitted, ChoreEngine.complete(chore, date, false, submitted))
        val approved = submitted.map { ChoreEngine.approve(it, true) }
        assertEquals(5, ChoreEngine.balance("alex", approved, emptyList()))
        assertEquals(5, approved.single().pointsAwarded)
    }

    @Test(expected = IllegalArgumentException::class)
    fun childrenCannotApprove() {
        ChoreEngine.approve(Completion("c", date, CompletionStatus.PENDING, 5, "alex"), false)
    }

    @Test fun redemptionIsIdempotentAndUndoPreservesSpentPoints() {
        val completed = ChoreEngine.complete(chore(), date, true, emptyList())
        val reward = Reward("r", "Family game", 5)
        val redeemed = ChoreEngine.redeem(reward, "alex", "request", date, true, completed, emptyList())
        assertEquals(redeemed, ChoreEngine.redeem(reward, "alex", "request", date, true, completed, redeemed))
        assertEquals(0, ChoreEngine.balance("alex", completed, redeemed))
        val undone = ChoreEngine.undo(completed, "c", date)
        assertEquals(-5, ChoreEngine.balance("alex", undone, redeemed))
    }

    @Test(expected = IllegalArgumentException::class)
    fun insufficientPointsCannotRedeem() {
        ChoreEngine.redeem(Reward("r", "Game", 5), "alex", "request", date, true, emptyList(), emptyList())
    }

    @Test fun completionRetainsOriginalOwnerAndPointsWhenChoreChanges() {
        val completed = ChoreEngine.complete(chore(), date, true, emptyList())
        val edited = chore().copy(assigneeId = "sam", points = 100)
        val repeated = ChoreEngine.complete(edited, date, true, completed)
        assertEquals(5, ChoreEngine.balance("alex", repeated, emptyList()))
        assertEquals(0, ChoreEngine.balance("sam", repeated, emptyList()))
    }
}
