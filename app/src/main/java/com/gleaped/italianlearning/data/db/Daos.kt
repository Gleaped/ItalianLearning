package com.gleaped.italianlearning.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {
    @Query("SELECT * FROM lesson_progress")
    fun observeLessons(): Flow<List<LessonProgressEntity>>

    @Query("SELECT * FROM lesson_progress WHERE lessonId = :lessonId")
    suspend fun lesson(lessonId: String): LessonProgressEntity?

    @Upsert
    suspend fun upsertLesson(entity: LessonProgressEntity)

    @Query("SELECT DISTINCT unitId FROM lesson_progress")
    suspend fun startedUnitIds(): List<String>

    @Insert
    suspend fun insertResults(results: List<ExerciseResultEntity>)

    @Query("SELECT * FROM activity_days")
    fun observeActivity(): Flow<List<ActivityDayEntity>>

    @Query("SELECT * FROM activity_days WHERE epochDay = :day")
    suspend fun activity(day: Long): ActivityDayEntity?

    @Upsert
    suspend fun upsertActivity(entity: ActivityDayEntity)

    @Query("DELETE FROM lesson_progress")
    suspend fun clearLessons()

    @Query("DELETE FROM exercise_results")
    suspend fun clearResults()

    @Query("DELETE FROM activity_days")
    suspend fun clearActivity()
}

@Dao
interface SrsDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(cards: List<SrsCardEntity>)

    @Update
    suspend fun update(card: SrsCardEntity)

    @Query("SELECT * FROM srs_cards WHERE firstReviewedEpochDay IS NOT NULL AND dueEpochDay <= :today ORDER BY dueEpochDay, id")
    suspend fun dueReviews(today: Long): List<SrsCardEntity>

    @Query("SELECT * FROM srs_cards WHERE firstReviewedEpochDay IS NULL ORDER BY rowid LIMIT :limit")
    suspend fun newCards(limit: Int): List<SrsCardEntity>

    @Query("SELECT COUNT(*) FROM srs_cards WHERE firstReviewedEpochDay = :today")
    suspend fun newIntroducedOn(today: Long): Int

    @Query("SELECT COUNT(*) FROM srs_cards WHERE firstReviewedEpochDay IS NOT NULL AND dueEpochDay <= :today")
    fun observeDueReviewCount(today: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM srs_cards WHERE firstReviewedEpochDay IS NULL")
    fun observeNewCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM srs_cards WHERE firstReviewedEpochDay IS NOT NULL AND dueEpochDay <= :today")
    suspend fun dueReviewCount(today: Long): Int

    @Query("SELECT COUNT(*) FROM srs_cards WHERE firstReviewedEpochDay IS NULL")
    suspend fun newCount(): Int

    @Query("SELECT COUNT(*) FROM srs_cards")
    fun observeTotal(): Flow<Int>

    @Query("DELETE FROM srs_cards")
    suspend fun clear()
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY hour, minute")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders")
    suspend fun all(): List<ReminderEntity>

    @Insert
    suspend fun insert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Delete
    suspend fun delete(reminder: ReminderEntity)
}
