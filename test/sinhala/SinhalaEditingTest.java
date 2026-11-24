package com.lakmal.keycraftsinhala;

import org.junit.Test;
import static org.junit.Assert.*;

public class SinhalaEditingTest
{
  @Test public void signsAttachToConsonantsAndReplacePriorVowels()
  {
    for (char sign : "ාැෑිීුූෘෙේෛොෝෞ්".toCharArray())
    {
      assertEquals(0, SinhalaText.replaceBeforeMark("ක", sign));
      assertEquals(1, SinhalaText.replaceBeforeMark("කෙ", sign));
      assertEquals(-1, SinhalaText.replaceBeforeMark("", sign));
      assertEquals(-1, SinhalaText.replaceBeforeMark("a", sign));
      assertEquals(0, SinhalaText.replaceBeforeMark("කෙර", sign));
    }
    assertEquals(0, SinhalaText.replaceBeforeMark("කෙ", 'ං'));
    assertEquals(1, SinhalaText.replaceBeforeMark("කං", 'ඃ'));
    assertEquals(0, SinhalaText.replaceBeforeMark("ර", '්'));
  }

  @Test public void backspaceUnwindsJoinedAndUnjoinedClusters()
  {
    for (String word : "ක්ක ග්ග ච්ච ජ්ජ ත්ත ද්ද න්න ප්ප ම්ම ල්ල ස්ථ ස්ත".split(" "))
      assertEquals(2, SinhalaText.backspaceLength(word));
    String word = "ස්ත්‍රි";
    for (String expected : new String[]{"ස්ත්‍ර", "ස්ත", "ස", ""})
    {
      word = word.substring(0, word.length() - SinhalaText.backspaceLength(word));
      assertEquals(expected, word);
    }
    assertEquals("ච්ච", SinhalaTransliterator.parsePhonetic("chcha"));
    assertEquals(0, SinhalaText.backspaceLength("😀"));
  }

  @Test public void savedWordsRankByExactMatchThenFrequencyAndCanBeRemoved()
  {
    PersonalWords words = new PersonalWords(null);
    assertFalse(words.learn("test@example.org"));
    words.learn("මම");
    words.learn("මල");
    words.learn("මල");
    assertEquals("මල", words.matching("ම").get(0));
    words.learn("ම");
    assertEquals("ම", words.matching("ම").get(0));
    words.remove("මල");
    assertFalse(words.matching("ම").contains("මල"));
  }
}
