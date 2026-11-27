package com.lakmal.keycraftsinhala;

import java.text.Normalizer;

/** Editing decisions in UTF-16 units, as used by Android InputConnection. */
public final class SinhalaText
{
  private SinhalaText() {}

  static boolean consonant(char c)
  {
    return c >= 'ක' && c <= 'ෆ' && Character.isLetter(c);
  }

  private static boolean letter(char c)
  {
    return c >= 'අ' && c <= 'ෆ' && Character.isLetter(c);
  }

  public static boolean isSinhalaWord(String word)
  {
    if (word.isEmpty() || !letter(word.charAt(0)))
      return false;
    for (int i = 1; i < word.length(); i++)
    {
      char c = word.charAt(i);
      if (!letter(c) && !mark(c) && !joiner(c))
        return false;
    }
    return true;
  }

  static boolean vowelSign(char c)
  {
    return c >= 'ා' && c <= 'ෟ' && Character.isDefined(c) || c == 'ෲ' || c == 'ෳ';
  }

  static boolean nasal(char c) { return c >= 'ඁ' && c <= 'ඃ'; }

  static boolean mark(char c) { return vowelSign(c) || nasal(c) || c == '්'; }

  static boolean joiner(char c) { return c == '\u200D' || c == '\u200C'; }

  static boolean canAppendJoiner(CharSequence before)
  {
    int length = before == null ? 0 : before.length();
    return length >= 2 && before.charAt(length - 1) == '්'
        && consonant(before.charAt(length - 2));
  }

  /** Return the number of preceding units to replace, or -1 for an orphan mark. */
  static int replaceBeforeMark(CharSequence before, char sign)
  {
    int i = before.length() - 1;
    if (i < 0) return -1;
    char tail = before.charAt(i);
    if (nasal(sign))
    {
      if (nasal(tail)) return 1;
      return consonant(tail) || vowelSign(tail) || tail >= 'අ' && tail <= 'ඖ' ? 0 : -1;
    }
    int end = i;
    while (i >= 0 && (vowelSign(before.charAt(i)) || before.charAt(i) == '්')) i--;
    return i >= 0 && consonant(before.charAt(i)) ? end - i : -1;
  }

  static int backspaceLength(CharSequence before)
  {
    int i = before.length() - 1;
    if (i < 0) return 0;
    char tail = before.charAt(i);
    if (mark(tail))
    {
      for (int count = Math.min(3, before.length()); count > 1; count--)
        if (isSingleVowelSign(before.subSequence(i + 1 - count, i + 1)))
          return count;
      return 1;
    }
    boolean joiner = joiner(tail);
    if (!consonant(tail) && !joiner) return letter(tail) ? 1 : 0;
    int start = i;
    if (!joiner && start > 0 && joiner(before.charAt(start-1))) start--;
    if (start >= 2 && before.charAt(start-1) == '්' && consonant(before.charAt(start-2)))
      return i - start + 2;
    return consonant(tail) ? 1 : 0;
  }

  /** Remove right-hand marks whose base or virama is being deleted. */
  static int backspaceFollowingLength(CharSequence before, int removed, CharSequence after)
  {
    if (after == null)
      return 0;
    char tail = before.charAt(before.length() - 1);
    int end = 0;
    if (letter(tail))
    {
      while (end < after.length() && (mark(after.charAt(end)) || joiner(after.charAt(end))))
        end++;
    }
    else if (mark(tail))
    {
      // A canonically decomposed vowel can straddle the cursor.
      String left = before.subSequence(before.length() - removed, before.length()).toString();
      for (int count = Math.min(3 - removed, after.length()); count > 0; count--)
        if (isSingleVowelSign(left + after.subSequence(0, count)))
          return count;
    }
    if (tail == '්' || joiner(tail))
    {
      while (end < after.length() && joiner(after.charAt(end)))
        end++;
    }
    return end;
  }

  private static boolean isSingleVowelSign(CharSequence text)
  {
    String normalized = Normalizer.normalize(text, Normalizer.Form.NFC);
    return normalized.length() == 1 && vowelSign(normalized.charAt(0));
  }

  /** Use only complete Sinhala clusters before the cursor for lookup.
      The full word and raw cursor offset are still needed for replacement. */
  static String suggestionPrefix(String word, int cursor)
  {
    if (cursor < 0 || cursor > word.length() || word.startsWith("//") || word.startsWith(".."))
      return word;

    int start = cursor;
    int end = cursor;
    while (start > 0 && wordPart(word.charAt(start - 1))) start--;
    while (end < word.length() && wordPart(word.charAt(end))) end++;
    if (start == end)
    {
      // Punctuation kept by the calculator tracker is not a Sinhala prefix.
      for (int i = 0; i < word.length(); i++)
        if (letter(word.charAt(i)))
          return "";
      return word;
    }
    if (!isSinhalaWord(word.substring(start, end)))
      return word;

    while (cursor > start && cursor < end && continuesCluster(word, start, cursor))
      cursor--;
    return word.substring(start, cursor);
  }

  private static boolean continuesCluster(String word, int start, int boundary)
  {
    char next = word.charAt(boundary);
    if (mark(next) || joiner(next))
      return true;
    int preceding = boundary - 1;
    while (preceding > start && joiner(word.charAt(preceding)))
      preceding--;
    return consonant(next) && preceding > start && word.charAt(preceding) == '්'
        && consonant(word.charAt(preceding - 1));
  }

  static boolean wordPart(char c)
  {
    return Character.isLetterOrDigit(c) || mark(c) || joiner(c);
  }
}
