/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed.hooks

import android.media.AudioFormat
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10

class AppleAtmosPcmAnalysisTest {
    private val context = AppleAtmosPcmContext(10, 20, 1)
    private val windows = mutableListOf<AppleAtmosPcmWindow>()
    private fun meter(channels: Int = 2) = AppleAtmosPcmAccumulator(
        context, channels, 8_000, AudioFormat.ENCODING_PCM_FLOAT,
    )

    @Test
    fun `meter waits for actual audio duration and carries partial frames across writes`() {
        val meter = meter()
        meter.add(1, 1f, 1f, { 0.5 }, windows::add)
        meter.add(31_998, 1f, 1f, { 0.5 }, windows::add)
        assertTrue(windows.isEmpty())
        meter.add(1, 1f, 1f, { 0.5 }, windows::add)
        assertEquals(16_000L, windows.single().frameCount)
        assertEquals(2_000L, windows.single().windowMs)
        assertEquals(-6.0206f, windows.single().frontRmsDbfs, 0.0001f)
    }

    @Test
    fun `front channel energy is independent of surround count but peaks include every channel`() {
        meter(12).add(16_000 * 12, 1f, 1f, { if (it % 12 < 2) 0.1 else 0.9 }, windows::add)
        assertEquals(-20f, windows.single().frontRmsDbfs, 0.001f)
        assertEquals((20 * log10(0.9)).toFloat(), windows.single().peakDbfs, 0.001f)
    }

    @Test
    fun `per-write Sound Check changes are integrated in power domain`() {
        val meter = meter()
        meter.add(16_000, 1f, 1f, { 0.5 }, windows::add)
        meter.add(16_000, 0.5f, 0.5f, { 0.5 }, windows::add)
        assertEquals((10 * log10((0.25 + 0.0625) / 2)).toFloat(), windows.single().frontEffectiveDbfs, 0.001f)
        assertEquals(-6.0206f, windows.single().effectivePeakDbfs, 0.001f)
    }

    @Test
    fun `asymmetric channel volume uses power not geometric average`() {
        meter().add(32_000, 1f, 0f, { 0.5 }, windows::add)
        assertEquals((10 * log10(0.125)).toFloat(), windows.single().frontEffectiveDbfs, 0.001f)
    }

    @Test
    fun `silence and client mute do not become a finite target level`() {
        meter().add(32_000, 1f, 1f, { 0.0 }, windows::add)
        assertEquals(Float.NEGATIVE_INFINITY, windows.single().frontRmsDbfs)
        windows.clear()
        meter().add(32_000, 0f, 0f, { 0.5 }, windows::add)
        assertEquals(Float.NEGATIVE_INFINITY, windows.single().frontEffectiveDbfs)
        assertEquals(Float.NEGATIVE_INFINITY, windows.single().effectivePeakDbfs)
    }

    @Test
    fun `invalid frame rejects window and next clean window recovers`() {
        val meter = meter()
        meter.add(32_000, 1f, 1f, { if (it == 0) Double.NaN else 0.5 }, windows::add)
        assertTrue(windows.isEmpty())
        meter.add(32_000, 1f, 1f, { 0.5 }, windows::add)
        assertEquals(-6.0206f, windows.single().frontRmsDbfs, 0.001f)
    }

    @Test
    fun `floating point overload is measured rather than clamped to full scale`() {
        meter(1).add(16_000, 1f, 1f, { 2.0 }, windows::add)
        assertEquals(6.0206f, windows.single().peakDbfs, 0.001f)
        assertEquals(0f, resolveAppleAtmosPcmGain(-20f, -30f, windows.single().peakDbfs).targetBoostDb, 0f)
    }

    @Test
    fun `large writes produce independent fixed audio windows`() {
        meter().add(64_000, 1f, 1f, { if (it < 32_000) 0.5 else 0.25 }, windows::add)
        assertEquals(2, windows.size)
        assertEquals(-6.0206f, windows[0].peakDbfs, 0.001f)
        assertEquals(-12.0412f, windows[1].peakDbfs, 0.001f)
    }

    @Test
    fun `short zero and failed writes count only accepted units`() {
        assertEquals(2, acceptedAppleAtmosPcmSamples(8, 100, 100, 4))
        assertEquals(8, acceptedAppleAtmosPcmSamples(8, 100, 100, 1))
        assertEquals(2, acceptedAppleAtmosPcmSamples(100, 8, 100, 4))
        assertEquals(2, acceptedAppleAtmosPcmSamples(100, 100, 8, 4))
        assertEquals(0, acceptedAppleAtmosPcmSamples(-6, 100, 100, 4))
        assertEquals(0, acceptedAppleAtmosPcmSamples(0, 100, 100, 4))
        assertEquals(0, acceptedAppleAtmosPcmSamples(100, -1, 100, 4))
    }

