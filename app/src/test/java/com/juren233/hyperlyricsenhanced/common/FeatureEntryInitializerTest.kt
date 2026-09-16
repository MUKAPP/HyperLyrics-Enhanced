/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.juren233.hyperlyricsenhanced.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureEntryInitializerTest {

    @Test
    fun `missing entry key falls back to the device capability`() {
        assertTrue(
            FeatureEntryInitializer.initialEntryValue(
                entryKeyPresent = false,
                storedValue = false,
                defaultEnabled = true,
            ),
        )
        assertFalse(
            FeatureEntryInitializer.initialEntryValue(
                entryKeyPresent = false,
                storedValue = true,
                defaultEnabled = false,
            ),
        )
    }

    @Test
    fun `existing entry key always keeps the value written before, including later updates`() {
        // 用户手动开启过：非米系设备上后续升级版本也不允许再自动关掉。
        assertTrue(
            FeatureEntryInitializer.initialEntryValue(
                entryKeyPresent = true,
                storedValue = true,
                defaultEnabled = false,
            ),
        )
        // 用户手动关闭过：已安装 Apple Music 的设备上同样保持关闭。
        assertFalse(
            FeatureEntryInitializer.initialEntryValue(
                entryKeyPresent = true,
                storedValue = false,
                defaultEnabled = true,
            ),
        )
    }

    @Test
    fun `apple music entry only re-triggers when the install state changed`() {
        // 第一次记录安装状态：本次只写快照，不改入口。
        assertNull(
            FeatureEntryInitializer.appleMusicEntryOnInstallStateChange(
                previousInstalled = null,
                currentInstalled = true,
                currentEntryValue = false,
            ),
        )
        // 卸载 Apple Music：即使入口是用户开着的一并关闭。
        assertFalse(
            FeatureEntryInitializer.appleMusicEntryOnInstallStateChange(
                previousInstalled = true,
                currentInstalled = false,
                currentEntryValue = true,
            )!!
        )
        // 重新安装 Apple Music：入口跟随安装状态自动开启。
        assertTrue(
            FeatureEntryInitializer.appleMusicEntryOnInstallStateChange(
                previousInstalled = false,
                currentInstalled = true,
                currentEntryValue = false,
            )!!
        )
        // 安装状态没变时，用户手动关闭的入口不会被重新打开。
        assertNull(
            FeatureEntryInitializer.appleMusicEntryOnInstallStateChange(
                previousInstalled = true,
                currentInstalled = true,
                currentEntryValue = false,
            ),
        )
        // 安装状态没变时，用户在未安装设备上手动开启的入口也不会被自动关掉。
        assertNull(
            FeatureEntryInitializer.appleMusicEntryOnInstallStateChange(
                previousInstalled = false,
                currentInstalled = false,
                currentEntryValue = true,
            ),
        )
    }
}
