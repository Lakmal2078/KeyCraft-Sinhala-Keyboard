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
    if (!TransliterationConfig.STRICT_CAPITALIZATION)
      return preserved.toLowerCase(Locale.ROOT);
    char first = preserved.charAt(0);
    if (preserved.length() > 1 && Character.isUpperCase(first)
        && Character.isLowerCase(preserved.charAt(1))
        && first != 'D' && first != 'T' && first != 'N')
    {
      return Character.toLowerCase(first) + preserved.substring(1);
    }
    return preserved;
  }
}
