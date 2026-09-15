# NHSCustomKeyboard 1-Click Wi-Fi Deploy Script for Antigravity / PowerShell
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host " NHSCustomKeyboard Deploy to Android Device" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

# Ensure Java and ADB are in the current session's PATH if needed
if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    $adbPath = "C:\Users\Sohan\AppData\Local\Android\Sdk\platform-tools"
    if (Test-Path "$adbPath\adb.exe") {
        $env:Path = "$adbPath;$env:Path"
    }
}
if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    $jdkPath = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
    if (Test-Path "$jdkPath\bin\java.exe") {
        $env:JAVA_HOME = $jdkPath
        $env:Path = "$jdkPath\bin;$env:Path"
    } elseif (Test-Path "C:\Program Files\Android\Android Studio\jbr\bin\java.exe") {
        $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
        $env:Path = "C:\Program Files\Android\Android Studio\jbr\bin;$env:Path"
    }
}

# 1. Check for connected ADB device
$deviceLines = adb devices | Select-String -Pattern "\b(device)\b"
if (-not $deviceLines) {
    adb connect 192.168.1.4:39435 | Out-Null
    $deviceLines = adb devices | Select-String -Pattern "\b(device)\b"
}
if (-not $deviceLines) {
    Write-Host "No device connected via ADB." -ForegroundColor Red
    Write-Host "Please connect your phone via Wi-Fi ADB first:" -ForegroundColor Yellow
    Write-Host "  adb connect <YOUR_PHONE_IP>:<PORT>" -ForegroundColor White
    exit 1
}

$targetDevice = ($deviceLines | ForEach-Object { ($_ -split "`t")[0] })[0]
Write-Host "Found connected device ($targetDevice). Building and installing debug APK..." -ForegroundColor Green

# 2. Build and install to phone
.\gradlew assembleDebug
if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed. Check errors above." -ForegroundColor Red
    exit $LASTEXITCODE
}

$apkPath = "build\outputs\apk\debug\OsthirKeyboard-debug.apk"
Write-Host "Installing $apkPath to $targetDevice..." -ForegroundColor Cyan
adb -s $targetDevice install -r $apkPath
if ($LASTEXITCODE -ne 0) {
    Write-Host "Direct install failed (signature mismatch). Uninstalling existing package and reinstalling..." -ForegroundColor Yellow
    adb -s $targetDevice uninstall com.nhs.customkeyboard.debug
    adb -s $targetDevice install -r $apkPath
}

if ($LASTEXITCODE -ne 0) {
    Write-Host "Installation failed. Check errors above." -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "Successfully installed!" -ForegroundColor Green

# 3. Launch keyboard settings activity on phone
Write-Host "Launching Keyboard Settings on phone..." -ForegroundColor Cyan
adb -s $targetDevice shell am start -n com.nhs.customkeyboard.debug/com.nhs.customkeyboard.SettingsActivity | Out-Null

Write-Host "Done! Keyboard is ready on your device." -ForegroundColor Green
