package com.lakmal.keycraftsinhala.transliteration;

import java.util.Locale;

/** Normalization helpers that never mutate the original input. */
public final class SinglishNormalizer
{
  private SinglishNormalizer() {}

  public static String preserveCase(String input)
  {
    return input == null ? "" : input;
  }

  public static String dictionaryKey(String input)
  {
    return input == null ? "" : input.toLowerCase(Locale.ROOT);
  }

  /** Preserves D/T/N phonemic capitalization while treating other initial capitals as sentence case. */
  public static String normalizeForPhonetic(String input)
  {
    String preserved = preserveCase(input);
    if (preserved.isEmpty()) return "";
    char first = preserved.charAt(0);
    if (!TransliterationConfig.STRICT_CAPITALIZATION)
      preserved = preserved.toLowerCase(Locale.ROOT);
    else if (preserved.length() > 1 && Character.isUpperCase(first)
        && Character.isLowerCase(preserved.charAt(1))
        && first != 'D' && first != 'T' && first != 'N')
    {
      preserved = Character.toLowerCase(first) + preserved.substring(1);
    }
    // The inflected forms of ස්ත්‍රී use dental ත්‍ර, as in ස්ත්‍රියක්.
    if (preserved.startsWith("striy"))
      return "sthr" + preserved.substring(3);
    return preserved;
  }
}