    @Test
    fun `nested framework overload delegates count once and exceptions leave no stale scope`() {
        val scope = AppleAtmosPcmWriteScope<Int>()
        assertTrue(scope.enter { 10 })
        assertFalse(scope.enter { error("Nested overload must not capture again") })
        assertEquals(10, scope.get())
        try {
            throw IllegalStateException("write failed")
        } catch (_: IllegalStateException) {
            // Mirrors ScopedCallbackHook's existing finally contract.
        } finally {
            scope.exit()
        }
        assertNull(scope.get())
        assertTrue(scope.enter { 20 })
        assertEquals(20, scope.get())
        scope.exit()
    }

    @Test
    fun `PCM decoding handles signed depths endian and nonzero read position without mutation`() {
        for (order in listOf(ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN)) {
            val buffer = ByteBuffer.allocate(16).order(order)
            buffer.position(3)
            buffer.putShort(3, Short.MIN_VALUE)
            assertEquals(-1.0, readAppleAtmosPcmSample(buffer, 3, AudioFormat.ENCODING_PCM_16BIT), 0.0)
            buffer.putInt(3, Int.MIN_VALUE)
            assertEquals(-1.0, readAppleAtmosPcmSample(buffer, 3, AudioFormat.ENCODING_PCM_32BIT), 0.0)
            buffer.putFloat(3, 1.5f)
            assertEquals(1.5, readAppleAtmosPcmSample(buffer, 3, AudioFormat.ENCODING_PCM_FLOAT), 0.0)
            buffer.put(3, if (order == ByteOrder.LITTLE_ENDIAN) 0 else 0x80.toByte())
            buffer.put(4, 0)
            buffer.put(5, if (order == ByteOrder.LITTLE_ENDIAN) 0x80.toByte() else 0)
            assertEquals(-1.0, readAppleAtmosPcmSample(buffer, 3, AudioFormat.ENCODING_PCM_24BIT_PACKED), 0.0)
            buffer.put(3, 0x80.toByte())
            assertEquals(0.0, readAppleAtmosPcmSample(buffer, 3, AudioFormat.ENCODING_PCM_8BIT), 0.0)
            assertEquals(3, buffer.position())
            assertEquals(16, buffer.limit())
            assertEquals(order, buffer.order())
        }
    }

    @Test
    fun `verified framework hook signatures reject the nonexistent stereo setVolume alias`() {
        val volume = appleAtmosPcmVolumeMethods()
        assertEquals(listOf("setVolume", "setStereoVolume"), volume.map { it.name })
        assertEquals(listOf(1, 2), volume.map { it.parameterCount })
        assertTrue(volume.all { it.returnType == Int::class.javaPrimitiveType })
        assertFalse(volume.any { it.name == "setVolume" && it.parameterCount == 2 })
        val writes = appleAtmosPcmWriteMethods()
        assertEquals(7, writes.size)
        assertEquals(7, writes.toSet().size)
        assertTrue(writes.all { it.name == "write" && it.returnType == Int::class.javaPrimitiveType })
    }

    @Test
    fun `gain decisions fail closed for invalid signal and cap by observed PCM peak`() {
        assertEquals(7f, resolveAppleAtmosPcmGain(-20f, -27f, -15f).targetBoostDb, 0f)
        assertEquals(3f, resolveAppleAtmosPcmGain(-20f, -27f, -6f).targetBoostDb, 0f)
        assertEquals(10f, resolveAppleAtmosPcmGain(-20f, -40f, -30f).targetBoostDb, 0f)
        assertEquals(0f, resolveAppleAtmosPcmGain(-20f, -17f, -6f).targetBoostDb, 0f)
        assertEquals(0f, resolveAppleAtmosPcmGain(-20f, Float.NaN, -6f).targetBoostDb, 0f)
        assertEquals(0f, resolveAppleAtmosPcmGain(-20f, Float.NEGATIVE_INFINITY, -6f).targetBoostDb, 0f)
        assertEquals(0f, resolveAppleAtmosPcmGain(-20f, -27f, Float.NaN).targetBoostDb, 0f)
    }

    @Test
    fun `reference averages power and invalid input does not poison it`() {
        assertEquals(-20f, mixAppleAtmosReferenceDb(null, -20f), 0f)
        assertEquals((10 * log10(0.00505)).toFloat(), mixAppleAtmosReferenceDb(-20f, -40f, 0.5f), 0.001f)
        assertEquals(-20f, mixAppleAtmosReferenceDb(-20f, Float.NaN), 0f)
        assertEquals(-20f, mixAppleAtmosReferenceDb(Float.NaN, -20f), 0f)
    }
}
