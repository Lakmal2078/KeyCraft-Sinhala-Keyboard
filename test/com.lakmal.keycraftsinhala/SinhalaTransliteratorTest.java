package com.lakmal.keycraftsinhala;

import org.junit.Test;
import static org.junit.Assert.*;

public class SinhalaTransliteratorTest
{
  @Test
  public void everydaySpellingVariantsUseTheSameFirstSuggestion()
  {
    assertEquals("ආයුබෝවන්", SinhalaTransliterator.getSuggestions("aayuboovan").get(0));
    assertEquals("යාලුවා", SinhalaTransliterator.getSuggestions("yaaluvaa").get(0));
    assertEquals("වතුර", SinhalaTransliterator.getSuggestions("vathura").get(0));
    assertEquals(SinhalaTransliterator.parsePhonetic("kryae"),
        SinhalaTransliterator.parsePhonetic("kryæ"));
    assertEquals(SinhalaTransliterator.parsePhonetic("kraae"),
        SinhalaTransliterator.parsePhonetic("krǣ"));
  }

  @Test
  public void nirmanayaAndItsInflectionsUseCanonicalSinhala()
  {
    assertEquals("නිර්මාණ", SinhalaTransliterator.transliterateWord("nirmana"));
    assertEquals("නිර්මාණය", SinhalaTransliterator.transliterateWord("nirmanaya"));
    assertEquals("නිර්මාණය", SinhalaTransliterator.transliterateWord("nirmaanaya"));
    assertEquals("නිර්මාණයක්", SinhalaTransliterator.transliterateWord("nirmanayak"));
    assertEquals("නිර්මාණයේ", SinhalaTransliterator.transliterateWord("nirmanaye"));
  }

  @Test
  public void commonSpellingsUseCanonicalGatapillaAndConjuncts()
  {
    assertEquals("කෘෂිකර්මය", SinhalaTransliterator.transliterateWord("krushikarmaya"));
    assertEquals("කෘෂිකර්මය", SinhalaTransliterator.getSuggestions("krushikarmaya").get(0));
    for (String spelling : new String[]{"stri", "sthri", "strii", "stree", "sthree"})
    {
      assertEquals(spelling, "ස්ත්‍රී", SinhalaTransliterator.transliterateWord(spelling));
      assertEquals(spelling, "ස්ත්‍රී", SinhalaTransliterator.getSuggestions(spelling).get(0));
    }
  }

  @Test
  public void intervocalicDhSupportsEverydaySinglish()
  {
    assertEquals("නිවරදි", SinhalaTransliterator.transliterateWord("niwaradhi"));
    assertEquals("නිවරදි", SinhalaTransliterator.getSuggestions("niwaradhi").get(0));
    assertTrue(SinhalaTransliterator.getSuggestions("niwaradhi").contains("නිවරධි"));
    assertEquals("හොඳටම", SinhalaTransliterator.getSuggestions("hodhatama").get(0));
    assertEquals("මාධව", SinhalaTransliterator.getSuggestions("madhawa").get(0));
  }

  @Test
  public void naUsesTheInherentVowelAndKeepsTheLongVowelSpellingAvailable()
  {
    SinhalaLearner learner = new SinhalaLearner((android.content.SharedPreferences) null);
    learner.recordCorrection("na", "නෑ");
    assertEquals("න", SinhalaTransliterator.transliterateWord("na", learner));
    assertEquals("න", SinhalaTransliterator.getSuggestions("na", learner).get(0));
    assertEquals("ණ", SinhalaTransliterator.transliterateWord("Na"));
    assertEquals("නෑ", SinhalaTransliterator.transliterateWord("naa"));
    assertEquals("නෑ", SinhalaTransliterator.transliterateWord("naae"));
  }

  @Test
  public void suggestionsRespectPhonemicCapitalization()
  {
    assertEquals("ඩ", SinhalaTransliterator.getSuggestions("Da").get(0));
    assertEquals("ද", SinhalaTransliterator.getSuggestions("da").get(0));
  }

