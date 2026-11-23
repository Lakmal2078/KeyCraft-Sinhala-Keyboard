package com.lakmal.keycraftsinhala.transliteration;

/** Compatibility adapter retaining the existing dictionary storage and ordering. */
public final class ExistingDictionaryAdapter implements DictionaryProvider
{
  private final DictionaryProvider delegate;

  public ExistingDictionaryAdapter(DictionaryProvider delegate)
  {
    if (delegate == null) throw new IllegalArgumentException("Dictionary provider is required.");
    this.delegate = delegate;
  }

  @Override public String lookup(String key) { return delegate.lookup(key); }
  @Override public boolean contains(String key) { return delegate.contains(key); }
}
