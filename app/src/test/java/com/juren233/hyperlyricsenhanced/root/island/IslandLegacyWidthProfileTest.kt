/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.juren233.hyperlyricsenhanced.root.island

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandLegacyWidthProfileTest {
    @Test
    fun `recognizes exact legacy calculation and layout descriptors`() {
        assertTrue(
            IslandLegacyWidthProfile.isCalculateMethod(
                declaringClassName = IslandLegacyWidthProfile.BASE_CONTENT_VIEW_CLASS,
                name = IslandLegacyWidthProfile.CALCULATE_METHOD,
                returnTypeName = Void.TYPE.name,
                parameterTypeNames = listOf("android.view.View", "android.view.View"),
            ),
        )
        assertTrue(
            IslandLegacyWidthProfile.isUpdateLayoutMethod(
                declaringClassName = IslandLegacyWidthProfile.CONTENT_VIEW_CLASS,
                name = IslandLegacyWidthProfile.UPDATE_LAYOUT_METHOD,
                returnTypeName = Void.TYPE.name,
                parameterTypeNames = emptyList(),
            ),
        )
    }

    @Test
    fun `rejects aliases overloads and static legacy methods`() {
        assertFalse(
            IslandLegacyWidthProfile.isCalculateMethod(
                declaringClassName = "miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentView_",
                name = IslandLegacyWidthProfile.CALCULATE_METHOD,
                returnTypeName = Void.TYPE.name,
                parameterTypeNames = listOf("android.view.View", "android.view.View"),
            ),
        )
        assertFalse(
            IslandLegacyWidthProfile.isCalculateMethod(
                declaringClassName = IslandLegacyWidthProfile.BASE_CONTENT_VIEW_CLASS,
                name = IslandLegacyWidthProfile.CALCULATE_METHOD,
                returnTypeName = Void.TYPE.name,
                parameterTypeNames = emptyList(),
            ),
        )
        assertFalse(
            IslandLegacyWidthProfile.isUpdateLayoutMethod(
                declaringClassName = IslandLegacyWidthProfile.CONTENT_VIEW_CLASS,
                name = IslandLegacyWidthProfile.UPDATE_LAYOUT_METHOD,
                returnTypeName = Void.TYPE.name,
                parameterTypeNames = emptyList(),
                isStatic = true,
            ),
        )
    }

    @Test
    fun `recognizes only exact legacy field owners and primitive types`() {
        assertTrue(
            IslandLegacyWidthProfile.isRequiredField(
                declaringClassName = IslandLegacyWidthProfile.BASE_CONTENT_VIEW_CLASS,
                name = IslandLegacyWidthProfile.MAX_WIDTH_FIELD,
                typeName = Float::class.javaPrimitiveType!!.name,
            ),
        )
        assertTrue(
            IslandLegacyWidthProfile.isRequiredField(
                declaringClassName = IslandLegacyWidthProfile.BASE_CONTENT_VIEW_CLASS,
                name = IslandLegacyWidthProfile.LEFT_WIDTH_FIELD,
                typeName = Int::class.javaPrimitiveType!!.name,
            ),
        )
        assertFalse(
            IslandLegacyWidthProfile.isRequiredField(
                declaringClassName = IslandLegacyWidthProfile.CONTENT_VIEW_CLASS,
                name = IslandLegacyWidthProfile.LEFT_WIDTH_FIELD,
                typeName = Int::class.javaPrimitiveType!!.name,
            ),
        )
        assertFalse(
            IslandLegacyWidthProfile.isRequiredField(
                declaringClassName = IslandLegacyWidthProfile.BASE_CONTENT_VIEW_CLASS,
                name = IslandLegacyWidthProfile.LEFT_WIDTH_FIELD,
                typeName = "java.lang.Integer",
            ),
        )
    }

    @Test
    fun `reconstructs centered unlocked geometry from native state`() {
        val geometry = IslandLegacyWidthProfile.unlockedGeometry(
            nativeWidth = 714,
            nativeX = 243,
            cutoutWidth = 68,
            measuredLeftWidth = 210,
            measuredRightWidth = 388,
        )

        assertEquals(
            IslandLegacyUnlockedGeometry(
                viewWidth = 844,
                leftWidth = 388,
                rightWidth = 388,
                viewX = 178,
            ),
            geometry,
        )
    }

    @Test
    fun `does not rewrite an unclipped or invalid legacy result`() {
        assertNull(
            IslandLegacyWidthProfile.unlockedGeometry(
                nativeWidth = 844,
                nativeX = 178,
                cutoutWidth = 68,
                measuredLeftWidth = 210,
                measuredRightWidth = 388,
            ),
        )
        assertNull(
            IslandLegacyWidthProfile.unlockedGeometry(
                nativeWidth = 714,
                nativeX = 243,
                cutoutWidth = 68,
                measuredLeftWidth = 0,
                measuredRightWidth = 0,
            ),
        )
    }
}