  @Test
  public void testRequestedWords()
  {
    // The specific words requested by the user
    assertEquals("අම්මා", SinhalaTransliterator.transliterate("amma"));
    assertEquals("සිංහල", SinhalaTransliterator.transliterate("sinhala"));
    assertEquals("කොහොමද", SinhalaTransliterator.transliterate("kohomada"));
    assertEquals("ලක්මාල්", SinhalaTransliterator.transliterate("lakmal"));
  }

  @Test
  public void testCorrectedDictionaryWordsAndPhrase()
  {
    assertEquals("\u0D86\u0DBB\u0DB8\u0DCA\u0DB7", SinhalaTransliterator.transliterate("Aaramba"));
    assertEquals("\u0DB1\u0DB8\u0DC3\u0DCA\u0D9A\u0DCF\u0DBB", SinhalaTransliterator.transliterate("namaskara"));
    assertEquals("\u0DC1\u0DCA\u200D\u0DBB\u0DD3\u0020\u0DBD\u0D82\u0D9A\u0DCF\u0DC0", SinhalaTransliterator.transliterate("Sri Lanka"));
  }

  @Test
  public void testMultipleSuggestions()
  {
    java.util.List<String> lakmalSug = SinhalaTransliterator.getSuggestions("lakmal");
    assertEquals("ලක්මාල්", lakmalSug.get(0)); // Primary
    assertTrue(lakmalSug.contains("ලක්මල්"));  // Secondary phonetic alternative
    assertTrue(lakmalSug.contains("lakmal"));  // English raw word

    java.util.List<String> ammaSug = SinhalaTransliterator.getSuggestions("amma");
    assertEquals("අම්මා", ammaSug.get(0));
    assertTrue(ammaSug.contains("අම්ම"));
  }

  @Test
  public void testCommonConversationalWords()
  {
    assertEquals("තාත්තා", SinhalaTransliterator.transliterate("thaththa"));
    assertEquals("අක්කා", SinhalaTransliterator.transliterate("akka"));
    assertEquals("අයියා", SinhalaTransliterator.transliterate("aiyya"));
    assertEquals("මල්ලි", SinhalaTransliterator.transliterate("malli"));
    assertEquals("නංගි", SinhalaTransliterator.transliterate("nangi"));
    assertEquals("නැති", SinhalaTransliterator.transliterate("nathi"));
    assertEquals("නැති", SinhalaTransliterator.transliterate("Nathi"));
    assertEquals("නැති", SinhalaTransliterator.transliterate("naethi"));
    assertEquals("මොකද", SinhalaTransliterator.transliterate("mokada"));
    assertEquals("ලංකා", SinhalaTransliterator.transliterate("lanka"));
    assertEquals("ආයුබෝවන්", SinhalaTransliterator.transliterate("ayubowan"));
    assertEquals("ස්තූතියි", SinhalaTransliterator.transliterate("sthuthi"));
  }

  @Test
  public void testPhaseFourVowelCombinationsAndDictionaryAccuracy()
  {
    assertEquals("නැති", SinhalaTransliterator.transliterate("nathi"));
    assertEquals("නැති", SinhalaTransliterator.transliterate("Nathi"));
    assertEquals("නැති", SinhalaTransliterator.transliterate("naethi"));

    assertEquals("ඇ", SinhalaTransliterator.transliterate("ae"));
    assertEquals("ඇ", SinhalaTransliterator.transliterate("æ"));
    assertEquals("කැ", SinhalaTransliterator.transliterate("kae"));
    assertEquals("කැ", SinhalaTransliterator.transliterate("kæ"));
    assertEquals("ආ", SinhalaTransliterator.transliterate("aa"));
    assertEquals("කා", SinhalaTransliterator.transliterate("kaa"));
    assertEquals("ඒ", SinhalaTransliterator.transliterate("ee"));
    assertEquals("කේ", SinhalaTransliterator.transliterate("kee"));
  }

  @Test
  public void testPhaseFourDictionaryOutranksConflictingLearnedCorrection()
  {
    SinhalaLearner learner = new SinhalaLearner((android.content.SharedPreferences) null);
    learner.recordCorrection("nathi", "නති");

    assertEquals("නැති", SinhalaTransliterator.transliterateWord("nathi", learner));
    assertEquals("නැති", SinhalaTransliterator.analyze("nathi", learner)
        .candidates.get(0).text);
    assertEquals("නැති", SinhalaTransliterator.getSuggestions("nathi", learner).get(0));

    learner.recordCorrection("qwerty", "සිංහල");
    assertEquals("සිංහල", SinhalaTransliterator.transliterateWord("qwerty", learner));
    learner.clearLearnedData();
  }

