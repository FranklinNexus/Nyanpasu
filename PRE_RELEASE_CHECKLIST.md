# Pre-Release Checklist 🚀

## 📋 Code Quality

### Build & Compilation
- [ ] ✅ Project builds successfully (`./gradlew clean build`)
- [ ] ✅ No linter errors (`./gradlew lintRelease`)
- [ ] ✅ No compiler warnings in release build
- [ ] ✅ ProGuard rules tested and working
- [ ] ✅ R8 shrinking doesn't break functionality

### Code Review
- [ ] 📝 All TODOs and FIXMEs addressed or documented
- [ ] 🔒 No hardcoded API keys or secrets
- [ ] 🌐 No hardcoded server URLs (use BuildConfig if needed)
- [ ] 📱 Proper permission handling (runtime permissions for Android 6+)
- [ ] 🔐 Sensitive operations use secure methods

### Performance
- [ ] ⚡ No memory leaks (test with LeakCanary)
- [ ] 📊 App starts in < 2 seconds on mid-range devices
- [ ] 🖼️ Images load smoothly without stuttering
- [ ] 🔋 Battery consumption is reasonable (< 5% per hour in background)
- [ ] 📦 APK size is optimized (< 10MB uncompressed)

## 🧪 Testing

### Functional Testing
- [ ] ✨ Main feature: Wallpaper refresh works
- [ ] 🖼️ WYSIWYG: Pan & zoom & crop works correctly
- [ ] 🏠 Home wallpaper sets correctly
- [ ] 🔒 Lock wallpaper sets correctly
- [ ] 🔄 Dual-mode (independent/sync) works
- [ ] 📦 Buffer system: Instant refresh works
- [ ] 🏷️ Tags: Both strict and soft modes work
- [ ] 🌶️ Spice level selector works (Pure/Mix/NSFW)
- [ ] ⏰ Auto-refresh: Scheduled updates work
- [ ] 🔙 Undo function works
- [ ] 💾 Save to gallery works
- [ ] 🐱 Mascot interactions work
- [ ] 🥚 Developer easter egg works

