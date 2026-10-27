package com.tidytroupe.core

import java.time.DayOfWeek
import java.time.LocalDate

enum class Role { ADULT, CHILD }
enum class Frequency { ONCE, DAILY, WEEKLY, MONTHLY, SELECTED_WEEKDAYS }
enum class CompletionStatus { PENDING, APPROVED }

data class Household(val id: String, val name: String, val rewardsEnabled: Boolean = true)

/** Nicknames only; child profiles intentionally have no birth dates or contact fields. */
data class Profile(
    val id: String,
    val name: String,
    val avatar: String = "leaf",
    val role: Role = Role.CHILD,
    val voiceEnabled: Boolean = false,
)

data class Schedule(
    val frequency: Frequency = Frequency.ONCE,
    val interval: Int = 1,
    val weekdays: Set<DayOfWeek> = emptySet(),
) {
    init {
        require(interval in 1..365) { "Recurrence interval must be between 1 and 365" }
        require(frequency != Frequency.SELECTED_WEEKDAYS || weekdays.isNotEmpty()) {
            "Select at least one weekday"
        }
    }
}

data class Chore(
    val id: String,
    val title: String,
    val assigneeId: String? = null,
    val category: String = "General Household",
    val notes: String = "",
    val points: Int = 1,
    val dueDate: LocalDate = LocalDate.now(),
    val schedule: Schedule = Schedule(),
    val requiresApproval: Boolean = false,
    val paused: Boolean = false,
    val archived: Boolean = false,
    val effort: Int = 1,
) {
    init {
        require(id.isNotBlank())
        require(title.isNotBlank()) { "A chore needs a title" }
        require(points in 0..10000) { "Points must be between 0 and 10000" }
        require(effort in 1..3)
    }
}

/** Points are captured at completion time, so editing a chore never rewrites past balances. */
data class Completion(
    val choreId: String,
    val occurrence: LocalDate,
    val status: CompletionStatus,
    val pointsAwarded: Int,
    val profileId: String?,
) {
    init { require(pointsAwarded >= 0) }
    val key: String get() = "$choreId:$occurrence"
}

data class Routine(
    val id: String,
    val title: String,
    val choreIds: List<String>,
    val assigneeId: String? = null,
) {
    init {
        require(title.isNotBlank())
        require(choreIds.distinct().size == choreIds.size) { "A routine cannot repeat a chore" }
    }
}

data class Reward(val id: String, val title: String, val cost: Int) {
    init { require(title.isNotBlank()); require(cost > 0) }
}

data class Redemption(
    val id: String,
    val rewardId: String,
    val profileId: String,
    val cost: Int,
    val date: LocalDate,
) {
    init { require(cost > 0) }
}
