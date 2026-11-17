package ro.stupina.offline.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import ro.stupina.offline.data.db.*

class StupinaRepository(private val db: StupinaDatabase) {

    val primaryApiary: Flow<ApiaryEntity?> = db.apiaryDao().observePrimaryApiary()
    val allHives: Flow<List<HiveEntity>> = db.hiveDao().observeAllHives()
    val activeHives: Flow<List<HiveEntity>> = db.hiveDao().observeActiveHives()
    val allObservations: Flow<List<ObservationEntity>> = db.observationDao().observeAll()
    val generalObservations: Flow<List<ObservationEntity>> = db.observationDao().observeGeneralApiaryObservations()
    val allQueens: Flow<List<QueenEntity>> = db.queenDao().observeAll()
    val allActivities: Flow<List<ActivityEntity>> = db.activityDao().observeAll()
    val allReminders: Flow<List<ReminderEntity>> = db.reminderDao().observeAll()
    val allEvents: Flow<List<EventEntity>> = db.eventDao().observeAll()

    fun observeHiveObservations(hiveId: Long): Flow<List<ObservationEntity>> =
        db.observationDao().observeForHive(hiveId)

    fun observeHiveQueens(hiveId: Long): Flow<List<QueenEntity>> =
        db.queenDao().observeForHive(hiveId)

    fun observeHiveActivities(hiveId: Long): Flow<List<ActivityEntity>> =
        db.activityDao().observeForHive(hiveId)

    fun observeHiveEvents(hiveId: Long): Flow<List<EventEntity>> =
        db.eventDao().observeForHive(hiveId)

    suspend fun getHiveByNumber(hiveNumber: Int): HiveEntity? =
        db.hiveDao().getByHiveNumber(hiveNumber)

    suspend fun updateApiary(apiary: ApiaryEntity) =
        db.apiaryDao().update(apiary)

    suspend fun addHive(
        hiveNumber: Int,
        familyType: String,
        hiveType: String,
        status: String,
        broodFrames: Int,
        totalFrames: Int,
        locationZone: String,
        queenYear: Int?,
        queenOrigin: String
    ): Result<Long> {
        val existing = db.hiveDao().getByHiveNumber(hiveNumber)
        if (existing != null) {
            return Result.failure(IllegalStateException("Stupul cu numărul $hiveNumber există deja."))
        }
        val hiveId = db.withTransaction {
            val newId = db.hiveDao().insert(
                HiveEntity(
                    hiveNumber = hiveNumber,
                    establishedDate = System.currentTimeMillis(),
                    familyType = familyType,
                    hiveType = hiveType,
                    status = status,
                    broodFrames = broodFrames,
                    totalFrames = totalFrames,
                    locationZone = locationZone
                )
            )
            if (queenYear != null && status != "Fără matcă") {
                db.queenDao().insert(
                    QueenEntity(
                        hiveId = newId,
                        hatchYear = queenYear,
                        origin = queenOrigin.ifBlank { "Proprie" },
                        isActive = true
                    )
                )
            }
            newId
        }
        return Result.success(hiveId)
    }

    suspend fun updateHive(hive: HiveEntity) {
        db.hiveDao().update(hive.copy(updatedAt = System.currentTimeMillis()))
    }

    /**
     * Arhivează familia fără a șterge istoricul observațiilor, reginelor sau activităților.
     */
    suspend fun archiveHive(hive: HiveEntity, reason: String) {
        db.withTransaction {
            val now = System.currentTimeMillis()
            db.hiveDao().update(
                hive.copy(
                    isArchived = true,
                    archivedAt = now,
                    status = "Arhivată",
                    updatedAt = now
                )
            )
            db.eventDao().insert(
                EventEntity(
                    primaryHiveId = hive.id,
                    eventType = "Arhivare",
                    description = "Stupul ${hive.hiveNumber} a fost arhivat (istoric păstrat): $reason",
                    eventDate = now
                )
            )
        }
    }

