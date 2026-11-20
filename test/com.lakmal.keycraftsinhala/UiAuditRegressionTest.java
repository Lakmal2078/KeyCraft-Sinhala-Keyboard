package com.lakmal.keycraftsinhala;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.TypedArray;
import com.lakmal.keycraftsinhala.prefs.ColorPickerPreference;
import com.lakmal.keycraftsinhala.suggestions.Cdict;
import com.lakmal.keycraftsinhala.suggestions.Suggestions;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Regression tests for the prioritized UI/UX audit fixes across emoji search,
 * WCAG contrast calculation, suggestion deduplication, key border roles, and
 * keyboard accessibility labels.
 */
public class UiAuditRegressionTest
{
  @Test
  public void emojiSearchMatchesCuratedSynonymsUnicodeNamesAndMultiTokens()
  {
    assertTrue(Emoji.matchesEmoji("\u2764\uFE0F", "love"));
    assertTrue(Emoji.matchesEmoji("\u2764", "romantic"));
    assertTrue(Emoji.matchesEmoji("\uD83D\uDE02", "lol"));
    assertTrue(Emoji.matchesEmoji("\uD83D\uDE4F", "ayubowan"));
    assertTrue(Emoji.matchesEmoji("\uD83D\uDE4F", "thanks"));
    assertTrue(Emoji.matchesEmoji("\uD83D\uDE80", "rocket"));
    assertTrue(Emoji.matchesEmoji("\u2764\uFE0F", "heart red"));
    assertFalse(Emoji.matchesEmoji("\uD83D\uDE80", "ayubowan"));
    assertFalse(Emoji.matchesEmoji(null, "love"));
    assertFalse(Emoji.matchesEmoji("", "love"));
    assertFalse(Emoji.matchesEmoji("\u2764\uFE0F", null));
    assertTrue(Emoji.matchesEmoji("\u2764\uFE0F", "   "));
  }

  @Test
  public void colorPickerContrastRatioFollowsWcagBounds()
  {
    double blackWhite = ColorPickerPreference.calculateContrastRatio(0xFF000000, 0xFFFFFFFF);
    assertEquals(21.0, blackWhite, 0.05);

    double identical = ColorPickerPreference.calculateContrastRatio(0xFF3D5AFE, 0xFF3D5AFE);
    assertEquals(1.0, identical, 0.001);

    double lowContrast = ColorPickerPreference.calculateContrastRatio(0xFF222222, 0xFF000000);
    assertTrue("Dark gray on black must fail AA normal text (< 4.5)", lowContrast < 4.5);

    double highContrast = ColorPickerPreference.calculateContrastRatio(0xFFFFFFFF, 0xFF1E1E2E);
    assertTrue("White on Catppuccin dark must pass AA (>= 4.5)", highContrast >= 4.5);
  }

  @Test
  public void suggestionsDeduplicatesPredictionsAndCapitalizedDictionaryResults()
  {
    Config config = new Config();
    config.editor_config = new EditorConfig();
    config.editor_config.should_show_candidates_view = true;
    config.suggestions_enabled = true;
    Suggestions.Callback callback = mock(Suggestions.Callback.class);
    Suggestions suggestions = new Suggestions(callback, config);
    suggestions.started();

    suggestions.set_predictions(Arrays.asList("hello", "hello", "world", "", null, "world"));
    assertEquals(2, suggestions.count);
    assertEquals("hello", suggestions.suggestions[0]);
    assertEquals("world", suggestions.suggestions[1]);
    assertNull(suggestions.suggestions[2]);

    Cdict mockDict = mock(Cdict.class);
    config.current_dictionary = mockDict;
    when(mockDict.find("hello")).thenReturn(new Cdict.Result(true, 0));
    when(mockDict.word(0)).thenReturn("hello");
    when(mockDict.word(1)).thenReturn("Hello");
    when(mockDict.word(2)).thenReturn("helpful");
    when(mockDict.suffixes(any(), anyInt())).thenReturn(new int[] { 1, 2 });
    when(mockDict.distance(any(), anyInt(), anyInt())).thenReturn(new int[0]);

    suggestions.currently_typed_word("Hello");
    assertEquals(2, suggestions.count);
    assertEquals("Hello", suggestions.suggestions[0]);
    assertEquals("Helpful", suggestions.suggestions[1]);
    assertNull(suggestions.suggestions[2]);
  }

  @Test
  public void themeKeyBordersPropagateToActionAndSpacebarAndDetectBevelEdges()
  {
    Context ctx = mock(Context.class);
    AssetManager assets = mock(AssetManager.class);
    Resources.Theme resTheme = mock(Resources.Theme.class);
    TypedArray ta = mock(TypedArray.class);
    when(ctx.getAssets()).thenReturn(assets);
    when(ctx.getTheme()).thenReturn(resTheme);
    when(resTheme.obtainStyledAttributes(any(), eq(R.styleable.keyboard), eq(0), eq(0)))
        .thenReturn(ta);
    when(ta.getColor(R.styleable.keyboard_colorKey, 0)).thenReturn(0xFF202020);
    when(ta.getColor(R.styleable.keyboard_colorKeyAction, 0xFF202020)).thenReturn(0xFF3D5AFE);
    when(ta.getColor(R.styleable.keyboard_colorKeySpaceBar, 0xFF202020)).thenReturn(0xFF2A2A2A);
    when(ta.getColor(R.styleable.keyboard_keyBorderColorTop, 0xFF202020)).thenReturn(0xFF555555);
    when(ta.getColor(R.styleable.keyboard_keyBorderColorBottom, 0xFF202020)).thenReturn(0xFF101010);
    when(ta.getColor(R.styleable.keyboard_keyBorderColorLeft, 0xFF202020)).thenReturn(0xFF333333);
    when(ta.getColor(R.styleable.keyboard_keyBorderColorRight, 0xFF202020)).thenReturn(0xFF333333);

    Theme theme = new Theme(ctx, null);
    Config config = new Config();
    config.borderConfig = true;
    config.customBorderLineWidth = 3.5f;
    config.custom_colors_enabled = false;
    config.keyShape = "rounded";

    Theme.Computed.Key actionKey =
        new Theme.Computed.Key(theme, config, 100f, false, KeyboardData.Key.Role.Action);
    Theme.Computed.Key spaceKey =
        new Theme.Computed.Key(theme, config, 100f, false, KeyboardData.Key.Role.Space_bar);

    assertEquals(3.5f, actionKey.border_width, 0.001f);
    assertEquals(3.5f, spaceKey.border_width, 0.001f);
    assertTrue("Distinct bevel edge colors must activate 4-edge border drawing",
        actionKey.has_distinct_borders);
  }

  @Test
  public void keyboardAccessibilityLabelsAndHitMarginAreConfigured()
  {
    assertTrue(Keyboard2View.TOUCH_MARGIN_TOLERANCE_PX >= 4.0f);
    assertEquals("Backspace",
        Keyboard2View.accessibilityLabelForKeyValue(KeyValue.getKeyByName("backspace")));
    assertEquals("Space",
        Keyboard2View.accessibilityLabelForKeyValue(KeyValue.getKeyByName("space")));
    assertEquals("Emoji",
        Keyboard2View.accessibilityLabelForKeyValue(KeyValue.getKeyByName("switch_emoji")));
    assertEquals("Clipboard",
        Keyboard2View.accessibilityLabelForKeyValue(KeyValue.getKeyByName("switch_clipboard")));
  }
}
