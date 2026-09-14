/* Copyright 2026 juren233 */
package com.juren233.hyperlyricsenhanced.lyric.view.line

/**
 * Gates marquee start for a plain-text lyric line.
 *
 * The overflow test must be part of the decision, not a check performed after the caller
 * has already latched "started". With the dynamic island width the first request can run
 * before the view has its final measured width; latching there made truncated text stay
 * clipped forever because later position ticks saw `scrollStarted == true` and returned.
 * Returning false while the text does not overflow keeps the line retryable.
 */
internal object MarqueeStartPolicy {
    fun canStart(
        playbackActive: Boolean,
        isStaticPreview: Boolean,
        isPlainText: Boolean,
        scrollUnlocked: Boolean,
        scrollStarted: Boolean,
        isOverflow: Boolean,
    ): Boolean = playbackActive &&
        !isStaticPreview &&
        isPlainText &&
        scrollUnlocked &&
        !scrollStarted &&
        isOverflow
}
