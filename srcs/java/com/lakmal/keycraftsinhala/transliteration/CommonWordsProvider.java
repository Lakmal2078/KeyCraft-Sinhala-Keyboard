package com.lakmal.keycraftsinhala.transliteration;

import java.util.Map;

/** Adapter over the existing in-memory common-word dictionary. */
public final class CommonWordsProvider implements DictionaryProvider
{
  private final Map<String, String> words;

  public CommonWordsProvider(Map<String, String> words)
  {
    if (words == null) throw new IllegalArgumentException("Dictionary map is required.");
    this.words = words;
  }

  @Override public String lookup(String key) { return words.get(key); }
  @Override public boolean contains(String key) { return words.containsKey(key); }
}
