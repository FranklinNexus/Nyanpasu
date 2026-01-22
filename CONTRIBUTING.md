# Contributing to Nyanpasu Wallpaper 🌸

First off, thank you for considering contributing to Nyanpasu! It's people like you that make this project better for everyone.

## 💭 Philosophy

Nyanpasu is built with these core values:
- **User-Centric**: Every feature should delight users
- **Simplicity**: Clean code, clean UI, clean experience
- **Performance**: Fast, efficient, and battery-friendly
- **Privacy**: Zero data collection, ever

## 🚀 Getting Started

### Prerequisites

- **Android Studio**: Latest stable version (Hedgehog or newer)
- **JDK**: Version 8 or higher
- **Kotlin**: 1.9+ (bundled with Android Studio)
- **Git**: For version control

### Setup Development Environment

1. **Fork the repository** on GitHub
2. **Clone your fork**:
   ```bash
   git clone https://github.com/YOUR_USERNAME/ACGWallpaper.git
   cd ACGWallpaper
   ```
3. **Open in Android Studio**: `File > Open > Select project folder`
4. **Sync Gradle**: Click the 🐘 Sync Now button
5. **Run the app**: Click ▶️ Run or press `Shift + F10`

### Branch Naming Convention

- `feature/your-feature-name` - New features
- `fix/bug-description` - Bug fixes
- `docs/what-you-changed` - Documentation updates
- `refactor/what-you-improved` - Code refactoring
- `perf/optimization-description` - Performance improvements

Example: `feature/dark-mode-support`

## 🔧 How to Contribute

### 1. Reporting Bugs 🐛

