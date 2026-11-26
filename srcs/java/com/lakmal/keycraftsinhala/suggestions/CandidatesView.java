package com.lakmal.keycraftsinhala.suggestions;

import android.content.Context;
import android.os.Build.VERSION;
import android.text.InputType;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import com.lakmal.keycraftsinhala.Config;
import com.lakmal.keycraftsinhala.KeyValue;
import com.lakmal.keycraftsinhala.Pointers;
import com.lakmal.keycraftsinhala.R;

public class CandidatesView extends LinearLayout
{
  static final int NUM_CANDIDATES = Suggestions.VISIBLE_COUNT + 1;
  private static final int EMOJI_INDEX = Suggestions.VISIBLE_COUNT;

  private TextView _more_toggle;
  private HorizontalScrollView _extra_scroll;
  private LinearLayout _extra_items;
  private final List<String> _extra_candidates = new ArrayList<>();
  private Consumer<String> _candidate_listener;
  private float _text_size;

  /** Candidates currently visible. Entries can be [null] when there are less
      than [NUM_CANDIDATES] suggestions.
      - Entries at indexes [0] to [2] are word suggestions.
      - Entry at index [3] is the emoji suggestion. */
  String[] _items = new String[NUM_CANDIDATES];

  /** Text views showing the candidates in [_items]. Text views visibility is
      set to [GONE] when there are less than [NUM_CANDIDATES] suggestions. */
  TextView[] _item_views = new TextView[NUM_CANDIDATES];

  /** Message when no dictionary is installed. Visible when no candidates are
      shown. Might be [null]. */
  View _status_no_dict = null;

  public CandidatesView(Context context, AttributeSet attrs)
  {
    super(context, attrs);
  }

  @Override
  protected void onFinishInflate()
  {
    super.onFinishInflate();
    setup_item_view(0, R.id.candidates_middle);
    setup_item_view(1, R.id.candidates_right);
    setup_item_view(2, R.id.candidates_left);
    setup_item_view(EMOJI_INDEX, R.id.candidates_emoji);
    _more_toggle = findViewById(R.id.candidates_more_toggle);
    _extra_scroll = findViewById(R.id.candidates_extra_scroll);
    _extra_items = findViewById(R.id.candidates_extra_items);
    _more_toggle.setOnClickListener(v -> show_extra_candidates(_extra_scroll.getVisibility() != View.VISIBLE));
  }

  public void setCandidateListener(Consumer<String> listener)
  {
    _candidate_listener = listener;
  }

  public void set_candidates(Suggestions s)
  {
    int s_count = s.count;
    for (int i = 0; i < Suggestions.VISIBLE_COUNT; i++)
      _items[i] = (i < s_count) ? s.suggestions[i] : null;
    _items[EMOJI_INDEX] = s.emoji_suggestion;
    _extra_candidates.clear();
    for (int i = Suggestions.VISIBLE_COUNT; i < s_count; i++)
      _extra_candidates.add(s.suggestions[i]);
    // Hide the status message when showing candidates.
    if (s_count != 0 && _status_no_dict != null)
      _status_no_dict.setVisibility(View.GONE);
    for (int i = 0; i < _item_views.length; i++)
    {
      TextView v = _item_views[i];
      v.setText(_items[i]);
      v.setContentDescription(accessibleDescriptionForItem(
          getContext(), i, _items[i], s.clipboard_full_text));
    }
    show_extra_candidates(false);
  }

  public static String accessibleDescriptionForItem(Context ctx, int itemIndex,
      String itemText, String clipboardFullText)
  {
    if (itemText == null || itemText.isEmpty())
      return null;
    if (itemIndex == EMOJI_INDEX)
    {
      if (ctx != null)
      {
        try
        {
          String formatted = ctx.getString(R.string.a11y_candidate_emoji_fmt, itemText);
          if (formatted != null && !formatted.isEmpty())
            return formatted;
        }
        catch (Exception ignored) {}
      }
      return "Emoji suggestion: " + itemText;
    }
    if (itemIndex == 0 && clipboardFullText != null && itemText.startsWith("📋"))
    {
      String preview = itemText.substring(itemText.indexOf(' ') >= 0
          ? itemText.indexOf(' ') + 1 : 0).trim();
      if (preview.isEmpty())
        preview = itemText;
      if (ctx != null)
      {
        try
        {
          String formatted = ctx.getString(R.string.a11y_candidate_clipboard_fmt, preview);
          if (formatted != null && !formatted.isEmpty())
            return formatted;
        }
        catch (Exception ignored) {}
      }
      return "Paste from clipboard: " + preview;
    }
    return null;
  }

