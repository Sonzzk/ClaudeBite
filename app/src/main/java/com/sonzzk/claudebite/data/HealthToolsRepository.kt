package com.sonzzk.claudebite.data

import com.sonzzk.claudebite.data.db.AppDatabase
import com.sonzzk.claudebite.data.db.DayBurn
import com.sonzzk.claudebite.data.db.DayNote
import com.sonzzk.claudebite.data.db.GroceryItem
import com.sonzzk.claudebite.data.db.ProgressPhoto
import com.sonzzk.claudebite.data.db.Workout
import com.sonzzk.claudebite.data.db.WorkoutSet
import com.sonzzk.claudebite.data.db.WorkoutWithSets
import com.sonzzk.claudebite.domain.GroupedMeal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.io.File
import java.time.LocalDate

/** Workouts, sleep/mood notes, progress photos and the grocery list. All local, zero tokens. */
class HealthToolsRepository(private val db: AppDatabase) {
    private val dao = db.healthToolsDao()

    fun today(): String = LocalDate.now().toString()

    // ---- Workouts ----
    fun workoutsWithSets(): Flow<List<WorkoutWithSets>> = combine(dao.workouts(), dao.allSets()) { ws, sets ->
        val byWorkout = sets.groupBy { it.workoutId }
        ws.map { WorkoutWithSets(it, byWorkout[it.id].orEmpty()) }
    }

    fun burnedOn(date: String): Flow<Int> = dao.workoutsOn(date).map { list -> list.sumOf { it.caloriesBurned } }
    fun burnSince(date: String): Flow<List<DayBurn>> = dao.burnSince(date)

    suspend fun saveWorkout(workout: Workout, sets: List<WorkoutSet>): Long {
        val id = dao.insertWorkout(workout)
        if (sets.isNotEmpty()) dao.insertSets(sets.mapIndexed { i, s -> s.copy(id = 0, workoutId = id, position = i) })
        return id
    }

    suspend fun deleteWorkout(w: Workout) {
        dao.deleteSets(w.id)
        dao.deleteWorkout(w)
    }

    suspend fun recentExercises(): List<String> = dao.recentExercises()
    suspend fun workoutCount(): Int = dao.workoutCount()

    // ---- Sleep & mood ----
    fun note(date: String): Flow<DayNote?> = dao.note(date)
    fun notes(): Flow<List<DayNote>> = dao.notes()
    suspend fun putNote(n: DayNote) = dao.putNote(n)

    // ---- Progress photos ----
    fun photos(): Flow<List<ProgressPhoto>> = dao.photos()
    suspend fun addPhoto(path: String, weightKg: Float?) = dao.insertPhoto(ProgressPhoto(date = today(), path = path, weightKg = weightKg))
    suspend fun deletePhoto(p: ProgressPhoto) {
        runCatching { File(p.path).delete() }
        dao.deletePhoto(p)
    }

    // ---- Groceries ----
    fun groceries(): Flow<List<GroceryItem>> = dao.groceries()
    suspend fun addGrocery(name: String) {
        if (name.isBlank()) return
        dao.insertGroceries(listOf(GroceryItem(name = name.trim().take(60))))
    }
    suspend fun toggleGrocery(item: GroceryItem) = dao.updateGrocery(item.copy(checked = !item.checked))
    suspend fun clearChecked() = dao.clearCheckedGroceries()

    /** Adds every ingredient of the given meals that isn't already on the list. Returns how many were added. */
    suspend fun addMealsToGroceries(meals: List<GroupedMeal>): Int {
        val open = dao.openGroceryNames().toHashSet()
        val items = meals.flatMap { m -> m.entries.map { e -> GroceryItem(name = groceryName(e.name), fromMeal = m.name) } }
            .distinctBy { it.name.lowercase() }
            .filter { it.name.lowercase() !in open }
        if (items.isNotEmpty()) dao.insertGroceries(items)
        return items.size
    }

    /** "Chicken breast, cooked" -> "Chicken breast" for a shopping list. */
    private fun groceryName(food: String): String =
        food.substringBefore(',').substringBefore(" · ").replace(Regex("\\s*\\(.*?\\)"), "").trim().ifBlank { food }
}
