/* Copyright 2026 juren233 */
package com.juren233.hyperlyricsenhanced.lyric.view.line

import android.os.SystemClock
import android.util.Log
import com.juren233.hyperlyricsenhanced.BuildConfig

/**
 * 跑马灯状态机诊断（仅 Debug 构建输出）。
 *
 * 用于定位"文本溢出但跑马灯不滚动"的卡点：解锁状态、落锁状态、帧回调是否存活、
 * 宽度翻转判定等。按 (视图, 事件) 限频，避免位置 tick 每秒刷屏。
 */
internal object MarqueeDiag {
    private const val TAG = "HyperLyrics Enhanced"
    private const val THROTTLE_MS = 1000L
    private val lastLoggedAt = HashMap<String, Long>()

    fun d(view: Any, event: String, detail: () -> String) {
        log(Log.DEBUG, view, event, detail)
    }

    fun i(view: Any, event: String, detail: () -> String) {
        log(Log.INFO, view, event, detail)
    }

    private fun log(priority: Int, view: Any, event: String, detail: () -> String) {
        if (!BuildConfig.DEBUG) return
        val viewId = Integer.toHexString(System.identityHashCode(view))
        val key = "$viewId:$event"
        val now = SystemClock.uptimeMillis()
        synchronized(lastLoggedAt) {
            val last = lastLoggedAt[key]
            if (last != null && now - last < THROTTLE_MS) return
            lastLoggedAt[key] = now
        }
        Log.println(
            priority,
            TAG,
            "[MarqueeDiag] view=$viewId event=$event ${detail()}",
        )
    }
}
