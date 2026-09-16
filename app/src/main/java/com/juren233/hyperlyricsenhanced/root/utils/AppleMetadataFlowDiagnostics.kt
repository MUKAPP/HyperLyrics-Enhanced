/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package com.juren233.hyperlyricsenhanced.root.utils

import android.os.SystemClock
import com.juren233.hyperlyricsenhanced.BuildConfig
import com.juren233.hyperlyricsenhanced.common.lyric.LyricMetadataKeys
import com.juren233.hyperlyricsenhanced.lyric.model.Song as LocalSong
import io.github.proify.lyricon.lyric.model.Song as ProviderSong

/** Metadata only: no lyric text, payload serialization, extra hooks, or release tracing. */
internal object AppleMetadataFlowDiagnostics {
    private const val TAG = "AppleMetadataFlow"
    private val history by lazy { AppleMetadataTraceBuffer() }

    fun record(stage: String, changedOnly: Boolean = false, details: () -> String) {
        if (!BuildConfig.DEBUG) return
        runCatching {
            history.record(now(), stage, details(), changedOnly)?.let(::emit)
        }
    }

    /** Replays bounded history so a busy logcat does not erase the song-start evidence. */
    fun checkpoint(stage: String, details: () -> String) {
        if (!BuildConfig.DEBUG) return
        runCatching { history.checkpoint(now(), stage, details).forEach(::emit) }
    }

    fun local(song: LocalSong?): String = if (song == null) "null" else snapshot(
        song.id, song.name, song.artist, song.lyrics.orEmpty().size,
        song.metadata?.getString(LyricMetadataKeys.APPLE_ORIGINAL_ARTIST),
        song.metadata?.getString(LyricMetadataKeys.APPLE_MISSING_LYRICS_SOURCE),
        song.metadata?.getString(LyricMetadataKeys.APPLE_MISSING_LYRICS_SUPPLEMENT),
        System.identityHashCode(song),
    )

    fun provider(song: ProviderSong?): String = if (song == null) "null" else snapshot(
        song.id, song.name, song.artist, song.lyrics.orEmpty().size,
        song.metadata?.getString(LyricMetadataKeys.APPLE_ORIGINAL_ARTIST),
        song.metadata?.getString(LyricMetadataKeys.APPLE_MISSING_LYRICS_SOURCE),
        song.metadata?.getString(LyricMetadataKeys.APPLE_MISSING_LYRICS_SUPPLEMENT),
        System.identityHashCode(song),
    )

    fun text(value: Any?): String = value?.toString().orEmpty()
        .replace('\n', ' ').replace('\r', ' ').take(64)

    private fun snapshot(
        id: String?, title: String?, artist: String?, lines: Int,
        originalArtist: String?, source: String?, supplement: String?, identity: Int,
    ): String = "{id=${text(id)},title=${text(title)},artist=${text(artist)}," +
        "originalArtist=${text(originalArtist)},source=${text(source)}," +
        "supplement=${text(supplement)},lines=$lines,obj=$identity}"

    private fun now(): Long = SystemClock.elapsedRealtime()

    private fun emit(message: String) {
        // INFO survives the normal Debug export level; failures must not affect playback.
        runCatching { HookLogger.i(TAG, "[AM_META_FLOW] build=${BuildConfig.VERSION_CODE} $message") }
    }
}

/** Pure bounded recorder; sequence/time are retained when a checkpoint replays old events. */
internal class AppleMetadataTraceBuffer(
    private val capacity: Int = 64,
    private val checkpointIntervalMs: Long = 30_000L,
) {
    init {
        require(capacity > 0)
        require(checkpointIntervalMs > 0)
    }

    private val events = ArrayDeque<String>()
    private val lastChangedDetails = linkedMapOf<String, String>()
    private var sequence = 0L
    private var lastCheckpointAt: Long? = null

    @Synchronized
    fun record(now: Long, stage: String, details: String, changedOnly: Boolean = false): String? {
        val boundedStage = clean(stage, 64)
        val boundedDetails = clean(details, 1_000)
        if (changedOnly) {
            if (lastChangedDetails[boundedStage] == boundedDetails) return null
            lastChangedDetails[boundedStage] = boundedDetails
            if (lastChangedDetails.size > capacity) {
                lastChangedDetails.remove(lastChangedDetails.keys.first())
            }
        }
        val event = "seq=${++sequence} elapsedMs=$now stage=$boundedStage $boundedDetails"
        events.addLast(event)
        while (events.size > capacity) events.removeFirst()
        return event
    }

    @Synchronized
    fun checkpoint(now: Long, stage: String, details: () -> String): List<String> {
        val previous = lastCheckpointAt
        if (previous != null && now - previous < checkpointIntervalMs) return emptyList()
        lastCheckpointAt = now
        return listOf(
            "checkpoint elapsedMs=$now stage=${clean(stage, 64)} ${clean(details(), 1_000)}"
        ) + events.map { "replay $it" }
    }

    private fun clean(value: String, limit: Int): String = value
        .replace('\n', ' ').replace('\r', ' ').take(limit)
}