  @Test
  public void testPhoneticRules()
  {
    // Systematic phonetic rules
    // Vowels
    assertEquals("අ", SinhalaTransliterator.transliterate("a"));
    assertEquals("ආ", SinhalaTransliterator.transliterate("aa"));
    assertEquals("ඇ", SinhalaTransliterator.transliterate("ae"));
    assertEquals("ඉ", SinhalaTransliterator.transliterate("i"));
    assertEquals("උ", SinhalaTransliterator.transliterate("u"));
    assertEquals("එ", SinhalaTransliterator.transliterate("e"));
    assertEquals("ඔ", SinhalaTransliterator.transliterate("o"));

    // Consonant + Vowels
    assertEquals("ක", SinhalaTransliterator.transliterate("ka"));
    assertEquals("කා", SinhalaTransliterator.transliterate("kaa"));
    assertEquals("කැ", SinhalaTransliterator.transliterate("kae"));
    assertEquals("කි", SinhalaTransliterator.transliterate("ki"));
    assertEquals("කී", SinhalaTransliterator.transliterate("kii"));
    assertEquals("කු", SinhalaTransliterator.transliterate("ku"));
    assertEquals("කූ", SinhalaTransliterator.transliterate("kuu"));
    assertEquals("කෙ", SinhalaTransliterator.transliterate("ke"));
    assertEquals("කේ", SinhalaTransliterator.transliterate("kee"));
    assertEquals("කො", SinhalaTransliterator.transliterate("ko"));
    assertEquals("කෝ", SinhalaTransliterator.transliterate("koo"));
    assertEquals("ක්", SinhalaTransliterator.transliterate("k"));

    // Conjuncts: Rakaranshaya and Yanshaya
    assertEquals("ක්‍රමය", SinhalaTransliterator.transliterate("kramaya"));
    assertEquals("ව්‍යංජන", SinhalaTransliterator.transliterate("vyanjana"));
  }

  @Test
  public void testSentencesWithPunctuation()
  {
    assertEquals("අම්මා, කොහොමද?", SinhalaTransliterator.transliterate("amma, kohomada?"));
    assertEquals("සුබ උදෑසනක්!", SinhalaTransliterator.transliterate("suba udaesanak!"));
    assertEquals("සිංහල භාෂාව", SinhalaTransliterator.transliterate("sinhala bhashawa"));
  }

  @Test
  public void testUserReportedWordsAndPhrases()
  {
    assertEquals("ගමගේ", SinhalaTransliterator.transliterate("gamage"));
    assertEquals("ගමගේ", SinhalaTransliterator.transliterate("gamagee"));
    assertEquals("නැතුව", SinhalaTransliterator.transliterate("nethuwa"));
    assertEquals("විදියට", SinhalaTransliterator.transliterate("widiyata"));
    assertEquals("හරියට", SinhalaTransliterator.transliterate("hariyata"));
    assertEquals("හදන්න", SinhalaTransliterator.transliterate("hadanna"));
    assertEquals("පුළුවන්ද", SinhalaTransliterator.transliterate("puluwanda"));
    assertEquals("වැඩ", SinhalaTransliterator.transliterate("wada"));
    assertEquals("කරනවා", SinhalaTransliterator.transliterate("karanawa"));
    assertEquals("යනවා", SinhalaTransliterator.transliterate("yanawa"));

    // Verify diacritic safety in suggestions
    java.util.List<String> gamageeSug = SinhalaTransliterator.getSuggestions("gamagee");
    assertFalse(gamageeSug.contains("ගමගේා")); // Invalid diacritic combination must NOT be generated
  }

  @Test
  public void testLeadingAnusvaraPrevention()
  {
    String res = SinhalaTransliterator.parsePhonetic("nsb");
    assertFalse(res.startsWith("\u0D82")); // Anusvara (ං) must never be the first character
  }

