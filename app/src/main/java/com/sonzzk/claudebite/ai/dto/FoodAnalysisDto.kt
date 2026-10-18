package com.sonzzk.claudebite.ai.dto

import com.sonzzk.claudebite.ai.AnalyzedItem
import com.sonzzk.claudebite.ai.FoodAnalysis
import kotlin.math.roundToInt

/**
 * The exact JSON shape Claude returns for a food analysis (enforced by structured outputs, see
 * [FoodAnalysisSchema]). Jackson fills these via the no-arg constructor + setters, which is why the
 * fields are `var` with defaults. ProGuard keeps this package so the names survive minification.
 */
data class FoodAnalysisDto(
    var items: List<FoodItemDto> = emptyList(),
    var healthScore: Int = 5,
    var grade: String = "",
    var comment: String = "",
    var tip: String = "",
    var confidence: Double = 0.5,
)

/** One food in [FoodAnalysisDto]. Numbers are for the whole portion shown. */
data class FoodItemDto(
    var name: String = "",
    var portion: String = "",
    var calories: Double = 0.0,
    var proteinG: Double = 0.0,
    var carbsG: Double = 0.0,
    var fatG: Double = 0.0,
    var fiberG: Double = 0.0,
    var sugarG: Double = 0.0,
)

/** Letter grade that matches a 1-10 score. Used when Claude's grade is missing or odd. */
fun gradeForScore(score: Int): String = when {
    score >= 9 -> "A"
    score >= 7 -> "B"
    score >= 5 -> "C"
    score >= 3 -> "D"
    else -> "F"
}

/** Turns a NaN/negative number into 0 (models occasionally output -1 for "unknown"). */
private fun Double.nonNegative(): Double = if (isNaN() || this < 0.0) 0.0 else this

private fun Double.grams(): Float = (nonNegative() * 10).roundToInt() / 10f

/**
 * Converts Claude's raw answer into the app's [FoodAnalysis], cleaning up anything out of range:
 * score clamped to 1-10, grade A-F (derived from the score if invalid), no negative nutrients,
 * confidence clamped to 0-1.
 */
fun FoodAnalysisDto.toFoodAnalysis(modelUsed: String): FoodAnalysis {
    val score = healthScore.coerceIn(1, 10)
    val cleanGrade = grade.trim().uppercase().take(1).takeIf { it in VALID_GRADES } ?: gradeForScore(score)
    val cleanConfidence = if (confidence.isNaN()) 0.5 else confidence.coerceIn(0.0, 1.0)
    return FoodAnalysis(
        items = items.map { it.toAnalyzedItem() },
        healthScore = score,
        grade = cleanGrade,
        comment = comment.trim(),
        tip = tip.trim(),
        confidence = cleanConfidence.toFloat(),
        modelUsed = modelUsed,
    )
}

fun FoodItemDto.toAnalyzedItem(): AnalyzedItem = AnalyzedItem(
    name = name.trim().ifBlank { "Food" },
    portion = portion.trim(),
    calories = calories.nonNegative().roundToInt(),
    proteinG = proteinG.grams(),
    carbsG = carbsG.grams(),
    fatG = fatG.grams(),
    fiberG = fiberG.grams(),
    sugarG = sugarG.grams(),
)

private val VALID_GRADES = setOf("A", "B", "C", "D", "F")
