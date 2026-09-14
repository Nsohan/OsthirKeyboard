---
name: nhscustomkeyboard-project
description: >
  Comprehensive reference for the NHSCustomKeyboard project — a heavily extended
  fork of Unexpected Keyboard. Load this skill before writing any code, planning
  any feature, or debugging any issue in this repository.
---

# NHSCustomKeyboard — Project Reference Skill

## 1. Project Identity

| Field | Value |
|---|---|
| **App name** | NHSCustomKeyboard (release label: OsthirKeyboard) |
| **Package ID** | `com.nhs.customkeyboard` |
| **GitHub repo** | `https://github.com/Nsohan/OsthirKeyboard` |
| **Upstream** | [Unexpected Keyboard](https://github.com/Julow/Unexpected-Keyboard) — a lightweight, gesture-driven FOSS Android keyboard |
| **Version** | `0.0.02` (versionCode 2) |
| **Min SDK** | 21 (Android 5.0 Lollipop) |
| **Target SDK** | 36 |
| **Compile SDK** | android-36 |
| **Build system** | Gradle Kotlin DSL (`build.gradle.kts`) |
| **Language** | Java (Java 8 source/target compat) |
| **APK name** | `OsthirKeyboard.apk` |

---

## 2. Upstream vs Fork — What Is Different

The upstream **Unexpected Keyboard** is a minimal, privacy-respecting keyboard whose main innovation is 8-directional swipe gestures on every key. It ships with no transliteration, no Tasker integration, no theme picker, no in-app updater, and no Avro phonetic engine.

**NHSCustomKeyboard adds the following major feature sets (all absent in upstream):**

### 2.1 Keymap / Transliteration Engine
- A JSON-based **live transliteration engine** that converts typed Latin sequences into another script (e.g. Tamil, Greek) using **longest-match prefix replacement**.
- **Toggle-style keymaps**: typing extra characters can cycle back to shorter forms (e.g. `a -> அ`, `aa -> ஆ`, `a` again -> `அ`).
- The engine is applied only to the primary tap output (`c`/`C`) by default; swipe output bypasses it unless `swipekeymap="true"` is declared on the layout.
- Keymaps are stored as named, reusable JSON resources independent of any layout.
- Key files: `KeymapEngine.java`, `Keymap.java`, `KeymapJsonUtils.java`, `KeymapXmlAttrUtils.java`, `prefs/KeymapManager.java`

### 2.2 Keymap Builder (Guided UI)
- A full in-app guided editor for constructing or editing a keymap without hand-writing JSON.
- Features: row-based Output -> Keys editor, Quick Add bar, duplicate key detection, "Dup only" filter, exact-match search (Output or Keys), raw JSON import, and overwrite-protection dialogs.
- Opening a saved keymap pre-fills the builder; rename propagates to every layout referencing the old name (referential integrity).
- Key file: `KeymapBuilderActivity.java` (50 KB)

### 2.3 Independent Key Labels (`cL`, `CL`, `nL`, ...)
- Every output attribute (`c`, `C`, `n`, `N`, `nw`, `NW`, ...) has a matching label attribute (`cL`, `CL`, `nL`, `NL`, ...).
- Labels control only what is **displayed** on a key and never affect what the key sends.
- Omitting a label attribute falls back to showing the output value.
- Rule: labels and outputs are always evaluated separately.

### 2.4 Keyboard Attributes Card in Layout Dialog
- When editing a custom layout, a **Keyboard Attributes** card appears above the XML editor:
  - **Name** field -> bound to `name="..."` attribute.
  - **Keymap** dropdown -> writes/removes `keymap="..."` attribute.
  - **Swipekeymap** checkbox -> bound to `swipekeymap="true"` attribute; auto-disabled when no keymap is selected.
- All three controls are fully bidirectional with the raw XML: GUI changes rewrite XML and vice-versa.
- Key file: `CustomLayoutEditDialog.java` (36 KB)

### 2.5 Default Layout Setting
- A **Default layout** dropdown in Settings (under "Layout, Keymap, and Default Layout"):
  - **Last used layout** (default): restores the layout that was active when the keyboard was last closed.
  - **Specific layout**: always loads a fixed layout when the keyboard opens.
- Key file: `prefs/DefaultLayoutPreference.java`

### 2.6 Space Bar Layout Indicator
- The space bar **displays the active layout's `name` attribute** instead of a plain space glyph, so the user always knows which layout (and implicitly which keymap) is active.

### 2.7 Tasker Automation Engine
- Keyboard can watch typed text for configured triggers and hand content off to Tasker tasks, inserting the result back into the field.
- Two trigger modes: **replace-whole-field** (`##keyword`) and **replace-trigger-only** (`@@keyword`).
- **Expand patterns** (`nhck_patterns`): open-ended prefix+suffix matchers with optional regex constraint; fire for any matching in-between text.
- **One-shot undo**: the first backspace immediately after a replacement swaps back the original trigger text.
- Config stored as a single app-wide JSON; auto-fills missing keys with defaults.
- **Automation Builder** (`TaskerAutomationBuilderActivity`): guided form alternative to raw JSON editing.
- Requires `net.dinglisch.android.tasker.PERMISSION_RUN_TASKS` permission and Tasker's "Allow External Access" setting.
- Key files: `TaskerTriggerEngine.java` (65 KB), `TaskerAutomationConfig.java` (30 KB), `TaskerAutomationBuilderActivity.java` (39 KB), `TaskerBridge.java` (16 KB)
- Tasker plugin (edit + fire): `tasker/TaskerActionEditActivity.java`, `tasker/TaskerActionFireReceiver.java`

### 2.8 Avro Phonetic Engine (Bangla)
- A dedicated phonetic input engine for Bengali/Bangla using the Avro phonetic standard.
- Buffers English phonetic keystrokes, runs `AvroParser`, and commits the parsed Bangla text via `InputConnection` batch edits.
- Supports stepwise backspace (deletes the last raw phonetic character, then re-parses and recommits).
- Active only when the Avro layout is in use; `AvroEngine.is_active()` gates processing.
- Key files: `avro/AvroEngine.java`, `avro/AvroParser.java`

### 2.9 Custom Theme System
- A full theme picker (`ThemeActivity`) with three sections: **My Themes** (user-created), **Default Themes** (built-in presets), and **Colors** (built-in color palettes).
- Users can import a wallpaper image, crop it interactively (`ThemeCropActivity` / `CropImageView`), and apply it as the keyboard background.
- Themes are stored in `SharedPreferences` and reloaded by `ThemeRepository`.
- Preview before applying via `ThemePreviewBottomSheet`.
- Key files: `theme/ThemeActivity.java`, `theme/ThemePreviewBottomSheet.java`, `theme/ThemeCropActivity.java`, `theme/ThemeRepository.java`, `theme/ThemeModel.java`
- **Glide** (`com.github.bumptech.glide:glide:4.16.0`) is used for image loading/display in the theme system.

### 2.10 In-App Update Checker
- Checks the GitHub Releases API for `Nsohan/OsthirKeyboard` on a background thread and compares against the installed `versionName`.
- Shows release notes in a dialog; **Download now** uses the system `DownloadManager` and launches the package installer on completion — no browser needed.
- Handles the "install unknown apps" permission check (Android 8+).
- APK asset is always named `OsthirKeyboard.apk`.
- Key files: `UpdateChecker.java`, `UpdateDialog.java`, `UpdateInstaller.java`, `prefs/CheckUpdatePreference.java`
- Requires `INTERNET`, `ACCESS_NETWORK_STATE`, `REQUEST_INSTALL_PACKAGES` permissions.

### 2.11 Landscape Split-Key Mode
- `LayoutLandscapeModifier` automatically splits each keyboard row at its midpoint, inserting 5 key-width units (`ADDED_WIDTH = 5`) of empty space to adapt to landscape orientation.
- Can optionally insert keys from a custom `split_middle_column.xml` into the center gap.
- Key file: `LayoutLandscapeModifier.java`

### 2.12 Word Suggestions & User Learning
- `suggestions/CandidatesView.java` (50 KB): full-featured candidate bar with expanded popup, Tasker integration hooks, translation toggle, menu toggle, and animation support.
- `suggestions/UserLearningDatabase.java` (20 KB): SQLite-backed learning database.
- `suggestions/UserLearningEngine.java`: learns from what the user types over time.
- `suggestions/NextWordPredictor.java`: predicts the next word based on learned context.
- `suggestions/ExpandedCandidatesPopup.java`: full-width expanded candidates popup.
- `dict/`: dictionary infrastructure — `Dictionaries.java`, `DictionariesActivity.java`, `DictionaryListView.java`, `DictionarySwitcher.java`, `SupportedDictionaries.java`.
- Suggestion queries use the keymap engine's committed output (e.g. Tamil text on screen), not raw Latin keystrokes.

### 2.13 Translation Panel
- An in-keyboard translation bar/panel.
- Key files: `translate/TranslationBarView.java`, `translate/TranslationService.java`, `translate/TranslationPanelController.java`

### 2.14 Voice Typing
- `voice/VoiceTypingController.java` (15 KB): manages voice input session via Android's speech recognition APIs.
- `VoiceImeSwitcher.java`: handles switching to/from voice IME.
- `VoicePermissionActivity.java`: transparent activity for requesting `RECORD_AUDIO` permission.

### 2.15 Markdown Renderer
- `MarkdownRenderer.java` (18 KB): renders markdown text in-app, used by the Tasker Automation Guide and other guide screens.

### 2.16 Layout Editor Activity
- `LayoutEditorActivity.java` (33 KB): a full-screen layout editor, distinct from the inline `CustomLayoutEditDialog`. Registered as an exported activity with `adjustResize` soft-input mode.

### 2.17 User Dictionary Backup/Restore
- `prefs/UserDictionaryBackupActivity.java` (5.8 KB): backup and restore of the system user dictionary.

---

## 3. What Is Inherited Unchanged from Upstream

The following core subsystems come from Unexpected Keyboard and are largely unmodified:

| Component | Key File(s) | Role |
|---|---|---|
| IME service | `Keyboard2.java` (47 KB) | Root InputMethodService; orchestrates all subsystems |
| Key rendering | `Keyboard2View.java` (35 KB) | Custom View drawing the keyboard |
| Pointer/touch | `Pointers.java` (22 KB) | Multi-touch, swipe detection, long press, sliding keys, modifier latching |
| Key modifier | `KeyModifier.java` (20 KB) | Shift, Ctrl, Alt, Meta, Fn, compose, dead keys, Hangul, gesture modifiers |
| Key event handler | `KeyEventHandler.java` (39 KB) | Dispatches key outputs; gates swipe vs center-tap for keymap |
| Key value system | `KeyValue.java` (38 KB), `KeyValueParser.java` (8.7 KB) | Key value definitions and XML parsing |
| Layout data | `KeyboardData.java` (26 KB) | In-memory layout representation |
| Layout modifier | `LayoutModifier.java` (10 KB) | Applies layout modifiers (extra keys, etc.) |
| Theme (base) | `Theme.java` (10 KB) | Theme colors, paints, fonts; reads from XML `<declare-styleable>` |
| Compose keys | `ComposeKey.java`, `ComposeKeyData.java` (111 KB) | Multi-character compose sequences |
| Clipboard | `ClipboardManagerView.java` (17 KB), `ClipboardHistoryService.java`, etc. | Clipboard history panel |
| Emoji | `EmojiGridView.java`, `EmojiGroupButtonsBar.java`, `Emoji.java` | Emoji picker |
| Extra keys | `ExtraKeys.java`, `prefs/ExtraKeysPreference.java` | User-configurable extra key row |
| Settings | `SettingsActivity.java` (15 KB) | Preference screen host |
| Config | `Config.java` (19 KB) | Global runtime config read from SharedPreferences |
| Editor config | `EditorConfig.java` (6.2 KB) | Per-editor field config |
| Resize | `KeyboardResizeManager.java`, `KeyboardResizeOverlay.java` | Keyboard height resize drag handle |
| Text editing | `TextEditManagerView.java` (10 KB) | Text navigation/selection panel |
| Fold state | `FoldStateTracker.java` | Foldable device state |
| Auto-capitalization | `Autocapitalisation.java` | Auto-cap on sentence start |
| Currently typed word | `CurrentlyTypedWord.java` (10 KB) | Word boundary tracker (extended for keymap integration) |
| Direct boot | `DirectBootAwarePreferences.java` | SharedPreferences that work before unlock |
| Gesture | `Gesture.java` | Gesture event data |
| Number row | `NumberLayout.java` | Optional number row |
| Logs | `Logs.java` | Debug logging |
| Utils | `Utils.java`, `DialogUtils.java`, `VibratorCompat.java`, `NonScrollListView.java` | Misc utilities |
| Device locales | `DeviceLocales.java` | Device locale enumeration |
| Keyboard menu | `KeyboardMenuManager.java` (24 KB) | The in-keyboard bottom-sheet menu |

---

## 4. Project Directory Structure

```
NHSCustomKeyboard/
├── AndroidManifest.xml          # Permissions, activities, service declaration
├── build.gradle.kts             # Build config; AGP 8.13.2; Kotlin DSL
├── settings.gradle.kts          # Single-project build
├── srcs/
│   └── com.nhs.customkeyboard/  # ALL Java source
│       ├── (root)               # Core IME, config, theme, update
│       ├── avro/                # Avro phonetic engine (AvroEngine, AvroParser)
│       ├── dict/                # Dictionary infrastructure
│       ├── gif/                 # GIF support assets
│       ├── prefs/               # Preference screen helpers & managers
│       ├── suggestions/         # Candidate bar, learning, predictor
│       ├── tasker/              # Tasker plugin action edit/fire
│       ├── theme/               # Theme picker, crop, preview, model, repo
│       ├── translate/           # Translation bar/panel
│       └── voice/              # Voice typing controller
├── res/
│   ├── anim/                    # Animations
│   ├── drawable/                # Drawables
│   ├── layout/                  # XML layouts (keyboard, settings, dialogs)
│   ├── menu/                    # Menu XMLs
│   ├── mipmap-*/                # App icons
│   ├── raw/                     # Raw resources (emojis list, etc.)
│   ├── values/                  # Base strings, colors, styles, attrs, layouts.xml
│   ├── values-*/                # Translations (40+ locales inherited from upstream)
│   ├── values-night/            # Night mode overrides
│   ├── values-v31/              # API 31 overrides
│   └── xml/                     # method.xml, preferences XMLs, file_paths.xml
├── vendor/
│   └── cdict/                   # Git submodule — compressed dictionary library (NDK)
├── srcs/
│   ├── layouts/                 # XML layout definitions (latn_qwerty_us.xml, etc.)
│   └── compose/                 # Compose sequence JSON files -> ComposeKeyData.java
├── assets/                      # special_font.ttf (built by fontforge)
├── test/                        # JUnit unit tests
├── scripts/                     # Helper scripts
├── doc/                         # Documentation
├── deploy.ps1                   # PowerShell deploy script
└── *.py                         # Code generation scripts (layouts, method.xml, emoji, etc.)
```

---

## 5. Build System & Code Generation

The build uses **Gradle Kotlin DSL** with Android Gradle Plugin 8.13.2.

### Dependencies
| Library | Version | Purpose |
|---|---|---|
| `androidx.window:window-java` | 1.4.0 | Fold state detection |
| `androidx.core:core` | 1.16.0 | AndroidX core |
| `androidx.appcompat:appcompat` | 1.7.0 | Settings theme / AppCompatActivity |
| `androidx.preference:preference` | 1.2.1 | Preference screen framework |
| `com.google.android.material:material` | 1.12.0 | Material components |
| `com.github.bumptech.glide:glide` | 4.16.0 | Image loading (theme wallpaper) |
| `junit:junit` | 4.13.2 | Unit tests |

### Custom Gradle Tasks
| Task | What it does |
|---|---|
| `buildKeyboardFont` | Runs FontForge to build `assets/special_font.ttf` from SVG sources in `srcs/special_font/` |
| `genEmojis` | Runs `gen_emoji.py` -> `res/raw/emojis.txt` |
| `genLayoutsList` | Runs `gen_layouts.py` -> `res/values/layouts.xml` (list of built-in layouts) |
| `genMethodXml` | Runs `gen_method_xml.py` -> `res/xml/method.xml` (subtypes) |
| `checkKeyboardLayouts` | Runs `check_layout.py` -> `check_layout.output` (validates layout XMLs) |
| `compileComposeSequences` | Compiles `srcs/compose/*.json` -> `ComposeKeyData.java` |
| `copyRawQwertyUS` | Copies `latn_qwerty_us.xml` to `build/generated-resources/raw/` |
| `copyLayoutDefinitions` | Copies all layout XMLs to `build/generated-resources/xml/` |
| `copyLayoutAssets` | Copies all layout XMLs to `build/generated-assets/layouts/` |
| `initDebugKeystore` | Auto-generates `debug.keystore` if missing |

### Source Sets
```kotlin
java.srcDirs("srcs/com.nhs.customkeyboard", "vendor/cdict/java/nhs.cdict")
res.srcDirs("res", "build/generated-resources")
assets.srcDirs("assets", "build/generated-assets")
```

The vendor `cdict` submodule must be initialized (`git submodule update --init`) before building.

### Build Variants
- **debug** — `applicationIdSuffix = ".debug"`, not minified, app name = "NHS Debug", `debug_logs = true`.
- **release** — minified + shrunk, signed with `release.keystore`, app name from `app_name_release`.

---

## 6. Permissions

| Permission | Why |
|---|---|
| `RECORD_AUDIO` | Voice typing |
| `VIBRATE` | Key press haptic feedback |
| `RECEIVE_BOOT_COMPLETED` | Clipboard history service autostart |
| `INTERNET` | In-app update checker, translation |
| `ACCESS_NETWORK_STATE` | Update checker network check |
| `REQUEST_INSTALL_PACKAGES` | Installing downloaded APK updates |
| `READ_USER_DICTIONARY` | Word suggestions |
| `WRITE_USER_DICTIONARY` | User learning (adding typed words) |
| `net.dinglisch.android.tasker.PERMISSION_RUN_TASKS` | Tasker automation |

---

## 7. Registered Components (AndroidManifest)

| Component | Class | Notes |
|---|---|---|
| IME Service | `Keyboard2` | `BIND_INPUT_METHOD`, `directBootAware` |
| Settings | `SettingsActivity` | Preference screen host |
| Launcher | `LauncherActivity` | Has `LAUNCHER` category; opens setup/landing flow |
| Dictionaries | `dict/DictionariesActivity` | |
| Theme picker | `theme/ThemeActivity` | |
| Theme crop | `theme/ThemeCropActivity` | `exported=false` |
| Layout editor | `LayoutEditorActivity` | `adjustResize`, `exported=true` |
| Keymap Builder | `KeymapBuilderActivity` | `adjustResize`, configChanges handles orientation |
| Tasker Automation Guide | `TaskerAutomationGuideActivity` | |
| Tasker Automation Builder | `TaskerAutomationBuilderActivity` | `exported=true`, `adjustResize` |
| Tasker Plugin Edit | `tasker/TaskerActionEditActivity` | Responds to `com.twofortyfouram.locale.intent.action.EDIT_SETTING` |
| Tasker Plugin Fire | `tasker/TaskerActionFireReceiver` | Broadcast receiver for `FIRE_SETTING` |
| Voice Permission | `VoicePermissionActivity` | Transparent theme, `exported=false` |
| User Dict Backup | `prefs/UserDictionaryBackupActivity` | |
| FileProvider | `androidx.core.content.FileProvider` | Serves update APK as `content://` URI |

---

## 8. Key XML Layout Format

Layout files live in `srcs/layouts/` (e.g. `latn_qwerty_us.xml`). The root element is `<keyboard>`.

### Keyboard Attributes
```xml
<keyboard
  name="My Layout"           <!-- Display name; shown on space bar -->
  script="latin"             <!-- Script hint for the keyboard -->
  keymap="Tamil"             <!-- (NHS fork only) Name of keymap to apply -->
  swipekeymap="true|false"   <!-- (NHS fork only) Apply keymap to swipe outputs too -->
  bottom_row="true|false"    <!-- Auto-generate standard bottom row -->
>
```

### Key Attributes (output)
| Attribute | Direction | Notes |
|---|---|---|
| `c` | Center (lowercase tap) | **Required** — defines the base output |
| `C` | Center (shift/uppercase) | Requires `c` to be defined first |
| `nw`, `n`, `ne`, `e`, `se`, `s`, `sw`, `w` | 8 swipe directions (lowercase) | |
| `NW`, `N`, `NE`, `E`, `SE`, `S`, `SW`, `W` | 8 swipe directions (uppercase/shift) | Each requires lowercase counterpart |

### Key Attributes (labels — NHS fork addition)
| Attribute | Matches output |
|---|---|
| `cL` | `c` |
| `CL` | `C` |
| `nwL`, `nL`, `neL`, `eL`, `seL`, `sL`, `swL`, `wL` | Directional swipes |
| `NWL`, `NL`, `NEL`, `EL`, `SEL`, `SL`, `SWL`, `WL` | Shifted directional swipes |

Labels are **purely visual**; they never affect what the key sends. If a label is omitted, the output value is displayed.

### Full XML Example
```xml
<keyboard name="Example" script="latin" keymap="Tamil" swipekeymap="false">
  <row>
    <key c="a" cL="அ" C="A" CL="ஆ" e="1"/>
    <key c="k" n="/" nL="div"/>
  </row>
</keyboard>
```

---

## 9. Keymap JSON Format

All keymaps use **grouped output -> keys** format:

```json
{
  "keymap_name": "Tamil",
  "அ": "a",
  "ஆ": "aa,A",
  "க": "ka",
  "கா": "kaa,kA"
}
```

- `keymap_name` is **mandatory** and must be non-empty.
- Each JSON key is the **output** (what appears on screen after transliteration).
- Each JSON value is a **comma-separated list of input key sequences** that produce that output.
- Use `\,` to include a literal comma inside a key sequence.
- Keymaps stored in `SharedPreferences` by the `KeymapManager`.

### Toggle-Style Keymaps
When multiple mappings share a common prefix (e.g. `a -> அ` and `aa -> ஆ`), the engine can **cycle** between them: typing `a` gives `அ`, typing `a` again gives `ஆ`, typing `a` again returns to `அ`.

### Swipekeymap Behavior
| Layout `keymap` | Layout `swipekeymap` | Behavior |
|---|---|---|
| Absent | (any) | No transliteration |
| Present | Absent / `"false"` | Only center taps (`c`/`C`) transliterated |
| Present | `"true"` | Center taps **and** all 8 directional swipes transliterated |

---

## 10. Tasker Automation Config JSON

Single app-wide config stored as JSON:

```json
{
  "nhck_replace": "##",
  "nhck_append": "@@",
  "nhck_timeout": "15000",
  "runtask1": "My Tasker Task",
  "nhck_patterns": [
    { "prefix": "..", "suffix": " ", "task": "ExpandTask" },
    { "prefix": "==", "regex": "\\d+.+\\d", "suffix": "\n", "task": "doMath" }
  ]
}
```

| Key | Required | Default | Meaning |
|---|---|---|---|
| `nhck_replace` | No | `"##"` | Trigger prefix: sends whole field to Tasker, replaces whole field |
| `nhck_append` | No | `"@@"` | Trigger prefix: replaces only the trigger+keyword text |
| `nhck_timeout` | No | `"15000"` | Timeout in ms (1000-120000) |
| Any other key | At least one | — | Keyword -> Tasker task name |
| `nhck_patterns` | No | `[]` | Expand patterns array |

`nhck_replace` and `nhck_append` must be distinct. Missing fields are auto-filled on save.

---

## 11. Input Pipeline Architecture

```
Touch
 |
 v
Pointers.java           -> Multi-touch, swipe detection, long press, sliding keys,
 |                         modifier latching/locking, gesture handling
 v
KeyModifier.java        -> Shift/Ctrl/Alt/Meta/Fn, compose keys, dead keys,
 |                         Hangul, gesture modifiers, selection mode
 v
KeyEventHandler.java    -> Distinguishes center-tap vs. swipe output (isSwipe flag);
 |                         gates whether keymap engine applies per swipekeymap attr
 v
AvroEngine (if Avro     -> Phonetic buffering -> AvroParser -> commit Bangla text
 |  layout active)
 v
KeymapEngine.java       -> Prefix matching, longest-sequence replacement,
 |                         live conversion, toggle-style cycling,
 |                         word-tracker synchronization,
 |                         always re-reads the active keymap from storage
 v
TaskerTriggerEngine     -> Watches typed characters for trigger+keyword or expand
 |  .java                  pattern; runs Tasker task; applies result; one-shot undo
 v
InputConnection         -> Final text committed to the app
```

---

## 12. Settings Screen — Key Preference Classes

`SettingsActivity` hosts a preference fragment. Key custom preference classes:

| Preference Class | Purpose |
|---|---|
| `prefs/LayoutsPreference.java` (29 KB) | Combined Layout + Keymap + Default Layout list |
| `prefs/KeymapManager.java` | Persistence of all saved keymaps |
| `prefs/KeymapEditDialog.java` | Raw JSON keymap editor dialog |
| `prefs/DefaultLayoutPreference.java` | Default layout dropdown |
| `prefs/ExtraKeysPreference.java` (13 KB) | Extra key row configuration |
| `prefs/CustomExtraKeysPreference.java` | Custom extra keys |
| `prefs/SuggestionToolsPreference.java` (29 KB) | Word suggestion settings |
| `prefs/TaskerAutomationPreference.java` (15 KB) | Tasker automation JSON editor |
| `prefs/TaskerAutomationBuilderLinkPreference.java` | Opens Automation Builder |
| `prefs/TaskerAutomationGuideLinkPreference.java` | Opens in-app Tasker guide |
| `prefs/TaskerAutomationManager.java` | Loads/saves Tasker config |
| `prefs/CheckUpdatePreference.java` | "Check for updates" preference row |
| `prefs/AboutHeaderPreference.java` | About header showing version |
| `prefs/UserDictionaryBackupActivity.java` | User dictionary backup/restore |
| `prefs/IntSlideBarPreference.java` | Integer slider preference |
| `prefs/SlideBarPreference.java` | Float slider preference |
| `prefs/ListGroupPreference.java` | Group list preference |

---

## 13. Theme System Details

- Base theme colors are defined in `res/values/attrs.xml` as `<declare-styleable name="keyboard">` attributes.
- `Theme.java` reads all color/border values via `TypedArray` from the active style.
- `Theme.Computed` pre-calculates all `Paint` objects for efficient drawing in `Keyboard2View`.
- The special key font (`special_font.ttf`) is loaded once as a singleton via `Theme.getKeyFont()`.
- Custom themes with wallpaper: image is picked -> cropped (user-controlled rect via `CropImageView`) -> saved as a theme entry -> loaded via Glide in the keyboard view.
- The active theme ID is stored as a `SharedPreferences` string (`"system"` = system default).

---

## 14. Referential Integrity Rules

When keymaps are renamed or deleted, the app enforces consistency:
- **Rename**: `KeymapBuilderActivity` uses `KeymapXmlAttrUtils` to rewrite the `keymap="..."` attribute in every stored custom layout XML that referenced the old name.
- **Delete**: If the keymap is used by one or more layouts, a confirmation dialog is shown. Confirming removes the keymap and strips `keymap`/`swipekeymap` attributes from all affected layouts.
- **Live**: The `KeymapEngine` always reads the keymap fresh from storage when a layout becomes active — there is no stale cache.

---

## 15. Naming & Coding Conventions

- **Package**: `com.nhs.customkeyboard`; sub-packages: `avro`, `dict`, `gif`, `prefs`, `suggestions`, `tasker`, `theme`, `translate`, `voice`.
- **Naming style**: `snake_case` for fields and methods (e.g. `_key_font`, `handle_char()`), `PascalCase` for classes.
- **No Kotlin** — everything is plain Java 8.
- **No dependency injection** — singletons via `static INSTANCE` pattern (e.g. `AvroEngine.get()`).
- **Python codegen**: always run codegen tasks (`genLayoutsList`, `genMethodXml`, etc.) after modifying layout XMLs or compose sequences. In Android Studio, `preBuild` depends on the copy tasks.
- **Submodule**: `vendor/cdict` must be initialized before building. Run `git submodule update --init`.
- **Debug APK ID**: `com.nhs.customkeyboard.debug` (suffix added). Release: `com.nhs.customkeyboard`.
- **Release APK filename**: `OsthirKeyboard.apk` — this exact name is expected by `UpdateChecker`.

---

## 16. Active Development Areas (as of September 2026)

Based on open files and recent conversation history:

| Area | Status / Notes |
|---|---|
| **Theme system** | Actively developed — `ThemePreviewBottomSheet`, `ThemeCropActivity`, `CropImageView`, `ThemeActivity` all recently worked on. Custom wallpaper theme support being built/refined. |
| **Landscape mode** | `LayoutLandscapeModifier` crash fix recently addressed. Handle null rows/keys defensively. |
| **Dynamic system accent** | Support for Material You / Android 12+ dynamic system accent color in themes (conversation dbb4f86f). |
| **Keyboard2.java** | Main IME file; cursor at line 1328 suggests active work near layout switching / key event processing. |
| **KeyEventHandler.java** | Recently open — likely touched when connecting AvroEngine or Tasker engine to the event pipeline. |

---

## 17. Key Quick-Reference Rules

1. **Labels never change output** — they are purely visual (`cL`, `CL`, etc.).
2. **Keymaps never change rendering directly** — they act on committed text.
3. **Only `c`/`C` passes through the keymap engine by default**; swipes bypass it unless `swipekeymap="true"`.
4. **`swipekeymap` is inert without a `keymap` attribute.**
5. **Avro engine is active only when the Avro layout is selected**; all other layouts go through `KeymapEngine` or no engine.
6. **The space bar always shows the active layout's `name` attribute**, not a plain space glyph.
7. **Default layout "Last used"** (default): restores last active layout on reopen. **Specific layout**: always loads that layout.
8. **Referential integrity**: rename or delete a keymap -> all layout XMLs are updated automatically.
9. **Update checker APK filename is `OsthirKeyboard.apk`** — do not change this without updating `UpdateChecker.EXPECTED_APK_ASSET_NAME`.
10. **Submodule `vendor/cdict` must be initialized** before any build. Run `git submodule update --init`.
11. **Debug build ID** has `.debug` suffix — install both side-by-side is possible.
12. **All Tasker automation config defaults are auto-filled** on save — missing keys are never silently lost.
