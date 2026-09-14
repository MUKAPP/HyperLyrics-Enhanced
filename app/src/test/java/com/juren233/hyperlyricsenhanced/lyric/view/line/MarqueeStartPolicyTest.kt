/* Copyright 2026 juren233 */
package com.juren233.hyperlyricsenhanced.lyric.view.line

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarqueeStartPolicyTest {
    private fun canStart(
        playbackActive: Boolean = true,
        isStaticPreview: Boolean = false,
        isPlainText: Boolean = true,
        scrollUnlocked: Boolean = true,
        scrollStarted: Boolean = false,
        isOverflow: Boolean = true,
    ): Boolean = MarqueeStartPolicy.canStart(
        playbackActive = playbackActive,
        isStaticPreview = isStaticPreview,
        isPlainText = isPlainText,
        scrollUnlocked = scrollUnlocked,
        scrollStarted = scrollStarted,
        isOverflow = isOverflow,
    )

    @Test
    fun `overflowing unlocked playing line starts`() {
        assertTrue(canStart())
    }

    @Test
    fun `line that does not overflow stays retryable instead of latching`() {
        // 首次请求早于最终宽度时不得锁死；宽度落定后同一条件必须能再次通过。
        assertFalse(canStart(isOverflow = false))
        assertTrue(canStart(isOverflow = true))
    }

    @Test
    fun `already started marquee does not restart`() {
        assertFalse(canStart(scrollStarted = true))
    }

    @Test
    fun `paused playback never scrolls`() {
        assertFalse(canStart(playbackActive = false))
    }

    @Test
    fun `locked or static preview or non plain text never scrolls`() {
        assertFalse(canStart(scrollUnlocked = false))
        assertFalse(canStart(isStaticPreview = true))
        assertFalse(canStart(isPlainText = false))
    }
}
