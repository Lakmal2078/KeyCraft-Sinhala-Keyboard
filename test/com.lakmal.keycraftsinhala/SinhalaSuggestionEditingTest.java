package com.lakmal.keycraftsinhala;

import android.os.Handler;
import android.view.inputmethod.InputConnection;
import com.lakmal.keycraftsinhala.suggestions.Suggestions;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import juloo.cdict.Cdict;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SinhalaSuggestionEditingTest
{
  private static Cdict dictionary;
  private final StringBuilder editor = new StringBuilder();
  private int cursor;
  private Config config;
  private InputConnection connection;
  private Handler mainHandler;
  private KeyEventHandler handler;
  private Suggestions suggestions;

  @BeforeClass
  public static void loadDictionary() throws Exception
  {
    dictionary = Cdict.of_bytes(Files.readAllBytes(Paths.get("assets/dict/si.dict")))[0];
  }

  @Before
  public void setUp()
  {
    config = new Config();
    config.editor_config = new EditorConfig();
    config.editor_config.should_show_candidates_view = true;
    config.editor_config.initial_text_before_cursor = "";
    config.suggestions_enabled = true;
    config.current_dictionary_name = "si";
    config.current_dictionary = dictionary;
    connection = mock(InputConnection.class);
    when(connection.getTextBeforeCursor(anyInt(), anyInt())).thenAnswer(call -> {
      int length = call.getArgument(0);
      return editor.substring(Math.max(0, cursor - length), cursor);
    });
    when(connection.getTextAfterCursor(anyInt(), anyInt())).thenAnswer(call -> {
      int length = call.getArgument(0);
      return editor.substring(cursor, Math.min(editor.length(), cursor + length));
    });
    when(connection.deleteSurroundingText(anyInt(), anyInt())).thenAnswer(call -> {
      int before = call.getArgument(0), after = call.getArgument(1);
      int start = Math.max(0, cursor - before);
      editor.delete(start, Math.min(editor.length(), cursor + after));
      cursor = start;
      return true;
    });
    when(connection.commitText(any(CharSequence.class), anyInt())).thenAnswer(call -> {
      String text = call.getArgument(0).toString();
      editor.insert(cursor, text);
      cursor += text.length();
      return true;
    });
    KeyEventHandler.IReceiver receiver = mock(KeyEventHandler.IReceiver.class);
    mainHandler = mock(Handler.class);
    when(receiver.getCurrentInputConnection()).thenReturn(connection);
    when(receiver.getHandler()).thenReturn(mainHandler);
    when(receiver.isSinhalaLayout()).thenReturn(true);
    suggestions = new Suggestions(receiver, config);
    handler = new KeyEventHandler(config, receiver, suggestions);
    handler.started();
  }

  private void moveCursor(int position)
  {
    int old = cursor;
    cursor = position;
    handler.selection_updated(old, cursor, cursor);
  }

  private void type(String word)
  {
    for (int i = 0; i < word.length(); i++)
      handler.send_text(word.substring(i, i + 1));
  }

  private void assertPrefixes(String prefix)
  {
    assertTrue("Expected suggestions for " + prefix, suggestions.count > 0);
    for (int i = 0; i < suggestions.count; i++)
      assertTrue(suggestions.suggestions[i], suggestions.suggestions[i].startsWith(prefix));
  }

  @Test
  public void partialDirectWordHasRelevantBundledCompletion()
  {
    type("කොහොම");
    assertPrefixes("කොහොම");
    assertTrue(Arrays.asList(suggestions.suggestions).contains("කොහොමද"));
  }

  @Test
  public void repeatedBackspaceRefreshesAfterEachDeletionAndResetsAtEmpty()
  {
    type("අපි");
    assertEquals("අපි", suggestions.suggestions[0]);
    handler.handle_backspace();
    assertEquals("අප", editor.toString());
    assertPrefixes("අප");
    handler.handle_backspace();
    assertEquals("අ", editor.toString());
    assertPrefixes("අ");
    handler.handle_backspace();
    assertEquals("", editor.toString());
    assertEquals(0, suggestions.count);
  }

  @Test
  public void deletionBeforeVowelSignRemovesTheBaseAndItsSign()
  {
    type("අපි");
    moveCursor(2);
    handler.handle_backspace();
    assertEquals("අ", editor.toString());
    assertPrefixes("අ");
  }

  @Test
  public void middleDeletionUsesActualEditorTextBeforeTheCursor()
  {
    type("අපිට");
    moveCursor(3);
    assertPrefixes("අපි");
    // Simulate an editor-side mutation without a selection notification.
    editor.setCharAt(1, 'ම');
    handler.handle_backspace();
    assertEquals("අමට", editor.toString());
    assertPrefixes("අම");
  }

  @Test
  public void cursorUsesOnlyCompleteClustersBeforeItAndClearsAtWordStart()
  {
    type("අපිට");
    moveCursor(3);
    assertPrefixes("අපි");
    moveCursor(2);
    assertPrefixes("අ");
    moveCursor(0);
    assertEquals(0, suggestions.count);
  }

  @Test
  public void deletingJoinedConsonantAlsoRemovesItsFollowingVowel()
  {
    type("අක්‍රි");
    moveCursor(5);
    handler.handle_backspace();
    assertEquals("අක", editor.toString());
    assertPrefixes("අක");
  }

  @Test
  public void deletingViramaDoesNotLeaveAnOrphanJoiner()
  {
    type("අක්‍රි");
    moveCursor(3);
    handler.handle_backspace();
    assertEquals("අකරි", editor.toString());
    assertPrefixes("අක");
  }

  @Test
  public void deletingInsideDecomposedVowelRemovesBothPartsAndKeepsTheBase()
  {
    String decomposed = Normalizer.normalize("කේ", Normalizer.Form.NFD);
    assertTrue(decomposed.length() > 2);
    handler.paste_from_clipboard_pane(decomposed);
    moveCursor(2);
    handler.handle_backspace();
    assertEquals("ක", editor.toString());
    assertPrefixes("ක");
  }

  @Test
  public void punctuationAfterSinhalaClearsTheCurrentWord()
  {
    type("අපි.");
    assertEquals(0, suggestions.count);
    handler.handle_backspace();
    editor.deleteCharAt(3);
    moveCursor(3);
    assertEquals("අපි", suggestions.suggestions[0]);
  }

  @Test
  public void unavailableEditorContextClearsStaleCandidatesAfterFallback()
  {
    type("අපි");
    assertEquals("අපි", suggestions.suggestions[0]);
    when(connection.getTextBeforeCursor(anyInt(), anyInt())).thenReturn(null);
    handler.handle_backspace();
    verify(mainHandler).postDelayed(same(handler._typedword.delayed_refresh_run), anyLong());
    handler._typedword.delayed_refresh_run.run();
    assertEquals(0, suggestions.count);
  }

  @Test
  public void fallbackKeyDeletionRefreshesWhenEditorReportsSelection()
  {
    config.sinhala_transliteration = true;
    handler.paste_from_clipboard_pane("amma");
    handler.handle_backspace();
    editor.deleteCharAt(3);
    moveCursor(3);
    assertLatinAndSinhalaCandidates("amm");
    assertFalse(Arrays.asList(suggestions.suggestions).contains("amma"));
  }

  @Test
  public void canonicalLookupAndConjunctVariantUseBundledDictionary()
  {
    suggestions.currently_typed_word(Normalizer.normalize("කොහොමද", Normalizer.Form.NFD));
    assertEquals("කොහොමද", suggestions.suggestions[0]);
    assertTrue(dictionary.find("ක්‍රිකට්").found);
    assertFalse(dictionary.find("ක්රිකට්").found);
    suggestions.currently_typed_word("ක්රිකට්");
    assertEquals("ක්‍රිකට්", suggestions.suggestions[0]);
  }

  @Test
  public void acceptedContextAndPersonalWordHaveVisibleSlots()
  {
    suggestions.set_predictions(Arrays.asList("අපි"));
    assertTrue(handler.personalWords.learn("අම්මා"));
    suggestions.currently_typed_word("අ");
    List<String> visible = Arrays.asList(suggestions.suggestions)
        .subList(0, Suggestions.VISIBLE_COUNT);
    assertTrue(visible.contains("අපි"));
    assertTrue(visible.contains("අම්මා"));
  }

  @Test
  public void everydayCompletionsStayVisibleAfterTheExactPrefix()
  {
    for (String[] pair : new String[][] {
        {"පා", "පාන්"}, {"පො", "පොත"}, {"ගුරු", "ගුරුවරිය"}, {"හො", "හොඳයි"}
    })
    {
      suggestions.currently_typed_word(pair[0]);
      assertEquals(pair[0], suggestions.suggestions[0]);
      assertPrefixes(pair[0]);
      assertTrue(pair[1], Arrays.asList(suggestions.suggestions)
          .subList(0, Suggestions.VISIBLE_COUNT).contains(pair[1]));
    }
  }

  @Test
  public void exactSinhalaMatchBeatsPersonalAndContextCompletions()
  {
    assertTrue(handler.personalWords.learn("අම්මා"));
    suggestions.set_predictions(Arrays.asList("අපි"));
    suggestions.currently_typed_word("අ");
    assertEquals("අ", suggestions.suggestions[0]);
    assertEquals("අම්මා", suggestions.suggestions[1]);
    assertEquals("අපි", suggestions.suggestions[2]);
  }

  @Test
  public void recentUsageBreaksPersonalFrequencyTies()
  {
    PersonalWords words = new PersonalWords(null);
    words.learn("මම");
    words.learn("මල");
    assertEquals("මල", words.matching("ම").get(0));
    words.learn("මම");
    assertEquals("මම", words.matching("ම").get(0));
    words.learn("මල");
    assertEquals("මල", words.matching("ම").get(0));
    assertEquals("මම", words.matching("මම").get(0));
  }

  @Test
  public void literalSinglishKeepsAnExactAlternativeAheadOfContextCompletion()
  {
    config.literal_singlish = true;
    config.sinhala_transliteration = true;
    suggestions.set_predictions(Arrays.asList("කොළඹ"));
    type("ka");
    assertEquals("ක", suggestions.suggestions[0]);
    assertEquals("කළා", suggestions.suggestions[1]);
    assertEquals("ka", suggestions.suggestions[2]);
    handler.handle_space_bar();
    assertEquals("ka ", editor.toString());
  }

  @Test
  public void literalSinglishOffersSinhalaButKeepsEnglishUntilExplicitSelection()
  {
    type("අපි");
    handler.handle_space_bar();
    config.literal_singlish = true;
    config.sinhala_transliteration = true;
    type("API");
    assertLatinAndSinhalaCandidates("API");
    handler.handle_space_bar();
    type("aayuboovan");
    assertEquals("ආයුබෝවන්", suggestions.suggestions[0]);
    handler.suggestion_entered("ආයුබෝවන්", false);
    assertEquals("අපි API ආයුබෝවන්", editor.toString());
  }

  private void assertLatinAndSinhalaCandidates(String latin)
  {
    List<String> visible = Arrays.asList(suggestions.suggestions)
        .subList(0, Suggestions.VISIBLE_COUNT);
    assertTrue(visible.contains(latin));
    assertTrue(visible.stream().anyMatch(word -> word != null && SinhalaText.isSinhalaWord(word)));
  }
}
