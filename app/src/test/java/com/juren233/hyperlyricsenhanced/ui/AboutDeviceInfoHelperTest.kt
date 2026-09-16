/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.juren233.hyperlyricsenhanced.ui

import com.juren233.hyperlyricsenhanced.ui.page.main.AboutDeviceInfoHelper
import org.junit.Assert.assertEquals
import org.junit.Test

class AboutDeviceInfoHelperTest {

    @Test
    fun `resolves custom device name from cached name first`() {
        val name = AboutDeviceInfoHelper.resolveDeviceNameInternal(
            cachedName = "Cavan的Xiaomi 15",
            miuiDeviceNameGetter = { "Other Name" },
            settingsGetter = { _, _ -> "Setting Name" },
            systemPropertyGetter = { "Prop Name" },
            bluetoothNameGetter = { "BT Name" },
            fallbackMarketName = "Xiaomi 15",
            fallbackModel = "24129PN74C",
        )
        assertEquals("Cavan的Xiaomi 15", name)
    }

    @Test
    fun `resolves custom device name from miui device name when cache is absent`() {
        val name = AboutDeviceInfoHelper.resolveDeviceNameInternal(
            cachedName = null,
            miuiDeviceNameGetter = { "MIUI Custom Name" },
            settingsGetter = { _, _ -> null },
            systemPropertyGetter = { null },
            bluetoothNameGetter = { null },
            fallbackMarketName = "Xiaomi 15",
            fallbackModel = "24129PN74C",
        )
        assertEquals("MIUI Custom Name", name)
    }

    @Test
    fun `resolves custom device name from secure bluetooth_name when miui is absent`() {
        val name = AboutDeviceInfoHelper.resolveDeviceNameInternal(
            cachedName = null,
            miuiDeviceNameGetter = { null },
            settingsGetter = { namespace, key ->
                if (namespace == "secure" && key == "bluetooth_name") "Cavan的小米手机" else null
            },
            systemPropertyGetter = { null },
            bluetoothNameGetter = { null },
            fallbackMarketName = "Xiaomi 14 Pro",
            fallbackModel = "23127PN0CC",
        )
        assertEquals("Cavan的小米手机", name)
    }

    @Test
    fun `resolves custom device name from system device_name`() {
        val name = AboutDeviceInfoHelper.resolveDeviceNameInternal(
            cachedName = null,
            miuiDeviceNameGetter = { null },
            settingsGetter = { namespace, key ->
                if (namespace == "system" && key == "device_name") "Custom Name" else null
            },
            systemPropertyGetter = { null },
            bluetoothNameGetter = { null },
            fallbackMarketName = "Xiaomi 15",
            fallbackModel = "24129PN74C",
        )
        assertEquals("Custom Name", name)
    }

    @Test
    fun `resolves custom device name from persist private device_name`() {
        val name = AboutDeviceInfoHelper.resolveDeviceNameInternal(
            cachedName = null,
            miuiDeviceNameGetter = { null },
            settingsGetter = { _, _ -> null },
            systemPropertyGetter = { key ->
                if (key == "persist.private.device_name") "Cavan的Xiaomi 15" else null
            },
            bluetoothNameGetter = { null },
            fallbackMarketName = "Xiaomi 15",
            fallbackModel = "24129PN74C",
        )
        assertEquals("Cavan的Xiaomi 15", name)
    }

    @Test
    fun `resolves custom device name from persist sys device_name`() {
        val name = AboutDeviceInfoHelper.resolveDeviceNameInternal(
            cachedName = null,
            miuiDeviceNameGetter = { null },
            settingsGetter = { _, _ -> null },
            systemPropertyGetter = { key ->
                if (key == "persist.sys.device_name") "Persisted Xiaomi" else null
            },
            bluetoothNameGetter = { null },
            fallbackMarketName = "Xiaomi 15",
            fallbackModel = "24129PN74C",
        )
        assertEquals("Persisted Xiaomi", name)
    }

    @Test
    fun `falls back to ro product marketname when not customized`() {
        val name = AboutDeviceInfoHelper.resolveDeviceNameInternal(
            cachedName = null,
            miuiDeviceNameGetter = { null },
            settingsGetter = { _, _ -> null },
            systemPropertyGetter = { null },
            bluetoothNameGetter = { null },
            fallbackMarketName = "Xiaomi 15",
            fallbackModel = "24129PN74C",
        )
        assertEquals("Xiaomi 15", name)
    }

    @Test
    fun `falls back to model when both settings and marketname are blank`() {
        val name = AboutDeviceInfoHelper.resolveDeviceNameInternal(
            cachedName = null,
            miuiDeviceNameGetter = { null },
            settingsGetter = { _, _ -> "   " },
            systemPropertyGetter = { _ -> "" },
            bluetoothNameGetter = { null },
            fallbackMarketName = null,
            fallbackModel = "GenericModel",
        )
        assertEquals("GenericModel", name)
    }

    @Test
    fun `device model resolves to marketing name Xiaomi 15 instead of certified model`() {
        val model = AboutDeviceInfoHelper.resolveDeviceModel(
            systemPropertyGetter = { key ->
                when (key) {
                    "ro.product.marketname" -> "Xiaomi 15"
                    "ro.product.model" -> "24129PN74C"
                    else -> null
                }
            },
            fallbackModel = "24129PN74C",
        )
        assertEquals("Xiaomi 15", model)
    }

    @Test
    fun `device model falls back to fallbackModel when ro product marketname is missing`() {
        val model = AboutDeviceInfoHelper.resolveDeviceModel(
            systemPropertyGetter = { null },
            fallbackModel = "FallbackModel",
        )
        assertEquals("FallbackModel", model)
    }

    @Test
    fun `root resolution prioritizes persist private device_name`() {
        val name = listOfNotNull(
            "Cavan的Xiaomi 15", // persist.private.device_name
            "Cavan的Xiaomi 15 BT", // persist.bluetooth.device_name
            "Xiaomi 15", // persist.sys.device_name
        ).firstOrNull { it.isNotBlank() }
        assertEquals("Cavan的Xiaomi 15", name)
    }
}
