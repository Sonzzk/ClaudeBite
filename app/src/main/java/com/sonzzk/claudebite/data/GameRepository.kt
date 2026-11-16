package com.sonzzk.claudebite.data

import com.sonzzk.claudebite.data.db.AppDatabase
import com.sonzzk.claudebite.data.db.DexEntry
import com.sonzzk.claudebite.data.db.GameState
import com.sonzzk.claudebite.data.db.InventoryItem
import com.sonzzk.claudebite.data.db.QuestProgress
import com.sonzzk.claudebite.data.prefs.ProfileRepository
import com.sonzzk.claudebite.domain.GameMetric
import com.sonzzk.claudebite.domain.Leveling
import com.sonzzk.claudebite.domain.Letters
import com.sonzzk.claudebite.domain.QuestDef
import com.sonzzk.claudebite.domain.Quests
import com.sonzzk.claudebite.domain.Shop
import com.sonzzk.claudebite.domain.ShopItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import kotlin.random.Random

/** A quest with its progress for the current day/week. */
data class QuestStatus(val def: QuestDef, val progress: Int, val claimed: Boolean) {
    val done: Boolean get() = progress >= def.goal
}

/** Something to celebrate in the game layer (shown as a toast by the Play screen and RewardOverlay). */
sealed interface GameEvent {
    data class QuestComplete(val quest: QuestDef) : GameEvent
    data class Coins(val amount: Int) : GameEvent
    data class LetterUnlocked(val title: String) : GameEvent
    data class NewFood(val name: String, val total: Int) : GameEvent
}

/**
 * Quests, boss challenges, coins, shop, room, Food Dex, sticker book, letters and the mini-game.
 * Everything here is local and costs zero tokens.
 */
class GameRepository(db: AppDatabase, private val profiles: ProfileRepository) {
    private val dao = db.gameDao()
    private val toolsDao = db.healthToolsDao()
    private val lock = Mutex()

    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<GameEvent> = _events

    val state: Flow<GameState> = dao.state().map { it ?: GameState() }
    val inventory: Flow<Set<String>> = dao.inventory().map { list -> list.map { it.itemId }.toSet() }
    val dex: Flow<List<DexEntry>> = dao.dex()

    private suspend fun current(): GameState = dao.stateNow() ?: GameState()

    // ---- Quests ----
    fun quests(date: LocalDate = LocalDate.now()): Flow<List<QuestStatus>> {
        val day = Quests.dayPeriod(date)
        val week = Quests.weekPeriod(date)
        val defs = Quests.dailiesFor(date) + Quests.bossFor(date)
        return dao.questsFor(listOf(day, week)).map { rows ->
            defs.map { d ->
                val row = rows.firstOrNull { it.questId == d.id && it.period == (if (d.weekly) week else day) }
                QuestStatus(d, row?.progress ?: 0, row?.claimed ?: false)
            }
        }
    }

    /** Moves every active quest using [metric] forward. "Per day" metrics count once per day. */
    suspend fun report(metric: GameMetric, amount: Int = 1) = lock.withLock {
        val today = LocalDate.now()
        val day = Quests.dayPeriod(today)
        val week = Quests.weekPeriod(today)
        val perDay = metric in setOf(GameMetric.WATER_GOAL_DAY, GameMetric.PROTEIN_GOAL_DAY, GameMetric.CHECKIN_DAY, GameMetric.ALL_MEALS_DAY)
        if (perDay) {
            val marker = dao.quest("mark_${metric.name}", day)
            if (marker != null) return@withLock
            dao.putQuest(QuestProgress("mark_${metric.name}", day, 1, true))
        }
        val active = Quests.dailiesFor(today) + Quests.bossFor(today)
        for (q in active.filter { it.metric == metric }) {
            val period = if (q.weekly) week else day
            val row = dao.quest(q.id, period) ?: QuestProgress(q.id, period)
            if (row.progress >= q.goal) continue
            val updated = row.copy(progress = (row.progress + amount).coerceAtMost(q.goal))
            dao.putQuest(updated)
            if (updated.progress >= q.goal) _events.tryEmit(GameEvent.QuestComplete(q))
        }
    }

