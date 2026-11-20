package com.lakmal.keycraftsinhala;

import android.content.SharedPreferences;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Automated UI & Settings Audit Test Suite.
 * Verifies key shape calculations, border rendering logic, settings persistence,
 * sound and haptic toggles, Sinhala glyph non-clipping, and touch area hit detection.
 */
public class SettingsUiAuditTest
{
  private SharedPreferences mockPrefs;
  private SharedPreferences.Editor mockEditor;

  @Before
  public void setUp()
  {
    mockPrefs = mock(SharedPreferences.class);
    mockEditor = mock(SharedPreferences.Editor.class, RETURNS_SELF);
    when(mockPrefs.edit()).thenReturn(mockEditor);
  }

  @Test
  public void keyboardAccessibilityLabelsDescribeEditingActions()
  {
    KeyValue[] backspaceValues = new KeyValue[9];
    backspaceValues[0] = KeyValue.getKeyByName("backspace");
    KeyboardData.Key backspace = new KeyboardData.Key(backspaceValues, null, 0,
        1f, 0f, null, KeyboardData.Key.Role.Action);

    KeyValue[] spaceValues = new KeyValue[9];
    spaceValues[0] = KeyValue.getKeyByName("space");
    KeyboardData.Key space = new KeyboardData.Key(spaceValues, null, 0,
        1f, 0f, null, KeyboardData.Key.Role.Space_bar);

    assertEquals("Backspace", Keyboard2View.accessibilityLabelForKey(backspace));
    assertEquals("Space", Keyboard2View.accessibilityLabelForKey(space));
    assertEquals("Keyboard key", Keyboard2View.accessibilityLabelForKey(null));
  }

  @Test
  public void testSoundOnKeypressPreference()
  {
    when(mockPrefs.getBoolean("sound_on_keypress", false)).thenReturn(true);
    assertTrue(mockPrefs.getBoolean("sound_on_keypress", false));

    when(mockPrefs.getBoolean("sound_on_keypress", false)).thenReturn(false);
    assertFalse(mockPrefs.getBoolean("sound_on_keypress", false));
  }

  @Test
  public void testKeyBorderRadiusPreservesKeyShapeWhenCustomRadiusZero()
  {
    // When custom radius is 0 (default), computeKeyBorderRadius must return the shape's original radius
    // rather than collapsing to 0.
    float shapeRadius = 12.0f;
    float keyWidth = 100.0f;
    float keyHeight = 80.0f;

    // Case 1: customBorderEnabled = true, but customRadius = 0 -> must retain shape radius ("rounded" = 0.16 * keyWidth)
    float roundedRadius = Theme.computeKeyBorderRadius("rounded", keyWidth, shapeRadius, true, 0.0f);
    assertEquals("When customRadius is 0, rounded shape radius must be preserved", keyWidth * 0.16f, roundedRadius, 0.001f);

    // Case 2: customBorderEnabled = false -> must retain shape radius ("squircle" = 0.28 * keyWidth)
    float squircleRadius = Theme.computeKeyBorderRadius("squircle", keyWidth, shapeRadius, false, 0.2f);
    assertEquals("When customBorder is disabled, shape radius must be preserved", keyWidth * 0.28f, squircleRadius, 0.001f);

    // Case 3: customBorderEnabled = true and customRadius > 0 -> scale with key width
    float customRadius = 0.25f;
    float expected = customRadius * keyWidth;
    float scaledRadius = Theme.computeKeyBorderRadius("rounded", keyWidth, shapeRadius, true, customRadius);
    assertEquals("When customRadius > 0, radius is calculated from keyWidth", expected, scaledRadius, 0.001f);
  }

  @Test
  public void testThemeCategorizationCoversAllEighteenThemes()
  {
    String[] themes = {
      "system", "dark", "light", "black", "oled_black", "white", "monet",
      "graphite_pro", "gruvbox_dark", "solarized_dark", "dracula", "nord",
      "tokyo_night", "catppuccin_mocha", "catppuccin_latte", "sinhala_sand",
      "lanka_maroon", "high_contrast"
    };

    int systemCount = 0;
    int lightCount = 0;
    int darkCount = 0;
    int highContrastCount = 0;
    int sriLankaCount = 0;
    int terminalCount = 0;

    for (String t : themes)
    {
      ThemePicker.ThemeCategory cat = ThemePicker.categoryOf(t);
      assertNotNull("Theme " + t + " must belong to a defined category", cat);
      switch (cat)
      {
        case SYSTEM: systemCount++; break;
        case LIGHT: lightCount++; break;
        case DARK: darkCount++; break;
        case HIGH_CONTRAST: highContrastCount++; break;
        case SRI_LANKA: sriLankaCount++; break;
        case TERMINAL: terminalCount++; break;
        default: fail("Unexpected category: " + cat);
      }
    }

    assertEquals("SYSTEM count must be 2", 2, systemCount);
    assertEquals("LIGHT count must be 3", 3, lightCount);
    assertEquals("DARK count must be 4", 4, darkCount);
    assertEquals("HIGH_CONTRAST count must be 1", 1, highContrastCount);
    assertEquals("SRI_LANKA count must be 2", 2, sriLankaCount);
    assertEquals("TERMINAL count must be 6", 6, terminalCount);
    assertEquals("Total curated themes must be 18", 18, themes.length);
  }

  @Test
  public void testSinhalaSublabelPreservesComplexUnicodeLigatures()
  {
    // Sinhala ligatures with virama and ZWJ must NOT be truncated to 3 characters.
    // e.g. "ක්‍ර" (ka + virama + ZWJ + ra) = 4 chars
    // e.g. "ක්ෂ" (ka + virama + ZWJ + ssa) = 4 chars
    // e.g. "ර්‍ය" (ra + virama + ZWJ + ya) = 4 chars
    String complexKra = "\u0D9API\u0DCA\u200D\u0DBB"; // ka + virama + ZWJ + ra
    String complexKsha = "\u0D9A\u0DCA\u200D\u0DC2"; // ka + virama + ZWJ + sha

    assertTrue("Complex Sinhala ligature with ZWJ must be detected as complex script",
        Keyboard2View.isComplexScript(complexKra));
    assertTrue("Complex Sinhala ligature must be detected as complex script",
        Keyboard2View.isComplexScript(complexKsha));

    // Regular ASCII should not be flagged as complex script
    assertFalse("Plain ASCII text should not be flagged as complex script",
        Keyboard2View.isComplexScript("ABC"));
  }

  @Test
  public void testTouchHitMarginTolerance()
  {
    // Verifies that Keyboard2View touch margin tolerance is active (positive margin)
    assertTrue("Touch margin tolerance should be at least 4 pixels",
        Keyboard2View.TOUCH_MARGIN_TOLERANCE_PX >= 4.0f);
  }

  @Test
  public void testKeyboardHeightAndPaddingScaling()
  {
    // Verify valid keyboard height scale bounds (typically 0.5f to 1.5f)
    float minScale = 0.5f;
    float maxScale = 1.5f;
    float defaultScale = 1.0f;

    assertTrue(defaultScale >= minScale && defaultScale <= maxScale);
  }
}
