/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed.hooks

import kotlin.math.abs
import kotlin.math.log10

internal const val APPLE_ATMOS_PCM_WINDOW_MS = 2_000L
internal const val APPLE_ATMOS_PCM_MIN_LEVEL_DBFS = -60f
internal const val APPLE_ATMOS_PCM_PEAK_CEILING_DBFS = -3f
internal const val APPLE_ATMOS_PCM_FALLBACK_REFERENCE_DBFS = -20f
internal const val APPLE_ATMOS_PCM_UP_HYSTERESIS_DB = 1f
internal const val APPLE_ATMOS_PCM_DOWN_HYSTERESIS_DB = 0.25f
internal const val APPLE_ATMOS_PCM_REFERENCE_EMA_ALPHA = 0.15f
// AM-ATMOS-IMMERSIVE-001 2026-09-07 真机：同一首杜比歌的立体声起播流未做 Sound Check
// （clientGain=0、RMS 高达 -9 dBFS），被参考 EMA 当成无损学习后虚高近 10 dB，导致随后的
// 杜比段落校准失真。比当前参考响出该门限的窗口视为未归一化离群值，拒绝入库。
internal const val APPLE_ATMOS_PCM_REFERENCE_MAX_ABOVE_DB = 6f
internal const val APPLE_ATMOS_PCM_RAMP_DURATION_MS = 1_500L

internal data class AppleAtmosPcmContext(
    val sessionId: Int,
    val trackIdentity: Int,
    val generation: Long,
)

internal data class AppleAtmosPcmWindow(
    val context: AppleAtmosPcmContext,
    val channelCount: Int,
    val sampleRate: Int,
    val encoding: Int,
    val frameCount: Long,
    val frontRmsDbfs: Float,
    val frontEffectiveDbfs: Float,
    val peakDbfs: Float,
    val effectivePeakDbfs: Float,
) {
    val sessionId: Int get() = context.sessionId
    val windowMs: Long get() = frameCount * 1_000L / sampleRate
    val clientGainDb: Float get() = if (frontRmsDbfs.isFinite()) {
        frontEffectiveDbfs - frontRmsDbfs
    } else {
        0f
    }
}

/** Counts accepted audio frames, not wall time. Reads only: never changes the caller's PCM. */
internal class AppleAtmosPcmAccumulator(
    val context: AppleAtmosPcmContext,
    val channelCount: Int,
    val sampleRate: Int,
    val encoding: Int,
) {
    private var channel = 0
    private var frameFrontPower = 0.0
    private var frameEffectivePower = 0.0
    private var frameValid = true
    private var frameCount = 0L
    private var validFrameCount = 0L
    private var frontPower = 0.0
    private var effectivePower = 0.0
    private var peak = 0.0
    private var effectivePeak = 0.0

    init {
        require(channelCount in 1..32 && sampleRate in 8_000..384_000)
    }

    fun add(
        sampleCount: Int,
        leftGain: Float,
        rightGain: Float,
        sampleAt: (Int) -> Double,
        onWindow: (AppleAtmosPcmWindow) -> Unit,
    ) {
        val frontChannels = minOf(channelCount, 2)
        // max(L,R) is conservative for multichannel mixer volume routing.
        val peakGain = maxOf(leftGain, rightGain).toDouble()
        repeat(sampleCount) { index ->
            val sample = sampleAt(index)
            if (sample.isFinite()) {
                peak = maxOf(peak, abs(sample)) // Do not clamp float PCM: > 1 is a real overload.
                effectivePeak = maxOf(effectivePeak, abs(sample) * peakGain)
                if (channel < frontChannels) {
                    val gain = if (channel == 0) leftGain else rightGain
                    frameFrontPower += sample * sample
                    frameEffectivePower += sample * sample * gain * gain
                }
            } else {
                frameValid = false
            }
            channel++
            if (channel == channelCount) {
                frameCount++
                if (frameValid) {
                    validFrameCount++
                    frontPower += frameFrontPower / frontChannels
                    effectivePower += frameEffectivePower / frontChannels
                }
                channel = 0
                frameFrontPower = 0.0
                frameEffectivePower = 0.0
                frameValid = true
                if (frameCount >= sampleRate * APPLE_ATMOS_PCM_WINDOW_MS / 1_000L) {
                    if (validFrameCount == frameCount) {
                        onWindow(AppleAtmosPcmWindow(
                            context, channelCount, sampleRate, encoding, frameCount,
                            powerDb(frontPower / frameCount), powerDb(effectivePower / frameCount),
                            amplitudeDb(peak), amplitudeDb(effectivePeak),
                        ))
                    }
                    frameCount = 0L
                    validFrameCount = 0L
                    frontPower = 0.0
                    effectivePower = 0.0
                    peak = 0.0
                    effectivePeak = 0.0
                }
            }
        }
    }

    private fun powerDb(power: Double): Float =
        if (power > 0.0) (10.0 * log10(power)).toFloat() else Float.NEGATIVE_INFINITY

    private fun amplitudeDb(amplitude: Double): Float =
        if (amplitude > 0.0) (20.0 * log10(amplitude)).toFloat() else Float.NEGATIVE_INFINITY
}

/** AudioTrack ByteBuffer/byte[] return bytes; short[]/float[] return samples. */
internal fun acceptedAppleAtmosPcmSamples(
    result: Int, requested: Int, available: Int, bytesPerSample: Int,
): Int = if (result <= 0 || requested < 0 || available < 0 || bytesPerSample <= 0) {
    0
} else {
    minOf(result, requested, available) / bytesPerSample
}

/** Guards delegating AudioTrack.write overloads; ScopedCallbackHook always clears it in finally. */
internal class AppleAtmosPcmWriteScope<T> {
    private val current = ThreadLocal<T>()
    fun enter(create: () -> T?): Boolean {
        if (current.get() != null) return false
        val value = create() ?: return false
        current.set(value)
        return true
    }
    fun get(): T? = current.get()
    fun exit() = current.remove()
}
