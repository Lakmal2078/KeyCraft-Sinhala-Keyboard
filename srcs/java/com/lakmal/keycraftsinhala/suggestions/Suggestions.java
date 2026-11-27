package com.lakmal.keycraftsinhala.suggestions;

import android.os.Build.VERSION;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import juloo.cdict.Cdict;
import com.lakmal.keycraftsinhala.dict.Dictionaries;
import com.lakmal.keycraftsinhala.Config;
import com.lakmal.keycraftsinhala.ComposeKey;
import com.lakmal.keycraftsinhala.ComposeKeyData;
import com.lakmal.keycraftsinhala.ClipboardHistoryService;
import com.lakmal.keycraftsinhala.QuickCalculator;
import com.lakmal.keycraftsinhala.Logs;
import com.lakmal.keycraftsinhala.SinhalaLearner;
import com.lakmal.keycraftsinhala.SinhalaText;
import com.lakmal.keycraftsinhala.SmartSnippets;
import com.lakmal.keycraftsinhala.SinhalaTransliterator;

/** Keep track of the word being typed and provide suggestions for
    [CandidatesView]. */
public final class Suggestions
{
  public com.lakmal.keycraftsinhala.PersonalWords personalWords;
  private List<String> contextWords = java.util.Collections.emptyList();
  Callback _callback;
  Config _config;
  boolean _enabled;
  private Cdict sinhalaDictionary;
  private Map<String, String> reviewedAliases = Collections.emptyMap();

  public void loadSinhalaDictionary(android.content.Context context)
  {
    Cdict[] dictionaries = Dictionaries.instance(context).load("si");
    if (dictionaries != null) sinhalaDictionary = Dictionaries.find_by_name(dictionaries, "main");
    try
    {
      loadReviewedAliases(context.getAssets().open("dict/si_aliases.tsv"));
    }
    catch (IOException error)
    {
      reviewedAliases = Collections.emptyMap();
      Logs.warn("Could not load reviewed Sinhala aliases");
    }
  }

