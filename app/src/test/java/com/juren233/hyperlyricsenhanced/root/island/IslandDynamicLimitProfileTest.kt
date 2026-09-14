/* Copyright 2026 juren233 */
package com.juren233.hyperlyricsenhanced.root.island

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class IslandDynamicLimitProfileTest {
    class Helper {
        fun getIslandViewHeight(): Int = 104
    }

    class ZeroHeightHelper {
        fun getIslandViewHeight(): Int = 0
    }

    class WrongDescriptorHelper {
        fun getIslandViewHeight(): Long = 104L
    }

    @Test fun `height getter retains original DEX name and integer descriptor`() {
        assertEquals("getIslandViewHeight", IslandDynamicLimitProfile.ISLAND_HEIGHT_GETTER)
        assertEquals(104, IslandDynamicLimitProfile.readIslandHeight(Helper()))
        assertThrows(IllegalArgumentException::class.java) {
            IslandDynamicLimitProfile.readIslandHeight(WrongDescriptorHelper())
        }
    }

    @Test fun `invalid native height fails instead of borrowing expanded window height`() {
        assertThrows(IllegalArgumentException::class.java) {
            IslandDynamicLimitProfile.readIslandHeight(ZeroHeightHelper())
        }
    }

    @Test fun `first return to island caps native width using capsule floor`() {
        // PID 28800: identical native geometry before/after the 2670px host closes.
        // Using the expanded host as a floor leaves 752px instead of applying 642px.
        val native = IslandDynamicLimitPolicy.Geometry(752, 336, 336, 224)
        val obstacles = listOf(IslandDynamicLimitPolicy.Span(83, 260, text = true))
        val floor = IslandDynamicLimitProfile.readIslandHeight(Helper()) + 6
        val limited = IslandDynamicLimitPolicy.limit(native, 1200, obstacles, 16, minWidth = floor)
        assertEquals(IslandDynamicLimitPolicy.Geometry(648, 284, 284, 276), limited)
        assertEquals(native, IslandDynamicLimitPolicy.limit(native, 1200, obstacles, 16, minWidth = 2676))
    }
}
