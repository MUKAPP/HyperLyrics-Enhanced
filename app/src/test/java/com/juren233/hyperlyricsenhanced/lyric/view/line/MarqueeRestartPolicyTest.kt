/* Copyright 2026 juren233 */
package com.juren233.hyperlyricsenhanced.lyric.view.line

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarqueeRestartPolicyTest {
    private fun canRestart(
        playbackActive: Boolean = true,
        isStaticPreview: Boolean = false,
        isPlainText: Boolean = true,
        scrollUnlocked: Boolean = true,
        previousOverflow: Boolean? = false,
        isOverflow: Boolean = true,
        isShown: Boolean = true,
        rendererPlaying: Boolean = false,
        frameLoopRunning: Boolean = false,
    ): Boolean = MarqueeRestartPolicy.canRestartOnWidthChange(
        playbackActive = playbackActive,
        isStaticPreview = isStaticPreview,
        isPlainText = isPlainText,
        scrollUnlocked = scrollUnlocked,
        previousOverflow = previousOverflow,
        isOverflow = isOverflow,
        isShown = isShown,
        rendererPlaying = rendererPlaying,
        frameLoopRunning = frameLoopRunning,
    )

    @Test
    fun `fits to overflow flip restarts idle marquee`() {
        // 宽了再窄的卡死场景：文本从放得下变为放不下时必须能重新开始滚动。
        assertTrue(canRestart(previousOverflow = false, isOverflow = true))
    }

    @Test
    fun `overflow to overflow width change never restarts`() {
        // 换句重算宽度但文本始终放不下：不打断正在滚动，也不重启已驻留的跑马灯。
        assertFalse(canRestart(previousOverflow = true, isOverflow = true))
        assertFalse(canRestart(previousOverflow = true, isOverflow = true, rendererPlaying = true, frameLoopRunning = true))
    }

    @Test
    fun `no baseline never restarts`() {
        // 首次布局/新行没有基线：启动交给 requestScroll 与位置 tick 的既有重试路径。
        assertFalse(canRestart(previousOverflow = null, isOverflow = true))
    }

    @Test
    fun `overflow to fits is handled by renderer fit detection`() {
        assertFalse(canRestart(previousOverflow = true, isOverflow = false))
    }

    @Test
    fun `stalled renderer restarts on flip while shown`() {
        // 隐藏期间落锁后帧回调从未运行的卡死状态：翻转时宽度变化必须自愈。
        assertTrue(canRestart(previousOverflow = false, isOverflow = true, rendererPlaying = true, frameLoopRunning = false))
    }

    @Test
    fun `paused locked preview and non plain text never restart`() {
        assertFalse(canRestart(playbackActive = false))
        assertFalse(canRestart(scrollUnlocked = false))
        assertFalse(canRestart(isStaticPreview = true))
        assertFalse(canRestart(isPlainText = false))
    }

    @Test
    fun `hidden view never restarts`() {
        // 不可见时动画无法启动，重启只会复现卡死；等可见后由下一次宽度变化或恢复链路处理。
        assertFalse(canRestart(isShown = false))
        assertFalse(canRestart(isShown = false, rendererPlaying = true, frameLoopRunning = false))
    }

    private fun canResumeOnShown(
        playbackActive: Boolean = true,
        isStaticPreview: Boolean = false,
        isPlainText: Boolean = true,
        scrollUnlocked: Boolean = true,
        isOverflow: Boolean = true,
        rendererPlaying: Boolean = true,
        frameLoopRunning: Boolean = false,
    ): Boolean = MarqueeRestartPolicy.canResumeFrameLoopOnShown(
        playbackActive = playbackActive,
        isStaticPreview = isStaticPreview,
        isPlainText = isPlainText,
        scrollUnlocked = scrollUnlocked,
        isOverflow = isOverflow,
        rendererPlaying = rendererPlaying,
        frameLoopRunning = frameLoopRunning,
    )

    @Test
    fun `stalled frame loop resumes on shown`() {
        // 隐藏窗口帧回调自停后重新可见：渲染器仍在播放、文本仍溢出时必须续播。
        assertTrue(canResumeOnShown())
    }

    @Test
    fun `healthy running loop is not touched on shown`() {
        assertFalse(canResumeOnShown(frameLoopRunning = true))
    }

    @Test
    fun `parked or finished marquee does not resume on shown`() {
        // stopAtEnd 驻留（rendererPlaying=false）不得因收起/展开被重启。
        assertFalse(canResumeOnShown(rendererPlaying = false))
    }

    @Test
    fun `resume gates mirror start gates`() {
        assertFalse(canResumeOnShown(playbackActive = false))
        assertFalse(canResumeOnShown(scrollUnlocked = false))
        assertFalse(canResumeOnShown(isStaticPreview = true))
        assertFalse(canResumeOnShown(isPlainText = false))
        assertFalse(canResumeOnShown(isOverflow = false))
    }
}
