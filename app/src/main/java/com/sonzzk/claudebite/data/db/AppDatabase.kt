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
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao
    abstract fun waterDao(): WaterDao
    abstract fun weightDao(): WeightDao
    abstract fun chatDao(): ChatDao
    abstract fun usageDao(): UsageDao
    abstract fun achievementDao(): AchievementDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "claudebite.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /** v2: multi-ingredient meals are grouped. Old entries stay stand-alone (null group). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE food_entries ADD COLUMN mealGroupId INTEGER")
                db.execSQL("ALTER TABLE food_entries ADD COLUMN mealName TEXT")
            }
        }
    }
}
