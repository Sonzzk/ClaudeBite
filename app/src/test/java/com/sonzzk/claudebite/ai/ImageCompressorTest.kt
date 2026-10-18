package com.sonzzk.claudebite.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageCompressorTest {

    @Test
    fun calculateInSampleSize_keepsLongSideAboveMax() {
        assertEquals(1, ImageCompressor.calculateInSampleSize(800, 600, 1024))
        assertEquals(2, ImageCompressor.calculateInSampleSize(3000, 2000, 1024))
        assertEquals(4, ImageCompressor.calculateInSampleSize(4032, 3024, 1024))
        assertEquals(1, ImageCompressor.calculateInSampleSize(0, 0, 1024))
    }
}
