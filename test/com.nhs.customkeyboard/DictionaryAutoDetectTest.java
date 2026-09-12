package com.nhs.customkeyboard;

import com.nhs.customkeyboard.dict.Dictionaries;
import java.util.ArrayList;
import org.junit.Test;
import static org.junit.Assert.*;

public class DictionaryAutoDetectTest
{
  @Test
  public void testAutoDetectBanglaLayouts()
  {
    KeyboardData avroLayout = new KeyboardData(
        new ArrayList<KeyboardData.Row>(), 10f, null, "avro", "bengali", "বাংলা (Avro)", "bn",
        null, false, true, false, false);
    assertEquals("bn", avroLayout.dictionary);

    KeyboardData customLayout = new KeyboardData(
        new ArrayList<KeyboardData.Row>(), 10f, null, "bengali", "bengali", "বাংলা (Custom)", "bn",
        null, false, true, false, false);
    assertEquals("bn", customLayout.dictionary);

    KeyboardData englishLayout = new KeyboardData(
        new ArrayList<KeyboardData.Row>(), 10f, null, "latin", "latin", "English (US)", "en_US",
        null, false, true, false, false);
    assertEquals("en_US", englishLayout.dictionary);
  }
}
