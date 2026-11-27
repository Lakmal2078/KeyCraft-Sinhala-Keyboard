package com.lakmal.keycraftsinhala;

import android.os.Build.VERSION;
import android.os.Handler;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.SurroundingText;

/** Keep track of the word being typed. This also tracks whether the selection
    is empty. */
public final class CurrentlyTypedWord
{
  InputConnection _ic = null;
  Handler _handler;
  Callback _callback;

  /** The currently typed word. */
  StringBuilder _w = new StringBuilder();
  /** This can be disabled if the editor doesn't support looking at the text
      before the cursor. */
  boolean _enabled = false;
  /** The current word is empty while the selection is ongoing. */
  boolean _has_selection = false;
  /** Whether an editor key event is awaiting a delayed refresh. */
  boolean _refresh_pending = false;

  /** The estimated cursor position in UTF-16 units. Used to avoid expensive IPC
      calls when the typed word can be estimated locally with [typed]. When the
      cursor position gets out of sync, the text before the cursor is queried
      again to the editor. */
  int _cursor;
  /** The cursor position within the current word relative to the end of the
      word in chars. Equal to [0] when the cursor is at the end of the word. */
  int _w_cursor;

  public CurrentlyTypedWord(Handler h, Callback cb)
  {
    _handler = h;
    _callback = cb;
  }

  public String get()
  {
    return _w.toString();
  }

  public boolean is_selection_not_empty()
  {
    return _has_selection;
  }

  /** The cursor position relative to the end of the word. */
  public int cursor_relative()
  {
    return _w_cursor;
  }

  public void started(Config conf, InputConnection ic)
  {
    _ic = ic;
    EditorConfig e = conf.editor_config;
    _enabled = !e.is_sensitive_input;
    _refresh_pending = false;
    _handler.removeCallbacks(delayed_refresh_run);
    _w.setLength(0);
    _has_selection = e.initial_sel_start != e.initial_sel_end;
    _cursor = e.initial_sel_start;
    _w_cursor = 0;
    if (_enabled && !_has_selection && e.initial_text_before_cursor != null)
      set_current_word(e.initial_text_before_cursor, e.initial_text_after_cursor);
  }

  public void typed(String s)
  {
    if (!_enabled)
      return;
    _has_selection = false;
    type_chars(s);
    callback();
  }

  public void selection_updated(int newSelStart, int newSelEnd)
  {
    boolean selected = newSelStart != newSelEnd;
    if (!_enabled)
    {
      _has_selection = selected;
      _cursor = newSelStart;
      return;
    }
    if (selected || _has_selection || newSelStart != _cursor || _refresh_pending)
    {
      _cursor = newSelStart;
      _has_selection = selected;
      refresh_current_word();
    }
  }

  void delayed_refresh()
  {
    // Editor key events can be asynchronous. A selection update can refresh
    // earlier, while the timer also covers editors that never send one.
    if (_enabled && !_refresh_pending)
    {
      _refresh_pending = true;
      _handler.postDelayed(delayed_refresh_run, 50);
    }
  }

  void backspace_deleted(int unitsBefore)
  {
    if (!_enabled)
      return;
    if (_cursor >= 0)
      _cursor = Math.max(0, _cursor - unitsBefore);
    refresh_current_word();
  }

  public void remove_surrounding_text(int remove_before, int remove_after)
  {
    if (!_enabled)
      return;
    int len = _w.length();
    int c = len + _w_cursor;
    _w.delete(Math.max(c - remove_before, 0), Math.max(Math.min(c + remove_after, len), 0));
    _cursor -= remove_before;
    _w_cursor += Math.min(remove_after, Math.max(0, -_w_cursor));
    callback();
  }

  void callback()
  {
    String w = _w.toString();
    _callback.currently_typed_word(w);
  }

  /** Estimate the currently typed word after [chars] has been typed. */
  void type_chars(CharSequence s, int start, int end)
  {
    int insert_start = 0;
    // Classify code points but keep editor offsets in UTF-16 units.
    for (int i = start; i < end;)
    {
      int c = Character.codePointAt(s, i);
      int units = Character.charCount(c);
      i += units;
      if (_cursor >= 0)
        _cursor += units;
      // [i >= end] might happen when the cursor is in the middle of a
      // surrogate pair
      if (!is_word_char(c) && i <= end)
        insert_start = i;
    }
    if (insert_start > 0)
    {
      _w.setLength(0);
      _w_cursor = 0;
    }
    _w.insert(Math.max(_w.length() + _w_cursor, 0), s, insert_start, end);
  }

  void type_chars(CharSequence s)
  {
    type_chars(s, 0, s.length());
  }

  /** Append chars to the current word without moving the cursor. Return the
      number of characters that were added in the current word. */
  int append_chars(CharSequence s, int start, int end)
  {
    int i = start;
    while (i < end)
    {
      int c = Character.codePointAt(s, i);
      if (!is_word_char(c))
        break;
      _w.appendCodePoint(c);
      i += Character.charCount(c);
    }
    return i - start;
  }

  int append_chars(CharSequence s)
  {
    return append_chars(s, 0, s.length());
  }

  /** Refresh the current word using the editor's post-edit context. */
  void refresh_current_word()
  {
    if (!_enabled)
      return;
    _refresh_pending = false;
    _handler.removeCallbacks(delayed_refresh_run);
    if (_ic != null && VERSION.SDK_INT >= 31 && refresh_surrounding_text())
      return;
    CharSequence before = _has_selection || _ic == null ? null : _ic.getTextBeforeCursor(64, 0);
    CharSequence after = before == null ? null : _ic.getTextAfterCursor(64, 0);
    set_current_word(before, after);
  }

  @androidx.annotation.RequiresApi(31)
  private boolean refresh_surrounding_text()
  {
    SurroundingText surrounding = _ic.getSurroundingText(64, 64, 0);
    if (surrounding == null)
      return false;
    CharSequence text = surrounding.getText();
    int start = surrounding.getSelectionStart();
    int end = surrounding.getSelectionEnd();
    if (start < 0 || end < start || end > text.length())
      return false;

    _has_selection = start != end;
    if (surrounding.getOffset() >= 0)
      _cursor = surrounding.getOffset() + start;
    set_current_word(_has_selection ? null : text.subSequence(0, start),
        _has_selection ? null : text.subSequence(end, text.length()));
    return true;
  }

  private void set_current_word(CharSequence before, CharSequence after)
  {
    _w.setLength(0);
    _w_cursor = 0;
    if (before != null)
    {
      int saved_cursor = _cursor;
      type_chars(before);
      _cursor = saved_cursor;
      if (after != null)
        _w_cursor = -append_chars(after);
    }
    callback();
  }

  Runnable delayed_refresh_run = new Runnable()
  {
    public void run()
    {
      if (_refresh_pending)
        refresh_current_word();
    }
  };

  /** A word is the longest consecutive sequence for which [is_word_char]
      returns [true]. */
  public static boolean is_word_char(int c)
  {
    int type = Character.getType(c);
    return Character.isLetterOrDigit(c)
        || type == Character.NON_SPACING_MARK
        || type == Character.COMBINING_SPACING_MARK
        || type == Character.ENCLOSING_MARK
        || c == 0x200D || c == 0x200C
        || (c == '\'') || (c == '/')
        || (c == '+') || (c == '-') || (c == '*') || (c == '%') || (c == '^')
        || (c == '×') || (c == '÷') || (c == '=') || (c == '.')
        || (c == '(') || (c == ')') || (c == ',');
  }

  public static interface Callback
  {
    public void currently_typed_word(String word);
  }
}
