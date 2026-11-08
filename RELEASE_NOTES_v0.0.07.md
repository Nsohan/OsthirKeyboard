# 🚀 OsthirKeyboard v0.0.07 — Number Row Symbol Independence, Navigation Bar Insets & Startup Stability

Welcome to **OsthirKeyboard v0.0.07**! This release unlinks main keyboard swipe symbols from the top number row, fixes edge-to-edge navigation bar overlap on Android 15, eliminates the startup missing-dictionary banner race condition, and refines custom Bengali layout symbol bindings.

---

### 🌟 Key Highlights

- **🔢 Number Row Symbol Isolation & Digit Stripping**: The Number Row now strips only duplicate number digits (`0-9`, `০-৯`) from the main keys. Custom swipe symbols (`!`, `@`, `#`, `?`, etc.) remain fully visible and operational across all keys, completely independent of whether the Number Row has symbols enabled or disabled.
- **📱 Universal Navigation Bar Auto-Adjust (Android 15 Edge-to-Edge)**: Fixed an issue where the keyboard's bottom row intermittently overlapped the system gesture handle / navigation bar on initial launch. System insets are now statically cached and trigger immediate relayout upon delivery.
- **📚 Startup Dictionary Banner Race Condition Fix**: Resolved a startup lifecycle order bug in `refresh_config()` where default layout switching lagged behind candidate bar initialization. The missing-dictionary status banner now correctly queries the active keyboard layout's language instead of hardcoding the system locale.
- **🇧🇩 Custom Bengali Layout Updates**: Refined punctuation, math operators, and swipe symbols on `beng_custom.xml`.
- **🧪 Automated Test Coverage**: Added comprehensive unit test verification in `LayoutModifierTest` to safeguard number row symbol preservation and digit stripping.

---

### 📦 Installation

1. Download **`OsthirKeyboard.apk`** from the Assets below.
2. Open the file on your Android device to install or update.
3. Your custom layouts, themes, keymaps, and Tasker automation settings will remain intact.

---

### 💬 Feedback & Community

Found a bug or have a suggestion?
- **Issues**: [GitHub Issues](https://github.com/Nsohan/OsthirKeyboard/issues)
- **Repository**: [Nsohan/OsthirKeyboard](https://github.com/Nsohan/OsthirKeyboard)

