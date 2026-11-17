package com.lakmal.keycraftsinhala;

import com.lakmal.keycraftsinhala.KeyModifier;
import com.lakmal.keycraftsinhala.KeyValue;
import org.junit.Test;
import static com.lakmal.keycraftsinhala.TestUtils.*;
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
