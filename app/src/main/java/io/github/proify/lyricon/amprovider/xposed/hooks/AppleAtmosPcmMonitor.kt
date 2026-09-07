/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed.hooks

import android.media.AudioFormat
import android.media.AudioTrack
import com.juren233.hyperlyricsenhanced.BuildConfig
import io.github.proify.lyricon.amprovider.xposed.AppleMusicProviderRuntime
import io.github.proify.lyricon.amprovider.xposed.ProviderLogger
import io.github.proify.lyricon.amprovider.xposed.internal.WeakIdentityMap
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

// Binary signatures verified with javap -s on Android 37 android.jar (2026-09-07).
// In particular setVolume(F)I != setStereoVolume(FF)I; setVolume(FF)I does not exist.
internal fun appleAtmosPcmWriteMethods(): List<Method> {
    val intType = Int::class.javaPrimitiveType!!
    val longType = Long::class.javaPrimitiveType!!
    val signatures = listOf(
        arrayOf(ByteBuffer::class.java, intType, intType),
        arrayOf(ByteBuffer::class.java, intType, intType, longType),
        arrayOf(ByteArray::class.java, intType, intType),
        arrayOf(ByteArray::class.java, intType, intType, intType),
        arrayOf(ShortArray::class.java, intType, intType),
        arrayOf(ShortArray::class.java, intType, intType, intType),
        arrayOf(FloatArray::class.java, intType, intType, intType),
    )
    return signatures.map { AudioTrack::class.java.getDeclaredMethod("write", *it) }
}

internal fun appleAtmosPcmVolumeMethods(): List<Method> {
    val floatType = Float::class.javaPrimitiveType!!
    return listOf(
        AudioTrack::class.java.getDeclaredMethod("setVolume", floatType),
        AudioTrack::class.java.getDeclaredMethod("setStereoVolume", floatType, floatType),
    )
}