    suspend fun changeQueen(
        hive: HiveEntity,
        hatchYear: Int,
        origin: String,
        breed: String,
        markedColor: String,
        notes: String
    ) {
        db.withTransaction {
            val now = System.currentTimeMillis()
            db.queenDao().deactivateForHive(hive.id)
            db.queenDao().insert(
                QueenEntity(
                    hiveId = hive.id,
                    hatchYear = hatchYear,
                    origin = origin,
                    breed = breed,
                    markedColor = markedColor,
                    notes = notes,
                    installedAt = now,
                    isActive = true
                )
            )
            if (hive.status == "Fără matcă") {
                db.hiveDao().update(hive.copy(status = "Productivă", updatedAt = now))
            }
            db.eventDao().insert(
                EventEntity(
                    primaryHiveId = hive.id,
                    eventType = "Schimbare regină",
                    description = "Schimbare matcă la stupul ${hive.hiveNumber}: an $hatchYear, origine $origin",
                    eventDate = now
                )
            )
        }
    }

    suspend fun convertFamilyType(hive: HiveEntity, newFamilyType: String) {
        db.withTransaction {
            val now = System.currentTimeMillis()
            val eventType = if (newFamilyType == "Nucleu") "Transformare în nucleu" else "Transformare în familie"
            db.hiveDao().update(hive.copy(familyType = newFamilyType, updatedAt = now))
            db.eventDao().insert(
                EventEntity(
                    primaryHiveId = hive.id,
                    eventType = eventType,
                    description = "Stupul ${hive.hiveNumber} transformat din ${hive.familyType} în $newFamilyType",
                    eventDate = now
                )
            )
        }
    }

    suspend fun moveHiveLocation(hive: HiveEntity, newLocationZone: String) {
        db.withTransaction {
            val now = System.currentTimeMillis()
            val oldLoc = hive.locationZone
            db.hiveDao().update(hive.copy(locationZone = newLocationZone, updatedAt = now))
            db.eventDao().insert(
                EventEntity(
                    primaryHiveId = hive.id,
                    eventType = "Mutare",
                    description = "Mutare stup ${hive.hiveNumber}: $oldLoc -> $newLocationZone",
                    fromLocation = oldLoc,
                    toLocation = newLocationZone,
                    eventDate = now
                )
            )
        }
    }

    suspend fun splitHive(
        sourceHive: HiveEntity,
        newHiveNumber: Int,
        framesMoved: Int,
        newFamilyType: String
    ): Result<Long> {
        if (db.hiveDao().getByHiveNumber(newHiveNumber) != null) {
            return Result.failure(IllegalStateException("Numărul de stup $newHiveNumber este deja folosit."))
        }
        val newId = db.withTransaction {
            val now = System.currentTimeMillis()
            val insertedId = db.hiveDao().insert(
                HiveEntity(
                    hiveNumber = newHiveNumber,
                    establishedDate = now,
                    familyType = newFamilyType,
                    hiveType = sourceHive.hiveType,
                    status = "În dezvoltare",
                    broodFrames = framesMoved,
                    totalFrames = framesMoved + 2,
                    locationZone = sourceHive.locationZone
                )
            )
            db.hiveDao().update(
                sourceHive.copy(
                    broodFrames = (sourceHive.broodFrames - framesMoved).coerceAtLeast(1),
                    updatedAt = now
                )
            )
            db.eventDao().insert(
                EventEntity(
                    primaryHiveId = sourceHive.id,
                    secondaryHiveNumber = newHiveNumber,
                    eventType = "Divizare",
                    description = "Divizare stup ${sourceHive.hiveNumber} -> format stup nou $newHiveNumber ($newFamilyType, $framesMoved rame puiet)",
                    eventDate = now
                )
            )
            insertedId
        }
        return Result.success(newId)
    }

