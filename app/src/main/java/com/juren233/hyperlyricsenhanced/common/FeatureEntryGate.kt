/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.juren233.hyperlyricsenhanced.common

/**
 * 功能入口开关变化后，功能自身开关与暂存值的落点。
 */
data class FeatureEntryGateOutcome(
    /** 需要写入功能自身开关的值；null 表示保持原值不变。 */
    val featureValue: Boolean? = null,
    /** 需要写入的暂存值；null 表示不写入。 */
    val stashValue: Boolean? = null,
    /** 是否需要清除暂存值。 */
    val clearStash: Boolean = false,
)

/**
 * “功能开关”（功能入口）的停用与恢复规则。
 *
 * 关闭入口时功能整体停用，但用户自己的设置在关闭期间原样暂存，重新打开入口后恢复，
 * 因此关闭入口不会丢失关闭前的任何设置。
 */
object FeatureEntryGate {

    fun stashKey(featureKey: String): String =
        UIConstants.KEY_FEATURE_ENTRY_STASH_PREFIX + featureKey

    fun resolveOnEntryToggle(
        entryEnabled: Boolean,
        currentFeatureValue: Boolean,
        stashedValue: Boolean?,
    ): FeatureEntryGateOutcome = if (entryEnabled) {
        if (stashedValue == null) {
            // 未因入口关闭而停用过：功能开关保持原值，什么都不用写。
            FeatureEntryGateOutcome()
        } else {
            // 恢复入口关闭前的设置，并清理暂存。
            FeatureEntryGateOutcome(featureValue = stashedValue, clearStash = true)
        }
    } else {
        // 关闭入口：把功能自身开关置为停用；已有暂存时保留最早的值，保证恢复的是用户真正设置过的状态。
        FeatureEntryGateOutcome(
            featureValue = false,
            stashValue = stashedValue ?: currentFeatureValue,
        )
    }
}
