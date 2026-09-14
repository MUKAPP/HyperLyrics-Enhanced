/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.juren233.hyperlyricsenhanced.common

/** Resolves the unified music-wave color mode while preserving legacy preferences. */
object IslandMusicWaveColorMode {
    const val UNSPECIFIED = -1

    fun resolve(
        storedMode: Int,
        hasLegacyCoverPreference: Boolean,
        legacyCoverEnabled: Boolean,
        legacyCoverGradient: Boolean,
    ): Int {
        if (storedMode in RootConstants.ISLAND_MUSIC_WAVE_COLOR_MODE_DISABLED..RootConstants.ISLAND_MUSIC_WAVE_COLOR_MODE_COVER_GRADIENT) {
            return storedMode
        }
        if (hasLegacyCoverPreference) {
            return when {
                !legacyCoverEnabled -> RootConstants.ISLAND_MUSIC_WAVE_COLOR_MODE_DISABLED
                legacyCoverGradient -> RootConstants.ISLAND_MUSIC_WAVE_COLOR_MODE_COVER_GRADIENT
                else -> RootConstants.ISLAND_MUSIC_WAVE_COLOR_MODE_COVER
            }
        }
        return RootConstants.DEFAULT_HOOK_ISLAND_MUSIC_WAVE_COLOR_MODE
    }

    fun isEnabled(mode: Int): Boolean =
        mode == RootConstants.ISLAND_MUSIC_WAVE_COLOR_MODE_COVER ||
            mode == RootConstants.ISLAND_MUSIC_WAVE_COLOR_MODE_COVER_GRADIENT

    fun usesCoverGradient(mode: Int): Boolean =
        mode == RootConstants.ISLAND_MUSIC_WAVE_COLOR_MODE_COVER_GRADIENT
}
