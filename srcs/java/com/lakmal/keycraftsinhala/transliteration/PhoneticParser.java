package com.lakmal.keycraftsinhala.transliteration;

/** Legacy-compatible Sinhala phonetic parser, extracted from the public facade. */
public final class PhoneticParser
{
  // Standalone vowels (start of word or preceded by vowel)
  private static final String[][] VOWELS_INDEPENDENT = {
    {"aae", "\u0D88"}, // ඈ
    {"aau", "\u0D96"}, // ඖ
    {"ae", "\u0D87"},  // ඇ
    {"æ", "\u0D87"},   // ඇ
    {"ǣ", "\u0D88"},   // ඈ
    {"aa", "\u0D86"},  // ආ
    {"A", "\u0D86"},   // ආ
    {"ii", "\u0D8A"},  // ඊ
    {"ee", "\u0D92"},  // ඒ
    {"I", "\u0D8A"},   // ඊ
    {"uu", "\u0D8C"},  // ඌ
    {"oo", "\u0D95"},  // ඕ
    {"U", "\u0D8C"},   // ඌ
    {"ai", "\u0D93"},  // ඓ
    {"au", "\u0D96"},  // ඖ
    {"ea", "\u0D92"},  // ඒ
    {"ei", "\u0D92"},  // ඒ
    {"oe", "\u0D95"},  // ඕ
    {"a", "\u0D85"},   // අ
    {"i", "\u0D89"},   // ඉ
    {"u", "\u0D8B"},   // උ
    {"e", "\u0D91"},   // එ
    {"E", "\u0D92"},   // ඒ
    {"o", "\u0D94"},   // ඔ
    {"O", "\u0D95"}    // ඕ
  };

  // Dependent vowel signs (attached to consonants)
  private static final String[][] VOWELS_DEPENDENT = {
    {"aae", "\u0DD1"}, // ෑ
    {"aau", "\u0DDE"}, // ෞ
    {"ae", "\u0DD0"},  // ැ
    {"æ", "\u0DD0"},   // ැ
    {"ǣ", "\u0DD1"},   // ෑ
    {"aa", "\u0DCF"},  // ා
    {"A", "\u0DCF"},   // ා
    {"ii", "\u0DD3"},  // ී
    {"ee", "\u0DDA"},  // ේ
    {"I", "\u0DD3"},   // ී
    {"uu", "\u0DD6"},  // ූ
    {"oo", "\u0DDD"},  // ෝ
    {"U", "\u0DD6"},   // ූ
    {"ai", "\u0DDB"},  // ෛ
    {"au", "\u0DDE"},  // ෞ
    {"ea", "\u0DDA"},  // ේ
    {"ei", "\u0DDA"},  // ේ
    {"oe", "\u0DDD"},  // ෝ
    {"a", ""},         // inherent 'a' (no diacritic)
    {"i", "\u0DD2"},   // ි
    {"u", "\u0DD4"},   // ු
    {"e", "\u0DD9"},   // ෙ
    {"E", "\u0DDA"},   // ේ
    {"o", "\u0DDC"},   // ො
    {"O", "\u0DDD"}    // ෝ
  };

