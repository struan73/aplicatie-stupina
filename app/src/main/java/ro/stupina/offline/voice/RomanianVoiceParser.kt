package ro.stupina.offline.voice

import java.text.Normalizer

enum class ObservationScope {
    INDIVIDUAL,
    GENERAL,
    UNCERTAIN
}

data class ParsedDictation(
    val rawTranscript: String,
    val scope: ObservationScope,
    val hiveNumber: Int?,
    val category: String,
    val detectedBroodFrames: Int?,
    val detectedQueenYear: Int?,
    val detectedFollowUpDays: Int?,
    val detectedStatus: String?,
    val requiresConfirmation: Boolean,
    val uncertaintyReason: String?
)

sealed class VoiceCommand {
    data class OpenHive(val hiveNumber: Int) : VoiceCommand()
    data class ShowHiveHistory(val hiveNumber: Int) : VoiceCommand()
    data class AddObservationToHive(val hiveNumber: Int) : VoiceCommand()
    data object RecordGeneralObservation : VoiceCommand()
    data object FilterQueenlessHives : VoiceCommand()
    data object FilterWeakHives : VoiceCommand()
    data object CountHives : VoiceCommand()
    data object ShowLatestObservations : VoiceCommand()
    data object SaveCurrentObservation : VoiceCommand()
    data object CancelCurrentAction : VoiceCommand()
    data class FallbackDictation(val parsed: ParsedDictation) : VoiceCommand()
}

/**
 * Parser 100% offline pentru limba română:
 * - Normalizează diacriticele (ă, â, î, ș, ț).
 * - Convertește numeralele românești în numere întregi (ex. „doisprezece” -> 12, „șapte” -> 7).
 * - Identifică intenția de comandă vocală vs. dictare de observație.
 * - Detectează numărul stupului sau caracterul general al observației și semnalează incertitudinea.
 */
object RomanianVoiceParser {

    private val basicNumbers = mapOf(
        "zero" to 0, "unu" to 1, "una" to 1, "un" to 1, "doi" to 2, "doua" to 2,
        "trei" to 3, "patru" to 4, "cinci" to 5, "sase" to 6, "sapte" to 7,
        "opt" to 8, "noua" to 9, "zece" to 10, "unsprezece" to 11, "doisprezece" to 12,
        "douasprezece" to 12, "treisprezece" to 13, "paisprezece" to 14, "cincisprezece" to 15,
        "saisprezece" to 16, "saptesprezece" to 17, "optsprezece" to 18, "nouasprezece" to 19,
        "douazeci" to 20, "treizeci" to 30, "patruzeci" to 40, "cincizeci" to 50,
        "saizeci" to 60, "saptezeci" to 70, "optzeci" to 80, "nouazeci" to 90,
        "o suta" to 100, "suta" to 100
    )