  void loadReviewedAliases(InputStream source) throws IOException
  {
    Map<String, String> aliases = new HashMap<>();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(source, StandardCharsets.UTF_8)))
    {
      String line;
      while ((line = reader.readLine()) != null)
      {
        String[] pair = line.split("\\t", -1);
        if (pair.length != 2 || pair[0].isEmpty() || !SinhalaText.isSinhalaWord(pair[1])
            || aliases.putIfAbsent(pair[0], pair[1]) != null)
          throw new IOException("Invalid reviewed Sinhala alias asset");
      }
    }
    reviewedAliases = aliases;
  }

  private SinhalaLearner _sinhala_learner = null;

  /** Current suggestions. The best suggestion is at index [0]. */
  public String[] suggestions = new String[MAX_COUNT];
  /** Number of suggestions at the beginning of the [suggestions] array that
      are not [null]. */
  public int count = 0;
  public String emoji_suggestion = null;
  public String clipboard_full_text = null;
  /** Clipboard text whose preview was dismissed by the user after pasting. */
  private String dismissed_clipboard_text = null;
  /** Number of suggestions in [suggestions]. */
  public static final int VISIBLE_COUNT = 3;
  public static final int MAX_COUNT = 12;
  /** Whether the current suggestions represent predicted next words rather than active input completions. */
  public boolean is_prediction = false;

  public Suggestions(Callback c, Config conf)
  {
    _callback = c;
    _config = conf;
  }

  public void setSinhalaLearner(SinhalaLearner learner)
  {
    _sinhala_learner = learner;
  }

  public void started()
  {
    contextWords = java.util.Collections.emptyList();
    _enabled = _config.suggestions_enabled
      && !_config.editor_config.is_sensitive_input
      && _config.editor_config.should_show_candidates_view;
    clear();
    if (_enabled)
      show_clipboard_suggestion();
    else
      _callback.set_suggestions(this);
  }

  void show_clipboard_suggestion()
  {
    // Older platforms cannot expose the clipboard sensitivity metadata.
    if (_enabled && _config.clipboard_suggestion_enabled
        && !_config.editor_config.is_no_suggestions && VERSION.SDK_INT >= 24)
    {
      String clip = ClipboardHistoryService.get_current_clip();
      if (should_show_clipboard_suggestion(clip))
      {
        clipboard_full_text = clip;
        String preview = clip.trim().replace('\n', ' ');
        if (preview.length() > 20)
          preview = preview.substring(0, 18) + "...";
        suggestions[0] = "📋 " + preview;
        count = 1;
      }
    }
    _callback.set_suggestions(this);
  }

  public void set_predictions(List<String> list)
  {
    if (!_enabled || list == null || list.isEmpty())
      return;
    clear();
    contextWords = new java.util.ArrayList<>(list);
    is_prediction = true;
    for (int i = 0; i < list.size() && count < MAX_COUNT; i++)
      addUniqueSuggestion(list.get(i));
    _callback.set_suggestions(this);
  }

  public void currently_typed_word(String word)
  {
    if (!_enabled)
      return;
    if (word == null || word.isEmpty())
    {
      clear();
      show_clipboard_suggestion();
      return;
    }
    if (SinhalaText.isSinhalaWord(word))
    {
      querySinhala(word);
      _callback.set_suggestions(this);
      return;
    }
    if (_config.quick_calculator_enabled)
    {
      String mathRes = QuickCalculator.evaluate(word);
      if (mathRes != null)
      {
        clear();
        suggestions[0] = mathRes;
        count = 1;
        _callback.set_suggestions(this);
        return;
      }
    }
    if (_config.smart_snippets_enabled)
    {
      String snippet = SmartSnippets.getSnippet(word);
      if (snippet != null)
      {
        clear();
        suggestions[0] = snippet;
        count = 1;
        _callback.set_suggestions(this);
        return;
      }
    }
    if (_config.sinhala_transliteration && _config.editor_config.isProseField() && is_ascii_letters(word))
    {
      SinhalaLearner learner = (_config.editor_config != null
          && _config.editor_config.is_sensitive_input) ? null : _sinhala_learner;
      java.util.List<String> list = SinhalaTransliterator.getSuggestions(word, learner);
      if (!list.isEmpty())
      {
        clear();
        if (_config.literal_singlish)
          addVisibleSinglishCandidates(word, list, learner);
        for (String candidate : list)
        {
          if (count >= MAX_COUNT)
            break;
          addUniqueSuggestion(normalized(candidate));
        }
        keepLiteralSpellingVisible(word);
        emoji_suggestion = query_emoji(word);
        _callback.set_suggestions(this);
        return;
      }
    }
    // Math punctuation belongs to the calculator. Dictionary distance search
    // could otherwise suggest deleting punctuation from ordinary prose.
    if (word.length() < 2 || _config.current_dictionary == null
        || word.indexOf('(') >= 0 || word.indexOf(')') >= 0
        || word.indexOf(',') >= 0)
      clear();
    else
      query_suggestions(word);
    _callback.set_suggestions(this);
  }

  private Cdict getSinhalaDictionary()
  {
    if ("si".equals(_config.current_dictionary_name) && _config.current_dictionary != null)
      return _config.current_dictionary;
    return sinhalaDictionary;
  }

  private void querySinhala(String word)
  {
    clear();
    String prefix = normalized(word);
    Cdict dict = getSinhalaDictionary();
    Cdict.Result match = dict == null ? null : dict.find(prefix);
    List<String> personal = personalWords == null ? Collections.emptyList()
        : personalWords.matching(prefix);
    if (match != null && match.found)
      addUniqueSuggestion(dict.word(match.index));
    if (!personal.isEmpty() && normalized(personal.get(0)).equals(prefix))
      addUniqueSuggestion(prefix);

    // Keep ZWNJ and all other spellings distinct. Offer a joined or unjoined
    // ra/ya spelling only when the alternate exists in the dictionary.
    String variant = prefix.contains("්‍ර") || prefix.contains("්‍ය")
        ? prefix.replace("්‍ර", "්ර").replace("්‍ය", "්ය")
        : prefix.replace("්ර", "්‍ර").replace("්ය", "්‍ය");
    Cdict.Result variantMatch = dict == null || variant.equals(prefix) ? null : dict.find(variant);
    if (variantMatch != null && variantMatch.found && count == 0)
      addUniqueSuggestion(dict.word(variantMatch.index));

    addMatchingSuggestions(personal, prefix, count + 1);
    addMatchingSuggestions(contextWords, prefix, count + 1);
    addRankedDirectCompletions(dict, match, prefix, MAX_COUNT);
    if (count < VISIBLE_COUNT)
      addRankedDirectCompletions(dict, variantMatch, variant, VISIBLE_COUNT);
    addMatchingSuggestions(personal, prefix, MAX_COUNT);
    addMatchingSuggestions(contextWords, prefix, MAX_COUNT);
  }

  private void addVisibleSinglishCandidates(String literal, List<String> candidates, SinhalaLearner learner)
  {
    Cdict dict = getSinhalaDictionary();
    String reviewed = reviewedAliases.get(literal.toLowerCase(Locale.ROOT));
    if (reviewed != null && dict != null && dict.find(reviewed).found)
      addUniqueSuggestion(reviewed);

    String primary = normalized(SinhalaTransliterator.transliterateWord(literal, learner));
    boolean sinhalaPrimary = SinhalaText.isSinhalaWord(primary);
    if (sinhalaPrimary && (count == 0 || (dict != null && dict.find(primary).found)))
      addUniqueSuggestion(primary);

    // Keep one visible place for the literal spelling. Exact Sinhala options
    // get the remaining places before completions and contextual predictions.
    int sinhalaSlots = VISIBLE_COUNT - 1;
    if (dict != null)
      for (String candidate : candidates)
      {
        if (count >= sinhalaSlots)
          break;
        String word = normalized(candidate);
        if (SinhalaText.isSinhalaWord(word) && dict.find(word).found)
          addUniqueSuggestion(word);
      }
    if (sinhalaPrimary && count < sinhalaSlots)
    {
      if (personalWords != null)
        addMatchingSuggestions(personalWords.matching(primary), primary, sinhalaSlots);
      addMatchingSuggestions(contextWords, primary, sinhalaSlots);
      if (dict != null && count < sinhalaSlots)
        addSinhalaSuffixes(dict, dict.find(primary), primary, sinhalaSlots);
    }
  }

  private void addMatchingSuggestions(List<String> candidates, String prefix, int limit)
  {
    for (String candidate : candidates)
    {
      if (count >= limit)
        return;
      String word = normalized(candidate);
      if (word.startsWith(prefix))
        addUniqueSuggestion(word);
    }
  }

  private void addRankedDirectCompletions(Cdict dict, Cdict.Result match, String prefix, int limit)
  {
    if (match == null || count >= limit)
      return;
    int[] completions = dict.suffixes(match, limit - count, candidate -> {
      // Filter exact and alias results before applying the completion limit.
      if (!candidate.startsWith(prefix) || candidate.equals(prefix) || containsSuggestion(candidate))
        return -1;
      int letters = 0;
      for (int i = prefix.length(); i < candidate.length(); i++)
        if (Character.isLetter(candidate.charAt(i))) letters++;
      return letters;
    });
    for (int index : completions)
      addUniqueSuggestion(dict.word(index));
  }

  private void addSinhalaSuffixes(Cdict dict, Cdict.Result match, String prefix, int limit)
  {
    if (match == null || count >= limit)
      return;
    for (int index : dict.suffixes(match, MAX_COUNT))
    {
      String word = dict.word(index);
      if (word.startsWith(prefix))
        addUniqueSuggestion(word);
      if (count >= limit)
        return;
    }
  }

  private static String normalized(String word)
  {
    return Normalizer.normalize(word, Normalizer.Form.NFC);
  }

  private void keepLiteralSpellingVisible(String word)
  {
    if (!containsSuggestion(word))
    {
      if (count == MAX_COUNT)
        suggestions[count - 1] = word;
      else
        suggestions[count++] = word;
    }
    int index = 0;
    while (!word.equals(suggestions[index]))
      index++;
    int lastVisible = VISIBLE_COUNT - 1;
    if (index > lastVisible)
    {
      System.arraycopy(suggestions, lastVisible, suggestions, lastVisible + 1, index - lastVisible);
      suggestions[lastVisible] = word;
    }
  }

  private void addUniqueSuggestion(String candidate)
  {
    if (candidate == null || candidate.isEmpty() || count >= MAX_COUNT)
      return;
    if (!containsSuggestion(candidate))
      suggestions[count++] = candidate;
  }

  private boolean containsSuggestion(String word)
  {
    return containsSuggestion(suggestions, count, word);
  }

  private static boolean containsSuggestion(String[] arr, int len, String word)
  {
    if (word == null)
      return false;
    for (int i = 0; i < len; i++)
    {
      if (word.equals(arr[i]))
        return true;
    }
    return false;
  }

  private static boolean is_ascii_letters(String s)
  {
    if (s == null || s.isEmpty())
      return false;
    for (int i = 0; i < s.length(); i++)
    {
      char c = s.charAt(i);
      if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')))
        return false;
    }
    return true;
  }

  /** Suppress the current clipboard preview after the user pastes it. */
  public void dismiss_clipboard_suggestion()
  {
    dismissed_clipboard_text = clipboard_full_text != null
        ? clipboard_full_text : ClipboardHistoryService.get_current_clip();
  }

  /** Returns false for empty clips and for the clip whose preview was dismissed. */
  boolean should_show_clipboard_suggestion(String clip)
  {
    if (clip == null || clip.trim().isEmpty())
      return false;
    if (clip.equals(dismissed_clipboard_text))
      return false;
    // A different clipboard value is new content, so it may be previewed.
    dismissed_clipboard_text = null;
    return true;
  }

  public void clear()
  {
    count = 0;
    is_prediction = false;
    for (int i = 0; i < MAX_COUNT; i++)
      suggestions[i] = null;
    emoji_suggestion = null;
    clipboard_full_text = null;
  }

  int query_suggestions(String word)
  {
    clear();
    Cdict dict = _config.current_dictionary;
    boolean first_char_upper = Character.isUpperCase(word.charAt(0));
    word = apply_substitutions(word);
    Cdict.Result r = dict.find(word);
    if (r.found)
      addUniqueSuggestion(dict.word(r.index));
    int[] suffixes = dict.suffixes(r, MAX_COUNT * 2);
    // Disable distance search for small words
    int[] dist = word.length() < 3 ? NO_RESULTS :
      dict.distance(word, 1, MAX_COUNT * 2);
    int limit = Math.max(suffixes.length, dist.length);
    for (int j = 0; j < limit && count < MAX_COUNT; j++)
    {
      if (suffixes.length > j)
        addUniqueSuggestion(dict.word(suffixes[j]));
      if (dist.length > j)
        addUniqueSuggestion(dict.word(dist[j]));
    }
    if (first_char_upper && count > 0)
    {
      capitalize_results(suggestions, count);
      int uniqueCount = 0;
      for (int i = 0; i < count; i++)
      {
        String s = suggestions[i];
        if (s != null && !containsSuggestion(suggestions, uniqueCount, s))
          suggestions[uniqueCount++] = s;
      }
      for (int i = uniqueCount; i < count; i++)
        suggestions[i] = null;
      count = uniqueCount;
    }
    emoji_suggestion = query_emoji(word); // word with substitutions applied
    return count;
  }

  static void capitalize_results(String[] s, int count)
  {
    for (int i = 0; i < count; i++)
      s[i] = s[i].substring(0, 1).toUpperCase(Locale.getDefault()) + s[i].substring(1);
  }

  String query_emoji(String word)
  {
    Cdict dict = _config.emoji_dictionary;
    // Disable emoji suggestion for short words
    if (dict == null || word.length() < 3)
      return null;
    Cdict.Result r = dict.find(word);
    if (r.found)
      return dict.word(r.index);
    int[] s = dict.suffixes(r, 1);
    if (s.length > 0)
      return dict.word(s[0]);
    return null;
  }

  /** Apply the same substitutions that were used when building the
      dictionaries to find word aliases. This catches missing diacritics for
      example. */
  String apply_substitutions(String w)
  {
    StringBuilder b = new StringBuilder(w);
    int len = w.length();
    for (int i = 0; i < len; i++)
    {
      char r =
        ComposeKey.transform_char(ComposeKeyData.substitutions, b.charAt(i));
      if (r != 0) b.setCharAt(i, r);
    }
    return b.toString();
  }

  static final int[] NO_RESULTS = new int[0];

  public static interface Callback
  {
    public void set_suggestions(Suggestions suggestions);
  }
}
