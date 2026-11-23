package com.lakmal.keycraftsinhala.transliteration;

/** Immutable mapping from a Singlish sequence to its Sinhala output. */
public final class PhoneticRule
{
  public final String input;
  public final String output;
  public final int priority;

  public PhoneticRule(String input, String output, int priority)
  {
    if (input == null || input.isEmpty() || output == null)
      throw new IllegalArgumentException("A phonetic rule requires a non-empty input and an output.");
    this.input = input;
    this.output = output;
    this.priority = priority;
  }
}
