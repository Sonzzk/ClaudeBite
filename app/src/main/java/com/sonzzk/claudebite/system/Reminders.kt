package com.sonzzk.claudebite.system

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sonzzk.claudebite.ClaudeBiteApp
import com.sonzzk.claudebite.MainActivity
import com.sonzzk.claudebite.R
import com.sonzzk.claudebite.data.db.MealType
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Gentle meal reminders: at most 2 a day (lunch and dinner), only when that meal isn't logged yet,
 * and never when reminders are off in Settings. A worker wakes about once an hour to check.
 */
object Reminders {
    private const val CHANNEL = "meal_reminders"
    private const val WORK = "meal_reminders"
    private const val PREFS = "reminders"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    internal fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Meal reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A friendly nudge from Claude-chan, at most twice a day"
            },
        )
    }

    /** Which reminder (if any) is due now. Pure, so it is unit-tested. */
    fun due(hour: Int, logged: Set<MealType>, sentToday: Set<String>): String? = when {
        hour in 12..14 && MealType.LUNCH !in logged && "lunch" !in sentToday && sentToday.size < 2 -> "lunch"
        hour in 19..20 && MealType.DINNER !in logged && "dinner" !in sentToday && sentToday.size < 2 -> "dinner"
        else -> null
    }

    internal suspend fun check(context: Context) {
        val app = context.applicationContext as? ClaudeBiteApp ?: return
        val c = app.container
        if (!c.profiles.settings.first().reminders) return
        if (!c.profiles.profile.first().onboarded) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        val sent = prefs.getStringSet(today, emptySet()).orEmpty()
        val logged = c.repository.entriesFor(today).first().map { it.mealType }.toSet()
        val which = due(LocalTime.now().hour, logged, sent) ?: return
        if (!notify(context, which)) return
        // Keep only today's record.
        prefs.edit().clear().putStringSet(today, sent + which).apply()
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun notify(context: Context, which: String): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        createChannel(context)
        val (title, text) = when (which) {
            "lunch" -> "Lunch time? 🍙" to "Snap a quick pic and I'll do the math~"
            else -> "How was dinner? 🍲" to "Tell me what you had, it takes 5 seconds!"
        }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_bite)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        return runCatching { NotificationManagerCompat.from(context).notify(which.hashCode(), n) }.isSuccess
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { Reminders.check(applicationContext) }
        runCatching { TodayWidget.refresh(applicationContext) }
        return Result.success()
    }
}
