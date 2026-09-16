/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.juren233.hyperlyricsenhanced.ui.page.main

import org.junit.Assert.assertEquals
import org.junit.Test

class MainTabPolicyTest {

    @Test
    fun `keeps home and apple music pages with about when both entries are enabled`() {
        assertEquals(
            listOf(MainTab.Home, MainTab.AppleMusic, MainTab.About),
            MainTabPolicy.tabs(
                superIslandEntryEnabled = true,
                aodLyricsEntryEnabled = true,
                appleMusicEntryEnabled = true,
            ),
        )
    }

    @Test
    fun `apple music becomes the first page when both xiaomi entries are disabled`() {
        assertEquals(
            listOf(MainTab.AppleMusic, MainTab.About),
            MainTabPolicy.tabs(
                superIslandEntryEnabled = false,
                aodLyricsEntryEnabled = false,
                appleMusicEntryEnabled = true,
            ),
        )
    }

    @Test
    fun `keeps home page with a single xiaomi entry`() {
        assertEquals(
            listOf(MainTab.Home, MainTab.About),
            MainTabPolicy.tabs(
                superIslandEntryEnabled = true,
                aodLyricsEntryEnabled = false,
                appleMusicEntryEnabled = false,
            ),
        )
        assertEquals(
            listOf(MainTab.Home, MainTab.About),
            MainTabPolicy.tabs(
                superIslandEntryEnabled = false,
                aodLyricsEntryEnabled = true,
                appleMusicEntryEnabled = false,
            ),
        )
    }

    @Test
    fun `shows only the guidance page when every entry is disabled`() {
        assertEquals(
            listOf(MainTab.Unsupported),
            MainTabPolicy.tabs(
                superIslandEntryEnabled = false,
                aodLyricsEntryEnabled = false,
                appleMusicEntryEnabled = false,
            ),
        )
    }

    @Test
    fun `home page stays visible while at least one xiaomi entry is enabled`() {
        assertEquals(
            true,
            MainTabPolicy.isHomePageVisible(
                superIslandEntryEnabled = true,
                aodLyricsEntryEnabled = true,
            ),
        )
        assertEquals(
            true,
            MainTabPolicy.isHomePageVisible(
                superIslandEntryEnabled = true,
                aodLyricsEntryEnabled = false,
            ),
        )
        assertEquals(
            true,
            MainTabPolicy.isHomePageVisible(
                superIslandEntryEnabled = false,
                aodLyricsEntryEnabled = true,
            ),
        )
    }

    @Test
    fun `home page content only moves into settings when both xiaomi entries are disabled`() {
        assertEquals(
            false,
            MainTabPolicy.isHomePageVisible(
                superIslandEntryEnabled = false,
                aodLyricsEntryEnabled = false,
            ),
        )
    }
}
