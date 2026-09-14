/* Copyright 2026 juren233 */
package com.juren233.hyperlyricsenhanced.root.island

/**
 * 决定状态栏空间变化何时才需要再触发一次岛宽刷新。
 *
 * 动态上限从状态栏的相关锚点投影计算可用长度，而岛在独立窗口中，宿主会围绕岛的宽度重新排布状态栏。
 * 如果对每一个观测到的布局变化都立刻重算，岛的宽度改动会被自己的下游布局结果再次触发：
 * 宽度永远无法稳定，每一轮还会重新应用岛两侧的内容（用户看到的就是"两侧内容不断刷新更新"）。
 *
 * 因此只有在满足下面两个条件时才请求刷新：
 * 1. 观测到的相关锚点在一段静默期内持续与"已应用基线"不同（瞬态布局不算）；
 * 2. 岛自身已不再处于宽度过渡中（过渡期内的布局变化是这次宽度写入的结果）。
 */
internal object IslandDynamicLimitRefreshPolicy {
    /** 状态栏需要保持静默的时间，短于此窗口的连续变化只推迟刷新而不触发。 */
    const val SETTLE_DELAY_MS = 160L

    enum class Decision { CANCEL, SCHEDULE }

    fun decide(
        enabled: Boolean,
        current: List<IslandDynamicLimitPolicy.Span>,
        baseline: List<IslandDynamicLimitPolicy.Span>?,
        islandWidthSettling: Boolean,
        coordinateTolerancePx: Int = 0,
        ignoreRightDrift: Boolean = false,
    ): Decision {
        if (!enabled) return Decision.CANCEL
        // 首次建立基线时必须刷新一次，让上限真正作用到岛宽结果上。
        if (baseline == null) return Decision.SCHEDULE
        if (IslandDynamicLimitPolicy.observationsEquivalent(
                current,
                baseline,
                coordinateTolerancePx,
                ignoreRightDrift,
            )
        ) return Decision.CANCEL
        // 岛宽仍在过渡时观测到的变化由这次宽度写入造成，不是新的状态栏内容。
        if (islandWidthSettling) return Decision.CANCEL
        return Decision.SCHEDULE
    }

    fun shouldRefresh(
        settled: List<IslandDynamicLimitPolicy.Span>,
        baseline: List<IslandDynamicLimitPolicy.Span>?,
        islandWidthSettling: Boolean,
        coordinateTolerancePx: Int = 0,
        ignoreRightDrift: Boolean = false,
    ): Boolean {
        if (baseline == null) return true
        if (islandWidthSettling) return false
        return !IslandDynamicLimitPolicy.observationsEquivalent(
            settled,
            baseline,
            coordinateTolerancePx,
            ignoreRightDrift,
        )
    }
}
