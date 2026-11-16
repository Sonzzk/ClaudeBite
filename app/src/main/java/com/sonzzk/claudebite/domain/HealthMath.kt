package com.sonzzk.claudebite.domain

import com.sonzzk.claudebite.data.db.WorkoutType
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/** Smoothed weight trend (exponential moving average), so daily water-weight swings don't scare anyone. */
object WeightTrend {
    data class Point(val date: LocalDate, val raw: Float, val trend: Float)

    /** [weighIns] can have gaps; the smoothing factor is applied once per weigh-in. */
    fun smooth(weighIns: List<Pair<LocalDate, Float>>, alpha: Float = 0.25f): List<Point> {
        var t: Float? = null
        return weighIns.sortedBy { it.first }.map { (d, w) ->
            val next = t?.let { it + alpha * (w - it) } ?: w
            t = next
            Point(d, w, next)
        }
    }

    /** kg per week from a least-squares line through the last [days] days of trend values. */
    fun weeklyRate(points: List<Point>, today: LocalDate, days: Long = 28): Float? {
        val recent = points.filter { ChronoUnit.DAYS.between(it.date, today) <= days }
        if (recent.size < 3) return null
        val x0 = recent.first().date
        val xs = recent.map { ChronoUnit.DAYS.between(x0, it.date).toDouble() }
        val ys = recent.map { it.trend.toDouble() }
        val mx = xs.average(); val my = ys.average()
        val den = xs.sumOf { (it - mx) * (it - mx) }
        if (den == 0.0) return null
        val slopePerDay = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / den
        return (slopePerDay * 7).toFloat()
    }

    /** When the trend reaches [targetKg] at the current pace, or null if not moving towards it. */
    fun eta(points: List<Point>, targetKg: Float, today: LocalDate): LocalDate? {
        val rate = weeklyRate(points, today) ?: return null
        val current = points.lastOrNull()?.trend ?: return null
        val diff = targetKg - current
        if (abs(diff) < 0.2f) return today
        if (rate == 0f || (diff > 0) != (rate > 0)) return null
        val weeks = diff / rate
        if (weeks > 260) return null // more than 5 years: not a useful forecast
        return today.plusDays((weeks * 7).roundToInt().toLong())
    }
}

/** Calories burned from MET values: kcal = MET x 3.5 x kg / 200 x minutes. */
object Burn {
    fun met(type: WorkoutType, intensity: Int): Float {
        val base = when (type) {
            WorkoutType.STRENGTH -> 5f
            WorkoutType.CARDIO -> 7f
            WorkoutType.SPORT -> 6.5f
            WorkoutType.OTHER -> 4f
        }
        return base * when (intensity) { 1 -> 0.7f; 3 -> 1.35f; else -> 1f }
    }

    fun estimate(type: WorkoutType, minutes: Int, weightKg: Float, intensity: Int = 2): Int =
        (met(type, intensity) * 3.5f * weightKg / 200f * minutes).roundToInt()
}

/** Zero-token pattern spotting for Claude-chan's insight cards. */
object Insights {
    /** Compares calories on days after short sleep (<6 h) with days after good sleep (>=7 h). */
    fun sleepVsCalories(sleepByDate: Map<LocalDate, Float>, caloriesByDate: Map<LocalDate, Int>): String? {
        val short = mutableListOf<Int>(); val good = mutableListOf<Int>()
        for ((d, h) in sleepByDate) {
            val kcal = caloriesByDate[d] ?: continue
            if (h < 6f) short += kcal else if (h >= 7f) good += kcal
        }
        if (short.size < 2 || good.size < 2) return null
        val diff = short.average() - good.average()
        return when {
            diff > 150 -> "On days after short sleep you eat about ${diff.roundToInt()} kcal more. Sleep is a superpower~"
            diff < -150 -> "You eat about ${(-diff).roundToInt()} kcal less after short nights. Make sure you still fuel up!"
            else -> "Your eating stays steady no matter how you slept. Nice consistency!"
        }
    }

    fun moodVsCalories(moodByDate: Map<LocalDate, Int>, caloriesByDate: Map<LocalDate, Int>): String? {
        val low = moodByDate.filter { it.value <= 2 }.keys.mapNotNull { caloriesByDate[it] }
        val high = moodByDate.filter { it.value >= 4 }.keys.mapNotNull { caloriesByDate[it] }
        if (low.size < 2 || high.size < 2) return null
        val diff = low.average() - high.average()
        return if (diff > 150) "Low-mood days come with about ${diff.roundToInt()} extra kcal. Comfort food is okay, and so is a walk or a chat with me~" else null
    }
}
