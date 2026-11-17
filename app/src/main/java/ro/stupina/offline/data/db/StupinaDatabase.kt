package ro.stupina.offline.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        ApiaryEntity::class,
        HiveEntity::class,
        ObservationEntity::class,
        QueenEntity::class,
        ActivityEntity::class,
        ReminderEntity::class,
        EventEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class StupinaDatabase : RoomDatabase() {
    abstract fun apiaryDao(): ApiaryDao
    abstract fun hiveDao(): HiveDao
    abstract fun observationDao(): ObservationDao
    abstract fun queenDao(): QueenDao
    abstract fun activityDao(): ActivityDao
    abstract fun reminderDao(): ReminderDao
    abstract fun eventDao(): EventDao

    companion object {
        @Volatile
        private var INSTANCE: StupinaDatabase? = null

        fun getInstance(context: Context): StupinaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StupinaDatabase::class.java,
                    "stupina_offline.db"
                )
                    .addCallback(SeedCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class SeedCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    seedInitialData(database)
                }
            }
        }

        private suspend fun seedInitialData(db: StupinaDatabase) {
            if (db.apiaryDao().getAllSync().isNotEmpty()) return
            val now = System.currentTimeMillis()
            val day = 86_400_000L

            val apiaryId = db.apiaryDao().insert(
                ApiaryEntity(
                    id = 1L,
                    name = "Stupina Valea Salcâmilor",
                    location = "Dealul Viei, Vrancea",
                    ownerName = "Apicultor",
                    notes = "Vatră permanentă cu cules de salcâm și tei",
                    createdAt = now - 180 * day
                )
            )

            val h1 = db.hiveDao().insert(
                HiveEntity(apiaryId = apiaryId, hiveNumber = 1, establishedDate = now - 365 * day, familyType = "Producție", hiveType = "Dadant 10 rame", status = "Productivă", broodFrames = 7, totalFrames = 10, locationZone = "Rândul 1")
            )
            val h8 = db.hiveDao().insert(
                HiveEntity(apiaryId = apiaryId, hiveNumber = 8, establishedDate = now - 200 * day, familyType = "Producție", hiveType = "Dadant 10 rame", status = "Slabă", broodFrames = 3, totalFrames = 7, locationZone = "Rândul 1")
            )
            val h12 = db.hiveDao().insert(
                HiveEntity(apiaryId = apiaryId, hiveNumber = 12, establishedDate = now - 400 * day, familyType = "Producție", hiveType = "ME (Langstroth)", status = "Productivă", broodFrames = 8, totalFrames = 20, locationZone = "Rândul 2")
            )
            val h15 = db.hiveDao().insert(
                HiveEntity(apiaryId = apiaryId, hiveNumber = 15, establishedDate = now - 90 * day, familyType = "Nucleu", hiveType = "Nucleu 6 rame", status = "În dezvoltare", broodFrames = 4, totalFrames = 6, locationZone = "Zona nuclee")
            )
            val h19 = db.hiveDao().insert(
                HiveEntity(apiaryId = apiaryId, hiveNumber = 19, establishedDate = now - 300 * day, familyType = "Producție", hiveType = "Dadant 10 rame", status = "Fără matcă", broodFrames = 2, totalFrames = 8, locationZone = "Rândul 2")
            )

            db.queenDao().insertAll(
                listOf(
                    QueenEntity(hiveId = h1, hatchYear = 2025, origin = "Proprie", breed = "Carpatină", markedColor = "Albastru", notes = "Pontă compactă", installedAt = now - 300 * day),
                    QueenEntity(hiveId = h8, hatchYear = 2024, origin = "Roire naturală", breed = "Carpatină", markedColor = "Verde", notes = "Necesită urmărire", installedAt = now - 190 * day),
                    QueenEntity(hiveId = h12, hatchYear = 2026, origin = "Translarvare", breed = "Carpatină selecționată", markedColor = "Alb", notes = "Matcă tânără foarte prolifică", installedAt = now - 60 * day),
                    QueenEntity(hiveId = h15, hatchYear = 2026, origin = "Proprie", breed = "Carpatină", markedColor = "Alb", notes = "Împerecheată în mai", installedAt = now - 50 * day)
                )
            )

            db.observationDao().insertAll(
                listOf(
                    ObservationEntity(apiaryId = apiaryId, hiveId = null, category = "Cules / Înflorire", text = "Astăzi a început înflorirea salcâmului în lizieră. Temperatură 22°C, zbor intens.", temperatureCelsius = 22.0, timestamp = now - 2 * day),
                    ObservationEntity(apiaryId = apiaryId, hiveId = h12, hiveNumberSnapshot = 12, category = "Control", text = "Familie puternică, 8 rame de puiet, adăugat corpul al treilea.", timestamp = now - day),
                    ObservationEntity(apiaryId = apiaryId, hiveId = h8, hiveNumberSnapshot = 8, category = "Control", text = "Familie slabă, 3 rame de puiet, rezerve moderate. Necesită verificare peste 7 zile.", timestamp = now - day / 2),
                    ObservationEntity(apiaryId = apiaryId, hiveId = h19, hiveNumberSnapshot = 19, category = "Control", text = "Nu s-au observat ouă de o zi; albine agitate. Posibilă pierdere matcă.", timestamp = now - 3600_000L * 4)
                )
            )

            db.activityDao().insertAll(
                listOf(
                    ActivityEntity(apiaryId = apiaryId, hiveId = null, activityType = "Tratament", description = "Tratament general primăvară împotriva Varroa", substanceOrMaterial = "Acid oxalic", quantity = "35 ml/stup", performedAt = now - 25 * day),
                    ActivityEntity(apiaryId = apiaryId, hiveId = h12, hiveNumberSnapshot = 12, activityType = "Adăugare corp", description = "Adăugat corpul 3 cu rame clădite", substanceOrMaterial = "Corp ME 10 rame", quantity = "1 corp", performedAt = now - day),
                    ActivityEntity(apiaryId = apiaryId, hiveId = h1, hiveNumberSnapshot = 1, activityType = "Recoltare miere", description = "Recoltare miere polifloră de primăvară", substanceOrMaterial = "Rame căpăcite", quantity = "14 kg", performedAt = now - 7 * day)
                )
            )

            db.reminderDao().insertAll(
                listOf(
                    ReminderEntity(apiaryId = apiaryId, hiveId = h19, hiveNumberSnapshot = 19, title = "Verificare prezență botci sau introducere matcă", category = "Verificare botci", dueDate = now + day, notes = "Stupul 19 este fără matcă"),
                    ReminderEntity(apiaryId = apiaryId, hiveId = h8, hiveNumberSnapshot = 8, title = "Verificare evoluție puiet și rezerve hrană", category = "Verificare periodică", dueDate = now + 5 * day, notes = "Reevaluare după restrângere cuib"),
                    ReminderEntity(apiaryId = apiaryId, hiveId = null, title = "Monitorizare cădere Varroa pe fundul antivarroa", category = "Cădere Varroa", dueDate = now - day, notes = "Sarcină scadentă la nivelul întregii stupine")
                )
            )
        }
    }
}
