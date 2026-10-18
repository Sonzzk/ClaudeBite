package com.sonzzk.claudebite.data

import com.sonzzk.claudebite.ai.AnalyzedItem
import com.sonzzk.claudebite.ai.CoachContext
import com.sonzzk.claudebite.ai.FoodAnalysis
import com.sonzzk.claudebite.data.db.Achievement
import com.sonzzk.claudebite.data.db.AppDatabase
import com.sonzzk.claudebite.data.db.ChatMessage
import com.sonzzk.claudebite.data.db.DayTotals
import com.sonzzk.claudebite.data.db.EntrySource
import com.sonzzk.claudebite.data.db.FoodEntry
import com.sonzzk.claudebite.data.db.MealType
import com.sonzzk.claudebite.data.db.TokenUsage
import com.sonzzk.claudebite.data.db.WaterLog
import com.sonzzk.claudebite.data.db.WeightLog
import com.sonzzk.claudebite.data.prefs.ProfileRepository
import com.sonzzk.claudebite.domain.Badge
import com.sonzzk.claudebite.domain.Leveling
import com.sonzzk.claudebite.domain.NutritionCalculator
import com.sonzzk.claudebite.domain.Streaks
import com.sonzzk.claudebite.domain.Xp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime

/** Something fun to show the user after an action: XP popup, level up, new badge. */
sealed interface Reward {
    data class XpGained(val amount: Int) : Reward
    data class LevelUp(val newLevel: Int) : Reward
    data class BadgeUnlocked(val badge: Badge) : Reward
}

