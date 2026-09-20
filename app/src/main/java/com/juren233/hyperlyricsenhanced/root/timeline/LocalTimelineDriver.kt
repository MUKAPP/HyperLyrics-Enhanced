/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.juren233.hyperlyricsenhanced.root.timeline

import android.os.Handler
import android.os.Looper
import com.juren233.hyperlyricsenhanced.BuildConfig
import com.juren233.hyperlyricsenhanced.lyric.model.Song
import com.juren233.hyperlyricsenhanced.lyric.model.RichLyricLine
import com.juren233.hyperlyricsenhanced.lyric.source.LyricSink
import com.juren233.hyperlyricsenhanced.lyric.source.SourceSelectionAwareSink
import com.juren233.hyperlyricsenhanced.lyric.source.TimelineContent
import com.juren233.hyperlyricsenhanced.root.LyriconDataBridge
import com.juren233.hyperlyricsenhanced.root.SystemUiEnhancementGate
import com.juren233.hyperlyricsenhanced.root.utils.HookLogger
import com.juren233.hyperlyricsenhanced.timeline.model.TrackIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * SystemUI 中唯一的歌词时间轴拥有者。
 *
 * 歌词源只能通过 [onTimelineContent] 提交一份完整、不可变的歌词内容；曲目身份校验、
 * 内容替换、播放状态、位置外推、逐行滚动与渲染清理由本类统一完成。来源回调里的位置、
 * seek、单行歌词和纯文本都不会再进入渲染管线，因此不存在两套时钟并行写状态。
 */
