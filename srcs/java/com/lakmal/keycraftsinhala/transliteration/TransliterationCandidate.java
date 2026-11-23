package com.lakmal.keycraftsinhala.transliteration;

/** One internally ranked Sinhala transliteration candidate; score is a heuristic rank, not a probability. */
public final class TransliterationCandidate
{
  public final String text;
  public final int score;
  public final String ruleId;

  public TransliterationCandidate(String text, int score, String ruleId)
  {
    if (text == null || ruleId == null)
      throw new IllegalArgumentException("Candidate text and rule ID are required.");
    this.text = text;
    this.score = score;
    this.ruleId = ruleId;
  }
}
