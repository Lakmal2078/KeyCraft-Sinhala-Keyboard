package com.lakmal.keycraftsinhala;

import org.junit.Test;
import static org.junit.Assert.*;

public class EdgeCaseTest
{
  @Test
  public void handlesNullEmptyAndWhitespace()
  {
    assertNull(SinhalaTransliterator.transliterate(null));
    assertEquals("", SinhalaTransliterator.transliterate(""));
    assertEquals("", SinhalaTransliterator.transliterateWord(null));
    assertEquals("", SinhalaTransliterator.parsePhonetic(null));
    assertEquals("  \t\n", SinhalaTransliterator.transliterate("  \t\n"));
  }

  @Test
  public void preservesSeparatorsAndNumericText()
  {
    assertEquals("අම්මා! 123", SinhalaTransliterator.transliterate("Amma! 123"));
    assertEquals("  සිංහල... ", SinhalaTransliterator.transliterate("  sinhala... "));
  }

  @Test
  public void preservesPastedSinhalaAndJoinerSequences()
  {
    String pasted = "\u0DC1\u0DCA\u200D\u0DBB\u0DD3 \u0DB1\u0DCA\u200D\u0DB0 \u0D9A\u0DCA\u200D\u0DC2";
    assertEquals(pasted, SinhalaTransliterator.transliterate(pasted));
  }

  @Test
  public void anusvaraRuleDoesNotInsertAtWordStart()
  {
    String output = SinhalaTransliterator.parsePhonetic("nsb");
    assertFalse(output.startsWith("\u0D82"));
  }
}
