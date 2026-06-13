package com.kuroshimira.nyanpasu.wallpaper

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.ArrayDeque

/** 主屏 / 锁屏壁纸撤销栈：文件落盘 + SharedPreferences 持久化。 */
object WallpaperHistory {

    private const val TAG = "WallpaperHistory"
    private const val PREFS_STACK = "HISTORY_STACK"
    private const val MAX_ENTRIES = 5
    private val RETENTION_MS = 7L * 24 * 60 * 60 * 1000

    private data class Entry(
        val id: Long,
        val homeState: Int,
        val lockState: Int,
    )

    private val stack = ArrayDeque<Entry>()
    private var loaded = false
    private val lock = Any()

    enum class UndoResult {
        Restored,
        Empty,
        FileMissing,
        ApplyFailed,
    }

    fun ensureLoaded(context: Context) {
        synchronized(lock) {
            if (loaded) return
            stack.clear()
            val raw = WallpaperPrefs.prefs(context).getString(PREFS_STACK, "") ?: ""
            if (raw.isNotEmpty()) {
                raw.split(",")
                    .mapNotNull { parseEntry(it.trim()) }
                    .forEach { stack.addLast(it) }
            }
            loaded = true
        }
    }

    fun backup(context: Context) {
        val prefs = WallpaperPrefs.prefs(context)
        val (homeState, lockState) = WallpaperPrefs.readHomeLockState(prefs)
        backup(context, homeState, lockState)
    }

    fun backup(context: Context, homeState: Int, lockState: Int) {
        synchronized(lock) {
            ensureLoaded(context)
            val filesDir = context.filesDir
            val id = System.currentTimeMillis()
            var backed = false

            val home = WallpaperFiles.homeFile(context)
            if (homeState > 0 && home.exists() && home.length() > 0L) {
                home.copyTo(homeBackupFile(filesDir, id), overwrite = true)
                backed = true
            }

            val lockFile = WallpaperFiles.lockFile(context)
            if (lockState == 2 && lockFile.exists() && lockFile.length() > 0L) {
                lockFile.copyTo(lockBackupFile(filesDir, id), overwrite = true)
                backed = true
            } else if (lockState >= 1 && homeState > 0 && home.exists() && home.length() > 0L) {
                home.copyTo(lockBackupFile(filesDir, id), overwrite = true)
                backed = true
            }

            if (!backed) return

            stack.addLast(Entry(id, homeState, lockState))
            while (stack.size > MAX_ENTRIES) {
                deleteEntryFiles(filesDir, stack.removeFirst().id)
            }
            persistLocked(context)
        }
    }

    suspend fun undo(context: Context): UndoResult = withContext(Dispatchers.IO) {
        val entry = synchronized(lock) {
            ensureLoaded(context)
            if (stack.isEmpty()) return@withContext UndoResult.Empty
            stack.last()
        }

        val filesDir = context.filesDir
        val homeBackup = resolveHomeBackup(filesDir, entry.id)
        val lockBackup = lockBackupFile(filesDir, entry.id)

        if (homeBackup == null && !lockBackup.exists()) {
            synchronized(lock) {
                ensureLoaded(context)
                if (stack.removeIf { it.id == entry.id }) {
                    persistLocked(context)
                }
            }
            return@withContext UndoResult.FileMissing
        }

        val outcome = WallpaperWriteGuard.withWriteLock {
            try {
                homeBackup?.copyTo(WallpaperFiles.homeFile(context), overwrite = true)
                if (lockBackup.exists()) {
                    lockBackup.copyTo(WallpaperFiles.lockFile(context), overwrite = true)
                }

                val (homeState, lockState) = if (entry.homeState >= 0) {
                    entry.homeState to entry.lockState
                } else {
                    WallpaperPrefs.readHomeLockState(WallpaperPrefs.prefs(context))
                }

                var homeBitmap = WallpaperFiles.homeFile(context).takeIf { it.exists() }?.let {
                    BitmapFactory.decodeFile(it.absolutePath)
                }
                var lockBitmap = if (lockState == 2 && lockBackup.exists()) {
                    BitmapFactory.decodeFile(lockBackup.absolutePath)
                } else {
                    null
                }

                try {
                    val bitmapForApply = when {
                        homeState > 0 && homeBitmap != null -> homeBitmap
                        lockState > 0 && lockBitmap != null -> lockBitmap
                        homeBitmap != null -> homeBitmap
                        lockBitmap != null -> lockBitmap
                        else -> return@withWriteLock UndoResult.FileMissing
                    }

                    val result = WallpaperApplier.applyForStates(
                        context,
                        bitmapForApply,
                        homeState,
                        lockState,
                        lockBitmap,
                    )

                    if (!result.fullySucceeded && homeState > 0 && !result.homeOk) {
                        Log.w(TAG, "undo apply partial failure homeOk=${result.homeOk} lockOk=${result.lockOk}")
                        return@withWriteLock UndoResult.ApplyFailed
                    }
                    if (lockState == 2 && !result.lockOk) {
                        return@withWriteLock UndoResult.ApplyFailed
                    }

                    UndoResult.Restored
                } finally {
                    homeBitmap?.takeIf { !it.isRecycled }?.recycle()
                    lockBitmap?.takeIf { it !== homeBitmap && !it.isRecycled }?.recycle()
                }
            } catch (e: Exception) {
                Log.e(TAG, "undo restore failed", e)
                UndoResult.ApplyFailed
            }
        }

        if (outcome == UndoResult.Restored) {
            synchronized(lock) {
                ensureLoaded(context)
                if (stack.removeIf { it.id == entry.id }) {
                    persistLocked(context)
                }
            }
        }

        outcome
    }

