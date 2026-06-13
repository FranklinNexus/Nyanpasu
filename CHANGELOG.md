# Changelog

All notable changes to Nyanpasu Wallpaper will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.0] - 2026-06-13

### 🎉 First Public Release

#### Core Features
- **Dual-Stream Engine**：粉 = 主屏锁屏联动，蓝 = 异色双图；`WallpaperTargetMode` 为唯一语义来源
- **WYSIWYG 预览**：PhotoView 缩放/平移/裁剪，所见即所得应用到系统壁纸
- **Smart Tags**：Strict 🔒 / Soft 🎲；看板娘对 Genshin、BA、Azur Lane 等 tag 有专属台词
- **Auto-Refresh**：每日 7:00 或每 6 / 12 / 24 小时；WorkManager + 精确闹钟
- **Interactive Mascot**：200+ 台词；连点 Logo 10 次有彩蛋 🎉
- **Tools**：Undo（5 张历史）、保存到相册、7 天自动清理

#### Architecture & Quality
- UI / Work / Schedule / Search / Wallpaper 模块化分包
- **WallpaperPipeline** + **docs/WALLPAPER_STATE.md**：双图搜索 / apply 不变量
- **OEM 兼容**：小米、华为、OPPO、vivo 等机型 sync 写入策略
- **NetworkEnvironment**：VPN / 弱网自适应；`i.pixiv.re` 优先 + 镜像回退
- WorkManager 三队列（手动 / 闹钟 / 周期）；背景预取 buffer
- 单元测试覆盖 Network、UrlPolicy、OEM、双图状态机等

#### Stability Highlights
- Refresh 永远联网搜新图；Tier 去重避免连续同图
- 异色双图：主屏 partial apply 仍持久化；complement 读 live prefs
- 冷启动 reconcile urgent/complement work；onResume sync 再补双图
- 预览 Home/Lock 单击切换；apply 进行中禁用 Refresh
- Windows：`scripts/build.ps1` 独立 `.out/app-<时间戳>` 构建

#### Privacy
- ❌ No data collection · ❌ No analytics · ❌ No ads
- ✅ All images stored locally · ✅ Open source

---

## [Unreleased]

### Planned
- [ ] Help/Tutorial page for first-time users
- [ ] Share wallpaper feature
- [ ] Local image import
- [ ] Wallpaper favorites/collections system
- [ ] Multiple mascot personalities (switchable)
- [ ] Custom schedule times (user-defined)
- [ ] Material You dynamic color support
- [ ] Filter and adjustment tools (brightness, contrast, blur)

---

## Version History

| Version | Release Date | Key Features |
|---------|-------------|--------------|
| 1.0.0   | 2026-06-13  | First public release |

---

## Notes

- All releases are signed with the same keystore for update continuity
- Versions follow Semantic Versioning (MAJOR.MINOR.PATCH)
- GitHub Release ships signed `app-release.apk` (tag `v*.*.*`)

---

**Nyanpasu~ (〃＾▽＾〃) 📝**
