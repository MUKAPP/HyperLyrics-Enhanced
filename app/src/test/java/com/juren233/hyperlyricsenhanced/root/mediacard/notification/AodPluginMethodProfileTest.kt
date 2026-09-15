/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.juren233.hyperlyricsenhanced.root.mediacard.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("UNUSED_PARAMETER")
class AodPluginMethodProfileTest {
    @Test
    fun `accepts known three argument callback`() {
        val resolved = AodPluginMethodProfile.resolveContentLayout(KnownThree::class.java)

        assertEquals(AodContentLayoutResolutionSource.KNOWN_THREE_ARGUMENT, resolved.source)
        assertEquals(listOf(3), resolved.methods.map { it.parameterCount })
    }

    @Test
    fun `accepts verified two argument callback from user package`() {
        val resolved = AodPluginMethodProfile.resolveContentLayout(KnownTwo::class.java)

        assertEquals(AodContentLayoutResolutionSource.KNOWN_TWO_ARGUMENT, resolved.source)
        assertEquals(
            listOf(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType),
            resolved.methods.single().parameterTypes.toList(),
        )
    }

    @Test
    fun `hooks both known callbacks when host exposes both`() {
        val resolved = AodPluginMethodProfile.resolveContentLayout(KnownBoth::class.java)

        assertEquals(AodContentLayoutResolutionSource.KNOWN_BOTH, resolved.source)
        assertEquals(listOf(3, 2), resolved.methods.map { it.parameterCount })
    }

    @Test
    fun `missing optional callback does not fail lifecycle contract`() {
        val resolved = AodPluginMethodProfile.resolve(LifecycleWithoutContentCallback::class.java)

        assertEquals(AodContentLayoutResolutionSource.UNAVAILABLE, resolved.contentLayout.source)
        assertEquals(4, resolved.hookMethods.size)
    }

    @Test
    fun `accepts one future same-name instance void callback`() {
        val resolved = AodPluginMethodProfile.resolveContentLayout(FutureUnique::class.java)

        assertEquals(AodContentLayoutResolutionSource.UNIQUE_COMPATIBLE, resolved.source)
        assertEquals(String::class.java, resolved.methods.single().parameterTypes.single())
    }

    @Test
    fun `rejects ambiguous future callbacks`() {
        assertThrows(IllegalArgumentException::class.java) {
            AodPluginMethodProfile.resolveContentLayout(FutureAmbiguous::class.java)
        }
    }

    @Test
    fun `ignores static and non-void same-name methods`() {
        assertTrue(
            AodPluginMethodProfile.resolveContentLayout(StaticOnly::class.java).methods.isEmpty(),
        )
        assertTrue(
            AodPluginMethodProfile.resolveContentLayout(NonVoidOnly::class.java).methods.isEmpty(),
        )
    }

    @Test
    fun `keeps exact original dex class name`() {
        assertEquals("com.miui.aod.AODView", NotificationMediaAodLyricHooker.AOD_PLUGIN_VIEW_CLASS)
    }

    class KnownThree {
        fun onAodContentLayoutChange(width: Int, height: Int, animated: Boolean) = Unit
    }

    class KnownTwo {
        fun onAodContentLayoutChange(width: Int, height: Int) = Unit
    }

    class KnownBoth {
        fun onAodContentLayoutChange(width: Int, height: Int, animated: Boolean) = Unit
        fun onAodContentLayoutChange(width: Int, height: Int) = Unit
        fun onAodContentLayoutChange(reason: String) = Unit
    }

    class LifecycleWithoutContentCallback {
        fun makeNormalPanel() = Unit
        fun onAttachedToWindow() = Unit
        fun onDetachedFromWindow() = Unit
        fun onUpdatePositionTimer() = Unit
    }

    class FutureUnique {
        fun onAodContentLayoutChange(reason: String) = Unit
    }

    class FutureAmbiguous {
        fun onAodContentLayoutChange(reason: String) = Unit
        fun onAodContentLayoutChange(reason: Long) = Unit
    }

    class StaticOnly private constructor() {
        companion object {
            @JvmStatic
            fun onAodContentLayoutChange(reason: String) = Unit
        }
    }

    class NonVoidOnly {
        fun onAodContentLayoutChange(reason: String): Int = reason.length
    }
}