    fun cleanExpired(context: Context) {
        val activeIds = synchronized(lock) {
            ensureLoaded(context)
            stack.map { it.id }.toSet()
        }
        val filesDir = context.filesDir
        val now = System.currentTimeMillis()
        filesDir.listFiles()?.forEach { file ->
            val id = entryIdFromName(file.name) ?: return@forEach
            if (now - id > RETENTION_MS && id !in activeIds) {
                deleteEntryFiles(filesDir, id)
            }
        }
        filesDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("history_") && file.name.endsWith(".png") &&
                !file.name.contains("_home") && !file.name.contains("_lock")
            ) {
                val id = file.name.removePrefix("history_").removeSuffix(".png").toLongOrNull()
                if (id != null && now - id > RETENTION_MS && id !in activeIds) {
                    file.delete()
                }
            }
        }
    }

    private fun parseEntry(token: String): Entry? {
        val parts = token.split(":")
        val id = parts[0].toLongOrNull() ?: return null
        return if (parts.size >= 3) {
            val home = parts[1].toIntOrNull() ?: return null
            val lock = parts[2].toIntOrNull() ?: return null
            Entry(id, home, lock)
        } else {
            Entry(id, -1, -1)
        }
    }

    private fun persistLocked(context: Context) {
        val serialized = stack.joinToString(",") { entry ->
            if (entry.homeState >= 0) {
                "${entry.id}:${entry.homeState}:${entry.lockState}"
            } else {
                entry.id.toString()
            }
        }
        WallpaperPrefs.prefs(context).edit()
            .putString(PREFS_STACK, serialized)
            .commit()
    }

    private fun homeBackupFile(dir: File, id: Long) = File(dir, "history_${id}_home.png")
    private fun lockBackupFile(dir: File, id: Long) = File(dir, "history_${id}_lock.png")

    private fun resolveHomeBackup(dir: File, id: Long): File? {
        val modern = homeBackupFile(dir, id)
        if (modern.exists()) return modern
        val legacy = File(dir, "history_$id.png")
        return legacy.takeIf { it.exists() }
    }

    private fun deleteEntryFiles(dir: File, id: Long) {
        homeBackupFile(dir, id).delete()
        lockBackupFile(dir, id).delete()
        File(dir, "history_$id.png").delete()
    }

    private fun entryIdFromName(name: String): Long? {
        val home = Regex("^history_(\\d+)_home\\.png$").matchEntire(name)?.groupValues?.get(1)
        val lock = Regex("^history_(\\d+)_lock\\.png$").matchEntire(name)?.groupValues?.get(1)
        return (home ?: lock)?.toLongOrNull()
    }
}
