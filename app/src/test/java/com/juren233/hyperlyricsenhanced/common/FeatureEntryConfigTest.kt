/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.juren233.hyperlyricsenhanced.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureEntryConfigTest {

    @Test
    fun `accepts xiaomi and redmi manufacturers or brands`() {
        assertTrue(FeatureEntryConfig.isXiaomiOrRedmi("Xiaomi", "Xiaomi"))
        assertTrue(FeatureEntryConfig.isXiaomiOrRedmi("Xiaomi", "Redmi"))
        assertTrue(FeatureEntryConfig.isXiaomiOrRedmi("Redmi", "Redmi"))
        assertTrue(FeatureEntryConfig.isXiaomiOrRedmi(" xiaomi ", null))
    }

    @Test
    fun `rejects other manufacturers and blank values`() {
        assertFalse(FeatureEntryConfig.isXiaomiOrRedmi("samsung", "SM-S9280"))
        assertFalse(FeatureEntryConfig.isXiaomiOrRedmi("Google", "Pixel"))
        assertFalse(FeatureEntryConfig.isXiaomiOrRedmi(null, ""))
        assertFalse(FeatureEntryConfig.isXiaomiOrRedmi("  ", null))
    }
}
