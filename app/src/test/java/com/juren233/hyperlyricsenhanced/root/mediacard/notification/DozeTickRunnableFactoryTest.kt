/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.juren233.hyperlyricsenhanced.root.mediacard.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DozeTickRunnableFactoryTest {
    class Host

    class LegacyRunnable(host: Host) : Runnable {
        private val captured: Host = host

        override fun run() {
            lastCaptured = captured
        }

        companion object {
            var lastCaptured: Host? = null
        }
    }

    // 新 D8 脱糖形态（报告者移植包 dexdump 证据）：默认构造器 + 公有 f$0 捕获字段。
    class NewDesugarRunnable : Runnable {
        @JvmField
        var `f$0`: Host? = null

        override fun run() {
            lastObserved = `f$0`
        }

        companion object {
            var lastObserved: Host? = null
        }
    }

    class PlainRunnable : Runnable {
        override fun run() {}
    }

    @Test
    fun `locks the binary-verified double-dollar desugar class name`() {
        assertEquals(
            "com.android.systemui.doze.DozeUi\$\$ExternalSyntheticLambda0",
            NotificationMediaAodLyricHooker.DOZE_TICK_RUNNABLE_CLASS,
        )
        // jadx 风格的单 $ 别名是反编译显示名，不得混入运行时标识。
        assertFalse(
            NotificationMediaAodLyricHooker.DOZE_TICK_RUNNABLE_CLASS
                .contains("doze.DozeUi\$External"),
        )
    }

    @Test
    fun `resolves the legacy constructor pattern`() {
        val host = Host()

        val factory = DozeTickRunnableFactory.resolve(LegacyRunnable::class.java, Host::class.java)
        val runnable = factory.create(host)

        assertTrue(runnable is LegacyRunnable)
        runnable.run()
        assertSame(host, LegacyRunnable.lastCaptured)
    }

    @Test
    fun `resolves the new desugar pattern via the captured field`() {
        val host = Host()

        val factory = DozeTickRunnableFactory.resolve(
            NewDesugarRunnable::class.java,
            Host::class.java,
        )
        val runnable = factory.create(host)

        assertTrue(runnable is NewDesugarRunnable)
        assertSame(host, (runnable as NewDesugarRunnable).`f$0`)
        runnable.run()
        assertSame(host, NewDesugarRunnable.lastObserved)
    }

    @Test
    fun `reports a clear error when neither pattern is available`() {
        val exception = assertThrows(IllegalStateException::class.java) {
            DozeTickRunnableFactory.resolve(PlainRunnable::class.java, Host::class.java)
        }
        assertTrue(exception.message!!.contains("f\$0"))
    }
}
