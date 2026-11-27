package com.lakmal.keycraftsinhala;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Small, shared on-device frequency table used by Settings and the IME. */
public final class PersonalWords
{
  private static PersonalWords instance;
  private final SharedPreferences prefs;
  private final Map<String, Integer> words = new HashMap<>();
  // Recency is scoped to this process. Persisted frequency remains the primary signal.
  private final Map<String, Long> recentUse = new HashMap<>();
  private long useSequence;
  private static final int LIMIT = 256;

  static synchronized PersonalWords open(Context context)
  {
    if (instance == null)
      {
      SharedPreferences prefs = DirectBootAwarePreferences.get_learned_prefs(context, "personal_sinhala_words");
      if (prefs == null) return new PersonalWords(null);
      instance = new PersonalWords(prefs);
    }
    return instance;
  }

  PersonalWords(SharedPreferences prefs)
  {
    this.prefs = prefs;
    if (prefs != null)
      for (Map.Entry<String, ?> entry : prefs.getAll().entrySet())
        if (entry.getValue() instanceof Integer && valid(entry.getKey()))
          words.put(entry.getKey(), (Integer)entry.getValue());
  }

  private static boolean valid(String word)
  {
    if (word == null || word.isEmpty() || word.length() > SinhalaLearner.MAX_WORD_LENGTH
        || !Character.isLetter(word.charAt(0))) return false;
    for (int i = 0; i < word.length(); i++)
    {
      char c = word.charAt(i);
      if (!(c >= 'අ' && c <= 'ෆ' && Character.isLetter(c)) && !SinhalaText.mark(c)
          && c != 0x200C && c != 0x200D) return false;
    }
    return true;
  }

  synchronized boolean learn(String word)
  {
    if (!valid(word)) return false;
    Integer count = words.get(word);
    if (count == null && words.size() >= LIMIT)
    {
      List<String> ranked = matching("");
      remove(ranked.get(ranked.size()-1));
    }
    int next = count == null ? 1 : Math.min(1000000, count + 1);
    words.put(word, next);
    recentUse.put(word, ++useSequence);
    if (prefs != null) prefs.edit().putInt(word, next).apply();
    return true;
  }

  synchronized void remove(String word)
  {
    words.remove(word);
    recentUse.remove(word);
    if (prefs != null) prefs.edit().remove(word).apply();
  }

  synchronized void clear()
  {
    words.clear();
    recentUse.clear();
    if (prefs != null) prefs.edit().clear().apply();
  }

  public synchronized boolean contains(String word) { return words.containsKey(word); }

  public synchronized List<String> matching(String prefix)
  {
    String key = Normalizer.normalize(prefix, Normalizer.Form.NFC);
    List<String> result = new ArrayList<>();
    for (String word : words.keySet())
      if (Normalizer.normalize(word, Normalizer.Form.NFC).startsWith(key)) result.add(word);
    result.sort((a, b) -> {
      boolean exactA = Normalizer.normalize(a, Normalizer.Form.NFC).equals(key);
      boolean exactB = Normalizer.normalize(b, Normalizer.Form.NFC).equals(key);
      if (exactA != exactB) return exactA ? -1 : 1;
      int byCount = Integer.compare(words.get(b), words.get(a));
      if (byCount != 0) return byCount;
      int byRecency = Long.compare(recentUse.getOrDefault(b, 0L), recentUse.getOrDefault(a, 0L));
      return byRecency != 0 ? byRecency : a.compareTo(b);
    });
    return result;
  }
}
