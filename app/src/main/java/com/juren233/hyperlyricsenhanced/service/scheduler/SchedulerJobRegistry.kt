package com.juren233.hyperlyricsenhanced.service.scheduler

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 调度协程的互斥注册表。
 *
 * startSchedulers 会从元数据线程（processSyncData）与取词完成回调（IO 线程）并发进入；
 * 若“取消旧任务 + 记录新任务”不是原子的，先启动的协程会失去引用、永远无法取消，
 * 与新协程以 150ms 相位差交替分发（Issue #34 超级岛歌词卡死的根因）。
 *
 * [launchReplacing] 在锁内完成“取消旧引用 + 注册新任务（懒启动）+ 放行”，
 * 循环体每轮先经 [isCurrent] 自查：一旦被更新的启动或 [clear] 取代立即退出，
 * 历史竞态遗留的孤儿也能在下一轮自愈。
 */
internal class SchedulerJobRegistry {
    private val lock = Any()

    @Volatile
    private var current: Job? = null

    /** 原子地取消旧任务并以新任务取代；返回新任务以便调用方按需管理。 */
    fun launchReplacing(scope: CoroutineScope, block: suspend CoroutineScope.() -> Unit): Job =
        synchronized(lock) {
            current?.cancel()
            val job = scope.launch(start = CoroutineStart.LAZY, block = block)
            current = job
            job.start()
            job
        }

    /** 清除引用并取消任务；已进入循环体的旧任务会经 [isCurrent] 自查退出。 */
    fun clear() {
        synchronized(lock) {
            current?.cancel()
            current = null
        }
    }

    /** [job] 是否仍是注册表当前引用的任务；循环体每轮必须先自查，失引用即退出。 */
    fun isCurrent(job: Job?): Boolean = synchronized(lock) { current === job }

    /** 引用为空或任务已结束/取消时需要重新启动。 */
    fun needsLaunch(): Boolean = synchronized(lock) { current == null || current?.isActive != true }
}
