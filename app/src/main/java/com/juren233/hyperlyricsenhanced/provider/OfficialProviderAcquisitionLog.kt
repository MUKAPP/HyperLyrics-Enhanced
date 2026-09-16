/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.juren233.hyperlyricsenhanced.provider

import com.juren233.hyperlyricsenhanced.utils.LogManager

/**
 * 官方 Provider 获取链路（目录加载 → Pack 下载 → 校验安装）的诊断日志。
 *
 * LogManager 只在 Debug 构建落盘，因此这些日志服务于 Debug 诊断包，会出现在
 * “日志 → 应用日志”里并可被导出。这里再包一层 runCatching，避免 JVM 单测中
 * android.util.Log 桩抛异常时打断下载与安装流程。
 */
internal object OfficialProviderAcquisitionLog {
    private const val TAG = "ProviderAcquisition"

    fun info(message: String) {
        runCatching { LogManager.i(TAG, message) }
    }

    fun warn(message: String, error: Throwable? = null) {
        runCatching { LogManager.w(TAG, message, error) }
    }

    fun error(message: String, error: Throwable? = null) {
        runCatching { LogManager.e(TAG, message, error) }
    }

    fun describe(error: Throwable?): String =
        if (error == null) "none" else "${error::class.java.simpleName}: ${error.message}"
}
