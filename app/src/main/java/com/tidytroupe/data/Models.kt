package com.tidytroupe.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tidytroupe.core.ChoreEngine
import com.tidytroupe.core.Frequency
import com.tidytroupe.core.Schedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

@Entity(tableName = "members")
data class Member(@PrimaryKey val id: String = UUID.randomUUID().toString(), val nickname: String, val child: Boolean = true, val avatar: Int = 0)

@Entity(tableName = "chores")
data class Chore(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val memberId: String? = null,
    val category: String = "General Household",
    val notes: String = "",
    val points: Int = 5,
    val due: String = LocalDate.now().toString(),
    val repeat: String = "NONE",
    val paused: Boolean = false,
    val archived: Boolean = false,
    val approval: Boolean = true,
    val repeatInterval: Int = 1,
    val weekdays: String = "",
    val effort: Int = 1,
    val reminderMinutes: Int? = null
)

@Entity(tableName = "completions", primaryKeys = ["choreId", "date"])
data class Completion(val choreId: String, val date: String, val memberId: String?, val points: Int, val status: String = "PENDING")

@Entity(tableName = "rewards")
data class Reward(@PrimaryKey val id: String = UUID.randomUUID().toString(), val title: String, val cost: Int = 25)

@Entity(tableName = "redemptions")
data class Redemption(@PrimaryKey val id: String = UUID.randomUUID().toString(), val rewardId: String, val memberId: String, val cost: Int, val date: String = LocalDate.now().toString(), val title: String = "")

/** Ordered chore identifiers are encoded as JSON rather than ambiguous delimiter-separated text. */
@Entity(tableName = "routines")
data class Routine(@PrimaryKey val id: String = UUID.randomUUID().toString(), val title: String, val choreIdsJson: String = "[]")

/** Persisted recurrence values map into the tested calendar engine; old rows keep their defaults. */
fun Chore.toDomain(): com.tidytroupe.core.Chore {
    val selected = if (weekdays.isBlank()) emptySet() else {
        val days = weekdays.split(',').map { it.trim().toInt() }
        require(days.all { it in 1..7 } && days.distinct().size == days.size) { "Choose valid, distinct weekdays." }
        days.map { DayOfWeek.of(it) }.toSet()
    }
    val frequency = when (repeat) {
        "NONE" -> Frequency.ONCE
        "DAILY", "CUSTOM" -> Frequency.DAILY
        "WEEKLY" -> Frequency.WEEKLY
        "MONTHLY" -> Frequency.MONTHLY
        "WEEKDAYS", "SELECTED" -> Frequency.SELECTED_WEEKDAYS
        else -> error("Unsupported recurrence")
    }
    val days = if (repeat == "WEEKDAYS") DayOfWeek.values().take(5).toSet() else selected
    return com.tidytroupe.core.Chore(
        id = id, title = title, assigneeId = memberId, category = category, notes = notes,
        points = points, dueDate = LocalDate.parse(due),
        schedule = Schedule(frequency, repeatInterval, days), requiresApproval = approval,
        paused = paused, archived = archived, effort = effort,
    )
}

/** Corrupt dates are excluded from views; repository writes/imports reject invalid models. */
fun Chore.isDueOn(date: LocalDate): Boolean = runCatching { ChoreEngine.isDue(toDomain(), date) }.getOrDefault(false)