### Device Compatibility
- [ ] 📱 Android 7.0 (API 24) - Minimum supported
- [ ] 📱 Android 10 (API 29) - Common version
- [ ] 📱 Android 12 (API 31) - Material You
- [ ] 📱 Android 14 (API 34) - Latest
- [ ] 📏 Small phone (< 5.5")
- [ ] 📏 Large phone (> 6.5")
- [ ] 📐 Tablet (if time permits)

### Edge Cases
- [ ] 🌐 No internet connection (graceful failure)
- [ ] 🐢 Slow network (3G speed)
- [ ] 💾 Low storage (< 100MB free)
- [ ] 🔋 Battery saver mode
- [ ] 🌙 Dark mode compatibility
- [ ] 🔄 Screen rotation (portrait ↔ landscape)
- [ ] ♿ Accessibility: TalkBack works
- [ ] 🌍 Different locales (date/time formats)

### Stress Testing
- [ ] 🔁 Refresh 20+ times in a row
- [ ] 🏷️ Add 10+ tags
- [ ] ⏱️ Leave app running for 1 hour
- [ ] 🔄 Kill app, restart, verify state persists
- [ ] 📦 Upgrade from previous version (if applicable)

## 📚 Documentation

### User-Facing
- [ ] 📖 README.md is up-to-date and accurate
- [ ] 📝 CHANGELOG.md includes all changes for this version
- [ ] 🖼️ Screenshots are current and high-quality
- [ ] 🔒 PRIVACY_POLICY.md is accurate
- [ ] 📜 LICENSE file is present

### Developer-Facing
- [ ] 🤝 CONTRIBUTING.md is clear and helpful
- [ ] 🚀 RELEASE_GUIDE.md is accurate
- [ ] 📊 GOOGLE_PLAY_LISTING.md is finalized
- [ ] 🛠️ Code comments explain "why", not "what"

## 🎨 UI/UX

### Visual Polish
- [ ] 🎨 All icons are high-resolution (xxxhdpi)
- [ ] 🖌️ Colors are consistent with brand
- [ ] ✍️ Font sizes are readable (min 12sp)
- [ ] 📏 Touch targets are minimum 48dp
- [ ] 🌈 Color contrast meets WCAG AA standards

### User Experience
- [ ] ⚡ No janky animations
- [ ] ✅ Loading states are clear
- [ ] ❌ Error messages are helpful, not technical
- [ ] 🔔 Toasts/Snackbars are not annoying
- [ ] 🎯 Primary actions are obvious

## 🔐 Security & Privacy

### Privacy
- [ ] 🔒 No analytics/tracking code
- [ ] 🚫 No data sent to third parties
- [ ] 💾 No PII (Personally Identifiable Information) collected
- [ ] 🌐 HTTPS only (no HTTP requests)
- [ ] 📜 Privacy policy accurately reflects app behavior

### Security
- [ ] 🔐 No SQL injection vulnerabilities
- [ ] 🔒 No hardcoded credentials
- [ ] 🛡️ Proper certificate pinning (if applicable)
- [ ] 📝 Secure file permissions
- [ ] 🔑 Keystore is safely backed up (for signing)

## 📦 Release Artifacts

### APK/AAB
- [ ] 📱 Release APK generated and tested
- [ ] 📦 Release AAB generated (for Play Store)
- [ ] ✍️ APK is signed with production keystore
- [ ] 🔢 versionCode is incremented
- [ ] 🏷️ versionName is correct (e.g., 1.0.0)
- [ ] 📦 APK size is acceptable (< 10MB)

### Play Store Assets
- [ ] 🖼️ 512x512 icon ready
- [ ] 🎨 1024x500 feature graphic ready
- [ ] 📸 6+ screenshots (1080x1920) ready
- [ ] 📝 Short description (< 80 chars) finalized
- [ ] 📚 Full description finalized
- [ ] 🏷️ App title finalized

## 🚀 Pre-Launch

### Google Play Console
- [ ] 🎮 Internal testing track tested (if using)
- [ ] 🧪 Closed testing track tested (if using)
- [ ] 📊 Store listing preview reviewed
- [ ] 🌍 Target countries selected
- [ ] 💰 Pricing set (Free/Paid)
- [ ] 📋 Content rating questionnaire completed
- [ ] 🔞 Age restrictions set (if needed)

### GitHub
- [ ] 🏷️ Version tag created (e.g., v1.0.0)
- [ ] 📦 Release notes written
- [ ] 🔗 APK attached to release
- [ ] 📚 README badges updated (if any)

### Communication
- [ ] 📣 Announcement post prepared (if applicable)
- [ ] 🐦 Social media posts drafted (if applicable)
- [ ] 💬 Community notified (Telegram/Discord)

## ✅ Final Steps

- [ ] 🙏 Take a deep breath
- [ ] ☕ Grab a coffee
- [ ] 🚀 Hit "Publish"
- [ ] 🎉 Celebrate! You did it!
- [ ] 👀 Monitor crash reports for 24 hours
- [ ] 📊 Check user reviews
- [ ] 🐛 Prepare hotfix branch (just in case)

---

## 🎯 Quick Sanity Check

**Answer these 5 questions honestly:**

1. **Would I install this app on my own phone?** ✅ / ❌
2. **Is the app polished enough to impress my friends?** ✅ / ❌
3. **Have I tested on at least 2 different Android versions?** ✅ / ❌
4. **Is the privacy policy 100% accurate?** ✅ / ❌
5. **Do I have the keystore backed up in 3 places?** ✅ / ❌

**If all ✅, you're ready to ship! 🚀**

---

*Last updated: [Insert Date]*
*Version: 1.0.0*
