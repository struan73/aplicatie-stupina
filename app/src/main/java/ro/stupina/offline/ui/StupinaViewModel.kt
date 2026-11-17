package ro.stupina.offline.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ro.stupina.offline.data.db.*
import ro.stupina.offline.data.repository.StupinaRepository
import ro.stupina.offline.export.BackupAndReportManager
import ro.stupina.offline.voice.*

enum class AppSection(val label: String) {
    STUPINA("Stupina"),
    STUPI("Stupi"),
    OBSERVATII("Observații"),
    CALENDAR("Calendar"),
    RAPOARTE("Rapoarte"),
    SETARI("Setări")
}

data class PendingVoiceConfirmation(
    val parsed: ParsedDictation,
    val selectedHiveNumber: Int?,
    val isGeneral: Boolean
)

class StupinaViewModel(application: Application) : AndroidViewModel(application) {
    private val db = StupinaDatabase.getInstance(application)
    private val repo = StupinaRepository(db)
    val speechManager = OfflineSpeechManager(application)
    val ttsManager = TtsConfirmManager(application)

    private val _currentSection = MutableStateFlow(AppSection.STUPINA)
    val currentSection: StateFlow<AppSection> = _currentSection.asStateFlow()

    private val _selectedHiveId = MutableStateFlow<Long?>(null)
    val selectedHiveId: StateFlow<Long?> = _selectedHiveId.asStateFlow()

    private val _hiveFilter = MutableStateFlow("Toate")
    val hiveFilter: StateFlow<String> = _hiveFilter.asStateFlow()

    private val _voiceModalOpen = MutableStateFlow(false)
    val voiceModalOpen: StateFlow<Boolean> = _voiceModalOpen.asStateFlow()

    private val _voiceInputText = MutableStateFlow("")
    val voiceInputText: StateFlow<String> = _voiceInputText.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<PendingVoiceConfirmation?>(null)
    val pendingConfirmation: StateFlow<PendingVoiceConfirmation?> = _pendingConfirmation.asStateFlow()

    private val _feedbackBanner = MutableStateFlow<String?>(null)
    val feedbackBanner: StateFlow<String?> = _feedbackBanner.asStateFlow()

    val apiary: StateFlow<ApiaryEntity?> = repo.primaryApiary
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allHives: StateFlow<List<HiveEntity>> = repo.allHives
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allObservations: StateFlow<List<ObservationEntity>> = repo.allObservations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allQueens: StateFlow<List<QueenEntity>> = repo.allQueens
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allActivities: StateFlow<List<ActivityEntity>> = repo.allActivities
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allReminders: StateFlow<List<ReminderEntity>> = repo.allReminders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allEvents: StateFlow<List<EventEntity>> = repo.allEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun navigateTo(section: AppSection) {
        _selectedHiveId.value = null
        _currentSection.value = section
    }

    fun openHiveDetail(hiveId: Long) {
        _selectedHiveId.value = hiveId
        _currentSection.value = AppSection.STUPI
    }

    fun setHiveFilter(filter: String) {
        _hiveFilter.value = filter
    }

    fun openVoiceModal() {
        _voiceInputText.value = ""
        _pendingConfirmation.value = null
        _voiceModalOpen.value = true
    }

    fun closeVoiceModal() {
        speechManager.stopListening()
        _voiceModalOpen.value = false
    }

    fun startOfflineVoiceListening() {
        speechManager.startListening { transcript ->
            _voiceInputText.value = transcript
            processVoiceText(transcript)
        }
    }

    fun updateVoiceInputText(text: String) {
        _voiceInputText.value = text
    }

