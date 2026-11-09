#!/usr/bin/env python3
"""
Syncs all res/drawable/ic_*.xml vector drawables directly from
assets/material_symbols_outlined.ttf, replacing custom made vector drawables
with official Google Material Symbols outline vectors.
"""

import os
import json
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FONT_PATH = os.path.join(ROOT_DIR, 'assets', 'material_symbols_outlined.ttf')
JSON_PATH = os.path.join(ROOT_DIR, 'assets', 'material_symbols.json')
DRAWABLE_DIR = os.path.join(ROOT_DIR, 'res', 'drawable')

EXPLICIT_MAP = {
    'ic_add': 'add',
    'ic_arrow_back': 'arrow_back',
    'ic_auto_complete': 'spellcheck',
    'ic_border': 'border_all',
    'ic_border_width': 'line_weight',
    'ic_brightness': 'brightness_6',
    'ic_bug_report': 'bug_report',
    'ic_capital': 'keyboard_capslock',
    'ic_cat_behavior': 'psychology',
    'ic_cat_clipboard': 'content_paste',
    'ic_cat_layout': 'dashboard',
    'ic_cat_style': 'palette',
    'ic_cat_suggestions': 'lightbulb',
    'ic_cat_tasker': 'bolt',
    'ic_cat_typing': 'keyboard',
    'ic_check': 'check',
    'ic_chevron_down': 'keyboard_arrow_down',
    'ic_chevron_right': 'chevron_right',
    'ic_circle_sensitivity': 'radio_button_checked',
    'ic_clipboard': 'assignment',
    'ic_clipboard_outline': 'assignment',
    'ic_clipboard_paste': 'content_paste_go',
    'ic_clipboard_save': 'bookmark_add',
    'ic_clock_recent': 'schedule',
    'ic_close': 'close',
    'ic_delete': 'delete',
    'ic_description': 'description',
    'ic_download': 'download',
    'ic_dpad_down': 'keyboard_arrow_down',
    'ic_dpad_left': 'keyboard_arrow_left',
    'ic_dpad_right': 'keyboard_arrow_right',
    'ic_dpad_up': 'keyboard_arrow_up',
    'ic_drag_handle': 'drag_handle',
    'ic_drag_move': 'drag_pan',
    'ic_edit': 'edit',
    'ic_edit_pencil': 'edit',
    'ic_emoji_menu': 'sentiment_satisfied',
    'ic_emoticon': 'mood',
    'ic_gboard_clipboard': 'content_paste',
    'ic_gboard_translate': 'translate',
    'ic_gesture': 'gesture',
    'ic_gif': 'gif',
    'ic_github': 'code',
    'ic_globe': 'language',
    'ic_grid_menu': 'apps',
    'ic_height': 'height',
    'ic_horizontal_spacing': 'width',
    'ic_importjson': 'download',
    'ic_info': 'info',
    'ic_info_outline': 'info',
    'ic_intensity': 'speed',
    'ic_jump_to_end': 'last_page',
    'ic_jump_to_start': 'first_page',
    'ic_keyboard': 'keyboard',
    'ic_keyboard_opacity': 'opacity',
    'ic_key_activated_opacity': 'opacity',
    'ic_key_opacity': 'opacity',
    'ic_key_repeat': 'repeat',
    'ic_lable_size': 'format_size',
    'ic_long_press_timeout': 'timer',
    'ic_margin': 'space_bar',
    'ic_menu_book': 'menu_book',
    'ic_mic': 'mic',
    'ic_more_vert': 'more_vert',
    'ic_numpad': 'dialpad',
    'ic_num_key': 'pin',
    'ic_num_order': 'format_list_numbered',
    'ic_one_handed': 'pan_tool',
    'ic_opacity': 'opacity',
    'ic_open_in_new': 'open_in_new',
    'ic_palette': 'palette',
    'ic_physical': 'keyboard_alt',
    'ic_pref_default': 'tune',
    'ic_radius': 'rounded_corner',
    'ic_reset': 'restart_alt',
    'ic_resize': 'aspect_ratio',
    'ic_search': 'search',
    'ic_settings_gear': 'settings',
    'ic_shield_check': 'verified_user',
    'ic_shift': 'shift',
    'ic_slider_sensitivity': 'tune',
    'ic_special_key': 'auto_awesome',
    'ic_spellcheck': 'spellcheck',
    'ic_split_key': 'call_split',
    'ic_sticker': 'sticker',
    'ic_suggestions': 'lightbulb',
    'ic_suggestion_tools': 'smart_button',
    'ic_swap_horiz': 'swap_horiz',
    'ic_swipe': 'swipe',
    'ic_system_update': 'system_update_alt',
    'ic_tasker': 'bolt',
    'ic_tasker_bolt': 'bolt',
    'ic_tasker_plugin': 'extension',
    'ic_text_edit': 'edit_note',
    'ic_theme': 'palette',
    'ic_time': 'schedule',
    'ic_tool_backspace': 'backspace',
    'ic_tool_clipboard': 'assignment',
    'ic_tool_copy': 'content_copy',
    'ic_tool_cut': 'content_cut',
    'ic_tool_paste': 'content_paste',
    'ic_tool_redo': 'redo',
    'ic_tool_select_all': 'select_all',
    'ic_tool_settings': 'settings',
    'ic_tool_translate': 'translate',
    'ic_tool_undo': 'undo',
    'ic_translate': 'translate',
    'ic_tune': 'tune',
    'ic_vertical_spacing': 'height',
    'ic_vibration': 'mobile_vibrate'
}

IGNORED = {'ic_launcher_background.xml', 'ic_launcher_foreground.xml'}

def main():
    print(f"Loading font: {FONT_PATH}")
    font = TTFont(FONT_PATH)
    glyph_set = font.getGlyphSet()

    with open(JSON_PATH, 'r', encoding='utf-8') as f:
        symbols = set(json.load(f))

    updated = 0
    skipped = 0

    files = [f for f in os.listdir(DRAWABLE_DIR) if f.startswith('ic_') and f.endswith('.xml')]
    for fname in sorted(files):
        if fname in IGNORED:
            skipped += 1
            continue

        base = fname[:-4]
        sym = EXPLICIT_MAP.get(base) or base[3:]

        if sym not in glyph_set:
            if '_' + sym in glyph_set:
                sym = '_' + sym
            else:
                print(f"WARNING: Symbol '{sym}' not in glyph_set for {fname}")
                skipped += 1
                continue

        pen = SVGPathPen(glyph_set)
        # Font coordinates: 960 units, Y goes up. Vector drawable: Y goes down.
        # Transform: x' = x, y' = 960 - y
        tpen = TransformPen(pen, (1, 0, 0, -1, 0, 960))
        glyph = glyph_set[sym]
        glyph.draw(tpen)
        path_data = pen.getCommands()

        xml_content = f"""<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="960"
    android:viewportHeight="960">
  <path
      android:fillColor="#FFFFFFFF"
      android:pathData="{path_data}" />
</vector>
"""
        target_path = os.path.join(DRAWABLE_DIR, fname)
        with open(target_path, 'w', encoding='utf-8') as f:
            f.write(xml_content)

        updated += 1

    print(f"Sync complete: {updated} vector drawables updated from {FONT_PATH}, {skipped} skipped.")

if __name__ == '__main__':
    main()

