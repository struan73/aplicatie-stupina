package ro.stupina.offline.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ApiaryDao {
    @Query("SELECT * FROM apiaries ORDER BY id ASC LIMIT 1")
    fun observePrimaryApiary(): Flow<ApiaryEntity?>

    @Query("SELECT * FROM apiaries")
    suspend fun getAllSync(): List<ApiaryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(apiary: ApiaryEntity): Long

    @Update
    suspend fun update(apiary: ApiaryEntity)

    @Query("DELETE FROM apiaries")
    suspend fun deleteAll()
}

@Dao
interface HiveDao {
    @Query("SELECT * FROM hives ORDER BY hiveNumber ASC")
    fun observeAllHives(): Flow<List<HiveEntity>>

    @Query("SELECT * FROM hives WHERE isArchived = 0 ORDER BY hiveNumber ASC")
    fun observeActiveHives(): Flow<List<HiveEntity>>

    @Query("SELECT * FROM hives WHERE hiveNumber = :hiveNumber LIMIT 1")
    suspend fun getByHiveNumber(hiveNumber: Int): HiveEntity?

    @Query("SELECT * FROM hives WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): HiveEntity?

    @Query("SELECT * FROM hives ORDER BY hiveNumber ASC")
    suspend fun getAllSync(): List<HiveEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(hive: HiveEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(hives: List<HiveEntity>)

    @Update
    suspend fun update(hive: HiveEntity)

    @Query("DELETE FROM hives")
    suspend fun deleteAll()
}

@Dao
interface ObservationDao {
    @Query("SELECT * FROM observations ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<ObservationEntity>>

    @Query("SELECT * FROM observations WHERE hiveId IS NULL ORDER BY timestamp DESC")
    fun observeGeneralApiaryObservations(): Flow<List<ObservationEntity>>

    @Query("SELECT * FROM observations WHERE hiveId = :hiveId ORDER BY timestamp DESC")
    fun observeForHive(hiveId: Long): Flow<List<ObservationEntity>>

    @Query("SELECT * FROM observations ORDER BY timestamp DESC")
    suspend fun getAllSync(): List<ObservationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(observation: ObservationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(observations: List<ObservationEntity>)

    @Update
    suspend fun update(observation: ObservationEntity)

    @Query("DELETE FROM observations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM observations")
    suspend fun deleteAll()
}

@Dao
interface QueenDao {
    @Query("SELECT * FROM queens ORDER BY installedAt DESC")
    fun observeAll(): Flow<List<QueenEntity>>

    @Query("SELECT * FROM queens WHERE hiveId = :hiveId ORDER BY installedAt DESC")
    fun observeForHive(hiveId: Long): Flow<List<QueenEntity>>

    @Query("UPDATE queens SET isActive = 0 WHERE hiveId = :hiveId")
    suspend fun deactivateForHive(hiveId: Long)

    @Query("SELECT * FROM queens")
    suspend fun getAllSync(): List<QueenEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(queen: QueenEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(queens: List<QueenEntity>)

    @Query("DELETE FROM queens")
    suspend fun deleteAll()
}

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activities ORDER BY performedAt DESC")
    fun observeAll(): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities WHERE hiveId = :hiveId ORDER BY performedAt DESC")
    fun observeForHive(hiveId: Long): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities ORDER BY performedAt DESC")
    suspend fun getAllSync(): List<ActivityEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(activity: ActivityEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(activities: List<ActivityEntity>)

    @Query("DELETE FROM activities")
    suspend fun deleteAll()
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY isCompleted ASC, dueDate ASC")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders ORDER BY dueDate ASC")
    suspend fun getAllSync(): List<ReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: ReminderEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reminders: List<ReminderEntity>)

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM reminders")
    suspend fun deleteAll()
}

@Dao
interface EventDao {
    @Query("SELECT * FROM events ORDER BY eventDate DESC")
    fun observeAll(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE primaryHiveId = :hiveId ORDER BY eventDate DESC")
    fun observeForHive(hiveId: Long): Flow<List<EventEntity>>

    @Query("SELECT * FROM events ORDER BY eventDate DESC")
    suspend fun getAllSync(): List<EventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: EventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<EventEntity>)

    @Query("DELETE FROM events")
    suspend fun deleteAll()
}
