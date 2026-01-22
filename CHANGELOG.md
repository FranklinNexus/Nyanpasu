# Changelog

All notable changes to Nyanpasu Wallpaper will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.1.0] - 2026-01-22

### ✨ Added
- **WYSIWYG Editor**: Pan, zoom, and crop images directly in the preview
  - Pinch to zoom in/out for perfect framing
  - Drag to reposition the image
  - Double-tap for quick zoom toggle
  - Applied wallpaper matches exactly what you see on screen
- **PhotoView Integration**: Gesture support for smooth image manipulation
- **Enhanced Tag System**: Mascot now responds to 26+ specific tags with custom quotes
  - Genshin Impact, Blue Archive, Azur Lane, Fate, Touhou, and more
- **Smart Cropping Algorithm**: Calculates visible bitmap from PhotoView's display matrix

### 🔧 Changed
- Updated ImageView to PhotoView in main layout
- Refactored wallpaper application logic to use cropped bitmaps
- Improved mascot chat timing and startup greeting

### 🐛 Fixed
- Image loading stability improvements
- Memory management optimizations
- Dual-stream mode edge cases

### 📦 Dependencies
- Added `com.github.chrisbanes:PhotoView:2.3.0`
- Added JitPack Maven repository

---

## [1.0.0] - 2026-01-15

### 🎉 Initial Release

#### Core Features
- **Dual-Stream Engine**: Independent control for Home and Lock screen wallpapers
  - Pink Mode: Sync both screens
  - Blue Mode: Independent wallpapers
  - Off Mode: Disable automatic management
- **Interactive Mascot System**: 200+ unique quotes with anime references
  - Random idle chat
  - Startup greeting
  - Easter egg confetti animation (10 taps on logo)
- **Smart Tag System**: Customize wallpaper preferences
  - Strict Mode (🔒): Force specific tags 100% of the time
  - Soft Mode (🎲): 20% chance to include tags
- **Auto-Refresh Scheduler**: Background wallpaper updates
  - Daily at 7:00 AM
  - Every 6 / 12 / 24 hours
  - WorkManager-based reliability
- **Porcelain White UI**: Minimalist design philosophy
  - Clean app background (#F8F9FA)
  - Pure white cards (#FFFFFF)
  - Pink (#FF80AB) and Blue (#64B5F6) brand colors
  - Smooth animations and haptic feedback

#### Tools
- **Undo System**: Restore up to 5 previous wallpapers
- **Save to Gallery**: Export current wallpaper to device storage
- **Smart Fallback**: Auto-adjusts search if strict tags return no results
- **Duplicate Prevention**: Ensures different images in Independent mode
- **Auto-Cleanup**: Removes history files older than 7 days

#### Tech Stack
- Kotlin
- Coroutines & Flow
- Material Design 3
- ViewBinding
- Coil (Image loading)
- WorkManager (Background tasks)
- Jsoup (API integration)

#### Privacy
- ❌ No data collection
- ❌ No analytics
- ❌ No ads
- ✅ All images stored locally
- ✅ Open source

---

## [Unreleased]

### Planned for V1.2.0
- [ ] Help/Tutorial page for first-time users
- [ ] Share wallpaper feature
- [ ] Local image import
- [ ] Wallpaper favorites/collections system

### Planned for V1.3.0
- [ ] Multiple mascot personalities (switchable)
- [ ] Custom schedule times (user-defined)
- [ ] Material You dynamic color support
- [ ] Filter and adjustment tools (brightness, contrast, blur)

---

## Version History

| Version | Release Date | Key Features |
|---------|-------------|--------------|
| 1.1.0   | 2026-01-22  | WYSIWYG Editor, PhotoView |
| 1.0.0   | 2026-01-15  | Initial Release |

---

## Notes

- All releases are signed with the same keystore for update continuity
- Versions follow Semantic Versioning (MAJOR.MINOR.PATCH)
- Each release includes both GitHub Release (APK) and Google Play (AAB)

---

**Nyanpasu~ (〃＾▽＾〃) 📝**
