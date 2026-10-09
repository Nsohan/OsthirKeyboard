# 🚀 OsthirKeyboard v0.0.06 — Dynamic Monet Theme Support, Dictionary Polish & Stability

Welcome to **OsthirKeyboard v0.0.06**! This release introduces dynamic Monet theme adaptation (reacting live to Android 12+ wallpaper and system accent changes), enhanced theme preview cards, strengthened dictionary management and device locale handling, and comprehensive stability improvements.

---

### 🌟 Key Highlights

- **🎨 Live Dynamic Monet Theme Support**: On Android 12+ (API 31+), the keyboard now dynamically detects system accent palette changes in real-time and refreshes views automatically to match your wallpaper theme without restarting.
- **🖼️ Dynamic Theme Previews**: The theme card picker now dynamically previews Material You / Monet accent colors directly on the theme card.
- **📚 Robust Dictionary & Locale Management**: Hardened dictionary loading, installation, and locale detection against null objects, out-of-bounds queries, and duplicate entries.
- **🛡️ Enhanced Stability & Error Logging**: Replaced narrow `Exception` catches with `Throwable` to safely handle runtime errors and NDK/dictionary loader issues across varied OEM devices.
- **✨ Edge-to-Edge Dictionary Polish**: Refined window navigation bar contrast and lifecycle handling in `DictionariesActivity`.

---

### ✨ What's New & Improvements

#### 🎨 Themes & UI
- **Live Accent Adaptation**: `Keyboard2` now inspects dynamic theme colors (`colorKeyboard`, `colorKey`, `colorKeyAction`, `colorLabelAction`) upon configuration refresh and seamlessly re-creates keyboard views when the system theme shifts.
- **Monet Theme Detection**: Added helper methods to accurately identify Monet dynamic themes (`monet`, `monetlight`, `monetdark`).
- **Dynamic Palette Card Rendering**: `ThemeCardView` draws native system accent colors (`system_accent2_50`, `system_neutral1_0`, `system_accent1_600`) on Android 12+ devices for an accurate preview.
- **Edge-to-Edge Polish**: Disabled navigation bar contrast enforcement on Android 10+ (API 29+) in `DictionariesActivity` for an edge-to-edge look.

#### 📚 Dictionary & Locales
- **Duplicate Prevention**: `DictionaryListView` now prevents duplicate dictionary entries from being displayed in the list.
- **Null & Bounds Safety**: Added defensive guards to `DeviceLocales`, `SupportedDictionaries`, and `Dictionaries` to prevent crashes when system locale or subtype lists are incomplete.
- **Safe Dictionary Installation**: Replaced exception boundaries with `Throwable` handling in `Dictionaries` and `DictionaryListView` to guarantee smooth fallback during dictionary downloads or corrupted asset loads.
- **Application Context Hygiene**: `Dictionaries` now uses `getApplicationContext()` to avoid Activity context retention.

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
