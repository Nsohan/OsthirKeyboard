# 🚀 OsthirKeyboard v0.0.03 — Custom Themes Studio, Suggestions Polish & Live Wallpaper

Welcome to **OsthirKeyboard v0.0.03**! This release brings a brand-new **Custom Image & Wallpaper Theme Studio**, major enhancements to suggestion candidates and user learning, interactive suggestion toolbar customization, and important visual contrast fixes across light and dark modes.

---

### 🌟 Key Highlights

- **🎨 Custom Image & Wallpaper Theme Studio**: Select any photo or wallpaper, crop to the perfect ratio, adjust brightness and background blur, and customize key opacity in real-time with an interactive live keyboard preview.
- **🔓 Independent Custom Key Opacity**: Custom theme opacity is now decoupled from global keyboard opacity—style your wallpaper themes without altering your default presets!
- **📜 Expanded Candidate Word Grid**: Long-press any suggestion candidate to expand into a high-visibility candidate grid with built-in drag-to-delete support for learned words.
- **🛠️ Suggestion Toolbar Customizer**: Toggle individual toolbar icons, customize icon size, and adjust spacing in real-time within Settings.
- **🌐 Contrast & Theme Polish**: Resolved text visibility issues across the translation language picker, suggestion action popups, and candidate menus on custom themes.

---

### ✨ What's New & Improvements

#### 🎨 Custom Themes & Visual Studio
- **Unified Live Crop & Editor (`ThemeCropActivity`)**: Crop your custom background images and immediately adjust brightness, background blur, and key transparency with live feedback.
- **Custom Theme Key Opacity Isolation**: Fixed an issue where tweaking custom theme key opacity inadvertently overwrote global `Settings > Style > Adjust key opacity`.
- **Theme Preview Bottom Sheet**: Preview saved themes with a live mock keyboard before applying.
- **Theme Storage & Persistence**: Streamlined theme management via `ThemeRepository` with automatic fallback handling.

#### 💡 Intelligent Suggestions & Candidate Grid
- **Candidate Long-Press Expansion (`ExpandedCandidatesPopup`)**: Long-press any suggestion to open an expanded candidate grid for fast browsing of predictions.
- **Drag-to-Delete Learned Words**: Easily remove mistyped or unwanted learned words directly from the expansion popup.
- **Opaque Surface & Contrast**: Enforced solid card background and dynamic text contrast for the candidate popup, ensuring perfect readability over any background image or wallpaper.

#### 🛠️ Suggestion Toolbar Customization
- **Modular Suggestion Tools**: Rearrange tools, hide unused shortcuts, and configure icon size and gap via the new `SuggestionToolsPreference`.
- **Tactile Micro-Animations**: Replaced toolbar spin animation with a snappy, tactile bounce effect for instantaneous responsiveness.

#### 🌐 Translation & UI Contrast Fixes
- **Translation Language Picker**: Replaced low-contrast label colors with theme-adaptive surface colors, eliminating white-on-white text in custom and light themes.
- **Suggestion Action Dialog**: Cleaned up contrast and padding for word-action options.
- **Dynamic System Accents**: Modernized dividers and accent elements to match modern Android Material guidelines.

#### 🔤 Typing Ergonomics
- **Gboard-Style Shift States**: Refined 3-state Shift toggle (off, shift-next, caps lock) with consistent auto-reset after punctuation and space.
- **Keyboard2 Core Stability**: Hardened key layout handling and pointer gesture recognition.

---

### 📦 Installation

1. Download **`OsthirKeyboard.apk`** from the Assets below.
2. Open the file on your Android device to install or update.
3. Your custom settings, themes, and learned dictionaries will be preserved.

---

### 💬 Feedback & Community

Found a bug or have a suggestion?
- **Issues**: [GitHub Issues](https://github.com/Nsohan/OsthirKeyboard/issues)
- **Repository**: [Nsohan/OsthirKeyboard](https://github.com/Nsohan/OsthirKeyboard)