    fun processVoiceText(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            when (val cmd = RomanianVoiceParser.parseCommandOrDictation(text)) {
                is VoiceCommand.OpenHive -> {
                    val hive = repo.getHiveByNumber(cmd.hiveNumber)
                    if (hive != null) {
                        openHiveDetail(hive.id)
                        _voiceModalOpen.value = false
                        announce("Am deschis fișa stupului ${cmd.hiveNumber}.")
                    } else {
                        announce("Stupul ${cmd.hiveNumber} nu a fost găsit.")
                    }
                }
                is VoiceCommand.ShowHiveHistory -> {
                    val hive = repo.getHiveByNumber(cmd.hiveNumber)
                    if (hive != null) {
                        openHiveDetail(hive.id)
                        _voiceModalOpen.value = false
                        announce("Afișez istoricul stupului ${cmd.hiveNumber}.")
                    } else {
                        announce("Stupul ${cmd.hiveNumber} nu există.")
                    }
                }
                is VoiceCommand.AddObservationToHive -> {
                    val parsed = ParsedDictation(
                        rawTranscript = text,
                        scope = ObservationScope.INDIVIDUAL,
                        hiveNumber = cmd.hiveNumber,
                        category = "Control",
                        detectedBroodFrames = null,
                        detectedQueenYear = null,
                        detectedFollowUpDays = null,
                        detectedStatus = null,
                        requiresConfirmation = true,
                        uncertaintyReason = "Introduceți observația pentru stupul ${cmd.hiveNumber}."
                    )
                    _pendingConfirmation.value = PendingVoiceConfirmation(parsed, cmd.hiveNumber, isGeneral = false)
                    announce("Pregătit pentru observație la stupul ${cmd.hiveNumber}.")
                }
                VoiceCommand.RecordGeneralObservation -> {
                    val parsed = ParsedDictation(
                        rawTranscript = "",
                        scope = ObservationScope.GENERAL,
                        hiveNumber = null,
                        category = "Observație generală",
                        detectedBroodFrames = null,
                        detectedQueenYear = null,
                        detectedFollowUpDays = null,
                        detectedStatus = null,
                        requiresConfirmation = true,
                        uncertaintyReason = "Observație generală la nivelul stupinei."
                    )
                    _pendingConfirmation.value = PendingVoiceConfirmation(parsed, null, isGeneral = true)
                    announce("Puteți dicta observația generală.")
                }
                VoiceCommand.FilterQueenlessHives -> {
                    _hiveFilter.value = "Fără matcă"
                    _selectedHiveId.value = null
                    _currentSection.value = AppSection.STUPI
                    _voiceModalOpen.value = false
                    val count = allHives.value.count { !it.isArchived && it.status == "Fără matcă" }
                    announce("Afișez $count familii fără matcă.")
                }
                VoiceCommand.FilterWeakHives -> {
                    _hiveFilter.value = "Slabă"
                    _selectedHiveId.value = null
                    _currentSection.value = AppSection.STUPI
                    _voiceModalOpen.value = false
                    val count = allHives.value.count { !it.isArchived && it.status == "Slabă" }
                    announce("Afișez $count familii slabe.")
                }
                VoiceCommand.CountHives -> {
                    val active = allHives.value.count { !it.isArchived }
                    announce("Aveți $active familii active în stupină.")
                }
                VoiceCommand.ShowLatestObservations -> {
                    _currentSection.value = AppSection.OBSERVATII
                    _voiceModalOpen.value = false
                    announce("Afișez ultimele observații.")
                }
                VoiceCommand.SaveCurrentObservation -> {
                    confirmAndSavePendingVoice()
                }
                VoiceCommand.CancelCurrentAction -> {
                    _pendingConfirmation.value = null
                    _voiceModalOpen.value = false
                    announce("Acțiune anulată.")
                }
                is VoiceCommand.FallbackDictation -> {
                    val p = cmd.parsed
                    if (!p.requiresConfirmation && p.scope != ObservationScope.UNCERTAIN) {
                        saveParsedObservation(p, p.hiveNumber, p.scope == ObservationScope.GENERAL)
                        _voiceModalOpen.value = false
                    } else {
                        _pendingConfirmation.value = PendingVoiceConfirmation(
                            parsed = p,
                            selectedHiveNumber = p.hiveNumber,
                            isGeneral = p.scope == ObservationScope.GENERAL
                        )
                        announce(p.uncertaintyReason ?: "Confirmați salvarea observației.")
                    }
                }
            }
        }
    }

    fun confirmAndSavePendingVoice(overrideHiveNumber: Int? = null, forceGeneral: Boolean? = null) {
        val pending = _pendingConfirmation.value ?: return
        viewModelScope.launch {
            val isGeneral = forceGeneral ?: pending.isGeneral
            val hiveNum = if (isGeneral) null else (overrideHiveNumber ?: pending.selectedHiveNumber)
            saveParsedObservation(pending.parsed, hiveNum, isGeneral)
            _pendingConfirmation.value = null
            _voiceModalOpen.value = false
        }
    }

    private suspend fun saveParsedObservation(parsed: ParsedDictation, hiveNumber: Int?, isGeneral: Boolean) {
        val targetNumber = if (isGeneral) null else hiveNumber
        repo.saveObservation(
            hiveNumber = targetNumber,
            category = parsed.category,
            text = parsed.rawTranscript,
            voiceTranscript = parsed.rawTranscript,
            isVoiceRecorded = true,
            autoFollowUpDays = parsed.detectedFollowUpDays
        )
        if (targetNumber != null) {
            val hive = repo.getHiveByNumber(targetNumber)
            if (hive != null && (parsed.detectedBroodFrames != null || parsed.detectedStatus != null)) {
                repo.updateHive(
                    hive.copy(
                        broodFrames = parsed.detectedBroodFrames ?: hive.broodFrames,
                        status = parsed.detectedStatus ?: hive.status
                    )
                )
            }
            announce("Observație salvată la stupul $targetNumber.")
        } else {
            announce("Observație generală salvată la stupină.")
        }
    }

    fun addHive(
        hiveNumber: Int,
        familyType: String,
        hiveType: String,
        status: String,
        broodFrames: Int,
        totalFrames: Int,
        locationZone: String,
        queenYear: Int?,
        queenOrigin: String
    ) {
        viewModelScope.launch {
            val result = repo.addHive(hiveNumber, familyType, hiveType, status, broodFrames, totalFrames, locationZone, queenYear, queenOrigin)
            _feedbackBanner.value = result.fold(
                onSuccess = { "Stupul $hiveNumber a fost adăugat." },
                onFailure = { it.message ?: "Eroare la adăugare." }
            )
        }
    }

    fun addManualObservation(hiveNumber: Int?, category: String, text: String, tempCelsius: Double?, honeyKg: Double?) {
        viewModelScope.launch {
            repo.saveObservation(hiveNumber, category, text, temperatureCelsius = tempCelsius, honeyKg = honeyKg)
            _feedbackBanner.value = if (hiveNumber == null) "Observație generală salvată." else "Observație salvată pentru stupul $hiveNumber."
        }
    }

    fun toggleReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            repo.updateReminder(
                reminder.copy(
                    isCompleted = !reminder.isCompleted,
                    completedAt = if (!reminder.isCompleted) System.currentTimeMillis() else null
                )
            )
        }
    }

    fun addReminder(hiveNumber: Int?, title: String, category: String, daysFromNow: Int) {
        viewModelScope.launch {
            val hive = hiveNumber?.let { repo.getHiveByNumber(it) }
            repo.addReminder(
                ReminderEntity(
                    hiveId = hive?.id,
                    hiveNumberSnapshot = hiveNumber,
                    title = title,
                    category = category,
                    dueDate = System.currentTimeMillis() + daysFromNow * 86_400_000L
                )
            )
            _feedbackBanner.value = "Memento programat peste $daysFromNow zile."
        }
    }

    fun archiveHive(hive: HiveEntity) {
        viewModelScope.launch {
            repo.archiveHive(hive, "Arhivat din fișa stupului")
            _feedbackBanner.value = "Stupul ${hive.hiveNumber} a fost arhivat. Istoricul este păstrat."
        }
    }

    fun changeQueen(hive: HiveEntity, year: Int, origin: String, notes: String) {
        viewModelScope.launch {
            repo.changeQueen(hive, year, origin, "Carpatină", "Alb", notes)
            _feedbackBanner.value = "Regina stupului ${hive.hiveNumber} a fost schimbată."
        }
    }

    suspend fun buildBackupJson(): String = BackupAndReportManager.serializeBackupToJson(repo.exportSnapshot())

    fun restoreFromJson(json: String) {
        viewModelScope.launch {
            try {
                val payload = BackupAndReportManager.deserializeBackupFromJson(json)
                repo.restoreSnapshot(payload)
                _feedbackBanner.value = "Backup restaurat cu succes (${payload.hives.size} stupi)."
            } catch (e: Exception) {
                _feedbackBanner.value = "Eroare la restaurarea backup-ului: ${e.message}"
            }
        }
    }

    fun buildCsvExport(): String = BackupAndReportManager.generateHivesCsv(allHives.value, allQueens.value)
    fun buildExcelExport(): String = BackupAndReportManager.generateExcelSpreadsheetXml(allHives.value, allObservations.value, allActivities.value)

    private fun announce(message: String) {
        _feedbackBanner.value = message
        ttsManager.speakConfirmation(message)
    }

    override fun onCleared() {
        speechManager.release()
        ttsManager.shutdown()
        super.onCleared()
    }
}
