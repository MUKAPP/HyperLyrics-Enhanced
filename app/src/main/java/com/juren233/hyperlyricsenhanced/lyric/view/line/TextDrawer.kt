/*
 * Copyright 2026 Proify, Tomakino, juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.juren233.hyperlyricsenhanced.lyric.view.line

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.PorterDuff
import android.graphics.Shader
import android.text.TextPaint
import androidx.core.graphics.withSave
import com.juren233.hyperlyricsenhanced.lyric.view.line.model.LyricModel
import com.juren233.hyperlyricsenhanced.lyric.view.line.model.WordModel
import kotlin.math.abs
import kotlin.math.max

internal class TextDrawer {
    private var bgColors = intArrayOf(Color.GRAY)
    private var hlColors = intArrayOf(Color.WHITE)

    var cjkLiftFactor = DEFAULT_CJK_LIFT_FACTOR
    var cjkWaveFactor = DEFAULT_CJK_WAVE_FACTOR
    var latinLiftFactor = DEFAULT_LATIN_LIFT_FACTOR
    var latinWaveFactor = DEFAULT_LATIN_WAVE_FACTOR

    var typefaceSelector: ((Char) -> Typeface)? = null

    val isRainbowBg get() = bgColors.size > 1
    val isRainbowHl get() = hlColors.size > 1

    private val fontMetrics = Paint.FontMetrics()
    private var baselineOffset = 0f

    private var cachedRainbowShader: LinearGradient? = null
    private var cachedAlphaMaskShader: LinearGradient? = null
    private var lastTotalWidth = -1f
    private var lastHighlightWidth = -1f
    private var lastColorsHash = 0

    fun setColors(background: IntArray, highlight: IntArray) {
        if (background.isNotEmpty()) bgColors = background
        if (highlight.isNotEmpty()) hlColors = highlight
    }

    fun updateMetrics(paint: TextPaint) {
        paint.getFontMetrics(fontMetrics)
        baselineOffset = -(fontMetrics.descent + fontMetrics.ascent) / 2f
    }

    fun clearShaderCache() {
        cachedRainbowShader = null
        cachedAlphaMaskShader = null
        lastTotalWidth = -1f
    }

    fun draw(
        canvas: Canvas,
        model: LyricModel,
        viewWidth: Int,
        viewHeight: Int,
        scrollX: Float,
        isOverflow: Boolean,
        highlightWidth: Float,
        useGradient: Boolean,
        scrollOnly: Boolean,
        charMotionEnabled: Boolean,
        centerIfPossible: Boolean,
        alignRight: Boolean,
        bgPaint: TextPaint,
        hlPaint: TextPaint,
        normPaint: TextPaint,
        gateSplit: GateSplitLayout? = null
    ) {
        val y = (viewHeight / 2f) + baselineOffset
        canvas.withSave {
            val xOffset = when {
                isOverflow -> scrollX
                alignRight -> viewWidth - model.width
                centerIfPossible -> (viewWidth - model.width) / 2f
                model.isAlignedRight -> viewWidth - model.width
                else -> 0f
            }
            translate(xOffset, 0f)

            if (scrollOnly) {
                forEachGateRun(model, gateSplit) { runText, unholedX, shift ->
                    canvas.withSave {
                        if (shift != 0f) translate(shift, 0f)
                        val selector = typefaceSelector
                        if (selector != null) {
                            MixedTypefaceText.drawText(canvas, runText, unholedX, y, normPaint, selector)
                        } else {
                            canvas.drawText(runText, unholedX, y, normPaint)
                        }
                    }
                }
                return@withSave
            }

            if (isRainbowBg) {
                bgPaint.shader = getOrCreateRainbowShader(model.width, bgColors)
            } else {
                bgPaint.shader = null
            }

            if (charMotionEnabled) {
                val bgClipStart = if (useGradient) 0f else highlightWidth
                drawAnimatedUnits(
                    canvas,
                    model,
                    highlightWidth,
                    bgClipStart,
                    Float.MAX_VALUE,
                    viewHeight,
                    y,
                    bgPaint,
                    gateSplit
                )
            } else if (!useGradient) {
                forEachGateRun(model, gateSplit) { runText, unholedX, shift ->
                    canvas.withSave {
                        if (shift != 0f) translate(shift, 0f)
                        canvas.clipRect(highlightWidth, 0f, Float.MAX_VALUE, viewHeight.toFloat())
                        val selector = typefaceSelector
                        if (selector != null) {
                            MixedTypefaceText.drawText(canvas, runText, unholedX, y, bgPaint, selector)
                        } else {
                            canvas.drawText(runText, unholedX, y, bgPaint)
                        }
                    }
                }
            } else {
                forEachGateRun(model, gateSplit) { runText, unholedX, shift ->
                    canvas.withSave {
                        if (shift != 0f) translate(shift, 0f)
                        val selector = typefaceSelector
                        if (selector != null) {
                            MixedTypefaceText.drawText(canvas, runText, unholedX, y, bgPaint, selector)
                        } else {
                            canvas.drawText(runText, unholedX, y, bgPaint)
                        }
                    }
                }
            }

            if (highlightWidth > 0f) {
                //val atEnd = highlightWidth >= model.width
                val atEnd = false
                if (useGradient && !atEnd) {
                    val baseShader = if (isRainbowHl) {
                        getOrCreateRainbowShader(model.width, hlColors)
                    } else {
                        LinearGradient(
                            0f, 0f, model.width, 0f,
                            hlPaint.color, hlPaint.color,
                            Shader.TileMode.CLAMP
                        )
                    }
                    val maskShader = getOrCreateAlphaMaskShader(model.width, highlightWidth)
                    hlPaint.shader = ComposeShader(baseShader, maskShader, PorterDuff.Mode.DST_IN)
                } else {
                    if (isRainbowHl) {
                        hlPaint.shader = getOrCreateRainbowShader(model.width, hlColors)
                    } else {
                        hlPaint.shader = null
                    }
                }
                if (charMotionEnabled) {
                    // 逐字单元自带按 clipStart/clipEnd 的未挖孔坐标裁剪，
                    // 外层再叠加 clipRect 会在平移后剪错位置。
                    drawAnimatedUnits(
                        canvas,
                        model,
                        highlightWidth,
                        0f,
                        highlightWidth,
                        viewHeight,
                        y,
                        hlPaint,
                        gateSplit
                    )
                } else {
                    forEachGateRun(model, gateSplit) { runText, unholedX, shift ->
                        canvas.withSave {
                            if (shift != 0f) translate(shift, 0f)
                            canvas.clipRect(0f, 0f, highlightWidth, viewHeight.toFloat())
                            val selector = typefaceSelector
                            if (selector != null) {
                                MixedTypefaceText.drawText(canvas, runText, unholedX, y, hlPaint, selector)
                            } else {
                                canvas.drawText(runText, unholedX, y, hlPaint)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 逐段绘制条带：无挖孔时只有一段（整行）；挖孔时前段在条带原点、
     * 后段在 [GateSplitLayout.runBStripStart]，平移量由 [GateSplitLayout.shiftFor]
     * 给出，段内裁剪坐标保持未挖孔坐标系。
     */
    private inline fun forEachGateRun(
        model: LyricModel,
        gateSplit: GateSplitLayout?,
        block: (runText: String, unholedX: Float, shift: Float) -> Unit
    ) {
        val text = if (model.isPlainText) model.text else model.wordText
        if (text.isEmpty()) return
        val split = gateSplit
        if (split == null || split.holeWidth <= 0f) {
            block(text, 0f, 0f)
            return
        }
        val k = split.splitCharIndex.coerceIn(0, text.length)
        if (k <= 0 || k >= text.length) {
            // 前段为空或后段为空：整行按单段处理，落在对应平移上。
            val shift = if (k <= 0) split.holeWidth else 0f
            block(text, 0f, shift)
            return
        }
        block(text.substring(0, k), 0f, 0f)
        block(text.substring(k), split.runAWidth, split.runBStripStart - split.runAWidth)
    }

    private fun drawAnimatedUnits(
        canvas: Canvas,
        model: LyricModel,
        highlightWidth: Float,
        clipStart: Float,
        clipEnd: Float,
        viewHeight: Int,
        baselineY: Float,
        paint: TextPaint,
        gateSplit: GateSplitLayout? = null
    ) {
        model.words.forEach { word ->
            val motionSpec = word.motionSpec()
            val wordShift = gateSplit?.shiftFor(word.startPosition) ?: 0f
            if (!motionSpec.animateByChar) {
                drawAnimatedTextUnit(
                    canvas = canvas,
                    text = word.text,
                    start = 0,
                    end = word.text.length,
                    drawX = word.startPosition,
                    unitStart = word.startPosition,
                    unitEnd = word.endPosition,
                    highlightWidth = highlightWidth,
                    clipStart = clipStart,
                    clipEnd = clipEnd,
                    viewHeight = viewHeight,
                    baselineY = baselineY,
                    paint = paint,
                    motionSpec = motionSpec,
                    xShift = wordShift
                )
                return@forEach
            }

            for (i in word.chars.indices) {
                val charStart = word.charStartPositions[i]
                val charEnd = word.charEndPositions[i]
                drawAnimatedTextUnit(
                    canvas = canvas,
                    text = word.text,
                    start = i,
                    end = i + 1,
                    drawX = charStart,
                    unitStart = charStart,
                    unitEnd = charEnd,
                    highlightWidth = highlightWidth,
                    clipStart = clipStart,
                    clipEnd = clipEnd,
                    viewHeight = viewHeight,
                    baselineY = baselineY,
                    paint = paint,
                    motionSpec = motionSpec,
                    xShift = wordShift
                )
            }
        }
    }

    private fun drawAnimatedTextUnit(
        canvas: Canvas,
        text: String,
        start: Int,
        end: Int,
        drawX: Float,
        unitStart: Float,
        unitEnd: Float,
        highlightWidth: Float,
        clipStart: Float,
        clipEnd: Float,
        viewHeight: Int,
        baselineY: Float,
        paint: TextPaint,
        motionSpec: MotionSpec,
        xShift: Float = 0f
    ) {
        if (unitEnd <= clipStart || unitStart >= clipEnd) return

        val visibleLeft = unitStart.coerceAtLeast(clipStart)
        val visibleRight = unitEnd.coerceAtMost(clipEnd)
        val liftY = computeUnitLift(highlightWidth, unitStart, unitEnd, paint.textSize, motionSpec)

        canvas.withSave {
            if (xShift != 0f) translate(xShift, 0f)
            clipRect(visibleLeft, 0f, visibleRight, viewHeight.toFloat())
            val selector = typefaceSelector
            if (selector != null) {
                MixedTypefaceText.drawText(
                    canvas,
                    text.substring(start, end),
                    drawX,
                    baselineY + liftY,
                    paint,
                    selector
                )
            } else {
                drawText(text, start, end, drawX, baselineY + liftY, paint)
            }
        }
    }

    private fun computeUnitLift(
        highlightWidth: Float,
        unitStart: Float,
        unitEnd: Float,
        textSize: Float,
        motionSpec: MotionSpec
    ): Float {
        val maxOffset = textSize * motionSpec.liftFactor
        val unitCenter = (unitStart + unitEnd) / 2f
        val waveLength = textSize * motionSpec.waveFactor
        val phase = ((highlightWidth - unitCenter) / waveLength).coerceIn(0f, 1f)
        return maxOffset * (1f - easeOutQuint(phase))
    }

    private fun WordModel.motionSpec(): MotionSpec {
        return if (text.any { it.isCjk() }) {
            MotionSpec(animateByChar = true, liftFactor = cjkLiftFactor, waveFactor = cjkWaveFactor)
        } else {
            MotionSpec(
                animateByChar = false,
                liftFactor = latinLiftFactor,
                waveFactor = latinWaveFactor
            )
        }
    }

    private fun Char.isCjk(): Boolean {
        val block = Character.UnicodeBlock.of(this)
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
                block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
                block == Character.UnicodeBlock.HIRAGANA ||
                block == Character.UnicodeBlock.KATAKANA ||
                block == Character.UnicodeBlock.HANGUL_SYLLABLES ||
                block == Character.UnicodeBlock.HANGUL_JAMO ||
                block == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO
    }

    private fun easeOutQuint(value: Float): Float {
        val inverse = 1f - value
        return 1f - inverse * inverse * inverse * inverse * inverse
    }

    private data class MotionSpec(
        val animateByChar: Boolean,
        val liftFactor: Float,
        val waveFactor: Float
    )

    private fun getOrCreateRainbowShader(totalWidth: Float, colors: IntArray): Shader {
        val colorsHash = colors.contentHashCode()
        if (cachedRainbowShader == null || lastTotalWidth != totalWidth || lastColorsHash != colorsHash) {
            cachedRainbowShader = LinearGradient(
                0f, 0f, totalWidth, 0f,
                colors, null, Shader.TileMode.CLAMP
            )
            lastTotalWidth = totalWidth
            lastColorsHash = colorsHash
        }
        return cachedRainbowShader!!
    }

    private fun getOrCreateAlphaMaskShader(totalWidth: Float, highlightWidth: Float): Shader {
        val edgePosition = max(highlightWidth / totalWidth, 0.9f)
        if (cachedAlphaMaskShader == null || abs(lastHighlightWidth - highlightWidth) > 0.1f) {
            cachedAlphaMaskShader = LinearGradient(
                0f, 0f, highlightWidth, 0f,
                intArrayOf(Color.BLACK, Color.BLACK, Color.TRANSPARENT),
                floatArrayOf(0f, edgePosition, 1f),
                Shader.TileMode.CLAMP
            )
            lastHighlightWidth = highlightWidth
        }
        return cachedAlphaMaskShader!!
    }

    private companion object {
        const val DEFAULT_CJK_LIFT_FACTOR = 0.055f
        const val DEFAULT_CJK_WAVE_FACTOR = 2.8f
        const val DEFAULT_LATIN_LIFT_FACTOR = 0.065f
        const val DEFAULT_LATIN_WAVE_FACTOR = 3.6f
    }
}
