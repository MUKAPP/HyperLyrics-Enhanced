/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed.hooks

import android.media.AudioDeviceInfo
import android.media.audiofx.LoudnessEnhancer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.juren233.hyperlyricsenhanced.BuildConfig
import io.github.proify.lyricon.amprovider.xposed.ProviderLogger
import io.github.proify.lyricon.amprovider.xposed.internal.WeakIdentityMap

internal const val APPLE_AUDIO_VARIANT_DOLBY_ATMOS = 4
internal const val APPLE_VOLUME_BALANCE_TARGET_LUFS = -16f
internal const val APPLE_ATMOS_FALLBACK_GAIN_DB = 6f
internal const val APPLE_ATMOS_UNKNOWN_PEAK_MAX_GAIN_DB = 4f
internal const val APPLE_ATMOS_MAX_INPUT_GAIN_DB = 10f
internal const val APPLE_ATMOS_LIMITER_THRESHOLD_DBFS = -1f
internal const val APPLE_ATMOS_HOT_UPGRADE_RAMP_DURATION_MS = 240L
internal const val APPLE_ATMOS_HOT_UPGRADE_RAMP_STEPS = 12

private fun atmosphereDiagnosticElapsedRealtime(): Long =
    runCatching(SystemClock::elapsedRealtime).getOrDefault(-1L)

internal enum class AppleAtmosOutputRoute {
    BUILT_IN_SPEAKER,
    NON_SPEAKER,
    UNKNOWN,
}

internal fun resolveAppleAtmosOutputRoute(deviceType: Int?): AppleAtmosOutputRoute = when (deviceType) {
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> AppleAtmosOutputRoute.BUILT_IN_SPEAKER
    null,
    AudioDeviceInfo.TYPE_UNKNOWN -> AppleAtmosOutputRoute.UNKNOWN
    else -> AppleAtmosOutputRoute.NON_SPEAKER
}

internal data class AppleAtmosGainDecision(
    val inputGainDb: Float,
    val metadataLoudness: Float?,
    val requestedInputGainDb: Float,
    val peakDbfs: Float?,
    val peakSource: String,
    val peakHeadroomDb: Float?,
    val peakLimited: Boolean,
    val fallback: Boolean,
)

internal fun resolveAppleAtmosGain(
    loudness: Float,
    peakMetadata: AppleAtmosPeakMetadata? = null,
): AppleAtmosGainDecision {
    val metadataLoudness = loudness.takeIf(Float::isFinite)
    val requestedGain = (metadataLoudness?.let {
        APPLE_VOLUME_BALANCE_TARGET_LUFS - it
    } ?: APPLE_ATMOS_FALLBACK_GAIN_DB).coerceAtLeast(0f)
    val matchingPeakMetadata = peakMetadata?.takeIf { metadata ->
        metadataLoudness == null ||
            kotlin.math.abs(metadata.loudness - metadataLoudness) <= 0.05f
    }
    val peakDbfs = matchingPeakMetadata?.truePeakDbfs?.takeIf(Float::isFinite)
        ?: matchingPeakMetadata?.samplePeakDbfs?.takeIf(Float::isFinite)
    val peakSource = when {
        matchingPeakMetadata?.truePeakDbfs?.isFinite() == true -> "true_peak"
        matchingPeakMetadata?.samplePeakDbfs?.isFinite() == true -> "sample_peak"
        else -> "missing"
    }
    val peakHeadroomDb = peakDbfs?.let { peak ->
        (APPLE_ATMOS_LIMITER_THRESHOLD_DBFS - peak).coerceAtLeast(0f)
    }
    val peakBoundedGain = peakHeadroomDb?.let { headroom ->
        minOf(requestedGain, headroom)
    } ?: minOf(requestedGain, APPLE_ATMOS_UNKNOWN_PEAK_MAX_GAIN_DB)
    val inputGain = peakBoundedGain.coerceIn(0f, APPLE_ATMOS_MAX_INPUT_GAIN_DB)
    return AppleAtmosGainDecision(
        inputGainDb = inputGain,
        metadataLoudness = metadataLoudness,
        requestedInputGainDb = requestedGain,
        peakDbfs = peakDbfs,
        peakSource = peakSource,
        peakHeadroomDb = peakHeadroomDb,
        peakLimited = inputGain + 0.001f < requestedGain,
        fallback = metadataLoudness == null,
    )
}

internal interface AppleSessionDynamicsEffect {
    val effectKind: String get() = "loudness_enhancer"

    fun setEnabled(enabled: Boolean)
    fun setInputGainDb(inputGainDb: Float)
    fun release()
    fun scheduleDiagnosticVerification(context: String) = Unit
}

internal data class AppleAtmosPcmGainDecision(
    val desiredBoostDb: Float,
    val peakCapDb: Float,
    val targetBoostDb: Float,
)

/**
 * 已播放部分的累计 PCM 电平决策（不是整曲预分析或 LUFS）：
 * 期望提升 = 普通音源有效参考电平 - 杜比累计有效电平（只提升不衰减），
 * 安全封顶 = 实测峰值距数字满幅的余量，取小者。
 * 不对短窗做直接 AGC；累计目标仍可能渐进变化，样本峰值不能保证未来峰值不超限。
 */
internal fun resolveAppleAtmosPcmGain(
    referenceFrontDbfs: Float,
    atmosIntegratedFrontDbfs: Float,
    learnedPeakDbfs: Float,
): AppleAtmosPcmGainDecision {
    if (!referenceFrontDbfs.isFinite() || !atmosIntegratedFrontDbfs.isFinite()) {
        return AppleAtmosPcmGainDecision(0f, 0f, 0f)
    }
    val desired = (referenceFrontDbfs - atmosIntegratedFrontDbfs)
        .coerceIn(0f, APPLE_ATMOS_MAX_INPUT_GAIN_DB)
    val peakCap = if (learnedPeakDbfs.isFinite()) {
        (APPLE_ATMOS_PCM_PEAK_CEILING_DBFS - learnedPeakDbfs)
            .coerceIn(0f, APPLE_ATMOS_MAX_INPUT_GAIN_DB)
    } else {
        0f
    }
    val target = minOf(desired, peakCap)
    return AppleAtmosPcmGainDecision(desired, peakCap, target)
}

