package com.lakmal.keycraftsinhala;

import android.content.Context;
import android.content.SharedPreferences;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.view.Window;
import com.lakmal.keycraftsinhala.suggestions.CandidatesView;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import static org.junit.Assert.*;
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

  @Test
  public void phase1_incrTextLayoutHandlesEmptyLayoutsSafely() throws Exception
  {
    Keyboard2 keyboard = mock(Keyboard2.class, CALLS_REAL_METHODS);
    Config cfg = new Config();
    cfg.layouts = new java.util.ArrayList<>();
    java.lang.reflect.Field configField = Keyboard2.class.getDeclaredField("_config");
    configField.setAccessible(true);
    configField.set(keyboard, cfg);

    // Must not throw ArithmeticException: / by zero
    keyboard.incrTextLayout(1);
    keyboard.incrTextLayout(-1);

    cfg.layouts = null;
    keyboard.incrTextLayout(1);
  }

  @Test
  public void phase1_showDialogOnImeHandlesNullInputsSafely()
  {
    Utils.show_dialog_on_ime(null, (android.os.IBinder) null);
    Utils.show_dialog_on_ime(null, (InputMethodService) null);

    InputMethodService ims = mock(InputMethodService.class);
    when(ims.getWindow()).thenReturn(null);
    Utils.show_dialog_on_ime(mock(android.app.AlertDialog.class), ims);
  }

  @Test
  public void phase1_privacyDocsDiscloseOptionalTranslateShortcut() throws Exception
  {
    String privacyHtml = new String(
        Files.readAllBytes(new File("docs/privacy.html").toPath()),
        StandardCharsets.UTF_8);
    assertTrue("docs/privacy.html must disclose Google Translate handoff",
        privacyHtml.contains("translate.google.com"));
  }

  @Test
  public void phase2_h1_localizedKeyboardAndCandidatesAccessibilityLabelsUseContextStrings()
  {
    Context ctx = mock(Context.class);
    when(ctx.getString(R.string.a11y_key_backspace)).thenReturn("ආපස්සට මකන්න");
    when(ctx.getString(R.string.a11y_key_space)).thenReturn("හිස්තැන");
    when(ctx.getString(R.string.a11y_candidate_emoji_fmt, "🙏")).thenReturn("ඉමොජි යෝජනාව: 🙏");
    when(ctx.getString(R.string.a11y_candidate_clipboard_fmt, "ආයුබෝවන්"))
        .thenReturn("ක්ලිප්බෝඩයෙන් අලවන්න: ආයුබෝවන්");

    KeyValue[] backspaceValues = new KeyValue[9];
    backspaceValues[0] = KeyValue.getKeyByName("backspace");
    KeyboardData.Key backspace = new KeyboardData.Key(backspaceValues, null, 0,
        1f, 0f, null, KeyboardData.Key.Role.Action);

    KeyValue[] spaceValues = new KeyValue[9];
    spaceValues[0] = KeyValue.getKeyByName("space");
    KeyboardData.Key space = new KeyboardData.Key(spaceValues, null, 0,
        1f, 0f, null, KeyboardData.Key.Role.Space_bar);

    assertEquals("ආපස්සට මකන්න", Keyboard2View.accessibilityLabelForKey(ctx, backspace, null));
    assertEquals("හිස්තැන", Keyboard2View.accessibilityLabelForKey(ctx, space, null));
    assertEquals("Backspace", Keyboard2View.accessibilityLabelForKey(null, backspace, null));

    assertEquals("ඉමොජි යෝජනාව: 🙏",
        CandidatesView.accessibleDescriptionForItem(ctx, 3, "🙏", null));
    assertEquals("ක්ලිප්බෝඩයෙන් අලවන්න: ආයුබෝවන්",
        CandidatesView.accessibleDescriptionForItem(ctx, 0, "📋 ආයුබෝවන්", "ආයුබෝවන්"));
    assertNull(CandidatesView.accessibleDescriptionForItem(ctx, 1, "සිංහල", null));
  }

  @Test
  public void phase2_h2_sinhalaStringsMatchEnglishTranslatableKeysAndFormats() throws Exception
  {
    Map<String, String> en = loadTranslatableStrings(new File("res/values/strings.xml"));
    Map<String, String> si = loadTranslatableStrings(new File("res/values-si/strings.xml"));

    assertFalse(en.isEmpty());
    for (Map.Entry<String, String> entry : en.entrySet())
    {
      String key = entry.getKey();
      assertTrue("Missing Sinhala translation for key: " + key, si.containsKey(key));
      assertFalse("Blank Sinhala translation for key: " + key, si.get(key).trim().isEmpty());
      assertEquals("Format specifier count mismatch for key: " + key,
          countFormatSpecifiers(entry.getValue()),
          countFormatSpecifiers(si.get(key)));
    }
  }

  @Test
  public void phase2_h3_touchTargetsAndColorPickerAccessibilityMeetMinimums() throws Exception
  {
    String keyboardXml = new String(
        Files.readAllBytes(new File("res/layout/keyboard.xml").toPath()),
        StandardCharsets.UTF_8);
    assertFalse(keyboardXml.contains("android:layout_height=\"36dp\""));

    String emojiPaneXml = new String(
        Files.readAllBytes(new File("res/layout/emoji_pane.xml").toPath()),
        StandardCharsets.UTF_8);
    assertFalse(emojiPaneXml.contains("android:layout_height=\"44dp\""));
    assertTrue(emojiPaneXml.contains("android:id=\"@+id/emoji_mode_bar\"\n      android:layout_width=\"match_parent\"\n      android:layout_height=\"48dp\""));

    String stylesXml = new String(
        Files.readAllBytes(new File("res/values/styles.xml").toPath()),
        StandardCharsets.UTF_8);
    assertTrue(stylesXml.contains("<style name=\"candidates_emoji\">\n    <item name=\"android:layout_width\">@dimen/candidates_emoji_width</item>\n    <item name=\"android:layout_height\">48dp</item>"));

    String colorWidgetXml = new String(
        Files.readAllBytes(new File("res/layout/pref_color_picker_widget.xml").toPath()),
        StandardCharsets.UTF_8);
    assertFalse(colorWidgetXml.contains("Color preview"));
    assertTrue(colorWidgetXml.contains("android:contentDescription=\"@null\""));

    String stickerHelper = new String(
        Files.readAllBytes(new File("srcs/java/com/lakmal/keycraftsinhala/StickerGifHelper.java").toPath()),
        StandardCharsets.UTF_8);
    assertFalse(stickerHelper.contains("Math.round(36 * density)"));
    assertTrue(stickerHelper.contains("Math.round(48 * density)"));
  }

  @Test
  public void phase2_h5_applySafeSystemBarInsetsHandlesNullAndActivityWiring() throws Exception
  {
    Utils.applySafeSystemBarInsets(null, true);
    Utils.applySafeSystemBarInsets(null, true, true);

    String settings = new String(
        Files.readAllBytes(new File("srcs/java/com/lakmal/keycraftsinhala/SettingsActivity.java").toPath()),
        StandardCharsets.UTF_8);
    assertTrue(settings.contains("applySafeSystemBarInsets"));
    assertTrue(settings.contains("onPreferenceTreeClick"));

    String launcher = new String(
        Files.readAllBytes(new File("srcs/java/com/lakmal/keycraftsinhala/LauncherActivity.java").toPath()),
        StandardCharsets.UTF_8);
    assertTrue(launcher.contains("applySafeSystemBarInsets"));

    String dicts = new String(
        Files.readAllBytes(new File("srcs/java/com/lakmal/keycraftsinhala/dict/DictionariesActivity.java").toPath()),
        StandardCharsets.UTF_8);
    assertTrue(dicts.contains("applySafeSystemBarInsets"));
  }

  private static Map<String, String> loadTranslatableStrings(File file) throws Exception
  {
    Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file);
    NodeList nodes = doc.getElementsByTagName("string");
    Map<String, String> map = new HashMap<>();
    for (int i = 0; i < nodes.getLength(); i++)
    {
      Element el = (Element) nodes.item(i);
      if ("false".equals(el.getAttribute("translatable")))
        continue;
      String name = el.getAttribute("name");
      if (name != null && !name.isEmpty())
        map.put(name, el.getTextContent());
    }
    return map;
  }

  private static int countFormatSpecifiers(String s)
  {
    Matcher m = Pattern.compile("%(?:\\d+\\$)?[sdfoxegac%]").matcher(s);
    int count = 0;
    while (m.find())
    {
      if (!"%%".equals(m.group()))
        count++;
    }
    return count;
  }

  @Test
  public void phase3_logsExnIsDebugGatedAndDictionariesUsesLogsHelper() throws Exception
  {
    Logs.set_debug_logs(false);
    assertFalse(Logs.isDebugEnabled());
    Logs.exn("release error", new RuntimeException("sensitive detail"));

    Logs.set_debug_logs(true);
    assertFalse("Non-debuggable contexts must not enable logging", Logs.isDebugEnabled());
    Logs.set_debug_logs(false);
    assertFalse(Logs.isDebugEnabled());

    String logsSrc = new String(
        Files.readAllBytes(new File("srcs/java/com/lakmal/keycraftsinhala/Logs.java").toPath()),
        StandardCharsets.UTF_8);
    assertTrue(logsSrc.contains("if (_debug_logs != null)\n      Log.e(TAG, msg, e);"));

    String dictionariesSrc = new String(
        Files.readAllBytes(new File("srcs/java/com/lakmal/keycraftsinhala/dict/Dictionaries.java").toPath()),
        StandardCharsets.UTF_8);
    assertFalse("Dictionaries.java must use Logs.exn instead of raw Log.e",
        dictionariesSrc.contains("android.util.Log.e("));
  }

  @Test
  public void phase3_monochromeIconTextEditLintAndPerfCleanupsApplied() throws Exception
  {
    String icLauncherXml = new String(
        Files.readAllBytes(new File("res/mipmap-anydpi-v26/ic_launcher.xml").toPath()),
        StandardCharsets.UTF_8);
    assertTrue(icLauncherXml.contains("<monochrome android:drawable=\"@drawable/ic_launcher_monochrome\"/>"));
    assertTrue(new File("res/drawable/ic_launcher_monochrome.xml").isFile());

    String textEditXml = new String(
        Files.readAllBytes(new File("res/layout/text_edit_pane.xml").toPath()),
        StandardCharsets.UTF_8);
    assertTrue(textEditXml.contains("android:baselineAligned=\"false\""));
    assertTrue(textEditXml.contains("@string/text_edit_arrow_left_symbol"));
    assertTrue(textEditXml.contains("@string/text_edit_arrow_right_symbol"));
    assertTrue(textEditXml.contains("@string/text_edit_backspace_symbol"));

    String emojiGridSrc = new String(
        Files.readAllBytes(new File("srcs/java/com/lakmal/keycraftsinhala/EmojiGridView.java").toPath()),
        StandardCharsets.UTF_8);
    assertTrue(emojiGridSrc.contains("SKIN_TONE_PAINT"));
    assertFalse(emojiGridSrc.contains("Paint paint = new Paint()"));

    String learnerSrc = new String(
        Files.readAllBytes(new File("srcs/java/com/lakmal/keycraftsinhala/SinhalaLearner.java").toPath()),
        StandardCharsets.UTF_8);
    assertFalse(learnerSrc.contains(".putBoolean(migrationKey, true)\n        .commit()"));
  }

  @Test
  public void phase3_ciWorkflowAndFdroidDocsAlignWithSdk36AndV130() throws Exception
  {
    String androidYml = new String(
        Files.readAllBytes(new File(".github/workflows/android.yml").toPath()),
        StandardCharsets.UTF_8);
    assertTrue(androidYml.contains("build-tools;36.0.0"));
    assertTrue(androidYml.contains("python3 check_layout.py"));

    String fdroidMd = new String(
        Files.readAllBytes(new File("docs/F_DROID.md").toPath()),
        StandardCharsets.UTF_8);
    assertTrue(fdroidMd.contains("CurrentVersion: 1.3.0"));
    assertTrue(fdroidMd.contains("CurrentVersionCode: 5"));
  }
}
