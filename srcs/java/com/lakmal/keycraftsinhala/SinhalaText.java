package com.lakmal.keycraftsinhala;

/** Editing decisions in UTF-16 units, as used by Android InputConnection. */
final class SinhalaText
{
  private SinhalaText() {}

  static boolean consonant(char c)
  {
    return c >= 'ක' && c <= 'ෆ' && Character.isLetter(c);
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
    if (mark(tail)) return 1;
    boolean joiner = joiner(tail);
    if (!consonant(tail) && !joiner) return 0;
    int start = i;
    if (!joiner && start > 0 && joiner(before.charAt(start-1))) start--;
    if (start >= 2 && before.charAt(start-1) == '්' && consonant(before.charAt(start-2)))
      return i - start + 2;
    return consonant(tail) ? 1 : 0;
  }

  static boolean wordPart(char c)
  {
    return Character.isLetterOrDigit(c) || mark(c) || joiner(c);
  }
}
