/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.juren233.hyperlyricsenhanced.common

import android.content.SharedPreferences

/**
 * “功能开关”的一次性初始化。
 *
 * 只在首次安装、以及首次升级到带功能入口的版本时执行一次：按当前设备能力写入各入口的初始值。
 * 已经存在的入口键（用户手动设置过）不会被改写，所以后续版本更新不会再触发自动关闭，
 * 用户手动开启的入口会一直保留。
 *
 * 唯一的例外是 Apple Music 的安装状态：安装或卸载 Apple Music 时按
 * [syncAppleMusicEntryWithInstallState] 重新触发一次自动开关（未安装则关闭、已安装则开启），
 * 状态没有变化时不会覆盖用户的手动设置。
 */
object FeatureEntryInitializer {

    /** 本次启动是否需要执行一次性初始化。 */
    fun shouldInitialize(prefs: SharedPreferences): Boolean =
        !prefs.getBoolean(UIConstants.KEY_FEATURE_ENTRY_INITIALIZED, false)

    /**
     * 单个入口的初始取值。
     *
     * 键已存在时保持用户当前值，只有从未写过时才采用设备能力推导出的默认值。
     */
    fun initialEntryValue(
        entryKeyPresent: Boolean,
        storedValue: Boolean,
        defaultEnabled: Boolean,
    ): Boolean = if (entryKeyPresent) storedValue else defaultEnabled

    /**
     * Apple Music 安装状态变化后，入口需要改写的值；null 表示不需要改写入口。
     *
     * [previousInstalled] 为 null 表示还没有记录过安装状态（本次只记录快照）；
     * 状态与上次相同、或入口当前值已经与安装状态一致时都不改写，避免覆盖用户的手动设置。
     */
    fun appleMusicEntryOnInstallStateChange(
        previousInstalled: Boolean?,
        currentInstalled: Boolean,
        currentEntryValue: Boolean,
    ): Boolean? = when {
        previousInstalled == null -> null
        previousInstalled == currentInstalled -> null
        currentEntryValue == currentInstalled -> null
        else -> currentInstalled
    }

    /**
     * 让 Apple Music 入口跟随 Apple Music 的安装状态。
     *
     * 仅在观测到安装状态与上次不同（新装了或卸载了 Apple Music）时重新触发一次：
     * 未安装 → 自动关闭入口，已安装 → 自动开启入口；状态没变化时不动入口。
     */
    fun syncAppleMusicEntryWithInstallState(
        prefs: SharedPreferences,
        appleMusicInstalled: Boolean,
    ): List<Pair<String, Boolean>> {
        val snapshotKey = UIConstants.KEY_FEATURE_ENTRY_APPLE_MUSIC_INSTALL_SNAPSHOT
        val previousInstalled = if (prefs.contains(snapshotKey)) {
            prefs.getBoolean(snapshotKey, false)
        } else {
            null
        }
        val entryValue = appleMusicEntryOnInstallStateChange(
            previousInstalled = previousInstalled,
            currentInstalled = appleMusicInstalled,
            currentEntryValue = prefs.getBoolean(
                UIConstants.KEY_FEATURE_ENTRY_APPLE_MUSIC,
                appleMusicInstalled,
            ),
        )
        if (previousInstalled == appleMusicInstalled && entryValue == null) {
            return emptyList()
        }

        val writes = linkedMapOf<String, Boolean>()
        writes[snapshotKey] = appleMusicInstalled
        entryValue?.let { writes[UIConstants.KEY_FEATURE_ENTRY_APPLE_MUSIC] = it }

        val storedWrites = writes.toList()
        val editor = prefs.edit()
        storedWrites.forEach { (key, value) -> editor.putBoolean(key, value) }
        editor.commit()
        return storedWrites
    }

    /**
     * 执行一次性初始化，返回需要同步到宿主进程的键值。
     *
     * 入口初始关闭时，对应功能自身的开关按 [FeatureEntryGate] 的规则停用并暂存，
     * 重新打开入口后依旧能恢复关闭前的设置。
     */
    fun applyOnce(
        prefs: SharedPreferences,
        xiaomiDevice: Boolean,
        appleMusicInstalled: Boolean,
    ): List<Pair<String, Boolean>> {
        if (!shouldInitialize(prefs)) {
            return emptyList()
        }

        val writes = linkedMapOf<String, Boolean>()
        seedEntry(
            prefs = prefs,
            writes = writes,
            entryKey = UIConstants.KEY_FEATURE_ENTRY_SUPER_ISLAND,
            defaultEnabled = xiaomiDevice,
            featureKey = RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND,
            featureDefault = RootConstants.DEFAULT_HOOK_ENABLE_SUPER_ISLAND,
        )
        seedEntry(
            prefs = prefs,
            writes = writes,
            entryKey = UIConstants.KEY_FEATURE_ENTRY_AOD_LYRICS,
            defaultEnabled = xiaomiDevice,
            featureKey = RootConstants.KEY_HOOK_ENABLE_AOD_LYRICS,
            featureDefault = RootConstants.DEFAULT_HOOK_ENABLE_AOD_LYRICS,
        )
        seedEntry(
            prefs = prefs,
            writes = writes,
            entryKey = UIConstants.KEY_FEATURE_ENTRY_DYNAMIC_ISLAND,
            defaultEnabled = true,
            featureKey = RootConstants.KEY_HOOK_ENABLE_DYNAMIC_ISLAND,
            featureDefault = RootConstants.DEFAULT_HOOK_ENABLE_DYNAMIC_ISLAND,
        )
        seedEntry(
            prefs = prefs,
            writes = writes,
            entryKey = UIConstants.KEY_FEATURE_ENTRY_APPLE_MUSIC,
            defaultEnabled = appleMusicInstalled,
            featureKey = null,
            featureDefault = false,
        )

        val storedWrites = writes.toList()
        val editor = prefs.edit()
        storedWrites.forEach { (key, value) -> editor.putBoolean(key, value) }
        editor.putBoolean(UIConstants.KEY_FEATURE_ENTRY_INITIALIZED, true)
        editor.commit()
        return storedWrites
    }

    private fun seedEntry(
        prefs: SharedPreferences,
        writes: MutableMap<String, Boolean>,
        entryKey: String,
        defaultEnabled: Boolean,
        featureKey: String?,
        featureDefault: Boolean,
    ) {
        val entryKeyPresent = prefs.contains(entryKey)
        val entryValue = initialEntryValue(
            entryKeyPresent = entryKeyPresent,
            storedValue = prefs.getBoolean(entryKey, false),
            defaultEnabled = defaultEnabled,
        )
        if (!entryKeyPresent) {
            writes[entryKey] = entryValue
        }
        if (featureKey == null || entryValue) {
            return
        }

        // 入口关闭即停用对应功能：把功能自身开关置为停用，并暂存关闭前的值供重新打开时恢复。
        val currentFeatureValue = prefs.getBoolean(featureKey, featureDefault)
        if (!currentFeatureValue) {
            return
        }
        val stashKey = FeatureEntryGate.stashKey(featureKey)
        val outcome = FeatureEntryGate.resolveOnEntryToggle(
            entryEnabled = false,
            currentFeatureValue = currentFeatureValue,
            stashedValue = if (prefs.contains(stashKey)) prefs.getBoolean(stashKey, false) else null,
        )
        outcome.featureValue?.let { writes[featureKey] = it }
        outcome.stashValue?.let { writes[stashKey] = it }
    }
}