  // Consonants ordered by prefix length descending
  private static final String[][] CONSONANTS = {
    {"ksh", "\u0D9A\u0DCA\u200D\u0DC2"},
    {"Ksh", "\u0D9A\u0DCA\u200D\u0DC2"},
    {"chch", "\u0DA0\u0DCA\u200D\u0DA0"}, // ච්ච
    {"nndh", "\u0DB3"}, // ඳ
    {"ndh", "\u0DB1\u0DCA\u200D\u0DB0"}, // න්‍ධ
    {"shh", "\u0DC2\u0DCA"}, // ෂ්
    {"chh", "\u0DA1"}, // ඡ
    {"nng", "\u0D9F"},  // ඟ
    {"mmb", "\u0DB9"},  // ඹ
    {"nnd", "\u0DAC"},  // ඬ
    {"nd", "\u0DB3"},   // ඳ (single nd; "ndh" guarded in parsePhonetic)
    {"kh", "\u0D9B"},  // ඛ
    {"Kh", "\u0D9B"},  // ඛ
    {"gh", "\u0D9D"},  // ඝ
    {"Gh", "\u0D9D"},  // ඝ
    {"Ch", "\u0DA1"},  // ඡ
    {"ch", "\u0DA0"},  // ච
    {"jh", "\u0DA3"},  // ඣ
    {"Jh", "\u0DA3"},  // ඣ
    {"Th", "\u0DAE"},  // ථ
    {"Dh", "\u0DAA"},  // ඪ
    {"th", "\u0DAD"},  // ත
    {"dh", "\u0DB0"},  // ධ
    {"ph", "\u0DB5"},  // ඵ
    {"Ph", "\u0DB5"},  // ඵ
    {"bh", "\u0DB7"},  // භ
    {"Bh", "\u0DB7"},  // භ
    {"sh", "\u0DC1"},  // ශ
    {"Sh", "\u0DC2"},  // ෂ
    {"ny", "\u0DA4"},  // ඤ
    {"gn", "\u0DA4"},  // ඤ
    {"kn", "\u0DA4"},  // ඤ
    {"jn", "\u0DA5"},  // ඥ
    {"nD", "\u0DAC"},  // ඬ
    {"mbh", "\u0DB8\u0DCA\u0DB7"}, // ම්භ
    {"mb", "\u0DB9"},  // ඹ
    {"ng", "\u0D9F"},  // ඟ
    {"k", "\u0D9A"},   // ක
    {"K", "\u0D9A"},   // ක
    {"g", "\u0D9C"},   // ග
    {"G", "\u0D9C"},   // ග
    {"c", "\u0DA0"},   // ච
    {"C", "\u0DA0"},   // ච
    {"j", "\u0DA2"},   // ජ
    {"J", "\u0DA2"},   // ජ
    {"T", "\u0DA8"},   // ඨ
    {"t", "\u0DA7"},   // ට
    {"D", "\u0DA9"},   // ඩ
    {"d", "\u0DAF"},   // ද
    {"N", "\u0DAB"},   // ණ
    {"n", "\u0DB1"},   // න
    {"p", "\u0DB4"},   // ප
    {"P", "\u0DB4"},   // ප
    {"b", "\u0DB6"},   // බ
    {"B", "\u0DB6"},   // බ
    {"m", "\u0DB8"},   // ම
    {"M", "\u0DB8"},   // ම
    {"y", "\u0DBA"},   // ය
    {"Y", "\u0DBA"},   // ය
    {"r", "\u0DBB"},   // ර
    {"R", "\u0DBB"},   // ර
    {"L", "\u0DC5"},   // ළ
    {"l", "\u0DBD"},   // ල
    {"v", "\u0DC0"},   // ව
    {"V", "\u0DC0"},   // ව
    {"w", "\u0DC0"},   // ව
    {"W", "\u0DC0"},   // ව
    {"s", "\u0DC3"},   // ස
    {"S", "\u0DC2"},   // ෂ
    {"h", "\u0DC4"},   // හ
    {"H", "\u0DC4"},   // හ
    {"f", "\u0DC6"},   // ෆ
    {"F", "\u0DC6"},   // ෆ
    {"x", "\u0D82"},   // ං (Anusvaraya)
    {"X", "\u0D82"}    // ං (Anusvaraya)
  };


  private static final String VIRAMA = "\u0DCA"; // ්
  private static final String ZWJ = "\u200D";    // Zero-Width Joiner
  private static final String ANUSVARA = "\u0D82"; // ං

  private static boolean isRuAt(String w, int idx)
  {
    return idx + 1 < w.length()
        && (w.charAt(idx) == 'r' || w.charAt(idx) == 'R')
        && (w.charAt(idx + 1) == 'u' || w.charAt(idx + 1) == 'U');
  }

