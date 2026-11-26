package com.lakmal.keycraftsinhala;

import com.lakmal.keycraftsinhala.suggestions.Suggestions;
import org.junit.Before;
import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SuggestionsTest
{
  private Config config;
  private Suggestions suggestions;
  private Suggestions.Callback callback;

  @Before
  public void setUp()
  {
    config = new Config();
    config.editor_config = new EditorConfig();
    config.editor_config.should_show_candidates_view = true;
    config.suggestions_enabled = true;
    config.quick_calculator_enabled = true;
    callback = mock(Suggestions.Callback.class);
    suggestions = new Suggestions(callback, config);
  }

  @Test
  public void emptyWordPublishesClearedCandidatesWithoutClipboardPreview()
  {
    suggestions.started();
    suggestions.currently_typed_word("2+2");
    assertEquals("= 4", suggestions.suggestions[0]);
    clearInvocations(callback);
    suggestions.currently_typed_word("");
    assertEquals(0, suggestions.count);
    assertNull(suggestions.suggestions[0]);
    verify(callback).set_suggestions(suggestions);
  }

  @Test
  public void startingWithoutClipboardStillPublishesEmptyState()
  {
    config.clipboard_suggestion_enabled = false;
    suggestions.started();
    verify(callback).set_suggestions(suggestions);
  }

  @Test
  public void ambiguousSinhalaPrefixKeepsLiteralSpellingVisible()
  {
    config.sinhala_transliteration = true;
    suggestions.started();
    suggestions.currently_typed_word("la");
    assertTrue(Arrays.asList(suggestions.suggestions).contains("la"));
    assertNotEquals("la", suggestions.suggestions[0]);
    assertEquals("la", suggestions.suggestions[Suggestions.VISIBLE_COUNT - 1]);
    assertTrue(suggestions.count > Suggestions.VISIBLE_COUNT);
    assertNotNull(suggestions.suggestions[Suggestions.VISIBLE_COUNT]);
  }

  @Test
  public void sensitiveEditorDoesNotGenerateProductivityCandidates()
  {
    config.editor_config.is_sensitive_input = true;
    suggestions.started();
    suggestions.currently_typed_word("2+2");
    assertEquals(0, suggestions.count);
  }

  @Test
  public void hostOptOutOverridesAllFeaturesAndClearsPreviousCandidates()
  {
    suggestions.started();
    suggestions.currently_typed_word("2+2");
    assertEquals(1, suggestions.count);
    config.sinhala_transliteration = true;
    config.smart_snippets_enabled = true;
    config.clipboard_suggestion_enabled = true;
    config.editor_config.should_show_candidates_view = false;
    suggestions.started();
    clearInvocations(callback);

    suggestions.currently_typed_word("2+2");
    suggestions.currently_typed_word("amma");
    suggestions.currently_typed_word("//lk");
    suggestions.set_predictions(Arrays.asList("private prediction"));

    assertEquals(0, suggestions.count);
    assertNull(suggestions.clipboard_full_text);
    assertNull(suggestions.emoji_suggestion);
    verifyNoInteractions(callback);
  }

  @Test
  public void privateModeDropsExistingCandidatesAndPredictions()
  {
    suggestions.started();
    suggestions.set_predictions(Arrays.asList("previous editor"));
    config.editor_config.is_sensitive_input = true;
    suggestions.started();
    clearInvocations(callback);
    suggestions.set_predictions(Arrays.asList("private prediction"));
    suggestions.currently_typed_word("2+2");
    assertEquals(0, suggestions.count);
    assertNull(suggestions.suggestions[0]);
    verifyNoInteractions(callback);
  }

  @Test
  public void wiredSinhalaLearnerSurfacesLearnedCorrectionForUnknownWord()
  {
    config.sinhala_transliteration = true;
    SinhalaLearner learner = new SinhalaLearner((android.content.SharedPreferences) null);
    learner.recordCorrection("keycraftword", "සිංහලවචනය");
    suggestions.setSinhalaLearner(learner);
    suggestions.started();

    suggestions.currently_typed_word("keycraftword");

    assertTrue("Expected learned correction in suggestions",
        Arrays.asList(suggestions.suggestions).contains("සිංහලවචනය"));
  }

  @Test
  public void sensitiveInputSuppressesSinhalaLearnerSuggestions()
  {
    config.sinhala_transliteration = true;
    SinhalaLearner learner = new SinhalaLearner((android.content.SharedPreferences) null);
    learner.recordCorrection("keycraftword", "සිංහලවචනය");
    suggestions.setSinhalaLearner(learner);
    config.editor_config.is_sensitive_input = true;
    suggestions.started();

    suggestions.currently_typed_word("keycraftword");

    assertEquals(0, suggestions.count);
    assertFalse(Arrays.asList(suggestions.suggestions).contains("සිංහලවචනය"));
  }
}
