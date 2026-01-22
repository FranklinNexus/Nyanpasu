# 📚 完整发布指南 - Nyanpasu Wallpaper

## 目录
1. [发布到 GitHub](#步骤一发布到-github)
2. [生成签名 APK](#步骤二生成签名-apk)
3. [准备应用商店资料](#步骤三准备应用商店资料)
4. [上传到 Google Play](#步骤四上传到-google-play)

---

## 步骤一：发布到 GitHub

### 1.1 创建 GitHub 仓库

1. 打开浏览器，访问 [GitHub](https://github.com)
2. 点击右上角的 `+` → `New repository`
3. 填写仓库信息：
   - **Repository name**: `ACGWallpaper` 或 `Nyanpasu-Wallpaper`
   - **Description**: `A minimalist anime-style wallpaper app with interactive mascot`
   - **Visibility**: ✅ Public（推荐，方便用户下载）
   - **不要** 勾选 "Initialize with README"（我们已经创建了）
4. 点击 `Create repository`

### 1.2 初始化本地 Git 仓库

在 Android Studio 打开 Terminal（底部），运行：

```bash
# 1. 初始化 Git 仓库
git init

# 2. 添加所有文件
git add .

# 3. 创建第一次提交
git commit -m "Initial commit: Nyanpasu Wallpaper v1.0.0"

# 4. 添加远程仓库（替换 YourUsername 为你的 GitHub 用户名）
git remote add origin https://github.com/YourUsername/ACGWallpaper.git

# 5. 推送到 GitHub
git branch -M main
git push -u origin main
```

### 1.3 推送成功后

访问你的 GitHub 仓库页面，你应该能看到：
- ✅ README.md（项目介绍）
- ✅ LICENSE（MIT 许可证）
- ✅ 完整的源代码

---

## 步骤二：生成签名 APK

### 2.1 创建密钥库（Keystore）

**第一次发布需要创建密钥库，非常重要！务必保管好！**

在 Android Studio：

1. 点击 `Build` → `Generate Signed Bundle / APK`
2. 选择 `APK` → `Next`
3. 点击 `Create new...`（创建新密钥库）

填写信息：

```
Key store path: 选择保存位置，例如：
  C:\Users\YourName\nyanpasu-keystore.jks

Password: 输入强密码（至少6位）
Confirm: 再次输入密码

------ 证书信息 ------
Alias: nyanpasu-key
Password: 输入密钥密码
Validity (years): 25（建议至少25年）

Certificate:
  First and Last Name: KuroshiMira（或你的名字）
  Organizational Unit: Development
  Organization: YourStudio
  City or Locality: YourCity
  State or Province: YourState
  Country Code (XX): CN（或你的国家代码）
```

4. 点击 `OK`

### 2.2 生成 Release APK

1. 选择刚创建的密钥库
2. 输入密码
3. `Build Variants`: 选择 `release`
4. `Signature Versions`: ✅ V1 和 ✅ V2 都勾选
5. 点击 `Next` → `Finish`

生成完成后，APK 位置：
```
app/release/app-release.apk
```

### 2.3 测试 Release APK

在真机上安装测试：

```bash
# 连接手机，开启 USB 调试
adb install app/release/app-release.apk

# 或直接复制 APK 到手机安装
```

**测试清单：**
- [ ] 首次启动引导正常显示
- [ ] 权限请求弹出
- [ ] 下载壁纸功能
- [ ] 三态按钮切换
- [ ] 保存到相册
- [ ] 撤销功能
- [ ] 看板娘对话
- [ ] 彩蛋触发（10次点击 Logo）

---

## 步骤三：准备应用商店资料

### 3.1 应用图标

**需要准备的尺寸：**
- 512x512 PNG（Google Play 高分辨率图标）
- 1024x500 PNG（Google Play 特色图片）

**当前图标路径：**
```
app/src/main/res/mipmap-xxxhdpi/ic_launcher.png
```

### 3.2 应用截图

**要求：**
- 至少 2 张，推荐 4-8 张
- 尺寸：1080x2400 或 1440x3120（手机屏幕比例）
- 格式：PNG 或 JPEG

**建议截图内容：**
1. **主界面** - 展示极简设计和预览卡片
2. **按钮状态** - 展示 Pink/Blue 双模式
3. **标签系统** - 展示 Strict/Soft 标签
4. **看板娘对话** - 展示可爱的互动
5. **设置界面** - 展示滑动条和定时功能
6. **壁纸效果** - 展示实际应用的壁纸

**如何截图：**
在真机上运行应用 → Android Studio → `Logcat` 旁边有 📷 截图按钮

### 3.3 应用描述

#### 简短描述（80字符以内）
```
Minimalist anime wallpaper app with an adorable interactive mascot. Nyanpasu~
```

#### 完整描述

```markdown
🎨 Nyanpasu - Your Anime Wallpaper Companion

Nyanpasu is a beautiful, minimalist wallpaper app that brings anime art to your device with a delightful interactive mascot!

✨ KEY FEATURES

🖼️ Smart Wallpaper System
• Automatic anime wallpaper fetching and application
• Separate wallpapers for Home and Lock screens
• Style slider to customize your preferences
• Custom tag system (Strict/Soft modes)
• Auto-update scheduling (6/12/24 hours or daily)

🤖 Interactive Mascot
• 200+ cute quotes and anime references
• Random idle chat with personality
• Hidden Easter egg (hint: tap the logo!)
• Packed with kaomoji and anime culture ♪(´▽｀)

🎯 Dual Wallpaper Mode
• Pink Button = Sync mode (same wallpaper everywhere)
• Blue Button = Independent mode (different wallpapers!)
• Gray Button = Off

🛠️ Powerful Tools
• Undo system (up to 5 previous wallpapers)
• Save to gallery
• Smart fallback when tags fail
• Auto-cleanup old files

🎨 Design Philosophy
• Porcelain White theme for maximum elegance
• Smooth animations and haptic feedback
• No ads, no tracking, just pure functionality

📱 Requirements
• Android 7.0 (Nougat) or higher
• Internet connection for fetching wallpapers
• Storage permission for saving images

💫 Perfect For
• Anime fans who love beautiful artwork
• Users who want fresh wallpapers regularly
• Anyone tired of boring wallpapers

Made with ❤️ by a veteran otaku. Nyanpasu~ 👋

---

📧 Contact & Support
• Report bugs or request features on GitHub
• Follow development updates on Telegram

🌟 Open Source
This app is open source! Check out the code on GitHub and contribute!
```

### 3.4 分类和标签

**Category**: Personalization

**Tags**:
- anime
- wallpaper
- minimalist
- customization
- cute
- otaku

### 3.5 内容分级

**适合所有年龄**（API 使用 r18=0，无成人内容）

### 3.6 隐私政策

创建一个简单的隐私政策页面：

```markdown
# Privacy Policy for Nyanpasu Wallpaper

Last updated: January 22, 2026

## Data Collection
Nyanpasu does NOT collect any personal information. We respect your privacy.

## Data Usage
- The app downloads wallpapers from public API (api.lolicon.app)
- All settings are stored locally on your device
- No analytics, no tracking, no ads

## Permissions
- INTERNET: To fetch wallpapers
- SET_WALLPAPER: To apply wallpapers
- READ_MEDIA_IMAGES: To save wallpapers to gallery (optional)

## Contact
If you have questions: https://t.me/FranklinNexus

---

Your privacy is important to us. Nyanpasu~ 👋
```

**将此页面上传到：**
- GitHub Pages（推荐）
- 你的博客
- Google Sites（免费）

---

## 步骤四：上传到 Google Play

### 4.1 创建 Google Play 开发者账号

1. 访问 [Google Play Console](https://play.google.com/console)
2. 支付 25 美元注册费（一次性）
3. 填写开发者信息
4. 等待审核（通常 1-2 天）

### 4.2 创建新应用

1. 登录 Google Play Console
2. 点击 `Create app`
3. 填写基本信息：
   - **App name**: Nyanpasu
   - **Default language**: English (United States)
   - **App or game**: App
   - **Free or paid**: Free
   - **Declarations**: 勾选所有必需项

### 4.3 填写应用内容

#### Store settings（商店设置）

1. **App details**:
   - App name: Nyanpasu
   - Short description: *(使用前面准备的简短描述)*
   - Full description: *(使用前面准备的完整描述)*

2. **Graphics**:
   - App icon: 上传 512x512 PNG
   - Feature graphic: 上传 1024x500 PNG
   - Screenshots: 上传 4-8 张截图

3. **Categorization**:
   - App category: Personalization
   - Tags: anime, wallpaper, customization

4. **Contact details**:
   - Email: 你的邮箱
   - Website: https://github.com/YourUsername/ACGWallpaper
   - Privacy policy: *(上传隐私政策后的链接)*

#### App content（应用内容）

1. **Privacy policy**: 粘贴隐私政策 URL
2. **App access**: All features available to all users
3. **Ads**: No ads
4. **Content rating**: 
   - Complete questionnaire
   - Should get EVERYONE rating
5. **Target audience**: Ages 13+
6. **Data safety**: 
   - Select "No data collected"
   - Complete all sections

### 4.4 上传 APK/AAB

**推荐使用 AAB（Android App Bundle）：**

生成 AAB：
```bash
# 在 Android Studio Terminal
./gradlew bundleRelease
```

AAB 位置：`app/release/app-release.aab`

**上传流程：**

1. 在 Google Play Console，点击 `Production`
2. 点击 `Create new release`
3. 上传 `app-release.aab`
4. 填写 Release notes（版本说明）：

```
Initial Release - v1.0.0

🎉 Welcome to Nyanpasu!

✨ Features:
• Smart anime wallpaper system
• Dual wallpaper mode (Home & Lock)
• Interactive mascot with 200+ quotes
• Style customization slider
• Custom tag system
• Auto-update scheduling
• Undo system
• Save to gallery

Made with ❤️ for anime fans!

Nyanpasu~ 👋
```

5. 点击 `Save` → `Review release`
6. 点击 `Start rollout to Production`

### 4.5 等待审核

- **审核时间**：通常 1-7 天
- **状态检查**：Google Play Console → Dashboard
- **通知**：审核结果会发邮件通知

### 4.6 发布后

**审核通过后：**

1. **更新 README.md**:
   ```markdown
   ## 📦 Download
   
   [![Google Play](https://img.shields.io/badge/Google%20Play-Download-green)](你的GooglePlay链接)
   ```

2. **创建 GitHub Release**:
   - 前往 GitHub → Releases → `Create a new release`
   - Tag: `v1.0.0`
   - Title: `Nyanpasu v1.0.0 - Initial Release`
   - Description: 粘贴版本说明
   - 上传 APK 文件

3. **宣传渠道**:
   - Telegram 频道
   - 博客文章
   - Reddit (r/androidapps, r/anime)
   - Twitter/X

---

## 🎉 完成！

恭喜！你的应用现在：
- ✅ 在 GitHub 开源
- ✅ 在 Google Play 发布
- ✅ 可供全球用户下载

---

## 📊 后续维护

### 监控反馈
- Google Play Console → Reviews（查看用户评价）
- GitHub Issues（收集 Bug 报告）
- Crashlytics（如果添加了崩溃报告）

### 更新流程
1. 修改代码
2. 修改 `versionCode` 和 `versionName`（在 `build.gradle.kts`）
3. 生成新的 APK/AAB
4. 上传到 Google Play
5. 创建 GitHub Release

---

## ❓ 常见问题

**Q: 审核被拒绝了怎么办？**
A: 查看拒绝原因，修改后重新提交。常见原因：
   - 隐私政策缺失或不完整
   - 截图不符合要求
   - 内容分级不准确

**Q: 需要多久才能在 Google Play 搜索到？**
A: 审核通过后 2-48 小时内可搜索到

**Q: 可以免费发布吗？**
A: Google Play 需要 25 美元注册费（一次性）
   GitHub 完全免费

**Q: 密钥库丢失了怎么办？**
A: 无法更新应用！必须发布新应用。所以一定要备份密钥库！

---

## 🔐 密钥库备份建议

**务必备份以下文件：**
- `nyanpasu-keystore.jks`（密钥库文件）
- 密码信息（保存在安全的地方）

**备份位置建议：**
- 加密的 U 盘
- 云盘（加密后上传）
- 密码管理器

---

**祝你发布顺利！Nyanpasu~ 🎉**