  private static boolean isUAt(String w, int idx)
  {
    return idx < w.length() && (w.charAt(idx) == 'u' || w.charAt(idx) == 'U');
  }

  public static String parse(String word)
  {
    if (word == null || word.isEmpty())
      return "";

    if ("dh".equals(word))
      return "\u0DB0"; // ධ

    StringBuilder out = new StringBuilder();
    int len = word.length();
    int i = 0;
    // True right after a Gatapilla / ඍ cluster: a following "sh" becomes ෂ.
    boolean ruCluster = false;

    while (i < len)
    {
      if (i == len - 4 && len >= 5 && word.substring(i).equalsIgnoreCase("nawa"))
      {
        out.append("\u0DB1\u0DC0\u0DCF"); // නවා
        break;
      }

      if (i == len - 2 && i > 0 && word.substring(i).equalsIgnoreCase("ge"))
      {
        out.append("\u0D9C\u0DDA"); // ගේ
        break;
      }

      // Independent ඍ (U+0D8D) / ඎ (U+0D8E) at word start: rushi, ruthu, ruuthu.
      // "rth..." at word start gives ඍ + th (Rthu -> ඍතු). Restricted to these
      // shapes so ordinary words keep their normal spelling.
      if (i == 0 && (word.charAt(0) == 'r' || word.charAt(0) == 'R'))
      {
        boolean dbl = isUAt(word, 2);
        int after = dbl ? 3 : 2;
        if (isRuAt(word, 0)
            && (word.regionMatches(true, after, "sh", 0, 2)
                || word.regionMatches(true, after, "th", 0, 2)))
        {
          out.append(dbl ? "\u0D8E" : "\u0D8D");
          i = after;
          ruCluster = true;
          continue;
        }
        if (word.regionMatches(true, 1, "th", 0, 2))
        {
          out.append("\u0D8D"); // ඍ
          i = 1;
          continue;
        }
      }

      // Anusvara before h, j, s, c, k, and before the "ngh" cluster.
      // A bare "ng" is left for the ඟ (U+0D9F) consonant below.
      if (i > 0 && out.length() > 0 && i + 1 < len
          && (word.charAt(i) == 'n' || word.charAt(i) == 'N'))
      {
        char next = Character.toLowerCase(word.charAt(i + 1));
        boolean nasalGha = next == 'g' && word.regionMatches(true, i, "ngh", 0, 3);
        if (next == 'h' || next == 'j' || next == 's' || next == 'c' || next == 'k' || nasalGha)
        {
          out.append(ANUSVARA);
          i++;
          continue;
        }
      }

      // Repaya: U+0DBB + U+0DCA + U+200D + following consonant
      if ((word.charAt(i) == 'r' || word.charAt(i) == 'R') && i > 0 && i + 1 < len)
      {
        char nextChar = word.charAt(i + 1);
        if (!isVowel(nextChar) && nextChar != 'r' && nextChar != 'R')
        {
          com.lakmal.keycraftsinhala.transliteration.PhoneticRule nextRule =
              com.lakmal.keycraftsinhala.transliteration.PhoneticRuleSet.findLongestMatch(word, i + 1, CONSONANTS);
          if (nextRule != null && !nextRule.output.equals(ANUSVARA))
          {
            out.append("\u0DBB").append(VIRAMA).append(ZWJ).append(nextRule.output);
            i += 1 + nextRule.input.length();

            com.lakmal.keycraftsinhala.transliteration.PhoneticRule vowelRule =
                com.lakmal.keycraftsinhala.transliteration.PhoneticRuleSet.findLongestMatch(word, i, VOWELS_DEPENDENT);
            if (vowelRule != null)
            {
              out.append(vowelRule.output);
              i += vowelRule.input.length();
            }
            continue;
          }
        }
      }

      // Consonant match
      com.lakmal.keycraftsinhala.transliteration.PhoneticRule consonantRule =
          com.lakmal.keycraftsinhala.transliteration.PhoneticRuleSet.findLongestMatch(word, i, CONSONANTS);
      String matchedKey = consonantRule == null ? null : consonantRule.input;
      String matchedConsonant = consonantRule == null ? null : consonantRule.output;
      int matchedConsonantLen = consonantRule == null ? 0 : consonantRule.input.length();

      if (matchedConsonant != null)
      {
        boolean retroflexSh = ruCluster;
        ruCluster = false;
        if (retroflexSh && "sh".equals(matchedKey))
          matchedConsonant = "\u0DC2"; // ෂ after ඍ / ෘ (krushi -> කෘෂි)

        i += matchedConsonantLen;

        if (matchedConsonant.equals(ANUSVARA))
        {
          out.append(ANUSVARA);
          continue;
        }

        // Gatapilla ෘ (U+0DD8) and Diga Gatapilla ෲ (U+0DF2): kru, kruu.
        // Must run before the Rakaransaya check below.
        if (isRuAt(word, i))
        {
          boolean dbl = isUAt(word, i + 2);
          out.append(matchedConsonant).append(dbl ? "\u0DF2" : "\u0DD8");
          i += dbl ? 3 : 2;
          ruCluster = true;
          continue;
        }

        // Check for conjuncts: Yanshaya (C + y + V) or Rakaranshaya (C + r + V)
        boolean hasConjunct = false;
        if (i < len)
        {
          char nextChar = word.charAt(i);
          if ((nextChar == 'y' || nextChar == 'Y') && (i + 1 == len || isVowel(word.charAt(i + 1)) || word.charAt(i + 1) == 'a'))
          {
            out.append(matchedConsonant).append(VIRAMA).append(ZWJ).append("\u0DBA");
            i++;
            hasConjunct = true;
          }
          else if ((nextChar == 'r' || nextChar == 'R') && (i + 1 == len || isVowel(word.charAt(i + 1))))
          {
            out.append(matchedConsonant).append(VIRAMA).append(ZWJ).append("\u0DBB");
            i++;
            hasConjunct = true;
          }
        }

        com.lakmal.keycraftsinhala.transliteration.PhoneticRule dependentVowelRule =
            com.lakmal.keycraftsinhala.transliteration.PhoneticRuleSet.findLongestMatch(word, i, VOWELS_DEPENDENT);
        String matchedVowelSign = dependentVowelRule == null ? null : dependentVowelRule.output;
        int matchedVowelLen = dependentVowelRule == null ? 0 : dependentVowelRule.input.length();
        if (i < len && word.charAt(i) == 'æ')
        {
          matchedVowelSign = "\u0DD0"; // ැ
          matchedVowelLen = 1;
        }

        if (matchedVowelSign != null)
        {
          if (!hasConjunct)
            out.append(matchedConsonant);
          out.append(matchedVowelSign);
          i += matchedVowelLen;
        }
        else
        {
          if (!hasConjunct)
            out.append(matchedConsonant);
          out.append(VIRAMA);
        }
      }
      else
      {
        com.lakmal.keycraftsinhala.transliteration.PhoneticRule independentVowelRule =
            com.lakmal.keycraftsinhala.transliteration.PhoneticRuleSet.findLongestMatch(word, i, VOWELS_INDEPENDENT);
        String matchedVowel = independentVowelRule == null ? null : independentVowelRule.output;
        int matchedVowelLen = independentVowelRule == null ? 0 : independentVowelRule.input.length();

        if (matchedVowel != null)
        {
          out.append(matchedVowel);
          i += matchedVowelLen;
        }
        else
        {
          out.append(word.charAt(i));
          i++;
        }
      }
    }

    return out.toString();
  }

  private static boolean isVowel(char c)
  {
    c = Character.toLowerCase(c);
    return c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u';
  }
  private PhoneticParser() {}
}
