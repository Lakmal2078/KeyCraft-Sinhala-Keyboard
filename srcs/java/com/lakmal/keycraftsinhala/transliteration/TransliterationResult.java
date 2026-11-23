package com.lakmal.keycraftsinhala.transliteration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable analysis result for a transliteration input. */
public final class TransliterationResult
{
  public final String input;
  public final List<TransliterationCandidate> candidates;

  public TransliterationResult(String input, List<TransliterationCandidate> candidates)
  {
    this.input = input == null ? "" : input;
    this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
  }

  public String getBestCandidate()
  {
    return candidates.isEmpty() ? "" : candidates.get(0).text;
  }
}