  @Test
  public void testNextWordPredictions()
  {
    java.util.List<String> subaPredictions = SinhalaTransliterator.getNextWordPredictions("සුබ");
    assertTrue(subaPredictions.contains("උදෑසනක්"));
    assertTrue(subaPredictions.contains("දවසක්"));

    java.util.List<String> sthuthiPredictions = SinhalaTransliterator.getNextWordPredictions("sthuthi");
    assertTrue(sthuthiPredictions.contains("බොහොම"));

    java.util.List<String> jayaPredictions = SinhalaTransliterator.getNextWordPredictions("ජය");
    assertTrue(jayaPredictions.contains("වේවා"));
  }

  @Test
  public void testNullEmptyAndWhitespaceInputs()
  {
    assertNull(SinhalaTransliterator.transliterate(null));
    assertEquals("", SinhalaTransliterator.transliterate(""));
    assertEquals("", SinhalaTransliterator.transliterateWord(null));
    assertTrue(SinhalaTransliterator.getSuggestions(null).isEmpty());
    assertTrue(SinhalaTransliterator.getSuggestions("").isEmpty());
    assertTrue(SinhalaTransliterator.getNextWordPredictions(null).isEmpty());
    assertTrue(SinhalaTransliterator.getNextWordPredictions(" \t\n").isEmpty());
  }

  @Test
  public void testCaseWhitespaceAndPunctuationEdges()
  {
    assertEquals("අම්මා! 123", SinhalaTransliterator.transliterate("Amma! 123"));
    assertTrue(SinhalaTransliterator.getNextWordPredictions("  සුබ \t")
        .contains("උදෑසනක්"));
    assertTrue(SinhalaTransliterator.getNextWordPredictions("STHUTHI")
        .contains("බොහොම"));
    assertTrue(SinhalaTransliterator.getNextWordPredictions("සුබ!")
        .contains("උදෑසනක්"));
  }

  @Test
  public void testExpandedVocabulary()
  {
    assertEquals("කවදාද", SinhalaTransliterator.transliterate("kawadada"));
    assertEquals("කීයද", SinhalaTransliterator.transliterate("kiyada"));
    assertEquals("කරමු", SinhalaTransliterator.transliterate("karamu"));
    assertEquals("දෙන්න", SinhalaTransliterator.transliterate("denna"));
    assertEquals("ගන්න", SinhalaTransliterator.transliterate("ganna"));
    assertEquals("බැරිද", SinhalaTransliterator.transliterate("baerida"));
    assertEquals("අලුත්", SinhalaTransliterator.transliterate("aluth"));
    assertEquals("අවුරුද්දක්", SinhalaTransliterator.transliterate("awuruddak"));
    assertEquals("මිතුරා", SinhalaTransliterator.transliterate("mithura"));
    assertEquals("ෆෝන්", SinhalaTransliterator.transliterate("phone"));
    assertEquals("ඇප්", SinhalaTransliterator.transliterate("app"));
    assertEquals("කීබෝඩ්", SinhalaTransliterator.transliterate("keyboard"));
  }

