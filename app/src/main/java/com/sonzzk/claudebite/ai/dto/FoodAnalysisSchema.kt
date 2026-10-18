package com.sonzzk.claudebite.ai.dto

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * The JSON schema sent as `output_config.format` so Claude must reply with pure JSON matching
 * [FoodAnalysisDto] (no prose, so no wasted tokens).
 *
 * We write the schema by hand instead of using the SDK's `outputConfig(Class)` overload: that one
 * generates the schema with reflection at runtime, which is fragile on Android after R8 shrinking.
 * Structured outputs require every property to be listed in `required` and `additionalProperties: false`.
 */
object FoodAnalysisSchema {

    private fun number(description: String) = mapOf("type" to "number", "description" to description)
    private fun string(description: String) = mapOf("type" to "string", "description" to description)

    private val itemSchema: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "name" to string("Short food name, e.g. 'Grilled chicken breast'"),
            "portion" to string("Estimated portion, e.g. '150 g' or '1 cup'"),
            "calories" to number("kcal for this portion"),
            "proteinG" to number("grams"),
            "carbsG" to number("grams"),
            "fatG" to number("grams"),
            "fiberG" to number("grams"),
            "sugarG" to number("grams"),
        ),
        "required" to listOf("name", "portion", "calories", "proteinG", "carbsG", "fatG", "fiberG", "sugarG"),
        "additionalProperties" to false,
    )

    /** Plain maps/lists; ClaudeCoach wraps this with the SDK's `JsonValue.from(...)`. */
    val schema: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "items" to mapOf("type" to "array", "items" to itemSchema),
            "healthScore" to mapOf("type" to "integer", "description" to "1-10 fit for the user's goal"),
            "grade" to mapOf("type" to "string", "enum" to listOf("A", "B", "C", "D", "F")),
            "comment" to string("1-2 cute sentences in Claude-chan's voice"),
            "tip" to string("One short practical tip"),
            "confidence" to number("0-1 confidence in the estimate"),
        ),
        "required" to listOf("items", "healthScore", "grade", "comment", "tip", "confidence"),
        "additionalProperties" to false,
    )
}

/** Parses Claude's JSON text into [FoodAnalysisDto] with Jackson (bundled with the Anthropic SDK). */
object FoodAnalysisParser {
    private val mapper: ObjectMapper = ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
        .configure(DeserializationFeature.ACCEPT_FLOAT_AS_INT, true)

    /** Throws a Jackson exception if [json] is not valid; ClaudeCoach turns that into a friendly error. */
    fun parse(json: String): FoodAnalysisDto = mapper.readValue(json.trim(), FoodAnalysisDto::class.java)
}
