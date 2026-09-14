/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.juren233.hyperlyricsenhanced.lyric.view.line

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import com.juren233.hyperlyricsenhanced.BuildConfig
import com.juren233.hyperlyricsenhanced.lyric.view.line.model.LyricModel
import com.juren233.hyperlyricsenhanced.root.utils.HookLogger

/** Debug-only history of the pixels' inputs before a content switch, not just wrapper bounds. */
internal class LyricSwitchTrace(private val view: View) {
    private val frames = ArrayDeque<String>(32)
    private val location = IntArray(2)

    fun record(
        event: String,
        model: LyricModel,
        availableWidth: Int,
        scrollOffset: Float,
        center: Boolean,
        alignRight: Boolean,
        paused: Boolean,
        frameRunning: Boolean,
        dumpHistory: Boolean,
    ) {
        if (!BuildConfig.DEBUG) return
        val parent = view.parent as? ViewGroup
        view.getLocationInWindow(location)
        // Reuse the renderer's offset rules. currentTextStartX() intentionally returns zero
        // for overflow (promotion geometry), so it cannot diagnose an old row's scroll reset.
        val drawX = resolveShadowTextStartX(
            model.width, availableWidth.toFloat(), scrollOffset, model.isPlainText,
            model.isAlignedRight, center, alignRight
        )
        val sample = "t=${SystemClock.uptimeMillis()} model=${System.identityHashCode(model).toString(16)} " +
            "text=${model.text.take(16).replace('\n', ' ')} begin=${model.begin} " +
            "right=${model.isAlignedRight} center=$center forcedRight=$alignRight " +
            "lineW=${model.width} w=${view.width} mw=${view.measuredWidth} available=$availableWidth " +
            "scroll=$scrollOffset drawX=$drawX windowX=${location[0]} " +
            "tx=${view.translationX} alpha=${view.alpha} scale=${view.scaleX} " +
            "parentTx=${parent?.translationX} parentAlpha=${parent?.alpha} " +
            "paused=$paused frameRunning=$frameRunning shown=${view.isShown}"
        if (event == "draw") {
            if (frames.size == 32) frames.removeFirst()
            frames.addLast(sample)
            return
        }
        val identity = "view=${System.identityHashCode(view).toString(16)} " +
            "group=${parent?.let { System.identityHashCode(it).toString(16) }} " +
            "slot=${parent?.tag} row=${parent?.indexOfChild(view)}"
        if (dumpHistory) {
            frames.forEach { HookLogger.d("SwitchPixels", "$identity before=$event $it") }
            frames.clear()
        }
        HookLogger.d("SwitchPixels", "$identity event=$event $sample")
    }
}
