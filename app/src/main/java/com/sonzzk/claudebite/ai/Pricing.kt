package com.sonzzk.claudebite.ai

/** Price of one model in US dollars per million tokens (MTok). */
data class ModelPrice(val displayName: String, val inputPerMTok: Double, val outputPerMTok: Double)

/**
 * Approximate API prices, used by Settings to show "you've spent about $0.03".
 * These are list prices; the real bill can differ slightly (cache discounts, price changes).
 */
object Pricing {
    private val table: List<Pair<String, ModelPrice>> = listOf(
        // Longer ids first so "claude-sonnet-5-5" wins over "claude-sonnet-5".
        "claude-haiku-5-5" to ModelPrice("Haiku 5.5", 0.10, 0.50),
        "claude-sonnet-5-5" to ModelPrice("Sonnet 5.5", 2.0, 10.0),
        "claude-opus-5-5" to ModelPrice("Opus 5.5", 4.0, 20.0),
        // Models that can answer via server-side refusal fallback.
        "claude-sonnet-5" to ModelPrice("Sonnet 5", 2.0, 10.0),
        "claude-opus-5" to ModelPrice("Opus 5", 5.0, 25.0),
        "claude-opus-4-8" to ModelPrice("Opus 4.8", 5.0, 25.0),
    )

    /** Used for unknown model ids so the estimate errs on the expensive side rather than showing $0. */
    private val fallbackPrice = ModelPrice("Claude", 4.0, 20.0)

    fun priceFor(model: String): ModelPrice =
        table.firstOrNull { (prefix, _) -> model.startsWith(prefix) }?.second ?: fallbackPrice

    /** Friendly model name for the usage panel, e.g. "Haiku 5.5". */
    fun displayName(model: String): String =
        table.firstOrNull { (prefix, _) -> model.startsWith(prefix) }?.second?.displayName ?: model

    fun estimateCostUsd(model: String, inputTokens: Long, outputTokens: Long): Double {
        val price = priceFor(model)
        return (inputTokens * price.inputPerMTok + outputTokens * price.outputPerMTok) / 1_000_000.0
    }
}
