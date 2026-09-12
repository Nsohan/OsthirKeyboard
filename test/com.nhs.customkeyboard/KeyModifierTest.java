package com.nhs.customkeyboard;

import com.nhs.customkeyboard.KeyModifier;
import com.nhs.customkeyboard.KeyValue;
import org.junit.Test;
import static com.nhs.customkeyboard.TestUtils.*;
import static org.junit.Assert.*;

public class KeyModifierTest
{
  public KeyModifierTest() {}

  @Test
  public void compose() throws Exception
  {
    assertEquals(eval("compose", "space", "space"), key("nbsp"));
    assertEquals(eval("compose", "-", "space"), str("~"));
    assertEquals(eval("compose", "space", "-"), str("~"));
  }
}
