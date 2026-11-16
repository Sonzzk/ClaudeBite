package com.sonzzk.claudebite

import android.content.Context
import com.sonzzk.claudebite.ai.AiCoach
import com.sonzzk.claudebite.ai.ClaudeCoach
import com.sonzzk.claudebite.data.HealthRepository
import com.sonzzk.claudebite.data.db.AppDatabase
import com.sonzzk.claudebite.data.prefs.ProfileRepository
import com.sonzzk.claudebite.data.prefs.SecureSettings

/**
 * Simple manual dependency injection: one place that creates the app's long-lived objects.
 * ViewModels get what they need from here (see ui/AppViewModelFactory.kt).
 */
class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.create(context)
    val profiles = ProfileRepository(context)
    val settings = SecureSettings(context)
    val game = com.sonzzk.claudebite.data.GameRepository(database, profiles)
    val repository = HealthRepository(database, profiles, game)
    val coach: AiCoach = ClaudeCoach(settings, repository)
    val tools = com.sonzzk.claudebite.data.HealthToolsRepository(database)
}
