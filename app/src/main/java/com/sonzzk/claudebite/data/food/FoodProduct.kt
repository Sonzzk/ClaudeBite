package com.sonzzk.claudebite.data.food

import com.sonzzk.claudebite.ai.AnalyzedItem
import kotlin.math.roundToInt

/** A food with nutrition per 100 g, from the built-in list, Open Food Facts or a barcode. */
data class FoodProduct(
    val name: String,
    val brand: String? = null,
    val kcal100: Float,
    val protein100: Float,
    val carbs100: Float,
    val fat100: Float,
    val fiber100: Float = 0f,
    val sugar100: Float = 0f,
    /** Typical serving in grams, and how people say it ("1 medium", "1 cup"). */
    val servingG: Float = 100f,
    val servingLabel: String = "100 g",
    val source: Source = Source.BUILT_IN,
    val barcode: String? = null,
) {
    enum class Source { BUILT_IN, OPEN_FOOD_FACTS, RECENT }

    val displayName: String get() = if (brand.isNullOrBlank()) name else "$name · $brand"

    /** Nutrition for [grams] of this food, as a review-screen item. */
    fun toItem(grams: Float): AnalyzedItem {
        val f = grams / 100f
        fun r(v: Float) = (v * f * 10).roundToInt() / 10f
        val portion = if (kotlin.math.abs(grams - servingG) < 0.5f && servingLabel != "100 g") {
            "$servingLabel (${grams.roundToInt()} g)"
        } else "${grams.roundToInt()} g"
        return AnalyzedItem(
            name = displayName,
            portion = portion,
            calories = (kcal100 * f).roundToInt(),
            proteinG = r(protein100),
            carbsG = r(carbs100),
            fatG = r(fat100),
            fiberG = r(fiber100),
            sugarG = r(sugar100),
        )
    }

    /** Zero-token health score 1-10: rewards protein and fiber density, penalises sugar and energy density. */
    fun healthScore(): Int {
        val kcal = kcal100.coerceAtLeast(1f)
        var s = 5f
        s += (protein100 * 4f / kcal) * 10f        // protein share of calories
        s += (fiber100 / 3f).coerceAtMost(2f)
        s -= (sugar100 / 10f).coerceAtMost(3f)
        s -= ((kcal100 - 250f) / 150f).coerceIn(0f, 2f)
        return s.roundToInt().coerceIn(1, 10)
    }
}