/** 普通立体声音源有效电平的功率域指数滑动平均；首个有效窗口初始化。 */
internal fun mixAppleAtmosReferenceDb(
    currentDb: Float?,
    windowDb: Float,
    alpha: Float = APPLE_ATMOS_PCM_REFERENCE_EMA_ALPHA,
): Float {
    if (!windowDb.isFinite()) return currentDb?.takeIf { it.isFinite() }
        ?: APPLE_ATMOS_PCM_FALLBACK_REFERENCE_DBFS
    if (currentDb == null || !currentDb.isFinite()) return windowDb
    val weight = alpha.takeIf { it.isFinite() }?.coerceIn(0f, 1f)
        ?: APPLE_ATMOS_PCM_REFERENCE_EMA_ALPHA
    val currentPower = Math.pow(10.0, (currentDb / 10f).toDouble())
    val windowPower = Math.pow(10.0, (windowDb / 10f).toDouble())
    val mixed = (1 - weight) * currentPower + weight * windowPower
    return (10.0 * Math.log10(mixed)).toFloat()
}

// AM-ATMOS-IMMERSIVE-001：DynamicsProcessing 在 HyperOS 4 多声道 Atmos 路径上会触发系统流
// 音量衰减被整体绕过（最低音量过响，2026-09-07 真机功率历史证实）；LoudnessEnhancer 挂同一
// 会话无此问题，因此统一使用 LoudnessEnhancer，不再使用 DynamicsProcessing。
private class AndroidAppleSessionLoudnessEffect(
    audioSessionId: Int,
    initialInputGainDb: Float,
) : AppleSessionDynamicsEffect {
    private val effect = LoudnessEnhancer(audioSessionId).also { created ->
        try {
            created.setTargetGain((initialInputGainDb * 100f).toInt())
        } catch (error: Throwable) {
            runCatching(created::release)
            throw error
        }
    }

    override val effectKind = "loudness_enhancer"

    override fun setEnabled(enabled: Boolean) {
        effect.enabled = enabled
    }

    override fun setInputGainDb(inputGainDb: Float) {
        effect.setTargetGain((inputGainDb * 100f).toInt())
    }

    override fun release() {
        effect.release()
    }

    override fun scheduleDiagnosticVerification(context: String) {
        if (!BuildConfig.DEBUG) return
        val handler = Handler(Looper.getMainLooper())
        listOf(100L, 1_000L).forEach { delayMs ->
            handler.postDelayed({
                val state = runCatching {
                    "enabled=${effect.enabled},targetGainmB=${effect.targetGain}"
                }.fold(
                    onSuccess = { it },
                    onFailure = { error ->
                        "readError=${error.javaClass.simpleName}:${error.message}"
                    },
                )
                ProviderLogger.diagnostic(
                    "[AtmosVolumeDiag] event=loudness_verify," +
                        "elapsedMs=${atmosphereDiagnosticElapsedRealtime()}," +
                        "delayMs=$delayMs,$context,$state"
                )
            }, delayMs)
        }
    }
}

