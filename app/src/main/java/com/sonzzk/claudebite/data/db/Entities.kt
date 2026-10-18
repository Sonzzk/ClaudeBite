package com.sonzzk.claudebite.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MealType { BREAKFAST, LUNCH, DINNER, SNACK }

enum class EntrySource { PHOTO, TEXT, MANUAL, RELOG }

/** One logged food item. Dates are stored as ISO strings (yyyy-MM-dd) so queries stay simple. */
@Entity(tableName = "food_entries", indices = [Index("date")])
data class FoodEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val timestamp: Long,
    val mealType: MealType,
    val name: String,
    val portion: String,
    val calories: Int,
    val proteinG: Float,
    val carbsG: Float,
    val fatG: Float,
    val fiberG: Float = 0f,
    val sugarG: Float = 0f,
    /** Claude-chan's health score for this food, 1-10. Null for manual entries. */
    val healthScore: Int? = null,
    /** Short comment from Claude-chan, shown on the entry card. */
    val mascotComment: String? = null,
    val photoPath: String? = null,
    val source: EntrySource = EntrySource.MANUAL,
    val isFavorite: Boolean = false,
)

@Entity(tableName = "water_logs", indices = [Index("date")])
data class WaterLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val timestamp: Long,
    val amountMl: Int,
)

@Entity(tableName = "weight_logs", indices = [Index("date", unique = true)])
data class WeightLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val timestamp: Long,
    val weightKg: Float,
)

enum class ChatRole { USER, ASSISTANT }

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val role: ChatRole,
    val text: String,
    /** Which model answered (for the token/cost panel). Null for user messages. */
    val model: String? = null,
)

/** Running token counter per model, so Settings can show usage and estimated cost. */
@Entity(tableName = "token_usage")
data class TokenUsage(
    @PrimaryKey val model: String,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val requests: Long = 0,
)

/** Badges unlocked by the gamification system. */
@Entity(tableName = "achievements")
data class Achievement(
    @PrimaryKey val id: String,
    val unlockedAt: Long,
)
