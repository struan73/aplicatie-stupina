package ro.stupina.offline.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stupina (Apiary) - Date generale despre locația și configurația stupinei.
 */
@Entity(tableName = "apiaries")
data class ApiaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val location: String,
    val ownerName: String,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Stup (Hive) - Fișa individuală a fiecărei familii, identificată prin numărul unic al stupului.
 */
@Entity(
    tableName = "hives",
    foreignKeys = [
        ForeignKey(
            entity = ApiaryEntity::class,
            parentColumns = ["id"],
            childColumns = ["apiaryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["hiveNumber"], unique = true),
        Index(value = ["apiaryId"])
    ]
)
data class HiveEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val apiaryId: Long = 1L,
    val hiveNumber: Int,
    val establishedDate: Long,
    val familyType: String, // "Producție", "Nucleu", "Rezervă", "Roi"
    val hiveType: String,   // "Dadant 10 rame", "ME (Langstroth)", "Vertical 12 rame", "Orizontal"
    val status: String,     // "Productivă", "Slabă", "Fără matcă", "În dezvoltare", "Arhivată"
    val broodFrames: Int = 0,
    val totalFrames: Int = 10,
    val locationZone: String = "Vatră principală",
    val isArchived: Boolean = false,
    val archivedAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Observație (Observation) - Asociată fie întregii stupine (hiveId = null), fie unui stup individual.
 */
@Entity(
    tableName = "observations",
    foreignKeys = [
        ForeignKey(
            entity = ApiaryEntity::class,
            parentColumns = ["id"],
            childColumns = ["apiaryId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = HiveEntity::class,
            parentColumns = ["id"],
            childColumns = ["hiveId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["apiaryId"]), Index(value = ["hiveId"]), Index(value = ["timestamp"])]
)
data class ObservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val apiaryId: Long = 1L,
    val hiveId: Long? = null, // null => observație generală la nivelul stupinei
    val hiveNumberSnapshot: Int? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val category: String,     // "Control", "Cules / Înflorire", "Vreme", "Tratament", "Lucrare", "Producție miere", "Observație generală"
    val text: String,
    val voiceTranscript: String? = null,
    val isVoiceRecorded: Boolean = false,
    val temperatureCelsius: Double? = null,
    val honeyKg: Double? = null
)

/**
 * Regină (Queen) - Istoricul complet al reginelor pentru fiecare stup.
 */
@Entity(
    tableName = "queens",
    foreignKeys = [
        ForeignKey(
            entity = HiveEntity::class,
            parentColumns = ["id"],
            childColumns = ["hiveId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["hiveId"])]
)
data class QueenEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hiveId: Long,
    val hatchYear: Int,
    val origin: String,       // "Proprie", "Cumpărată", "Translarvare", "Roire naturală"
    val breed: String = "Carpatină",
    val markedColor: String = "Alb",
    val notes: String = "",
    val installedAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)

/**
 * Activitate / Intervenție (Activity) - Lucrări, tratamente și recoltări efectuate.
 */
@Entity(
    tableName = "activities",
    foreignKeys = [
        ForeignKey(
            entity = ApiaryEntity::class,
            parentColumns = ["id"],
            childColumns = ["apiaryId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = HiveEntity::class,
            parentColumns = ["id"],
            childColumns = ["hiveId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["apiaryId"]), Index(value = ["hiveId"]), Index(value = ["performedAt"])]
)
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val apiaryId: Long = 1L,
    val hiveId: Long? = null,
    val hiveNumberSnapshot: Int? = null,
    val activityType: String, // "Tratament", "Adăugare corp", "Hrănire", "Recoltare miere", "Verificare puiet", "Schimbare matcă"
    val description: String,
    val substanceOrMaterial: String = "",
    val quantity: String = "",
    val performedAt: Long = System.currentTimeMillis()
)

/**
 * Memento (Reminder) - Sarcini viitoare și verificări programate.
 */
@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = ApiaryEntity::class,
            parentColumns = ["id"],
            childColumns = ["apiaryId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = HiveEntity::class,
            parentColumns = ["id"],
            childColumns = ["hiveId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["apiaryId"]), Index(value = ["hiveId"]), Index(value = ["dueDate"])]
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val apiaryId: Long = 1L,
    val hiveId: Long? = null,
    val hiveNumberSnapshot: Int? = null,
    val title: String,
    val category: String, // "Verificare periodică", "Verificare botci", "Împerechere regină", "Cădere Varroa", "Repetare tratament", "Rezerve hrană"
    val notes: String = "",
    val dueDate: Long,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null
)

/**
 * Eveniment (Event) - Divizări, uniri, mutări, transformări familie <-> nucleu, arhivări.
 */
@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = ApiaryEntity::class,
            parentColumns = ["id"],
            childColumns = ["apiaryId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = HiveEntity::class,
            parentColumns = ["id"],
            childColumns = ["primaryHiveId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["apiaryId"]), Index(value = ["primaryHiveId"]), Index(value = ["eventDate"])]
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val apiaryId: Long = 1L,
    val primaryHiveId: Long? = null,
    val secondaryHiveNumber: Int? = null,
    val eventType: String, // "Divizare", "Unire", "Mutare", "Transformare în nucleu", "Transformare în familie", "Arhivare", "Schimbare regină"
    val description: String,
    val fromLocation: String? = null,
    val toLocation: String? = null,
    val eventDate: Long = System.currentTimeMillis()
)
