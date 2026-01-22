# 📸 Screenshots Guide

This folder contains app screenshots for marketing purposes (Google Play, GitHub, etc.)

## 📋 Screenshot Checklist

### Required Screenshots (6 minimum)

Save your screenshots here with these filenames:

- [ ] `01_main.png` - Main interface showing preview card, buttons, and mascot
- [ ] `02_dual.png` - Dual-stream mode with "Home Screen" or "Lock Screen" indicator
- [ ] `03_tags.png` - Tag system with multiple chips (Pink Strict, Gray Soft)
- [ ] `04_mascot.png` - Mascot speech bubble with a quote
- [ ] `05_dev.png` - Developer card (tap logo 10 times) with confetti
- [ ] `06_editor.png` - WYSIWYG editor showing zoomed/panned image

### Optional Screenshots (for bonus points)

- [ ] `07_schedule.png` - Schedule dialog showing frequency options
- [ ] `08_settings.png` - Style slider and auto-update toggle
- [ ] `09_before_after.png` - Side-by-side comparison of wallpapers

---

## 🎨 Screenshot Specifications

### Google Play Requirements

| Aspect | Requirement | Recommended |
|--------|-------------|-------------|
| **Format** | PNG or JPEG | PNG (for quality) |
| **Aspect Ratio** | 16:9 or 9:16 | 9:16 (portrait) |
| **Min Dimension** | 320 px | 1080 px |
| **Max Dimension** | 3840 px | 1920 px |
| **Recommended Size** | - | **1080 x 1920 px** |
| **Color Space** | RGB | sRGB |
| **Max File Size** | 8 MB | < 2 MB |

### GitHub Display

For README.md, screenshots will be displayed at ~600px width, so:
- Use high-resolution originals (1080 x 1920)
- They will auto-scale in Markdown tables

---

## 🎬 How to Take Screenshots

### Method 1: Android Studio Emulator

1. **Run the app** in the emulator (Pixel 6, Android 13 recommended)
2. Navigate to the desired screen
3. **Take screenshot**:
   - Click the **camera icon** 📷 in the emulator toolbar
   - Or press `Ctrl + S` (Windows) / `Cmd + S` (Mac)
4. Screenshots save to: `C:\Users\YourName\Pictures\Screenshots`

### Method 2: Physical Device (Better Quality)

1. **Run the app** on your phone
2. Navigate to the desired screen
3. **Take screenshot**:
   - **Most Android**: `Power + Volume Down`
   - **Samsung**: `Power + Home`
   - **Xiaomi/MIUI**: `Volume Down + Menu`
4. Transfer via USB or Google Photos

### Method 3: ADB (Command Line)

```bash
# Take screenshot
adb shell screencap -p /sdcard/screenshot.png

# Pull to computer
adb pull /sdcard/screenshot.png screenshots/01_main.png
```

---

## ✂️ How to Edit Screenshots

### Resize to 1080 x 1920 px

**Option 1: Online Tool**
- Visit: https://www.iloveimg.com/resize-image
- Upload screenshot
- Set dimensions: **1080 x 1920 px**
- Download

**Option 2: Photoshop / GIMP**
- Open image
- Image → Image Size → 1080 x 1920 px
- Export as PNG

**Option 3: Android Studio**
- Right-click image → Open in → External Editor
- Use system image editor

### Add Text Overlays (Optional but Recommended)

