/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.juren233.hyperlyricsenhanced.root.source

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CentralPlaybackPositionWitnessTest {
    @Test
    fun `continuous active positions recover a stale stopped sink`() {
        val witness = CentralPlaybackPositionWitness()
        witness.onSinkStopped()

        assertFalse(witness.observeActivePosition(1_000L))
        assertFalse(witness.observeActivePosition(1_033L))
        assertTrue(witness.observeActivePosition(1_066L))
        assertFalse(witness.observeActivePosition(1_099L))
    }

    @Test
    fun `one or two in-flight positions after pause do not resume playback`() {
        val witness = CentralPlaybackPositionWitness()
        witness.onSinkPlaybackState(false)

        assertFalse(witness.observeActivePosition(2_000L))
        assertFalse(witness.observeActivePosition(2_033L))
    }

    @Test
    fun `large gaps restart the witness sequence`() {
        val witness = CentralPlaybackPositionWitness()
        witness.onSinkStopped()

        assertFalse(witness.observeActivePosition(3_000L))
        assertFalse(witness.observeActivePosition(3_033L))
        assertFalse(witness.observeActivePosition(4_000L))
        assertFalse(witness.observeActivePosition(4_033L))
        assertTrue(witness.observeActivePosition(4_066L))
    }

    @Test
    fun `known active sink ignores position witnesses`() {
        val witness = CentralPlaybackPositionWitness()
        witness.onSinkPlaybackState(true)

        assertFalse(witness.observeActivePosition(5_000L))
        assertFalse(witness.observeActivePosition(5_033L))
        assertFalse(witness.observeActivePosition(5_066L))
    }

    @Test
    fun `actual stopped sink overrides a previously delivered playing state`() {
        val witness = CentralPlaybackPositionWitness()
        witness.onSinkPlaybackState(true)

        assertFalse(witness.observeActivePosition(6_000L, false, true))
        assertFalse(witness.observeActivePosition(6_033L, false, true))
        assertTrue(witness.observeActivePosition(6_066L, false, true))
        assertFalse(witness.observeActivePosition(6_099L, true, true))
    }

    @Test
    fun `each later track can recover if its sink is stopped without a new upstream edge`() {
        val witness = CentralPlaybackPositionWitness()
        witness.onSinkPlaybackState(true)
        repeat(12) { track ->
            val start = 10_000L + track * 1_000L
            assertFalse(witness.observeActivePosition(start, false, true))
            assertFalse(witness.observeActivePosition(start + 33, false, true))
            assertTrue(witness.observeActivePosition(start + 66, false, true))
            assertFalse(witness.observeActivePosition(start + 99, true, true))
        }
    }

    @Test
    fun `authoritative pause or suppression vetoes even continuous stale positions`() {
        val witness = CentralPlaybackPositionWitness()
        witness.onSinkPlaybackState(true)
        repeat(20) { sample ->
            assertFalse(witness.observeActivePosition(20_000L + sample * 41, false, false))
        }
        assertFalse(witness.observeActivePosition(21_000L, false, true))
        assertFalse(witness.observeActivePosition(21_033L, false, true))
        assertTrue(witness.observeActivePosition(21_066L, false, true))
    }

    @Test
    fun `authoritative pause clears a partially collected recovery sequence`() {
        val witness = CentralPlaybackPositionWitness()
        assertFalse(witness.observeActivePosition(30_000L, false, true))
        assertFalse(witness.observeActivePosition(30_033L, false, true))
        assertFalse(witness.observeActivePosition(30_066L, false, false))
        assertFalse(witness.observeActivePosition(30_099L, false, true))
        assertFalse(witness.observeActivePosition(30_132L, false, true))
        assertTrue(witness.observeActivePosition(30_165L, false, true))
    }

    @Test
    fun `standalone central preserves bounded witness fallback without local authority`() {
        val witness = CentralPlaybackPositionWitness()
        witness.onSinkPlaybackState(true)
        assertFalse(witness.observeActivePosition(40_000L, false, null))
        assertFalse(witness.observeActivePosition(40_033L, false, null))
        assertTrue(witness.observeActivePosition(40_066L, false, null))
    }

    @Test
    fun `actual playing sink cancels stale recovery witnesses`() {
        val witness = CentralPlaybackPositionWitness()
        witness.onSinkStopped()
        assertFalse(witness.observeActivePosition(50_000L, false, true))
        assertFalse(witness.observeActivePosition(50_033L, false, true))
        assertFalse(witness.observeActivePosition(50_066L, true, true))
        assertFalse(witness.observeActivePosition(50_099L, true, true))
    }

}
