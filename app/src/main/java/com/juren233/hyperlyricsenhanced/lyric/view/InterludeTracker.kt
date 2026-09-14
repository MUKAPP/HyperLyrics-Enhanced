/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.juren233.hyperlyricsenhanced.lyric.view

internal class InterludeTracker(
    lines: List<TimedLine> = emptyList(),
    private val minGapMs: Long = 7_000L
) {

    private val firstLyric = lines.firstOrNull { !it.isTitleLine() }

    /**
     * [advanceMs] 是"提前预览下一句"的提前量：开启后间奏指示器接管预览原本
     * 占用的时段，窗口整体前移——从上一句 end 前 advance 毫秒开始，到下一句
     * begin 前 advance 毫秒结束（之后由预览提前切句）。传 null 表示功能关闭，
     * 保持原始边界；前奏没有当前句可移交，始终不前移。
     */
    fun evaluate(
        posMs: Long,
        lineAtOrBefore: TimedLine?,
        current: Interlude?,
        advanceMs: Long? = null
    ): Interlude? {
        current?.let { if (posMs in it.start until it.end) return it }

        val previous = lineAtOrBefore?.takeUnless { it.isTitleLine() }
        val advance = if (previous != null) advanceMs else null
        val start = when {
            previous == null -> 0L
            advance == null -> previous.end + 1L
            else -> previous.end - advance
        }
        if (previous != null && posMs < start) return null

        val next = lineAtOrBefore?.nextLyric() ?: firstLyric ?: return null

        val gap = next.begin - (previous?.end ?: 0L)
        if (gap < minGapMs) return null
        val end = if (advance == null) next.begin else next.begin - advance
        if (posMs !in start until end) return null

        return Interlude(
            start = start,
            end = end,
            type = if (previous == null) Type.INTRO else Type.INTERLUDE,
            next = next
        )
    }

    private fun TimedLine.nextLyric(): TimedLine? {
        var candidate = next
        while (candidate?.isTitleLine() == true) candidate = candidate.next
        return candidate
    }

    private fun TimedLine.isTitleLine(): Boolean =
        metadata?.getBoolean(SongPreprocessor.KEY_TITLE_LINE) == true

    enum class Type {
        INTRO,
        INTERLUDE
    }

    data class Interlude(
        val start: Long,
        val end: Long,
        val type: Type,
        val next: TimedLine
    ) {
        val duration get() = end - start
    }
}
