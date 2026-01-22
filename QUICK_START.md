# 🚀 快速开始指南 - 30分钟发布你的应用！

## ⏱️ 时间分配
- GitHub 发布：5 分钟
- 生成 APK：10 分钟
- 准备资料：10 分钟
- Google Play 上传：5 分钟

---

## 📝 发布前检查清单

### 第 1 步：代码检查 ✅

```bash
# 在 Android Studio Terminal 运行
./gradlew clean
./gradlew lint
```

确保：
- [ ] 无 Linter 错误
- [ ] 所有功能测试通过
- [ ] 应用在真机上正常运行

---

### 第 2 步：发布到 GitHub（5 分钟）⏱️

#### 2.1 创建 GitHub 仓库
1. 打开 https://github.com/new
2. Repository name: `ACGWallpaper`
3. Description: `A minimalist anime-style wallpaper app`
4. Public ✅
5. 点击 `Create repository`

#### 2.2 推送代码
在 Android Studio Terminal：

```bash
# 初始化
git init
git add .
git commit -m "Initial commit: Nyanpasu v1.0.0"

# 连接到 GitHub（替换你的用户名）
git remote add origin https://github.com/你的用户名/ACGWallpaper.git
git branch -M main
git push -u origin main
```

✅ **完成！** 访问你的仓库查看是否成功

---

### 第 3 步：生成签名 APK（10 分钟）⏱️

#### 3.1 创建密钥库（首次）

1. Android Studio → `Build` → `Generate Signed Bundle / APK`
2. 选择 `APK` → `Next`
3. `Create new...`

**填写信息：**
```
密钥库路径: C:\Users\你的用户名\nyanpasu-keystore.jks
密钥库密码: [强密码，记住它！]
确认密码: [再次输入]

Alias: nyanpasu-key
密钥密码: [密钥密码，记住它！]
Validity: 25 年

姓名: 你的名字
组织单位: Development
组织: Indie
城市: 你的城市
省份: 你的省份
国家代码: CN
```

4. 点击 `OK`

#### 3.2 生成 APK

1. 输入刚才的密码
2. Build Variants: `release`
3. Signature Versions: ✅ V1 ✅ V2
4. 点击 `Finish`

生成位置：`app/release/app-release.apk`

#### 3.3 测试 APK

```bash
# 安装到手机测试
adb install app/release/app-release.apk
```

**测试项：**
- [ ] 首次启动引导
- [ ] 权限请求
- [ ] 下载壁纸
- [ ] 按钮切换
- [ ] 保存功能
- [ ] 看板娘对话

---

### 第 4 步：准备商店资料（10 分钟）⏱️

#### 4.1 截图（在真机上运行应用）

**需要 4-8 张截图：**
1. 主界面
2. 双模式展示
3. 标签系统
4. 看板娘对话
5. 设置界面
6. 壁纸效果

**如何截图：**
- 在手机上运行应用
- Android Studio → Logcat 旁边的 📷 按钮
- 或使用手机自带截图功能

保存到：`screenshots/` 文件夹

#### 4.2 准备图标

**需要的尺寸：**
- 512x512 PNG（应用图标）
- 1024x500 PNG（特色图片）

**快速制作方法：**
- 使用 Figma/Canva（免费在线工具）
- 或使用 Android Asset Studio

#### 4.3 隐私政策

**上传隐私政策到网上：**

选项 1：GitHub Pages（推荐）
```bash
# 在项目根目录
mkdir docs
cp PRIVACY_POLICY.md docs/index.md

# 推送到 GitHub
git add docs/
git commit -m "Add privacy policy"
git push

# 在 GitHub 仓库设置中启用 GitHub Pages
# Settings → Pages → Source: main → /docs
```

URL：`https://你的用户名.github.io/ACGWallpaper/`

选项 2：直接用 GitHub 原始链接
```
https://raw.githubusercontent.com/你的用户名/ACGWallpaper/main/PRIVACY_POLICY.md
```

---

### 第 5 步：上传到 Google Play（5 分钟）⏱️

#### 5.1 创建开发者账号（如果还没有）
1. 访问 https://play.google.com/console
2. 支付 $25 注册费
3. 等待审核（1-2 天）

#### 5.2 创建应用

1. 登录 Google Play Console
2. `Create app`
3. 填写：
   - App name: `Nyanpasu`
   - Language: `English`
   - App/Game: `App`
   - Free/Paid: `Free`

#### 5.3 快速填写（最少必填项）

**App details:**
- Short description: 复制 `STORE_LISTING.md` 中的简短描述
- Full description: 复制完整描述

**Graphics:**
- App icon: 上传 512x512
- Feature graphic: 上传 1024x500
- Screenshots: 上传 4-8 张

**Categorization:**
- Category: `Personalization`

**Contact:**
- Email: 你的邮箱
- Privacy policy: 粘贴隐私政策 URL

**Content rating:**
- 完成问卷（选择所有"No"）
- 应该得到 `EVERYONE` 评级

**Data safety:**
- "No data collected"

#### 5.4 上传 APK

1. 点击 `Production`
2. `Create new release`
3. 上传 `app/release/app-release.apk`
4. Release notes: 复制 `STORE_LISTING.md` 中的发布说明
5. `Save` → `Review release` → `Start rollout`

✅ **完成！** 等待审核（1-7 天）

---

## 🎉 全部完成！

你已经成功：
- ✅ 发布到 GitHub
- ✅ 生成签名 APK
- ✅ 上传到 Google Play

---

## 📞 如遇问题

### 常见问题

**Q: 推送到 GitHub 失败？**
```bash
# 检查 Git 配置
git config --global user.name "你的用户名"
git config --global user.email "你的邮箱"

# 如果需要认证
git remote set-url origin https://你的用户名@github.com/你的用户名/ACGWallpaper.git
```

**Q: APK 生成失败？**
- 检查密钥库密码是否正确
- 确保 `build.gradle.kts` 中版本号正确
- 尝试 `./gradlew clean` 后重新生成

**Q: Google Play 审核被拒？**
- 最常见原因：隐私政策不完整或无法访问
- 检查隐私政策 URL 是否正常打开
- 查看拒绝邮件中的具体原因

**Q: 密钥库忘记密码了？**
- 无法恢复！必须创建新的密钥库
- 意味着无法更新现有应用
- **务必备份密钥库和密码！**

---

## 🔐 密钥库备份提醒

**立即备份以下文件：**
```
nyanpasu-keystore.jks
密码（写在纸上或密码管理器中）
```

**备份到：**
- 加密的 U 盘
- 云盘（加密后）
- 密码管理器（1Password、LastPass 等）

---

## 📊 发布后监控

### 查看统计
Google Play Console → Dashboard
- 安装数
- 崩溃率
- 评分/评价

### 收集反馈
- GitHub Issues
- Google Play 评论
- Telegram 社群

---

## 🚀 下一步

### 立即完成
- [ ] 备份密钥库
- [ ] 在 README 中添加 Google Play 徽章
- [ ] 创建 GitHub Release（tag: v1.0.0）

### 宣传推广
- [ ] 发推文/微博
- [ ] 发布到 Reddit (r/androidapps, r/anime)
- [ ] 在 Telegram 频道分享
- [ ] 写博客文章

### 后续开发
查看 `README.md` 中的 Roadmap

---

**恭喜！你现在是一名 Android 开发者了！🎉**

**Nyanpasu~ 👋**
