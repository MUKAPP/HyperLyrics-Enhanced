package com.juren233.hyperlyricsenhanced.service.scheduler

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Issue #34 回归：并发双启动不得留下常驻孤儿 ticker；
 * 被取代/清除的任务必须能凭 isCurrent 自查退出。
 */
class SchedulerJobRegistryTest {

    @Test
    fun 空注册表需要启动() {
        val registry = SchedulerJobRegistry()
        assertTrue(registry.needsLaunch())
    }

    @Test
    fun 引用存活期间不需要启动_结束后恢复需要() = runBlocking {
        val registry = SchedulerJobRegistry()
        val gate = CompletableDeferred<Unit>()
        val scope = CoroutineScope(Dispatchers.Default)
        val job = registry.launchReplacing(scope) { gate.await() }
        assertFalse(registry.needsLaunch())

        gate.complete(Unit)
        job.join()
        assertTrue(registry.needsLaunch())
        scope.cancel()
    }

    @Test
    fun clear后引用清空且循环任务自退() = runBlocking {
        val registry = SchedulerJobRegistry()
        val started = CompletableDeferred<Unit>()
        val iterations = AtomicInteger(0)
        val scope = CoroutineScope(Dispatchers.Default)

        val job = registry.launchReplacing(scope) {
            val self = coroutineContext[Job]
            started.complete(Unit)
            while (isActive) {
                if (!registry.isCurrent(self)) break
                iterations.incrementAndGet()
                delay(10)
            }
        }
        started.await()
        withTimeout(2000) { while (iterations.get() == 0) delay(5) }

        registry.clear()
        withTimeout(2000) { job.join() }
        assertFalse(registry.isCurrent(job))
        assertTrue(registry.needsLaunch())
        scope.cancel()
    }

    @Test
    fun 未被注册引用的孤儿首轮即自退() = runBlocking {
        val registry = SchedulerJobRegistry()
        val iterations = AtomicInteger(0)

        // 模拟竞态孤儿：协程在运行，但从未被注册表引用。
        val orphan = launch(Dispatchers.Default) {
            val self = coroutineContext[Job]
            while (isActive) {
                if (!registry.isCurrent(self)) break
                iterations.incrementAndGet()
                delay(10)
            }
        }
        withTimeout(2000) { orphan.join() }
        assertEquals(0, iterations.get())
    }

    @Test
    fun 被取代的任务自退且新任务成为正主() = runBlocking {
        val registry = SchedulerJobRegistry()
        val firstStarted = CompletableDeferred<Unit>()
        val iterations = AtomicInteger(0)
        val scope = CoroutineScope(Dispatchers.Default)

        val first = registry.launchReplacing(scope) {
            val self = coroutineContext[Job]
            firstStarted.complete(Unit)
            while (isActive) {
                if (!registry.isCurrent(self)) break
                iterations.incrementAndGet()
                delay(10)
            }
        }
        firstStarted.await()
        withTimeout(2000) { while (iterations.get() == 0) delay(5) }

        val successorStarted = CompletableDeferred<Unit>()
        val successor = registry.launchReplacing(scope) {
            successorStarted.complete(Unit)
            delay(10_000)
        }
        successorStarted.await()

        withTimeout(2000) { first.join() }
        assertFalse(registry.isCurrent(first))
        assertTrue(registry.isCurrent(successor))
        assertTrue(iterations.get() >= 1)
        successor.cancel()
        scope.cancel()
    }

    @Test
    fun 并发双启动后仅一个正主存活_另一个自退() = runBlocking {
        val registry = SchedulerJobRegistry()
        val scope = CoroutineScope(Dispatchers.Default)

        // 两个线程同时 launchReplacing：锁内原子替换保证只有一个成为正主。
        val jobs = (1..2).map {
            async(Dispatchers.IO) {
                registry.launchReplacing(scope) {
                    val self = coroutineContext[Job]
                    while (isActive) {
                        if (!registry.isCurrent(self)) break
                        delay(10)
                    }
                }
            }
        }.map { it.await() }

        val currentAlive = jobs.count { registry.isCurrent(it) && it.isActive }
        assertEquals(1, currentAlive)

        // 两个任务都必须结束：正主之后由 clear/cancel 收尾时退出，另一个一轮内自退。
        registry.clear()
        withTimeout(3000) { jobs.forEach { it.join() } }
        scope.cancel()
    }
}
