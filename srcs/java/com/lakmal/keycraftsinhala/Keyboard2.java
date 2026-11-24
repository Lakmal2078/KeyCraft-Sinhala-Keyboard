package com.lakmal.keycraftsinhala;

import android.app.AlertDialog;
import android.content.Context;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.inputmethodservice.InputMethodService;
import android.os.Build.VERSION;
import android.os.Handler;
import android.os.IBinder;
import android.text.InputType;
import android.util.Log;
import android.util.LogPrinter;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import juloo.cdict.Cdict;
import com.lakmal.keycraftsinhala.dict.Dictionaries;
import com.lakmal.keycraftsinhala.dict.DictionariesActivity;
import com.lakmal.keycraftsinhala.dict.DictionarySwitcher;
import com.lakmal.keycraftsinhala.prefs.LayoutsPreference;
import com.lakmal.keycraftsinhala.suggestions.CandidatesView;
import com.lakmal.keycraftsinhala.suggestions.Suggestions;

public class Keyboard2 extends InputMethodService
  implements SharedPreferences.OnSharedPreferenceChangeListener
{
  /** The view containing the keyboard and candidates view. */
  private ViewGroup _keyboard_container_view;
  private ViewGroup _keyboard_window_container;
  private ViewGroup _keyboard_body_row;
  private View _one_hand_side_rail;
  private View _floating_handle;
  private Keyboard2View _keyboard_layout_view;
  private CandidatesView _candidates_view;
  private ViewGroup _quick_toolbar;
  private LinearLayout _top_strip_container;
  private boolean _has_active_suggestions = false;
  private boolean _show_quick_toolbar_override = false;
  private Suggestions _suggestions;
  private KeyEventHandler _keyeventhandler;
  /** If not 'null', the layout to use instead of [_config.current_layout]. */
  private KeyboardData _currentSpecialLayout;
  /** Layout associated with the currently selected locale. Not 'null'. */
  private KeyboardData _localeTextLayout;
  /** Installed and current locales. */
  private Dictionaries _dictionaries;
  private ViewGroup _emojiPane = null;
  private ViewGroup _clipboard_pane = null;
  private ViewGroup _text_edit_pane = null;
  private boolean _text_edit_select_active = false;
  private Handler _handler;
  private float _dragStartRawX;
  private float _dragStartRawY;
  private float _dragBaseTransX;
  private float _dragBaseTransY;

  private Config _config;
  /** Last configuration used to derive orientation- and window-dependent keyboard metrics. */
  private Configuration _last_configuration;

  private FoldStateTracker _foldStateTracker;

  /** Layout currently visible before it has been modified. */
  KeyboardData current_layout_unmodified()
  {
    if (_currentSpecialLayout != null)
      return _currentSpecialLayout;
    KeyboardData layout = null;
    int layout_i = _config.get_current_layout();
    if (layout_i >= _config.layouts.size())
      layout_i = 0;
    if (layout_i < _config.layouts.size())
      layout = _config.layouts.get(layout_i);
    if (layout == null)
      layout = _localeTextLayout;
    if (layout != null && "sinhala".equals(layout.script) && !_config.sinhala_transliteration)
      return loadLayout(R.xml.latn_qwerty_us);
    return layout;
  }

  /** Layout currently visible. */
  KeyboardData current_layout()
  {
    if (_currentSpecialLayout != null)
      return _currentSpecialLayout;
    return LayoutModifier.modify_layout(current_layout_unmodified());
  }

  void setTextLayout(int l)
  {
    _config.set_current_layout(l);
    _currentSpecialLayout = null;
    // The active dictionary depends on the current layout.
    refresh_current_dictionary();
    refresh_candidates_view();
    _keyboard_layout_view.setKeyboard(current_layout());
  }

  void incrTextLayout(int delta)
  {
    if (_config == null || _config.layouts == null || _config.layouts.isEmpty())
      return;
    int s = _config.layouts.size();
    setTextLayout(((_config.get_current_layout() + delta) % s + s) % s);
  }

  void setSpecialLayout(KeyboardData l)
  {
    _currentSpecialLayout = l;
    _keyboard_layout_view.setKeyboard(l);
  }

  KeyboardData loadLayout(int layout_id)
  {
    return KeyboardData.load(getResources(), layout_id);
  }

  /** Load a layout that contains a numpad. */
  KeyboardData loadNumpad(int layout_id)
  {
    return LayoutModifier.modify_numpad(KeyboardData.load(getResources(), layout_id),
        current_layout_unmodified());
  }

  KeyboardData loadNumericLayout()
  {
    return loadNumpad(_config.orientation_landscape ?
        R.xml.numeric_landscape : R.xml.numeric);
  }

  KeyboardData loadPinentry(int layout_id)
  {
    return LayoutModifier.modify_pinentry(KeyboardData.load(getResources(), layout_id),
        current_layout_unmodified());
  }

  @Override
  public void onCreate()
  {
    super.onCreate();
    SharedPreferences prefs = DirectBootAwarePreferences.get_shared_preferences(this);
    _handler = new Handler(getMainLooper());
    _foldStateTracker = new FoldStateTracker(this);
    _dictionaries = Dictionaries.instance(this);
    Config.initGlobalConfig(prefs, getResources(),
        _foldStateTracker.isUnfolded(), _dictionaries);
    _config = Config.globalConfig();
    _last_configuration = new Configuration(getResources().getConfiguration());
    Receiver recvr = this.new Receiver();
    _suggestions = new Suggestions(recvr, _config);
    _keyeventhandler = new KeyEventHandler(_config, recvr, _suggestions, this);
    KeyValue.Stateful._handler = recvr;
    _config.handler = _keyeventhandler;
    prefs.registerOnSharedPreferenceChangeListener(this);
    Logs.configure(this, getResources().getBoolean(R.bool.debug_logs));
    refreshSubtypeImm();
    create_keyboard_view();
    ClipboardHistoryService.on_startup(this, _keyeventhandler);
    _foldStateTracker.setChangedCallback(() -> { refresh_config_and_layout(); });
  }

  @Override
  public void onDestroy() {
    super.onDestroy();

    _foldStateTracker.close();
    try
    {
      SharedPreferences prefs = DirectBootAwarePreferences.get_shared_preferences(this);
      prefs.unregisterOnSharedPreferenceChangeListener(this);
    }
    catch (Exception e) {}
    KeyValue.Stateful._handler = null;
    ClipboardHistoryService.on_destroy();
  }

  private void create_keyboard_view()
  {
    _keyboard_container_view = (ViewGroup)inflate_view(R.layout.keyboard);
    _keyboard_window_container = (ViewGroup)_keyboard_container_view.findViewById(R.id.keyboard_window_container);
    _keyboard_body_row = (ViewGroup)_keyboard_container_view.findViewById(R.id.keyboard_body_row);
    _one_hand_side_rail = _keyboard_container_view.findViewById(R.id.one_hand_side_rail);
    _floating_handle = _keyboard_container_view.findViewById(R.id.keyboard_floating_handle);
    _keyboard_layout_view = (Keyboard2View)_keyboard_container_view.findViewById(R.id.keyboard_view);
    _candidates_view = (CandidatesView)_keyboard_container_view.findViewById(R.id.candidates_view);
    _quick_toolbar = (ViewGroup)_keyboard_container_view.findViewById(R.id.quick_toolbar);
    _top_strip_container = (LinearLayout)_keyboard_container_view.findViewById(R.id.top_strip_container);
    setup_quick_toolbar();
    setup_window_mode_controls();
    apply_window_mode_layout();
    View candidatesToolbarToggle = _keyboard_container_view.findViewById(R.id.candidates_toolbar_toggle);
    if (candidatesToolbarToggle != null)
      candidatesToolbarToggle.setOnClickListener(new View.OnClickListener()
      {
        @Override
        public void onClick(View v)
        {
          _show_quick_toolbar_override = true;
          update_top_bar_visibility();
        }
      });
  }

  private void setup_window_mode_controls()
  {
    if (_one_hand_side_rail != null)
    {
      View expandBtn = _one_hand_side_rail.findViewById(R.id.one_hand_expand_btn);
      if (expandBtn != null)
        expandBtn.setOnClickListener(v -> updatePrefString("one_hand_mode", "off"));

      View flipBtn = _one_hand_side_rail.findViewById(R.id.one_hand_flip_btn);
      if (flipBtn != null)
        flipBtn.setOnClickListener(v -> {
          String next = "left".equals(_config.one_hand_mode) ? "right" : "left";
          updatePrefString("one_hand_mode", next);
        });

      View floatBtn = _one_hand_side_rail.findViewById(R.id.one_hand_float_btn);
      if (floatBtn != null)
        floatBtn.setOnClickListener(v -> {
          SharedPreferences prefs = DirectBootAwarePreferences.get_shared_preferences(Keyboard2.this);
          prefs.edit()
              .putString("one_hand_mode", "off")
              .putBoolean("floating_keyboard", true)
              .apply();
        });
    }

    if (_floating_handle != null)
    {
      View dockBtn = _floating_handle.findViewById(R.id.keyboard_floating_dock_btn);
      if (dockBtn != null)
        dockBtn.setOnClickListener(v -> {
          SharedPreferences prefs = DirectBootAwarePreferences.get_shared_preferences(Keyboard2.this);
          prefs.edit().putBoolean("floating_keyboard", false).apply();
        });

      _floating_handle.setOnTouchListener((v, event) -> {
        if (_keyboard_window_container == null)
          return false;
        switch (event.getActionMasked())
        {
          case MotionEvent.ACTION_DOWN:
            _dragStartRawX = event.getRawX();
            _dragStartRawY = event.getRawY();
            _dragBaseTransX = _keyboard_window_container.getTranslationX();
            _dragBaseTransY = _keyboard_window_container.getTranslationY();
            return true;
          case MotionEvent.ACTION_MOVE:
            float dx = event.getRawX() - _dragStartRawX;
            float dy = event.getRawY() - _dragStartRawY;
            int screenW = getResources().getDisplayMetrics().widthPixels;
            int maxX = Math.max(0, (screenW - _keyboard_window_container.getWidth()) / 2);
            float clampedX = Math.max(-maxX, Math.min(maxX, _dragBaseTransX + dx));
            float clampedY = Math.max(-600f, Math.min(0f, _dragBaseTransY + dy));
            _keyboard_window_container.setTranslationX(clampedX);
            _keyboard_window_container.setTranslationY(clampedY);
            return true;
          case MotionEvent.ACTION_UP:
          case MotionEvent.ACTION_CANCEL:
            SharedPreferences prefs = DirectBootAwarePreferences.get_shared_preferences(Keyboard2.this);
            prefs.edit()
                .putInt("floating_offset_x", Math.round(_keyboard_window_container.getTranslationX()))
                .putInt("floating_offset_y", Math.round(_keyboard_window_container.getTranslationY()))
                .apply();
            if (event.getActionMasked() == MotionEvent.ACTION_UP
                && Math.abs(event.getRawX() - _dragStartRawX) < 10f
                && Math.abs(event.getRawY() - _dragStartRawY) < 10f)
              v.performClick();
            return true;
        }
        return false;
      });
    }
  }

  private void updatePrefString(String key, String value)
  {
    SharedPreferences prefs = DirectBootAwarePreferences.get_shared_preferences(this);
    prefs.edit().putString(key, value).apply();
  }

  private void apply_window_mode_layout()
  {
    if (_keyboard_window_container == null || _keyboard_body_row == null || _keyboard_layout_view == null)
      return;
    android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
    boolean floating = _config.floating_keyboard;
    boolean oneHandLeft = !floating && "left".equals(_config.one_hand_mode);
    boolean oneHandRight = !floating && "right".equals(_config.one_hand_mode);
    boolean oneHandActive = oneHandLeft || oneHandRight;

    ViewGroup.LayoutParams winLp = _keyboard_window_container.getLayoutParams();
    if (winLp instanceof FrameLayout.LayoutParams)
    {
      FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) winLp;
      if (floating)
      {
        int targetW = Math.min(Math.round(dm.widthPixels * 0.86f), Math.round(440f * dm.density));
        flp.width = Math.max(Math.round(280f * dm.density), targetW);
        flp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
      }
      else
      {
        flp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        flp.gravity = Gravity.BOTTOM;
      }
      _keyboard_window_container.setLayoutParams(flp);
    }

    _keyboard_window_container.setElevation(floating ? 10f * dm.density : 0f);

    if (floating)
    {
      int maxX = Math.max(0, (dm.widthPixels - Math.round(dm.widthPixels * 0.86f)) / 2);
      _keyboard_window_container.setTranslationX(
          Math.max(-maxX, Math.min(maxX, _config.floating_offset_x)));
      _keyboard_window_container.setTranslationY(
          Math.max(-600f, Math.min(0f, _config.floating_offset_y)));
    }
    else
    {
      _keyboard_window_container.setTranslationX(0f);
      _keyboard_window_container.setTranslationY(0f);
    }

    if (_floating_handle != null)
      _floating_handle.setVisibility(floating ? View.VISIBLE : View.GONE);

    if (_one_hand_side_rail != null)
    {
      _one_hand_side_rail.setVisibility(oneHandActive ? View.VISIBLE : View.GONE);
      _keyboard_body_row.removeAllViews();
      LinearLayout.LayoutParams kbdLp = new LinearLayout.LayoutParams(
          oneHandActive ? 0 : ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.WRAP_CONTENT);
      kbdLp.weight = oneHandActive ? 0.82f : 0f;
      LinearLayout.LayoutParams railLp = new LinearLayout.LayoutParams(
          0, ViewGroup.LayoutParams.MATCH_PARENT);
      railLp.weight = 0.18f;
      if (oneHandRight)
      {
        _keyboard_body_row.addView(_one_hand_side_rail, railLp);
        _keyboard_body_row.addView(_keyboard_layout_view, kbdLp);
      }
      else
      {
        _keyboard_body_row.addView(_keyboard_layout_view, kbdLp);
        _keyboard_body_row.addView(_one_hand_side_rail, railLp);
      }
    }
  }

  private void setup_quick_toolbar()
  {
    if (_quick_toolbar == null)
      return;

    View btnSuggestions = _quick_toolbar.findViewById(R.id.toolbar_btn_suggestions);
    if (btnSuggestions != null)
    {
      btnSuggestions.setOnClickListener(v -> {
        _show_quick_toolbar_override = false;
        update_top_bar_visibility();
      });
    }

    View btnApps = _quick_toolbar.findViewById(R.id.toolbar_btn_apps);
    if (btnApps != null)
    {
      btnApps.setOnClickListener(v -> {
        ensure_emoji_pane();
        setInputView(_emojiPane);
      });
      btnApps.setOnLongClickListener(v -> {
        incrTextLayout(1);
        return true;
      });
    }

    View btnEdit = _quick_toolbar.findViewById(R.id.toolbar_btn_edit);
    if (btnEdit != null)
    {
      btnEdit.setOnClickListener(v -> showTextEditPane());
    }

    View btnOneHand = _quick_toolbar.findViewById(R.id.toolbar_btn_one_hand);
    if (btnOneHand != null)
    {
      btnOneHand.setOnClickListener(v -> {
        SharedPreferences prefs = DirectBootAwarePreferences.get_shared_preferences(Keyboard2.this);
        if (_config.floating_keyboard)
        {
          prefs.edit()
              .putBoolean("floating_keyboard", false)
              .putString("one_hand_mode", "right")
              .apply();
        }
        else
        {
          String next = "off".equals(_config.one_hand_mode) ? "right" : "off";
          prefs.edit().putString("one_hand_mode", next).apply();
        }
      });
      btnOneHand.setOnLongClickListener(v -> {
        SharedPreferences prefs = DirectBootAwarePreferences.get_shared_preferences(Keyboard2.this);
        prefs.edit()
            .putString("one_hand_mode", "off")
            .putBoolean("floating_keyboard", !_config.floating_keyboard)
            .apply();
        return true;
      });
    }

    View btnKeyboard = _quick_toolbar.findViewById(R.id.toolbar_btn_keyboard);
    if (btnKeyboard != null)
    {
      btnKeyboard.setOnClickListener(v -> {
        InputMethodManager imm = get_imm();
        if (imm != null)
          imm.showInputMethodPicker();
      });
      btnKeyboard.setOnLongClickListener(v -> {
        incrTextLayout(1);
        return true;
      });
    }

    View btnTranslate = _quick_toolbar.findViewById(R.id.toolbar_btn_translate);
    if (btnTranslate != null)
    {
      btnTranslate.setOnClickListener(v -> launch_translate());
      btnTranslate.setOnLongClickListener(v -> {
        try
        {
          SharedPreferences prefs = DirectBootAwarePreferences.get_shared_preferences(Keyboard2.this);
          boolean nextState = !_config.sinhala_transliteration;
          prefs.edit().putBoolean("sinhala_transliteration", nextState).apply();
        }
        catch (Exception e) {}
        return true;
      });
    }

    View btnSettings = _quick_toolbar.findViewById(R.id.toolbar_btn_settings);
    if (btnSettings != null)
    {
      btnSettings.setOnClickListener(v -> {
        Intent intent = new Intent(Keyboard2.this, SettingsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
      });
    }

    View btnClipboard = _quick_toolbar.findViewById(R.id.toolbar_btn_clipboard);
    if (btnClipboard != null)
    {
      btnClipboard.setOnClickListener(v -> showClipboardPane());
    }

    View btnTheme = _quick_toolbar.findViewById(R.id.toolbar_btn_theme);
    if (btnTheme != null)
    {
      btnTheme.setOnClickListener(v -> show_theme_picker());
      btnTheme.setOnLongClickListener(v -> {
        Intent intent = new Intent(Keyboard2.this, SettingsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        return true;
      });
    }

    int[] btnIds = new int[] {
      R.id.toolbar_btn_suggestions, R.id.toolbar_btn_apps, R.id.toolbar_btn_edit,
      R.id.toolbar_btn_one_hand, R.id.toolbar_btn_translate, R.id.toolbar_btn_clipboard,
      R.id.toolbar_btn_theme, R.id.toolbar_btn_keyboard, R.id.toolbar_btn_settings
    };
    if (_config.custom_colors_enabled)
    {
      int iconTint = Theme.getContrastColor(_config.custom_bg_color);
      for (int id : btnIds)
      {
        android.widget.ImageButton btn = _quick_toolbar.findViewById(id);
        if (btn != null)
          btn.setColorFilter(iconTint);
      }
    }
    else
    {
      for (int id : btnIds)
      {
        android.widget.ImageButton btn = _quick_toolbar.findViewById(id);
        if (btn != null)
          btn.clearColorFilter();
      }
    }
  }

  private void showTextEditPane()
  {
    if (_text_edit_pane == null)
    {
      _text_edit_pane = (ViewGroup)inflate_view(R.layout.text_edit_pane);
      wire_text_edit_pane(_text_edit_pane);
    }
    int targetHeight = _keyboard_container_view != null ? _keyboard_container_view.getHeight() : 0;
    if (targetHeight <= 0 && _keyboard_container_view != null)
      targetHeight = _keyboard_container_view.getMeasuredHeight();
    if (targetHeight > 0)
      _text_edit_pane.setMinimumHeight(targetHeight);
    setInputView(_text_edit_pane);
  }

  private void wire_text_edit_pane(ViewGroup pane)
  {
    android.widget.Button selectToggle = pane.findViewById(R.id.text_edit_select_toggle);
    if (selectToggle != null)
    {
      selectToggle.setOnClickListener(v -> {
        _text_edit_select_active = !_text_edit_select_active;
        selectToggle.setBackgroundResource(_text_edit_select_active
            ? R.drawable.suggestions_pill_background
            : R.drawable.suggestions_item_background);
      });
    }
    View backBtn = pane.findViewById(R.id.text_edit_back);
    if (backBtn != null)
      backBtn.setOnClickListener(v -> setInputView(_keyboard_container_view));

    bindEditKey(pane, R.id.text_edit_left, "left", true);
    bindEditKey(pane, R.id.text_edit_right, "right", true);
    bindEditKey(pane, R.id.text_edit_up, "up", true);
    bindEditKey(pane, R.id.text_edit_down, "down", true);
    bindEditKey(pane, R.id.text_edit_home, "home", true);
    bindEditKey(pane, R.id.text_edit_end, "end", true);
    bindEditKey(pane, R.id.text_edit_select_all, "selectAll", false);
    bindEditKey(pane, R.id.text_edit_copy, "copy", false);
    bindEditKey(pane, R.id.text_edit_cut, "cut", false);
    bindEditKey(pane, R.id.text_edit_paste, "paste", false);
    bindEditKey(pane, R.id.text_edit_undo, "undo", false);
    bindEditKey(pane, R.id.text_edit_redo, "redo", false);
    bindEditKey(pane, R.id.text_edit_backspace, "backspace", false);
    bindEditKey(pane, R.id.text_edit_del_word, "delete_word", false);
  }

  private void bindEditKey(ViewGroup pane, int viewId, final String keyName,
      final boolean respectSelectToggle)
  {
    View view = pane.findViewById(viewId);
    if (view == null)
      return;
    view.setOnClickListener(v -> {
      KeyValue kv = KeyValue.getKeyByName(keyName);
      if (kv == null || _keyeventhandler == null)
        return;
      Pointers.Modifiers mods = (respectSelectToggle && _text_edit_select_active)
          ? Pointers.Modifiers.EMPTY.with_extra_mod(KeyValue.SHIFT)
          : Pointers.Modifiers.EMPTY;
      _keyeventhandler.key_down(kv, false);
      _keyeventhandler.key_up(kv, mods);
    });
  }

  /** Commit image/sticker/GIF content to the active editor if accepted. */
  public boolean commitMediaContent(android.net.Uri uri, String mime)
  {
    return ContentCommitter.commit(getCurrentInputConnection(),
        getCurrentInputEditorInfo(), uri, mime);
  }

  /** Inflate the emoji pane and size its grid to the active keyboard viewport. */
  private void ensure_emoji_pane()
  {
    if (_emojiPane == null)
    {
      _emojiPane = (ViewGroup)inflate_view(R.layout.emoji_pane);
      StickerGifHelper.bindEmojiPane(_emojiPane, this);
    }

    resizeEmojiPaneContent();
  }

  void resizeEmojiPaneContent()
  {
    if (_emojiPane == null)
      return;
    View contentFrame = _emojiPane.findViewById(R.id.emoji_content_frame);
    View grid = contentFrame != null ? contentFrame : _emojiPane.findViewById(R.id.emoji_grid);
    if (grid == null)
      return;

    int targetHeight = _keyboard_container_view != null
        ? _keyboard_container_view.getHeight() : 0;
    if (targetHeight <= 0 && _keyboard_container_view != null)
      targetHeight = _keyboard_container_view.getMeasuredHeight();

    int width = _keyboard_container_view != null ? _keyboard_container_view.getWidth() : 0;
    if (width <= 0)
      width = getResources().getDisplayMetrics().widthPixels;

    int fixedHeight = 0;
    int[] fixedIds = new int[] {
      R.id.emoji_mode_bar, R.id.emoji_search_strip,
      R.id.emoji_search_input_row, R.id.emoji_group_bar, R.id.emoji_bottom_row
    };
    for (int id : fixedIds)
    {
      View fixedView = _emojiPane.findViewById(id);
      if (fixedView == null || fixedView.getVisibility() == View.GONE)
        continue;
      fixedView.measure(
          View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
      fixedHeight += fixedView.getMeasuredHeight();
    }

    int gridHeight = targetHeight > fixedHeight
        ? targetHeight - fixedHeight : _config.keyboard_rows_height_pixels * 3;
    ViewGroup.LayoutParams gridParams = grid.getLayoutParams();
    if (gridParams != null)
    {
      gridParams.height = Math.max(0, gridHeight);
      grid.setLayoutParams(gridParams);
    }
  }

  /** Keep the clipboard pane at the exact measured height of the active keyboard. */
  private void showClipboardPane()
  {
    if (_clipboard_pane == null)
      _clipboard_pane = (ViewGroup)inflate_view(R.layout.clipboard_pane);

    int targetHeight = _keyboard_container_view.getHeight();
    if (targetHeight <= 0)
      targetHeight = _keyboard_container_view.getMeasuredHeight();

    View clipboardBottomRow = _clipboard_pane.findViewById(R.id.clipboard_bottom_row);
    View clipboardScroll = _clipboard_pane.findViewById(R.id.clipboard_scroll_view);
    if (targetHeight > 0 && clipboardBottomRow != null && clipboardScroll != null)
    {
      int width = _keyboard_container_view.getWidth();
      if (width <= 0)
        width = getResources().getDisplayMetrics().widthPixels;
      clipboardBottomRow.measure(
          View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));

      ViewGroup.LayoutParams paneParams = _clipboard_pane.getLayoutParams();
      if (paneParams == null)
        paneParams = new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, targetHeight);
      else
        paneParams.height = targetHeight;
      _clipboard_pane.setLayoutParams(paneParams);

      ViewGroup.LayoutParams scrollParams = clipboardScroll.getLayoutParams();
      if (scrollParams != null)
      {
        scrollParams.height = Math.max(0, targetHeight - clipboardBottomRow.getMeasuredHeight());
        clipboardScroll.setLayoutParams(scrollParams);
      }
    }
    ClipboardHistoryView historyView = _clipboard_pane.findViewById(R.id.clipboard_history_view);
    if (historyView != null)
      historyView.refreshEmptyStates();
    setInputView(_clipboard_pane);
  }

  private void launch_translate()
  {
    // Check before reading the selection or constructing an external intent.
    if (_config == null || _config.editor_config.is_sensitive_input)
      return;
    try
    {
      CharSequence selected = null;
      InputConnection ic = getCurrentInputConnection();
      if (ic != null)
        selected = ic.getSelectedText(0);
      Intent intent = getPackageManager().getLaunchIntentForPackage("com.google.android.apps.translate");
      if (intent != null && (selected == null || selected.length() == 0))
      {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        return;
      }
      String query = (selected != null && selected.length() > 0)
          ? android.net.Uri.encode(selected.toString()) : "";
      String url = query.isEmpty()
          ? "https://translate.google.com/"
          : "https://translate.google.com/?sl=auto&tl=si&text=" + query + "&op=translate";
      Intent webIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
      webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      if (!query.isEmpty())
      {
        IBinder token = getConnectionToken();
        if (token == null)
          return;
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(R.string.translate_confirm_title)
            .setMessage(R.string.translate_confirm_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.translate_confirm_action, (d, w) -> {
              try { startActivity(webIntent); }
              catch (ActivityNotFoundException | SecurityException | IllegalArgumentException ex)
              {
                Log.w(Logs.TAG, "Unable to launch translation: " + ex.getClass().getSimpleName());
              }
            })
            .create();
        Utils.show_dialog_on_ime(dialog, token);
        return;
      }
      startActivity(webIntent);
    }
    catch (ActivityNotFoundException | SecurityException | IllegalArgumentException e)
    {
      // Exception messages can include the URL and selected text.
      Log.w(Logs.TAG, "Unable to launch translation: " + e.getClass().getSimpleName());
    }
  }

  private boolean shouldConfirmExternalTranslate()
  {
    if (getBaseContext() == null)
      return false;
    SharedPreferences prefs = DirectBootAwarePreferences.get_shared_preferences(this);
    return prefs == null || !prefs.getBoolean("translate_external_confirmed", false);
  }

  private void markExternalTranslateConfirmed()
  {
    if (getBaseContext() == null)
      return;
    SharedPreferences prefs = DirectBootAwarePreferences.get_shared_preferences(this);
    if (prefs != null)
      prefs.edit().putBoolean("translate_external_confirmed", true).apply();
  }

  private void show_theme_picker()
  {
    try
    {
      SharedPreferences prefs = DirectBootAwarePreferences.get_shared_preferences(this);
      AlertDialog dialog = ThemePicker.create(new AlertDialog.Builder(this), getResources(), prefs);
      Utils.show_dialog_on_ime(dialog, this);
    }
    catch (android.content.res.Resources.NotFoundException | ClassCastException
        | IllegalStateException | SecurityException | WindowManager.BadTokenException e)
    {
      Log.w(Logs.TAG, "Unable to open theme picker: " + e.getClass().getSimpleName());
    }
  }

  InputMethodManager get_imm()
  {
    return (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
  }

  private void refreshSubtypeImm()
  {
    _config.shouldOfferVoiceTyping = true;
    KeyboardData default_layout = null;
    _config.device_locales = DeviceLocales.load(this);
    if (_config.device_locales.default_ != null)
    {
      String layout_name = _config.device_locales.default_.default_layout;
      if (layout_name != null)
        default_layout = LayoutsPreference.layout_of_string(getResources(), layout_name);
    }
    _config.extra_keys_subtype = _config.device_locales.extra_keys();
    if (default_layout == null)
      default_layout = loadLayout(R.xml.latn_qwerty_us);
    _localeTextLayout = default_layout;
  }

  private void refresh_current_dictionary()
  {
    _config.should_show_dictionary_switch =
      (_config.device_locales.installed.size() > 0);
    String dict_name = _dictionaries.get_selected(_config);
    if (dict_name == null)
      dict_name = (_config.device_locales.default_ != null) ?
        _config.device_locales.default_.dictionary : null;
    if (dict_name == null || !_dictionaries.get_installed().contains(dict_name))
      dict_name = "en_US";
    _dictionaries.set_current_dictionary(_config, dict_name);
  }

  /** Remember and apply the dictionary chosen by the user for the current
      context. */
  private void select_dictionary(String dict_name)
  {
    _dictionaries.set_selected(_config, dict_name);
    refresh_current_dictionary();
    refresh_candidates_view();
  }

  private boolean should_show_candidates()
  {
    boolean has_dict = _config.current_dictionary != null;
    boolean has_sinhala = _config.sinhala_transliteration;
    boolean has_productivity = _config.quick_calculator_enabled
        || _config.smart_snippets_enabled
        || _config.clipboard_suggestion_enabled;
    return _config.suggestions_enabled
      && !_config.editor_config.is_sensitive_input
      && (has_dict || has_sinhala || has_productivity)
      && _config.editor_config.should_show_candidates_view;
  }

  private void update_top_bar_visibility()
  {
    boolean can_show_candidates = should_show_candidates();
    boolean show_toolbar = _config.show_quick_toolbar && _quick_toolbar != null;
    boolean has_active_candidates = can_show_candidates && _has_active_suggestions;
    boolean can_side_by_side =
        _config.split_layout || _config.wide_screen || _config.orientation_landscape;

    boolean toolbar_visible;
    boolean candidates_visible;
    if (can_side_by_side)
    {
      toolbar_visible = show_toolbar;
      candidates_visible = can_show_candidates && (!toolbar_visible || _has_active_suggestions);
    }
    else if (show_toolbar)
    {
      boolean showing_suggestions = has_active_candidates && !_show_quick_toolbar_override;
      candidates_visible = showing_suggestions;
      toolbar_visible = !showing_suggestions;
    }
    else
    {
      toolbar_visible = false;
      candidates_visible = can_show_candidates;
    }

    if (_quick_toolbar != null)
    {
      View translate = _quick_toolbar.findViewById(R.id.toolbar_btn_translate);
      if (translate != null)
      {
        boolean enabled = !_config.editor_config.is_sensitive_input;
        translate.setEnabled(enabled);
        translate.setAlpha(enabled ? 1.0f : 0.38f);
      }
      View backToSuggestions = _quick_toolbar.findViewById(R.id.toolbar_btn_suggestions);
      if (backToSuggestions != null)
      {
        boolean showBack = toolbar_visible && !can_side_by_side
            && has_active_candidates && _show_quick_toolbar_override;
        backToSuggestions.setVisibility(showBack ? View.VISIBLE : View.GONE);
      }
      _quick_toolbar.setVisibility(toolbar_visible ? View.VISIBLE : View.GONE);
    }

    if (_candidates_view != null)
    {
      _candidates_view.setVisibility(candidates_visible ? View.VISIBLE : View.GONE);
      View toggle = _candidates_view.findViewById(R.id.candidates_toolbar_toggle);
      if (toggle != null)
      {
        boolean showToggle = candidates_visible && show_toolbar && !can_side_by_side;
        toggle.setVisibility(showToggle ? View.VISIBLE : View.GONE);
      }
    }

    if (_top_strip_container != null && _quick_toolbar != null && _candidates_view != null)
    {
      boolean sideBySide = toolbar_visible && candidates_visible && can_side_by_side;
      int barHeight = Math.round(48f * getResources().getDisplayMetrics().density);
      _top_strip_container.setOrientation(
          sideBySide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);

      LinearLayout.LayoutParams tbLp = new LinearLayout.LayoutParams(
          sideBySide ? 0 : ViewGroup.LayoutParams.MATCH_PARENT, barHeight);
      tbLp.weight = sideBySide ? 1f : 0f;
      _quick_toolbar.setLayoutParams(tbLp);

      LinearLayout.LayoutParams cvLp = new LinearLayout.LayoutParams(
          sideBySide ? 0 : ViewGroup.LayoutParams.MATCH_PARENT, barHeight);
      cvLp.weight = sideBySide ? 1f : 0f;
      _candidates_view.setLayoutParams(cvLp);
    }
  }

  private void refresh_candidates_view()
  {
    _has_active_suggestions = false;
    _show_quick_toolbar_override = false;
    if (should_show_candidates())
    {
      _candidates_view.refresh_config(_config);
      _keyeventhandler.dictionary_changed();
    }
    update_top_bar_visibility();
  }

  /** Whether a configuration change affects keyboard geometry or its system theme. */
  static boolean shouldRefreshKeyboardConfig(Configuration previous, Configuration next,
      boolean themeFollowsSystem)
  {
    if (previous == null || next == null)
      return true;

    boolean geometryChanged =
        previous.orientation != next.orientation
        || previous.screenWidthDp != next.screenWidthDp
        || previous.screenHeightDp != next.screenHeightDp
        || previous.smallestScreenWidthDp != next.smallestScreenWidthDp
        || previous.densityDpi != next.densityDpi
        || previous.screenLayout != next.screenLayout
        || Float.compare(previous.fontScale, next.fontScale) != 0;

    boolean nightModeChanged = (previous.uiMode & Configuration.UI_MODE_NIGHT_MASK)
        != (next.uiMode & Configuration.UI_MODE_NIGHT_MASK);
    return geometryChanged || (themeFollowsSystem && nightModeChanged);
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig)
  {
    super.onConfigurationChanged(newConfig);
    if (_config == null || newConfig == null) return;

    Configuration previous = _last_configuration;
    _last_configuration = new Configuration(newConfig);
    if (shouldRefreshKeyboardConfig(previous, newConfig, _config.theme_follows_system))
      refresh_config_and_layout();
  }

  /** Might re-create the keyboard view. [_keyboard_layout_view.setKeyboard()] and
      [setInputView()] must be called soon after. */
  private void refresh_config()
  {
    int prev_theme = _config.theme;
    boolean prev_custom_colors = _config.custom_colors_enabled;
    boolean prev_clipboard_history_enabled = _config.clipboard_history_enabled;
    int prev_custom_bg = _config.custom_bg_color;
    int prev_custom_accent = _config.custom_accent_color;

    _config.refresh(getResources(), _foldStateTracker.isUnfolded(), _dictionaries);
    boolean clipboardHistoryChanged =
        prev_clipboard_history_enabled != _config.clipboard_history_enabled;
    if (prev_clipboard_history_enabled && !_config.clipboard_history_enabled)
      ClipboardHistoryService.clear_history_if_disabled();
    refresh_current_dictionary();
    // Refreshing the theme config or custom colors requires re-creating the views
    if (prev_theme != _config.theme
        || prev_custom_colors != _config.custom_colors_enabled
        || prev_custom_bg != _config.custom_bg_color
        || prev_custom_accent != _config.custom_accent_color)
    {
      create_keyboard_view();
      _emojiPane = null;
      _clipboard_pane = null;
      _text_edit_pane = null;
      setInputView(_keyboard_container_view);
    }
    else
    {
      apply_window_mode_layout();
    }
    View bgTarget = _keyboard_window_container != null ? _keyboard_window_container : _keyboard_container_view;
    if (_config.custom_colors_enabled)
    {
      bgTarget.setBackgroundColor(_config.custom_bg_color);
      if (bgTarget.getBackground() != null)
      {
        Drawable bg = bgTarget.getBackground().mutate();
        bg.setAlpha(_config.keyboardOpacity);
        bgTarget.setBackground(bg);
      }
    }
    else if (bgTarget.getBackground() != null)
    {
      Drawable bg = bgTarget.getBackground().mutate();
      bg.setAlpha(_config.keyboardOpacity);
      bgTarget.setBackground(bg);
    }
    if (clipboardHistoryChanged && _clipboard_pane != null)
    {
      ClipboardHistoryCheckBox checkbox =
          _clipboard_pane.findViewById(R.id.clipboard_history_checkbox);
      if (checkbox != null)
        checkbox.refreshFromConfig();
      ClipboardHistoryView historyView =
          _clipboard_pane.findViewById(R.id.clipboard_history_view);
      if (historyView != null)
        historyView.refreshEmptyStates();
    }
    _keyboard_layout_view.reset();
    refresh_candidates_view();
  }

  /** Recompute config, select the layout for the current window/editor, and refresh key handling. */
  private void refresh_config_and_layout()
  {
    refresh_config();
    _currentSpecialLayout = refresh_special_layout();
    if (_keyboard_layout_view != null)
      _keyboard_layout_view.setKeyboard(current_layout());
    if (_keyeventhandler != null)
      _keyeventhandler.started();
  }

  private KeyboardData refresh_special_layout()
  {
    if (_config.editor_config.numeric_layout)
    {
      switch (_config.selected_number_layout)
      {
        case PIN:
          return loadPinentry(_config.orientation_landscape ?
              R.xml.pin_landscape : R.xml.pin);
        case NUMBER:
          return loadNumericLayout();
      }
    }
    return null;
  }

  @Override
  public void onStartInputView(EditorInfo info, boolean restarting)
  {
    _config.editor_config.refresh(info, getResources());
    refresh_config_and_layout();
    setInputView(_keyboard_container_view);
    Logs.debug_startup_input_view(info, _config);
  }

  @Override
  public void setExtractViewShown(boolean shown){
      super.setExtractViewShown(false);
  }

  @Override
  public void setInputView(View v)
  {
    ViewParent parent = v.getParent();
    if (parent != null && parent instanceof ViewGroup)
      ((ViewGroup)parent).removeView(v);
    super.setInputView(v);
    updateSoftInputWindowLayoutParams();
    v.requestApplyInsets();
  }

  @Override
  public void updateFullscreenMode() {
    super.updateFullscreenMode();
    updateSoftInputWindowLayoutParams();
  }

  private void updateSoftInputWindowLayoutParams() {
    final android.app.Dialog dialog = getWindow();
    final Window window = (dialog != null) ? dialog.getWindow() : null;
    if (window == null)
      return;
    // On API >= 35, Keyboard2View behaves as edge-to-edge
    // APIs 30 to 34 have visual artifact when edge-to-edge is enabled
    if (VERSION.SDK_INT >= 35)
    {
      WindowManager.LayoutParams wattrs = window.getAttributes();
      if (wattrs != null)
      {
        wattrs.layoutInDisplayCutoutMode =
          WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        // Allow to draw behind system bars
        wattrs.setFitInsetsTypes(0);
        window.setAttributes(wattrs);
      }
      window.setDecorFitsSystemWindows(false);
    }
    updateLayoutHeightOf(window, ViewGroup.LayoutParams.MATCH_PARENT);
    final View inputArea = window.findViewById(android.R.id.inputArea);
    if (inputArea == null)
      return;
    final ViewParent parent = inputArea.getParent();
    if (!(parent instanceof View))
      return;
    final View inputAreaParent = (View) parent;

    updateLayoutHeightOf(
            inputAreaParent,
            isFullscreenMode()
                    ? ViewGroup.LayoutParams.MATCH_PARENT
                    : ViewGroup.LayoutParams.WRAP_CONTENT);
    updateLayoutGravityOf(inputAreaParent, Gravity.BOTTOM);
  }

  private static void updateLayoutHeightOf(final Window window, final int layoutHeight) {
    if (window == null)
      return;
    final WindowManager.LayoutParams params = window.getAttributes();
    if (params != null && params.height != layoutHeight) {
      params.height = layoutHeight;
      window.setAttributes(params);
    }
  }

  private static void updateLayoutHeightOf(final View view, final int layoutHeight) {
    if (view == null)
      return;
    final ViewGroup.LayoutParams params = view.getLayoutParams();
    if (params != null && params.height != layoutHeight) {
      params.height = layoutHeight;
      view.setLayoutParams(params);
    }
  }

  private static void updateLayoutGravityOf(final View view, final int layoutGravity) {
    if (view == null)
      return;
    final ViewGroup.LayoutParams lp = view.getLayoutParams();
    if (lp instanceof LinearLayout.LayoutParams) {
      final LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) lp;
      if (params.gravity != layoutGravity) {
        params.gravity = layoutGravity;
        view.setLayoutParams(params);
      }
    } else if (lp instanceof FrameLayout.LayoutParams) {
      final FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) lp;
      if (params.gravity != layoutGravity) {
        params.gravity = layoutGravity;
        view.setLayoutParams(params);
      }
    }
  }

  @Override
  public void onCurrentInputMethodSubtypeChanged(InputMethodSubtype subtype)
  {
    refreshSubtypeImm();
    refresh_current_dictionary();
    refresh_candidates_view();
    _keyboard_layout_view.setKeyboard(current_layout());
  }

  @Override
  public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd, int candidatesStart, int candidatesEnd)
  {
    super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
    _keyeventhandler.selection_updated(oldSelStart, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
    if ((oldSelStart == oldSelEnd) != (newSelStart == newSelEnd))
      _keyboard_layout_view.set_selection_state(newSelStart != newSelEnd);
  }

  @Override
  public void onFinishInputView(boolean finishingInput)
  {
    super.onFinishInputView(finishingInput);
    if (_keyeventhandler != null)
      _keyeventhandler.finish_composing();
    _keyboard_layout_view.reset();
  }

  @Override
  public void onFinishInput()
  {
    super.onFinishInput();
    if (_keyeventhandler != null)
      _keyeventhandler.finish_composing();
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences _prefs, String _key)
  {
    refresh_config_and_layout();
  }

  @Override
  public boolean onEvaluateFullscreenMode()
  {
    /* Entirely disable fullscreen mode. */
    return false;
  }

  @Override
  public boolean onEvaluateInputViewShown()
  {
    // Since Android 16, this method returns [false] for unknown reasons.
    if (super.onEvaluateInputViewShown())
      return true;
    if (getResources().getConfiguration().hardKeyboardHidden
        == Configuration.HARDKEYBOARDHIDDEN_NO
        && _config.physical_keyboard_hide)
    {
      Logs.debug("Physical keyboard is present");
      return false;
    }
    return true;
  }

  public void launch_dictionaries_activity()
  {
    start_activity(DictionariesActivity.class);
  }

  /** Called from [onClick] attributes. */
  public void launch_dictionaries_activity(View v)
  {
    launch_dictionaries_activity();
  }

  void start_activity(Class cls)
  {
    Intent intent = new Intent(this, cls);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    startActivity(intent);
  }

  /** Not static */
  public class Receiver implements KeyEventHandler.IReceiver,
         KeyValue.Stateful.Symbol_provider, DictionarySwitcher.Callback
  {
    public void handle_event_key(KeyValue.Event ev)
    {
      switch (ev)
      {
        case CONFIG:
          start_activity(SettingsActivity.class);
          break;

        case SWITCH_TEXT:
          _currentSpecialLayout = null;
          _keyboard_layout_view.setKeyboard(current_layout());
          break;

        case SWITCH_NUMERIC:
          setSpecialLayout(loadNumericLayout());
          break;

        case SWITCH_EMOJI:
          ensure_emoji_pane();
          setInputView(_emojiPane);
          break;

        case SWITCH_CLIPBOARD:
          showClipboardPane();
          break;

        case SWITCH_BACK_EMOJI:
        case SWITCH_BACK_CLIPBOARD:
          setInputView(_keyboard_container_view);
          break;

        case CHANGE_METHOD_PICKER:
        {
          InputMethodManager imm = get_imm();
          if (imm != null)
            imm.showInputMethodPicker();
          break;
        }

        case CHANGE_METHOD_PREV:
          if (VERSION.SDK_INT < 28)
          {
            InputMethodManager imm = get_imm();
            if (imm != null)
              imm.switchToLastInputMethod(getConnectionToken());
          }
          else
            switchToPreviousInputMethod();
          break;

        case CHANGE_METHOD_NEXT:
          if (VERSION.SDK_INT < 28)
          {
            InputMethodManager imm = get_imm();
            if (imm != null)
              imm.switchToNextInputMethod(getConnectionToken(), false);
          }
          else
            switchToNextInputMethod(false);
          break;

        case ACTION:
          InputConnection conn = getCurrentInputConnection();
          if (conn != null)
            conn.performEditorAction(_config.editor_config.actionId);
          break;

        case SWITCH_FORWARD:
          incrTextLayout(1);
          break;

        case SWITCH_BACKWARD:
          incrTextLayout(-1);
          break;

        case SWITCH_GREEKMATH:
          setSpecialLayout(loadNumpad(R.xml.greekmath));
          break;

        case CAPS_LOCK:
          set_shift_state(true, true);
          break;

        case SWITCH_VOICE_TYPING:
        {
          InputMethodManager imm = get_imm();
          if (imm == null || !VoiceImeSwitcher.switch_to_voice_ime(Keyboard2.this, imm,
                Config.globalPrefs()))
            _config.shouldOfferVoiceTyping = false;
          break;
        }

        case SWITCH_VOICE_TYPING_CHOOSER:
        {
          InputMethodManager imm = get_imm();
          if (imm != null)
            VoiceImeSwitcher.choose_voice_ime(Keyboard2.this, imm,
                Config.globalPrefs());
          break;
        }

        case HIDE_SELF:
          Keyboard2.this.requestHideSelf(0);
          break;

        case CHANGE_DICTIONARY:
          new DictionarySwitcher(Keyboard2.this, _dictionaries, this).choose();
          break;
      }
    }

    public void set_shift_state(boolean state, boolean lock)
    {
      _keyboard_layout_view.set_shift_state(state, lock);
    }

    public void set_compose_pending(boolean pending)
    {
      _keyboard_layout_view.set_compose_pending(pending);
    }

    public void selection_state_changed(boolean selection_is_ongoing)
    {
      _keyboard_layout_view.set_selection_state(selection_is_ongoing);
    }

    public InputConnection getCurrentInputConnection()
    {
      return Keyboard2.this.getCurrentInputConnection();
    }

    public Handler getHandler()
    {
      return _handler;
    }

    public void set_suggestions(Suggestions suggestions)
    {
      _has_active_suggestions = (suggestions != null)
          && (suggestions.count > 0 || suggestions.emoji_suggestion != null);
      _show_quick_toolbar_override = false;
      _candidates_view.set_candidates(suggestions);
      update_top_bar_visibility();
    }

    public String provide_stateful_key_symbol(KeyValue.Stateful q)
    {
      switch (q)
      {
        case Complete_first: return _suggestions.suggestions[0];
        case Complete_second: return _suggestions.suggestions[1];
        case Complete_third: return _suggestions.suggestions[2];
        case Complete_emoji: return _suggestions.emoji_suggestion;
        case Toggle_language:
          return _config.sinhala_transliteration ? "සි" : "EN";
      }
      return "";
    }

    @Override public boolean isSinhalaLayout()
    {
      KeyboardData layout = current_layout_unmodified();
      return layout != null && "sinhala".equals(layout.script);
    }

    public void language_changed()
    {
      refresh_current_dictionary();
      refresh_candidates_view();
      if (_keyboard_layout_view != null)
        _keyboard_layout_view.setKeyboard(current_layout());
    }

    public void on_change_dictionary(String dict_name)
    {
      select_dictionary(dict_name);
    }

    public void launch_dictionaries_activity()
    {
      Keyboard2.this.launch_dictionaries_activity();
    }
  }

  private IBinder getConnectionToken()
  {
    android.app.Dialog dialog = getWindow();
    Window window = (dialog != null) ? dialog.getWindow() : null;
    WindowManager.LayoutParams attrs = (window != null) ? window.getAttributes() : null;
    return (attrs != null) ? attrs.token : null;
  }

  private View inflate_view(int layout)
  {
    return View.inflate(new ContextThemeWrapper(this, _config.theme), layout, null);
  }
}