Use [Canva](https://www.canva.com) or Figma to add captions:

**Example captions**:
1. `01_main.png`: "Dual-Stream Engine - Pink for Sync, Blue for Independent"
2. `02_dual.png`: "Separate Control for Home & Lock Screens"
3. `03_tags.png`: "Smart Tags - Strict (🔒) or Soft (🎲) Mode"
4. `04_mascot.png`: "Interactive Mascot with 200+ Quotes"
5. `05_dev.png`: "Hidden Easter Egg - Tap Logo 10 Times!"
6. `06_editor.png`: "WYSIWYG Editor - Pan, Zoom, Crop"

**Design tips**:
- Use semi-transparent background for text (Black with 50% opacity)
- White text for contrast
- Font: Roboto or Noto Sans (Android standard)
- Font size: 36-48 px

---

## 🎨 Feature Graphic (Google Play)

Create a banner image for Google Play's main listing page.

### Specifications
- **Size**: 1024 x 500 px
- **Format**: PNG or JPEG
- **Content**: App name + key visual

### Quick Creation with Canva

1. Go to https://www.canva.com
2. Create custom size: **1024 x 500 px**
3. Design elements:
   - **Background**: Gradient (Pink #FF80AB → Blue #64B5F6)
   - **App Icon**: Place on left (150 x 150 px)
   - **App Name**: "Nyanpasu" (Bold, 80px)
   - **Tagline**: "Daily Anime Wallpapers" (Regular, 36px)
   - **Preview**: 1-2 phone mockups showing the app
4. Download as PNG
5. Save as `feature_graphic.png` in this folder

**Template inspiration**:
```
[App Icon]  Nyanpasu
            Daily Anime Wallpapers with Dual-Stream Control
            
            [Phone Mockup 1]  [Phone Mockup 2]
```

---

## 🖼️ App Icon for Google Play

Google Play requires a **512 x 512 px** app icon.

### Convert from Current Icon

Your current icon is in `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png` (192 x 192).

**Upscale method**:
1. Open ic_launcher.png
2. Use https://www.iloveimg.com/resize-image
3. Set dimensions: **512 x 512 px**
4. **Important**: Use "Fit without stretching" (add transparent borders if needed)
5. Download and save as `icon_512.png` in this folder

---

## 📂 Folder Structure

After you've prepared everything:

```
screenshots/
├── README.md                  (this file)
├── 01_main.png               (1080 x 1920)
├── 02_dual.png               (1080 x 1920)
├── 03_tags.png               (1080 x 1920)
├── 04_mascot.png             (1080 x 1920)
├── 05_dev.png                (1080 x 1920)
├── 06_editor.png             (1080 x 1920)
├── feature_graphic.png       (1024 x 500)
└── icon_512.png              (512 x 512)
```

---

## 🚀 Where These Are Used

### GitHub README.md
```markdown
| Main Interface | Dual-Stream Mode | Tag System |
|:---:|:---:|:---:|
| ![Main](screenshots/01_main.png) | ![Dual](screenshots/02_dual.png) | ![Tags](screenshots/03_tags.png) |
```

### Google Play Console
1. **Store Listing** → **Phone screenshots**: Upload `01_main.png` ~ `06_editor.png`
2. **Store Listing** → **Feature graphic**: Upload `feature_graphic.png`
3. **Store Listing** → **App icon**: Upload `icon_512.png`

### GitHub Releases
Attach screenshots to release notes for visual changelog

### Social Media
Use for Twitter/Reddit posts when sharing your app

---

## 🎯 Screenshot Content Tips

### 01_main.png - First Impression Matters!
- Show a **beautiful wallpaper** in the preview
- Mascot speech bubble visible
- All buttons clearly visible
- Clean, uncluttered UI

### 02_dual.png - Show the Unique Feature
- Enable dual-mode (Home ≠ Lock)
- Show "Home Screen" or "Lock Screen" indicator at top
- Use contrasting wallpapers (e.g., character vs scenery)

### 03_tags.png - Demonstrate Customization
- Add 5-8 tags to the chip group
- Mix of Pink (Strict) and Gray (Soft) chips
- Include popular tags like "genshin", "blue_archive"

### 04_mascot.png - Showcase Personality
- Trigger a cute quote (tap the logo)
- Capture the speech bubble mid-animation
- Use a quote with kaomoji like "(〃＾▽＾〃)"

### 05_dev.png - Easter Egg Appeal
- Tap logo 10 times to trigger developer dialog
- Capture with confetti animation visible
- Shows app has "soul" and attention to detail

### 06_editor.png - New Feature Highlight
- Zoom in on the preview image
- Show part of the image cropped out of view
- Optional: Add finger gesture overlay (can edit in post)

---

## 🎨 Design Consistency

All screenshots should have:
- ✅ Same device frame (if using mockups)
- ✅ Same status bar style
- ✅ Same time (e.g., "12:00")
- ✅ Full battery icon
- ✅ WiFi connected
- ✅ No notifications in status bar

**Device mockup tools**:
- **Figma**: Free device frames
- **Shots.so**: https://shots.so (online mockup generator)
- **MockuPhone**: https://mockuphone.com

---

## ✅ Quality Checklist

Before uploading to Google Play:

- [ ] All screenshots are **1080 x 1920 px**
- [ ] All screenshots are **PNG format**
- [ ] No personal information visible (real phone number, etc.)
- [ ] No placeholder text (like "Lorem ipsum")
- [ ] UI text is readable (not too small)
- [ ] No blurry or pixelated areas
- [ ] Consistent branding (colors, fonts)
- [ ] No offensive or inappropriate content
- [ ] Feature graphic includes app name
- [ ] App icon is crisp at 512 x 512 px

---

## 🆘 Need Help?

### Can't take good screenshots?
- Use emulator instead of physical device (cleaner UI)
- Use light mode (better contrast)
- Clear all notifications before screenshotting

### Screenshots look low quality?
- Make sure you're using PNG (not JPEG)
- Use 1080 x 1920 native resolution
- Don't upscale from smaller images

### Feature graphic looks amateurish?
- Use Canva templates for "App Feature Graphic"
- Study other apps on Google Play for inspiration
- Keep it simple: Icon + Name + One Key Feature

---

## 🎉 You're Ready!

Once this folder has:
- ✅ 6+ high-quality screenshots
- ✅ Feature graphic (1024 x 500)
- ✅ App icon (512 x 512)

You can proceed to the **Google Play Store listing** section in `RELEASE_GUIDE.md`.

**Good luck! (๑˃ᴗ˂)ﻭ**

**Nyanpasu~ (〃＾▽＾〃) 📸**