  private void show_extra_candidates(boolean show)
  {
    _extra_items.removeAllViews();
    _extra_scroll.setVisibility(show ? View.VISIBLE : View.GONE);
    _more_toggle.setVisibility(_extra_candidates.isEmpty() ? View.GONE : View.VISIBLE);
    _more_toggle.setText(show ? R.string.candidates_fewer_symbol : R.string.candidates_more_symbol);
    _more_toggle.setContentDescription(getResources().getText(
        show ? R.string.candidates_show_fewer : R.string.candidates_show_more));
    for (int i = 0; i < _item_views.length; i++)
      _item_views[i].setVisibility(!show && _items[i] != null ? View.VISIBLE : View.GONE);

    if (show)
    {
      for (String candidate : _extra_candidates)
      {
        TextView item = new TextView(getContext(), null, 0, R.style.candidates_item);
        item.setText(candidate);
        item.setTextColor(_item_views[1].getTextColors());
        if (_text_size > 0)
          item.setTextSize(TypedValue.COMPLEX_UNIT_PX, _text_size);
        item.setSingleLine(true);
        item.setMinWidth(Math.round(48 * getResources().getDisplayMetrics().density));
        item.setFocusable(true);
        item.setOnClickListener(v -> {
          show_extra_candidates(false);
          select_candidate(candidate);
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        params.setMarginStart(getResources().getDimensionPixelSize(R.dimen.candidates_gap));
        _extra_items.addView(item, params);
      }
      _extra_scroll.scrollTo(0, 0);
    }
  }

  void clear_candidates()
  {
    for (int i = 0; i < _item_views.length; i++)
    {
      _items[i] = null;
      _item_views[i].setText(null);
      _item_views[i].setContentDescription(null);
    }
    _extra_candidates.clear();
    show_extra_candidates(false);
  }

  private void select_candidate(String candidate)
  {
    if (candidate != null && _candidate_listener != null)
      _candidate_listener.accept(candidate);
  }

  public void refresh_config(Config config)
  {
    clear_candidates();
    if (_status_no_dict != null)
      _status_no_dict.setVisibility(View.GONE);
    set_sizes(config);
    if (config.custom_colors_enabled)
    {
      int textColor = com.lakmal.keycraftsinhala.Theme.getContrastColor(config.custom_bg_color);
      for (int i = 0; i < NUM_CANDIDATES; i++)
      {
        if (_item_views[i] != null)
          _item_views[i].setTextColor(textColor);
      }
      View toggle = findViewById(R.id.candidates_toolbar_toggle);
      if (toggle instanceof TextView)
        ((TextView)toggle).setTextColor(textColor);
      _more_toggle.setTextColor(textColor);
    }
    else
    {
      TypedValue tv = new TypedValue();
      if (_item_views[0] != null
          && getContext().getTheme().resolveAttribute(R.attr.colorLabelActivated, tv, true))
        _item_views[0].setTextColor(tv.data);
    }
  }

  /** Set the height of the suggestion row and the text size. */
  void set_sizes(Config config)
  {
    // Make the candidates view about as high as a keyboard row.
    float row_height = config.keyboard_rows_height_pixels * (1 - config.key_vertical_margin);
    // The contextual toolbar and candidate strip share a fixed 48dp viewport.
    // Match candidate text size to the keyboard row without changing bar height.
    // Match the size of labels on the keyboard.
    _text_size = row_height * config.characterSize * config.labelTextSize;
    for (int i = 0; i < NUM_CANDIDATES; i++)
    {
      TextView v = _item_views[i];
      // Set text size and enable auto size if supported.
      if (VERSION.SDK_INT < 26)
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, _text_size);
      else
        v.setAutoSizeTextTypeUniformWithConfiguration(
            Math.max(1, (int)(_text_size / 2.)), Math.max(2, (int)_text_size), 1, TypedValue.COMPLEX_UNIT_PX);
    }
  }

  private void setup_item_view(final int item_index, int item_id)
  {
    TextView v = (TextView)findViewById(item_id);
    if (item_index == 0)
    {
      v.setBackgroundResource(R.drawable.suggestions_pill_background);
      v.setTypeface(v.getTypeface(), android.graphics.Typeface.BOLD);
    }
    v.setSingleLine(true);
    v.setEllipsize(android.text.TextUtils.TruncateAt.END);
    v.setFocusable(true);
    v.setOnClickListener(view -> select_candidate(_items[item_index]));
    v.setVisibility(View.GONE);
    _item_views[item_index] = v;
  }

  /** Whether the candidates view should be shown for a given editor. */
  public static boolean should_show(EditorInfo info)
  {
    if (info == null)
      return false;
    if (SuggestionPolicy.is_sensitive_input(info.inputType, info.imeOptions))
      return false;
    Config config = Config.globalConfig();
    if (config != null && !config.suggestions_enabled)
      return false;
    switch (info.inputType & InputType.TYPE_MASK_CLASS)
    {
      case InputType.TYPE_CLASS_TEXT:
        return true;
      case InputType.TYPE_NULL:
        return !is_terminal_package(info.packageName);
      case InputType.TYPE_CLASS_NUMBER:
        // Beware of TYPE_NUMBER_VARIATION_PASSWORD
        return false;
      default:
        return false;
    }
  }

  private static boolean is_terminal_package(String pkg)
  {
    if (pkg == null)
      return false;
    return pkg.startsWith("com.termux")
        || pkg.equals("jackpal.androidterm")
        || pkg.equals("org.connectbot")
        || pkg.equals("com.sonelli.juicessh");
  }
}
