package com.threadmill.connect.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        WorkoutEntity::class,
        IntervalEntity::class,
        RunEntity::class,
        RunIntervalEntity::class,
        SpeedSampleEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workouts(): WorkoutDao
    abstract fun runs(): RunDao

    companion object {
        fun create(context: Context): AppDatabase = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "treadmill.db",
        ).build()
    }
}
