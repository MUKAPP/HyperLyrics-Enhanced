package com.juren233.hyperlyricsenhanced.service.scheduler

import android.content.Context
import com.juren233.hyperlyricsenhanced.common.ServiceConstants
import com.juren233.hyperlyricsenhanced.common.UIConstants
import com.juren233.hyperlyricsenhanced.lyric.DynamicLyricData
import com.juren233.hyperlyricsenhanced.lyric.LrcLine
import com.juren233.hyperlyricsenhanced.service.source.SyncData
import com.juren233.hyperlyricsenhanced.utils.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

interface LyricSchedulerListener {
    fun onLyricTick(lyricText: String, data: SyncData)
    fun onProgressTick(progressPercent: Float)
}

class LyricScheduler(
    private val context: Context,
    private val scope: CoroutineScope,
    private val listener: LyricSchedulerListener
) {
    private val tickerRegistry = SchedulerJobRegistry()
    private val progressRegistry = SchedulerJobRegistry()

    @Volatile
    private var currentLyricLines: List<LrcLine>? = null
    @Volatile
    private var currentSyncData: SyncData? = null
    @Volatile
    private var lastDispatchedLrc: String = ""

    /**
     * 更新当前歌曲的歌词行列表，并标记是否是新歌
     */
    fun updateLyrics(lines: List<LrcLine>?, isNewSong: Boolean) {
        currentLyricLines = lines
        if (isNewSong) {
            lastDispatchedLrc = ""
        }
    }

    /**
     * 更新当前媒体状态数据
     */
    fun updateSyncData(data: SyncData) {
        currentSyncData = data
    }

    /**
     * 停止之前的调度器并按需重新启动
     */
    fun startSchedulers(isSongChanged: Boolean, playStateChanged: Boolean) {
        val lines = currentLyricLines
        val data = currentSyncData ?: return

        if (lines != null) {
            // 有滚动歌词
            if (isSongChanged || playStateChanged || tickerRegistry.needsLaunch()) {
                launchLyricScheduler(lines)
            }
            if (isSongChanged || playStateChanged || progressRegistry.needsLaunch()) {
                launchProgressScheduler()
            }
        } else {
            // 没有滚动歌词 (静态标题模式)
            if (isSongChanged || playStateChanged || progressRegistry.needsLaunch()) {
                launchProgressScheduler()
            }
        }
    }

    /**
     * 停止全部调度任务
     */
    fun stop() {
        tickerRegistry.clear()
        progressRegistry.clear()
        currentLyricLines = null
        currentSyncData = null
        lastDispatchedLrc = ""
    }

    private fun launchLyricScheduler(lines: List<LrcLine>) {
        LogManager.d("LyricScheduler", "启动歌词滚动调度器: 行数=${lines.size}")

        tickerRegistry.launchReplacing(scope) {
            val self = coroutineContext[Job]
            while (true) {
                // 孤儿自愈：被更新的启动或 stop() 取代后立即退出，防止双 ticker 交替分发。
                if (!tickerRegistry.isCurrent(self)) break
                val data = currentSyncData ?: break
                val currentPos = with(DynamicLyricData) { musicState.value.getCurrentPosition() }

                val currentLineIndex = lines.indexOfLast { it.startTimeMs <= currentPos }
                val targetLine = if (currentLineIndex != -1) lines[currentLineIndex].content else data.dynamicTitle

                if (targetLine != lastDispatchedLrc) {
                    lastDispatchedLrc = targetLine
                    listener.onLyricTick(targetLine, data)
                }

                if (!data.isPlaying) break
                delay(150.milliseconds)
            }
        }
    }

    private fun launchProgressScheduler() {
        val sp = context.getSharedPreferences(UIConstants.PREF_NAME, Context.MODE_PRIVATE)
        val showProgress = sp.getBoolean(ServiceConstants.KEY_NOTIFICATION_SHOW_PROGRESS, ServiceConstants.DEFAULT_NOTIFICATION_SHOW_PROGRESS)
        if (!showProgress) {
            progressRegistry.clear()
            return
        }
        LogManager.d("LyricScheduler", "启动播放进度调度器")

        progressRegistry.launchReplacing(scope) {
            val self = coroutineContext[Job]
            var lastPercent = -1
            while (true) {
                if (!progressRegistry.isCurrent(self)) break
                val data = currentSyncData ?: break
                val duration = data.duration
                if (!data.isPlaying || duration <= 1000) break

                val currentPos = with(DynamicLyricData) { musicState.value.getCurrentPosition() }
                val currentPercent = ((currentPos.toDouble() / duration.toDouble()) * 100).toInt().coerceIn(0, 100)

                if (currentPercent != lastPercent) {
                    listener.onProgressTick(currentPercent.toFloat())
                    lastPercent = currentPercent
                }

                if (currentPercent >= 100) break
                delay(1000.milliseconds)
            }
        }
    }
}
