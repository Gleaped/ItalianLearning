package com.gleaped.italianlearning.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        LessonProgressEntity::class,
        ExerciseResultEntity::class,
        SrsCardEntity::class,
        ActivityDayEntity::class,
        ReminderEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao
    abstract fun srsDao(): SrsDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "italiano.db")
                .build()
    }
}
