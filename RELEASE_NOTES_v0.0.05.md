# 🚀 OsthirKeyboard v0.0.05 — Silent Tasker Execution, Direct Task Actions & UI Refinements

Welcome to **OsthirKeyboard v0.0.05**! This release brings silent, non-intrusive Tasker automation execution, direct key action bindings for tasks, comprehensive UI/Activity polishing across Android versions, and improved deployment tooling.

---

### 🌟 Key Highlights

- **⚡ Silent Tasker Execution**: Removed popup toast messages on every task run, allowing seamless and distraction-free background automation while typing.
- **🎯 Direct Key Task Execution**: Full support for `task:<name>` and `tasker:<name>` actions directly from layout keys without typing ghost characters.
- **🛡️ Task Mapping Validation**: Clear alerts if an unmapped task is triggered, directing users to configure it in `Settings > Tasker & Automation`.
- **🎨 Activity & Window Polish**: Consistent theme styling, edge-to-edge window support, and streamlined back handling across Settings, Layout Editor, Dictionary, and Theme activities.
- **🛠️ Enhanced Deploy Tooling**: Smarter device detection and IP fallback for rapid local test deployments.

---

### ✨ What's New & Improvements

#### ⚡ Tasker & Automation
- **Quiet Execution**: Eliminated the `"Running Tasker: <task>"` toast message on both custom key swipes/taps and candidate bar triggers.
- **Direct Layout Actions**: Native parsing and handling for `task:<name>` / `tasker:<name>` in custom XML layouts.
- **Targeted Error Reporting**: Suppressed redundant timeout notifications for fire-and-forget key actions while retaining alerts for real communication issues.

#### 🎨 Activity & UI Polish
- **Modern Activity Architecture**: Modernized styling and window handling across `SettingsActivity`, `LayoutEditorActivity`, `DictionariesActivity`, `UserDictionaryBackupActivity`, and `ThemeActivity`.
- **Space Bar & Layout Styling**: Polished custom layout rendering and documentation updates.

#### 🔧 Developer Experience
- **Automated Deployment**: Enhanced `deploy.ps1` with automatic device detection and IP caching.

---

### 📦 Installation

1. Download **`OsthirKeyboard.apk`** from the Assets below.
2. Open the file on your Android device to install or update.
3. Your custom settings, themes, and dictionary data will remain intact.

---

### 💬 Feedback & Community

Found a bug or have a suggestion?
- **Issues**: [GitHub Issues](https://github.com/Nsohan/OsthirKeyboard/issues)
- **Repository**: [Nsohan/OsthirKeyboard](https://github.com/Nsohan/OsthirKeyboard)
