package com.lakmal.keycraftsinhala;

import android.content.Context;
import android.content.SharedPreferences;
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
    if (prefs != null) prefs.edit().putInt(word, next).apply();
    return true;
  }

  synchronized void remove(String word)
  {
    words.remove(word);
    if (prefs != null) prefs.edit().remove(word).apply();
  }

  synchronized void clear()
  {
    words.clear();
    if (prefs != null) prefs.edit().clear().apply();
  }

  public synchronized boolean contains(String word) { return words.containsKey(word); }

  public synchronized List<String> matching(String prefix)
  {
    List<String> result = new ArrayList<>();
    for (String word : words.keySet())
      if (word.startsWith(prefix)) result.add(word);
    java.util.Collections.sort(result, (a,b) -> {
      if (a.equals(b)) return 0;
      if (a.equals(prefix)) return -1;
      if (b.equals(prefix)) return 1;
      int byCount = Integer.compare(words.get(b), words.get(a));
      return byCount != 0 ? byCount : a.compareTo(b);
    });
    return result;
  }
}
