package com.lakmal.keycraftsinhala.transliteration;

public interface DictionaryProvider
{
  String lookup(String key);
  boolean contains(String key);
}
