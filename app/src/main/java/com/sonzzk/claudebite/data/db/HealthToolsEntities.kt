package com.sonzzk.claudebite.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

enum class WorkoutType { STRENGTH, CARDIO, SPORT, OTHER }

/** One training session. Burned calories are added to that day's food budget. */
@Entity(tableName = "workouts", indices = [Index("date")])
data class Workout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val timestamp: Long,
    val name: String,
    val type: WorkoutType,
    val durationMin: Int,
    val caloriesBurned: Int,
    val notes: String = "",
)

/** One set inside a workout ("Bench press, 8 reps, 60 kg"). Cardio sessions usually have none. */
@Entity(tableName = "workout_sets", indices = [Index("workoutId")])
data class WorkoutSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exercise: String,
    val reps: Int,
    val weightKg: Float,
    val position: Int,
)

/** Daily check-in: sleep and mood, so Claude-chan can spot patterns (e.g. more snacking after bad sleep). */
@Entity(tableName = "day_notes")
data class DayNote(
    @PrimaryKey val date: String,
    val sleepHours: Float? = null,
    /** 1 (awful) .. 5 (great) */
    val sleepQuality: Int? = null,
    /** 1 .. 5 */
    val mood: Int? = null,
    val energy: Int? = null,
    val note: String = "",
)

@Entity(tableName = "progress_photos", indices = [Index("date")])
data class ProgressPhoto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val path: String,
    val weightKg: Float? = null,
)

@Entity(tableName = "grocery_items")
data class GroceryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val checked: Boolean = false,
    /** Which saved meal it came from, if any. */
    val fromMeal: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

data class WorkoutWithSets(val workout: Workout, val sets: List<WorkoutSet>)

data class DayBurn(val date: String, val calories: Int)

@Dao
interface HealthToolsDao {
    @Query("SELECT * FROM workouts ORDER BY timestamp DESC")
    fun workouts(): Flow<List<Workout>>

    @Query("SELECT * FROM workouts WHERE date = :date ORDER BY timestamp")
    fun workoutsOn(date: String): Flow<List<Workout>>

    @Query("SELECT date, SUM(caloriesBurned) AS calories FROM workouts WHERE date >= :fromDate GROUP BY date")
    fun burnSince(fromDate: String): Flow<List<DayBurn>>

    @Query("SELECT * FROM workout_sets ORDER BY workoutId, position")
    fun allSets(): Flow<List<WorkoutSet>>

    @Query("SELECT * FROM workout_sets WHERE workoutId = :id ORDER BY position")
    suspend fun setsFor(id: Long): List<WorkoutSet>

    @Query("SELECT DISTINCT exercise FROM workout_sets ORDER BY id DESC LIMIT 40")
    suspend fun recentExercises(): List<String>

    @Query("SELECT COUNT(*) FROM workouts")
    suspend fun workoutCount(): Int

    @Insert suspend fun insertWorkout(w: Workout): Long
    @Insert suspend fun insertSets(sets: List<WorkoutSet>)
    @Delete suspend fun deleteWorkout(w: Workout)

    @Query("DELETE FROM workout_sets WHERE workoutId = :id")
    suspend fun deleteSets(id: Long)

    @Query("SELECT * FROM day_notes WHERE date = :date")
    fun note(date: String): Flow<DayNote?>

    @Query("SELECT * FROM day_notes ORDER BY date")
    fun notes(): Flow<List<DayNote>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putNote(n: DayNote)

    @Query("SELECT * FROM progress_photos ORDER BY date")
    fun photos(): Flow<List<ProgressPhoto>>

    @Insert suspend fun insertPhoto(p: ProgressPhoto): Long
    @Delete suspend fun deletePhoto(p: ProgressPhoto)

    @Query("SELECT * FROM grocery_items ORDER BY checked, createdAt DESC")
    fun groceries(): Flow<List<GroceryItem>>

    @Insert suspend fun insertGroceries(items: List<GroceryItem>)
    @Update suspend fun updateGrocery(item: GroceryItem)

    @Query("DELETE FROM grocery_items WHERE checked = 1")
    suspend fun clearCheckedGroceries()

    @Query("SELECT LOWER(name) FROM grocery_items WHERE checked = 0")
    suspend fun openGroceryNames(): List<String>
}
