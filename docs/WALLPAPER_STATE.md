# Nyanpasu 壁纸状态规格

## 三态

| 值 | UI | 含义 |
|----|-----|------|
| 0 | 灰 | 关闭 |
| 1 | 粉 | 按钮颜色（与联动/独立无关） |
| 2 | 蓝 | 按钮颜色（与联动/独立无关） |

## 组合行为（唯一真相）

| 主屏 | 锁屏 | 模式 | 图片数 | Refresh |
|------|------|------|--------|---------|
| 关 | 关 | — | 0 | 禁止 |
| 开 | 关 | 仅主屏 | 1 | 搜 1 次 |
| 关 | 开 | 仅锁屏 | 1 | 搜 1 次 |
| **粉** | **粉** | **联动** | **1** | 搜 1 次 |
| **蓝** | **蓝** | **联动** | **1** | 搜 1 次 |
| **粉** | **蓝** | **双图** | **2** | 搜 2 次（必须 distinct） |
| **蓝** | **粉** | **双图** | **2** | 搜 2 次（必须 distinct） |

**规则**：同色 = 联动；异色 = 双图。粉蓝与蓝粉完全等价。

## 产品语义

| 动作 | 行为 |
|------|------|
| Refresh | 永远联网搜新图，不用预取 Instant Load |
| 预取 buffer | 仅单图模式；双图模式必须 urgent 下载 |
| 自动换壁纸 | 与 Refresh 同一 pipeline |

## 系统不变量

- **I1** 双图模式 ⇒ `dedupeKey(homeUrl) ≠ dedupeKey(lockUrl)`
- **I2** 双图模式 ⇒ apply 必须带 `lockBitmap`
- **I3** 任何写盘/apply ⇒ 在 `WallpaperWriteGuard` 内
- **I4** 双图预览锁屏 ⇒ 不 fallback 到 home 文件
- **I5** Refresh ⇒ 不走 prefetch promote
- **I7** 从联动切到异色双图 ⇒ 清除相同 lock 文件，并下载第二张

## 代码入口

- 语义：`WallpaperTargetMode`
- 编排：`WallpaperJobRunner` / `WallpaperWorker`
- 不变量：`WallpaperPipeline`
- Apply：`WallpaperApplier.applyForStates`
