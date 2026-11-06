package com.nhs.customkeyboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class LayoutModifierTest
{
  @Test
  public void testNumberRowDoesNotStripSymbolsFromMainLayout()
  {
    // Configure globalConfig to have number row enabled with symbols
    Config config = new Config();
    config.add_number_row = true;
    config.number_row_symbols = true;
    config.show_numpad = false;
    config.split_layout = false;
    config.extra_keys_param = new HashMap<KeyValue, KeyboardData.PreferredPos>();
    config.extra_keys_custom = new HashMap<KeyValue, KeyboardData.PreferredPos>();
    config.editor_config = new EditorConfig();
    LayoutModifier.globalConfig = config;

    // Build a mock number row with symbols: key '1' with se='!'
    KeyValue[] nrKey1Values = new KeyValue[9];
    nrKey1Values[0] = KeyValue.makeStringKey("1");
    nrKey1Values[4] = KeyValue.makeStringKey("!"); // se="!"
    KeyboardData.Key nrKey1 = new KeyboardData.Key(
        nrKey1Values, new KeyValue[9], null, null, 0, 1f, 0f, null, new String[18], KeyboardData.Key.Role.Normal);

    List<KeyboardData.Key> nrKeys = new ArrayList<KeyboardData.Key>();
    nrKeys.add(nrKey1);
    LayoutModifier.number_row_symbols = new KeyboardData.Row(nrKeys, 1f, 0f);
    LayoutModifier.number_row_no_symbols = new KeyboardData.Row(nrKeys, 1f, 0f);

    // Build a main keyboard layout with key 'হ' having ne='1' and se='!'
    KeyValue[] mainKeyValues = new KeyValue[9];
    mainKeyValues[0] = KeyValue.makeStringKey("হ");
    mainKeyValues[2] = KeyValue.makeStringKey("1"); // ne="1" (number)
    mainKeyValues[4] = KeyValue.makeStringKey("!"); // se="!" (symbol)
    KeyboardData.Key mainKey = new KeyboardData.Key(
        mainKeyValues, new KeyValue[9], null, null, 0, 1f, 0f, null, new String[18], KeyboardData.Key.Role.Normal);

    List<KeyboardData.Key> mainKeys = new ArrayList<KeyboardData.Key>();
    mainKeys.add(mainKey);
    List<KeyboardData.Row> mainRows = new ArrayList<KeyboardData.Row>();
    mainRows.add(new KeyboardData.Row(mainKeys, 1f, 0f));

    KeyboardData layout = new KeyboardData(
        mainRows, 1f, 1f, null, "bengali", "bengali", "বাংলা (Custom)", "bn",
        null, false, false, false, false);

    // Modify layout with number row included
    KeyboardData modified = LayoutModifier.modify_layout(layout, true, false);

    // The modified keyboard layout should have 2 rows: row 0 (number row), row 1 (main keys)
    assertEquals(2, modified.rows.size());

    // Row 1 key 'হ' must still have its se='!' symbol intact (not stripped to null)
    KeyboardData.Key modifiedMainKey = modified.rows.get(1).keys.get(0);
    assertNotNull("Key 'হ' must not be null", modifiedMainKey);
    assertEquals(KeyValue.makeStringKey("হ"), modifiedMainKey.keys[0]);
    assertNotNull("se='!' on key 'হ' must not be stripped by the number row", modifiedMainKey.keys[4]);
    assertEquals(KeyValue.makeStringKey("!"), modifiedMainKey.keys[4]);

    // But ne='1' on key 'হ' MUST be stripped because number 1 is in the number row
    assertNull("ne='1' on key 'হ' must be stripped by the number row", modifiedMainKey.keys[2]);
  }
}

