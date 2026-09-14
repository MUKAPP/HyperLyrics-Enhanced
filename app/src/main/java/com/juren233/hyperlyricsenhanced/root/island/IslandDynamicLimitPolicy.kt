/* Copyright 2026 juren233 */
package com.juren233.hyperlyricsenhanced.root.island

import kotlin.math.abs

/** Geometry uses screen pixels; slot content has already been measured upstream. */
internal object IslandDynamicLimitPolicy {
    data class Span(val left: Int, val right: Int, val text: Boolean = false)
    data class Geometry(val width: Int, val left: Int, val right: Int, val x: Int)

    /**
     * 手机端唯一锚点是岛中心左侧的状态栏时间/日期文本右缘。
     * 右侧文本、图标及左侧通知图标都不参与手机端上限或刷新判断。
     */
    fun relevantObservation(
        obstacles: List<Span>,
        screenWidth: Int,
        leftAnchor: Int? = null,
    ): List<Span> {
        if (leftAnchor != null) return obstacles.distinct().sortedBy { it.left }
        val center = screenWidth / 2
        return listOfNotNull(
            obstacles
                .asSequence()
                .filter { it.text && it.right <= center }
                .maxByOrNull { it.right },
        )
    }

    /**
     * [ignoreRightDrift] = true（手机端）时忽略右缘漂移：状态栏时钟右缘会被岛自身
     * 挤压（岛变宽 → 时钟被压窄），实时右缘是岛宽的影子，追逐它会让上限自激振荡
     * （真机 45s 内岛宽被重定 14 次，642↔680px）。结构性变化（条目数量、文本属性、
     * 左缘移动）才视为新的状态栏内容。
     */
    fun observationsEquivalent(
        first: List<Span>?,
        second: List<Span>?,
        coordinateTolerancePx: Int,
        ignoreRightDrift: Boolean = false,
    ): Boolean {
        if (first == null || second == null || first.size != second.size) return first == second
        val tolerance = coordinateTolerancePx.coerceAtLeast(0)
        return first.zip(second).all { (a, b) ->
            a.text == b.text &&
                abs(a.left - b.left) <= tolerance &&
                (ignoreRightDrift || abs(a.right - b.right) <= tolerance)
        }
    }

    fun limit(
        original: Geometry,
        screenWidth: Int,
        obstacles: List<Span>,
        gap: Int,
        leftAnchor: Int? = null,
        minWidth: Int = 0,
    ): Geometry {
        if (original.width <= 0 || screenWidth <= 0) return original
        val anchor = leftAnchor ?: (original.x + original.width / 2)
        // 手机只以时间为锚点：用时间文本右缘确定居中岛的左右对称上限，右侧图标
        // 完全不参与。平板岛锚定在日期位，继续使用独立的右侧避让策略。
        if (leftAnchor == null) {
            val time = relevantObservation(obstacles, screenWidth).singleOrNull() ?: return original
            val available = (2 * (anchor - time.right - gap)).coerceAtLeast(0)
            return resize(original, screenWidth, available, minWidth, phoneCenter = anchor)
        }

        // 平板锚点起的连续日期+时间文本块是被岛替代的原生内容，不算障碍。
        // 跨在锚点上的内容位于岛覆盖区，同样不参与让位。
        var end = screenWidth - gap
        var absorbingText = true
        for (span in obstacles.sortedBy { it.left }) {
            when {
                span.left <= anchor && span.right > anchor -> {
                    if (!span.text) absorbingText = false
                }
                span.right <= anchor -> Unit
                else -> {
                    if (absorbingText && span.text) continue
                    absorbingText = false
                    end = minOf(end, span.left - gap)
                }
            }
        }
        // 平板岛左缘恒锚定在日期位，不能右移，可用空间只看右侧。
        val available = (end - anchor).coerceAtLeast(0)
        return resize(original, screenWidth, available, minWidth, phoneCenter = null)
    }

    private fun resize(
        original: Geometry,
        screenWidth: Int,
        available: Int,
        minWidth: Int,
        phoneCenter: Int?,
    ): Geometry {
        // Keep the native cutout / inter-area gap, and never cap below the pill floor
        // (island height, mirroring BigIslandMinWidthHook): 空间不足时保持原生岛形，
        // 而不是把岛压缩到趋近于零。
        val fixed = (original.width - original.left - original.right).coerceAtLeast(0)
        val floor = maxOf(fixed, minWidth)
        val width = minOf(original.width, maxOf(available, floor))
        if (width == original.width) return original
        val budget = (width - fixed).coerceAtLeast(0)
        val left = minOf(original.left, maxOf(budget / 2, budget - original.right))
        val right = minOf(original.right, budget - left)
        val x = phoneCenter?.let { it - width / 2 } ?: (screenWidth - width) / 2
        return Geometry(width, left, right, x)
    }
}
