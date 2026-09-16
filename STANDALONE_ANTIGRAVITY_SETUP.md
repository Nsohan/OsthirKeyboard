# Antigravity Standalone Android Development & Deployment Guide

This guide explains how to build, test, and deploy **NHSCustomKeyboard** directly from **Antigravity** on your laptop **without installing or opening Android Studio**.

---

## 1. One-Time Laptop Setup (No Android Studio Required)

Run these two commands in PowerShell (or have Antigravity run them for you):

### A. Install Java OpenJDK 21
```powershell
& "$env:LOCALAPPDATA\Microsoft\WindowsApps\winget.exe" install EclipseAdoptium.Temurin.21.JDK
```
*Accept any Windows UAC administrator prompt if it pops up.*

### B. Install Standalone ADB (Android Platform Tools)
```powershell
& "$env:LOCALAPPDATA\Microsoft\WindowsApps\winget.exe" install Google.AndroidPlatformTools
```

### C. Android SDK Path (`local.properties`)
Make sure `local.properties` in your project root points to your Android SDK folder:
```properties
sdk.dir=C\:\\Users\\Sohan\\AppData\\Local\\Android\\Sdk
```
*(Even if you uninstall Android Studio, the SDK folder can stay right there on your laptop).*

---

## 2. Connect Your Phone via Wi-Fi ADB

No USB cable is required! You can pair and connect over your local Wi-Fi:

### Step 1: Enable Wireless Debugging on Phone
1. Go to **Settings** ➔ **Developer Options**.
2. Turn on **Wireless debugging**.
3. Tap **"Pair device with pairing code"**. You will see:
   - **IP address & pairing port**: e.g., `192.168.1.50:37123`
   - **6-digit Wi-Fi pairing code**: e.g., `482910`

### Step 2: Pair in Antigravity Terminal
Run:
```powershell
adb pair <IP>:<PAIRING_PORT> <CODE>
# Example: adb pair 192.168.1.50:37123 482910
```

### Step 3: Connect to Device
Look at the main Wireless Debugging screen on your phone for the **IP and main connection port**:
```powershell
adb connect <IP>:<PORT>
# Example: adb connect 192.168.1.50:5555
```

### Step 4: Verify
```powershell
adb devices
```
You should see your device listed as `connected` or `device`.

---

## 3. Deploy & Run to Phone from Antigravity

Whenever you make code changes, you can deploy immediately.

### Option A: Use the 1-Click Deploy Script
In PowerShell inside the project root:
```powershell
.\deploy.ps1
```

### Option B: Run via Gradle & ADB Manually
```powershell
# 1. Build and install to the connected phone
.\gradlew installDebug

# 2. Open keyboard settings on your phone
adb shell am start -n com.nhs.customkeyboard.debug/com.nhs.customkeyboard.SettingsActivity
```

### Option C: Ask Antigravity in Chat
You can simply type:
> *"Deploy this to my phone"*  
> *"Install the latest debug build on my connected device"*

Antigravity will run the build and push it to your device automatically.

---

## 4. Build & Publish a Production Release (1-Click Automation)

To build, sign, tag, and publish an official GitHub Release with release notes and APK assets:

### 1-Click Automated Release
```powershell
.\release.ps1
```
This script automatically:
1. Reads `versionName` and `versionCode` from `build.gradle.kts`.
2. Runs `.\gradlew assembleRelease` to compile and sign the release APK using `release.keystore`.
3. Packages both `OsthirKeyboard.apk` (required by the in-app `UpdateChecker`) and `OsthirKeyboard-v<version>.apk`.
4. Computes SHA-256 hashes and file sizes.
5. Loads or generates release notes (from `RELEASE_NOTES_v<version>.md` or Git history).
6. Creates the Git tag `v<version>` and pushes commits + tags to GitHub.
7. Publishes the release to GitHub via the REST API and uploads the APK assets directly.

#### Useful Release Flags:
```powershell
# Preview everything without modifying git or GitHub
.\release.ps1 -DryRun

# Publish without rebuilding the APK (if already built)
.\release.ps1 -SkipBuild

# Build and tag locally without pushing to GitHub
.\release.ps1 -SkipPush
```

---

## 5. Useful Commands Cheat Sheet

| Action | Command |
| :--- | :--- |
| **Check connected devices** | `adb devices` |
| **Restart ADB server** | `adb kill-server; adb start-server` |
| **View real-time keyboard logs** | `adb logcat -s Keyboard2:V Keyboard2View:V` |
| **Uninstall debug version** | `adb uninstall com.nhs.customkeyboard.debug` |
| **Clean build directory** | `.\gradlew clean` |
