package com.tidytroupe.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Parsing is local and deterministic. No audio or transcript persistence is needed. */
sealed interface VoiceCommand {
    val requiresConfirmation: Boolean get() = false
    data class Add(val title: String) : VoiceCommand { override val requiresConfirmation = true }
    data class Assign(val chore: String, val member: String) : VoiceCommand { override val requiresConfirmation = true }
    data class Complete(val chore: String?) : VoiceCommand { override val requiresConfirmation = true }
    data class Reschedule(val chore: String, val date: LocalDate) : VoiceCommand { override val requiresConfirmation = true }
    data class Reminder(val title: String, val date: LocalDate) : VoiceCommand { override val requiresConfirmation = true }
    data object Today : VoiceCommand
    data object MyChores : VoiceCommand
    data object NextTask : VoiceCommand
    data object Progress : VoiceCommand
    data object Checklist : VoiceCommand
    data object Stars : VoiceCommand
    data object Repeat : VoiceCommand
    data object Back : VoiceCommand
    data class Unknown(val transcript: String) : VoiceCommand
    data class UnsupportedLanguage(val languageTag: String) : VoiceCommand
}

object VoiceParser {
    val supportedLanguages: Set<String> = setOf("en")

    fun parse(
        transcript: String,
        locale: Locale = Locale.UK,
        today: LocalDate = LocalDate.now(),
        childMode: Boolean = false,
    ): VoiceCommand {
        if (locale.language !in supportedLanguages) return VoiceCommand.UnsupportedLanguage(locale.toLanguageTag())
        val input = transcript.trim().trimEnd('.', '!', '?').replace(Regex("\\s+"), " ")
        val text = input.lowercase(Locale.ROOT)
        val childCommand = when (text) {
            "what are my chores", "show my chores" -> VoiceCommand.MyChores
            "read my next task", "read my next chore" -> VoiceCommand.NextTask
            "mark my task complete", "mark my chore complete" -> VoiceCommand.Complete(null)
            "how many stars have i earned", "how many stars do i have" -> VoiceCommand.Stars
            "repeat that", "repeat" -> VoiceCommand.Repeat
            "go back", "back" -> VoiceCommand.Back
            else -> null
        }
        if (childCommand != null) return childCommand
        if (childMode) return VoiceCommand.Unknown(input)
        when (text) {
            "add a new chore", "add chore", "new chore" -> return VoiceCommand.Add("")
            "what chores are due today", "what is due today", "show today's chores" -> return VoiceCommand.Today
            "show this week's family progress", "show this weeks family progress", "show family progress" -> return VoiceCommand.Progress
            "read my household checklist", "read household checklist" -> return VoiceCommand.Checklist
        }
        Regex("^(?:add a chore|add chore|add a new chore) (.+)$", RegexOption.IGNORE_CASE)
            .matchEntire(input)?.let { return VoiceCommand.Add(it.groupValues[1]) }
        Regex("^assign (.+) to (.+)$", RegexOption.IGNORE_CASE)
            .matchEntire(input)?.let { return VoiceCommand.Assign(it.groupValues[1], it.groupValues[2]) }
        Regex("^mark (.+?) (?:as )?complete$", RegexOption.IGNORE_CASE)
            .matchEntire(input)?.let { return VoiceCommand.Complete(it.groupValues[1]) }
        Regex("^move (.+) to (today|tomorrow|monday|tuesday|wednesday|thursday|friday|saturday|sunday)$", RegexOption.IGNORE_CASE)
            .matchEntire(input)?.let {
                return VoiceCommand.Reschedule(it.groupValues[1], dateFor(it.groupValues[2], today))
            }
        Regex("^remind me to (.+) (today|tomorrow|monday|tuesday|wednesday|thursday|friday|saturday|sunday)$", RegexOption.IGNORE_CASE)
            .matchEntire(input)?.let {
                return VoiceCommand.Reminder(it.groupValues[1], dateFor(it.groupValues[2], today))
            }
        return VoiceCommand.Unknown(input)
    }

    private fun dateFor(word: String, today: LocalDate): LocalDate = when (word.lowercase(Locale.ROOT)) {
        "today" -> today
        "tomorrow" -> today.plusDays(1)
        else -> today.with(TemporalAdjusters.next(DayOfWeek.valueOf(word.uppercase(Locale.ROOT))))
    }
}
