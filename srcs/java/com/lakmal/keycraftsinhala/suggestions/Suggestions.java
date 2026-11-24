package com.lakmal.keycraftsinhala.suggestions;

import android.os.Build.VERSION;
import java.util.List;
import java.util.Locale;
import juloo.cdict.Cdict;
import com.lakmal.keycraftsinhala.dict.Dictionaries;
import com.lakmal.keycraftsinhala.Config;
import com.lakmal.keycraftsinhala.ComposeKey;
import com.lakmal.keycraftsinhala.ComposeKeyData;
import com.lakmal.keycraftsinhala.ClipboardHistoryService;
import com.lakmal.keycraftsinhala.QuickCalculator;
import com.lakmal.keycraftsinhala.SinhalaLearner;
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

  public void loadSinhalaDictionary(android.content.Context context)
  {
    Cdict[] dictionaries = Dictionaries.instance(context).load("si");
    if (dictionaries != null) sinhalaDictionary = Dictionaries.find_by_name(dictionaries, "main");
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
  public static final int MAX_COUNT = 3;
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
    if (word.charAt(0) >= 'අ' && word.charAt(0) <= 'ෆ')
    {
      clear();
      Cdict dict = "si".equals(_config.current_dictionary_name) ? _config.current_dictionary : sinhalaDictionary;
      if (dict != null)
      {
        Cdict.Result match = dict.find(word);
        if (match.found) addUniqueSuggestion(dict.word(match.index));
        if (personalWords != null && personalWords.contains(word)) addUniqueSuggestion(word);
        int[] suffixes = dict.suffixes(match, MAX_COUNT);
        for (int index : suffixes)
        {
          if (count >= 2) break;
          addUniqueSuggestion(dict.word(index));
        }
      }
      for (String next : contextWords)
        if (next.startsWith(word)) addUniqueSuggestion(next);
      if (personalWords != null)
        for (String personal : personalWords.matching(word)) addUniqueSuggestion(personal);
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
        {
          String primary = SinhalaTransliterator.transliterateWord(word, learner);
          addUniqueSuggestion(primary);
          for (String next : contextWords)
            if (next.startsWith(primary)) addUniqueSuggestion(next);
          if (personalWords != null)
            for (String next : personalWords.matching(primary)) addUniqueSuggestion(next);
        }
        for (int i = 0; i < list.size() && count < MAX_COUNT; i++)
          addUniqueSuggestion(list.get(i));
        // Keep the literal spelling reachable even for ambiguous prefixes.
        if (!containsSuggestion(word))
        {
          if (count < MAX_COUNT)
            suggestions[count++] = word;
          else
            suggestions[count - 1] = word;
        }
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
    int[] dist = (word.length() < 3 || count + 1 >= MAX_COUNT) ? NO_RESULTS :
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