  @Test
  public void testHelakuruSinglishV2()
  {
    // Gatapilla and Diga Gatapilla
    assertEquals("කෘ", SinhalaTransliterator.transliterate("kru"));
    assertEquals("කෲ", SinhalaTransliterator.transliterate("kruu"));
    assertEquals("අමෘත", SinhalaTransliterator.transliterate("amrutha"));
    assertEquals("කෘත්‍ය", SinhalaTransliterator.transliterate("kruthya"));
    assertEquals("කෘෂි", SinhalaTransliterator.transliterate("krushi"));

    // Independent ඍ
    assertEquals("ඍෂි", SinhalaTransliterator.transliterate("rushi"));
    assertEquals("ඍෂි", SinhalaTransliterator.transliterate("Rushi"));
    assertEquals("ඍතු", SinhalaTransliterator.transliterate("ruthu"));
    assertEquals("ඍතු", SinhalaTransliterator.transliterate("Rthu"));
    assertEquals("ඍ", SinhalaTransliterator.transliterate("Iru"));

    // Sanñaka consonants
    assertEquals("සඳ", SinhalaTransliterator.transliterate("sanda"));
    assertEquals("හඳ", SinhalaTransliterator.transliterate("handa"));
    assertEquals("බඳ", SinhalaTransliterator.transliterate("banda"));
    assertEquals("ගඟ", SinhalaTransliterator.transliterate("ganga"));
    assertEquals("කොළඹ", SinhalaTransliterator.transliterate("kolamba"));

    // Conjuncts
    assertEquals("විද්‍යාව", SinhalaTransliterator.transliterate("vidyaawa"));
    assertEquals("චන්ද්‍ර", SinhalaTransliterator.transliterate("chandra"));

    // Helakuru Singlish lexicon
    assertEquals("වැඩ", SinhalaTransliterator.transliterate("wada"));
    assertEquals("දැන්", SinhalaTransliterator.transliterate("dan"));
    assertEquals("තේරුණා", SinhalaTransliterator.transliterate("theruna"));
    assertEquals("වුණේ", SinhalaTransliterator.transliterate("une"));
    assertEquals("දන්නේ", SinhalaTransliterator.transliterate("danne"));
    assertEquals("කරන්නේ", SinhalaTransliterator.transliterate("karanne"));
    assertEquals("හොඳටම", SinhalaTransliterator.transliterate("hodatama"));
    assertEquals("පුළුවන්ද", SinhalaTransliterator.transliterate("puluwanda"));
  }

  @Test
  public void testSuggestionsAlwaysContainRawEnglish()
  {
    String[] inputs = {"ka", "lakmal", "sanda", "ganga", "wifi", "zoom", "anda"};
    for (String input : inputs)
    {
      assertTrue(input + " must include raw English",
          SinhalaTransliterator.getSuggestions(input).contains(input));
    }
  }

  @Test
  public void testNdAlternativeSuggestions()
  {
    java.util.List<String> andaSug = SinhalaTransliterator.getSuggestions("anda");
    assertEquals("අඳ", andaSug.get(0));
    assertTrue(andaSug.contains("අඬ"));

    java.util.List<String> kandaSug = SinhalaTransliterator.getSuggestions("kanda");
    assertTrue(kandaSug.contains("කඳ"));
    assertTrue(kandaSug.contains("කන්ද"));
  }

  @Test
  public void testHelakuruPhoneticBasicWords()
  {
    assertEquals("අම්මා", SinhalaTransliterator.transliterate("ammaa"));
    assertEquals("තාත්තා", SinhalaTransliterator.transliterate("thaaththaa"));
    assertEquals("මම", SinhalaTransliterator.transliterate("mama"));
    assertEquals("ඔබ", SinhalaTransliterator.transliterate("oba"));
    assertEquals("ගෙදර", SinhalaTransliterator.transliterate("gedhara"));
  }

  @Test
  public void testSangyakaConsonants()
  {
    assertEquals("සඳ", SinhalaTransliterator.transliterate("sanndha"));
    assertEquals("ගඟ", SinhalaTransliterator.transliterate("gannga"));
    assertEquals("හඹ", SinhalaTransliterator.transliterate("hammba"));
    assertEquals("කඬ", SinhalaTransliterator.transliterate("kannda"));
  }

  @Test
  public void testLigaturesYansayaAndRakaransaya()
  {
    assertEquals("ක්‍රමය", SinhalaTransliterator.transliterate("kramaya"));
    assertEquals("ප්‍රකාශන", SinhalaTransliterator.transliterate("prakaashana"));
    assertEquals("ව්‍යාපාර", SinhalaTransliterator.transliterate("vyaapaara"));
    assertEquals("විද්‍යාව", SinhalaTransliterator.transliterate("vidhyaava"));
  }

  @Test
  public void testSinhalaConjunctsUseExpectedUnicodeJoinerSequences()
  {
    assertEquals("\u0D9A\u0DCA\u200D\u0DBB\u0DB8\u0DBA",
        SinhalaTransliterator.transliterate("kramaya"));
    assertEquals("\u0DC0\u0DCA\u200D\u0DBA\u0D82\u0DA2\u0DB1",
        SinhalaTransliterator.transliterate("vyanjana"));
    assertEquals("\u0DB0\u0DBB\u0DCA\u200D\u0DB8",
        SinhalaTransliterator.transliterate("dharma"));
  }