    suspend fun mergeHives(absorberHive: HiveEntity, absorbedHive: HiveEntity) {
        db.withTransaction {
            val now = System.currentTimeMillis()
            db.hiveDao().update(
                absorberHive.copy(
                    broodFrames = absorberHive.broodFrames + absorbedHive.broodFrames,
                    totalFrames = (absorberHive.totalFrames + absorbedHive.totalFrames).coerceAtMost(24),
                    updatedAt = now
                )
            )
            db.hiveDao().update(
                absorbedHive.copy(
                    isArchived = true,
                    archivedAt = now,
                    status = "Arhivată",
                    updatedAt = now
                )
            )
            db.eventDao().insert(
                EventEntity(
                    primaryHiveId = absorberHive.id,
                    secondaryHiveNumber = absorbedHive.hiveNumber,
                    eventType = "Unire",
                    description = "Unire: stupul ${absorbedHive.hiveNumber} a fost unit cu stupul ${absorberHive.hiveNumber}",
                    eventDate = now
                )
            )
        }
    }

    suspend fun saveObservation(
        hiveNumber: Int?,
        category: String,
        text: String,
        voiceTranscript: String? = null,
        isVoiceRecorded: Boolean = false,
        temperatureCelsius: Double? = null,
        honeyKg: Double? = null,
        autoFollowUpDays: Int? = null
    ): Long {
        val now = System.currentTimeMillis()
        val targetHive = hiveNumber?.let { db.hiveDao().getByHiveNumber(it) }
        val obsId = db.observationDao().insert(
            ObservationEntity(
                hiveId = targetHive?.id,
                hiveNumberSnapshot = targetHive?.hiveNumber ?: hiveNumber,
                timestamp = now,
                category = category,
                text = text,
                voiceTranscript = voiceTranscript,
                isVoiceRecorded = isVoiceRecorded,
                temperatureCelsius = temperatureCelsius,
                honeyKg = honeyKg
            )
        )
        if (autoFollowUpDays != null && autoFollowUpDays > 0) {
            db.reminderDao().insert(
                ReminderEntity(
                    hiveId = targetHive?.id,
                    hiveNumberSnapshot = targetHive?.hiveNumber ?: hiveNumber,
                    title = "Verificare programată prin dictare: $text".take(100),
                    category = "Verificare periodică",
                    dueDate = now + autoFollowUpDays * 86_400_000L
                )
            )
        }
        return obsId
    }

    suspend fun addActivity(activity: ActivityEntity): Long = db.activityDao().insert(activity)
    suspend fun addReminder(reminder: ReminderEntity): Long = db.reminderDao().insert(reminder)
    suspend fun updateReminder(reminder: ReminderEntity) = db.reminderDao().update(reminder)
    suspend fun deleteReminder(id: Long) = db.reminderDao().deleteById(id)

    suspend fun exportSnapshot(): ApiaryBackupPayload {
        return ApiaryBackupPayload(
            apiaries = db.apiaryDao().getAllSync(),
            hives = db.hiveDao().getAllSync(),
            observations = db.observationDao().getAllSync(),
            queens = db.queenDao().getAllSync(),
            activities = db.activityDao().getAllSync(),
            reminders = db.reminderDao().getAllSync(),
            events = db.eventDao().getAllSync()
        )
    }

    suspend fun restoreSnapshot(payload: ApiaryBackupPayload) {
        db.withTransaction {
            db.eventDao().deleteAll()
            db.reminderDao().deleteAll()
            db.activityDao().deleteAll()
            db.queenDao().deleteAll()
            db.observationDao().deleteAll()
            db.hiveDao().deleteAll()
            db.apiaryDao().deleteAll()

            payload.apiaries.forEach { db.apiaryDao().insert(it) }
            db.hiveDao().insertAll(payload.hives)
            db.observationDao().insertAll(payload.observations)
            db.queenDao().insertAll(payload.queens)
            db.activityDao().insertAll(payload.activities)
            db.reminderDao().insertAll(payload.reminders)
            db.eventDao().insertAll(payload.events)
        }
    }
}

data class ApiaryBackupPayload(
    val apiaries: List<ApiaryEntity>,
    val hives: List<HiveEntity>,
    val observations: List<ObservationEntity>,
    val queens: List<QueenEntity>,
    val activities: List<ActivityEntity>,
    val reminders: List<ReminderEntity>,
    val events: List<EventEntity>
)
