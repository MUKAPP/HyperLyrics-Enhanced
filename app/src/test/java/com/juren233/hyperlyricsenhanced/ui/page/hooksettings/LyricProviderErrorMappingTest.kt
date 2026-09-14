/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.juren233.hyperlyricsenhanced.ui.page.hooksettings

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricProviderErrorMappingTest {

    private val texts = ProviderErrorTexts(
        network = "NETWORK",
        http = "HTTP:%1\$s@%2\$s",
        catalogSignature = "SIGNATURE",
        catalogVersion = "CATALOG_VERSION",
        catalogDuplicate = "CATALOG_DUPLICATE",
        catalogTarget = "CATALOG_TARGET",
        catalogUrl = "CATALOG_URL",
        catalogData = "CATALOG_DATA",
        unavailable = "UNAVAILABLE",
        integrity = "INTEGRITY",
        incompatible = "INCOMPATIBLE",
        unsupportedTarget = "UNSUPPORTED_TARGET",
        storage = "STORAGE",
        generic = "GENERIC:%1\$s",
    )

    private fun localize(raw: String?): String =
        localizeProviderError(raw, texts, unknownText = "UNKNOWN")

    @Test
    fun `stale app catalog errors map to catalog version`() {
        for (raw in setOf(
            "Provider 目录包含未知插件",
            "Provider 目录与内置允许列表不一致",
            "Provider 目录格式不兼容",
        )) {
            assertEquals("CATALOG_VERSION", localize(raw))
        }
    }

    @Test
    fun `repository side catalog errors split into dedicated buckets`() {
        assertEquals(
            "CATALOG_DUPLICATE",
            localize("Provider 目录包含重复插件"),
        )
        assertEquals(
            "CATALOG_TARGET",
            localize("Provider 目录目标包名无效"),
        )
        assertEquals(
            "CATALOG_URL",
            localize("Provider 下载地址必须使用 GitHub HTTPS"),
        )
        assertEquals(
            "CATALOG_URL",
            localize("未发布 Provider 不得声明下载地址"),
        )
    }

    @Test
    fun `unknown catalog errors fall back to catalog data bucket`() {
        assertEquals("CATALOG_DATA", localize("Provider 目录不可用"))
    }

    @Test
    fun `http error extracts status and source host`() {
        assertEquals(
            "HTTP:403@hleplugins.juren233.top",
            localize("下载失败: HTTP 403 (hleplugins.juren233.top)"),
        )
    }

    @Test
    fun `http error without source host falls back to unknown`() {
        assertEquals("HTTP:500@UNKNOWN", localize("下载失败: HTTP 500"))
    }

    @Test
    fun `tls and hijacked connection errors map to network`() {
        for (raw in listOf(
            "Handshake failed",
            "Connection prematurely closed during TLS",
            "Unexpected end of stream on raw.githubusercontent.com",
            "Software caused connection abort",
            "Use keystore to trust certificate of hleplugins.juren233.top",
        )) {
            assertEquals("NETWORK", localize(raw))
        }
    }

    @Test
    fun `unsupported target app errors map to dedicated text`() {
        for (raw in listOf(
            "Provider 不支持当前音乐软件",
            "Provider 与当前音乐软件不匹配",
        )) {
            assertEquals("UNSUPPORTED_TARGET", localize(raw))
        }
    }

    @Test
    fun `storage write check wins over integrity by keyword order`() {
        assertEquals("STORAGE", localize("Provider Pack 写入校验失败"))
        assertEquals("STORAGE", localize("Provider Pack 原子替换失败"))
    }

    @Test
    fun `integrity failures still map to integrity`() {
        assertEquals("INTEGRITY", localize("Provider Pack 与目录摘要不一致"))
        assertEquals("INTEGRITY", localize("同一 Provider 版本对应了不同内容"))
    }

    @Test
    fun `signature error wins over generic catalog bucket`() {
        assertEquals("SIGNATURE", localize("Provider 目录签名无效"))
    }

    @Test
    fun `empty message uses unknown placeholder in generic`() {
        assertEquals("GENERIC:UNKNOWN", localize(null))
        assertEquals("GENERIC:UNKNOWN", localize("  "))
    }

    @Test
    fun `unmapped messages fall through to generic with raw text`() {
        assertEquals("GENERIC:Provider 入口类不在允许命名空间", localize("Provider 入口类不在允许命名空间"))
    }
}