  @Test
  public void testHalKirimaAndPiliCombinations()
  {
    assertEquals("සිංහල", SinhalaTransliterator.transliterate("sinhala"));
    assertEquals("සුබ", SinhalaTransliterator.transliterate("suba"));
    assertEquals("දවසක්", SinhalaTransliterator.transliterate("dhavasak"));
    assertEquals("කොහොමද", SinhalaTransliterator.transliterate("kohomadha"));
  }

  @Test
  public void testCapitalDMapsToRetroflexDa()
  {
    assertEquals("ද", SinhalaTransliterator.transliterate("da"));
    assertEquals("දි", SinhalaTransliterator.transliterate("di"));
    assertEquals("දු", SinhalaTransliterator.transliterate("du"));
    assertEquals("ඩ", SinhalaTransliterator.transliterate("Da"));
    assertEquals("ඩි", SinhalaTransliterator.transliterate("Di"));
    assertEquals("ඩු", SinhalaTransliterator.transliterate("Du"));
    assertEquals("දවසක්", SinhalaTransliterator.transliterate("Dawasak"));
  }

  @Test
  public void testStrictPhonemicCapitalization()
  {
    assertEquals("ද", SinhalaTransliterator.transliterate("da"));
    assertEquals("ඩ", SinhalaTransliterator.transliterate("Da"));
    assertEquals("ට", SinhalaTransliterator.transliterate("ta"));
    assertEquals("ඨ", SinhalaTransliterator.transliterate("Ta"));
    assertEquals("නි", SinhalaTransliterator.transliterate("ni"));
    assertEquals("ණි", SinhalaTransliterator.transliterate("Ni"));

    // Ordinary sentence-initial capitals retain their familiar behavior.
    assertEquals("සුබ", SinhalaTransliterator.transliterate("Suba"));
    assertEquals("කොහොමද", SinhalaTransliterator.transliterate("Kohomada"));
    assertEquals("දවසක්", SinhalaTransliterator.transliterate("Dawasak"));
  }

  @Test
  public void testInternalAnalysisReturnsBoundedSinhalaCandidates()
  {
    com.lakmal.keycraftsinhala.transliteration.TransliterationResult result =
        SinhalaTransliterator.analyze("kohomada");
    assertEquals("kohomada", result.input);
    assertEquals("කොහොමද", result.getBestCandidate());
    assertEquals(SinhalaTransliterator.transliterateWord("kohomada"), result.getBestCandidate());
    assertTrue(result.candidates.size() <=
        com.lakmal.keycraftsinhala.transliteration.TransliterationConfig.MAX_CANDIDATES);
    for (com.lakmal.keycraftsinhala.transliteration.TransliterationCandidate candidate : result.candidates)
    {
      assertFalse("Raw English must not be exposed as a transliteration candidate",
          "kohomada".equals(candidate.text));
      assertNotNull(candidate.ruleId);
    }
    assertTrue(SinhalaTransliterator.analyze("").candidates.isEmpty());
    assertTrue(SinhalaTransliterator.analyze(null).candidates.isEmpty());

    com.lakmal.keycraftsinhala.transliteration.TransliterationResult retroflex =
        SinhalaTransliterator.analyze("Da");
    assertEquals("ඩ", retroflex.getBestCandidate());
    assertEquals("phonetic-primary", retroflex.candidates.get(0).ruleId);

    com.lakmal.keycraftsinhala.transliteration.TransliterationResult lexical =
        SinhalaTransliterator.analyze("sinhala");
    assertEquals("සිංහල", lexical.getBestCandidate());
    assertEquals("dictionary-exact", lexical.candidates.get(0).ruleId);
  }

  @Test
  public void testPhaseThreeLexicalVowelInference()
  {
    assertEquals("ගැලපෙනවා", SinhalaTransliterator.transliterate("galapenawa"));
    com.lakmal.keycraftsinhala.transliteration.TransliterationResult result =
        SinhalaTransliterator.analyze("galapenawa");
    assertEquals("ගැලපෙනවා", result.getBestCandidate());
    assertEquals("dictionary-exact", result.candidates.get(0).ruleId);
  }

