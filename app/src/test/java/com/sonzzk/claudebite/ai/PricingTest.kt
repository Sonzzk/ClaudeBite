package com.sonzzk.claudebite.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class PricingTest {

    @Test
    fun estimateCost_perModel() {
        assertEquals(0.10 + 0.50, Pricing.estimateCostUsd("claude-haiku-5-5", 1_000_000, 1_000_000), 1e-9)
        assertEquals(2.0 + 10.0, Pricing.estimateCostUsd("claude-sonnet-5-5", 1_000_000, 1_000_000), 1e-9)
        assertEquals(4.0 + 20.0, Pricing.estimateCostUsd("claude-opus-5-5", 1_000_000, 1_000_000), 1e-9)
        // A typical photo analysis on Sonnet: ~1.6k in, ~400 out.
        assertEquals(0.0072, Pricing.estimateCostUsd("claude-sonnet-5-5", 1_600, 400), 1e-9)
    }

    @Test
    fun displayName_matchesLongestPrefix() {
        assertEquals("Sonnet 5.5", Pricing.displayName("claude-sonnet-5-5"))
        assertEquals("Sonnet 5", Pricing.displayName("claude-sonnet-5"))
        assertEquals("some-model", Pricing.displayName("some-model"))
    }
}
