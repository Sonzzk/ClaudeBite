package com.sonzzk.claudebite.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FoodEntry::class, WaterLog::class, WeightLog::class,
        ChatMessage::class, TokenUsage::class, Achievement::class,
        Workout::class, WorkoutSet::class, DayNote::class, ProgressPhoto::class, GroceryItem::class,
        GameState::class, QuestProgress::class, InventoryItem::class, DexEntry::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao
    abstract fun waterDao(): WaterDao
    abstract fun weightDao(): WeightDao
    abstract fun chatDao(): ChatDao
    abstract fun usageDao(): UsageDao
    abstract fun achievementDao(): AchievementDao
    abstract fun healthToolsDao(): HealthToolsDao
    abstract fun gameDao(): GameDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "claudebite.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()

        /** v2: multi-ingredient meals are grouped. Old entries stay stand-alone (null group). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE food_entries ADD COLUMN mealGroupId INTEGER")
                db.execSQL("ALTER TABLE food_entries ADD COLUMN mealName TEXT")
            }
        }

        /** v3: health tools (workouts, sleep/mood notes, progress photos, groceries) and the game layer. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `workouts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `durationMin` INTEGER NOT NULL, `caloriesBurned` INTEGER NOT NULL, `notes` TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workouts_date` ON `workouts` (`date`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `workout_sets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `workoutId` INTEGER NOT NULL, `exercise` TEXT NOT NULL, `reps` INTEGER NOT NULL, `weightKg` REAL NOT NULL, `position` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_sets_workoutId` ON `workout_sets` (`workoutId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `day_notes` (`date` TEXT NOT NULL, `sleepHours` REAL, `sleepQuality` INTEGER, `mood` INTEGER, `energy` INTEGER, `note` TEXT NOT NULL, PRIMARY KEY(`date`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `progress_photos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, `path` TEXT NOT NULL, `weightKg` REAL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_progress_photos_date` ON `progress_photos` (`date`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `grocery_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `checked` INTEGER NOT NULL, `fromMeal` TEXT, `createdAt` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `game_state` (`id` INTEGER NOT NULL, `coins` INTEGER NOT NULL, `streakFreezes` INTEGER NOT NULL, `lastChestDay` TEXT, `bestMiniGame` INTEGER NOT NULL, `roomLayout` TEXT NOT NULL, `lettersRead` TEXT NOT NULL, `bondPoints` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `quest_progress` (`questId` TEXT NOT NULL, `period` TEXT NOT NULL, `progress` INTEGER NOT NULL, `claimed` INTEGER NOT NULL, PRIMARY KEY(`questId`, `period`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `inventory` (`itemId` TEXT NOT NULL, `acquiredAt` INTEGER NOT NULL, PRIMARY KEY(`itemId`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `food_dex` (`key` TEXT NOT NULL, `name` TEXT NOT NULL, `firstDate` TEXT NOT NULL, `timesLogged` INTEGER NOT NULL, `bestScore` INTEGER NOT NULL, PRIMARY KEY(`key`))")
            }
        }
    }
}