  @Test
  public void testPhaseThreeLearnedCorrectionAndContextRanking()
  {
    SinhalaLearner learner = new SinhalaLearner((android.content.SharedPreferences) null);
    learner.recordCorrection("qwerty", "සිංහල");
    com.lakmal.keycraftsinhala.transliteration.TransliterationResult corrected =
        SinhalaTransliterator.analyze("qwerty", learner);
    assertEquals("සිංහල", corrected.getBestCandidate());
    assertEquals("learned-correction", corrected.candidates.get(0).ruleId);

    learner.recordCorrection("amma", "අම්ම");
    com.lakmal.keycraftsinhala.transliteration.TransliterationResult canonical =
        SinhalaTransliterator.analyze("amma", learner);
    assertEquals("අම්මා", canonical.getBestCandidate());
    assertEquals("dictionary-exact", canonical.candidates.get(0).ruleId);

    learner.clearLearnedData();
    learner.recordTransition("මම", "අඬ");
    com.lakmal.keycraftsinhala.transliteration.TransliterationResult contextual =
        SinhalaTransliterator.analyze("anda", "මම", learner);
    assertEquals("අඬ", contextual.getBestCandidate());
    assertTrue(contextual.candidates.get(0).ruleId.contains("+context"));
  }

  @Test
  public void testLongestMatchPhoneticSequences()
  {
    assertEquals("ඈ", SinhalaTransliterator.parsePhonetic("aae"));
    assertEquals("ඡ්", SinhalaTransliterator.parsePhonetic("chh"));
    assertEquals("ක්‍ෂ්", SinhalaTransliterator.parsePhonetic("ksh"));
    assertEquals("කැ", SinhalaTransliterator.parsePhonetic("kae"));
  }

  @Test
  public void testInitialCapitalLetters()
  {
    assertEquals("මම", SinhalaTransliterator.transliterate("Mama"));
    assertEquals("කොහොමද", SinhalaTransliterator.transliterate("Kohomada"));
    assertEquals("ලංකා", SinhalaTransliterator.transliterate("Lanka"));
    assertEquals("සුබ", SinhalaTransliterator.transliterate("Suba"));
    assertEquals("අපි", SinhalaTransliterator.transliterate("Api"));
    assertEquals("ඔයා", SinhalaTransliterator.transliterate("Oya"));
    assertEquals("ම්", SinhalaTransliterator.transliterate("M"));
    assertEquals("ක්", SinhalaTransliterator.transliterate("K"));
    assertEquals("ම", SinhalaTransliterator.transliterate("Ma"));
    assertEquals("ක", SinhalaTransliterator.transliterate("Ka"));
  }

  @Test
  public void testAedaPillaAndPhonetics()
  {
    assertEquals("සැ", SinhalaTransliterator.transliterate("sae"));
    assertEquals("සෑ", SinhalaTransliterator.transliterate("saae"));
    assertEquals("සැප", SinhalaTransliterator.transliterate("saepa"));
    assertEquals("සැක", SinhalaTransliterator.transliterate("saeka"));
    assertEquals("සැලසුම", SinhalaTransliterator.transliterate("saelasuma"));

    java.util.List<String> sepaSug = SinhalaTransliterator.getSuggestions("sepa");
    assertTrue("sepa should suggest සැප", sepaSug.contains("සැප"));

    java.util.List<String> saeSug = SinhalaTransliterator.getSuggestions("sae");
    assertEquals("සැ", saeSug.get(0));
  }

  @Test
  public void testPrefixSuggestionsForPlacesAndCountries()
  {
    java.util.List<String> colomSug = SinhalaTransliterator.getSuggestions("colom");
    assertTrue("colom should suggest කොළඹ", colomSug.contains("කොළඹ"));

    java.util.List<String> lankSug = SinhalaTransliterator.getSuggestions("lank");
    assertTrue("lank should suggest ලංකාව", lankSug.contains("ලංකාව"));

    java.util.List<String> gampSug = SinhalaTransliterator.getSuggestions("gamp");
    assertTrue("gamp should suggest ගම්පහ", gampSug.contains("ගම්පහ"));
  }

