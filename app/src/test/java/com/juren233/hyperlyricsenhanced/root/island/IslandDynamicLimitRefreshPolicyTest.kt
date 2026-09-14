/* Copyright 2026 juren233 */
package com.juren233.hyperlyricsenhanced.root.island

import com.juren233.hyperlyricsenhanced.root.island.IslandDynamicLimitPolicy.Span
import com.juren233.hyperlyricsenhanced.root.island.IslandDynamicLimitRefreshPolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandDynamicLimitRefreshPolicyTest {
    private val clock = listOf(Span(20, 120, text = true))
    private val clockAndIcon = listOf(Span(20, 120, text = true), Span(900, 980))

    @Test
    fun `disabled monitor cancels any pending refresh`() {
        assertEquals(
            Decision.CANCEL,
            IslandDynamicLimitRefreshPolicy.decide(
                enabled = false,
                current = clockAndIcon,
                baseline = clock,
                islandWidthSettling = false,
            ),
        )
    }

    @Test
    fun `first observation schedules the baseline refresh`() {
        assertEquals(
            Decision.SCHEDULE,
            IslandDynamicLimitRefreshPolicy.decide(
                enabled = true,
                current = clockAndIcon,
                baseline = null,
                islandWidthSettling = false,
            ),
        )
    }

    @Test
    fun `unchanged obstacles never refresh`() {
        assertEquals(
            Decision.CANCEL,
            IslandDynamicLimitRefreshPolicy.decide(
                enabled = true,
                current = clock,
                baseline = clock,
                islandWidthSettling = false,
            ),
        )
    }

    @Test
    fun `change observed while the island width is settling is self induced`() {
        assertEquals(
            Decision.CANCEL,
            IslandDynamicLimitRefreshPolicy.decide(
                enabled = true,
                current = clockAndIcon,
                baseline = clock,
                islandWidthSettling = true,
            ),
        )
        assertFalse(
            IslandDynamicLimitRefreshPolicy.shouldRefresh(
                settled = clockAndIcon,
                baseline = clock,
                islandWidthSettling = true,
            ),
        )
    }

    @Test
    fun `settled change schedules exactly one refresh`() {
        assertEquals(
            Decision.SCHEDULE,
            IslandDynamicLimitRefreshPolicy.decide(
                enabled = true,
                current = clockAndIcon,
                baseline = clock,
                islandWidthSettling = false,
            ),
        )
        assertTrue(
            IslandDynamicLimitRefreshPolicy.shouldRefresh(
                settled = clockAndIcon,
                baseline = clock,
                islandWidthSettling = false,
            ),
        )
    }

    @Test
    fun `settle check drops a change that reverted to the applied baseline`() {
        assertFalse(
            IslandDynamicLimitRefreshPolicy.shouldRefresh(
                settled = clock,
                baseline = clock,
                islandWidthSettling = false,
            ),
        )
    }

    @Test
    fun `settle check while still animating never refreshes`() {
        assertFalse(
            IslandDynamicLimitRefreshPolicy.shouldRefresh(
                settled = clockAndIcon,
                baseline = clock,
                islandWidthSettling = true,
            ),
        )
    }

    @Test
    fun `baseline is still established when the first sample lands mid transition`() {
        assertTrue(
            IslandDynamicLimitRefreshPolicy.shouldRefresh(
                settled = clockAndIcon,
                baseline = null,
                islandWidthSettling = true,
            ),
        )
    }

    @Test
    fun `alternating island induced layouts during one transition cannot refresh`() {
        // 一次宽度写入期间状态栏可能在两组布局之间反复跳动；过渡窗口内两者都必须被抑制。
        var baseline: List<Span>? = clock
        val samples = listOf(clockAndIcon, clock, clockAndIcon, clock)
        for (sample in samples) {
            val decision = IslandDynamicLimitRefreshPolicy.decide(
                enabled = true,
                current = sample,
                baseline = baseline,
                islandWidthSettling = true,
            )
            assertEquals(Decision.CANCEL, decision)
            // 基线只能来自我们真正应用过的那一次结果，不能被抑制样本推进。
            baseline = if (decision == Decision.SCHEDULE) sample else baseline
        }
        assertEquals(clock, baseline)
    }

    @Test
    fun `phone time anchor jitter inside tolerance never refreshes`() {
        val shiftedClock = listOf(Span(24, 125, text = true))
        assertEquals(
            Decision.CANCEL,
            IslandDynamicLimitRefreshPolicy.decide(
                enabled = true,
                current = shiftedClock,
                baseline = clock,
                islandWidthSettling = false,
                coordinateTolerancePx = 8,
            ),
        )
        assertFalse(
            IslandDynamicLimitRefreshPolicy.shouldRefresh(
                settled = shiftedClock,
                baseline = clock,
                islandWidthSettling = false,
                coordinateTolerancePx = 8,
            ),
        )
    }

    @Test
    fun `phone right side changes disappear before refresh policy`() {
        val before = IslandDynamicLimitPolicy.relevantObservation(
            listOf(Span(20, 120, text = true), Span(800, 850)),
            screenWidth = 1000,
        )
        val after = IslandDynamicLimitPolicy.relevantObservation(
            listOf(Span(20, 120, text = true), Span(720, 760), Span(900, 990)),
            screenWidth = 1000,
        )
        assertEquals(before, after)
        assertEquals(
            Decision.CANCEL,
            IslandDynamicLimitRefreshPolicy.decide(
                enabled = true,
                current = after,
                baseline = before,
                islandWidthSettling = false,
            ),
        )
    }
}
