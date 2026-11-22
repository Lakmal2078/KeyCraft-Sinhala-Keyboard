package com.lakmal.keycraftsinhala.suggestions;

import android.annotation.SuppressLint;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;

public final class SuggestionPolicy
{
  private SuggestionPolicy() {}

  // This inlined integer mask is safe on older Android versions too.
  @SuppressLint("InlinedApi")
  public static boolean is_sensitive_input(int inputType, int imeOptions)
  {
    return (imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
        || is_sensitive_input(inputType);
  }

  public static boolean is_sensitive_input(int inputType)
  {
    return is_password_input(inputType);
  }

  /** Whether the host requested disabling automatic dictionary spellcheck/autocorrect
      (e.g. AI chat prompts, search bars, or autocomplete=off web inputs). Unlike
      [is_sensitive_input], this is not a privacy/incognito restriction. */
  public static boolean is_no_suggestions(int inputType)
  {
    int inputClass = inputType & InputType.TYPE_MASK_CLASS;
    int flags = inputType & InputType.TYPE_MASK_FLAGS;
    return inputClass == InputType.TYPE_CLASS_TEXT
        && (flags & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0;
  }

  /** Whether the input is a password field where transliteration should be
      disabled. Unlike [is_sensitive_input], this does NOT include incognito
      mode (IME_FLAG_NO_PERSONALIZED_LEARNING) or no-suggestions flags, which
      should only restrict personalized/autocorrect behavior without blocking transliteration. */
  public static boolean is_password_input(int inputType)
  {
    int inputClass = inputType & InputType.TYPE_MASK_CLASS;
    int variation = inputType & InputType.TYPE_MASK_VARIATION;
    if (inputClass == InputType.TYPE_CLASS_TEXT)
    {
      switch (variation)
      {
        case InputType.TYPE_TEXT_VARIATION_PASSWORD:
        case InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD:
        case InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD:
          return true;
        default:
          return false;
      }
    }
    return inputClass == InputType.TYPE_CLASS_NUMBER
      && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD;
  }
}