  @Test
  public void testPrefixSuggestionsHaveDeterministicPriority()
  {
    java.util.List<String> suggestions = SinhalaTransliterator.getSuggestions("ka");
    assertEquals(java.util.Arrays.asList("කළා", "ක", "කෑම", "කලින්"),
        suggestions.subList(0, 4));

    // Repeated lookups must preserve the same ordering.
    for (int i = 0; i < 20; i++)
    {
      assertEquals(suggestions, SinhalaTransliterator.getSuggestions("ka"));
    }
  }

  @Test
  public void testEverydaySinglishAndPlaceNameRegressions()
  {
    assertEquals("ශ්‍රී ලංකාව", SinhalaTransliterator.transliterate("srilanka"));
    assertEquals("කොළඹ", SinhalaTransliterator.transliterate("colombo"));
    assertEquals("මහනුවර", SinhalaTransliterator.transliterate("kandy"));
    assertEquals("ගාල්ල", SinhalaTransliterator.transliterate("galle"));
    assertEquals("මාතර", SinhalaTransliterator.transliterate("matara"));
    assertEquals("කුරුණෑගල", SinhalaTransliterator.transliterate("kurunegala"));
    assertEquals("අනුරාධපුරය", SinhalaTransliterator.transliterate("anuradhapura"));
    assertEquals("ත්‍රිකුණාමලය", SinhalaTransliterator.transliterate("trincomalee"));
    assertEquals("ඉන්දියාව", SinhalaTransliterator.transliterate("india"));
    assertEquals("ඕස්ට්‍රේලියාව", SinhalaTransliterator.transliterate("australia"));

    assertEquals("අම්මා, මම ගෙදර යනවා!",
        SinhalaTransliterator.transliterate("amma, mama gedhara yanawa!"));
    assertEquals("සුබ උදෑසනක්!", SinhalaTransliterator.transliterate("suba udaesanak!"));
  }

  @Test
  public void testNoDuplicateCommonWordsAndCleanedMappings() throws Exception
  {
    assertEquals("උනා", SinhalaTransliterator.transliterate("una"));
    assertEquals("වුණා", SinhalaTransliterator.transliterate("wuna"));
    assertEquals("ලවීනියා", SinhalaTransliterator.transliterate("lavinia"));
    assertEquals("ගල්කිස්ස", SinhalaTransliterator.transliterate("galkissa"));
    assertEquals("නදීක", SinhalaTransliterator.transliterate("nadeeka"));
    assertEquals("නදීකා", SinhalaTransliterator.transliterate("nadeekaa"));
    assertEquals("මොනද", SinhalaTransliterator.transliterate("monada"));
    assertEquals("මොනවද", SinhalaTransliterator.transliterate("monawada"));
    assertEquals("බලංගොඩ", SinhalaTransliterator.transliterate("balangoda"));

    String src = new String(java.nio.file.Files.readAllBytes(
        new java.io.File("srcs/java/com/lakmal/keycraftsinhala/SinhalaTransliterator.java").toPath()),
        java.nio.charset.StandardCharsets.UTF_8);
    java.util.regex.Matcher m = java.util.regex.Pattern
        .compile("COMMON_WORDS\\.put\\(\"([^\"]+)\",")
        .matcher(src);
    java.util.Set<String> seen = new java.util.HashSet<>();
    while (m.find())
    {
      String key = m.group(1);
      assertTrue("Duplicate COMMON_WORDS key found: " + key, seen.add(key));
    }
  }

  public static void main(String[] args)
  {
    SinhalaTransliteratorTest test = new SinhalaTransliteratorTest();
    int passed = 0;
    int failed = 0;

    java.lang.reflect.Method[] methods = SinhalaTransliteratorTest.class.getDeclaredMethods();
    for (java.lang.reflect.Method m : methods)
    {
      if (m.isAnnotationPresent(Test.class))
      {
        try
        {
          m.invoke(test);
          System.out.println("PASS: " + m.getName());
          passed++;
        }
        catch (Throwable t)
        {
          System.err.println("FAIL: " + m.getName() + " -> " + t.getCause());
          t.printStackTrace();
          failed++;
        }
      }
    }

    System.out.println("\nSummary: " + passed + " passed, " + failed + " failed.");
    if (failed > 0)
    {
      System.exit(1);
    }
  }
}
