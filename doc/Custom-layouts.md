# Custom layouts
You select a key layout for Unexpected Keyboard by calling up the Settings page (swipe the gear icon) and, at the top of the page, either tapping an existing layout or tapping _Add an alternate layout_. This displays a menu of available layouts. You can define your own layout by choosing _Custom layout_ at the bottom of this menu. Unexpected Keyboard now displays code in the XML format. You make changes by replacing this with different code and tapping OK.

We recommend you keep your work in a file outside Unexpected Keyboard (named something like `MyChanges.xml`). If you installed a new version of Unexpected from a different website (with a different signature), then the work you did solely by editing the XML inside Unexpected would be lost.

Put initial contents into your file in one of these ways:
* Copypaste the code Unexpected displays for _Custom layout_.
* Make a copy of one of the built-in layouts found in [`/srcs/layouts`](https://github.com/Julow/Unexpected-Keyboard/tree/master/srcs/layouts).
* Use the [web-based editor](https://domportera.github.io/app-unexpected-keyboard-layout-editor/). Interact with this web page to define keys and swipes and move keys to desired positions, and it will write the XML code for you. You can make the web page put the XML in a text file or copy it to the clipboard.

When you have prepared suitable XML code in one of these ways, copy it to the clipboard and paste it into Unexpected Keyboard.

## XML language overview
A layout XML file comprises tags that start with `<` and end with `>`.
* Every layout file starts with this declaration:
  `<?xml version="1.0" encoding="utf-8"?>`
* Certain tags come in pairs—an opening tag and a closing tag—and apply to everything between them.
  * The `<keyboard>`...`</keyboard>` pair says that the material between them is the definition of your keyboard. There can be only one of these.
  * The `<row>`...`</row>` pair encloses the definition of a single row.
  * An optional `<modmap>`...`</modmap>` pair contains instructions if you want to change the behavior of a modifier key such as Shift.
* Stand-alone tags include `<key`...`/>`, which defines a single key.

A tag can have properties, defined using an equals sign and a pair of ASCII double quotes. For example, `<key c="a" />` defines the "a" key. The `c` property of the `key` tag says which key you are defining, and the tag's location inside `<row>`...`</row>` specifies where it will go in the row.

### Example
Here is a complete keyboard file with a single row containing an "a" key on the left and a "b" key on the right:

    <?xml version="1.0" encoding="utf-8"?>
    <keyboard name="Simple example" script="latin">
        <row>
            <key c="a" />
            <key c="b" />
        </row>
    </keyboard>

## Keyboard metadata

The `<keyboard>`...`</keyboard>` pair follows the declaration tag and encloses the whole keyboard. The following properties may be used (The first two appear in the example above):

* `name`: The name of the keyboard. The name you specify will appear in the Settings menu. If not present, the layout will just appear as “Custom layout”.

* `script`: The (main) writing system that the keyboard supports. The possible values are `arabic`, `armenian`, `bengali`, `cyrillic`, `devanagari`, `gujarati`, `hangul`, `hebrew`, `latin`, `persian`, `shavian`, and `urdu`. It defaults to `latin`.

* `numpad_script`: The script to use for the numpad. This is useful for scripts where a different, non-ASCII set of numerals is used, like Devanagari and Arabic. It defaults to the same as `script`.

* `bottom_row`: Whether or not to show the built-in bottom row. It accepts `true` or `false`, and defaults to `true`. If your custom layout defines the bottom row, then specify `bottom_row="false"` to disable the built-in bottom row.
  + We recommend your layout use the built-in bottom row, because it is still evolving and your layout will incorporate innovations in future versions. However, to define your own, the current definition of the bottom row is in [bottom_row.xml](https://github.com/Julow/Unexpected-Keyboard/blob/master/res/xml/bottom_row.xml). You can copypaste this XML into your custom layout as a starting point.
  + Likewise, the current definition of the top (number) row is in [number_row.xml](https://github.com/Julow/Unexpected-Keyboard/blob/master/res/xml/number_row.xml).

* `embedded_number_row`: Whether the layout has an embedded number row, and thus the "Show number row" setting shouldn't add another one. It accepts `true` or `false`, and defaults to `false`.

* `locale_extra_keys`: Whether Unexpected should add language-dependent extra keys from [method.xml](../res/xml/method.xml) to this layout. It accepts `true` or `false`, and defaults to `true`. To disable these automatic additions, specify `locale_extra_keys="false"`.

* `keymap`: (NHSCustomKeyboard) The name of a saved transliteration keymap to apply (e.g. `keymap="Tamil"`). Center-tap characters pass through this keymap engine to produce transliterated script.

* `swipekeymap`: (NHSCustomKeyboard) Whether transliteration also applies to swipe outputs. Accepts `true` or `false`, defaults to `false`. (Inert if `keymap` is not set).

## Row
The `<row>`...`</row>` pair encloses one row on the keyboard. It has the following optional property:
* `height`: The height of the row: a positive floating-point value.

* `scale`: A positive floating-point value. If present, scale the width of each key so that the total is equal to the specified value, in key width unit.

A row's default height is 1.0 (one quarter of the keyboard height specified on the Settings menu). The `height` property makes the row taller or shorter than this. For example, if you define a 5-row keyboard but one row has `height="0.7"`, then the keyboard's total height is 4.7 units. If the total is different from 4.0, the keyboard will be taller or shorter than that specified in Settings.

(A row of keys is drawn with a minimum height of 0.5 even if you specify a smaller value for `height`. There is no such minimum for a row without keys, such as a spacer row.)

## Key
The `<key />` tag defines a key on the keyboard. Its position in the sequence of keys inside `<row>`...`</row>` indicates its position in the row from left to right. What the key does and displays is defined by optional attributes.

### Outputs: Taps & Swipes
Each key has a center position and 8 compass directions. Every position supports both **normal** (lowercase) and **shifted** (uppercase) output:

```
        NW   N   NE          nw   n   ne
          \  |  /              \  |  /
        W -  C  - E          w  - c -  e
          /  |  \              /  |  \
        SW   S   SE          sw   s   se
     [Shifted Outputs]      [Normal Outputs]
```

* **Center Tap**:
  * `c`: Base tap output (**required** for standard keys).
  * `C`: Shifted tap output (requires `c` to be defined; if omitted, Shift uppercase-transforms `c`).
* **Swipes (8 directions)**:
  * Normal: `nw`, `n`, `ne`, `w`, `e`, `sw`, `s`, `se`
  * Shifted: `NW`, `N`, `NE`, `W`, `E`, `SW`, `S`, `SE`
* **Legacy numeric notation**: `key0` (center), `key1`..`key8` (directions).

### Visible Labels (Visual Overrides)
Labels control **what is visually printed on the key**, completely independent of what is typed:

| Position | Normal Label (matches output) | Shifted Label (matches output) |
| :--- | :--- | :--- |
| **Center** | `cL` (matches `c`) | `CL` (matches `C`) |
| **North-West (↖)** | `nwL` (matches `nw`) | `NWL` (matches `NW`) |
| **North (↑)** | `nL` (matches `n`) | `NL` (matches `N`) |
| **North-East (↗)** | `neL` (matches `ne`) | `NEL` (matches `NE`) |
| **West (←)** | `wL` (matches `w`) | `WL` (matches `W`) |
| **East (→)** | `eL` (matches `e`) | `EL` (matches `E`) |
| **South-West (↙)** | `swL` (matches `sw`) | `SWL` (matches `SW`) |
| **South (↓)** | `sL` (matches `s`) | `SL` (matches `S`) |
| **South-East (↘)** | `seL` (matches `se`) | `SEL` (matches `SE`) |

> **Important Rule**: Labels never affect what the key types. If a label attribute is omitted (missing), the keyboard automatically displays the output key value instead.

#### Label Examples:
```xml
<!-- Outputs "a" but displays 🍎; shifted outputs "A" but displays ✈️ -->
<key c="a" cL="🍎" C="A" CL="✈️" />

<!-- Swipe ↘ outputs email address, but displays an envelope icon 📧 -->
<key c="e" se="myemail@domain.com" seL="📧" />
```

### Gestures & Long Press
* `lp` or `long_press`: Key value to send when holding down the key.
* `anticircle`: Key value to send when drawing an anti-clockwise circle gesture on the key.

### Layout & Appearance
* `width`: The width of the key, a positive floating-point value. Defaults to `1.0`. (Action keys like Backspace or Shift often use `1.5`, Space uses `4.0` or `5.0`).
* `shift`: How much empty space to add to the left of this key in key-width units. Defaults to `0.0`. (For example, `shift="0.5"` staggers row 2).
* `role`: Styling role for the key:
  * `role="action"`: Renders the key with the theme's action/accent color (like Shift, Enter, Backspace).
  * `role="normal"`: Standard character key styling (default).
* `indication`: An optional extra legend to show under the main label (e.g. `<key c="2" indication="ABC" />`).

### Possible Key Values
Built-in strings that assign a special function to a key are described in [Possible key values](Possible-key-values.md). For example, `se="copy"` means a southeasterly swipe produces the Copy action. If a key value does not match any built-in special string, it outputs that text _verbatim_ (including full strings or emojis).

In a layout, a key value can also start with the `loc` prefix. These are place-holders; the tap or swipe does nothing unless enabled through the "Add keys to keyboard" option in the Settings menu, or implicitly enabled by the language the device is set to use. For example, `ne="loc accent_aigu"` says that a northeast swipe produces the acute accent combinatorial key—if enabled.

## Modmap
The `<modmap>`...`</modmap>` pair encloses custom mappings for modifier keys. The modmap is placed inside the `<keyboard>`...`</keyboard>` pair, but outside any row. A layout can have at most one modmap. It can contain any number of mappings. Each mapping has an `a` property and a `b` property and maps the `a` key to the `b` key. Valid values are listed in [Possible key values](Possible-key-values.md).

The following mappings are supported:

```xml
  <shift a="before" b="after" />
```
This means that when the Shift modifier is on, the key `before` is changed into `after`.

```xml
  <fn a="before" b="after" />
```
This means that when the Fn modifier is on, the key `before` is changed into `after`.

```xml
  <ctrl a="before" b="after" />
```
This means that when the Ctrl modifier is on, the key `before` is changed into `after`. The `<ctrl />` mapping is special in that the Ctrl modifier is applied to `after` after the mapping.

The clockwise circle and the round-trip gestures are affected by the `<fn />` mappings. Otherwise, they are defined by the Shift mappings (including the `<shift />` mappings), then, if that did not modify the key, the builtin Fn mappings are used instead.

### Examples
① Turkish keyboards use the Latin alphabet, but when "i" is shifted, it should produce "İ". This is achieved with the following mapping: 

```xml
    <shift a="i" b="İ" />
```
② Cyrillic layouts have no V key. A layout can define Ctrl-V with the following mapping:

```xml
    <ctrl a="в" b="v" />
```
This maps Ctrl+в to Ctrl+V—not to v.

### Default mappings
Unexpected Keyboard's built-in mappings are not expressed as a modmap but by a series of .json files in `https://github.com/Julow/Unexpected-Keyboard/tree/master/srcs/compose`. For example, the mappings for `fn` are in [`fn.json`](https://github.com/Julow/Unexpected-Keyboard/blob/master/srcs/compose/fn.json). These built-in mappings are common to every language.

## Portrait vs. landscape
Unexpected Keyboard remembers *separately* which layout has last been used in portrait and landscape orientation. So you may have one custom layout for portrait orientation, but another custom layout for landscape orientation, and Unexpected Keyboard will switch between them without your intervention.

## Contributing your layout
The Unexpected Keyboard project enthusiastically accepts user contributions, including custom layouts. (See the guidance for layouts at [CONTRIBUTING.md](https://github.com/Julow/Unexpected-Keyboard/blob/master/CONTRIBUTING.md#Adding-a-layout)).
* Submit a layout that has innovations of possible interest to other users at [Unexpected-Keyboard-layouts](https://github.com/Julow/Unexpected-Keyboard-layouts).
* Propose that your layout be included in the set of built-in layouts by making a Pull Request for an addition to [srcs/layouts](https://github.com/Julow/Unexpected-Keyboard/tree/master/srcs/layouts). Please show that such a layout is standard in your locale or has a substantial number of users.
