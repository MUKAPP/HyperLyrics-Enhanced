/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.juren233.hyperlyricsenhanced.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialProviderRepositorySourcesTest {

    @Test
    fun `catalog sources keep official github raw first and r2 second`() {
        val sources = OfficialProviderRepository.CATALOG_BASE_URLS
        assertTrue(
            sources.first().startsWith(
                "https://raw.githubusercontent.com/juren233/HLE-Providers/main/catalog/",
            ),
        )
        assertEquals(
            OfficialProviderRepository.R2_PUBLIC_BASE + "catalog/",
            sources[1],
        )
        assertTrue(sources.size > 2)
        assertTrue(sources.all { it.startsWith("https://") && it.endsWith('/') })
    }

    @Test
    fun `r2 public base is https custom domain with trailing slash`() {
        val base = OfficialProviderRepository.R2_PUBLIC_BASE
        assertTrue(base.startsWith("https://"))
        assertTrue(base.endsWith('/'))
        assertTrue(!base.contains("github"))
    }

    @Test
    fun `pack url candidates keep direct github first then r2 then mirrors`() {
        val assetUrl = "https://github.com/juren233/HLE-Providers/releases/download/" +
            "kugou-v1.0.13/kugou-1.0.13.hlp"
        val candidates = OfficialProviderRepository.packUrlCandidates(assetUrl)
        assertEquals(assetUrl, candidates.first())
        assertEquals(
            OfficialProviderRepository.R2_PUBLIC_BASE +
                "releases/download/kugou-v1.0.13/kugou-1.0.13.hlp",
            candidates[1],
        )
        val mirrors = candidates.drop(2)
        assertTrue(mirrors.isNotEmpty())
        assertTrue(mirrors.all { it.endsWith(assetUrl.removePrefix("https://github.com")) })
        assertTrue(
            mirrors.all { url ->
                OfficialProviderRepository.PACK_MIRROR_PREFIXES.any { url.startsWith(it) }
            },
        )
    }

    @Test
    fun `catalog cache roundtrip preserves signature and bytes`() {
        val encoded = OfficialProviderRepository.encodeCatalogCache(
            "c2ln",
            "catalog-bytes".toByteArray(),
        )
        val decoded = OfficialProviderRepository.decodeCatalogCache(encoded)
        assertNotNull(decoded)
        assertEquals("sig".toByteArray().toList(), decoded!!.signatureBytes.toList())
        assertEquals("catalog-bytes".toByteArray().toList(), decoded.catalogBytes.toList())
    }

    @Test
    fun `catalog cache decode keeps catalog bytes containing newlines`() {
        val encoded = OfficialProviderRepository.encodeCatalogCache(
            "c2ln",
            "{\n\"schemaVersion\": 1\n}".toByteArray(),
        )
        val decoded = OfficialProviderRepository.decodeCatalogCache(encoded)
        assertNotNull(decoded)
        assertEquals(
            "{\n\"schemaVersion\": 1\n}".toByteArray().toList(),
            decoded!!.catalogBytes.toList(),
        )
    }

    @Test
    fun `catalog cache decode rejects garbage input`() {
        assertNull(OfficialProviderRepository.decodeCatalogCache(null))
        assertNull(OfficialProviderRepository.decodeCatalogCache(ByteArray(0)))
        assertNull(OfficialProviderRepository.decodeCatalogCache("no-newline-here".toByteArray()))
        assertNull(OfficialProviderRepository.decodeCatalogCache("\nempty-signature".toByteArray()))
        assertNull(
            OfficialProviderRepository.decodeCatalogCache("%%%not-base64\nabc".toByteArray()),
        )
    }
}
