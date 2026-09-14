/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.juren233.hyperlyricsenhanced.root.island

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class IslandWidthEventRebindGuardTest {
    @Before
    fun setUp() = IslandWidthEventRebindGuard.clearStateForTest()

    @After
    fun tearDown() = IslandWidthEventRebindGuard.clearStateForTest()

    @Test
    fun `accepts exact original dex method descriptors`() {
        assertTrue(
            IslandWidthEventMethodProfile.isDispatchMethod(
                name = "dispatchEvent",
                returnTypeName = "void",
                parameterTypeNames = listOf(
                    "miui.systemui.dynamicisland.event.DynamicIslandEvent",
                    "miui.systemui.dynamicisland.window.content.DynamicIslandContentView",
                ),
            )
        )
        assertTrue(
            IslandWidthEventMethodProfile.isDispatchTransitionMethod(
                name = "dispatchTransition",
                returnTypeName = "void",
                parameterTypeNames = listOf(
                    "miui.systemui.dynamicisland.anim.model.AnimTransitionType",
                    "miui.systemui.dynamicisland.window.content.DynamicIslandContentView",
                ),
            )
        )
        assertTrue(
            IslandWidthEventMethodProfile.isVisibleLottieScenesMethod(
                name = "visibleLottieScenesFor",
                returnTypeName = "java.util.Set",
                parameterTypeNames = listOf(
                    "miui.systemui.dynamicisland.anim.model.AnimTransitionType",
                ),
            )
        )
    }

    @Test
    fun `rejects aliases overloads and static methods`() {
        val dispatchParams = listOf(
            IslandWidthEventMethodProfile.EVENT_CLASS,
            IslandWidthEventMethodProfile.CONTENT_VIEW_CLASS,
        )
        assertFalse(
            IslandWidthEventMethodProfile.isDispatchMethod(
                "dispatch", "void", dispatchParams
            )
        )
        assertFalse(
            IslandWidthEventMethodProfile.isDispatchMethod(
                "dispatchEvent", "java.lang.Object", dispatchParams
            )
        )
        assertFalse(
            IslandWidthEventMethodProfile.isDispatchMethod(
                "dispatchEvent", "void", dispatchParams.reversed()
            )
        )
        assertFalse(
            IslandWidthEventMethodProfile.isDispatchMethod(
                "dispatchEvent", "void", dispatchParams, isStatic = true
            )
        )
        assertFalse(
            IslandWidthEventMethodProfile.isDispatchTransitionMethod(
                "dispatchTransition",
                "void",
                listOf(IslandWidthEventMethodProfile.TRANSITION_TYPE_CLASS),
            )
        )
        assertFalse(
            IslandWidthEventMethodProfile.isVisibleLottieScenesMethod(
                "visibleLottieScenesFor",
                "java.util.Collection",
                listOf(IslandWidthEventMethodProfile.TRANSITION_TYPE_CLASS),
            )
        )
        assertFalse(
            IslandWidthEventMethodProfile.isVisibleLottieScenesMethod(
                "visibleLottieScenesFor",
                "java.util.Set",
                listOf(IslandWidthEventMethodProfile.TRANSITION_TYPE_CLASS),
                isStatic = true,
            )
        )
    }

    @Test
    fun `scope recognizes only exact width event and identical target`() {
        val target = Any()
        val otherTarget = Any()

        assertFalse(
            IslandWidthEventRebindGuard.isScopedWidthEvent(
                IslandWidthEventMethodProfile.UPDATE_WIDTH_EVENT_CLASS,
                target,
            )
        )
        IslandWidthEventRebindGuard.aroundLyricWidthRelayout(target) {
            assertTrue(
                IslandWidthEventRebindGuard.isScopedWidthEvent(
                    IslandWidthEventMethodProfile.UPDATE_WIDTH_EVENT_CLASS,
                    target,
                )
            )
            assertFalse(
                IslandWidthEventRebindGuard.isScopedWidthEvent(
                    IslandWidthEventMethodProfile.UPDATE_WIDTH_EVENT_CLASS,
                    otherTarget,
                )
            )
            assertFalse(
                IslandWidthEventRebindGuard.isScopedWidthEvent(
                    "miui.systemui.dynamicisland.event.DynamicIslandEvent\$Update",
                    target,
                )
            )
        }
    }

    @Test
    fun `marks and consumes only big island transition from exact width event`() {
        val coordinator = Any()
        val otherCoordinator = Any()
        val target = Any()
        val transition = Any()

        assertFalse(
            IslandWidthEventRebindGuard.markWidthTransition(
                IslandWidthEventMethodProfile.BIG_ISLAND_CHANGED_CLASS,
                transition,
                target,
            )
        )
        IslandWidthEventRebindGuard.duringWidthEvent(coordinator, target) {
            assertFalse(
                IslandWidthEventRebindGuard.markWidthTransition(
                    "miui.systemui.dynamicisland.anim.model.AnimTransitionType\$SmallIslandChanged",
                    transition,
                    target,
                )
            )
            assertFalse(
                IslandWidthEventRebindGuard.markWidthTransition(
                    IslandWidthEventMethodProfile.BIG_ISLAND_CHANGED_CLASS,
                    transition,
                    Any(),
                )
            )
            assertTrue(
                IslandWidthEventRebindGuard.markWidthTransition(
                    IslandWidthEventMethodProfile.BIG_ISLAND_CHANGED_CLASS,
                    transition,
                    target,
                )
            )
        }

        assertFalse(
            IslandWidthEventRebindGuard.consumeWidthTransition(otherCoordinator, transition)
        )
        assertTrue(
            IslandWidthEventRebindGuard.consumeWidthTransition(coordinator, transition)
        )
        assertFalse(
            IslandWidthEventRebindGuard.consumeWidthTransition(coordinator, transition)
        )
    }

    @Test
    fun `transition tracking uses identity rather than equals`() {
        data class EqualTransition(val value: Int)

        val coordinator = Any()
        val target = Any()
        val marked = EqualTransition(1)
        val equalButDistinct = EqualTransition(1)

        IslandWidthEventRebindGuard.duringWidthEvent(coordinator, target) {
            assertTrue(
                IslandWidthEventRebindGuard.markWidthTransition(
                    IslandWidthEventMethodProfile.BIG_ISLAND_CHANGED_CLASS,
                    marked,
                    target,
                )
            )
        }
        assertFalse(
            IslandWidthEventRebindGuard.consumeWidthTransition(coordinator, equalButDistinct)
        )
        assertTrue(
            IslandWidthEventRebindGuard.consumeWidthTransition(coordinator, marked)
        )
    }

    @Test
    fun `nested lyric scope restores outer target`() {
        val outer = Any()
        val inner = Any()

        IslandWidthEventRebindGuard.aroundLyricWidthRelayout(outer) {
            IslandWidthEventRebindGuard.aroundLyricWidthRelayout(inner) {
                assertTrue(
                    IslandWidthEventRebindGuard.isScopedWidthEvent(
                        IslandWidthEventMethodProfile.UPDATE_WIDTH_EVENT_CLASS,
                        inner,
                    )
                )
                assertFalse(
                    IslandWidthEventRebindGuard.isScopedWidthEvent(
                        IslandWidthEventMethodProfile.UPDATE_WIDTH_EVENT_CLASS,
                        outer,
                    )
                )
            }
            assertTrue(
                IslandWidthEventRebindGuard.isScopedWidthEvent(
                    IslandWidthEventMethodProfile.UPDATE_WIDTH_EVENT_CLASS,
                    outer,
                )
            )
        }
    }
}
