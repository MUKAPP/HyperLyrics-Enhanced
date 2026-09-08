/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed

import com.juren233.hyperlyricsenhanced.common.RootConstants
import io.github.proify.lyricon.amprovider.xposed.hooks.AppleCellularDataSettingsScope
import io.github.proify.lyricon.amprovider.xposed.hooks.forceAppleCellularAvailability
import org.junit.Assert.*
import org.junit.Test

class AppleCellularDataSettingsTest {
    @Test
    fun `availability is always true when enabled and original when disabled`() {
        listOf(false, true).forEach { original ->
            assertEquals(true, forceAppleCellularAvailability(true, original))
            assertEquals(original, forceAppleCellularAvailability(false, original))
        }
        // No one-shot allowance or stale enabled state across subsequent calls.
        repeat(3) { assertEquals(true, forceAppleCellularAvailability(true, false)) }
        assertEquals(false, forceAppleCellularAvailability(false, false))
        assertEquals(true, forceAppleCellularAvailability(true, false))
    }

    @Test
    fun `availability target is the verified concrete instance method`() {
        val target = AppleMusicHookProfiles.exactTargets(
            AppleMusicVersion("6.5.2", 1586L), AppleMusicHookPoint.CELLULAR_AVAILABILITY,
        ).single()
        assertEquals(
            "com.apple.android.music.playback.connectivity.FuseConnectivityChecker",
            target.className,
        )
        assertFalse(target.className.startsWith("defpackage."))
        assertNotEquals("com.apple.android.music.playback.connectivity.ConnectivityChecker", target.className)
        assertEquals("isCellularAvailable", target.methodName)
        assertEquals(0, target.parameterCount)
        assertEquals(emptyList<String>(), target.parameterTypeNames)
        assertEquals("boolean", target.returnTypeName)
        assertEquals(false, target.isStatic)
        assertFalse(target.includeSynthetic)
    }

    @Test
    fun `setting defaults off and does not override outside settings`() {
        assertFalse(RootConstants.DEFAULT_HOOK_APPLE_MUSIC_FORCE_CELLULAR_DATA_ENTRY)
        val scope = AppleCellularDataSettingsScope()
        assertFalse(scope.consume())
        scope.enter(false)
        assertFalse(scope.consume())
        scope.exit()
        assertFalse(scope.consume())
    }

    @Test
    fun `enabled rebuild overrides only category gate not subsequent network checks`() {
        val scope = AppleCellularDataSettingsScope()
        repeat(2) {
            scope.enter(true)
            assertTrue(scope.consume())
            assertFalse(scope.consume())
            scope.exit()
            assertFalse(scope.consume())
        }
        scope.enter(false)
        assertFalse(scope.consume())
        scope.exit()
    }

    @Test
    fun `nested disabled rebuild and other threads do not inherit allowance`() {
        val scope = AppleCellularDataSettingsScope()
        scope.enter(true)
        scope.enter(false)
        assertFalse(scope.consume())
        scope.exit()
        var otherThreadResult = true
        Thread { otherThreadResult = scope.consume() }.apply { start(); join() }
        assertFalse(otherThreadResult)
        assertTrue(scope.consume())
        scope.exit()
    }

    @Test
    fun `scope cleanup before gate prevents later overrides`() {
        val scope = AppleCellularDataSettingsScope()
        try {
            scope.enter(true)
        } finally {
            scope.exit()
        }
        assertFalse(scope.consume())
    }

    @Test
    fun `1586 targets preserve original binary names and full descriptors`() {
        val version = AppleMusicVersion("6.5.2", 1586L)
        val build = AppleMusicHookProfiles.exactTargets(
            version, AppleMusicHookPoint.SETTINGS_DATA_CATEGORY_BUILD,
        ).single()
        assertEquals("com.apple.android.music.settings.fragment.SettingsFragment", build.className)
        assertEquals("t1", build.methodName)
        assertEquals(emptyList<String>(), build.parameterTypeNames)
        assertEquals("void", build.returnTypeName)
        assertEquals(false, build.isStatic)
        assertFalse(build.includeSynthetic)
        val sim = AppleMusicHookProfiles.exactTargets(
            version, AppleMusicHookPoint.SETTINGS_CELLULAR_SIM_CHECK,
        ).single()
        assertEquals("La.c", sim.className)
        assertNotEquals("la.c", sim.className)
        assertFalse(sim.className.startsWith("defpackage."))
        assertEquals("e", sim.methodName)
        assertEquals(listOf("android.content.Context"), sim.parameterTypeNames)
        assertEquals("boolean", sim.returnTypeName)
        assertEquals(true, sim.isStatic)
        assertFalse(sim.includeSynthetic)
    }
}
