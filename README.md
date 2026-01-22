# Nyanpasu Wallpaper (喵帕斯壁纸) 🐱

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="120" alt="Nyanpasu Logo" />
</p>

<p align="center">
  <img src="https://img.shields.io/badge/version-1.0.0-pink" alt="Version" />
  <img src="https://img.shields.io/badge/platform-Android-green" alt="Platform" />
  <img src="https://img.shields.io/badge/API-24%2B-blue" alt="API Level" />
  <img src="https://img.shields.io/badge/license-MIT-orange" alt="License" />
  <img src="https://github.com/YourUsername/ACGWallpaper/workflows/Android%20CI/badge.svg" alt="CI Status" />
</p>

<p align="center">
  <b>A Moe, Minimalist, and Dual-Stream Wallpaper Manager for Android.</b>
  <br>
  <i>"Nyanpasu~ (〃＾▽＾〃) 👋"</i>
</p>

<p align="center">
  <a href="#-download">Download</a> •
  <a href="#-features">Features</a> •
  <a href="#-screenshots">Screenshots</a> •
  <a href="#-build">Build</a> •
  <a href="#-developer">Developer</a>
</p>

---

## ✨ Features

### 🌸 **Dual-Stream Engine**
Separately manage your Home Screen and Lock Screen wallpapers:
- **Pink Mode** (🔄 Sync): Same wallpaper for both screens
- **Blue Mode** (🔵 Independent): Different wallpapers (e.g., Scenery for Lock, Character for Home)
- **Off Mode** (⚫ Disabled): No automatic wallpaper management

### 🤖 **Interactive Mascot**
Your cute companion that brings personality to the app:
- **200+ Unique Quotes**: Random idle chat with anime references, kaomoji, and memes
- **Tag Triggers**: Special responses when you add specific tags (Genshin, Blue Archive, etc.)
- **Easter Egg**: Tap the logo 10 times for a surprise confetti animation! 🎉
- **Startup Greeting**: "Nyanpasu~ (〃＾▽＾〃) 👋"

### 🖼️ **WYSIWYG Editor** (New in V1.1.0!)
What You See Is What You Get - Full control over wallpaper composition:
- **Pan & Drag**: Move the image to frame the perfect shot
- **Pinch to Zoom**: Zoom in on details or zoom out for full view
- **Double Tap**: Quick zoom toggle
- **Smart Crop**: Applied wallpaper matches exactly what you see on screen

### 🔍 **Smart Tagging System**
Customize your wallpaper preferences:
- **Strict Mode** (🔒 Pink Chip): Force specific tags 100% of the time
- **Soft Mode** (🎲 Gray Chip): 20% chance to include the tag for variety
- **26+ Tag Responses**: Mascot reacts to special tags (Fate, Touhou, Azur Lane, etc.)

### 🎨 **Porcelain White UI**
Clean, minimalist design philosophy:
- **App Background**: #F8F9FA (Soft white)
- **Card Surface**: #FFFFFF (Pure white)
- **Brand Colors**: Pink (#FF80AB) and Blue (#64B5F6)
- **Smooth Animations**: Bounce effects, fade transitions, haptic feedback

### ⏰ **Auto-Refresh Scheduler**
Wake up to fresh wallpapers:
- Daily at 7:00 AM
- Every 6 / 12 / 24 hours
- Reliable WorkManager-based background updates

### 🛠️ **Advanced Tools**
- **Undo System**: Restore up to 5 previous wallpapers
- **Save to Gallery**: Export current wallpaper to device storage
- **Smart Fallback**: Automatically adjusts search if strict tags return no results
- **Duplicate Prevention**: Ensures different images in Independent mode
- **Auto-Cleanup**: Removes history files older than 7 days

---

## 📸 Screenshots

| Main Interface | Dual-Stream Mode | Tag System |
|:---:|:---:|:---:|
| ![Main](screenshots/01_main.png) | ![Dual](screenshots/02_dual.png) | ![Tags](screenshots/03_tags.png) |

| Mascot Chat | Developer Card | WYSIWYG Editor |
|:---:|:---:|:---:|
| ![Mascot](screenshots/04_mascot.png) | ![Dev](screenshots/05_dev.png) | ![Editor](screenshots/06_editor.png) |

> **Note**: Add your screenshots to `screenshots/` folder before pushing to GitHub

---

## 🔧 Build Instructions

### Prerequisites
- Android Studio Hedgehog or later
- JDK 8 or higher
- Android SDK (API 24+)

### Clone and Build
```bash
# Clone the repository
git clone https://github.com/YourUsername/ACGWallpaper.git
cd ACGWallpaper

# Build debug APK
./gradlew assembleDebug

# Build release APK (requires keystore)
./gradlew assembleRelease
```

### Tech Stack
- **Kotlin** - First-class language for Android
- **Coroutines & Flow** - Asynchronous programming
- **Material Design 3** - Modern UI components
- **ViewBinding** - Type-safe view access

### Key Libraries
- [Coil](https://coil-kt.github.io/coil/) - Fast image loading and caching
- [PhotoView](https://github.com/Baseflow/PhotoView) - Gesture-based image zooming
- [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) - Reliable background tasks
- [Jsoup](https://jsoup.org/) - API integration

---

## 📥 Download

<p align="center">
  <a href="YOUR_GOOGLE_PLAY_LINK_HERE">
    <img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" height="80">
  </a>
</p>

**Latest Version**: v1.0.0 (Initial Release)

**Alternative Downloads**:
- [GitHub Releases](https://github.com/YourUsername/ACGWallpaper/releases) - Direct APK download
- [F-Droid](#) *(Coming soon)*

---

## 🎯 Roadmap

### V1.0.0 (✅ Released - Initial Launch)
- [x] Dual-Stream Engine (Home/Lock wallpapers)
- [x] WYSIWYG Editor (Pan, Zoom, Crop)
- [x] PhotoView integration
- [x] Smart tagging system (Strict/Soft modes)
- [x] Interactive Mascot with 200+ quotes
- [x] Auto-refresh scheduler
- [x] Spice Level selector (Pure/Mix/NSFW)
- [x] Buffer system for instant refresh

### V1.1.0 (Planned)
- [ ] Help/Tutorial page
- [ ] Share wallpaper feature
- [ ] Local image import
- [ ] Wallpaper favorites/collections

### V1.3.0 (Future)
- [ ] Multiple mascot personalities
- [ ] Custom schedule times
- [ ] Material You dynamic colors
- [ ] Filters and adjustments

---

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the project
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 👨‍💻 Developer

**KuroshiMira**

*A veteran otaku building dreams! Let's grab a coffee and be friends? ☕💫*

- 🌐 **Blog**: [Wisdom Echoes](https://WisdomEchoes.net)
- 📱 **Telegram**: [@FranklinNexus](https://t.me/FranklinNexus)
- 💻 **GitHub**: [@YourUsername](https://github.com/YourUsername)

---

## 🙏 Acknowledgments

- [Lolicon API](https://api.lolicon.app/) - Wallpaper image source
- [PhotoView](https://github.com/Baseflow/PhotoView) by Chris Banes - Gesture support
- The anime community for endless inspiration
- All contributors and users who make this project better

## 🔒 Privacy

Nyanpasu **does not collect any personal data**:
- ❌ No analytics
- ❌ No tracking
- ❌ No ads
- ✅ All images stored locally
- ✅ Open source - verify yourself!

See [PRIVACY_POLICY.md](PRIVACY_POLICY.md) for details.

---

<div align="center">

**Nyanpasu~ 👋**

If you like this project, please give it a ⭐!

</div>
