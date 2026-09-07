/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.juren233.hyperlyricsenhanced.root.mediacard.notification

import java.lang.reflect.Constructor
import java.lang.reflect.Field

/**
 * SystemUI dozeTimeTick 脱糖 Runnable 的实例化策略。
 *
 * 两种已在原始 DEX 中核实的形态（以 dexdump 为准，不以反编译器显示为准）：
 * - 旧脱糖：合成 Runnable 声明 `(DozeServiceHost)` 构造器，捕获在构造时完成；
 * - 新脱糖（issue #16 报告者 HyperOS 4.0.0.25 移植包 classes2.dex 的
 *   `Lcom/android/systemui/doze/DozeUi$$ExternalSyntheticLambda0;`：PUBLIC FINAL
 *   SYNTHETIC、公有字段 `f$0 : DozeServiceHost`、`run()V`，构造器为默认无参，
 *   捕获方在调用点为 `f$0` 赋值）：本模块同样先写 `f$0` 再把 runnable 交给
 *   SystemUI 执行，跨线程可见性由后续 Handler 投递的同步边界保证。
 *
 * 旧模式优先：命中即保持既有行为不变；两种模式都不满足时按原样抛出，
 * 由上层记录"初始化 AOD 原生刷新接口失败"。
 */
internal interface DozeTickRunnableFactory {
    fun create(host: Any): Runnable

    companion object {
        const val CAPTURED_RECEIVER_FIELD = "f\$0"

        fun resolve(tickRunnableClass: Class<*>, hostClass: Class<*>): DozeTickRunnableFactory {
            val legacy = runCatching {
                tickRunnableClass.getDeclaredConstructor(hostClass)
            }.getOrNull()
            if (legacy != null) {
                legacy.isAccessible = true
                return LegacyConstructor(legacy)
            }
            val noArg = runCatching {
                tickRunnableClass.getDeclaredConstructor()
            }.getOrElse {
                throw IllegalStateException(
                    "脱糖 Runnable ${tickRunnableClass.name} 既无 " +
                        "(${hostClass.name}) 构造器也无默认构造器",
                    it,
                )
            }
            val captured = runCatching {
                tickRunnableClass.getDeclaredField(CAPTURED_RECEIVER_FIELD)
            }.getOrElse {
                throw IllegalStateException(
                    "脱糖 Runnable ${tickRunnableClass.name} 既无 " +
                        "(${hostClass.name}) 构造器也无 $CAPTURED_RECEIVER_FIELD 捕获字段",
                    it,
                )
            }
            require(captured.type.isAssignableFrom(hostClass)) {
                "脱糖 Runnable 的 $CAPTURED_RECEIVER_FIELD 字段类型 " +
                    "${captured.type.name} 与 ${hostClass.name} 不兼容"
            }
            noArg.isAccessible = true
            captured.isAccessible = true
            return CapturedField(noArg, captured)
        }
    }

    private class LegacyConstructor(
        private val constructor: Constructor<*>,
    ) : DozeTickRunnableFactory {
        override fun create(host: Any): Runnable = constructor.newInstance(host) as Runnable
    }

    private class CapturedField(
        private val constructor: Constructor<*>,
        private val field: Field,
    ) : DozeTickRunnableFactory {
        override fun create(host: Any): Runnable =
            (constructor.newInstance() as Runnable).also { field.set(it, host) }
    }
}
