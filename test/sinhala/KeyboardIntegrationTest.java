package com.lakmal.keycraftsinhala;

import android.os.Handler;
import android.view.inputmethod.InputConnection;
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
  public void backspaceUpdatesAndClearsActiveSinhalaComposition()
  {
    handler.key_up(KeyValue.makeCharKey('k'), Pointers.Modifiers.EMPTY);
    handler.key_up(KeyValue.makeCharKey('a'), Pointers.Modifiers.EMPTY);
    handler.key_up(KeyValue.getKeyByName("backspace"), Pointers.Modifiers.EMPTY);

    verify(connection, times(2)).setComposingText("ක්", 1);
    assertEquals("k", handler._sinhala_buffer.toString());

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
}
