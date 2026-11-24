package com.lakmal.keycraftsinhala;

import android.content.res.Resources;
import android.os.Handler;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import com.lakmal.keycraftsinhala.suggestions.CandidatesView;
import com.lakmal.keycraftsinhala.suggestions.Suggestions;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class KeyboardIntegrationTest
{
  private Config config;
  private InputConnection connection;
  private KeyEventHandler handler;
  private Suggestions suggestions;

  @Before
  public void setUp()
  {
    config = new Config();
    config.literal_singlish = false; // Exercise legacy live composition unless a test opts into literal preview.
    config.editor_config = new EditorConfig();
    config.editor_config.initial_text_before_cursor = "";
    config.suggestions_enabled = false;
    config.sinhala_transliteration = true;

    connection = mock(InputConnection.class);
    KeyEventHandler.IReceiver receiver = mock(KeyEventHandler.IReceiver.class);
    when(receiver.getCurrentInputConnection()).thenReturn(connection);
    when(receiver.getHandler()).thenReturn(mock(Handler.class));

    suggestions = new Suggestions(receiver, config);
    handler = new KeyEventHandler(config, receiver, suggestions);
    handler.started();
  }

  @Test
  public void keyEventsComposeAndCommitSinhalaThroughInputConnection()
  {
    handler.key_up(KeyValue.makeCharKey('k'), Pointers.Modifiers.EMPTY);
    verify(connection).setComposingText("ක්", 1);

    handler.key_up(KeyValue.makeCharKey('a'), Pointers.Modifiers.EMPTY);
    verify(connection).setComposingText("ක", 1);
    verify(connection, times(2)).beginBatchEdit();
    verify(connection, times(2)).endBatchEdit();

    handler.key_up(KeyValue.getKeyByName("space"), Pointers.Modifiers.EMPTY);
    verify(connection).commitText("ක ", 1);
    verify(connection, times(3)).beginBatchEdit();
    verify(connection, times(3)).endBatchEdit();
  }

  @Test
  public void selectedSinhalaSuggestionCommitsComposingSpanAtomically()
  {
    handler.key_up(KeyValue.makeCharKey('k'), Pointers.Modifiers.EMPTY);
    handler.key_up(KeyValue.makeCharKey('a'), Pointers.Modifiers.EMPTY);

    handler.suggestion_entered("කොහොමද", false);

    verify(connection).commitText("කොහොමද", 1);
    verify(connection, times(3)).beginBatchEdit();
    verify(connection, times(3)).endBatchEdit();
    assertEquals(0, handler._sinhala_buffer.length());
  }

  @Test
  public void multiCharacterSinhalaWordComposesAndCommitsAsOneWord()
  {
    for (char c : "amma".toCharArray())
      handler.key_up(KeyValue.makeCharKey(c), Pointers.Modifiers.EMPTY);

    verify(connection).setComposingText("අම්මා", 1);
    handler.key_up(KeyValue.getKeyByName("space"), Pointers.Modifiers.EMPTY);

    verify(connection).commitText("අම්මා ", 1);
    assertEquals(0, handler._sinhala_buffer.length());
  }

  @Test
  public void aarambaComposesAndCommitsCorrectDictionaryWord()
  {
    for (char c : "Aaramba".toCharArray())
      handler.key_up(KeyValue.makeCharKey(c), Pointers.Modifiers.EMPTY);

    verify(connection).setComposingText("\u0D86\u0DBB\u0DB8\u0DCA\u0DB7", 1);
    handler.key_up(KeyValue.getKeyByName("space"), Pointers.Modifiers.EMPTY);
    verify(connection).commitText("\u0D86\u0DBB\u0DB8\u0DCA\u0DB7 ", 1);
    assertEquals(0, handler._sinhala_buffer.length());
  }

  @Test
  public void backspaceUpdatesAndClearsActiveSinhalaComposition()
  {
    handler.key_up(KeyValue.makeCharKey('k'), Pointers.Modifiers.EMPTY);
    handler.key_up(KeyValue.makeCharKey('a'), Pointers.Modifiers.EMPTY);
    handler.key_up(KeyValue.getKeyByName("backspace"), Pointers.Modifiers.EMPTY);

    verify(connection).setComposingText("ක්", 1);
    assertEquals(0, handler._sinhala_buffer.length());

    handler.key_up(KeyValue.getKeyByName("backspace"), Pointers.Modifiers.EMPTY);
    verify(connection).setComposingText("", 1);
    verify(connection).finishComposingText();
    assertEquals(0, handler._sinhala_buffer.length());
  }

  @Test
  public void SinhalaCandidatesRemainSelectableDuringComposition()
  {
    config.suggestions_enabled = true;
    config.editor_config.should_show_candidates_view = true;
    handler.started();
    handler.key_up(KeyValue.makeCharKey('k'), Pointers.Modifiers.EMPTY);
    handler.key_up(KeyValue.makeCharKey('a'), Pointers.Modifiers.EMPTY);

    assertTrue("Sinhala candidates should be available while composing", suggestions.count > 1);
    assertEquals("කළා", suggestions.suggestions[0]);

    handler.suggestion_entered(suggestions.suggestions[0], false);
    verify(connection).commitText("කළා", 1);
    assertEquals(0, handler._sinhala_buffer.length());
  }

  @Test
  public void passwordEditorCommitsLiteralInputWithoutComposing()
  {
    config.editor_config.is_sensitive_input = true;
    config.editor_config.is_password_input = true;
    handler.started();
    handler.key_up(KeyValue.makeCharKey('k'), Pointers.Modifiers.EMPTY);
    handler.key_up(KeyValue.makeCharKey('a'), Pointers.Modifiers.EMPTY);

    verify(connection, never()).setComposingText(any(CharSequence.class), anyInt());
    verify(connection).commitText("k", 1);
    verify(connection).commitText("a", 1);
    assertEquals(0, handler._sinhala_buffer.length());
  }

  @Test
  public void startedFinishesActiveCompositionBeforeResettingItsState()
  {
    handler.key_up(KeyValue.makeCharKey('k'), Pointers.Modifiers.EMPTY);
    handler.key_up(KeyValue.makeCharKey('a'), Pointers.Modifiers.EMPTY);

    handler.started();

    verify(connection).finishComposingText();
    verify(connection, never()).commitText(anyString(), anyInt());
    assertEquals(0, handler._sinhala_buffer.length());
  }

  @Test
  public void incognitoSensitiveEditorAllowsSinhalaTransliterationWithoutReadingContext()
  {
    // Incognito browsing sets is_sensitive_input but NOT is_password_input
    config.editor_config.is_sensitive_input = true;
    config.editor_config.is_password_input = false;
    handler.started();
    handler.key_up(KeyValue.makeCharKey('k'), Pointers.Modifiers.EMPTY);
    verify(connection).setComposingText("ක්", 1);

    handler.key_up(KeyValue.makeCharKey('a'), Pointers.Modifiers.EMPTY);
    verify(connection).setComposingText("ක", 1);

    handler.key_up(KeyValue.getKeyByName("space"), Pointers.Modifiers.EMPTY);
    verify(connection).commitText("ක ", 1);
    verify(connection, never()).getTextBeforeCursor(anyInt(), anyInt());
    assertEquals(0, handler._sinhala_buffer.length());
  }

  @Test
  public void chromeWebEditorTypingSamaharaProducesCleanTextWithoutDuplication()
  {
    EditorInfo webInfo = new EditorInfo();
    webInfo.packageName = "com.android.chrome";
    webInfo.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT;
    config.editor_config.refresh(webInfo, mock(Resources.class));
    config.suggestions_enabled = true;

    final StringBuilder doc = new StringBuilder();
    final int[] cursor = new int[] { 0 };
    InputConnection docConn = mock(InputConnection.class);
    when(docConn.beginBatchEdit()).thenReturn(true);
    when(docConn.endBatchEdit()).thenReturn(true);
    when(docConn.deleteSurroundingText(anyInt(), anyInt())).thenAnswer(inv -> {
      int before = inv.getArgument(0);
      int start = Math.max(0, cursor[0] - before);
      doc.delete(start, cursor[0]);
      cursor[0] = start;
      return true;
    });
    when(docConn.commitText(any(CharSequence.class), anyInt())).thenAnswer(inv -> {
      String s = inv.getArgument(0).toString();
      doc.insert(cursor[0], s);
      cursor[0] += s.length();
      return true;
    });

    KeyEventHandler.IReceiver docRecv = mock(KeyEventHandler.IReceiver.class);
    when(docRecv.getCurrentInputConnection()).thenReturn(docConn);
    when(docRecv.getHandler()).thenReturn(mock(Handler.class));
    Suggestions webSuggestions = new Suggestions(docRecv, config);
    KeyEventHandler webHandler = new KeyEventHandler(config, docRecv, webSuggestions);
    webHandler.started();

    assertTrue(config.editor_config.use_commit_replace_composing);
    assertTrue(config.editor_config.should_show_candidates_view);

    for (char c : "Samahara".toCharArray())
    {
      int prevCursor = cursor[0];
      webHandler.key_up(KeyValue.makeCharKey(c), Pointers.Modifiers.EMPTY);
      webHandler.selection_updated(prevCursor, cursor[0], cursor[0], -1, -1);
    }

    assertEquals("සමහර", doc.toString());
    assertTrue("Suggestions should remain populated for Chrome PWA", webSuggestions.count >= 2);
    verify(docConn, never()).setComposingText(any(CharSequence.class), anyInt());

    // Tapping literal English candidate replaces committed Sinhala word cleanly
    webHandler.suggestion_entered("Samahara", true);
    assertEquals("Samahara ", doc.toString());
    assertEquals(0, webHandler._sinhala_buffer.length());
  }

  @Test
  public void chromeWebEditorBackspaceClearsAndExternalCursorMoveFinishesComposing()
  {
    EditorInfo webInfo = new EditorInfo();
    webInfo.packageName = "com.android.chrome";
    webInfo.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT;
    config.editor_config.refresh(webInfo, mock(Resources.class));

    final StringBuilder doc = new StringBuilder();
    final int[] cursor = new int[] { 0 };
    InputConnection docConn = mock(InputConnection.class);
    when(docConn.deleteSurroundingText(anyInt(), anyInt())).thenAnswer(inv -> {
      int before = inv.getArgument(0);
      int start = Math.max(0, cursor[0] - before);
      doc.delete(start, cursor[0]);
      cursor[0] = start;
      return true;
    });
    when(docConn.commitText(any(CharSequence.class), anyInt())).thenAnswer(inv -> {
      String s = inv.getArgument(0).toString();
      doc.insert(cursor[0], s);
      cursor[0] += s.length();
      return true;
    });

    KeyEventHandler.IReceiver docRecv = mock(KeyEventHandler.IReceiver.class);
    when(docRecv.getCurrentInputConnection()).thenReturn(docConn);
    when(docRecv.getHandler()).thenReturn(mock(Handler.class));
    Suggestions webSuggestions = new Suggestions(docRecv, config);
    KeyEventHandler webHandler = new KeyEventHandler(config, docRecv, webSuggestions);
    webHandler.started();

    webHandler.key_up(KeyValue.makeCharKey('S'), Pointers.Modifiers.EMPTY);
    webHandler.selection_updated(0, cursor[0], cursor[0], -1, -1);
    webHandler.key_up(KeyValue.makeCharKey('a'), Pointers.Modifiers.EMPTY);
    webHandler.selection_updated(2, cursor[0], cursor[0], -1, -1);
    assertEquals("ස", doc.toString());

    // Backspace removes the displayed independent consonant, not the Latin suffix.
    webHandler.key_up(KeyValue.getKeyByName("backspace"), Pointers.Modifiers.EMPTY);
    webHandler.selection_updated(1, cursor[0], cursor[0], -1, -1);
    assertEquals("", doc.toString());
    assertEquals("", doc.toString());
    assertEquals(0, webHandler._sinhala_buffer.length());

    // Type again, then simulate user tapping cursor back to start (external move)
    webHandler.key_up(KeyValue.makeCharKey('k'), Pointers.Modifiers.EMPTY);
    webHandler.selection_updated(0, cursor[0], cursor[0], -1, -1);
    webHandler.selection_updated(cursor[0], 0, 0, -1, -1);
    assertEquals(0, webHandler._sinhala_buffer.length());
  }

  @Test
  public void composingExpansionTriggersRuntimeFallbackAndRecovers()
  {
    handler.key_up(KeyValue.makeCharKey('S'), Pointers.Modifiers.EMPTY);
    handler.key_up(KeyValue.makeCharKey('a'), Pointers.Modifiers.EMPTY);

    // Simulate a broken host expanding the composing span to 3 chars ("ෂ්ස")
    handler.selection_updated(2, 3, 3, 0, 3);

    verify(connection).deleteSurroundingText(3, 0);
    verify(connection).commitText("ස", 1);

    // Ack recovery cursor move (3 -> 1) and ensure next keystroke stays in commit-replace mode
    handler.selection_updated(3, 1, 1, -1, -1);
    handler.key_up(KeyValue.makeCharKey('m'), Pointers.Modifiers.EMPTY);
    verify(connection).commitText("ම්", 1);
  }

  @Test
  public void geminiAndComposeNoSuggestionsShowCandidatesWhileSuppressingSpaceAutocorrect()
  {
    EditorInfo geminiInfo = new EditorInfo();
    geminiInfo.packageName = "com.google.android.apps.bard";
    geminiInfo.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
    config.editor_config.refresh(geminiInfo, mock(Resources.class));
    config.suggestions_enabled = true;
    config.space_bar_auto_complete = true;

    assertTrue(CandidatesView.should_show(geminiInfo));
    assertTrue(config.editor_config.should_show_candidates_view);
    assertTrue(config.editor_config.is_no_suggestions);
    assertFalse(config.editor_config.is_sensitive_input);
    assertTrue(config.editor_config.use_commit_replace_composing);

    // Terminal apps with TYPE_NULL still hide candidates
    EditorInfo termuxInfo = new EditorInfo();
    termuxInfo.packageName = "com.termux";
    termuxInfo.inputType = InputType.TYPE_NULL;
    assertFalse(CandidatesView.should_show(termuxInfo));

    // In English mode with is_no_suggestions, spacebar commits literal word + space, not autocomplete
    config.sinhala_transliteration = false;
    handler.started();
    handler.key_up(KeyValue.makeCharKey('t'), Pointers.Modifiers.EMPTY);
    handler.key_up(KeyValue.makeCharKey('e'), Pointers.Modifiers.EMPTY);
    suggestions.suggestions[0] = "testing";
    suggestions.count = 1;
    handler.key_up(KeyValue.getKeyByName("space"), Pointers.Modifiers.EMPTY);
    verify(connection).commitText(" ", 1);
    verify(connection, never()).commitText("testing ", 1);
  }
}
