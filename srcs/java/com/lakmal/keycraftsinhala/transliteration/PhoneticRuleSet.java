package com.lakmal.keycraftsinhala.transliteration;

/** Ordered phonetic rules with deterministic longest-match selection. */
public final class PhoneticRuleSet
{
  private PhoneticRuleSet() {}

  public static PhoneticRule findLongestMatch(String input, int offset, String[][] rules)
  {
    PhoneticRule best = null;
    for (String[] rule : rules)
    {
      if ("nd".equals(rule[0]) && input.startsWith("ndh", offset)) continue;
      if (input.startsWith(rule[0], offset)
          && (best == null || rule[0].length() > best.input.length()))
      {
        best = new PhoneticRule(rule[0], rule[1], rule[0].length());
      }
    }
    return best;
  }
}
