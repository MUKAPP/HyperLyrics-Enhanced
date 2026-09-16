/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.juren233.hyperlyricsenhanced.root.source

import java.lang.reflect.Modifier
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks the synchronization contract without constructing Android Views or a Handler. */
class RootLyricSinkThreadSafetyTest {
    @Test
    fun `playback snapshots are visible across Binder and main threads`() {
        val state = RootLyricSink::class.java.getDeclaredField("playbackActive")
        assertTrue(Modifier.isVolatile(state.modifiers))
    }

    @Test
    fun `pause resume stop and position writes use the same sink monitor`() {
        val sink = RootLyricSink::class.java
        val methods = listOf(
            sink.getDeclaredMethod("onStop"),
            sink.getDeclaredMethod("onPlaybackStateChanged", Boolean::class.javaPrimitiveType),
            sink.getDeclaredMethod("onPositionChanged", Long::class.javaPrimitiveType),
            sink.getDeclaredMethod("onSeekTo", Long::class.javaPrimitiveType),
        )
        methods.forEach { method ->
            assertTrue("${method.name} must serialize bridge state writes", Modifier.isSynchronized(method.modifiers))
        }
    }
}
