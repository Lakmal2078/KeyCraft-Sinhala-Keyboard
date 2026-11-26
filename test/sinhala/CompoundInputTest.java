package com.lakmal.keycraftsinhala;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class CompoundInputTest
{
  @Test
  public void mapsYanshayaAndRakaranshaya()
  {
    assertEquals("ක්‍රමය", SinhalaTransliterator.transliterate("kramaya"));
    assertEquals("ව්‍යංජන", SinhalaTransliterator.transliterate("vyanjana"));
    assertEquals("ප්‍රකාශන", SinhalaTransliterator.transliterate("prakaashana"));
    assertEquals("විද්‍යාව", SinhalaTransliterator.transliterate("vidhyaava"));
  }

  @Test
  public void mapsSpecialConsonantClustersWithExactUnicode()
  {
    assertEquals("\u0D9A\u0DCA\u200D\u0DC2\u0DBA", SinhalaTransliterator.transliterate("kshaya"));
    assertEquals("\u0D9A\u0DCA\u200D\u0DBB", SinhalaTransliterator.transliterate("kra"));
    assertEquals("\u0DC0\u0DCA\u200D\u0DBA", SinhalaTransliterator.transliterate("vya"));
    assertEquals("\u0DB1\u0DCA\u200D\u0DB0", SinhalaTransliterator.transliterate("ndha"));
    assertEquals("\u0DB8\u0DCA\u0DB7", SinhalaTransliterator.transliterate("mbha"));
    assertEquals("අඹ", SinhalaTransliterator.transliterate("amba"));
  }

  @Test
  public void keepsSinhalaConjunctsAcrossWordEndings()
  {
    assertEquals("ස්ත්‍රියක්", SinhalaTransliterator.transliterateWord("striyak"));
    assertEquals("ස්ත්‍රියට", SinhalaTransliterator.transliterateWord("Striyata"));
    assertEquals("කෘෂිකර්මයේ", SinhalaTransliterator.transliterateWord("krushikarmaye"));
    assertEquals("කෘෂිකර්මයට", SinhalaTransliterator.transliterateWord("krushikarmayata"));
    assertEquals("ඕස්ට්‍රේලියාව", SinhalaTransliterator.transliterateWord("ostreliyawa"));
    assertEquals("අස්ත්‍රයක්", SinhalaTransliterator.getSuggestions("astrayak").get(1));
  }

  @Test
  public void keepsPunctuationAndSpacesAroundTranslatedWords()
  {
    assertEquals("අම්මා, කොහොමද?", SinhalaTransliterator.transliterate("amma, kohomada?"));
    assertEquals("සුබ උදෑසනක්!", SinhalaTransliterator.transliterate("suba udaesanak!"));
  }
}
