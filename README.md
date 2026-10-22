# Rider Assistant (Android APK & SDK Build)

**Hands-Free Voice Gesture Control & Bluetooth Earbuds Companion**

Rider Assistant is an Android application designed for motorcycle riders and drivers. It enables hands-free upward swipe gestures when saying **"Rider"** or **"Buddy"**, operating system-wide even when other apps (Instagram, YouTube Shorts, Google Maps) are open or when the app is running in the background.

---

## 🚀 Key Features

### 1. System-Wide Background Swipe Gesture
- **Works when app is closed / minimized**: Powered by Android's `AccessibilityService` (`RiderAccessibilityService`).
- **Gesture Action**: Center screen upward swipe (`startY: 82%` to `endY: 25%`, 350 ms duration) matching natural thumb/finger scrolls.
- **Privacy First**: Does not inspect or retrieve screen contents.

### 2. Background Voice Wake-Word Service
- **Wake Words**: Choose between **"RIDER"** or **"BUDDY"**.
- **Continuous Foreground Service**: Uses `VoiceWakeWordService` with `FOREGROUND_SERVICE_MICROPHONE` and an ongoing notification so Android will not kill the background listener while riding.
- **Haptic Vibration**: Vibrates briefly upon wake-word detection so riders receive tactile feedback through their pocket or phone mount.

### 3. Bluetooth Earbuds Battery Dashboard
- **Disconnected State**: Shows the charging case icon and "Not Connected" with quick scan/connect.
- **Connected State**: Displays device name, individual Left Earbud (L) battery, Center Charging Case battery, and Right Earbud (R) battery with progress bars.

---

## 🛠️ GitHub Automated Build (GitHub Actions)

A ready-to-use GitHub Actions workflow is included at `.github/workflows/build-apk.yml`.

Whenever you push code to GitHub:
1. Go to the **Actions** tab on your GitHub repository.
2. Select **Build Android APK** workflow.
3. Click on the latest run and download the generated **`RiderAssistant-debug.apk`** under **Artifacts**.
4. Install the APK on your Android phone.

---

## 📱 How to Build Locally with Android SDK / Gradle

```bash
# Clone the repository
git clone https://github.com/RIDER013/RU.git
cd RU

# Build Debug APK using Gradle wrapper
./gradlew assembleDebug

# Output APK path:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## ⚙️ Android Phone Setup (Real Life Usage)

1. **Install APK**: Install `app-debug.apk` on your phone.
2. **Grant Permissions**:
   - Audio / Microphone (for wake-word detection).
   - Nearby Devices / Bluetooth (for earbuds battery reading).
   - Notifications (for persistent background service).
3. **Enable Accessibility Service**:
   - Open Phone Settings ➔ **Accessibility** ➔ **Installed Apps / Services**.
   - Tap **Rider Assistant Swipe Control** and turn it **ON**.
4. **Start Listening**:
   - Open Rider Assistant, pick **Rider** or **Buddy**, and tap **Start Listening**.
   - You can now open any app (Instagram Reels, YouTube Shorts, TikTok, Navigation) or lock and ride — saying *"Rider"* will swipe up automatically!