/** Single entry point for app data. ViewModels talk to this, not to DAOs directly. */
class HealthRepository(
    private val db: AppDatabase,
    val profiles: ProfileRepository,
) {
    private val food = db.foodDao()
    private val water = db.waterDao()
    private val weight = db.weightDao()
    private val chat = db.chatDao()
    private val usage = db.usageDao()
    private val achievements = db.achievementDao()

    private val _rewards = MutableSharedFlow<Reward>(extraBufferCapacity = 8)
    /** UI collects this to show XP toasts, level-up and badge celebrations. */
    val rewards: SharedFlow<Reward> = _rewards

    fun today(): String = LocalDate.now().toString()

    // ---- Food ----
    fun entriesFor(date: String): Flow<List<FoodEntry>> = food.entriesFor(date)
    fun dailyTotalsSince(date: String): Flow<List<DayTotals>> = food.dailyTotalsSince(date)
    fun recentFoods(): Flow<List<FoodEntry>> = food.recentDistinct()

    /** Saves every item of an analysis as separate entries (so the user can delete one item later). */
    suspend fun logAnalysis(
        analysis: FoodAnalysis,
        items: List<AnalyzedItem>,
        mealType: MealType,
        source: EntrySource,
        photoPath: String?,
    ) {
        val now = System.currentTimeMillis()
        val date = today()
        food.insertAll(items.map {
            FoodEntry(
                date = date, timestamp = now, mealType = mealType,
                name = it.name, portion = it.portion, calories = it.calories,
                proteinG = it.proteinG, carbsG = it.carbsG, fatG = it.fatG,
                fiberG = it.fiberG, sugarG = it.sugarG,
                healthScore = analysis.healthScore, mascotComment = analysis.comment,
                photoPath = photoPath, source = source,
            )
        })
        var xp = Xp.LOG_FOOD
        if (source == EntrySource.PHOTO) xp += Xp.LOG_PHOTO_BONUS
        if (analysis.healthScore >= 8) xp += Xp.HEALTHY_FOOD_BONUS
        if (analysis.healthScore >= 10) unlock(Badge.GREEN_PLATE)
        afterFoodLogged(xp)
    }

    suspend fun logManual(entry: FoodEntry) {
        food.insert(entry.copy(date = today(), timestamp = System.currentTimeMillis()))
        afterFoodLogged(Xp.LOG_FOOD)
    }

    /** One-tap re-log of a previous food (zero tokens). */
    suspend fun relog(entry: FoodEntry, mealType: MealType) {
        food.insert(entry.copy(id = 0, date = today(), timestamp = System.currentTimeMillis(), mealType = mealType, source = EntrySource.RELOG))
        afterFoodLogged(Xp.LOG_FOOD)
    }

    suspend fun updateEntry(entry: FoodEntry) = food.update(entry)
    suspend fun deleteEntry(entry: FoodEntry) = food.delete(entry)
    suspend fun toggleFavorite(entry: FoodEntry) = food.update(entry.copy(isFavorite = !entry.isFavorite))

    private suspend fun afterFoodLogged(xp: Int) {
        if (food.count() == 1) unlock(Badge.FIRST_BITE)
        val streak = currentStreak()
        if (streak >= 3) unlock(Badge.STREAK_3)
        if (streak >= 7) unlock(Badge.STREAK_7)
        if (streak >= 30) unlock(Badge.STREAK_30)
        checkDailyGoals()
        profiles.changeAffection(+2)
        grantXp(xp)
    }

    private suspend fun checkDailyGoals() {
        val profile = profiles.profile.first()
        val targets = NutritionCalculator.targets(profile)
        val todayTotals = food.dailyTotalsSince(today()).first().firstOrNull() ?: return
        if (todayTotals.proteinG >= targets.proteinG) unlock(Badge.PROTEIN_PRO)
        // "Bullseye" only counts in the evening, when the day is mostly done.
        if (LocalTime.now().hour >= 19 && kotlin.math.abs(todayTotals.calories - targets.calories) <= 100) {
            unlock(Badge.ON_TARGET)
        }
    }

    suspend fun currentStreak(): Int = Streaks.current(food.loggedDatesDesc(), LocalDate.now())

    // ---- Water ----
    fun waterToday(): Flow<Int> = water.totalFor(today())

    suspend fun addWater(ml: Int) {
        water.insert(WaterLog(date = today(), timestamp = System.currentTimeMillis(), amountMl = ml))
        val goal = profiles.profile.first().waterGoalMl
        if (water.totalFor(today()).first() >= goal) unlock(Badge.HYDRATED)
        grantXp(Xp.LOG_WATER)
    }

    suspend fun undoWater() = water.undoLast(today())

    // ---- Weight ----
    fun weights(): Flow<List<WeightLog>> = weight.all()

    suspend fun logWeight(kg: Float) {
        weight.upsert(WeightLog(date = today(), timestamp = System.currentTimeMillis(), weightKg = kg))
        profiles.updateWeight(kg)
        unlock(Badge.WEIGH_IN)
        grantXp(Xp.LOG_WEIGHT)
    }

    suspend fun deleteWeight(log: WeightLog) = weight.delete(log)

    // ---- Chat ----
    fun chatMessages(): Flow<List<ChatMessage>> = chat.all()
    suspend fun recentChat(n: Int): List<ChatMessage> = chat.lastN(n)
    suspend fun addChat(message: ChatMessage) {
        chat.insert(message)
        if (message.role == com.sonzzk.claudebite.data.db.ChatRole.USER) {
            grantXp(Xp.CHAT)
            if (chat.lastN(1000).count { it.role == com.sonzzk.claudebite.data.db.ChatRole.USER } >= 20) unlock(Badge.CHATTY)
        }
    }
    suspend fun clearChat() = chat.clear()

    // ---- Token usage ----
    fun tokenUsage(): Flow<List<TokenUsage>> = usage.all()

    suspend fun recordUsage(model: String, input: Long, output: Long) {
        val old = usage.get(model) ?: TokenUsage(model)
        usage.put(old.copy(inputTokens = old.inputTokens + input, outputTokens = old.outputTokens + output, requests = old.requests + 1))
    }

    suspend fun resetUsage() = usage.clear()

    // ---- Achievements & XP ----
    fun achievements(): Flow<List<Achievement>> = achievements.all()

    private suspend fun unlock(badge: Badge) {
        val inserted = achievements.unlock(Achievement(badge.name, System.currentTimeMillis()))
        if (inserted != -1L) _rewards.tryEmit(Reward.BadgeUnlocked(badge))
    }

    private suspend fun grantXp(amount: Int) {
        val before = Leveling.info(profiles.progression.first().xp).level
        profiles.addXp(amount)
        val after = Leveling.info(profiles.progression.first().xp).level
        _rewards.tryEmit(Reward.XpGained(amount))
        if (after > before) _rewards.tryEmit(Reward.LevelUp(after))
    }

    // ---- Context for Claude ----
    suspend fun coachContext(): CoachContext {
        val profile = profiles.profile.first()
        val targets = NutritionCalculator.targets(profile)
        val entries = food.entriesFor(today()).first()
        return CoachContext(
            profile = profile,
            targets = targets,
            eatenToday = entries.sumOf { it.calories },
            proteinToday = entries.sumOf { it.proteinG.toDouble() }.toFloat(),
            carbsToday = entries.sumOf { it.carbsG.toDouble() }.toFloat(),
            fatToday = entries.sumOf { it.fatG.toDouble() }.toFloat(),
            waterTodayMl = water.totalFor(today()).first(),
            streak = currentStreak(),
            foodsToday = entries.map { it.name },
        )
    }

    suspend fun wipeEverything() {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { db.clearAllTables() }
        profiles.clearAll()
    }
}
