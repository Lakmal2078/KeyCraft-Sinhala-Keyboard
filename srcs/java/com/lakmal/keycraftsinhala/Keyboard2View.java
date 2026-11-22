package com.lakmal.keycraftsinhala;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Canvas;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Build.VERSION;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.os.Bundle;
import android.widget.PopupWindow;
import android.widget.TextView;
import androidx.customview.widget.ExploreByTouchHelper;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import java.util.Arrays;
import java.util.List;

public class Keyboard2View extends View
  implements View.OnTouchListener, Pointers.IPointerEventHandler
{
  private KeyboardData _keyboard;

  /** The key holding the shift key is used to set shift state from
      autocapitalisation. */
  private KeyboardData.Key _shift_key;

  /** Used to add fake pointers. */
  private KeyboardData.Key _compose_key;

  private Pointers _pointers;

  private Pointers.Modifiers _mods;


  private Config _config;

  private float _keyWidth;
  private float _mainLabelSize;
  private float _subLabelSize;
  private float _marginRight;
  private float _marginLeft;
  private float _marginBottom;
  private int _insets_left = 0;
  private int _insets_right = 0;
  private int _insets_bottom = 0;

  private Theme _theme;
  private Theme.Computed _tc;
  private final KeyboardAccessibilityHelper _accessibilityHelper;
  private int _accessibilityMeasuredWidth = -1;
  private int _accessibilityMeasuredHeight = -1;
  private float _accessibilityKeyWidth = Float.NaN;
  private float _accessibilityRowHeight = Float.NaN;
  private float _accessibilityMarginLeft = Float.NaN;
  private float _accessibilityMarginRight = Float.NaN;
  private float _accessibilityMarginBottom = Float.NaN;
  private static final int ACCESSIBILITY_POINTER_ID = 0x7ffffffe;
  private static final int ACCESSIBILITY_SWIPE_ACTION_BASE = 0x7f0a1000;

  private PopupWindow _previewPopup;
  private TextView _previewText;
  private final int[] _windowLocation = new int[2];
  private final RectF _previewKeyRect = new RectF();

  private static RectF _tmpRect = new RectF();

  enum Vertical
  {
    TOP,
    CENTER,
    BOTTOM
  }

  public static final float TOUCH_MARGIN_TOLERANCE_PX = 8.0f;

  public Keyboard2View(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    _theme = new Theme(getContext(), attrs);
    _config = Config.globalConfig();
    _pointers = new Pointers(this, _config);
    _accessibilityHelper = new KeyboardAccessibilityHelper(this);
    ViewCompat.setAccessibilityDelegate(this, _accessibilityHelper);
    setFocusable(true);
    setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
    refresh_navigation_bar(context);
    setHapticFeedbackEnabled(true);
    setOnTouchListener(this);
    int layout_id = (attrs == null) ? 0 :
      attrs.getAttributeResourceValue(null, "layout", 0);
    if (layout_id == 0)
      reset();
    else
      setKeyboard(KeyboardData.load(getResources(), layout_id));
  }

  private Window getParentWindow(Context context)
  {
    if (context instanceof InputMethodService)
    {
      android.app.Dialog dialog = ((InputMethodService)context).getWindow();
      return (dialog != null) ? dialog.getWindow() : null;
    }
    if (context instanceof ContextWrapper)
      return getParentWindow(((ContextWrapper)context).getBaseContext());
    return null;
  }

  public void refresh_navigation_bar(Context context)
  {
    if (VERSION.SDK_INT < 21)
      return;
    // The intermediate Window is a [Dialog].
    Window w = getParentWindow(context);
    if (w == null)
      return;
    if (VERSION.SDK_INT < 35)
      setLegacyNavigationBarColor(w, _theme.colorNavBar);
    if (VERSION.SDK_INT < 26)
      return;
    WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(w, this);
    if (controller != null)
      controller.setAppearanceLightNavigationBars(_theme.isLightNavBar);
  }

  @SuppressWarnings("deprecation")
  private static void setLegacyNavigationBarColor(Window w, int color)
  {
    w.setNavigationBarColor(color);
  }

  public void setKeyboard(KeyboardData kw)
  {
    _keyboard = kw;
    _accessibilityHelper.invalidateRoot();
    _shift_key = _keyboard.findKeyWithValue(KeyValue.SHIFT);
    _compose_key = _keyboard.findKeyWithValue(KeyValue.COMPOSE);
    KeyModifier.set_modmap(_keyboard.modmap);
    reset();
  }

  public void reset()
  {
    _mods = Pointers.Modifiers.EMPTY;
    _pointers.clear();
    hidePreviewPopup();
    requestLayout();
    invalidate();
  }

  void set_fake_ptr_latched(KeyboardData.Key key, KeyValue kv, boolean latched,
      boolean lock)
  {
    if (_keyboard == null || key == null)
      return;
    _pointers.set_fake_pointer_state(key, kv, latched, lock);
  }

  /** Called by auto-capitalisation. */
  public void set_shift_state(boolean latched, boolean lock)
  {
    set_fake_ptr_latched(_shift_key, KeyValue.SHIFT, latched, lock);
  }

  /** Called from [KeyEventHandler]. */
  public void set_compose_pending(boolean pending)
  {
    set_fake_ptr_latched(_compose_key, KeyValue.COMPOSE, pending, false);
  }

  /** Called from [Keybard2.onUpdateSelection].  */
  public void set_selection_state(boolean selection_state)
  {
    if (_config.editor_config.selection_mode_enabled)
      set_fake_ptr_latched(KeyboardData.Key.EMPTY,
          KeyValue.SELECTION_MODE, selection_state, true);
  }

  public KeyValue modifyKey(KeyValue k, Pointers.Modifiers mods)
  {
    return KeyModifier.modify(k, mods);
  }

  public void onPointerDown(KeyValue k, boolean isSwipe)
  {
    updateFlags();
    _config.handler.key_down(k, isSwipe);
    updatePreviewPopup();
    invalidate();
    vibrate();
    playKeySound();
  }

  private void playKeySound()
  {
    if (_config != null && _config.sound_on_keypress)
    {
      playSoundEffect(android.view.SoundEffectConstants.CLICK);
    }
  }

  public void onPointerUp(KeyValue k, Pointers.Modifiers mods)
  {
    // [key_up] must be called before [updateFlags]. The latter might disable
    // flags.
    _config.handler.key_up(k, mods);
    updateFlags();
    updatePreviewPopup();
    invalidate();
  }

  public void onPointerHold(KeyValue k, Pointers.Modifiers mods)
  {
    _config.handler.key_up(k, mods);
    updateFlags();
  }

  public void onPointerFlagsChanged(boolean shouldVibrate)
  {
    updateFlags();
    updatePreviewPopup();
    invalidate();
    if (shouldVibrate)
      vibrate();
  }

  private void updateFlags()
  {
    _mods = _pointers.getModifiers();
    _config.handler.mods_changed(_mods);
  }

  @Override
  public boolean onTouch(View v, MotionEvent event)
  {
    int p;
    switch (event.getActionMasked())
    {
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_POINTER_UP:
        _pointers.onTouchUp(event.getPointerId(event.getActionIndex()));
        break;
      case MotionEvent.ACTION_DOWN:
      case MotionEvent.ACTION_POINTER_DOWN:
        p = event.getActionIndex();
        float tx = event.getX(p);
        float ty = event.getY(p);
        KeyboardData.Key key = getKeyAtPosition(tx, ty);
        if (key != null)
          _pointers.onTouchDown(tx, ty, event.getPointerId(p), key);
        break;
      case MotionEvent.ACTION_MOVE:
        for (p = 0; p < event.getPointerCount(); p++)
          _pointers.onTouchMove(event.getX(p), event.getY(p), event.getPointerId(p));
        break;
      case MotionEvent.ACTION_CANCEL:
        _pointers.onTouchCancel();
        break;
      default:
        return (false);
    }
    return (true);
  }

  private KeyboardData.Row getRowAtPosition(float ty)
  {
    if (_keyboard == null || _keyboard.rows.isEmpty() || _tc == null)
      return null;

    float maxHeight = getMeasuredHeight();
    float tolY = Math.max(TOUCH_MARGIN_TOLERANCE_PX, _tc.vertical_margin * 0.75f);
    // Vertical tolerance is only for gaps between rows, never for taps outside
    // the measured keyboard surface.
    if (ty < 0 || (maxHeight > 0 && ty >= maxHeight))
      return null;

    float y = _tc.margin_top;
    KeyboardData.Row nearestRow = null;
    float bestDist = Float.MAX_VALUE;
    for (int i = 0; i < _keyboard.rows.size(); i++)
    {
      KeyboardData.Row row = _keyboard.rows.get(i);
      float top = y + row.shift * _tc.row_height;
      float visualBottom = top + row.height * _tc.row_height - _tc.vertical_margin;
      if (ty >= top && ty < visualBottom)
        return row;
      float dist = ty < top ? (top - ty) : (ty - visualBottom);
      if (dist < bestDist)
      {
        bestDist = dist;
        nearestRow = row;
      }
      y = top + row.height * _tc.row_height;
    }
    return bestDist <= tolY ? nearestRow : null;
  }

  KeyboardData.Key getKeyAtPosition(float tx, float ty)
  {
    KeyboardData.Row row = getRowAtPosition(ty);
    if (row == null || row.keys.isEmpty() || _tc == null)
      return null;
    float x = _marginLeft + _tc.margin_left;
    for (int i = 0; i < row.keys.size(); i++)
    {
      KeyboardData.Key key = row.keys.get(i);
      float xLeft = x + key.shift * _keyWidth;
      float visualRight = xLeft + key.width * _keyWidth - _tc.horizontal_margin;
      if (tx >= xLeft && tx < visualRight)
        return key;
      x = xLeft + key.width * _keyWidth;
    }
    // Keep touch hit targets identical to the visible key surfaces. In particular,
    // do not activate a neighbouring key when a pointer lands in an inter-key gap.
    return null;
  }

  private void vibrate()
  {
    VibratorCompat.vibrate(this, _config);
  }

  /** Resolve the keyboard's available width from its parent window, not the full display. */
  static int resolveAvailableWidth(int widthSpecSize, int displayWidth)
  {
    return widthSpecSize > 0 ? widthSpecSize : displayWidth;
  }

  /** Limit row height to the space actually offered by the IME window. */
  static float resolveMaxRowHeight(int availableHeight, float verticalMargins,
      float keysHeight)
  {
    if (availableHeight <= 0 || keysHeight <= 0)
      return Float.POSITIVE_INFINITY;
    return Math.max(1f, (availableHeight - verticalMargins) / keysHeight);
  }

  @Override
  public void onMeasure(int wSpec, int hSpec)
  {
    DisplayMetrics dm = getContext().getResources().getDisplayMetrics();
    int width = resolveAvailableWidth(MeasureSpec.getSize(wSpec), dm.widthPixels);
    _marginLeft = Math.max(_config.horizontal_margin, _insets_left);
    _marginRight = Math.max(_config.horizontal_margin, _insets_right);
    _marginBottom = _config.margin_bottom + _insets_bottom;
    _keyWidth = (width - _marginLeft - _marginRight) / _keyboard.keysWidth;
    int heightMode = MeasureSpec.getMode(hSpec);
    int availableHeight = MeasureSpec.getSize(hSpec);
    float maxRowHeight = (heightMode == MeasureSpec.UNSPECIFIED || availableHeight <= 0)
        ? Float.POSITIVE_INFINITY
        : resolveMaxRowHeight(availableHeight, _config.marginTop + _marginBottom,
            _keyboard.keysHeight);
    _tc = new Theme.Computed(_theme, _config, _keyWidth, _keyboard, maxRowHeight);
    // Compute the size of labels based on the width or the height of keys. The
    // margin around keys is taken into account. Keys normal aspect ratio is
    // assumed to be 3/2 for a 10 columns layout. It's generally more, the
    // width computation is useful when the keyboard is unusually high.
    float labelBaseSize = Math.min(
        _tc.row_height - _tc.vertical_margin,
        (width / 10 - _tc.horizontal_margin) * 3/2
        ) * _config.characterSize;
    _mainLabelSize = labelBaseSize * _config.labelTextSize;
    _subLabelSize = labelBaseSize * _config.sublabelTextSize;
    int height =
      (int)(_tc.row_height * _keyboard.keysHeight
          + _config.marginTop + _marginBottom);
    setMeasuredDimension(resolveSize(width, wSpec), resolveSize(height, hSpec));
    int measuredWidth = getMeasuredWidth();
    int measuredHeight = getMeasuredHeight();
    if (measuredWidth != _accessibilityMeasuredWidth
        || measuredHeight != _accessibilityMeasuredHeight
        || Float.compare(_keyWidth, _accessibilityKeyWidth) != 0
        || Float.compare(_tc.row_height, _accessibilityRowHeight) != 0
        || Float.compare(_marginLeft, _accessibilityMarginLeft) != 0
        || Float.compare(_marginRight, _accessibilityMarginRight) != 0
        || Float.compare(_marginBottom, _accessibilityMarginBottom) != 0)
    {
      _accessibilityMeasuredWidth = measuredWidth;
      _accessibilityMeasuredHeight = measuredHeight;
      _accessibilityKeyWidth = _keyWidth;
      _accessibilityRowHeight = _tc.row_height;
      _accessibilityMarginLeft = _marginLeft;
      _accessibilityMarginRight = _marginRight;
      _accessibilityMarginBottom = _marginBottom;
      _accessibilityHelper.invalidateRoot();
    }
  }

  Rect _cached_exclusion_rect = new Rect();
  List<Rect> _cached_exclusion_rects = Arrays.asList(_cached_exclusion_rect);
  @Override
  public void onLayout(boolean changed, int left, int top, int right, int bottom)
  {
    if (!changed)
      return;
    // Since SDK 30, this is done automatically:
    // https://android.googlesource.com/platform/frameworks/base/+/android11-release/core/java/android/inputmethodservice/InputMethodService.java#852
    if (VERSION.SDK_INT == 29)
    {
      // Disable the back-gesture on the keyboard area
      _cached_exclusion_rect.set(
          left + (int)_marginLeft,
          top + (int)_config.marginTop,
          right - (int)_marginRight,
          bottom - (int)_marginBottom);
      setSystemGestureExclusionRects(_cached_exclusion_rects);
    }
  }

  @Override
  public WindowInsets onApplyWindowInsets(WindowInsets wi)
  {
    // LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS is set in [Keyboard2#updateSoftInputWindowLayoutParams] for SDK_INT >= 35.
    if (VERSION.SDK_INT < 35)
      return wi;
    int insets_types =
      WindowInsets.Type.systemBars()
      | WindowInsets.Type.displayCutout();
    Insets insets = wi.getInsets(insets_types);
    _insets_left = insets.left;
    _insets_right = insets.right;
    _insets_bottom = insets.bottom;
    return WindowInsets.CONSUMED;
  }

  /** Horizontal and vertical position of the 9 indexes. */
  static final Paint.Align[] LABEL_POSITION_H = new Paint.Align[]{
    Paint.Align.CENTER, Paint.Align.LEFT, Paint.Align.RIGHT, Paint.Align.LEFT,
    Paint.Align.RIGHT, Paint.Align.LEFT, Paint.Align.RIGHT,
    Paint.Align.CENTER, Paint.Align.CENTER
  };

  static final Vertical[] LABEL_POSITION_V = new Vertical[]{
    Vertical.CENTER, Vertical.TOP, Vertical.TOP, Vertical.BOTTOM,
    Vertical.BOTTOM, Vertical.CENTER, Vertical.CENTER, Vertical.TOP,
    Vertical.BOTTOM
  };

  @Override
  protected void onDraw(Canvas canvas)
  {
    if (_tc.keyboard_background_paint != null)
    {
      canvas.drawRect(
              0,
              0,
              getWidth(),
              getHeight(),
              _tc.keyboard_background_paint);
    }

    float y = _tc.margin_top;
    for (int rowIndex = 0; rowIndex < _keyboard.rows.size(); rowIndex++)
    {
      KeyboardData.Row row = _keyboard.rows.get(rowIndex);
      y += row.shift * _tc.row_height;
      float x = _marginLeft + _tc.margin_left;
      float keyH = row.height * _tc.row_height - _tc.vertical_margin;
      for (int keyIndex = 0; keyIndex < row.keys.size(); keyIndex++)
      {
        KeyboardData.Key k = row.keys.get(keyIndex);
        x += k.shift * _keyWidth;
        float keyW = _keyWidth * k.width - _tc.horizontal_margin;
        boolean isKeyDown = _pointers.isKeyDown(k);
        int activeSwipeSub = isKeyDown ? _pointers.getActiveSwipeSubIndex(k) : 0;
        Theme.Computed.Key tc_key;
        if (isKeyDown)
          tc_key = _tc.key_activated;
        else
          switch (k.role)
          {
            case Action: tc_key = _tc.key_action; break;
            case Space_bar: tc_key = _tc.key_space_bar; break;
            case Suggestion: tc_key = _tc.key_suggestion; break;
            default:
            case Normal: tc_key = _tc.key; break;
          }
        drawKeyFrame(canvas, x, y, keyW, keyH, tc_key);
        if (k.keys[0] != null)
          drawLabel(canvas, k.keys[0], keyW / 2f + x, y, keyH, isKeyDown, tc_key);
        for (int i = 1; i < 9; i++)
        {
          if (k.keys[i] != null)
            drawSubLabel(canvas, k.keys[i], x, y, keyW, keyH, i, isKeyDown, i == activeSwipeSub, tc_key);
        }
        drawIndication(canvas, k, x, y, keyW, keyH, _tc);
        x += _keyWidth * k.width;
      }
      y += row.height * _tc.row_height;
    }
  }

  /** Number of virtual key nodes exposed to accessibility services. */
  int accessibilityKeyCount()
  {
    if (_keyboard == null)
      return 0;
    int count = 0;
    for (int i = 0; i < _keyboard.rows.size(); i++)
      count += _keyboard.rows.get(i).keys.size();
    return count;
  }

  KeyboardData.Key accessibilityKeyAt(int index)
  {
    if (_keyboard == null || index < 0)
      return null;
    for (int i = 0; i < _keyboard.rows.size(); i++)
    {
      List<KeyboardData.Key> keys = _keyboard.rows.get(i).keys;
      if (index < keys.size())
        return keys.get(index);
      index -= keys.size();
    }
    return null;
  }

  /** Set virtual key bounds in this view's local coordinates. */
  void accessibilityBounds(int keyIndex, Rect out)
  {
    if (_keyboard == null || _tc == null || keyIndex < 0)
    {
      out.setEmpty();
      return;
    }
    int index = 0;
    float y = _tc.margin_top;
    for (int rowIndex = 0; rowIndex < _keyboard.rows.size(); rowIndex++)
    {
      KeyboardData.Row row = _keyboard.rows.get(rowIndex);
      y += row.shift * _tc.row_height;
      float x = _marginLeft + _tc.margin_left;
      float keyH = row.height * _tc.row_height - _tc.vertical_margin;
      for (int keyIndexInRow = 0; keyIndexInRow < row.keys.size(); keyIndexInRow++)
      {
        KeyboardData.Key key = row.keys.get(keyIndexInRow);
        x += key.shift * _keyWidth;
        float keyW = _keyWidth * key.width - _tc.horizontal_margin;
        if (index == keyIndex)
        {
          out.set((int)Math.floor(x), (int)Math.floor(y),
              (int)Math.ceil(x + keyW), (int)Math.ceil(y + keyH));
          return;
        }
        index++;
        x += _keyWidth * key.width;
      }
      y += row.height * _tc.row_height;
    }
    out.setEmpty();
  }

  boolean performAccessibilityClick(int keyIndex)
  {
    KeyboardData.Key key = accessibilityKeyAt(keyIndex);
    if (key == null)
      return false;
    if (key.keys[0] != null)
    {
      Rect bounds = new Rect();
      accessibilityBounds(keyIndex, bounds);
      if (bounds.isEmpty())
        return false;
      float x = (bounds.left + bounds.right) / 2f;
      float y = (bounds.top + bounds.bottom) / 2f;
      _pointers.onTouchDown(x, y, ACCESSIBILITY_POINTER_ID, key);
      _pointers.onTouchUp(ACCESSIBILITY_POINTER_ID);
      return true;
    }
    // Some custom layouts define a key only through a directional/sublabel value.
    // Activate its first defined value instead of exposing an inert virtual button.
    for (int i = 1; i < key.keys.length; i++)
    {
      if (key.keys[i] != null)
      {
        Pointers.Modifiers mods = _mods;
        KeyValue value = modifyKey(key.keys[i], mods);
        if (value == null)
          return false;
        onPointerDown(value, false);
        onPointerUp(value, mods);
        return true;
      }
    }
    return false;
  }

  boolean performAccessibilityLongClick(int keyIndex)
  {
    KeyboardData.Key key = accessibilityKeyAt(keyIndex);
    if (key == null || key.keys[0] == null)
      return false;
    Pointers.Modifiers mods = _mods;
    KeyValue base = modifyKey(key.keys[0], mods);
    if (base == null)
      return false;
    if (base.hasFlagsAny(KeyValue.FLAG_LATCH) && base.hasFlagsAny(KeyValue.FLAG_SPECIAL))
    {
      int currentFlags = _pointers.getKeyFlags(base);
      boolean currentlyLocked = currentFlags != -1 && (currentFlags & Pointers.FLAG_P_LOCKED) != 0;
      _pointers.set_fake_pointer_state(key, base, !currentlyLocked, true);
      return true;
    }
    KeyValue longPress = KeyModifier.modify_long_press(base);
    if (longPress == null || longPress.equals(base))
      return false;
    onPointerDown(longPress, false);
    onPointerUp(longPress, mods);
    return true;
  }

  boolean performAccessibilitySwipe(int keyIndex, int subIndex)
  {
    KeyboardData.Key key = accessibilityKeyAt(keyIndex);
    if (key == null || subIndex <= 0 || subIndex >= key.keys.length || key.keys[subIndex] == null)
      return false;
    Pointers.Modifiers mods = _mods;
    KeyValue value = modifyKey(key.keys[subIndex], mods);
    if (value == null)
      return false;
    onPointerDown(value, true);
    onPointerUp(value, mods);
    return true;
  }

  static String accessibilityLabelForKey(KeyboardData.Key key)
  {
    return accessibilityLabelForKey(key, null);
  }

  static String accessibilityLabelForKey(KeyboardData.Key key, Pointers.Modifiers mods)
  {
    if (key == null)
      return "Keyboard key";
    KeyValue value = key.keys[0];
    if (value == null)
    {
      for (int i = 1; i < key.keys.length; i++)
      {
        if (key.keys[i] != null)
        {
          value = key.keys[i];
          break;
        }
      }
    }
    if (value == null)
      return "Empty key";
    if (mods != null)
    {
      KeyValue modified = KeyModifier.modify(value, mods);
      if (modified != null)
        value = modified;
    }
    return accessibilityLabelForKeyValue(value);
  }

  static String accessibilityLabelForKeyValue(KeyValue value)
  {
    if (value == null)
      return "Empty key";
    switch (value.getKind())
    {
      case Editing:
        switch (value.getEditing())
        {
          case BACKSPACE: return "Backspace";
          case FORWARD_DELETE_WORD: return "Forward delete word";
          case DELETE_WORD: return "Delete word";
          case SPACE_BAR: return "Space";
          case COPY: return "Copy";
          case CUT: return "Cut";
          case PASTE: return "Paste";
          case PASTE_PLAIN: return "Paste as plain text";
          case SELECT_ALL: return "Select all";
          case UNDO: return "Undo";
          case REDO: return "Redo";
          default: return humanize(value.getEditing().name());
        }
      case Modifier:
        switch (value.getModifier())
        {
          case SHIFT: return "Shift";
          case CTRL: return "Control";
          case ALT: return "Alt";
          case META: return "Meta";
          case FN: return "Function";
          default: return humanize(value.getModifier().name());
        }
      case Event:
        switch (value.getEvent())
        {
          case CONFIG: return "Settings";
          case ACTION: return value.getString().isEmpty() ? "Action" : value.getString();
          case HIDE_SELF: return "Hide keyboard";
          case CHANGE_METHOD_PICKER:
          case CHANGE_METHOD_PREV:
          case CHANGE_METHOD_NEXT:
            return "Change keyboard";
          case SWITCH_TEXT:
          case SWITCH_BACK_EMOJI:
            return "Switch to text layout";
          case SWITCH_BACK_CLIPBOARD:
            return "Switch back clipboard";
          case SWITCH_NUMERIC: return "Switch to numeric layout";
          case SWITCH_EMOJI: return "Open emoji keyboard";
          case SWITCH_CLIPBOARD: return "Open clipboard history";
          case SWITCH_FORWARD: return "Next layout";
          case SWITCH_BACKWARD: return "Previous layout";
          case SWITCH_GREEKMATH: return "Greek and math symbols";
          case CAPS_LOCK: return "Caps lock";
          case SWITCH_VOICE_TYPING:
            return "Voice typing";
          case SWITCH_VOICE_TYPING_CHOOSER:
            return "Switch voice typing chooser";
          case CHANGE_DICTIONARY: return "Change language dictionary";
          default: return humanize(value.getEvent().name());
        }
      case Keyevent:
        switch (value.getKeyevent())
        {
          case KeyEvent.KEYCODE_ENTER: return "Enter";
          case KeyEvent.KEYCODE_DPAD_UP: return "Arrow up";
          case KeyEvent.KEYCODE_DPAD_DOWN: return "Arrow down";
          case KeyEvent.KEYCODE_DPAD_LEFT: return "Arrow left";
          case KeyEvent.KEYCODE_DPAD_RIGHT: return "Arrow right";
          case KeyEvent.KEYCODE_PAGE_UP: return "Page up";
          case KeyEvent.KEYCODE_PAGE_DOWN: return "Page down";
          case KeyEvent.KEYCODE_MOVE_HOME: return "Home";
          case KeyEvent.KEYCODE_MOVE_END: return "End";
          case KeyEvent.KEYCODE_FORWARD_DEL: return "Forward delete";
          case KeyEvent.KEYCODE_TAB: return "Tab";
          case KeyEvent.KEYCODE_ESCAPE: return "Escape";
          case KeyEvent.KEYCODE_INSERT: return "Insert";
          default:
            String evLabel = value.getString();
            if (evLabel != null && !evLabel.isEmpty() && !value.hasFlagsAny(KeyValue.FLAG_KEY_FONT))
              return evLabel;
            return humanize(KeyEvent.keyCodeToString(value.getKeyevent()).replace("KEYCODE_", ""));
        }
      case Slider:
        switch (value.getSlider())
        {
          case Cursor_left: return "Move cursor left";
          case Cursor_right: return "Move cursor right";
          case Cursor_up: return "Move cursor up";
          case Cursor_down: return "Move cursor down";
          case Selection_cursor_left: return "Expand selection left";
          case Selection_cursor_right: return "Expand selection right";
          default: return humanize(value.getSlider().name());
        }
      case Compose_pending:
        return "Compose pending";
      case Placeholder:
        switch (value.getPlaceholder())
        {
          case COMPOSE_CANCEL: return "Cancel compose";
          case REMOVED: return "Placeholder";
          default: return humanize(value.getPlaceholder().name());
        }
      case Char:
        switch (value.getChar())
        {
          case '\t': return "Tab";
          case '\n': return "New line";
          case '\u00A0': return "Non-breaking space";
          case '\u202F': return "Narrow non-breaking space";
          case '\u200D': return "Zero width joiner";
          case '\u200C': return "Zero width non-joiner";
          default: break;
        }
        // fall through
      default:
        String label = value.getString();
        return (label == null || label.length() == 0)
            ? humanize(value.getKind().name()) : label;
    }
  }

  private static String humanize(String value)
  {
    String lower = value.replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
    if (lower.length() == 0)
      return "Keyboard key";
    return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
  }

  @Override
  public boolean dispatchHoverEvent(MotionEvent event)
  {
    return _accessibilityHelper.dispatchHoverEvent(event)
        || super.dispatchHoverEvent(event);
  }

  @Override
  public void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect)
  {
    super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
    _accessibilityHelper.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
  }

  @Override
  public void onDetachedFromWindow()
  {
    hidePreviewPopup();
    super.onDetachedFromWindow();
  }

  @Override
  protected void onVisibilityChanged(View changedView, int visibility)
  {
    super.onVisibilityChanged(changedView, visibility);
    if (visibility != View.VISIBLE)
      hidePreviewPopup();
  }

  private void hidePreviewPopup()
  {
    if (_previewPopup != null && _previewPopup.isShowing())
    {
      try {
        _previewPopup.dismiss();
      } catch (Exception ignored) {}
    }
  }

  private boolean shouldShowKeyPreview(KeyValue value)
  {
    if (value == null)
      return false;
    if (value.getKind() == KeyValue.Kind.Slider)
      return false;
    if (value.getKind() == KeyValue.Kind.Editing)
      return false;
    if (value.getKind() == KeyValue.Kind.Event || value.getKind() == KeyValue.Kind.Modifier)
      return false;
    String s = value.getString();
    return s != null && !s.trim().isEmpty();
  }

  private boolean findKeyRect(KeyboardData.Key target, RectF out)
  {
    if (_keyboard == null || _tc == null || target == null)
      return false;
    float y = _tc.margin_top;
    for (int rowIndex = 0; rowIndex < _keyboard.rows.size(); rowIndex++)
    {
      KeyboardData.Row row = _keyboard.rows.get(rowIndex);
      y += row.shift * _tc.row_height;
      float x = _marginLeft + _tc.margin_left;
      float keyH = row.height * _tc.row_height - _tc.vertical_margin;
      for (int keyIndex = 0; keyIndex < row.keys.size(); keyIndex++)
      {
        KeyboardData.Key k = row.keys.get(keyIndex);
        x += k.shift * _keyWidth;
        float keyW = _keyWidth * k.width - _tc.horizontal_margin;
        if (k == target)
        {
          out.set(x, y, x + keyW, y + keyH);
          return true;
        }
        x += _keyWidth * k.width;
      }
      y += row.height * _tc.row_height;
    }
    return false;
  }

  private void updatePreviewPopup()
  {
    if (!isAttachedToWindow() || getWindowToken() == null || getVisibility() != VISIBLE
        || _keyboard == null || _tc == null || getWidth() <= 0 || getHeight() <= 0)
    {
      hidePreviewPopup();
      return;
    }
    Pointers.ActivePreview preview = _pointers.getActivePreview();
    if (preview == null || !shouldShowKeyPreview(preview.value)
        || !findKeyRect(preview.key, _previewKeyRect))
    {
      hidePreviewPopup();
      return;
    }
    String label = preview.value.getString();
    if (label == null || label.isEmpty())
    {
      hidePreviewPopup();
      return;
    }
    DisplayMetrics dm = getResources().getDisplayMetrics();
    int hPad = Math.round(10f * dm.density);
    if (_previewPopup == null)
    {
      _previewText = new TextView(getContext());
      _previewText.setGravity(Gravity.CENTER);
      _previewText.setSingleLine(true);
      _previewText.setPadding(hPad, 0, hPad, 0);
      _previewPopup = new PopupWindow(_previewText, 1, 1, false);
      _previewPopup.setTouchable(false);
      _previewPopup.setClippingEnabled(false);
    }

    int bgColor = _config.custom_colors_enabled ? _config.custom_accent_color : _theme.colorKeyActivated;
    int fgColor = _config.custom_colors_enabled
        ? Theme.getContrastColor(bgColor)
        : (_theme.activatedColor != 0 ? _theme.activatedColor : Theme.getContrastColor(bgColor));
    int borderColor = Theme.blend(bgColor, fgColor, 0.25f);

    GradientDrawable bg = new GradientDrawable();
    bg.setColor(bgColor);
    bg.setCornerRadius(Math.max(10f * dm.density, _tc.key.border_radius * 1.2f));
    bg.setStroke(Math.max(1, (int) (1.5f * dm.density)), borderColor);
    _previewText.setBackground(bg);
    if (VERSION.SDK_INT >= 21)
      _previewText.setElevation(6f * dm.density);
    _previewText.setTextColor(fgColor);
    _previewText.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.max(_mainLabelSize * 1.35f, 22f * dm.density));
    _previewText.setTypeface(preview.value.hasFlagsAny(KeyValue.FLAG_KEY_FONT)
        ? Theme.getKeyFont(getContext())
        : (_config.custom_font != null ? _config.custom_font : Typeface.DEFAULT_BOLD));
    _previewText.setText(label);

    int measuredTextW = (int) Math.ceil(_previewText.getPaint().measureText(label)) + hPad * 2;
    int popupW = Math.max(Math.max((int) (_previewKeyRect.width() * 1.25f), (int) (46 * dm.density)), measuredTextW);
    int popupH = Math.max((int) (_previewKeyRect.height() * 1.15f), (int) (54 * dm.density));

    getLocationInWindow(_windowLocation);
    int px = (int) (_windowLocation[0] + _previewKeyRect.centerX() - popupW / 2f);
    int py = (int) (_windowLocation[1] + _previewKeyRect.top - popupH - 6 * dm.density);
    px = Math.max(4, Math.min(px, dm.widthPixels - popupW - 4));
    if (py < 4)
      py = (int) (_windowLocation[1] + _previewKeyRect.top);

    try {
      if (_previewPopup.isShowing())
        _previewPopup.update(px, py, popupW, popupH);
      else
      {
        _previewPopup.setWidth(popupW);
        _previewPopup.setHeight(popupH);
        _previewPopup.showAtLocation(this, Gravity.NO_GRAVITY, px, py);
      }
    } catch (Exception ignored) {
      hidePreviewPopup();
    }
  }

  /** Draw borders and background of the key. */
  void drawKeyFrame(Canvas canvas, float x, float y, float keyW, float keyH,
      Theme.Computed.Key tc)
  {
    float r = tc.border_radius;
    float w = tc.border_width;
    float padding = w / 2.f;
    _tmpRect.set(x + padding, y + padding, x + keyW - padding, y + keyH - padding);
    canvas.drawRoundRect(_tmpRect, r, r, tc.bg_paint);
    if (w > 0.f && tc.border_left_paint != null)
    {
      if (!tc.has_distinct_borders)
      {
        canvas.drawRoundRect(_tmpRect, r, r, tc.border_left_paint);
      }
      else
      {
        float inset = w + r * 0.5f;
        drawBorder(canvas, _tmpRect.left - w, _tmpRect.top,
            _tmpRect.left + inset, _tmpRect.bottom, tc.border_left_paint, tc);
        drawBorder(canvas, _tmpRect.left, _tmpRect.top - w,
            _tmpRect.right, _tmpRect.top + inset, tc.border_top_paint, tc);
        drawBorder(canvas, _tmpRect.right - inset, _tmpRect.top,
            _tmpRect.right + w, _tmpRect.bottom, tc.border_right_paint, tc);
        drawBorder(canvas, _tmpRect.left, _tmpRect.bottom - inset,
            _tmpRect.right, _tmpRect.bottom + w, tc.border_bottom_paint, tc);
      }
    }
  }

  /** Clip to draw a border at a time. This allows to call [drawRoundRect]
      several time with the same parameters but a different Paint. */
  void drawBorder(Canvas canvas, float clipl, float clipt, float clipr,
      float clipb, Paint paint, Theme.Computed.Key tc)
  {
    float r = tc.border_radius;
    canvas.save();
    canvas.clipRect(clipl, clipt, clipr, clipb);
    canvas.drawRoundRect(_tmpRect, r, r, paint);
    canvas.restore();
  }

  private int labelColor(KeyValue k, boolean isKeyDown, boolean sublabel, Theme.Computed.Key tc)
  {
    if (_config.custom_colors_enabled)
    {
      if (tc == _tc.key_action || tc == _tc.key_activated || isKeyDown)
      {
        int text = Theme.getContrastColor(_config.custom_accent_color);
        return sublabel ? Theme.adjustLight(text, 0.35f) : text;
      }
      else
      {
        int text = Theme.getContrastColor(_config.custom_bg_color);
        if (sublabel)
          return Theme.adjustLight(text, 0.35f);
        if (k.hasFlagsAny(KeyValue.FLAG_SECONDARY | KeyValue.FLAG_GREYED))
          return Theme.adjustLight(text, 0.5f);
        return text;
      }
    }
    if (isKeyDown)
    {
      int flags = _pointers.getKeyFlags(k);
      if (flags != -1)
      {
        if ((flags & Pointers.FLAG_P_LOCKED) != 0)
          return _theme.lockedColor;
        return _theme.activatedColor;
      }
      return _theme.pressedColor;
    }
    if (k.hasFlagsAny(KeyValue.FLAG_SECONDARY | KeyValue.FLAG_GREYED))
    {
      if (k.hasFlagsAny(KeyValue.FLAG_GREYED))
        return _theme.greyedLabelColor;
      return _theme.secondaryLabelColor;
    }
    return sublabel ? _theme.subLabelColor : _theme.labelColor;
  }

  private void drawLabel(Canvas canvas, KeyValue kv, float x, float y,
      float keyH, boolean isKeyDown, Theme.Computed.Key tc)
  {
    kv = modifyKey(kv, _mods);
    if (kv == null)
      return;
    String str = kv.getString();
    if (str == null || str.isEmpty())
      return;
    float textSize = scaleTextSize(kv, true);
    Paint p = tc.label_paint(kv.hasFlagsAny(KeyValue.FLAG_KEY_FONT), labelColor(kv, isKeyDown, false, tc), textSize);
    float textW = p.measureText(str);
    float maxW = _keyWidth - tc.border_width * 2f - 4f;
    if (textW > maxW && maxW > 0)
    {
      textSize = Math.max(8f, textSize * (maxW / textW));
      p = tc.label_paint(kv.hasFlagsAny(KeyValue.FLAG_KEY_FONT), labelColor(kv, isKeyDown, false, tc), textSize);
    }
    canvas.drawText(str, x, (keyH - p.ascent() - p.descent()) / 2f + y, p);
  }

  public static boolean isComplexScript(String label)
  {
    if (label == null)
      return false;
    for (int ci = 0; ci < label.length(); ci++)
    {
      char ch = label.charAt(ci);
      if ((ch >= '\u0D80' && ch <= '\u0DFF') || ch == '\u200D' || ch == '\u200C')
        return true;
    }
    return false;
  }

  private void drawSubLabel(Canvas canvas, KeyValue kv, float x, float y,
      float keyW, float keyH, int sub_index, boolean isKeyDown,
      boolean isActiveSwipeCorner, Theme.Computed.Key tc)
  {
    Paint.Align a = LABEL_POSITION_H[sub_index];
    Vertical v = LABEL_POSITION_V[sub_index];
    kv = modifyKey(kv, _mods);
    if (kv == null)
      return;
    String label = kv.getString();
    if (label == null || label.isEmpty())
      return;
    float textSize = scaleTextSize(kv, false);
    if (isActiveSwipeCorner)
      textSize *= 1.18f;
    int color = isActiveSwipeCorner
        ? (_config.custom_colors_enabled ? Theme.getContrastColor(_config.custom_accent_color) : _theme.activatedColor)
        : labelColor(kv, isKeyDown, true, tc);
    Paint p = tc.sublabel_paint(kv.hasFlagsAny(KeyValue.FLAG_KEY_FONT), color, textSize, a);
    boolean prevBold = p.isFakeBoldText();
    if (isActiveSwipeCorner)
      p.setFakeBoldText(true);
    float subPadding = _config.keyPadding;
    if (v == Vertical.CENTER)
      y += (keyH - p.ascent() - p.descent()) / 2f;
    else
      y += (v == Vertical.TOP) ? subPadding - p.ascent() : keyH - subPadding - p.descent();
    if (a == Paint.Align.CENTER)
      x += keyW / 2f;
    else
      x += (a == Paint.Align.LEFT) ? subPadding : keyW - subPadding;

    int label_len = label.length();
    boolean isComplexScript = isComplexScript(label);
    if (!isComplexScript && label_len > 3 && kv.getKind() == KeyValue.Kind.String)
      label_len = 3;

    float textW = p.measureText(label, 0, label_len);
    float maxSubW = keyW * 0.46f;
    if (textW > maxSubW && maxSubW > 0)
    {
      float minSubPx = Math.max(9f * getResources().getDisplayMetrics().density, 10f);
      textSize = Math.max(minSubPx, textSize * (maxSubW / textW));
      p = tc.sublabel_paint(kv.hasFlagsAny(KeyValue.FLAG_KEY_FONT), color, textSize, a);
      if (isActiveSwipeCorner)
        p.setFakeBoldText(true);
    }
    canvas.drawText(label, 0, label_len, x, y, p);
    if (isActiveSwipeCorner)
      p.setFakeBoldText(prevBold);
  }

  private void drawIndication(Canvas canvas, KeyboardData.Key k, float x,
      float y, float keyW, float keyH, Theme.Computed tc)
  {
    if (k.indication == null || k.indication.equals(""))
      return;
    Paint p = tc.indication_paint;
    p.setTextSize(_subLabelSize);
    canvas.drawText(k.indication, 0, k.indication.length(),
        x + keyW / 2f, (keyH - p.ascent() - p.descent()) * 4/5 + y, p);
  }

  private float scaleTextSize(KeyValue k, boolean main_label)
  {
    float smaller_font = k.hasFlagsAny(KeyValue.FLAG_SMALLER_FONT) ? 0.75f : 1.f;
    float label_size = main_label ? _mainLabelSize : _subLabelSize;
    return label_size * smaller_font;
  }

  /** Exposes each drawn key as a focusable virtual node for TalkBack. */
  private static final class KeyboardAccessibilityHelper extends ExploreByTouchHelper
  {
    private final Keyboard2View _host;
    private final Rect _bounds = new Rect();

    KeyboardAccessibilityHelper(Keyboard2View host)
    {
      super(host);
      _host = host;
    }

    @Override
    protected int getVirtualViewAt(float x, float y)
    {
      int count = _host.accessibilityKeyCount();
      for (int i = 0; i < count; i++)
      {
        _host.accessibilityBounds(i, _bounds);
        if (_bounds.contains((int)x, (int)y))
          return i;
      }
      return INVALID_ID;
    }

    @Override
    protected void getVisibleVirtualViews(List<Integer> virtualViewIds)
    {
      int count = _host.accessibilityKeyCount();
      for (int i = 0; i < count; i++)
        virtualViewIds.add(i);
    }

    @Override
    protected void onPopulateNodeForVirtualView(int virtualViewId,
        AccessibilityNodeInfoCompat node)
    {
      KeyboardData.Key key = _host.accessibilityKeyAt(virtualViewId);
      _host.accessibilityBounds(virtualViewId, _bounds);
      node.setBoundsInParent(_bounds);
      node.setClassName("android.widget.Button");
      String primaryLabel = Keyboard2View.accessibilityLabelForKey(key, _host._mods);
      node.setText(primaryLabel);
      node.setContentDescription(primaryLabel);
      boolean enabled = key != null && !"Empty key".equals(primaryLabel);
      node.setClickable(enabled);
      node.setFocusable(enabled);
      node.setEnabled(enabled);
      if (enabled)
      {
        node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
        if (supportsAccessibilityLongClick(key))
        {
          node.setLongClickable(true);
          node.addAction(AccessibilityNodeInfoCompat.ACTION_LONG_CLICK);
        }
        Context ctx = _host.getContext();
        for (int sub = 1; sub <= 8; sub++)
        {
          if (key.keys[sub] != null)
          {
            KeyValue subVal = _host.modifyKey(key.keys[sub], _host._mods);
            if (subVal == null)
              subVal = key.keys[sub];
            String subLabel = Keyboard2View.accessibilityLabelForKeyValue(subVal);
            String dirLabel = getDirectionLabel(ctx, sub);
            String actionLabel = ctx.getString(R.string.a11y_swipe_action_fmt, dirLabel, subLabel);
            node.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                ACCESSIBILITY_SWIPE_ACTION_BASE + sub, actionLabel));
          }
        }
      }
    }

    private boolean supportsAccessibilityLongClick(KeyboardData.Key key)
    {
      if (key == null || key.keys[0] == null)
        return false;
      KeyValue base = _host.modifyKey(key.keys[0], _host._mods);
      if (base == null)
        return false;
      if (base.hasFlagsAny(KeyValue.FLAG_LATCH)
          && base.hasFlagsAny(KeyValue.FLAG_SPECIAL))
        return true;
      KeyValue longPress = KeyModifier.modify_long_press(base);
      return longPress != null && !longPress.equals(base);
    }

    private String getDirectionLabel(Context ctx, int subIndex)
    {
      switch (subIndex)
      {
        case 1: return ctx.getString(R.string.a11y_swipe_dir_nw);
        case 2: return ctx.getString(R.string.a11y_swipe_dir_ne);
        case 3: return ctx.getString(R.string.a11y_swipe_dir_sw);
        case 4: return ctx.getString(R.string.a11y_swipe_dir_se);
        case 5: return ctx.getString(R.string.a11y_swipe_dir_w);
        case 6: return ctx.getString(R.string.a11y_swipe_dir_e);
        case 7: return ctx.getString(R.string.a11y_swipe_dir_n);
        case 8: return ctx.getString(R.string.a11y_swipe_dir_s);
        default: return "";
      }
    }

    @Override
    protected boolean onPerformActionForVirtualView(int virtualViewId,
        int action, Bundle arguments)
    {
      if (action == AccessibilityNodeInfoCompat.ACTION_CLICK
          && _host.performAccessibilityClick(virtualViewId))
      {
        sendEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED);
        return true;
      }
      if (action == AccessibilityNodeInfoCompat.ACTION_LONG_CLICK
          && _host.performAccessibilityLongClick(virtualViewId))
      {
        sendEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_LONG_CLICKED);
        return true;
      }
      if (action > ACCESSIBILITY_SWIPE_ACTION_BASE
          && action <= ACCESSIBILITY_SWIPE_ACTION_BASE + 8)
      {
        int subIndex = action - ACCESSIBILITY_SWIPE_ACTION_BASE;
        if (_host.performAccessibilitySwipe(virtualViewId, subIndex))
        {
          sendEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED);
          return true;
        }
      }
      return false;
    }
  }
}
