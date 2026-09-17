/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.juren233.hyperlyricsenhanced.common

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * 功能入口开关的统一落盘：入口值 + 按 [FeatureEntryGate] 规则联动的功能自身开关与暂存值。
 *
 * 设置页的“功能开关”与引导页选择工作模式时的自动关闭共用这一份实现，保证行为一致；
 * 返回的键值对由调用方按需同步到宿主进程（[PrefsBridge]）。
 */
object FeatureEntryApplier {

    /**
     * 将功能入口切换为 [enabled]，并把联动写入应用到 [prefs]。
     *
     * [featureKey] 为 null 表示该入口没有联动的功能自身开关。返回实际写入的布尔键值对
     * （不含暂存清理），调用方遍历后同步到宿主进程即可。
     */
    fun setEntryEnabled(
        prefs: SharedPreferences,
        entryKey: String,
        enabled: Boolean,
        featureKey: String?,
    ): List<Pair<String, Boolean>> {
        val writes = linkedMapOf(entryKey to enabled)
        if (featureKey != null) {
            val stashKey = FeatureEntryGate.stashKey(featureKey)
            val outcome = FeatureEntryGate.resolveOnEntryToggle(
                entryEnabled = enabled,
                currentFeatureValue = prefs.getBoolean(featureKey, false),
                stashedValue = if (prefs.contains(stashKey)) prefs.getBoolean(stashKey, false) else null,
            )
            outcome.featureValue?.let { writes[featureKey] = it }
            outcome.stashValue?.let { writes[stashKey] = it }
            if (outcome.clearStash) {
                prefs.edit { remove(stashKey) }
            }
        }
        writes.forEach { (key, value) -> prefs.edit { putBoolean(key, value) } }
        return writes.toList()
    }
}