    /** Collects a finished quest's coins and XP. */
    suspend fun claim(status: QuestStatus): Boolean = lock.withLock {
        val today = LocalDate.now()
        val period = if (status.def.weekly) Quests.weekPeriod(today) else Quests.dayPeriod(today)
        val row = dao.quest(status.def.id, period) ?: return@withLock false
        if (row.claimed || row.progress < status.def.goal) return@withLock false
        dao.putQuest(row.copy(claimed = true))
        addCoinsLocked(status.def.coins)
        profiles.addXp(status.def.xp)
        true
    }

    suspend fun questsClaimed(): Int = dao.questsClaimed()

    // ---- Coins, chest, shop ----
    private suspend fun addCoinsLocked(amount: Int) {
        val s = current()
        dao.putState(s.copy(coins = s.coins + amount))
        _events.tryEmit(GameEvent.Coins(amount))
    }

    suspend fun addCoins(amount: Int) = lock.withLock { addCoinsLocked(amount) }

    /** Daily chest: 10-40 coins, sometimes a streak freeze. Returns null if already opened today. */
    suspend fun openChest(): Pair<Int, Boolean>? = lock.withLock {
        val s = current()
        val today = LocalDate.now().toString()
        if (s.lastChestDay == today) return@withLock null
        val coins = Random.nextInt(10, 41)
        val freeze = Random.nextInt(100) < 8 && s.streakFreezes < 2
        dao.putState(s.copy(coins = s.coins + coins, lastChestDay = today, streakFreezes = s.streakFreezes + if (freeze) 1 else 0))
        _events.tryEmit(GameEvent.Coins(coins))
        coins to freeze
    }

    suspend fun buy(item: ShopItem): Boolean = lock.withLock {
        val s = current()
        if (s.coins < item.price) return@withLock false
        if (item.kind == ShopItem.Kind.FREEZE) {
            if (s.streakFreezes >= 2) return@withLock false
            dao.putState(s.copy(coins = s.coins - item.price, streakFreezes = s.streakFreezes + 1))
            return@withLock true
        }
        val inserted = dao.addItem(InventoryItem(item.id, System.currentTimeMillis()))
        if (inserted == -1L) return@withLock false
        dao.putState(s.copy(coins = s.coins - item.price))
        true
    }

    /** Free unlock (milestones give stickers). */
    suspend fun grant(itemId: String) {
        dao.addItem(InventoryItem(itemId, System.currentTimeMillis()))
    }

    // ---- Room ----
    /** slot index -> item id */
    fun layout(s: GameState): Map<Int, String> = s.roomLayout.split(';').mapNotNull { part ->
        val (id, slot) = part.split('@').takeIf { it.size == 2 } ?: return@mapNotNull null
        slot.toIntOrNull()?.let { it to id }
    }.toMap()

    suspend fun place(slot: Int, itemId: String?) = lock.withLock {
        val s = current()
        val map = layout(s).toMutableMap()
        map.entries.removeAll { it.value == itemId }
        if (itemId == null) map.remove(slot) else map[slot] = itemId
        dao.putState(s.copy(roomLayout = map.entries.joinToString(";") { "${it.value}@${it.key}" }))
    }

    // ---- Mini-game ----
    /** Coins for a mini-game round: score/5, max 20, and only the first 3 rounds a day pay out. */
    suspend fun finishMiniGame(score: Int): Int = lock.withLock {
        val day = Quests.dayPeriod(LocalDate.now())
        val row = dao.quest("minigame", day) ?: QuestProgress("minigame", day)
        val s = current()
        val best = maxOf(s.bestMiniGame, score)
        val coins = if (row.progress < 3) (score / 5).coerceIn(0, 20) else 0
        dao.putQuest(row.copy(progress = row.progress + 1))
        dao.putState(s.copy(bestMiniGame = best, coins = s.coins + coins))
        if (coins > 0) _events.tryEmit(GameEvent.Coins(coins))
        coins
    }

