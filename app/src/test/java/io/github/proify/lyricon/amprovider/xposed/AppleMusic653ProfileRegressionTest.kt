/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 */
package io.github.proify.lyricon.amprovider.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppleMusic653ProfileRegressionTest {
    private val version = AppleMusicVersion("6.5.3", 1599L)
    private fun targets(point: AppleMusicHookPoint) = AppleMusicHookProfiles.exactTargets(version, point)
    private fun target(point: AppleMusicHookPoint) = targets(point).single()

    @Test
    fun `1599 has exact targets for every hook group without losing multi-class groups`() {
        AppleMusicHookPoint.entries.forEach { point ->
            assertTrue("Missing exact 1599 group: $point", targets(point).isNotEmpty())
        }
        assertEquals(2, targets(AppleMusicHookPoint.LYRICS_RECYCLER_ADAPTER).size)
        assertEquals(8, targets(AppleMusicHookPoint.COLLECTION_SURFACE_CLASSES).size)
        assertEquals(6, targets(AppleMusicHookPoint.ARTIST_SURFACE_CLASSES).size)
        assertEquals(5, targets(AppleMusicHookPoint.DATA_BINDING_RUNTIME_CLASSES).size)
    }

    @Test
    fun `queue and history use raw DEX owners rather than repurposed Y8 and Z8 classes`() {
        val submit = target(AppleMusicHookPoint.IN_APP_QUEUE_ADAPTER_SUBMIT)
        val bind = target(AppleMusicHookPoint.IN_APP_QUEUE_ADAPTER_BIND)
        assertEquals("a9.a", submit.className)
        assertEquals("B", submit.methodName)
        assertEquals(listOf("java.util.List"), submit.parameterTypeNames)
        assertEquals("a9.a", bind.className)
        assertEquals(listOf("androidx.recyclerview.widget.RecyclerView\$D", "int"), bind.parameterTypeNames)
        assertEquals("void", bind.returnTypeName)
        assertEquals(false, bind.isStatic)
        assertEquals("b9.d", target(AppleMusicHookPoint.IN_APP_HISTORY_UPDATE)
            .runtimeMemberName(AppleMusicRuntimeMember.QUEUE_HISTORY_ENTRY_CLASS_NAME))
    }

    @Test
    fun `catalog and playback conversion reject same-name different-business targets`() {
        assertEquals("com.apple.android.music.player.P", target(AppleMusicHookPoint.APPLE_PLAYER_UTIL_CLASS).className)
        assertEquals("v", target(AppleMusicHookPoint.MEDIA_API_REPOSITORY_HOLDER_CLASS)
            .runtimeMemberName(AppleMusicRuntimeMember.MEDIA_API_DIRECT_QUERY_METHOD))
        assertEquals("com.apple.android.music.i1", target(AppleMusicHookPoint.LISTEN_NOW_MODEL).className)
        val network = target(AppleMusicHookPoint.LYRICS_NETWORK_REQUEST)
        assertEquals("Hg.c", network.parameterTypeNames!!.last())
        assertFalse(network.allowFirstMatch)
    }

    @Test
    fun `lyrics fields follow binding and active adapter instead of a word-only adapter`() {
        val ui = target(AppleMusicHookPoint.LYRICS_UI_ON_CREATE_VIEW)
        assertEquals("g0", ui.runtimeMemberName(AppleMusicRuntimeMember.LYRICS_UI_BINDING_FIELD))
        assertEquals("Y", ui.runtimeMemberName(AppleMusicRuntimeMember.LYRICS_UI_BINDING_RECYCLER_FIELD))
        assertEquals("i0", ui.runtimeMemberName(AppleMusicRuntimeMember.LYRICS_UI_ADAPTER_FIELD))
        assertEquals("h1", ui.runtimeMemberName(AppleMusicRuntimeMember.LYRICS_UI_VIEW_MODEL_FIELD))
        val binding = targets(AppleMusicHookPoint.DATA_BINDING_RUNTIME_CLASSES).first()
        assertEquals("A", binding.runtimeMemberName(AppleMusicRuntimeMember.DATA_BINDING_INVALIDATE_METHOD))
    }

    @Test
    fun `collection and artist models retain their actual semantic text fields`() {
        val collection = targets(AppleMusicHookPoint.COLLECTION_SURFACE_CLASSES).associateBy {
            it.runtimeMemberName(AppleMusicRuntimeMember.COLLECTION_RUNTIME_ROLE)
        }
        assertEquals("com.apple.android.music.i", collection.getValue("album_header_model").className)
        assertEquals("m6.b", collection.getValue("album_row_model").className)
        assertEquals("m6.d", collection.getValue("playlist_row_model").className)
        assertEquals("N", collection.getValue("playlist_row_model")
            .runtimeMemberName(AppleMusicRuntimeMember.COLLECTION_PLAYLIST_SUBTITLE_FIELD))
        val artist = targets(AppleMusicHookPoint.ARTIST_SURFACE_CLASSES).associateBy {
            it.runtimeMemberName(AppleMusicRuntimeMember.ARTIST_RUNTIME_ROLE)
        }
        assertEquals("com.apple.android.music.e1", artist.getValue("top_song_model").className)
        assertEquals("N", artist.getValue("top_song_model")
            .runtimeMemberName(AppleMusicRuntimeMember.ARTIST_TOP_SONG_SUBTITLE_FIELD))
        assertEquals("com.apple.android.music.S", artist.getValue("header_model").className)
    }

    @Test
    fun `1599 overrides do not mutate the verified 651 and 652 profiles`() {
        for (old in listOf(AppleMusicVersion("6.5.1", 1583L), AppleMusicVersion("6.5.2", 1586L))) {
            fun candidate(point: AppleMusicHookPoint) = AppleMusicHookProfiles.candidates(old, point)
                .first { it.className == "Y8.a" }
            assertEquals("B", candidate(AppleMusicHookPoint.IN_APP_QUEUE_ADAPTER_SUBMIT).methodName)
        }
        assertEquals("com.apple.android.music.l1", AppleMusicHookProfiles.candidates(
            AppleMusicVersion("6.5.0", 1580L), AppleMusicHookPoint.LISTEN_NOW_MODEL).first().className)
        val sparse652 = AppleMusicVersion("6.5.2", 1586L)
        assertEquals("com.apple.android.music.player.O", AppleMusicHookProfiles.candidates(
            sparse652, AppleMusicHookPoint.APPLE_PLAYER_UTIL_CLASS).first().className)
        assertEquals("Y8.a", AppleMusicHookProfiles.candidates(
            sparse652, AppleMusicHookPoint.IN_APP_QUEUE_ADAPTER_SUBMIT).first().className)
        assertTrue(AppleMusicHookProfiles.candidates(sparse652, AppleMusicHookPoint.COLLECTION_SURFACE_CLASSES)
            .none { it.className.startsWith("m6.") })
        val old = AppleMusicVersion("6.5.1", 1583L)
        assertEquals("com.apple.android.music.player.O", AppleMusicHookProfiles.exactTargets(old,
            AppleMusicHookPoint.APPLE_PLAYER_UTIL_CLASS).single().className)
        assertEquals("j1", AppleMusicHookProfiles.exactTargets(old, AppleMusicHookPoint.LYRICS_UI_ON_CREATE_VIEW)
            .single().runtimeMemberName(AppleMusicRuntimeMember.LYRICS_UI_VIEW_MODEL_FIELD))
    }
}