internal class AppleAtmosVolumeProcessor(
    private val preferenceEnabled: () -> Boolean,
    private val effectFactory: (
        audioSessionId: Int,
        channelCount: Int,
        decision: AppleAtmosGainDecision,
        initialInputGainDb: Float,
    ) -> AppleSessionDynamicsEffect = { sessionId, _, _, initialInputGainDb ->
        AndroidAppleSessionLoudnessEffect(sessionId, initialInputGainDb)
    },
    private val scheduleDelayed: (delayMs: Long, action: () -> Unit) -> Unit =
        { delayMs, action -> Handler(Looper.getMainLooper()).postDelayed(action, delayMs) },
) {
    private data class PlayerState(
        var audioSessionId: Int = 0,
        var audioVariant: Int = -1,
        var periodId: Long = 0L,
        var loudness: Float = Float.NaN,
        var peakMetadata: AppleAtmosPeakMetadata? = null,
        var channelCount: Int = 2,
        var sessionGeneration: Long = 0L,
        var sessionGenerationAtLastVariant: Long = 0L,
        var pendingHotUpgradeSessionId: Int = 0,
        var pendingHotUpgradeTrackIds: Set<Int> = emptySet(),
        var rampHotUpgradeOnNextApply: Boolean = false,
        var pcmFrontPowerSum: Double = 0.0,
        var pcmFrontFrameCount: Long = 0L,
        var pcmLearnedPeakDbfs: Float = Float.NaN,
    ) {
        fun resetPcmStats() {
            pcmFrontPowerSum = 0.0
            pcmFrontFrameCount = 0L
            pcmLearnedPeakDbfs = Float.NaN
        }

        fun integratedFrontPowerDb(): Float? =
            if (pcmFrontFrameCount > 0L) {
                (10.0 * Math.log10(pcmFrontPowerSum / pcmFrontFrameCount)).toFloat()
            } else {
                null
            }
    }

    private val playerStates = WeakIdentityMap<Any, PlayerState>()
    private val activeAudioTrackIdsBySession = mutableMapOf<Int, MutableSet<Int>>()
    private val audioTrackRoutes = mutableMapOf<Int, AppleAtmosOutputRoute>()
    private var activePlayer: Any? = null
    private var activeEffect: AppleSessionDynamicsEffect? = null
    private var activeEffectSessionId = 0
    private var activeEffectGeneration = 0L
    private var activeEffectInputGainDb = 0f
    private var failedSessionId = 0
    private var nonAtmosReferenceDbfs: Float? = null
    private var pcmGeneration = 0L
    private var pcmTargetGainDb: Float? = null

    @Synchronized
    fun onPlayerActivated(player: Any) {
        if (activePlayer !== player) {
            invalidatePcm()
            releaseActiveEffect("active_player_changed")
            activePlayer = player
            playerStates[player]?.resetPcmStats()
            failedSessionId = 0
        }
        val state = playerState(player)
        logProcessorState("player_activated", "received", player, state)
        reconcileActivePlayer("player_activated")
    }

    @Synchronized
    fun onAudioSessionId(player: Any, audioSessionId: Int) {
        val state = playerState(player)
        val previousSessionId = state.audioSessionId
        val sessionChanged = previousSessionId != audioSessionId
        state.sessionGeneration += 1L
        if (sessionChanged) {
            if (state.pendingHotUpgradeSessionId != 0 &&
                state.pendingHotUpgradeSessionId != audioSessionId
            ) {
                clearPendingHotUpgrade(state)
            }
            state.audioSessionId = audioSessionId
            state.resetPcmStats()
            if (activePlayer === player) {
                invalidatePcm()
                releaseActiveEffect("player_session_changed")
                failedSessionId = 0
            }
        }
        logProcessorState(
            event = "player_session",
            action = if (sessionChanged) "updated" else "repeated",
            player = player,
            state = state,
            extra = "previousSessionId=$previousSessionId,callbackSessionId=$audioSessionId," +
                "sessionGeneration=${state.sessionGeneration}",
        )
        if (activePlayer === player) reconcileActivePlayer("player_session")
    }

    @Synchronized
    fun onAudioVariantChanged(
        player: Any,
        audioVariant: Int,
        periodId: Long,
        loudness: Float,
        channelCount: Int,
        peakMetadata: AppleAtmosPeakMetadata? = null,
    ) {
        val state = playerState(player)
        val previousVariant = state.audioVariant
        val previousPeriodId = state.periodId
        val previousSessionId = state.audioSessionId
        val effectivePeriodId = periodId.takeIf { it != 0L } ?: previousPeriodId
        val periodChanged = previousPeriodId != effectivePeriodId
        val freshSessionSincePreviousVariant =
            state.sessionGeneration > state.sessionGenerationAtLastVariant
        val samePeriodAtmosUpgrade = !periodChanged &&
            previousVariant != APPLE_AUDIO_VARIANT_DOLBY_ATMOS &&
            audioVariant == APPLE_AUDIO_VARIANT_DOLBY_ATMOS
        state.audioVariant = audioVariant
        state.periodId = effectivePeriodId
        state.loudness = loudness
        state.peakMetadata = peakMetadata
        state.channelCount = channelCount.coerceAtLeast(1)
        state.sessionGenerationAtLastVariant = state.sessionGeneration
        if (periodChanged || audioVariant != APPLE_AUDIO_VARIANT_DOLBY_ATMOS) {
            state.resetPcmStats()
        }
        if (activePlayer === player) {
            failedSessionId = 0
            if (periodChanged || previousVariant != audioVariant) {
                invalidatePcm()
                // Numeric Session IDs can be reused. Never leave the old Period effect/ramp alive.
                if (periodChanged) releaseActiveEffect("player_period_changed")
            }
        }

        // A cross-period transition may keep the numeric Session ID, but it must have produced a
        // fresh callback after the preceding variant before that Session can be associated with the
        // new Period. This preserves stale-session isolation while allowing callback-before-format.
        val mustWaitForFreshSession = periodChanged && previousVariant != -1 &&
            !freshSessionSincePreviousVariant
        if (mustWaitForFreshSession) {
            state.audioSessionId = 0
            clearPendingHotUpgrade(state)
        }

        if (audioVariant != APPLE_AUDIO_VARIANT_DOLBY_ATMOS) {
            clearPendingHotUpgrade(state)
            if (activePlayer === player) releaseActiveEffect("non_atmos")
            logProcessorState(
                event = "player_variant",
                action = if (mustWaitForFreshSession) {
                    "non_atmos_wait_fresh_session_cross_period"
                } else {
                    "non_atmos_session_retained"
                },
                player = player,
                state = state,
                extra = "rawPeriodId=$periodId,previousVariant=$previousVariant," +
                    "previousPeriodId=$previousPeriodId,previousSessionId=$previousSessionId," +
                    "periodChanged=$periodChanged," +
                    "freshSessionSincePreviousVariant=$freshSessionSincePreviousVariant," +
                    "sessionGeneration=${state.sessionGeneration}",
            )
            return
        }

        if (mustWaitForFreshSession) {
            logProcessorState(
                event = "player_variant",
                action = "wait_fresh_session_cross_period",
                player = player,
                state = state,
                extra = "rawPeriodId=$periodId,previousVariant=$previousVariant," +
                    "previousPeriodId=$previousPeriodId,discardedSessionId=$previousSessionId," +
                    "periodChanged=true,freshSessionSincePreviousVariant=false," +
                    "sessionGeneration=${state.sessionGeneration}",
            )
            return
        }

        if (samePeriodAtmosUpgrade && state.audioSessionId > 0) {
            val oldTrackIds = activeAudioTrackIdsBySession[state.audioSessionId]
                ?.toSet()
                .orEmpty()
            state.pendingHotUpgradeSessionId = state.audioSessionId
            state.pendingHotUpgradeTrackIds = oldTrackIds
            state.rampHotUpgradeOnNextApply = true
            if (activePlayer === player) releaseActiveEffect("same_period_hot_upgrade_pending")
            logProcessorState(
                event = "player_variant",
                action = if (oldTrackIds.isEmpty()) {
                    "same_period_hot_upgrade_no_old_track"
                } else {
                    "defer_same_period_hot_upgrade_until_old_track_stops"
                },
                player = player,
                state = state,
                extra = "rawPeriodId=$periodId,previousVariant=$previousVariant," +
                    "previousPeriodId=$previousPeriodId,previousSessionId=$previousSessionId," +
                    "periodChanged=false,samePeriodAtmosUpgrade=true," +
                    "oldTrackIds=${oldTrackIds.sorted()}," +
                    "sessionGeneration=${state.sessionGeneration}",
            )
            if (oldTrackIds.isNotEmpty()) return
        } else {
            clearPendingHotUpgrade(state)
        }

        logProcessorState(
            event = "player_variant",
            action = if (samePeriodAtmosUpgrade) {
                "same_period_hot_upgrade_ready"
            } else {
                "atmos_received"
            },
            player = player,
            state = state,
            extra = "rawPeriodId=$periodId,previousVariant=$previousVariant," +
                "previousPeriodId=$previousPeriodId,previousSessionId=$previousSessionId," +
                "periodChanged=$periodChanged,samePeriodAtmosUpgrade=$samePeriodAtmosUpgrade," +
                "freshSessionSincePreviousVariant=$freshSessionSincePreviousVariant," +
                "sessionGeneration=${state.sessionGeneration}",
        )
        if (activePlayer === player) reconcileActivePlayer("player_variant")
    }

    @Synchronized
    fun onAudioTrackPlayed(
        audioSessionId: Int,
        trackIdentity: Int,
        routedDeviceType: Int?,
    ) {
        if (audioSessionId <= 0) return
        val route = resolveAppleAtmosOutputRoute(routedDeviceType)
        val added = activeAudioTrackIdsBySession.getOrPut(audioSessionId, ::mutableSetOf).add(trackIdentity)
        val routeChanged = audioTrackRoutes[trackIdentity] != route
        audioTrackRoutes[trackIdentity] = route
        if ((added || routeChanged) && activePlayer?.let(playerStates::get)?.audioSessionId == audioSessionId) {
            invalidatePcm()
        }
        val player = activePlayer
        val state = player?.let(playerStates::get)
        if (player != null && state != null) {
            logProcessorState(
                event = "audio_track_play",
                action = "tracked",
                player = player,
                state = state,
                extra = "track=$trackIdentity,trackSessionId=$audioSessionId," +
                    "route=$route,routedDeviceType=$routedDeviceType," +
                    "activeTracks=${activeAudioTrackIdsBySession[audioSessionId]?.sorted()}",
            )
            if (state.audioSessionId == audioSessionId) {
                reconcileActivePlayer("audio_track_play_route")
            }
        }
    }

    @Synchronized
    fun onAudioTrackRouteChanged(
        audioSessionId: Int,
        trackIdentity: Int,
        routedDeviceType: Int?,
    ) {
        val route = resolveAppleAtmosOutputRoute(routedDeviceType)
        val isTracked = trackIdentity in activeAudioTrackIdsBySession[audioSessionId].orEmpty()
        if (isTracked) {
            if (audioTrackRoutes[trackIdentity] != route &&
                activePlayer?.let(playerStates::get)?.audioSessionId == audioSessionId
            ) invalidatePcm()
            audioTrackRoutes[trackIdentity] = route
        }
        val player = activePlayer ?: return
        val state = playerStates[player] ?: return
        logProcessorState(
            event = "audio_route_changed",
            action = if (isTracked) "tracked_route_updated" else "ignored_inactive_track",
            player = player,
            state = state,
            extra = "track=$trackIdentity,trackSessionId=$audioSessionId," +
                "route=$route,routedDeviceType=$routedDeviceType",
        )
        if (isTracked && state.audioSessionId == audioSessionId) {
            reconcileActivePlayer("audio_route_changed")
        }
    }

    @Synchronized
    fun onAudioTrackStopped(audioSessionId: Int, trackIdentity: Int, source: String) {
        if (trackIdentity in activeAudioTrackIdsBySession[audioSessionId].orEmpty() &&
            activePlayer?.let(playerStates::get)?.audioSessionId == audioSessionId
        ) invalidatePcm()
        audioTrackRoutes.remove(trackIdentity)
        activeAudioTrackIdsBySession[audioSessionId]?.let { tracks ->
            tracks.remove(trackIdentity)
            if (tracks.isEmpty()) activeAudioTrackIdsBySession.remove(audioSessionId)
        }
        val player = activePlayer ?: return
        val state = playerStates[player] ?: return
        if (state.pendingHotUpgradeSessionId != audioSessionId ||
            trackIdentity !in state.pendingHotUpgradeTrackIds
        ) {
            logProcessorState(
                event = "audio_track_end",
                action = "not_pending_hot_upgrade_track",
                player = player,
                state = state,
                extra = "source=$source,track=$trackIdentity," +
                    "trackSessionId=$audioSessionId",
            )
            if (state.audioSessionId == audioSessionId) {
                reconcileActivePlayer("audio_track_end_route")
            }
            return
        }

        state.pendingHotUpgradeTrackIds = state.pendingHotUpgradeTrackIds - trackIdentity
        logProcessorState(
            event = "audio_track_end",
            action = if (state.pendingHotUpgradeTrackIds.isEmpty()) {
                "old_tracks_drained_apply_hot_upgrade"
            } else {
                "waiting_remaining_old_tracks"
            },
            player = player,
            state = state,
            extra = "source=$source,track=$trackIdentity,trackSessionId=$audioSessionId," +
                "remainingOldTrackIds=${state.pendingHotUpgradeTrackIds.sorted()}",
        )
        if (state.pendingHotUpgradeTrackIds.isEmpty()) {
            reconcileActivePlayer("old_audio_track_ended")
        }
    }

    @Synchronized
    fun onPreferenceChanged() {
        invalidatePcm()
        if (!runCatching(preferenceEnabled).getOrDefault(false)) nonAtmosReferenceDbfs = null
        failedSessionId = 0
        activePlayer?.let { player ->
            playerStates[player]?.let { state ->
                logProcessorState("preference_changed", "received", player, state)
            }
        }
        reconcileActivePlayer("preference_changed")
    }

    private fun invalidatePcm(resetStats: Boolean = true) {
        pcmGeneration++
        if (resetStats) {
            activePlayer?.let(playerStates::get)?.resetPcmStats()
            pcmTargetGainDb = null
            activeEffectGeneration++
        }
    }

    @Synchronized
    fun capturePcmContext(audioSessionId: Int, trackIdentity: Int): AppleAtmosPcmContext? {
        if (!runCatching(preferenceEnabled).getOrDefault(false)) return null
        val state = activePlayer?.let(playerStates::get) ?: return null
        if (audioSessionId <= 0 || state.audioVariant < 0 ||
            state.pendingHotUpgradeTrackIds.isNotEmpty()
        ) return null
        val isStateSession = audioSessionId == state.audioSessionId
        // Renderer switches can start a new AudioTrack session without a player session
        // callback (AM-ATMOS-IMMERSIVE-001 2026-09-07 真机：Atmos 3401 → ALAC 3321 无回调)。
        // 非 Atmos 期间放行本进程内已注册的真实扬声器音轨；Atmos 期间仍要求状态会话严格一致。
        if (!isStateSession && state.audioVariant == APPLE_AUDIO_VARIANT_DOLBY_ATMOS) return null
        if (trackIdentity !in activeAudioTrackIdsBySession[audioSessionId].orEmpty() ||
            audioTrackRoutes[trackIdentity] != AppleAtmosOutputRoute.BUILT_IN_SPEAKER ||
            outputRouteForSession(audioSessionId) != AppleAtmosOutputRoute.BUILT_IN_SPEAKER
        ) return null
        return AppleAtmosPcmContext(audioSessionId, trackIdentity, pcmGeneration)
    }

    @Synchronized
    fun onPcmDiscontinuity(audioSessionId: Int, trackIdentity: Int, flush: Boolean) {
        if (audioSessionId != activePlayer?.let(playerStates::get)?.audioSessionId ||
            trackIdentity !in activeAudioTrackIdsBySession[audioSessionId].orEmpty()
        ) return
        invalidatePcm(resetStats = flush)
        if (flush) reconcileActivePlayer("pcm_flush")
    }

    /** Only the active speaker Track/Period may contribute a successful-write PCM window. */
    @Synchronized
    fun onPcmWindow(window: AppleAtmosPcmWindow) {
        if (window.context != capturePcmContext(window.sessionId, window.context.trackIdentity)) return
        if (window.frameCount <= 0 || window.sampleRate !in 8_000..384_000 ||
            window.channelCount !in 1..32 || !window.effectivePeakDbfs.isFinite()
        ) return
        val player = activePlayer ?: return
        val state = playerStates[player] ?: return
        if (window.sessionId != state.audioSessionId) {
            // 已注册的异会话音轨（渲染器切换未触发会话回调）只允许学习响度参考，
            // 严禁驱动 Atmos 增益路径：效果必须只挂在状态会话上。
            if (state.audioVariant != APPLE_AUDIO_VARIANT_DOLBY_ATMOS &&
                window.channelCount <= 2 && window.frontEffectiveDbfs.isFinite() &&
                window.frontEffectiveDbfs >= APPLE_ATMOS_PCM_MIN_LEVEL_DBFS
            ) {
                updateNonAtmosReference(state, window)
            }
            return
        }
        if (state.audioVariant == APPLE_AUDIO_VARIANT_DOLBY_ATMOS) {
            updateAtmosPcmGain(player, state, window)
        } else if (window.channelCount <= 2 && window.frontEffectiveDbfs.isFinite() &&
            window.frontEffectiveDbfs >= APPLE_ATMOS_PCM_MIN_LEVEL_DBFS
        ) {
            updateNonAtmosReference(state, window)
        }
    }

    private fun updateAtmosPcmGain(
        player: Any,
        state: PlayerState,
        window: AppleAtmosPcmWindow,
    ) {
        if (state.pcmLearnedPeakDbfs.isNaN() || window.effectivePeakDbfs > state.pcmLearnedPeakDbfs) {
            state.pcmLearnedPeakDbfs = window.effectivePeakDbfs
        }
        val peakCap = (APPLE_ATMOS_PCM_PEAK_CEILING_DBFS - state.pcmLearnedPeakDbfs)
            .coerceIn(0f, APPLE_ATMOS_MAX_INPUT_GAIN_DB)
        val hasSignal = window.frontEffectiveDbfs.isFinite() &&
            window.frontEffectiveDbfs >= APPLE_ATMOS_PCM_MIN_LEVEL_DBFS
        if (hasSignal) {
            // Gain is applied per write by the meter, not retroactively using the last volume.
            val windowPower = Math.pow(10.0, (window.frontEffectiveDbfs / 10f).toDouble())
            state.pcmFrontPowerSum += windowPower * window.frameCount
            state.pcmFrontFrameCount += window.frameCount
        }
        val integratedFrontDb = state.integratedFrontPowerDb()
        val reference = nonAtmosReferenceDbfs ?: APPLE_ATMOS_PCM_FALLBACK_REFERENCE_DBFS
        val decision = if (integratedFrontDb != null) {
            resolveAppleAtmosPcmGain(reference, integratedFrontDb, state.pcmLearnedPeakDbfs)
        } else {
            AppleAtmosPcmGainDecision(activeEffectInputGainDb, peakCap, minOf(activeEffectInputGainDb, peakCap))
        }
        if (BuildConfig.DEBUG) {
            ProviderLogger.diagnostic(
                "[AtmosVolumeDiag] event=pcm_control,elapsedMs=${atmosphereDiagnosticElapsedRealtime()}," +
                    "player=${System.identityHashCode(player)},periodId=${state.periodId}," +
                    "sessionId=${state.audioSessionId},integratedFrontDb=$integratedFrontDb," +
                    "clientGainDb=${window.clientGainDb},atmosEffectiveDb=$integratedFrontDb," +
                    "referenceDb=$reference,referenceSource=" +
                    "${if (nonAtmosReferenceDbfs == null) "fallback" else "non_atmos_ema"}," +
                    "learnedPeakDbfs=${state.pcmLearnedPeakDbfs}," +
                    "windowPeakDbfs=${window.peakDbfs}," +
                    "desiredBoostDb=${decision.desiredBoostDb},peakCapDb=${decision.peakCapDb}," +
                    "targetBoostDb=${decision.targetBoostDb},commandedTargetDb=$pcmTargetGainDb," +
                    "activeEffectSessionId=$activeEffectSessionId," +
                    "activeEffectInputGainDb=$activeEffectInputGainDb"
            )
        }
        if (activeEffect == null || activeEffectSessionId != state.audioSessionId) return
        val commanded = pcmTargetGainDb ?: activeEffectInputGainDb
        if (peakCap < maxOf(commanded, activeEffectInputGainDb)) {
            // Observed peak headroom takes precedence over both hysteresis and a pending rise.
            applyGainToActiveEffect(player, state, minOf(peakCap, decision.targetBoostDb),
                "pcm_safety_down", immediate = true)
            return
        }
        if (!hasSignal) return // Silence must not request a boost or contaminate the reference.
        val target = decision.targetBoostDb
        val reason = when {
            target >= commanded + APPLE_ATMOS_PCM_UP_HYSTERESIS_DB -> "pcm_target_up"
            target <= commanded - APPLE_ATMOS_PCM_DOWN_HYSTERESIS_DB -> "pcm_target_down"
            else -> return
        }
        // Quiet intros cannot cause a single large step to the +10 dB ceiling.
        applyGainToActiveEffect(player, state, minOf(target, commanded + 1f), reason)
    }

    private fun updateNonAtmosReference(state: PlayerState, window: AppleAtmosPcmWindow) {
        val previous = nonAtmosReferenceDbfs
        // 响侧离群门限：比当前参考响出 6 dB 的窗口视为未做 Sound Check 的爆响流
        // （如杜比歌的立体声起播段），拒绝入库；低于参考的窗口正常接受，保证可向下恢复。
        if (previous != null && previous.isFinite() &&
            window.frontEffectiveDbfs > previous + APPLE_ATMOS_PCM_REFERENCE_MAX_ABOVE_DB
        ) {
            if (BuildConfig.DEBUG) {
                ProviderLogger.diagnostic(
                    "[AtmosVolumeDiag] event=pcm_reference_skip," +
                        "elapsedMs=${atmosphereDiagnosticElapsedRealtime()}," +
                        "sessionId=${state.audioSessionId},variant=${state.audioVariant}," +
                        "windowEffectiveDb=${window.frontEffectiveDbfs}," +
                        "referenceDb=$previous,limitDb=${previous + APPLE_ATMOS_PCM_REFERENCE_MAX_ABOVE_DB}"
                )
            }
            return
        }
        val updated = mixAppleAtmosReferenceDb(previous, window.frontEffectiveDbfs)
        nonAtmosReferenceDbfs = updated
        if (BuildConfig.DEBUG && previous != updated) {
            ProviderLogger.diagnostic(
                "[AtmosVolumeDiag] event=pcm_reference," +
                    "elapsedMs=${atmosphereDiagnosticElapsedRealtime()}," +
                    "sessionId=${state.audioSessionId},variant=${state.audioVariant}," +
                    "frontRmsDbfs=${window.frontRmsDbfs},clientGainDb=${window.clientGainDb}," +
                    "windowEffectiveDb=${window.frontEffectiveDbfs}," +
                    "previousDb=$previous,updatedDb=$updated"
            )
        }
    }

    private fun applyGainToActiveEffect(
        player: Any,
        state: PlayerState,
        targetGainDb: Float,
        reason: String,
        immediate: Boolean = false,
    ) {
        val effect = activeEffect ?: return
        activeEffectGeneration += 1L
        val effectGeneration = activeEffectGeneration
        pcmTargetGainDb = targetGainDb
        if (immediate) {
            runCatching { effect.setInputGainDb(targetGainDb) }
                .onSuccess {
                    activeEffectInputGainDb = targetGainDb
                    if (BuildConfig.DEBUG) {
                        effect.scheduleDiagnosticVerification("sessionId=${state.audioSessionId}," +
                            "reason=$reason,expectedInputGainDb=$targetGainDb")
                    }
                }
                .onFailure {
                    failedSessionId = state.audioSessionId
                    ProviderLogger.error("Apple Music PCM 峰值保护增益更新失败", it)
                    releaseActiveEffect("pcm_safety_update_failed")
                }
            return
        }
        logProcessorState(
            event = "processor_reconcile",
            action = "pcm_gain_update",
            player = player,
            state = state,
            extra = "reason=$reason,fromInputGainDb=$activeEffectInputGainDb," +
                "targetInputGainDb=$targetGainDb",
        )
        scheduleInputGainRamp(
            effect = effect,
            effectGeneration = effectGeneration,
            playerIdentity = System.identityHashCode(player),
            periodId = state.periodId,
            sessionId = state.audioSessionId,
            fromInputGainDb = activeEffectInputGainDb,
            targetInputGainDb = targetGainDb,
            reason = reason,
            durationMs = APPLE_ATMOS_PCM_RAMP_DURATION_MS,
            diagnosticContext = "player=${System.identityHashCode(player)}," +
                "periodId=${state.periodId},sessionId=${state.audioSessionId}," +
                "expectedInputGainDb=$targetGainDb",
        )
    }

    @Synchronized
    fun onPlayerReleased(player: Any) {
        playerStates.remove(player)
        if (activePlayer === player) {
            invalidatePcm()
            releaseActiveEffect("player_released")
            activePlayer = null
            failedSessionId = 0
        }
    }

    private fun playerState(player: Any): PlayerState =
        playerStates[player] ?: PlayerState().also { playerStates[player] = it }

    private fun reconcileActivePlayer(trigger: String) {
        val player = activePlayer
        val state = player?.let(playerStates::get)
        val enabled = runCatching(preferenceEnabled).getOrDefault(false)
        val outputRoute = state?.audioSessionId
            ?.takeIf { it > 0 }
            ?.let(::outputRouteForSession)
            ?: AppleAtmosOutputRoute.UNKNOWN
        val blockedReason = when {
            state == null -> "no_active_state"
            !enabled -> "preference_disabled"
            state.audioVariant != APPLE_AUDIO_VARIANT_DOLBY_ATMOS -> "not_atmos"
            state.audioSessionId <= 0 -> "no_session"
            state.pendingHotUpgradeTrackIds.isNotEmpty() -> "waiting_old_audio_tracks"
            outputRoute == AppleAtmosOutputRoute.UNKNOWN -> "route_unknown"
            outputRoute != AppleAtmosOutputRoute.BUILT_IN_SPEAKER -> "non_speaker_route"
            else -> null
        }
        if (blockedReason != null) {
            releaseActiveEffect("reconcile_$blockedReason")
            if (player != null && state != null) {
                logProcessorState(
                    event = "processor_reconcile",
                    action = "blocked_$blockedReason",
                    player = player,
                    state = state,
                    extra = "trigger=$trigger,preferenceEnabled=$enabled," +
                        "outputRoute=$outputRoute",
                )
            }
            return
        }
        checkNotNull(player)
        checkNotNull(state)
        val decision = resolveAppleAtmosGain(state.loudness, state.peakMetadata)
        if (activeEffect != null && activeEffectSessionId == state.audioSessionId) {
            // PCM 校准接管后，重复元数据/路由通知不得覆盖累计目标。
            if (pcmTargetGainDb == null &&
                kotlin.math.abs(decision.inputGainDb - activeEffectInputGainDb) >= 0.05f
            ) {
                activeEffectGeneration += 1L
                val effectGeneration = activeEffectGeneration
                val effect = checkNotNull(activeEffect)
                logProcessorState(
                    event = "processor_reconcile",
                    action = "updating_active_gain",
                    player = player,
                    state = state,
                    extra = "trigger=$trigger,fromInputGainDb=$activeEffectInputGainDb," +
                        "targetInputGainDb=${decision.inputGainDb}," +
                        gainDecisionLogFields(decision),
                )
                scheduleInputGainRamp(
                    effect = effect,
                    effectGeneration = effectGeneration,
                    playerIdentity = System.identityHashCode(player),
                    periodId = state.periodId,
                    sessionId = state.audioSessionId,
                    fromInputGainDb = activeEffectInputGainDb,
                    targetInputGainDb = decision.inputGainDb,
                    reason = "metadata_update",
                    diagnosticContext = "player=${System.identityHashCode(player)}," +
                        "periodId=${state.periodId},sessionId=${state.audioSessionId}," +
                        "expectedInputGainDb=${decision.inputGainDb}",
                )
                return
            }
            logProcessorState(
                event = "processor_reconcile",
                action = "already_active",
                player = player,
                state = state,
                extra = "trigger=$trigger,preferenceEnabled=$enabled," +
                    "outputRoute=$outputRoute," + gainDecisionLogFields(decision),
            )
            return
        }
        releaseActiveEffect("replace_effect")
        if (failedSessionId == state.audioSessionId) {
            logProcessorState(
                event = "processor_reconcile",
                action = "blocked_failed_session",
                player = player,
                state = state,
                extra = "trigger=$trigger,preferenceEnabled=$enabled," +
                    "outputRoute=$outputRoute",
            )
            return
        }

        val hotUpgradeRamp = state.rampHotUpgradeOnNextApply
        val initialInputGainDb = if (hotUpgradeRamp) 0f else decision.inputGainDb
        logProcessorState(
            event = "processor_reconcile",
            action = "creating_effect",
            player = player,
            state = state,
            extra = "trigger=$trigger,preferenceEnabled=$enabled," +
                "initialInputGainDb=$initialInputGainDb," +
                "hotUpgradeRamp=$hotUpgradeRamp,fallback=${decision.fallback}," +
                "outputRoute=$outputRoute," + gainDecisionLogFields(decision),
        )
        var createdEffect: AppleSessionDynamicsEffect? = null
        runCatching {
            effectFactory(
                state.audioSessionId,
                state.channelCount,
                decision,
                initialInputGainDb,
            ).also { effect ->
                createdEffect = effect
                effect.setEnabled(true)
            }
        }.onSuccess { effect ->
            activeEffect = effect
            activeEffectSessionId = state.audioSessionId
            activeEffectInputGainDb = initialInputGainDb
            activeEffectGeneration += 1L
            val effectGeneration = activeEffectGeneration
            val appliedPeriodId = state.periodId
            val appliedSessionId = state.audioSessionId
            state.pendingHotUpgradeSessionId = 0
            state.pendingHotUpgradeTrackIds = emptySet()
            state.rampHotUpgradeOnNextApply = false
            val diagnosticContext =
                "player=${System.identityHashCode(player)},periodId=$appliedPeriodId," +
                    "sessionId=$appliedSessionId,expectedInputGainDb=${decision.inputGainDb}"
            if (BuildConfig.DEBUG) runCatching {
                ProviderLogger.diagnostic(
                    "[AtmosVolumeDiag] event=dynamics_apply," +
                        "elapsedMs=${atmosphereDiagnosticElapsedRealtime()}," +
                        "player=${System.identityHashCode(player)}," +
                        "periodId=$appliedPeriodId,sessionId=$appliedSessionId," +
                        "effect=${effect.effectKind}," +
                        "loudness=${decision.metadataLoudness},channels=${state.channelCount}," +
                        "initialInputGainDb=$initialInputGainDb," +
                        "targetInputGainDb=${decision.inputGainDb}," +
                        "requestedInputGainDb=${decision.requestedInputGainDb}," +
                        "peakDbfs=${decision.peakDbfs},peakSource=${decision.peakSource}," +
                        "peakHeadroomDb=${decision.peakHeadroomDb}," +
                        "peakLimited=${decision.peakLimited}," +
                        "peakAssociation=${state.peakMetadata?.associationSource}," +
                        "hotUpgradeRamp=$hotUpgradeRamp,fallback=${decision.fallback}," +
                        "outputRoute=$outputRoute"
                )
            }
            if (hotUpgradeRamp && decision.inputGainDb > initialInputGainDb) {
                scheduleInputGainRamp(
                    effect = effect,
                    effectGeneration = effectGeneration,
                    playerIdentity = System.identityHashCode(player),
                    periodId = appliedPeriodId,
                    sessionId = appliedSessionId,
                    fromInputGainDb = initialInputGainDb,
                    targetInputGainDb = decision.inputGainDb,
                    reason = "hot_upgrade",
                    diagnosticContext = diagnosticContext,
                )
            } else if (BuildConfig.DEBUG) {
                effect.scheduleDiagnosticVerification(diagnosticContext)
            }
        }.onFailure { error ->
            createdEffect?.let { effect ->
                runCatching { effect.setEnabled(false) }
                runCatching(effect::release)
            }
            failedSessionId = state.audioSessionId
            logProcessorState(
                event = "processor_reconcile",
                action = "effect_create_failed",
                player = player,
                state = state,
                extra = "trigger=$trigger,error=${error.javaClass.simpleName}:${error.message}",
            )
            runCatching {
                ProviderLogger.error(
                    "Apple Music 当前音频会话不支持 LoudnessEnhancer 音量提升：" +
                        "session=${state.audioSessionId}",
                    error,
                )
            }
        }
    }

    private fun scheduleInputGainRamp(
        effect: AppleSessionDynamicsEffect,
        effectGeneration: Long,
        playerIdentity: Int,
        periodId: Long,
        sessionId: Int,
        fromInputGainDb: Float,
        targetInputGainDb: Float,
        reason: String,
        diagnosticContext: String,
        durationMs: Long = APPLE_ATMOS_HOT_UPGRADE_RAMP_DURATION_MS,
    ) {
        repeat(APPLE_ATMOS_HOT_UPGRADE_RAMP_STEPS) { zeroBasedStep ->
            val step = zeroBasedStep + 1
            val delayMs = durationMs * step /
                APPLE_ATMOS_HOT_UPGRADE_RAMP_STEPS
            scheduleDelayed(delayMs) {
                synchronized(this@AppleAtmosVolumeProcessor) {
                    if (activeEffect !== effect ||
                        activeEffectGeneration != effectGeneration ||
                        activeEffectSessionId != sessionId ||
                        activePlayer?.let(System::identityHashCode) != playerIdentity ||
                        activePlayer?.let(playerStates::get)?.periodId != periodId
                    ) {
                        return@synchronized
                    }
                    val progress = step.toFloat() / APPLE_ATMOS_HOT_UPGRADE_RAMP_STEPS
                    val inputGainDb = fromInputGainDb +
                        (targetInputGainDb - fromInputGainDb) * progress
                    runCatching { effect.setInputGainDb(inputGainDb) }
                        .onSuccess { activeEffectInputGainDb = inputGainDb }
                        .onFailure { error ->
                            failedSessionId = sessionId
                            ProviderLogger.error(
                                "Apple Music LoudnessEnhancer 增益渐变失败：" +
                                    "session=$sessionId, step=$step, reason=$reason",
                                error,
                            )
                            releaseActiveEffect("input_gain_ramp_failed")
                            return@synchronized
                        }
                    if (BuildConfig.DEBUG && step == APPLE_ATMOS_HOT_UPGRADE_RAMP_STEPS) {
                        runCatching {
                            ProviderLogger.diagnostic(
                                "[AtmosVolumeDiag] event=dynamics_ramp_complete," +
                                    "elapsedMs=${atmosphereDiagnosticElapsedRealtime()}," +
                                "player=$playerIdentity,periodId=$periodId," +
                                "sessionId=$sessionId,inputGainDb=$inputGainDb," +
                                "reason=$reason," +
                                "durationMs=$durationMs," +
                                    "steps=$APPLE_ATMOS_HOT_UPGRADE_RAMP_STEPS"
                            )
                        }
                        effect.scheduleDiagnosticVerification(diagnosticContext)
                    }
                }
            }
        }
    }

    private fun outputRouteForSession(audioSessionId: Int): AppleAtmosOutputRoute {
        val routes = activeAudioTrackIdsBySession[audioSessionId]
            .orEmpty()
            .mapNotNull(audioTrackRoutes::get)
        return when {
            routes.any { it == AppleAtmosOutputRoute.NON_SPEAKER } ->
                AppleAtmosOutputRoute.NON_SPEAKER
            routes.any { it == AppleAtmosOutputRoute.UNKNOWN } ->
                AppleAtmosOutputRoute.UNKNOWN
            routes.any { it == AppleAtmosOutputRoute.BUILT_IN_SPEAKER } ->
                AppleAtmosOutputRoute.BUILT_IN_SPEAKER
            else -> AppleAtmosOutputRoute.UNKNOWN
        }
    }

    private fun clearPendingHotUpgrade(state: PlayerState) {
        state.pendingHotUpgradeSessionId = 0
        state.pendingHotUpgradeTrackIds = emptySet()
        state.rampHotUpgradeOnNextApply = false
    }

    private fun gainDecisionLogFields(decision: AppleAtmosGainDecision): String =
        "inputGainDb=${decision.inputGainDb}," +
            "requestedInputGainDb=${decision.requestedInputGainDb}," +
            "peakDbfs=${decision.peakDbfs},peakSource=${decision.peakSource}," +
            "peakHeadroomDb=${decision.peakHeadroomDb}," +
            "peakLimited=${decision.peakLimited},fallback=${decision.fallback}"

    private fun releaseActiveEffect(reason: String) {
        pcmTargetGainDb = null
        val effect = activeEffect ?: return
        if (BuildConfig.DEBUG) runCatching {
            ProviderLogger.diagnostic(
                "[AtmosVolumeDiag] event=dynamics_release," +
                    "elapsedMs=${atmosphereDiagnosticElapsedRealtime()}," +
                    "player=${activePlayer?.let(System::identityHashCode)}," +
                    "sessionId=$activeEffectSessionId,effect=${effect.effectKind},reason=$reason"
            )
        }
        activeEffectGeneration += 1L
        runCatching { effect.setEnabled(false) }
        runCatching(effect::release)
        activeEffect = null
        activeEffectSessionId = 0
        activeEffectInputGainDb = 0f
        pcmTargetGainDb = null
    }

    private fun logProcessorState(
        event: String,
        action: String,
        player: Any,
        state: PlayerState,
        extra: String? = null,
    ) {
        if (!BuildConfig.DEBUG) return
        val suffix = extra?.let { ",$it" }.orEmpty()
        ProviderLogger.diagnostic(
            "[AtmosVolumeDiag] event=$event,elapsedMs=${atmosphereDiagnosticElapsedRealtime()}," +
                "action=$action,player=${System.identityHashCode(player)}," +
                "activePlayer=${activePlayer?.let(System::identityHashCode)}," +
                "variant=${state.audioVariant},periodId=${state.periodId}," +
                "sessionId=${state.audioSessionId},loudness=${state.loudness}," +
                "truePeakDbfs=${state.peakMetadata?.truePeakDbfs}," +
                "samplePeakDbfs=${state.peakMetadata?.samplePeakDbfs}," +
                "peakAssociation=${state.peakMetadata?.associationSource}," +
                "channels=${state.channelCount},sessionGeneration=${state.sessionGeneration}," +
                "sessionGenerationAtLastVariant=${state.sessionGenerationAtLastVariant}," +
                "pendingHotUpgradeSessionId=${state.pendingHotUpgradeSessionId}," +
                "pendingHotUpgradeTrackIds=${state.pendingHotUpgradeTrackIds.sorted()}," +
                "rampHotUpgradeOnNextApply=${state.rampHotUpgradeOnNextApply}," +
                "outputRoute=${outputRouteForSession(state.audioSessionId)}," +
                "activeEffectSessionId=$activeEffectSessionId," +
                "activeEffectInputGainDb=$activeEffectInputGainDb," +
                "failedSessionId=$failedSessionId$suffix"
        )
    }
}