    // ---- Food Dex ----
    /** Records foods in the Dex. Returns how many were new. */
    suspend fun discover(names: List<String>, healthScore: Int?): Int = lock.withLock {
        var fresh = 0
        val today = LocalDate.now().toString()
        for (raw in names) {
            val key = dexKey(raw)
            if (key.isEmpty()) continue
            val old = dao.dexEntry(key)
            if (old == null) {
                fresh++
                dao.putDex(DexEntry(key, raw.substringBefore(" · ").take(40), today, 1, healthScore ?: 0))
            } else {
                dao.putDex(old.copy(timesLogged = old.timesLogged + 1, bestScore = maxOf(old.bestScore, healthScore ?: 0)))
            }
        }
        if (fresh > 0) _events.tryEmit(GameEvent.NewFood(names.first(), dao.dexCount()))
        fresh
    }

    private fun dexKey(name: String): String =
        name.lowercase().substringBefore(" · ").substringBefore(",").replace(Regex("[^a-z0-9 ]"), "").trim()
            .split(' ').filter { it.isNotBlank() && it !in setOf("a", "the", "with", "and", "of") }.sorted().joinToString(" ")

    suspend fun dexCount() = dao.dexCount()

    // ---- Letters & stickers from milestones ----
    /** Checks milestones and unlocks letters (and a free sticker with each). */
    suspend fun checkMilestones(streak: Int, foodsLogged: Int, workouts: Int) {
        val level = Leveling.info(profiles.progression.first().xp).level
        val dexN = dao.dexCount()
        val quests = dao.questsClaimed()
        val unlocked = buildList {
            if (foodsLogged >= 1) add("first")
            if (streak >= 7) add("streak7")
            if (level >= 5) add("level5")
            if (dexN >= 25) add("dex25")
            if (workouts >= 5) add("workout5")
            if (streak >= 30) add("streak30")
            if (level >= 15) add("level15")
            if (quests >= 20) add("quests20")
        }
        lock.withLock {
            val s = current()
            val have = s.lettersRead.split(',').filter { it.isNotBlank() }.toSet()
            val new = unlocked.filter { it !in have }
            if (new.isEmpty()) return@withLock
            dao.putState(s.copy(lettersRead = (have + new).joinToString(",")))
            new.forEachIndexed { i, id ->
                Letters.all.firstOrNull { it.id == id }?.let { _events.tryEmit(GameEvent.LetterUnlocked(it.title)) }
                dao.addItem(InventoryItem(Shop.stickers[(have.size + i) % Shop.stickers.size].id, System.currentTimeMillis()))
            }
        }
    }

    fun unlockedLetters(s: GameState): Set<String> = s.lettersRead.split(',').filter { it.isNotBlank() }.toSet()

    // ---- Hooks from the rest of the app ----
    suspend fun onWorkoutLogged() {
        report(GameMetric.WORKOUT)
        checkMilestones(streak = 0, foodsLogged = 0, workouts = toolsDao.workoutCount())
    }

    /** Called by HealthRepository after any food log. */
    suspend fun onFoodLogged(names: List<String>, healthScore: Int?, photo: Boolean, streak: Int, foodCount: Int) {
        report(GameMetric.MEAL_LOGGED)
        if (photo) report(GameMetric.PHOTO_LOGGED)
        if ((healthScore ?: 0) >= 8) report(GameMetric.HEALTHY_MEAL)
        val fresh = discover(names, healthScore)
        if (fresh > 0) report(GameMetric.NEW_FOOD, fresh)
        checkMilestones(streak, foodCount, toolsDao.workoutCount())
    }

    suspend fun onCheckIn() = report(GameMetric.CHECKIN_DAY)
    suspend fun onChat() = report(GameMetric.CHAT)
}
