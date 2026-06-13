package com.kuroshimira.nyanpasu.wallpaper

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 串行化 home/lock 文件写入与系统 apply，避免 urgent / 自动 / 预取提升并发竞态。 */
object WallpaperWriteGuard {

    private val mutex = Mutex()

    fun isWriteInProgress(): Boolean = mutex.isLocked

    suspend fun <T> withWriteLock(block: suspend () -> T): T = mutex.withLock { block() }
}
