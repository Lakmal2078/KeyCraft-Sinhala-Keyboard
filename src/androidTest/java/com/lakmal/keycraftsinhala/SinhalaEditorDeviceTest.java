package com.lakmal.keycraftsinhala;

import android.os.Handler;
import android.os.Looper;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.EditText;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.lakmal.keycraftsinhala.suggestions.Suggestions;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SinhalaEditorDeviceTest
{
  @Test public void inputConnectionPreservesSuffixAndUnwindsConjunct()
  {
    InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
      EditText editor = new EditText(ApplicationProvider.getApplicationContext());
      editor.setText("!");
      editor.setSelection(0);
      EditorInfo info = new EditorInfo();
      InputConnection connection = editor.onCreateInputConnection(info);
      assertNotNull(connection);
      Config config = new Config();
      config.editor_config = new EditorConfig();
      config.sinhala_transliteration = true;
      config.suggestions_enabled = true;
      config.editor_config.should_show_candidates_view = true;
      config.editor_config.initial_sel_start = config.editor_config.initial_sel_end = 0;
      config.editor_config.initial_text_before_cursor = "";
      KeyEventHandler.IReceiver receiver = new KeyEventHandler.IReceiver() {
        public void handle_event_key(KeyValue.Event event) {}
        public void set_shift_state(boolean enabled, boolean lock) {}
        public void set_compose_pending(boolean pending) {}
        public void selection_state_changed(boolean selected) {}
        public InputConnection getCurrentInputConnection() { return connection; }
        public Handler getHandler() { return new Handler(Looper.getMainLooper()); }
        public void language_changed() {}
        public boolean isSinhalaLayout() { return true; }
        public void set_suggestions(Suggestions suggestions) {}
      };
      KeyEventHandler handler = new KeyEventHandler(config, receiver, new Suggestions(receiver, config));
      handler.started();
      handler.key_up(KeyValue.makeCharKey('ක'), Pointers.Modifiers.EMPTY);
      assertEquals("ක!", editor.getText().toString());
      handler.key_up(KeyValue.makeCharKey('ෙ'), Pointers.Modifiers.EMPTY);
      assertEquals("කෙ!", editor.getText().toString());
      handler.send_text("ේ");
      assertEquals("කේ!", editor.getText().toString());
      handler.send_text("්");
      handler.send_text("ක");
      assertEquals("ක්ක!", editor.getText().toString());
      handler.handle_backspace();
      assertEquals("ක!", editor.getText().toString());
      assertEquals(1, editor.getSelectionStart());
    });
  }
}
