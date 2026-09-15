/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.juren233.hyperlyricsenhanced.root.island

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@Suppress("UNUSED_PARAMETER")
class IslandRelayoutMethodResolverTest {
    @Test
    fun `selects zero argument calculate overload instead of inherited two argument overload`() {
        val resolved = IslandRelayoutMethodResolver.resolve(ContentView::class.java)!!

        assertEquals(IslandRelayoutEntry.CALCULATE_BIG_ISLAND_WIDTH, resolved.entry)
        assertEquals(0, resolved.method.parameterCount)
        assertEquals(ContentView::class.java, resolved.method.declaringClass)
    }

    @Test
    fun `prefers update entry over calculate entry`() {
        val resolved = IslandRelayoutMethodResolver.resolve(UpdateAndCalculate::class.java)!!

        assertEquals(IslandRelayoutEntry.UPDATE_BIG_ISLAND_VIEW_WIDTH, resolved.entry)
        assertEquals("updateBigIslandViewWidth", resolved.method.name)
    }

    @Test
    fun `calculate fallback keeps legacy return type compatibility`() {
        val resolved = IslandRelayoutMethodResolver.resolve(CalculateReturningBoolean::class.java)!!

        assertEquals(Boolean::class.javaPrimitiveType, resolved.method.returnType)
    }

    @Test
    fun `rejects host with only two argument calculate overload`() {
        assertNull(IslandRelayoutMethodResolver.resolve(OnlyTwoArgument::class.java))
    }

    @Test
    fun `rejects static zero argument entry`() {
        assertNull(IslandRelayoutMethodResolver.resolve(StaticUpdate::class.java))
    }

    open class OnlyTwoArgument {
        fun calculateBigIslandWidth(left: Any, right: Any) = Unit
    }

    class ContentView : OnlyTwoArgument() {
        fun calculateBigIslandWidth() = Unit
    }

    class UpdateAndCalculate {
        fun updateBigIslandViewWidth() = Unit
        fun calculateBigIslandWidth() = Unit
    }

    class CalculateReturningBoolean {
        fun calculateBigIslandWidth(): Boolean = true
    }

    class StaticUpdate private constructor() {
        companion object {
            @JvmStatic
            fun updateBigIslandViewWidth() = Unit
        }
    }
}
