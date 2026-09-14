/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.juren233.hyperlyricsenhanced.root.island

import com.juren233.hyperlyricsenhanced.lyric.model.RichLyricLine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandSlotBoundStatePolicyTest {

    private fun textLine(text: String?) = RichLyricLine(text = text, words = emptyList())

    private fun wordSyncLine() = RichLyricLine(
        text = "",
        words = listOf(com.juren233.hyperlyricsenhanced.lyric.model.LyricWord(text = "a")),
    )

    @Test
    fun `null target never counts as lost`() {
        assertFalse(isLyricViewContentLostState(null, null, 0f))
        assertFalse(isLyricViewContentLostState(null, textLine("old"), 0f))
    }

    @Test
    fun `target with text and unbound view counts as lost`() {
        assertTrue(isLyricViewContentLostState(textLine("新歌词"), null, 0f))
    }

    @Test
    fun `target with text and zero main width counts as lost`() {
        assertTrue(isLyricViewContentLostState(textLine("新歌词"), textLine("新歌词"), 0f))
    }

    @Test
    fun `healthy bound view is not lost`() {
        assertFalse(isLyricViewContentLostState(textLine("新歌词"), textLine("新歌词"), 120f))
    }

    @Test
    fun `stale bound line with zero width counts as lost`() {
        assertTrue(isLyricViewContentLostState(textLine("新歌词"), textLine("旧歌词"), 0f))
    }

    @Test
    fun `word sync target without text but with words uses words as main content`() {
        assertTrue(isLyricViewContentLostState(wordSyncLine(), null, 0f))
        assertFalse(isLyricViewContentLostState(wordSyncLine(), wordSyncLine(), 88f))
    }

    @Test
    fun `main-less target never counts as lost even at zero width`() {
        // 纯翻译/间奏点等主行为空的行：主行宽度 0 属正常
        val mainLess = RichLyricLine(
            text = null,
            words = emptyList(),
            secondary = "第二行内容",
        )
        assertFalse(isLyricViewContentLostState(mainLess, null, 0f))
        assertFalse(isLyricViewContentLostState(mainLess, mainLess, 0f))
    }

    @Test
    fun `blank text target behaves like main-less`() {
        assertFalse(isLyricViewContentLostState(textLine(""), textLine(""), 0f))
    }
}
