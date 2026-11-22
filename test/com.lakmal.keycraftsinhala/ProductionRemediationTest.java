package com.lakmal.keycraftsinhala;

import android.content.Context;
import android.content.SharedPreferences;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.view.Window;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Regression tests for critical null/crash safety (C-1..C-4), in-IME emoji
 * search UX without EditText (H-2), and privacy/backup policy alignment (H-4).
 */
public class ProductionRemediationTest
{
  @Test
  public void c1_keyboard2ViewHandlesMissingParentWindowWithoutCrashing() throws Exception
  {
    InputMethodService ims = mock(InputMethodService.class);
    when(ims.getWindow()).thenReturn(null);
    Keyboard2View view = mock(Keyboard2View.class, CALLS_REAL_METHODS);

    Method getParentWindow = Keyboard2View.class.getDeclaredMethod("getParentWindow", Context.class);
    getParentWindow.setAccessible(true);
    assertNull(getParentWindow.invoke(view, ims));

    // Must return cleanly even if Theme/Context has no attached Dialog window
    view.refresh_navigation_bar(ims);
  }

  @Test
  public void c2_keyboard2WindowAndTokenHelpersHandleNullWindowGracefully() throws Exception
  {
    Keyboard2 keyboard = mock(Keyboard2.class, CALLS_REAL_METHODS);
    when(keyboard.getWindow()).thenReturn(null);

    Method updateLayout = Keyboard2.class.getDeclaredMethod("updateSoftInputWindowLayoutParams");
    updateLayout.setAccessible(true);
    updateLayout.invoke(keyboard);

    Method getToken = Keyboard2.class.getDeclaredMethod("getConnectionToken");
    getToken.setAccessible(true);
    assertNull(getToken.invoke(keyboard));
  }

  @Test
  public void c3_malformedNumericPreferencesAndEmojiCountsFallbackSafely()
  {
    SharedPreferences prefs = mock(SharedPreferences.class);
    when(prefs.getString("swipe_dist", null)).thenReturn("not-a-number");
    when(prefs.getString("slider_sensitivity", null)).thenReturn("NaN");
    when(prefs.getString("circle_sensitivity", null)).thenReturn("");
    when(prefs.getString("clipboard_history_duration", null)).thenThrow(new ClassCastException("int"));

    assertEquals(15f, Config.parseFloatPref(prefs, "swipe_dist", 15f), 0.001f);
    assertEquals(30f, Config.parseFloatPref(prefs, "slider_sensitivity", 30f), 0.001f);
    assertEquals(2, Config.parseIntPref(prefs, "circle_sensitivity", 2));
    assertEquals(5, Config.parseIntPref(prefs, "clipboard_history_duration", 5));

    when(prefs.getString("valid_f", null)).thenReturn(" 22.5 ");
    when(prefs.getString("valid_i", null)).thenReturn(" 12 ");
    assertEquals(22.5f, Config.parseFloatPref(prefs, "valid_f", 1f), 0.001f);
    assertEquals(12, Config.parseIntPref(prefs, "valid_i", 1));

    assertEquals(7, EmojiGridView.parseLastUsedCount("7"));
    assertEquals(-1, EmojiGridView.parseLastUsedCount("corrupted"));
    assertEquals(-1, EmojiGridView.parseLastUsedCount("-3"));
    assertEquals(-1, EmojiGridView.parseLastUsedCount(null));
  }

  @Test
  public void c3_clipboardHistoryServiceHandlesNullSystemClipboardManager()
  {
    Context ctx = mock(Context.class);
    when(ctx.getSystemService(Context.CLIPBOARD_SERVICE)).thenReturn(null);
    Handler handler = mock(Handler.class);

    ClipboardHistoryService svc = new ClipboardHistoryService(ctx, handler);
    assertNotNull(svc.get_entries());
    // Must not crash on missing ClipboardManager
    svc.add_current_clip();
  }

  @Test
  public void c4_editorConfigRefreshAcceptsNullEditorInfo()
  {
    EditorConfig cfg = new EditorConfig();
    cfg.numeric_layout = true;
    cfg.is_password_input = true;
    cfg.should_show_candidates_view = true;

    cfg.refresh(null, null);

    assertNull(cfg.info);
    assertFalse(cfg.numeric_layout);
    assertFalse(cfg.is_password_input);
    assertFalse(cfg.should_show_candidates_view);
    assertNull(cfg.action_key_replacement);
  }

  @Test
  public void h2_emojiPaneAvoidsEditTextInsideImeAndRetainsSearchControls() throws Exception
  {
    String xml = new String(
        Files.readAllBytes(new File("res/layout/emoji_pane.xml").toPath()),
        StandardCharsets.UTF_8);
    assertFalse("emoji_pane.xml must not contain EditText inside IME window", xml.contains("<EditText"));
    assertTrue(xml.contains("@+id/emoji_search_input"));
    assertTrue(xml.contains("@+id/emoji_search_paste"));
    assertTrue(xml.contains("@+id/emoji_search_clear"));
    assertTrue(xml.contains("@+id/emoji_search_letters"));
  }

  @Test
  public void h4_privacyDocsMatchIncludeOnlySettingsBackupPolicy() throws Exception
  {
    String privacyHtml = new String(
        Files.readAllBytes(new File("docs/privacy.html").toPath()),
        StandardCharsets.UTF_8);
    String readme = new String(
        Files.readAllBytes(new File("README.md").toPath()),
        StandardCharsets.UTF_8);

    assertTrue("docs/privacy.html must disclose preferences backup filename",
        privacyHtml.contains("com.lakmal.keycraftsinhala_preferences.xml"));
    assertFalse("docs/privacy.html must not falsely claim preferences are excluded",
        privacyHtml.contains("preferences and app data are excluded"));

    assertTrue("README.md must disclose preferences backup filename",
        readme.contains("com.lakmal.keycraftsinhala_preferences.xml"));
    assertFalse("README.md must not falsely claim preferences do not backup",
        readme.contains("Keyboard preferences සහ app data cloud backup හෝ device-to-device transfer වෙත backup නොවේ"));
  }
}
