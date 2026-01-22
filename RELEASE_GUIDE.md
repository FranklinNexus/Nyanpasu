# 🚀 完整发布指南 - GitHub 开源 + Google Play 上架

> **目标**：把你的 Nyanpasu 应用发布到 GitHub 开源仓库，并上架到 Google Play Store。

---

## 📋 准备工作清单

在开始之前，确保你有：

- [x] ✅ Android Studio 项目已完成
- [ ] GitHub 账号 ([注册](https://github.com/signup))
- [ ] Google Play 开发者账号 ([注册](https://play.google.com/console/signup)) - **需要一次性支付 $25 美元**
- [ ] 项目已编译通过，无 Linter 错误
- [ ] 已测试所有功能
- [ ] 准备好 App Icon 和截图

---

## 第一阶段：GitHub 开源 🌐

### 步骤 1：创建 GitHub 仓库

#### 1.1 登录 GitHub
访问 [github.com](https://github.com) 并登录

#### 1.2 创建新仓库
1. 点击右上角 **"+"** → **"New repository"**
2. 填写仓库信息：
   - **Repository name**: `ACGWallpaper` 或 `NyanpasuWallpaper`
   - **Description**: `A Moe, Minimalist, and Dual-Stream Wallpaper Manager for Android.`
   - **Public** ✅ (开源项目)
   - **Add README**: ❌ 不勾选（我们已经有了）
   - **Add .gitignore**: ❌ 不勾选（我们已经有了）
   - **Choose a license**: ❌ 不勾选（我们已经有了）
3. 点击 **"Create repository"**

#### 1.3 记录仓库 URL
创建后，你会看到类似这样的 URL：
```
https://github.com/YourUsername/ACGWallpaper
```
**记下这个 URL，后面会用到！**

---

### 步骤 2：初始化 Git 并推送代码

#### 2.1 打开终端

在 **Android Studio** 中：
- Windows: `View` → `Tool Windows` → `Terminal`
- 或者直接按 `Alt + F12`

#### 2.2 检查 Git 是否安装

```bash
git --version
```

**如果显示版本号**（例如 `git version 2.43.0`）：✅ 已安装

**如果显示错误**：
- 下载 Git: https://git-scm.com/downloads
- 安装后重启 Android Studio

#### 2.3 配置 Git（首次使用）

```bash
# 设置你的名字（会显示在提交记录中）
git config --global user.name "KuroshiMira"

# 设置你的邮箱（建议使用 GitHub 注册邮箱）
git config --global user.email "your-email@example.com"
```

#### 2.4 初始化 Git 仓库

**重要**：确保你在项目根目录（`ACGWallpaper` 文件夹）下

```bash
# 初始化 Git
git init

# 查看当前状态
git status
```

你应该看到一堆 **红色** 的文件名（表示未追踪）

#### 2.5 添加所有文件

```bash
# 添加所有文件到暂存区
git add .

# 再次查看状态
git status
```

现在文件应该变成 **绿色**（表示已暂存）

#### 2.6 首次提交

```bash
# 提交到本地仓库
git commit -m "🎉 Initial Release: Nyanpasu Wallpaper v1.1.0"
```

#### 2.7 连接到 GitHub 远程仓库

**替换下面的 URL 为你自己的仓库地址！**

```bash
# 添加远程仓库（替换 YourUsername）
git remote add origin https://github.com/YourUsername/ACGWallpaper.git

# 验证远程仓库
git remote -v
```

应该显示：
```
origin  https://github.com/YourUsername/ACGWallpaper.git (fetch)
origin  https://github.com/YourUsername/ACGWallpaper.git (push)
```

#### 2.8 推送到 GitHub

```bash
# 创建主分支并推送
git branch -M main
git push -u origin main
```

**如果要求输入用户名和密码**：
- **用户名**: 你的 GitHub 用户名
- **密码**: ⚠️ **不是你的登录密码**，而是 **Personal Access Token**

#### 2.9 创建 Personal Access Token（如果需要）

1. 访问 https://github.com/settings/tokens
2. 点击 **"Generate new token"** → **"Generate new token (classic)"**
3. 设置：
   - **Note**: `ACGWallpaper Push`
   - **Expiration**: `90 days`（或自定义）
   - **Scopes**: 勾选 `repo` 下的所有选项
4. 点击 **"Generate token"**
5. **立刻复制 Token**（只显示一次！）
6. 在终端中用这个 Token 作为密码

**推送成功后**，访问你的 GitHub 仓库，应该能看到所有代码！

---

### 步骤 3：完善 GitHub 仓库

#### 3.1 更新 README.md 中的链接

在 `README.md` 中，替换所有占位符：

```markdown
# 查找并替换（使用 Ctrl+H）
YourUsername → 你的真实 GitHub 用户名
YOUR_GOOGLE_PLAY_LINK_HERE → 先留空，上架后再填
```

提交更新：

```bash
git add README.md
git commit -m "📝 Update README links"
git push
```

#### 3.2 添加 Topics（标签）

在 GitHub 仓库页面：
1. 点击右侧 **"About"** 旁边的齿轮图标 ⚙️
2. 在 **"Topics"** 中添加：
   ```
   android
   kotlin
   wallpaper
   anime
   material-design
   wallpaper-manager
   anime-wallpaper
   otaku
   ```
3. 点击 **"Save changes"**

#### 3.3 创建 Release（发布版本）

1. 在仓库页面，点击右侧 **"Releases"** → **"Create a new release"**
2. 填写信息：
   - **Tag version**: `v1.1.0`
   - **Release title**: `🎉 V1.1.0 - WYSIWYG Editor Update`
   - **Description**:
     ```markdown
     ## ✨ What's New

     ### 🖼️ WYSIWYG Editor
     - Pan and zoom images before applying as wallpaper
     - Pinch to zoom, drag to reframe
     - What you see is what you get!

     ### 🐛 Bug Fixes
     - Improved image loading stability
     - Enhanced mascot chat timing

     ### 📦 Downloads
     - [app-release.apk](link) - Direct install (will upload later)

     **Nyanpasu~ (〃＾▽＾〃) 👋**
     ```
3. **先不点发布**，我们稍后会上传 APK 文件

---

### 步骤 4：生成 Release APK

#### 4.1 创建签名密钥（Keystore）

**⚠️ 极其重要：这个密钥一旦丢失，你将永远无法更新你的应用！**

在 Android Studio 中：

1. 菜单栏：`Build` → `Generate Signed Bundle / APK...`
2. 选择 **"APK"** → **Next**
3. 点击 **"Create new..."**
4. 填写信息：
   ```
   Key store path: C:\Users\kfr34\AndroidStudioProjects\ACGWallpaper\nyanpasu-release.jks
   Password: [创建一个强密码，必须记住！]
   Alias: nyanpasu-key
   Alias password: [可以和上面一样]
   
   Validity (years): 25
   
   Certificate:
   First and Last Name: KuroshiMira
   Organizational Unit: [留空]
   Organization: [留空]
   City or Locality: [你的城市]
   State or Province: [你的省份]
   Country Code (XX): CN (或你的国家代码)
   ```
5. 点击 **OK**

#### 4.2 签名并生成 APK

1. 选择刚创建的 Keystore
2. 输入密码
3. **Build Variants**: `release`
4. **Signature Versions**: ✅ V1 和 ✅ V2 都勾选
5. 点击 **Next** → **Finish**

等待编译完成...

#### 4.3 找到生成的 APK

编译完成后，Android Studio 会显示通知。点击 **"locate"** 或手动前往：

```
ACGWallpaper\app\release\app-release.apk
```

**文件大小**：约 5-8 MB

#### 4.4 备份 Keystore 文件

**⚠️ 超级重要！**

1. 复制 `nyanpasu-release.jks` 到安全位置：
   - U盘
   - 云盘（Google Drive / OneDrive）
   - 加密的 USB 硬盘
2. 记录密码到密码管理器（如 1Password / Bitwarden）

**如果丢失**：
- ❌ 无法更新应用
- ❌ 只能创建新应用（不同包名）
- ❌ 用户需要卸载旧版重新安装

#### 4.5 上传 APK 到 GitHub Release

回到 GitHub 的 Release 编辑页面：
1. 拖拽 `app-release.apk` 到 **"Attach binaries"** 区域
2. 点击 **"Publish release"**

现在任何人都可以从 GitHub 下载你的 APK！

---

### 步骤 5：准备截图

#### 5.1 在真机或模拟器上运行应用

#### 5.2 截取以下场景

1. **主界面** - 显示预览卡片、按钮、看板娘
2. **Tag 系统** - 显示多个 Chips（粉色 Strict、灰色 Soft）
3. **双壁纸模式** - 顶部显示 "Home Screen" 或 "Lock Screen" 指示器
4. **WYSIWYG 编辑** - 手指拖拽/缩放图片的状态
5. **看板娘对话** - 显示对话气泡
6. **开发者名片** - 点击 Logo 10 次后的弹窗 + 丝带动画

#### 5.3 裁剪截图

使用 **Paint** / **Photoshop** / 在线工具裁剪为：
- **尺寸**: 1080 x 1920 px（9:16 比例）
- **格式**: PNG 或 JPEG

#### 5.4 添加标注（可选但推荐）

使用 Canva / Figma 添加文字说明：
- Screenshot 1: "Dual-Stream Engine - Pink for Sync, Blue for Independent"
- Screenshot 2: "Smart Tags - Strict (🔒) or Soft (🎲) Mode"

#### 5.5 保存到项目

创建文件夹并保存：

```bash
mkdir screenshots
# 保存为：
# screenshots/01_main.png
# screenshots/02_dual.png
# screenshots/03_tags.png
# screenshots/04_mascot.png
# screenshots/05_dev.png
# screenshots/06_editor.png
```

推送到 GitHub：

```bash
git add screenshots/
git commit -m "📸 Add app screenshots"
git push
```

---

## 第二阶段：Google Play 上架 🎮

### 步骤 1：注册 Google Play 开发者账号

#### 1.1 访问 Play Console
https://play.google.com/console/signup

#### 1.2 支付注册费

- **费用**: $25 美元（一次性，终身有效）
- **支付方式**: 信用卡 / PayPal

#### 1.3 填写账号信息

- **Developer name**: `KuroshiMira`（公开显示）
- **Email**: 你的联系邮箱
- **Phone**: 你的手机号（用于验证）

#### 1.4 同意条款

阅读并同意 Google Play 开发者协议

---

### 步骤 2：创建应用

#### 2.1 创建新应用

1. 登录 [Play Console](https://play.google.com/console/)
2. 点击 **"Create app"**
3. 填写：
   - **App name**: `Nyanpasu - Anime Wallpaper`
   - **Default language**: `English (United States)`
   - **App or game**: `App`
   - **Free or paid**: `Free`
4. 声明：
   - ✅ 勾选所有必需的声明
5. 点击 **"Create app"**

---

### 步骤 3：填写应用信息

Google Play 会要求你完成一系列任务，按顺序进行：

#### 3.1 App Access (应用访问)

**问题**: Does your app restrict access to any features?

**回答**: 
- ❌ No, all features are available to all users

点击 **Save**

---

#### 3.2 Ads (广告)

**问题**: Does your app contain ads?

**回答**:
- ❌ No, my app does not contain ads

点击 **Save**

---

#### 3.3 Content Rating (内容分级)

⚠️ **重要**：决定你的应用可以展示给哪些年龄段用户

1. 点击 **"Start questionnaire"**
2. 选择 **Category**: `Entertainment`
3. 填写邮箱
4. 回答问题：

   **Does your app contain violence?**
   - ❌ No

   **Does your app contain sexual content?**
   - ⚠️ **关键选择**：
     - 如果你用 Lolicon API 的 **R18=0** (Safe) → ❌ No → 评级 **Everyone**
     - 如果允许 R18 内容 → ✅ Yes → 评级 **Mature 17+**
   - **建议**：选 No，并在代码中强制 `r=0` 参数

   **Does your app contain profanity?**
   - ❌ No

   **Does your app contain drug/alcohol/tobacco references?**
   - ❌ No

   **Does your app contain gambling?**
   - ❌ No

5. 点击 **Save** → **Submit**

等待几秒，会显示评级结果（通常是 **Everyone** 或 **Teen**）

---

#### 3.4 Target Audience (目标受众)

1. **Target age groups**: `13-17` 和 `18+` (两个都勾选)
2. **Is your app appealing to children?**: ❌ No
3. 点击 **Save**

---

#### 3.5 News Apps (新闻应用)

- ❌ No, my app is not a news app

---

#### 3.6 COVID-19 Contact Tracing

- ❌ No

---

#### 3.7 Data Safety (数据安全) - ⚠️ 重要！

这个部分对应你的隐私政策。

1. **Does your app collect or share any user data?**
   - ❌ No, we don't collect any data

2. 点击 **Save** → **Next** → **Submit**

---

#### 3.8 Privacy Policy (隐私政策)

**Privacy Policy URL**:

**选项 1**：使用 GitHub 托管（推荐）
```
https://raw.githubusercontent.com/YourUsername/ACGWallpaper/main/PRIVACY_POLICY.md
```

**选项 2**：使用你的博客
```
https://WisdomEchoes.net/nyanpasu-privacy-policy
```

**验证**：点击链接确保可以访问

点击 **Save**

---

#### 3.9 App Category (应用类别)

- **App category**: `Personalization`
- **Tags** (可选): `Wallpaper`, `Anime`, `Cute`

点击 **Save**

---

#### 3.10 Store Listing (商店详情)

这是用户看到的应用页面，参考 `GOOGLE_PLAY_LISTING.md` 文件：

##### App Name (已填写)
```
Nyanpasu - Anime Wallpaper
```

##### Short Description
```
Daily anime wallpapers with a cute mascot companion. Dual-screen support!
```

##### Full Description

**复制** `GOOGLE_PLAY_LISTING.md` 中的 "Full Description" 部分（约 2400 字符）

##### App Icon

上传 `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png`（需要先调整为 512x512 px）

**调整方法**：
1. 打开 ic_launcher.png (192x192)
2. 使用在线工具放大到 512x512：https://www.iloveimg.com/resize-image
3. 上传

##### Feature Graphic (需要设计)

**尺寸**: 1024 x 500 px

**快速制作方法（使用 Canva）**：
1. 访问 https://www.canva.com
2. 搜索 "App Feature Graphic" 模板
3. 设计内容：
   - 背景：渐变色（粉色 → 蓝色）
   - 文字：`Nyanpasu - Daily Anime Wallpapers`
   - 放置 App Icon
   - 添加一两张壁纸截图
4. 下载为 PNG (1024 x 500)
5. 上传到 Play Console

##### Phone Screenshots

上传你之前准备的 6 张截图（`screenshots/` 文件夹中）

**要求**：
- 至少 2 张
- 推荐 4-8 张
- 尺寸：1080 x 1920 px

拖拽上传到对应位置

##### 7-inch Tablet Screenshots (可选)

如果你想支持平板，上传横屏截图（可以跳过）

##### 10-inch Tablet Screenshots (可选)

同上

点击 **Save**

---

### 步骤 4：上传 AAB 文件

Google Play 现在要求上传 **AAB**（Android App Bundle）而不是 APK

#### 4.1 生成 AAB

在 Android Studio 中：

1. 菜单栏：`Build` → `Generate Signed Bundle / APK...`
2. 选择 **"Android App Bundle"** → **Next**
3. 选择你之前创建的 Keystore (`nyanpasu-release.jks`)
4. 输入密码
5. **Build Variants**: `release`
6. 点击 **Finish**

等待编译完成...

生成的文件在：
```
ACGWallpaper\app\release\app-release.aab
```

#### 4.2 创建 Production Track

在 Play Console：

1. 左侧菜单：`Release` → `Production`
2. 点击 **"Create new release"**
3. 上传 `app-release.aab`（拖拽到页面）

等待上传和处理（可能需要几分钟）...

#### 4.3 填写 Release Notes

**Release name**: `1.1.0 (1)` (自动生成)

**Release notes**（支持多语言，建议至少填 English）:

```
🎉 What's New in V1.1.0:

✨ WYSIWYG Editor
• Pan and zoom images before applying as wallpaper
• Pinch to zoom, drag to reframe
• What you see is what you get!

🌸 Dual-Stream Engine
• Separate control for Home and Lock screens
• Pink mode for sync, Blue mode for independent

🤖 Interactive Mascot
• 200+ cute quotes and anime references
• Special tag responses (Genshin, Blue Archive, etc.)

Nyanpasu~ (〃＾▽＾〃) 👋
```

#### 4.4 Review and Rollout

1. 检查所有信息
2. 点击 **"Save"**
3. 点击 **"Review release"**
4. 仔细阅读警告和提示
5. 如果一切正常，点击 **"Start rollout to Production"**

---

### 步骤 5：等待审核

#### 审核时间

- **通常**: 1-3 天
- **首次发布**: 可能长达 7 天
- **节假日**: 可能更久

#### 审核状态

在 Play Console 查看状态：
- 🟡 **Pending publication** - 等待审核
- 🔵 **In review** - 正在审核
- 🟢 **Published** - 已上架！
- 🔴 **Rejected** - 被拒绝（查看原因并修改）

#### 常见拒绝原因

1. **隐私政策不可访问** → 检查 URL
2. **内容分级不当** → 重新评估是否有敏感内容
3. **版权问题** → 确保 Icon 和素材原创
4. **功能描述不符** → 确保描述准确反映功能

---

### 步骤 6：上架后优化

#### 6.1 更新 README.md

在你的 GitHub 仓库中，更新 Play Store 链接：

```markdown
## 📥 Download

<p align="center">
  <a href="https://play.google.com/store/apps/details?id=com.example.acgwallpaper">
    <img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" height="80">
  </a>
</p>
```

提交更新：

```bash
git add README.md
git commit -m "📝 Add Google Play link"
git push
```

#### 6.2 分享你的应用

在以下平台宣传：

**Reddit**:
- r/androidapps
- r/android
- r/anime
- r/genshin_impact (如果支持原神 Tag)

**示例帖子**：
```
Title: [DEV] Nyanpasu - A privacy-first anime wallpaper app with a cute mascot

Hey r/androidapps! I built a minimalist anime wallpaper manager called Nyanpasu.

Key features:
• Dual-Stream engine (separate Home/Lock wallpapers)
• WYSIWYG editor (pan, zoom, crop)
• Interactive mascot with 200+ quotes
• Zero tracking, completely open-source

[Google Play Link] | [GitHub Repo]

Would love your feedback! ❤️
```

**Telegram**:
- 发送到你的 Channel
- 相关的动漫/二次元群组

**Twitter / X**:
- 使用 Hashtags: `#AndroidApp #AnimeWallpaper #OpenSource`

**你的博客**:
- 写一篇发布日志: "Building Nyanpasu - A Journey in Android Development"

---

## 🎯 版本更新流程

当你要发布 V1.2.0 时：

### 1. 更新版本号

在 `app/build.gradle.kts`:

```kotlin
android {
    defaultConfig {
        versionCode = 2        // +1
        versionName = "1.2.0"  // 新版本号
    }
}
```

### 2. 更新 CHANGELOG.md

```markdown
## [1.2.0] - 2026-02-15

### Added
- Local image import feature
- Wallpaper favorites system
- Help/Tutorial page

### Fixed
- Memory leak in image loading
- Crash on Android 14
```

### 3. 提交代码到 GitHub

```bash
git add .
git commit -m "🚀 Release V1.2.0 - Favorites & Import"
git push
```

### 4. 创建 GitHub Release

同上，Tag 改为 `v1.2.0`

### 5. 生成新的 AAB

使用**同一个 Keystore** 签名（重要！）

### 6. 上传到 Play Console

重复"上传 AAB"步骤

---

## ⚠️ 重要注意事项

### 安全

1. **永远不要提交 Keystore 到 GitHub**
   - `.gitignore` 已包含 `*.jks`, `*.keystore`
   - 提交前用 `git status` 检查

2. **API 密钥保护**
   - 如果你用了私有 API，使用 `local.properties` 存储密钥
   - 不要硬编码在代码中

### 法律

1. **隐私政策**
   - 必须准确反映数据收集实践
   - 不要抄袭他人的隐私政策

2. **版权**
   - 确保 App Icon 原创或有授权
   - Lolicon API 的图片来自 Pixiv，属于各自作者
   - 你的应用只是"聚合工具"，不拥有图片版权

3. **商标**
   - "Nyanpasu" 来自动漫《悠哉日常大王》的梗
   - 通常动漫梗可以用，但避免直接使用动漫角色作为 Icon

### 持续维护

1. **监控崩溃**
   - Play Console → Quality → Android vitals
   - 查看崩溃报告，及时修复

2. **回复评论**
   - 好评：感谢用户
   - 差评：询问问题，提供解决方案
   - 目标：4.5+ 星评分

3. **定期更新**
   - 至少每 3-6 个月更新一次
   - 适配新的 Android 版本
   - 修复 Google Play 警告

---

## 🎉 完成清单

### GitHub 部分
- [ ] 创建 GitHub 仓库
- [ ] 推送代码到 GitHub
- [ ] 添加 Topics 标签
- [ ] 创建 Release (v1.1.0)
- [ ] 上传 APK 到 Release
- [ ] 准备截图并推送
- [ ] 更新 README 链接

### Google Play 部分
- [ ] 注册开发者账号 ($25)
- [ ] 创建应用
- [ ] 填写应用信息 (App Access, Ads, etc.)
- [ ] 完成内容分级
- [ ] 填写隐私政策
- [ ] 填写商店详情 (描述、截图)
- [ ] 制作 Feature Graphic
- [ ] 生成签名 AAB
- [ ] 备份 Keystore 文件
- [ ] 上传 AAB 到 Production
- [ ] 提交审核
- [ ] 等待审核通过
- [ ] 更新 GitHub README 的 Play Store 链接
- [ ] 在社交媒体分享

---

## 🆘 常见问题

### Q: 审核被拒绝了怎么办？

**A**: 
1. 查看 Play Console 中的拒绝原因
2. 根据原因修改（通常是隐私政策、内容分级）
3. 重新提交审核
4. 如果不理解原因，可以回复审核团队询问

### Q: Keystore 丢失了怎么办？

**A**: 
- **无法找回**！这就是为什么备份极其重要。
- 只能创建新应用（修改包名），旧应用无法更新。

### Q: 如何修改应用包名？

**A**: 
- **不推荐**在发布后修改。
- 如果必须修改：
  1. 在 `build.gradle.kts` 中修改 `applicationId`
  2. 重构所有代码文件的 `package` 声明
  3. 这会被视为全新应用，需要重新上架

### Q: 如何添加付费功能？

**A**: 
1. 集成 Google Play Billing Library
2. 在 Play Console 中设置"应用内购买"
3. 创建商品（如"高级版" $2.99）
4. 在代码中实现购买逻辑

### Q: 如何查看下载量？

**A**: 
- Play Console → Statistics → Overview
- 显示安装量、卸载量、评分等数据

---

## 🚀 你已经准备好了！

现在，按照这个指南一步步操作，你的 Nyanpasu 应用将会：

1. ✅ 在 GitHub 开源，让全世界的开发者看到
2. ✅ 在 Google Play 上架，让全世界的用户下载
3. ✅ 成为你的作品集中的亮点

**预计总时间**：
- GitHub 发布：30 分钟
- Google Play 上架：2 小时（填表）+ 1-3 天（审核）

**Good luck, and have fun! (๑˃ᴗ˂)ﻭ**

**Nyanpasu~ (〃＾▽＾〃) 🎉**
