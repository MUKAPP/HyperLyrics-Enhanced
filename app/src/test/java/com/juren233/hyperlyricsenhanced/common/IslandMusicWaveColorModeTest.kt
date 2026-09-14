/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.juren233.hyperlyricsenhanced.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandMusicWaveColorModeTest {
    @Test
    fun `explicit mode wins over legacy switches`() {
        assertEquals(
            RootConstants.ISLAND_MUSIC_WAVE_COLOR_MODE_DISABLED,
            IslandMusicWaveColorMode.resolve(
                storedMode = RootConstants.ISLAND_MUSIC_WAVE_COLOR_MODE_DISABLED,
                hasLegacyCoverPreference = true,
                legacyCoverEnabled = true,
                legacyCoverGradient = true,
            )
        )
        assertEquals(
            RootConstants.ISLAND_MUSIC_WAVE_COLOR_MODE_COVER_GRADIENT,
            IslandMusicWaveColorMode.resolve(
                storedMode = RootConstants.ISLAND_MUSIC_WAVE_COLOR_MODE_COVER_GRADIENT,
                hasLegacyCoverPreference = false,
                legacyCoverEnabled = false,
                legacyCoverGradient = false,
            )
        )
    }

    @Test
    fun `legacy switches migrate to equivalent color modes`() {
        assertEquals(
            RootConstants.ISLAND_MUSIC_WAVE_COLOR_MODE_DISABLED,
            IslandMusicWaveColorMode.resolve(
                storedMode = IslandMusicWaveColorMode.UNSPECIFIED,
                hasLegacyCoverPreference = true,
                legacyCoverEnabled = false,
                legacyCoverGradient = false,
            )
        )
        assertEquals(
            RootConstants.ISLAND_MUSIC_WAVE_COLOR_MODE_COVER,
            IslandMusicWaveColorMode.resolve(
                storedMode = IslandMusicWaveColorMode.UNSPECIFIED,
                hasLegacyCoverPreference = true,
                legacyCoverEnabled = true,
                legacyCoverGradient = false,
            )
        )
        assertEquals(
            RootConstants.ISLAND_MUSIC_WAVE_COLOR_MODE_COVER_GRADIENT,
            IslandMusicWaveColorMode.resolve(
                storedMode = IslandMusicWaveColorMode.UNSPECIFIED,
                hasLegacyCoverPreference = true,
                legacyCoverEnabled = true,
                legacyCoverGradient = true,
            )
        )
    }

    @Test
    fun `fresh install without legacy preference defaults to cover color`() {
        assertEquals(
            RootConstants.ISLAND_MUSIC_WAVE_COLOR_MODE_COVER,
            IslandMusicWaveColorMode.resolve(
                storedMode = IslandMusicWaveColorMode.UNSPECIFIED,
                hasLegacyCoverPreference = false,
                legacyCoverEnabled = false,
                legacyCoverGradient = false,
            )
        )
        assertEquals(
            RootConstants.DEFAULT_HOOK_ISLAND_MUSIC_WAVE_COLOR_MODE,
            RootConstants.ISLAND_MUSIC_WAVE_COLOR_MODE_COVER,
        )
    }

    @Test
    fun `enabled and gradient helpers follow the unified mode`() {
        assertFalse(IslandMusicWaveColorMode.isEnabled(RootConstants.ISLAND_MUSIC_WAVE_COLOR_MODE_DISABLED))
        assertTrue(IslandMusicWaveColorMode.isEnabled(RootConstants.ISLAND_MUSIC_WAVE_COLOR_MODE_COVER))
        assertTrue(IslandMusicWaveColorMode.isEnabled(RootConstants.ISLAND_MUSIC_WAVE_COLOR_MODE_COVER_GRADIENT))
        assertFalse(
            IslandMusicWaveColorMode.usesCoverGradient(
                RootConstants.ISLAND_MUSIC_WAVE_COLOR_MODE_COVER
            )
        )
        assertTrue(
            IslandMusicWaveColorMode.usesCoverGradient(
                RootConstants.ISLAND_MUSIC_WAVE_COLOR_MODE_COVER_GRADIENT
            )
        )
    }
}