    fun normalize(input: String): String {
        val decomposed = Normalizer.normalize(input.lowercase(), Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace("ș", "s")
            .replace("ț", "t")
            .replace(Regex("[.,!?;:]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun replaceRomanianNumberWords(normalizedText: String): String {
        var result = normalizedText
        // Compuse ex: "douazeci si cinci" -> "25"
        val tens = listOf(
            "douazeci" to 20, "treizeci" to 30, "patruzeci" to 40, "cincizeci" to 50,
            "saizeci" to 60, "saptezeci" to 70, "optzeci" to 80, "nouazeci" to 90
        )
        val units = listOf(
            "unu" to 1, "una" to 1, "doi" to 2, "doua" to 2, "trei" to 3, "patru" to 4,
            "cinci" to 5, "sase" to 6, "sapte" to 7, "opt" to 8, "noua" to 9
        )
        for ((tWord, tVal) in tens) {
            for ((uWord, uVal) in units) {
                result = result.replace(Regex("\\b$tWord\\s+si\\s+$uWord\\b"), (tVal + uVal).toString())
            }
        }
        // Simple words sorted by length descending
        basicNumbers.entries.sortedByDescending { it.key.length }.forEach { (word, value) ->
            result = result.replace(Regex("\\b$word\\b"), value.toString())
        }
        return result
    }

    fun parseCommandOrDictation(rawInput: String): VoiceCommand {
        val norm = replaceRomanianNumberWords(normalize(rawInput))

        if (Regex("\\b(salveaza|salvati|confirma)\\b.*\\bobservati").containsMatchIn(norm) || norm == "salveaza" || norm == "salveaza observatia") {
            return VoiceCommand.SaveCurrentObservation
        }
        if (norm == "anuleaza" || norm.startsWith("anuleaza ") || norm == "renunta") {
            return VoiceCommand.CancelCurrentAction
        }
        if (norm.contains("cate familii") || norm.contains("cati stupi")) {
            return VoiceCommand.CountHives
        }
        if (norm.contains("fara matca") && (norm.contains("cauta") || norm.contains("arata") || norm.contains("familii"))) {
            return VoiceCommand.FilterQueenlessHives
        }
        if ((norm.contains("slabe") || norm.contains("familii slabe")) && (norm.contains("arata") || norm.contains("cauta") || norm.contains("vezi"))) {
            return VoiceCommand.FilterWeakHives
        }
        if (norm.contains("ultimele observatii")) {
            return VoiceCommand.ShowLatestObservations
        }
        if (norm.contains("observatie generala") && (norm.contains("inregistreaza") || norm.contains("adauga") || norm.contains("noua"))) {
            return VoiceCommand.RecordGeneralObservation
        }

        val hiveNum = extractHiveNumber(norm)
        if (hiveNum != null) {
            if (norm.contains("deschide") && (norm.contains("stup") || norm.contains("fisa"))) {
                return VoiceCommand.OpenHive(hiveNum)
            }
            if (norm.contains("istoric") && norm.contains("stup")) {
                return VoiceCommand.ShowHiveHistory(hiveNum)
            }
            if ((norm.contains("adauga observatie") || norm.contains("inregistreaza observatie")) && norm.length < 48) {
                return VoiceCommand.AddObservationToHive(hiveNum)
            }
        }

        return VoiceCommand.FallbackDictation(parseDictation(rawInput))
    }

    fun parseDictation(rawInput: String): ParsedDictation {
        val norm = replaceRomanianNumberWords(normalize(rawInput))
        val hiveNumber = extractHiveNumber(norm)

        val generalKeywords = listOf(
            "intreaga stupina", "toata stupina", "la stupina", "general", "salcam",
            "inflorire", "cules", "vreme", "temperatura", "ploaie", "mutarea stupinei"
        )
        val hasGeneralCue = generalKeywords.any { norm.contains(it) }

        val broodFrames = Regex("(\\d{1,2})\\s+rame\\s+(?:de\\s+)?puiet")
            .find(norm)?.groupValues?.get(1)?.toIntOrNull()
        val queenYear = Regex("matca\\s+(?:din\\s+)?(20\\d{2})")
            .find(norm)?.groupValues?.get(1)?.toIntOrNull()
        val followUpDays = Regex("(?:peste|in|dupa)\\s+(\\d{1,2})\\s+zile")
            .find(norm)?.groupValues?.get(1)?.toIntOrNull()

        val detectedStatus = when {
            norm.contains("fara matca") -> "Fără matcă"
            norm.contains("familie slaba") || norm.contains("slaba") -> "Slabă"
            norm.contains("familie puternica") || norm.contains("productiva") -> "Productivă"
            else -> null
        }

        val category = when {
            norm.contains("tratament") || norm.contains("varroa") || norm.contains("oxalic") -> "Tratament"
            norm.contains("salcam") || norm.contains("inflorire") || norm.contains("cules") || norm.contains("tei") -> "Cules / Înflorire"
            norm.contains("vreme") || norm.contains("grade") || norm.contains("temperatura") -> "Vreme"
            norm.contains("miere") || norm.contains("recolt") -> "Producție miere"
            norm.contains("corp") || norm.contains("hran") || norm.contains("rama") -> "Lucrare"
            else -> if (hiveNumber == null && hasGeneralCue) "Observație generală" else "Control"
        }

        val (scope, requiresConfirmation, reason) = when {
            hiveNumber != null && !hasGeneralCue -> Triple(ObservationScope.INDIVIDUAL, false, null)
            hiveNumber != null && hasGeneralCue -> Triple(
                ObservationScope.UNCERTAIN,
                true,
                "Textul menționează atât stupul $hiveNumber, cât și termeni generali ai stupinei. Confirmați asocierea."
            )
            hiveNumber == null && hasGeneralCue -> Triple(ObservationScope.GENERAL, false, null)
            else -> Triple(
                ObservationScope.UNCERTAIN,
                true,
                "Nu a fost precizat numărul stupului. Alegeți dacă observația este generală sau pentru un stup anume."
            )
        }

        return ParsedDictation(
            rawTranscript = rawInput.trim(),
            scope = scope,
            hiveNumber = hiveNumber,
            category = category,
            detectedBroodFrames = broodFrames,
            detectedQueenYear = queenYear,
            detectedFollowUpDays = followUpDays,
            detectedStatus = detectedStatus,
            requiresConfirmation = requiresConfirmation,
            uncertaintyReason = reason
        )
    }

    private fun extractHiveNumber(norm: String): Int? {
        val pattern = Regex("\\b(?:stupului|stupul|stup|familiei|familia|nr\\.?|numarul)\\s+(\\d{1,4})\\b")
        return pattern.find(norm)?.groupValues?.get(1)?.toIntOrNull()
    }
}
