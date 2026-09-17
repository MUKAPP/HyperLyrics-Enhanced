/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.juren233.hyperlyricsenhanced.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureEntryGateTest {

    @Test
    fun `disabling an entry stashes the feature switch and disables the feature`() {
        val outcome = FeatureEntryGate.resolveOnEntryToggle(
            entryEnabled = false,
            currentFeatureValue = true,
            stashedValue = null,
        )

        assertEquals(false, outcome.featureValue)
        assertEquals(true, outcome.stashValue)
        assertFalse(outcome.clearStash)
    }

    @Test
    fun `disabling an entry twice keeps the earliest stashed value`() {
        val outcome = FeatureEntryGate.resolveOnEntryToggle(
            entryEnabled = false,
            currentFeatureValue = false,
            stashedValue = true,
        )

        assertEquals(false, outcome.featureValue)
        assertEquals(true, outcome.stashValue)
    }

    @Test
    fun `enabling an entry restores every feature setting saved before it was disabled`() {
        val outcome = FeatureEntryGate.resolveOnEntryToggle(
            entryEnabled = true,
            currentFeatureValue = false,
            stashedValue = true,
        )

        assertEquals(true, outcome.featureValue)
        assertNull(outcome.stashValue)
        assertTrue(outcome.clearStash)
    }

    @Test
    fun `enabling an entry without a stashed value leaves the feature switch untouched`() {
        val outcome = FeatureEntryGate.resolveOnEntryToggle(
            entryEnabled = true,
            currentFeatureValue = true,
            stashedValue = null,
        )

        assertNull(outcome.featureValue)
        assertNull(outcome.stashValue)
        assertFalse(outcome.clearStash)
    }

    @Test
    fun `stash key is scoped by the feature key`() {
        assertEquals(
            UIConstants.KEY_FEATURE_ENTRY_STASH_PREFIX + RootConstants.KEY_HOOK_ENABLE_HYPER_ISLAND,
            FeatureEntryGate.stashKey(RootConstants.KEY_HOOK_ENABLE_HYPER_ISLAND),
        )
    }
}
