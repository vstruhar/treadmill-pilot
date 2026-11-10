package com.threadmill.connect.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Transaction
    @Query("SELECT * FROM workouts ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<WorkoutWithIntervals>>

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :id")
    fun observe(id: String): Flow<WorkoutWithIntervals?>

    @Transaction
    @Query("SELECT * FROM workouts ORDER BY updatedAt DESC")
    suspend fun all(): List<WorkoutWithIntervals>

    @Query("SELECT COUNT(*) FROM workouts")
    suspend fun count(): Int

    @Query("SELECT id FROM workouts")
    suspend fun ids(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: WorkoutEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIntervals(entities: List<IntervalEntity>)

    @Query("DELETE FROM intervals WHERE workoutId = :workoutId")
    suspend fun deleteIntervals(workoutId: String)

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface RunDao {
    @Transaction
    @Query("SELECT * FROM runs ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<RunWithDetails>>

    @Transaction
    @Query("SELECT * FROM runs WHERE id = :id")
    fun observe(id: String): Flow<RunWithDetails?>

    @Transaction
    @Query("SELECT * FROM runs ORDER BY startedAt DESC")
    suspend fun all(): List<RunWithDetails>

    @Query("SELECT id FROM runs")
    suspend fun ids(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: RunEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIntervals(entities: List<RunIntervalEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSamples(entities: List<SpeedSampleEntity>)

    @Query("DELETE FROM runs WHERE id = :id")
    suspend fun delete(id: String)
}