**Before submitting a bug report:**
- Check if the bug has already been reported in [Issues](https://github.com/YourUsername/ACGWallpaper/issues)
- Make sure you're using the latest version

**When filing a bug report, include:**
- **Device info**: Model, Android version
- **App version**: Found in Settings
- **Steps to reproduce**: Clear, numbered steps
- **Expected behavior**: What should happen
- **Actual behavior**: What actually happens
- **Screenshots**: If applicable
- **Logcat**: If you can capture it

**Example bug report:**
```markdown
**Device**: Pixel 7 Pro, Android 14
**App Version**: 1.0.0

**Steps to Reproduce:**
1. Open app
2. Add tag "原神"
3. Tap Refresh
4. App crashes

**Expected**: Wallpaper should update
**Actual**: App crashes

**Logcat**: [Attach crash log]
```

### 2. Suggesting Features ✨

We love new ideas! When suggesting a feature:

- **Check existing issues** to avoid duplicates
- **Explain the problem** your feature solves
- **Describe your solution** in detail
- **Consider alternatives** you've thought of
- **Mockups/sketches** are always appreciated!

### 3. Contributing Code 💻

#### Step-by-Step Process

1. **Find an issue** to work on (or create one)
   - Comment "I'd like to work on this!" to claim it
   - Wait for maintainer approval (usually within 24h)

2. **Create a branch** from `main`:
   ```bash
   git checkout -b feature/awesome-feature
   ```

3. **Make your changes**:
   - Follow the [Code Style Guide](#code-style-guide)
   - Write meaningful commit messages
   - Test thoroughly on multiple devices

4. **Commit your changes**:
   ```bash
   git add .
   git commit -m "feat: add awesome feature 🎉"
   ```

5. **Push to your fork**:
   ```bash
   git push origin feature/awesome-feature
   ```

6. **Create a Pull Request**:
   - Go to the original repository
   - Click "New Pull Request"
   - Fill in the PR template
   - Link the related issue

#### PR Checklist

Before submitting, make sure:
- [ ] Code follows the project's code style
- [ ] You've tested on at least 2 different Android versions
- [ ] No new linter warnings/errors
- [ ] Screenshots/GIFs included for UI changes
- [ ] Updated documentation if needed
- [ ] Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/)

## 📝 Code Style Guide

### Kotlin Style

We follow the [official Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html):

```kotlin
// ✅ Good
class WallpaperWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    
    private val baseUrl = "https://api.example.com"
    
    override suspend fun doWork(): Result {
        return try {
            // Implementation
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
}

// ❌ Bad
class wallpaperWorker(appContext:Context,workerParams:WorkerParameters):CoroutineWorker(appContext,workerParams){
    val BaseURL="https://api.example.com"
    override suspend fun doWork():Result{
        // Implementation
    }
}
```

### Comments

- **Use Chinese or English** - either is fine, just be consistent in each file
- **Explain WHY, not WHAT** - code should be self-explanatory
- **Use emoji** to categorize comments (optional but encouraged):
  - 🎯 Main logic
  - ⚡ Performance optimization
  - 🐛 Bug fix
  - 🎨 UI/UX
  - 🔒 Security

```kotlin
// 🎯 核心搜索算法：死磕模式，绝不放弃
private fun executeUnbreakableSearch(...): String {
    // 为什么要轮询所有同义词？
    // 因为用户可能用中文、英文、日文任意一种输入
    for (variant in tagVariants) {
        // ...
    }
}
```

### XML Style

```xml
<!-- ✅ Good: Consistent indentation, logical ordering -->
<TextView
    android:id="@+id/tvTitle"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="@string/title"
    android:textColor="@color/text_primary"
    android:textSize="16sp"
    app:layout_constraintTop_toTopOf="parent" />

<!-- ❌ Bad: Inconsistent, hard to read -->
<TextView android:id="@+id/tvTitle" android:textSize="16sp"
    android:layout_width="wrap_content"
android:text="@string/title" android:layout_height="wrap_content"/>
```

### Resource Naming

- **Layouts**: `activity_main.xml`, `fragment_settings.xml`, `item_tag.xml`
- **Drawables**: `ic_launcher.xml`, `bg_card.xml`, `shape_circle.xml`
- **Colors**: `brand_pink`, `text_primary`, `bg_secondary`
- **Strings**: `error_network`, `btn_submit`, `title_home`

## 🧪 Testing

### Manual Testing Checklist

Before submitting a PR, test these scenarios:

- [ ] **Fresh install**: Uninstall app, install fresh
- [ ] **Upgrade**: Install old version, then upgrade to your build
- [ ] **Rotation**: Test landscape/portrait rotation
- [ ] **Background**: Test app coming from background
- [ ] **Airplane mode**: Test offline behavior
- [ ] **Low storage**: Test with <100MB storage
- [ ] **Slow network**: Enable network throttling

### Test Devices (Recommended)

Test on at least **2 different Android versions**:
- **Modern**: Android 12+ (API 31+)
- **Legacy**: Android 7.0 (API 24) - our minimum supported version

## 📚 Documentation

When adding new features:

1. **Update README.md**: Add to feature list if user-facing
2. **Update CHANGELOG.md**: Log your changes under "Unreleased"
3. **Add code comments**: Explain complex logic
4. **Update strings.xml**: For any new UI text

## 🏆 Recognition

Contributors will be:
- Listed in the **Contributors** section of README.md
- Mentioned in release notes
- Credited in the app's About page (coming soon!)
- Sent virtual high-fives and cat memes 🐱

## 💬 Communication

- **GitHub Issues**: For bugs, features, and technical discussions
- **Pull Requests**: For code reviews
- **Telegram**: [@FranklinNexus](https://t.me/FranklinNexus) - For quick questions

## 📜 Code of Conduct

### Our Pledge

We pledge to make participation in this project a harassment-free experience for everyone, regardless of:
- Age, body size, disability, ethnicity, gender identity
- Level of experience, nationality, personal appearance
- Race, religion, sexual identity and orientation

### Our Standards

**Positive behavior:**
- Being respectful of differing viewpoints
- Gracefully accepting constructive criticism
- Focusing on what is best for the community
- Showing empathy towards other community members

**Unacceptable behavior:**
- Trolling, insulting comments, personal attacks
- Public or private harassment
- Publishing others' private information
- Other conduct which could reasonably be considered inappropriate

### Enforcement

Instances of abusive, harassing, or otherwise unacceptable behavior may be reported by contacting the project team. All complaints will be reviewed and investigated promptly and fairly.

## 🎉 Thank You!

Your contributions make Nyanpasu better for thousands of anime fans worldwide. Every bug report, feature suggestion, and pull request is deeply appreciated.

**Nyanpasu~ (〃＾▽＾〃) 🚀**

---

*Questions? Feel free to ask in Issues or reach out on [Telegram](https://t.me/FranklinNexus).*