class LocalTimelineDriver(
    private val anchor: SystemMediaPlaybackAnchor,
    private val renderSink: LyricSink,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
) : LyricSink, SourceSelectionAwareSink, SystemMediaPlaybackAnchor.Listener {

    @Volatile
    private var activeSourceId: String? = null

    @Volatile
    private var appliedTrackKey: String? = null

    @Volatile
    private var appliedPackageName: String? = null

    @Volatile
    private var anchorPlaying: Boolean = false

    @Volatile
    private var hintPlaying: Boolean = false

    @Volatile
    private var hintPackage: String? = null

    /** 最近一次下发给渲染层的合成播放态；null=尚未下发过。 */
    private var renderedPlaying: Boolean? = null

    private var pendingContent: TimelineContent? = null
    private val appliedContentCache = LinkedHashMap<String, TimelineContent>()
    private var streamingTrackKey: String? = null
    private val streamingLines = linkedMapOf<Long, RichLyricLine>()
    private var positionJob: Job? = null
    private val positionScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    init {
        registerActiveInstance(this)
    }

    fun start() {
        anchor.start(this)
    }

    fun stop() {
        runOnMain {
            stopPositionLoop()
            activeSourceId = null
            appliedTrackKey = null
            appliedPackageName = null
            pendingContent = null
            appliedContentCache.clear()
            clearStreamingContent()
            anchorPlaying = false
            hintPlaying = false
            hintPackage = null
            renderedPlaying = null
            renderSink.onStop()
            registerActiveInstance(null)
        }
        anchor.removeListener(this)
    }

    /** 所有歌词渲染面都关闭时停止时间轴，不保留上一首内容。 */
    fun stopDriving() {
        runOnMain { clearTimeline(showTrackFallback = false) }
    }

    override fun onSourceSelected(sourceId: String) {
        runOnMain {
            if (activeSourceId == sourceId) return@runOnMain
            activeSourceId = sourceId
            pendingContent = null
            clearStreamingContent()
            clearTimeline(showTrackFallback = true)
            diagnostic("活动来源=$sourceId")
        }
    }

    override fun onSourceStopped(sourceId: String) {
        runOnMain {
            if (activeSourceId != sourceId) return@runOnMain
            activeSourceId = null
            pendingContent = null
            clearStreamingContent()
            clearTimeline(showTrackFallback = false)
        }
    }

    override fun onTimelineContent(content: TimelineContent) {
        runOnMain { handleTimelineContent(content) }
    }

    /** 兼容尚未改造完的完整 Song 回调；仍会转成统一内容，不允许直接写桥。 */
    override fun onSongChanged(song: Any?) {
        val sourceId = activeSourceId ?: return
        onTimelineContent(
            TimelineContent(
                sourceId = sourceId,
                track = anchor.currentTrack,
                song = song as? Song,
            )
        )
    }

    override fun onOnlineTranslationMatched(song: Any?) {
        val sourceId = activeSourceId ?: return
        onTimelineContent(
            TimelineContent(
                sourceId = sourceId,
                track = anchor.currentTrack,
                song = song as? Song,
                onlineTranslationMatched = true,
            )
        )
    }

    override fun onOnlineTranslationUnavailable(song: Any?) {
        runOnMain { renderSink.onOnlineTranslationUnavailable(song) }
    }

    override fun onStop() {
        runOnMain { clearTimeline(showTrackFallback = true) }
    }

    // 逐行兼容来源只提交内容；显示、播放时钟与滚动仍归本 driver。
    override fun onLyricLine(line: Any?) {
        val richLine = line as? RichLyricLine ?: return
        runOnMain { applyStreamingLine(richLine) }
    }

    override fun onPlainText(text: String?) {
        val value = text?.takeIf { it.isNotBlank() } ?: return
        runOnMain {
            val position = anchor.estimatedPosition()?.coerceAtLeast(0L) ?: 0L
            clearStreamingContent()
            applyStreamingLine(
                RichLyricLine(
                    begin = position,
                    end = Long.MAX_VALUE,
                    duration = Long.MAX_VALUE - position,
                    text = value,
                )
            )
        }
    }
    override fun onPositionChanged(position: Long) = rejectSourceClockEvent("position")
    override fun onSeekTo(position: Long) = rejectSourceClockEvent("seek")
    override fun onMetadata(title: String?, artist: String?, album: String?, publisher: String?) = Unit
    override fun currentPlaybackState(): Boolean = renderedPlaying ?: anchorPlaying

    override fun onTrackChanged(track: TrackIdentity?) {
        runOnMain {
            // 来源提示是包级播放意图，不是单曲快照。同 app 切歌时提示常早于
            // MediaSession 元数据到达；若在这里无条件清空，来源之后又没有状态翻转，
            // 新曲缓冲窗口就只剩下 MediaSession 的假暂停。跨 app/会话清空仍立即作废。
            if (!PlaybackSmoothingPolicy.shouldRetainHint(hintPackage, track?.packageName)) {
                hintPlaying = false
                hintPackage = null
            }
            val effectiveBeforeTransition = renderedPlaying ?: PlaybackSmoothingPolicy.effectivePlaying(
                anchorPlaying = anchorPlaying,
                hintPlaying = hintPlaying,
                hintPackage = hintPackage,
                anchorPackage = track?.packageName,
            )
            val preserveHost = PlaybackSmoothingPolicy.shouldPreserveHostAcrossTrackChange(
                currentPackage = appliedPackageName,
                nextPackage = track?.packageName,
                effectivePlaying = effectiveBeforeTransition,
            )
            if (!preserveHost) renderedPlaying = null
            clearStreamingContent()
            if (preserveHost && track != null) {
                prepareTrackTransition(track)
            } else {
                clearTimeline(showTrackFallback = track != null)
            }
            val pending = pendingContent
            pendingContent = null
            if (track != null) {
                val pendingApplies = pending != null &&
                    TimelineContentPolicy.decide(activeSourceId, track, pending) ==
                    TimelineContentPolicy.Decision.APPLY
                if (pendingApplies) {
                    applyContent(pending)
                    return@runOnMain
                }
                // 锚点让位后切回的曲目：暂存内容不匹配（或没有）时回放缓存内容。
                // 缓存键是锚点规范化键，应用时已经过一次身份校验；这里不再重复
                // 标题级匹配，否则原名刷新后的回放会被组合键标题差异误拒。
                val cached = appliedContentCache[track.normalizedKey()]
                if (cached != null && cached.sourceId == activeSourceId) {
                    applyContent(cached)
                    diagnostic("锚点切回，回放缓存内容: ${track.normalizedKey()}")
                } else if (pending != null) {
                    diagnostic("锚点更新后丢弃仍不匹配的暂存内容: ${pending.track?.normalizedKey()}")
                }
            }
        }
    }

    override fun onTrackMetadataRefreshed(track: TrackIdentity) {
        runOnMain {
            // 同一曲展示信息刷新（如原名恢复）：只更新标题元数据，时间轴与歌词保持不动。
            renderSink.onMetadata(track.title, track.artist, track.album, track.packageName)
        }
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        anchorPlaying = isPlaying
        runOnMain {
            if (appliedTrackKey == null) return@runOnMain
            refreshRenderPlaybackState()
        }
    }

    /**
     * 来源侧（app 进程内）播放态提示。部分 app 缓冲期向 MediaSession 上报暂停态，
     * 渲染层只信锚点会把缓冲当暂停触发岛缩回；同包来源侧仍报播放时维持播放态。
     */
    override fun onSourcePlaybackHint(playing: Boolean, packageName: String?) {
        runOnMain {
            if (hintPlaying == playing && hintPackage == packageName) return@runOnMain
            hintPlaying = playing
            hintPackage = packageName
            if (BuildConfig.DEBUG) {
                HookLogger.d(TAG, "[TIMELINE] 来源播放态提示: playing=$playing, pkg=$packageName")
            }
            if (appliedTrackKey != null) refreshRenderPlaybackState()
        }
    }

    private fun refreshRenderPlaybackState() {
        val effective = PlaybackSmoothingPolicy.effectivePlaying(
            anchorPlaying = anchorPlaying,
            hintPlaying = hintPlaying,
            hintPackage = hintPackage,
            anchorPackage = anchor.currentTrack?.packageName,
        )
        val cachedPlaying = renderedPlaying
        val sinkPlaying = renderSink.currentPlaybackState()
        if (
            PlaybackSmoothingPolicy.shouldDispatchPlaybackState(
                effectivePlaying = effective,
                renderedPlaying = cachedPlaying,
                sinkPlaying = sinkPlaying,
            )
        ) {
            renderedPlaying = effective
            renderSink.onPlaybackStateChanged(effective)
            diagnostic(
                "同步渲染播放态: effective=$effective, cached=$cachedPlaying, " +
                    "sink=$sinkPlaying"
            )
        }
        if (effective) {
            startPositionLoop()
        } else {
            stopPositionLoop()
            anchor.estimatedPosition()?.let(renderSink::onPositionChanged)
        }
    }

    private fun handleTimelineContent(content: TimelineContent) {
        when (TimelineContentPolicy.decide(activeSourceId, anchor.currentTrack, content)) {
            TimelineContentPolicy.Decision.APPLY -> applyContent(content)
            TimelineContentPolicy.Decision.HOLD_FOR_TRACK -> {
                pendingContent = content
                diagnostic("等待媒体身份: source=${content.sourceId}, title=${content.track?.title}")
            }
            TimelineContentPolicy.Decision.DROP_WRONG_TRACK -> {
                // 来源可能比 MediaSession 更早切歌，暂存一次；锚点更新后只会在严格匹配时应用。
                pendingContent = content
                diagnostic(
                    "暂存身份不符内容: source=${content.sourceId}, " +
                        "content=${content.track?.normalizedKey()}, anchor=${anchor.currentTrack?.normalizedKey()}"
                )
            }
            TimelineContentPolicy.Decision.DROP_INACTIVE_SOURCE -> diagnostic(
                "拒绝非活动来源: active=$activeSourceId, incoming=${content.sourceId}"
            )
        }
    }

    private fun applyContent(content: TimelineContent) {
        if (!SystemUiEnhancementGate.isLyricRuntimeEnabled()) return

        // 媒体会话是 SystemUI 侧的曲目身份权威；来源身份只用于入站匹配。
        val track = anchor.currentTrack ?: content.track
        if (track == null) {
            pendingContent = content
            return
        }
        val song = content.song
        if (song?.lyrics.isNullOrEmpty()) {
            applyTitleFallback(track, content.fallbackTitle)
            return
        }
        if (!content.streaming) clearStreamingContent()

        LyriconDataBridge.updateLyricPackage(track.packageName)
        val sameTrack = appliedTrackKey == track.normalizedKey()
        val replaced = sameTrack && LyriconDataBridge.replaceSameSongContent(song)
        if (!replaced) LyriconDataBridge.updateSong(song)

        val trackKey = track.normalizedKey()
        appliedTrackKey = trackKey
        appliedPackageName = track.packageName
        rememberAppliedContent(trackKey, content)
        pendingContent = null
        if (content.streaming) {
            // 流式兼容内容不触发整首歌词的在线/AI 翻译编排。
        } else if (content.onlineTranslationMatched) {
            renderSink.onOnlineTranslationMatched(song)
        } else {
            renderSink.onSongChanged(song)
        }
        renderSink.onMetadata(track.title, track.artist, track.album, track.packageName)
        anchor.estimatedPosition()?.let(renderSink::onPositionChanged)
        refreshRenderPlaybackState()
        HookLogger.i(
            TAG,
            "[TIMELINE] 已应用完整内容: source=${content.sourceId}, title=${track.title}, " +
                "lines=${song.lyrics?.size ?: 0}, revision=${content.revision}, replaced=$replaced"
        )
    }

    private fun applyTitleFallback(track: TrackIdentity, fallbackTitle: String) {
        val trackKey = track.normalizedKey()
        val fallbackAction = PlaybackSmoothingPolicy.emptyLyricsFallbackAction(
            appliedTrackKey = appliedTrackKey,
            nextTrackKey = trackKey,
            currentPackage = appliedPackageName,
            nextPackage = track.packageName,
            renderedPlaying = renderedPlaying,
        )
        when (fallbackAction) {
            PlaybackSmoothingPolicy.EmptyLyricsFallbackAction.KEEP_CURRENT_CONTENT -> Unit

            PlaybackSmoothingPolicy.EmptyLyricsFallbackAction.PRESERVE_HOST -> {
                stopPositionLoop()
                appliedTrackKey = null
                appliedPackageName = track.packageName
                renderSink.onTrackTransition(
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    publisher = track.packageName,
                )
            }

            PlaybackSmoothingPolicy.EmptyLyricsFallbackAction.FULL_RESET -> {
                stopPositionLoop()
                appliedTrackKey = null
                appliedPackageName = null
                renderedPlaying = null
                renderSink.onStop()
            }
        }
        LyriconDataBridge.updateLyricPackage(track.packageName)
        LyriconDataBridge.currentSongName = fallbackTitle.ifBlank { track.title }
        renderSink.onMetadata(track.title, track.artist, track.album, track.packageName)
        diagnostic(
            "空歌词内容回退: action=$fallbackAction, pkg=${track.packageName}, " +
                "title=${track.title}, renderedPlaying=$renderedPlaying"
        )
    }

    private fun clearTimeline(showTrackFallback: Boolean) {
        stopPositionLoop()
        appliedTrackKey = null
        appliedPackageName = null
        renderedPlaying = null
        renderSink.onStop()
        if (showTrackFallback) {
            anchor.currentTrack?.let { track ->
                LyriconDataBridge.updateLyricPackage(track.packageName)
                LyriconDataBridge.currentSongName = track.title
                renderSink.onMetadata(track.title, track.artist, track.album, track.packageName)
            }
        }
    }

    private fun prepareTrackTransition(track: TrackIdentity) {
        stopPositionLoop()
        appliedTrackKey = null
        appliedPackageName = track.packageName
        renderSink.onTrackTransition(
            title = track.title,
            artist = track.artist,
            album = track.album,
            publisher = track.packageName,
        )
        diagnostic("同包切歌保留岛宿主: pkg=${track.packageName}, title=${track.title}")
    }

    private fun applyStreamingLine(line: RichLyricLine) {
        val sourceId = activeSourceId ?: return
        val track = anchor.currentTrack ?: return
        val trackKey = track.normalizedKey()
        if (streamingTrackKey != trackKey) {
            clearStreamingContent()
            streamingTrackKey = trackKey
        }
        streamingLines[line.begin] = line
        while (streamingLines.size > MAX_STREAMING_LINES) {
            streamingLines.remove(streamingLines.keys.first())
        }
        handleTimelineContent(
            TimelineContent(
                sourceId = sourceId,
                track = track,
                song = Song(
                    id = trackKey,
                    name = track.title,
                    artist = track.artist,
                    duration = track.durationMs,
                    lyrics = streamingLines.values.sortedBy { it.begin },
                ),
                streaming = true,
            )
        )
    }

    private fun clearStreamingContent() {
        streamingTrackKey = null
        streamingLines.clear()
    }

    /** 记住最近应用的曲目内容，锚点让位后切回同一曲目时立即恢复，不必等来源重发。 */
    private fun rememberAppliedContent(trackKey: String, content: TimelineContent) {
        appliedContentCache[trackKey] = content
        while (appliedContentCache.size > MAX_CACHED_TRACKS) {
            appliedContentCache.remove(appliedContentCache.keys.first())
        }
    }

    private fun startPositionLoop() {
        if (positionJob?.isActive == true || appliedTrackKey == null) return
        positionJob = positionScope.launch {
            while (
                isActive && PlaybackSmoothingPolicy.shouldDrivePositionLoop(
                    appliedTrackKey = appliedTrackKey,
                    renderedPlaying = renderedPlaying,
                )
            ) {
                anchor.estimatedPosition()?.let(renderSink::onPositionChanged)
                delay(POSITION_INTERVAL_MS)
            }
        }
    }

    private fun stopPositionLoop() {
        positionJob?.cancel()
        positionJob = null
    }

    private fun rejectSourceClockEvent(event: String) {
        diagnostic("拒绝来源侧显示/时钟事件: source=$activeSourceId, event=$event")
    }

    private fun diagnostic(message: String) {
        if (BuildConfig.DEBUG) HookLogger.d(TAG, "[TIMELINE] $message")
    }

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == mainHandler.looper) action() else mainHandler.post(action)
    }

    companion object {
        private const val TAG = "LocalTimelineDriver"
        private const val POSITION_INTERVAL_MS = 33L
        private const val MAX_STREAMING_LINES = 256
        private const val MAX_CACHED_TRACKS = 8

        val appliedTrackKeyForDiag: String?
            get() = activeInstance?.appliedTrackKey

        @Volatile
        private var activeInstance: LocalTimelineDriver? = null

        internal fun registerActiveInstance(instance: LocalTimelineDriver?) {
            activeInstance = instance
        }
    }
}
