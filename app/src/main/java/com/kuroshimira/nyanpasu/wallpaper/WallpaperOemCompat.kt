package com.kuroshimira.nyanpasu.wallpaper

import android.os.Build

/**
 * 各 OEM 对 [android.app.WallpaperManager.setBitmap] 行为差异的兜底策略。
 * 发布版统一走此处，避免仅在某品牌真机上踩坑。
 */
internal object WallpaperOemCompat {

    private const val RETRY_COUNT = 3
    private const val RETRY_DELAY_MS = 120L
    private const val STEP_DELAY_MS = 80L

    /** 同步主屏+锁屏时的写入顺序。 */
    enum class SyncApplyOrder {
        /** 默认：主屏 → 锁屏 → 再锁屏 */
        HOME_LOCK_LOCK,
        /** 小米/华为/OPPO/vivo 等：锁屏 → 主屏 → 再锁屏 */
        LOCK_HOME_LOCK,
    }

    fun syncApplyOrder(): SyncApplyOrder =
        syncApplyOrderFor(Build.MANUFACTURER, Build.BRAND)

    /** 主屏写入后是否再写一次锁屏（独立锁屏模式同样适用）。 */
    fun shouldReassertLockAfterHome(): Boolean = true

    fun retryCount(): Int = RETRY_COUNT

    fun retryDelayMs(): Long =
        when {
            isHeavyHandedOem() -> RETRY_DELAY_MS
            else -> 60L
        }

    fun stepDelayMs(): Long =
        when {
            isHeavyHandedOem() -> STEP_DELAY_MS
            else -> 40L
        }

    /**
     * 锁屏优先使用 allowBackup=false（部分 OEM 对 lock+backup 组合异常）；
     * 主屏两种都试。
     */
    fun preferNoBackupForLock(): Boolean = isHeavyHandedOem() || isSamsung()

    internal fun syncApplyOrderFor(manufacturer: String, brand: String): SyncApplyOrder {
        val m = manufacturer.lowercase()
        val b = brand.lowercase()
        return when {
            m in XIAOMI_FAMILY || b in XIAOMI_FAMILY -> SyncApplyOrder.LOCK_HOME_LOCK
            m in HUAWEI_FAMILY || b in HUAWEI_FAMILY -> SyncApplyOrder.LOCK_HOME_LOCK
            m in OPPO_FAMILY || b in OPPO_FAMILY -> SyncApplyOrder.LOCK_HOME_LOCK
            m in VIVO_FAMILY || b in VIVO_FAMILY -> SyncApplyOrder.LOCK_HOME_LOCK
            m in MEIZU_FAMILY || b in MEIZU_FAMILY -> SyncApplyOrder.LOCK_HOME_LOCK
            m.contains("tecno") || m.contains("infinix") -> SyncApplyOrder.LOCK_HOME_LOCK
            else -> SyncApplyOrder.HOME_LOCK_LOCK
        }
    }

    private fun isHeavyHandedOem(): Boolean =
        syncApplyOrder() == SyncApplyOrder.LOCK_HOME_LOCK

    private fun isSamsung(): Boolean {
        val m = Build.MANUFACTURER.lowercase()
        return m == "samsung"
    }

    private val XIAOMI_FAMILY = setOf("xiaomi", "redmi", "poco", "blackshark")
    private val HUAWEI_FAMILY = setOf("huawei", "honor", "hinova", "tianyi")
    private val OPPO_FAMILY = setOf("oppo", "realme", "oneplus", "heytap")
    private val VIVO_FAMILY = setOf("vivo", "iqoo")
    private val MEIZU_FAMILY = setOf("meizu")
}
