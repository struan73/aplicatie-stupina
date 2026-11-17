package ro.stupina.offline

import org.junit.Assert.*
import org.junit.Test
import ro.stupina.offline.voice.ObservationScope
import ro.stupina.offline.voice.RomanianVoiceParser
import ro.stupina.offline.voice.VoiceCommand

class RomanianVoiceParserTest {

    @Test
    fun parsesIndividualHiveDictationWithRomanianNumberWords() {
        val text = "Stupul doisprezece, familie puternică, șapte rame de puiet, matcă din 2026, adăugat corpul al treilea."
        val parsed = RomanianVoiceParser.parseDictation(text)

        assertEquals(ObservationScope.INDIVIDUAL, parsed.scope)
        assertEquals(12, parsed.hiveNumber)
        assertEquals(7, parsed.detectedBroodFrames)
        assertEquals(2026, parsed.detectedQueenYear)
        assertEquals("Productivă", parsed.detectedStatus)
        assertFalse(parsed.requiresConfirmation)
    }

    @Test
    fun parsesWeakHiveWithFollowUpDays() {
        val text = "Stupul 8, familie slabă, trei rame de puiet, necesită verificare peste șapte zile."
        val parsed = RomanianVoiceParser.parseDictation(text)

        assertEquals(ObservationScope.INDIVIDUAL, parsed.scope)
        assertEquals(8, parsed.hiveNumber)
        assertEquals(3, parsed.detectedBroodFrames)
        assertEquals(7, parsed.detectedFollowUpDays)
        assertEquals("Slabă", parsed.detectedStatus)
    }

    @Test
    fun parsesGeneralApiaryObservation() {
        val text = "La întreaga stupină, astăzi a început înflorirea salcâmului."
        val parsed = RomanianVoiceParser.parseDictation(text)

        assertEquals(ObservationScope.GENERAL, parsed.scope)
        assertNull(parsed.hiveNumber)
        assertEquals("Cules / Înflorire", parsed.category)
        assertFalse(parsed.requiresConfirmation)
    }

    @Test
    fun flagsUncertainObservationWhenHiveNumberMissing() {
        val text = "Albine foarte agitate la urdiniș, posibil furtisag."
        val parsed = RomanianVoiceParser.parseDictation(text)

        assertEquals(ObservationScope.UNCERTAIN, parsed.scope)
        assertTrue(parsed.requiresConfirmation)
    }

    @Test
    fun parsesVoiceCommandsInRomanian() {
        assertEquals(VoiceCommand.OpenHive(15), RomanianVoiceParser.parseCommandOrDictation("Deschide stupul 15."))
        assertEquals(VoiceCommand.ShowHiveHistory(12), RomanianVoiceParser.parseCommandOrDictation("Arată istoricul stupului doisprezece."))
        assertEquals(VoiceCommand.FilterQueenlessHives, RomanianVoiceParser.parseCommandOrDictation("Caută familiile fără matcă."))
        assertEquals(VoiceCommand.FilterWeakHives, RomanianVoiceParser.parseCommandOrDictation("Arată familiile slabe."))
        assertEquals(VoiceCommand.CountHives, RomanianVoiceParser.parseCommandOrDictation("Câte familii am?"))
        assertEquals(VoiceCommand.SaveCurrentObservation, RomanianVoiceParser.parseCommandOrDictation("Salvează observația."))
        assertEquals(VoiceCommand.CancelCurrentAction, RomanianVoiceParser.parseCommandOrDictation("Anulează."))
    }
}
