package com.sonzzk.claudebite.ai.dto

import org.junit.Assert.assertEquals
import org.junit.Test

class FoodAnalysisDtoTest {

    @Test
    fun mapping_clampsAndCleansValues() {
        val dto = FoodAnalysisDto(
            items = listOf(
                FoodItemDto(name = "  Rice ", portion = "1 cup", calories = 205.4, proteinG = 4.26, carbsG = 44.5, fatG = -1.0, fiberG = Double.NaN, sugarG = 0.1),
                FoodItemDto(name = "", calories = -50.0),
            ),
            healthScore = 14,
            grade = "a+",
            comment = " Yum~ ",
            tip = "Add veggies",
            confidence = 1.7,
        )
        val result = dto.toFoodAnalysis("claude-haiku-5-5")

        assertEquals(10, result.healthScore)
        assertEquals("A", result.grade)
        assertEquals("Yum~", result.comment)
        assertEquals(1f, result.confidence)
        assertEquals("claude-haiku-5-5", result.modelUsed)

        val rice = result.items[0]
        assertEquals("Rice", rice.name)
        assertEquals(205, rice.calories)
        assertEquals(4.3f, rice.proteinG)
        assertEquals(0f, rice.fatG)
        assertEquals(0f, rice.fiberG)

        val unnamed = result.items[1]
        assertEquals("Food", unnamed.name)
        assertEquals(0, unnamed.calories)
        assertEquals(205, result.totalCalories)
    }

    @Test
    fun mapping_gradeFallsBackToScore() {
        assertEquals("B", FoodAnalysisDto(healthScore = 7, grade = "").toFoodAnalysis("m").grade)
        assertEquals("F", FoodAnalysisDto(healthScore = -3, grade = "Z").toFoodAnalysis("m").grade)
        assertEquals(1, FoodAnalysisDto(healthScore = -3).toFoodAnalysis("m").healthScore)
        assertEquals("C", FoodAnalysisDto(healthScore = 5, grade = "excellent").toFoodAnalysis("m").grade)
        assertEquals(0f, FoodAnalysisDto(confidence = -0.2).toFoodAnalysis("m").confidence)
    }

    @Test
    fun gradeForScore_table() {
        assertEquals(listOf("F", "F", "D", "D", "C", "C", "B", "B", "A", "A"), (1..10).map { gradeForScore(it) })
    }

    @Test
    fun parser_readsStructuredOutputJson() {
        val json = """
            {"items":[{"name":"Banana","portion":"1 medium","calories":105,"proteinG":1.3,"carbsG":27,"fatG":0.4,"fiberG":3.1,"sugarG":14.4}],
             "healthScore":8,"grade":"B","comment":"Potassium power~","tip":"Pair with yogurt for protein.","confidence":0.9,"extra":"ignored"}
        """.trimIndent()
        val result = FoodAnalysisParser.parse(json).toFoodAnalysis("claude-sonnet-5-5")
        assertEquals(1, result.items.size)
        assertEquals("Banana", result.items[0].name)
        assertEquals(105, result.items[0].calories)
        assertEquals(27f, result.items[0].carbsG)
        assertEquals("B", result.grade)
        assertEquals(0.9f, result.confidence)
    }

    @Test
    fun schema_requiresEveryProperty() {
        @Suppress("UNCHECKED_CAST")
        val props = FoodAnalysisSchema.schema["properties"] as Map<String, Any>
        assertEquals(props.keys, (FoodAnalysisSchema.schema["required"] as List<*>).toSet())
        assertEquals(false, FoodAnalysisSchema.schema["additionalProperties"])
    }
}
