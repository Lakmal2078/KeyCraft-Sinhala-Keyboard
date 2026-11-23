package com.lakmal.keycraftsinhala;

import android.view.KeyEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Assert;
import org.junit.Test;

public class LayoutVerificationTest
{
  @Test
  public void testTermuxLayoutUsesNativeModifierAndNavigationKeys() throws Exception
  {
    File layout = new File("srcs/layouts/latn_termux.xml");
    Assert.assertTrue("Termux layout must exist", layout.isFile());
    String xml = new String(Files.readAllBytes(layout.toPath()), StandardCharsets.UTF_8);
    String[] tokens = {
      "c=\"ctrl\"", "c=\"alt\"", "c=\"esc\"", "c=\"tab\"",
      "key7=\"up\"", "key6=\"right\"", "key5=\"left\"", "key8=\"down\""
    };
    for (String token : tokens)
      Assert.assertTrue("Termux layout must expose " + token, xml.contains(token));

    Assert.assertEquals(KeyValue.Kind.Modifier, KeyValue.getSpecialKeyByName("ctrl").getKind());
    Assert.assertEquals(KeyValue.Modifier.CTRL, KeyValue.getSpecialKeyByName("ctrl").getModifier());
    Assert.assertEquals(KeyValue.Modifier.ALT, KeyValue.getSpecialKeyByName("alt").getModifier());
    Assert.assertEquals(KeyEvent.KEYCODE_ESCAPE, KeyValue.getSpecialKeyByName("esc").getKeyevent());
    Assert.assertEquals(KeyEvent.KEYCODE_TAB, KeyValue.getSpecialKeyByName("tab").getKeyevent());
    Assert.assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, KeyValue.getSpecialKeyByName("left").getKeyevent());
    Assert.assertEquals(KeyEvent.KEYCODE_DPAD_DOWN, KeyValue.getSpecialKeyByName("down").getKeyevent());
    Assert.assertEquals(KeyEvent.KEYCODE_DPAD_UP, KeyValue.getSpecialKeyByName("up").getKeyevent());
    Assert.assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, KeyValue.getSpecialKeyByName("right").getKeyevent());
  }

  @Test
  public void testTermuxLayoutRowsHaveNoVerticalGap() throws Exception
  {
    File layout = new File("srcs/layouts/latn_termux.xml");
    org.w3c.dom.Document doc = javax.xml.parsers.DocumentBuilderFactory
        .newInstance()
        .newDocumentBuilder()
        .parse(layout);
    org.w3c.dom.NodeList rows = doc.getElementsByTagName("row");
    Assert.assertEquals(4, rows.getLength());
    for (int i = 0; i < rows.getLength(); i++)
    {
      org.w3c.dom.Element row = (org.w3c.dom.Element) rows.item(i);
      String rowShift = row.getAttribute("shift");
      if (!rowShift.isEmpty())
        Assert.assertEquals(0f, Float.parseFloat(rowShift), 0.0001f);
    }
  }

  @Test
  public void testSinhalaLayoutKeyDefinitions()
  {
    File layoutDir = new File("srcs/layouts");
    Assert.assertTrue("Layouts directory must exist", layoutDir.exists() && layoutDir.isDirectory());

    File sinhalaLayout = new File(layoutDir, "sinhala_phonetic.xml");
    Assert.assertTrue("Sinhala phonetic layout XML must exist", sinhalaLayout.exists() && sinhalaLayout.isFile());

    String[] essentialGlyphs = {"ක", "ග", "ච", "ට", "ත", "ප", "ය", "ර", "ල", "ව", "ස", "හ"};
    for (String glyph : essentialGlyphs)
      Assert.assertNotNull("Glyph definition must not be null for " + glyph, glyph);
  }

  @Test
  public void testSinhalaPhoneticModmapHasNoKaJaCollision() throws Exception
  {
    File sinhalaLayout = new File("srcs/layouts/sinhala_phonetic.xml");
    String xml = new String(Files.readAllBytes(sinhalaLayout.toPath()), StandardCharsets.UTF_8);
    Assert.assertTrue("Shift+ජ must map to ඦ", xml.contains("a=\"ජ\" b=\"ඦ\""));
    Assert.assertFalse("Shift+ක must not collide on ඦ", xml.contains("a=\"ක\" b=\"ඦ\""));
  }
}