/** Functional PCM metering. No PCM mutation and no output/session effect is installed here. */
internal class AppleAtmosPcmMonitor(
    private val runtime: AppleMusicProviderRuntime,
    private val captureContext: (sessionId: Int, trackIdentity: Int) -> AppleAtmosPcmContext?,
    private val onWindow: (AppleAtmosPcmWindow) -> Unit,
    private val onDiscontinuity: (sessionId: Int, trackIdentity: Int, flush: Boolean) -> Unit,
) {
    private data class Volume(val left: Float = 1f, val right: Float = 1f)
    private data class Capture(
        val track: AudioTrack,
        val context: AppleAtmosPcmContext,
        val encoding: Int,
        val channels: Int,
        val sampleRate: Int,
        val requested: Int,
        val available: Int,
        val bytesPerSample: Int,
        val volume: Volume,
        val sampleAt: (Int) -> Double,
    )

    private val lock = Any()
    private val trackStates = WeakIdentityMap<AudioTrack, AppleAtmosPcmAccumulator>()
    private val volumes = WeakIdentityMap<AudioTrack, Volume>()
    private val writes = AppleAtmosPcmWriteScope<Capture>()
    private var installed = false

    fun installHooks() {
        if (installed) return
        installed = true
        appleAtmosPcmWriteMethods().forEach { method ->
            install(method) { firstHit ->
                runtime.hookRegistrar.installScopedHook(
                    method,
                    enter = { chain ->
                        firstHit()
                        writes.enter capture@{
                            val track = chain.thisObject as? AudioTrack ?: return@capture null
                            capture(track, chain.args.toTypedArray())
                        }
                    },
                    after = { _, result ->
                        val capture = writes.get()
                        if (capture != null && result is Int) accept(capture, result)
                    },
                    exit = writes::exit,
                )
            }
        }
        appleAtmosPcmVolumeMethods().forEach { method ->
            install(method) { firstHit ->
                runtime.hookRegistrar.installHook(method, after = { chain, result ->
                    firstHit()
                    if (result != AudioTrack.SUCCESS) return@installHook
                    val track = chain.thisObject as? AudioTrack ?: return@installHook
                    val left = chain.args.getOrNull(0) as? Float ?: return@installHook
                    val right = chain.args.getOrNull(1) as? Float ?: left
                    if (!left.isFinite() || !right.isFinite()) return@installHook
                    synchronized(lock) {
                        volumes[track] = Volume(left.coerceIn(0f, 1f), right.coerceIn(0f, 1f))
                    }
                })
            }
        }
        listOf("pause", "flush", "stop", "release").forEach { name ->
            install(AudioTrack::class.java.getDeclaredMethod(name)) { _ ->
                runtime.hookRegistrar.installHook(
                    AudioTrack::class.java.getDeclaredMethod(name),
                    // Clear before release, while its session ID is still available.
                    before = { chain ->
                        val track = chain.thisObject as? AudioTrack ?: return@installHook
                        if (name == "pause" || name == "flush") {
                            onDiscontinuity(track.audioSessionId, System.identityHashCode(track), name == "flush")
                        }
                        synchronized(lock) {
                            trackStates.remove(track)
                            if (name == "release") volumes.remove(track)
                        }
                    },
                )
            }
        }
    }

    private fun install(method: Method, hook: (() -> Unit) -> Unit) {
        // Installation/first-hit tracing is diagnostic only, not release callback bookkeeping.
        val first = if (BuildConfig.DEBUG) AtomicBoolean() else null
        runCatching {
            hook {
                if (BuildConfig.DEBUG && first?.compareAndSet(false, true) == true) {
                    ProviderLogger.diagnostic("[AtmosVolumeDiag] event=pcm_hook_hit,target=$method")
                }
            }
            if (BuildConfig.DEBUG) {
                ProviderLogger.diagnostic("[AtmosVolumeDiag] event=pcm_hook_installed,target=$method")
            }
        }.onFailure { ProviderLogger.error("Apple Music PCM Hook 安装失败: $method", it) }
    }

    private fun capture(track: AudioTrack, args: Array<Any?>): Capture? {
        val context = captureContext(track.audioSessionId, System.identityHashCode(track)) ?: return null
        val channels = track.channelCount.takeIf { it in 1..32 } ?: return null
        val rate = track.sampleRate.takeIf { it in 8_000..384_000 } ?: return null
        val encoding = track.audioFormat
        val width = when (encoding) {
            AudioFormat.ENCODING_PCM_8BIT -> 1
            AudioFormat.ENCODING_PCM_16BIT -> 2
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
            AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_32BIT -> 4
            else -> return null // Never interpret compressed/offload/IEC61937 data as PCM.
        }
        val source = args.getOrNull(0)
        val volume = synchronized(lock) { volumes[track] ?: Volume() }
        var unitsPerSample = width
        val requested: Int
        val available: Int
        val sampleAt: (Int) -> Double
        if (source is ByteBuffer) {
            val buffer = source.duplicate().order(ByteOrder.nativeOrder())
            requested = args.getOrNull(1) as? Int ?: return null
            available = buffer.remaining()
            sampleAt = { index -> readAppleAtmosPcmSample(buffer, buffer.position() + index * width, encoding) }
        } else {
            val offset = args.getOrNull(1) as? Int ?: return null
            requested = args.getOrNull(2) as? Int ?: return null
            when (source) {
                is ByteArray -> {
                    if (offset !in 0..source.size) return null
                    available = source.size - offset
                    val buffer = ByteBuffer.wrap(source).order(ByteOrder.nativeOrder())
                    sampleAt = { index -> readAppleAtmosPcmSample(buffer, offset + index * width, encoding) }
                }
                is ShortArray -> {
                    if (encoding != AudioFormat.ENCODING_PCM_16BIT || offset !in 0..source.size) return null
                    unitsPerSample = 1
                    available = source.size - offset
                    sampleAt = { index -> source[offset + index] / 32768.0 }
                }
                is FloatArray -> {
                    if (encoding != AudioFormat.ENCODING_PCM_FLOAT || offset !in 0..source.size) return null
                    unitsPerSample = 1
                    available = source.size - offset
                    sampleAt = { index -> source[offset + index].toDouble() }
                }
                else -> return null
            }
        }
        return Capture(track, context, encoding, channels, rate, requested, available,
            unitsPerSample, volume, sampleAt)
    }

    private fun accept(capture: Capture, result: Int) {
        val count = acceptedAppleAtmosPcmSamples(result, capture.requested,
            capture.available, capture.bytesPerSample)
        if (count <= 0) return
        if (captureContext(capture.context.sessionId, capture.context.trackIdentity) != capture.context) return
        var reports: MutableList<AppleAtmosPcmWindow>? = null
        synchronized(lock) {
            val old = trackStates[capture.track]
            val state = old?.takeIf {
                it.context == capture.context && it.channelCount == capture.channels &&
                    it.sampleRate == capture.sampleRate && it.encoding == capture.encoding
            } ?: AppleAtmosPcmAccumulator(capture.context, capture.channels,
                capture.sampleRate, capture.encoding).also { trackStates[capture.track] = it }
            state.add(count, capture.volume.left, capture.volume.right, capture.sampleAt) { report ->
                (reports ?: mutableListOf<AppleAtmosPcmWindow>().also { reports = it }).add(report)
            }
        }
        // Never call back into the processor while holding the monitor lock.
        reports?.forEach { report ->
            if (BuildConfig.DEBUG) {
                ProviderLogger.diagnostic("[AtmosVolumeDiag] event=pcm_window," +
                    "track=${report.context.trackIdentity},generation=${report.context.generation}," +
                    "sessionId=${report.sessionId},encoding=${report.encoding},channels=${report.channelCount}," +
                    "sampleRate=${report.sampleRate},windowMs=${report.windowMs},frameCount=${report.frameCount}," +
                    "frontRmsDbfs=${report.frontRmsDbfs},frontEffectiveDbfs=${report.frontEffectiveDbfs}," +
                    "peakDbfs=${report.peakDbfs},effectivePeakDbfs=${report.effectivePeakDbfs}," +
                    "clientGainDb=${report.clientGainDb}")
            }
            onWindow(report)
        }
    }
}

internal fun readAppleAtmosPcmSample(buffer: ByteBuffer, offset: Int, encoding: Int): Double =
    when (encoding) {
        AudioFormat.ENCODING_PCM_8BIT -> ((buffer.get(offset).toInt() and 0xff) - 128) / 128.0
        AudioFormat.ENCODING_PCM_16BIT -> buffer.getShort(offset) / 32768.0
        AudioFormat.ENCODING_PCM_FLOAT -> buffer.getFloat(offset).toDouble()
        AudioFormat.ENCODING_PCM_32BIT -> buffer.getInt(offset) / 2147483648.0
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
            val littleEndian = buffer.order() == ByteOrder.LITTLE_ENDIAN
            val low = buffer.get(offset + if (littleEndian) 0 else 2).toInt() and 0xff
            val middle = buffer.get(offset + 1).toInt() and 0xff
            val high = buffer.get(offset + if (littleEndian) 2 else 0).toInt()
            (low or (middle shl 8) or (high shl 16)) / 8388608.0
        }
        else -> Double.NaN
    }
