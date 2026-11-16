package com.sonzzk.claudebite.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Single row (id 0) with the game economy: coins ("Bites"), streak freezes, room layout, letters... */
@Entity(tableName = "game_state")
data class GameState(
    @PrimaryKey val id: Int = 0,
    val coins: Int = 0,
    val streakFreezes: Int = 0,
    /** Last day (yyyy-MM-dd) the daily chest was opened. */
    val lastChestDay: String? = null,
    val bestMiniGame: Int = 0,
    /** Room decorations: "itemId@slot;itemId@slot". */
    val roomLayout: String = "",
    /** Ids of milestone letters already opened, comma separated. */
    val lettersRead: String = "",
    val bondPoints: Int = 0,
)

/** Progress on one quest for one period ("2026-11-16" for dailies, "2026-W47" for weeklies/bosses). */
@Entity(tableName = "quest_progress", primaryKeys = ["questId", "period"])
data class QuestProgress(
    val questId: String,
    val period: String,
    val progress: Int = 0,
    val claimed: Boolean = false,
)

/** Owned shop items: room decorations, stickers, outfits bought with coins. */
@Entity(tableName = "inventory")
data class InventoryItem(@PrimaryKey val itemId: String, val acquiredAt: Long)

/** Food Dex: every distinct food ever logged. */
@Entity(tableName = "food_dex")
data class DexEntry(
    @PrimaryKey val key: String,
    val name: String,
    val firstDate: String,
    val timesLogged: Int,
    val bestScore: Int,
)

@Dao
interface GameDao {
    @Query("SELECT * FROM game_state WHERE id = 0")
    fun state(): Flow<GameState?>

    @Query("SELECT * FROM game_state WHERE id = 0")
    suspend fun stateNow(): GameState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putState(s: GameState)

    @Query("SELECT * FROM quest_progress WHERE period IN (:periods)")
    fun questsFor(periods: List<String>): Flow<List<QuestProgress>>

    @Query("SELECT * FROM quest_progress WHERE questId = :id AND period = :period")
    suspend fun quest(id: String, period: String): QuestProgress?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putQuest(q: QuestProgress)

    @Query("SELECT COUNT(*) FROM quest_progress WHERE claimed = 1")
    suspend fun questsClaimed(): Int

    @Query("SELECT * FROM inventory")
    fun inventory(): Flow<List<InventoryItem>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addItem(i: InventoryItem): Long

    @Query("SELECT * FROM food_dex ORDER BY firstDate DESC")
    fun dex(): Flow<List<DexEntry>>

    @Query("SELECT * FROM food_dex WHERE `key` = :key")
    suspend fun dexEntry(key: String): DexEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putDex(e: DexEntry)

    @Query("SELECT COUNT(*) FROM food_dex")
    suspend fun dexCount(): Int
}
