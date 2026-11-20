package com.lakmal.keycraftsinhala.suggestions;

import android.content.Context;
import android.os.Build.VERSION;
import android.text.InputType;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.lakmal.keycraftsinhala.Config;
import com.lakmal.keycraftsinhala.KeyValue;
import com.lakmal.keycraftsinhala.Pointers;
import com.lakmal.keycraftsinhala.R;
import com.lakmal.keycraftsinhala.dict.SupportedDictionaries;

public class CandidatesView extends LinearLayout
{
  static final int NUM_CANDIDATES = 4;

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
    setup_item_view(3, R.id.candidates_emoji);
  }

  public void set_candidates(Suggestions s)
  {
    int s_count = s.count;
    for (int i = 0; i < Suggestions.MAX_COUNT; i++)
      _items[i] = (i < s_count) ? s.suggestions[i] : null;
    _items[3] = s.emoji_suggestion;
    // Hide the status message when showing candidates.
    if (s_count != 0 && _status_no_dict != null)
      _status_no_dict.setVisibility(View.GONE);
    for (int i = 0; i < _item_views.length; i++)
    {
      TextView v = _item_views[i];
      if (_items[i] != null)
      {
        v.setText(_items[i]);
        v.setVisibility(View.VISIBLE);
      }
      else
      {
        v.setVisibility(View.GONE);
      }
    }
  }

  void clear_candidates()
  {
    for (int i = 0; i < _item_views.length; i++)
    {
      _items[i] = null;
      _item_views[i].setVisibility(View.GONE);
    }
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
    float text_size = row_height * config.characterSize * config.labelTextSize;
    for (int i = 0; i < NUM_CANDIDATES; i++)
    {
      TextView v = _item_views[i];
      // Set text size and enable auto size if supported.
      if (VERSION.SDK_INT < 26)
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, text_size);
      else
        v.setAutoSizeTextTypeUniformWithConfiguration(
            (int)(text_size / 2.), (int)text_size, 1, TypedValue.COMPLEX_UNIT_PX);
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
    v.setOnClickListener(new View.OnClickListener()
        {
          @Override
          public void onClick(View _v)
          {
            String it = _items[item_index];
            if (it != null)
              Config.globalConfig().handler.suggestion_entered(it);
          }
        });
    v.setVisibility(View.GONE);
    _item_views[item_index] = v;
  }

  /** Whether the candidates view should be shown for a given editor. */
  public static boolean should_show(EditorInfo info)
  {
    if (SuggestionPolicy.is_sensitive_input(info.inputType, info.imeOptions))
      return false;
    Config config = Config.globalConfig();
    if (config != null && !config.suggestions_enabled)
      return false;
    int flags = info.inputType & InputType.TYPE_MASK_FLAGS;
    switch (info.inputType & InputType.TYPE_MASK_CLASS)
    {
      case InputType.TYPE_CLASS_TEXT:
        // Host privacy settings take precedence over every suggestion source,
        // including when AUTO_CORRECT is also set.
        return (flags & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) == 0;
      case InputType.TYPE_CLASS_NUMBER:
        // Beware of TYPE_NUMBER_VARIATION_PASSWORD
        return false;
      default: return false;
    }
  }
}
