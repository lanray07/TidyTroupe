package com.tidytroupe.core

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Calendar-based recurrences use local dates rather than 24-hour durations, preserving DST. */
object ChoreEngine {
    fun isDue(chore: Chore, date: LocalDate): Boolean {
        if (chore.paused || chore.archived || date < chore.dueDate) return false
        val anchor = chore.dueDate
        val schedule = chore.schedule
        return when (schedule.frequency) {
            Frequency.ONCE -> date == anchor
            Frequency.DAILY -> ChronoUnit.DAYS.between(anchor, date) % schedule.interval == 0L
            Frequency.WEEKLY -> {
                val days = if (schedule.weekdays.isEmpty()) setOf(anchor.dayOfWeek) else schedule.weekdays
                date.dayOfWeek in days && weeksBetween(anchor, date) % schedule.interval == 0L
            }
            Frequency.SELECTED_WEEKDAYS -> date.dayOfWeek in schedule.weekdays &&
                weeksBetween(anchor, date) % schedule.interval == 0L
            Frequency.MONTHLY -> {
                val month = YearMonth.from(date)
                ChronoUnit.MONTHS.between(YearMonth.from(anchor), month) % schedule.interval == 0L &&
                    date.dayOfMonth == minOf(anchor.dayOfMonth, month.lengthOfMonth())
            }
        }
    }

    private fun weeksBetween(anchor: LocalDate, date: LocalDate): Long = ChronoUnit.WEEKS.between(
        anchor.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)),
        date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)),
    )

    /** Inclusive search; null means paused, archived, or a one-off date already in the past. */
    fun nextDue(chore: Chore, from: LocalDate): LocalDate? {
        if (chore.paused || chore.archived) return null
        val start = maxOf(from, chore.dueDate)
        if (chore.schedule.frequency == Frequency.ONCE) return chore.dueDate.takeIf { it >= from }
        // At interval <= 365, a monthly recurrence can be at most 365 months away.
        val limit = start.plusMonths(366).plusDays(7)
        var cursor = start
        while (cursor <= limit) {
            if (isDue(chore, cursor)) return cursor
            cursor = cursor.plusDays(1)
        }
        return null
    }

    /** Idempotent: repeated taps cannot create a second completion or award extra points. */
    fun complete(
        chore: Chore,
        date: LocalDate,
        actorIsAdult: Boolean,
        existing: List<Completion>,
    ): List<Completion> {
        require(isDue(chore, date)) { "This chore is not scheduled on that date" }
        if (existing.any { it.choreId == chore.id && it.occurrence == date }) return existing
        val status = if (chore.requiresApproval && !actorIsAdult) CompletionStatus.PENDING else CompletionStatus.APPROVED
        return existing + Completion(chore.id, date, status, chore.points, chore.assigneeId)
    }

    /** The repository must also enforce adult access before invoking this domain operation. */
    fun approve(completion: Completion, actorIsAdult: Boolean): Completion {
        require(actorIsAdult) { "Adult approval required" }
        return completion.copy(status = CompletionStatus.APPROVED)
    }

    fun undo(existing: List<Completion>, choreId: String, date: LocalDate): List<Completion> =
        existing.filterNot { it.choreId == choreId && it.occurrence == date }

    /** A correction may make balance negative after redemption; never silently erase the debt. */
    fun balance(profileId: String, completions: List<Completion>, redemptions: List<Redemption>): Int =
        completions.distinctBy { it.key }
            .filter { it.profileId == profileId && it.status == CompletionStatus.APPROVED }
            .sumOf { it.pointsAwarded } -
            redemptions.distinctBy { it.id }.filter { it.profileId == profileId }.sumOf { it.cost }

    fun redeem(
        reward: Reward,
        profileId: String,
        requestId: String,
        date: LocalDate,
        actorIsAdult: Boolean,
        completions: List<Completion>,
        redemptions: List<Redemption>,
    ): List<Redemption> {
        require(actorIsAdult) { "Adult approval required" }
        require(requestId.isNotBlank())
        if (redemptions.any { it.id == requestId }) return redemptions
        require(balance(profileId, completions, redemptions) >= reward.cost) { "More points needed" }
        return redemptions + Redemption(requestId, reward.id, profileId, reward.cost, date)
    }

    fun routineProgress(routine: Routine, date: LocalDate, completions: List<Completion>): Pair<Int, Int> =
        routine.choreIds.count { id -> completions.any { it.choreId == id && it.occurrence == date && it.status == CompletionStatus.APPROVED } } to routine.choreIds.size
}
