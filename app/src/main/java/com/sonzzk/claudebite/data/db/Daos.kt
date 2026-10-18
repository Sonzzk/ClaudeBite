package com.sonzzk.claudebite.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Calorie + macro sums for one day, used by charts and streaks. */
data class DayTotals(
    val date: String,
    val calories: Int,
    val proteinG: Float,
    val carbsG: Float,
    val fatG: Float,
    val entries: Int,
)

@Dao
interface FoodDao {
    @Query("SELECT * FROM food_entries WHERE date = :date ORDER BY timestamp")
    fun entriesFor(date: String): Flow<List<FoodEntry>>

    @Query("SELECT * FROM food_entries WHERE id = :id")
    suspend fun byId(id: Long): FoodEntry?

    @Query(
        """SELECT date, SUM(calories) AS calories, SUM(proteinG) AS proteinG, SUM(carbsG) AS carbsG,
           SUM(fatG) AS fatG, COUNT(*) AS entries
           FROM food_entries WHERE date >= :fromDate GROUP BY date ORDER BY date"""
    )
    fun dailyTotalsSince(fromDate: String): Flow<List<DayTotals>>

    @Query("SELECT DISTINCT date FROM food_entries ORDER BY date DESC")
    suspend fun loggedDatesDesc(): List<String>

    /** Most recent distinct foods, for one-tap re-logging (costs no tokens). */
    @Query(
        """SELECT * FROM food_entries WHERE id IN
           (SELECT MAX(id) FROM food_entries GROUP BY name) ORDER BY isFavorite DESC, timestamp DESC LIMIT :limit"""
    )
    fun recentDistinct(limit: Int = 20): Flow<List<FoodEntry>>

    @Query("SELECT COUNT(*) FROM food_entries")
    suspend fun count(): Int

    @Insert suspend fun insert(entry: FoodEntry): Long
    @Insert suspend fun insertAll(entries: List<FoodEntry>)
    @Update suspend fun update(entry: FoodEntry)
    @Delete suspend fun delete(entry: FoodEntry)
}

@Dao
interface WaterDao {
    @Query("SELECT COALESCE(SUM(amountMl), 0) FROM water_logs WHERE date = :date")
    fun totalFor(date: String): Flow<Int>

    @Insert suspend fun insert(log: WaterLog)

    @Query("DELETE FROM water_logs WHERE id = (SELECT MAX(id) FROM water_logs WHERE date = :date)")
    suspend fun undoLast(date: String)
}

@Dao
interface WeightDao {
    @Query("SELECT * FROM weight_logs ORDER BY date")
    fun all(): Flow<List<WeightLog>>

    @Query("SELECT * FROM weight_logs ORDER BY date DESC LIMIT 1")
    suspend fun latest(): WeightLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: WeightLog)

    @Delete suspend fun delete(log: WeightLog)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages ORDER BY id")
    fun all(): Flow<List<ChatMessage>>

    @Query("SELECT * FROM (SELECT * FROM chat_messages ORDER BY id DESC LIMIT :n) ORDER BY id")
    suspend fun lastN(n: Int): List<ChatMessage>

    @Insert suspend fun insert(message: ChatMessage): Long

    @Query("DELETE FROM chat_messages")
    suspend fun clear()
}

@Dao
interface UsageDao {
    @Query("SELECT * FROM token_usage")
    fun all(): Flow<List<TokenUsage>>

    @Query("SELECT * FROM token_usage WHERE model = :model")
    suspend fun get(model: String): TokenUsage?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(usage: TokenUsage)

    @Query("DELETE FROM token_usage")
    suspend fun clear()
}

@Dao
interface AchievementDao {
    @Query("SELECT * FROM achievements")
    fun all(): Flow<List<Achievement>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun unlock(achievement: Achievement): Long
}
