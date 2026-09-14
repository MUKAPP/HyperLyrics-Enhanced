/* Copyright 2026 juren233 */
package com.juren233.hyperlyricsenhanced.lyric.view.line

/**
 * 动态宽度/动态上限会让同一行文本显示期间的实际可用宽度发生变化（换句、
 * 状态栏内容变化都会触发岛宽重算）。宽度变化时按"溢出状态翻转"重新评估
 * 纯文本行的跑马灯：
 *
 * - 仅当上一次评估时文本放得下（previousOverflow == false）、现在放不下
 *   （isOverflow == true）才允许（重新）启动滚动；这是修复"宽了再窄后
 *   永久卡死"的唯一入口。
 * - 放不下 → 放不下：一律不重启。换句引起的宽度变化、正在滚动的动画、
 *   已按 stopAtEnd 驻留的文本都不被打断，否则另一侧换句会让本侧跑马灯
 *   频繁回顶。
 * - 放不下 → 放得下：不在这里处理，由渲染器的 fit 判定自然停住归位。
 * - previousOverflow == null 表示尚无基线（首次布局/新行），不重启；
 *   首次启动交给 requestScroll 与位置 tick 的既有重试路径。
 *
 * 帧回调未运行但渲染器自认在播放（隐藏期间落锁的卡死态）且视图可见时，
 * 翻转时刻同样允许重启以自愈。
 */
internal object MarqueeRestartPolicy {
    fun canRestartOnWidthChange(
        playbackActive: Boolean,
        isStaticPreview: Boolean,
        isPlainText: Boolean,
        scrollUnlocked: Boolean,
        previousOverflow: Boolean?,
        isOverflow: Boolean,
        isShown: Boolean,
        rendererPlaying: Boolean,
        frameLoopRunning: Boolean,
    ): Boolean = playbackActive &&
        !isStaticPreview &&
        isPlainText &&
        scrollUnlocked &&
        previousOverflow == false &&
        isOverflow &&
        isShown &&
        (!rendererPlaying || !frameLoopRunning)

    /**
     * 视图重新可见（isShown false→true）时的帧回调自愈判定。隐藏窗口里
     * Animator.doFrame 因 !isShown 自停、无人再踢，形成"渲染器自认在播放但
     * 帧回调没跑"的卡死态；重新可见后允许直接续播（不重置滚动位置）。
     * 已按 stopAtEnd 驻留/判完成的（rendererPlaying == false）不在可见时重启，
     * 保持"同宽度只滚 N 次"语义。
     */
    fun canResumeFrameLoopOnShown(
        playbackActive: Boolean,
        isStaticPreview: Boolean,
        isPlainText: Boolean,
        scrollUnlocked: Boolean,
        isOverflow: Boolean,
        rendererPlaying: Boolean,
        frameLoopRunning: Boolean,
    ): Boolean = playbackActive &&
        !isStaticPreview &&
        isPlainText &&
        scrollUnlocked &&
        isOverflow &&
        rendererPlaying &&
        !frameLoopRunning
}
