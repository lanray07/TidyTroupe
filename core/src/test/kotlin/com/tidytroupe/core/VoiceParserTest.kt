package com.tidytroupe.core

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class VoiceParserTest {
    private val today = LocalDate.of(2026, 10, 27)

    @Test fun parseAdultExamplesIntoStructuredIntents() {
        assertEquals(VoiceCommand.Add(""), VoiceParser.parse("Add a new chore."))
        assertEquals(VoiceCommand.Assign("kitchen cleaning", "Alex"), VoiceParser.parse("Assign kitchen cleaning to Alex."))
        assertEquals(VoiceCommand.Today, VoiceParser.parse("What chores are due today?"))
        assertEquals(VoiceCommand.Complete("laundry"), VoiceParser.parse("Mark laundry as complete."))
        assertEquals(VoiceCommand.Progress, VoiceParser.parse("Show this week's family progress."))
        assertEquals(VoiceCommand.Checklist, VoiceParser.parse("Read my household checklist."))
    }

    @Test fun relativeDatesAreResolvedAgainstInjectedClockDate() {
        assertEquals(VoiceCommand.Reminder("take out the bins", today.plusDays(1)),
            VoiceParser.parse("Remind me to take out the bins tomorrow.", today = today))
        assertEquals(VoiceCommand.Reschedule("vacuuming", LocalDate.of(2026, 10, 31)),
            VoiceParser.parse("Move vacuuming to Saturday.", today = today))
    }

    @Test fun childDictionaryExcludesAdultCommands() {
        assertEquals(VoiceCommand.Stars, VoiceParser.parse("How many stars have I earned?", childMode = true))
        assertEquals(VoiceCommand.Complete(null), VoiceParser.parse("Mark my task complete.", childMode = true))
        assertTrue(VoiceParser.parse("Assign kitchen cleaning to Alex.", childMode = true) is VoiceCommand.Unknown)
    }

    @Test fun unsupportedLanguagesNeverPretendToRecogniseEnglish() {
        assertEquals(VoiceCommand.UnsupportedLanguage("ar"), VoiceParser.parse("Add a new chore", Locale.forLanguageTag("ar")))
    }

    @Test fun mutationNeedsConfirmationAndUnknownInputCannotExecute() {
        assertTrue(VoiceParser.parse("Mark laundry complete").requiresConfirmation)
        assertTrue(VoiceParser.parse("Move laundry to Saturday").requiresConfirmation)
        assertFalse(VoiceParser.parse("What are my chores?").requiresConfirmation)
        assertTrue(VoiceParser.parse("Delete every chore") is VoiceCommand.Unknown)
    }
}
