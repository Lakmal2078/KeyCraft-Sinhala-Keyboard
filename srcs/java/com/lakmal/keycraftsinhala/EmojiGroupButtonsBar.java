package com.lakmal.keycraftsinhala;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.List;

public class EmojiGroupButtonsBar extends HorizontalScrollView
{
  private static final int[] GROUP_DESCRIPTION_RES = new int[] {
    R.string.emoji_category_smileys,
    R.string.emoji_category_people,
    R.string.emoji_category_animals,
    R.string.emoji_category_food,
    R.string.emoji_category_travel,
    R.string.emoji_category_activities,
    R.string.emoji_category_objects,
    R.string.emoji_category_symbols,
    R.string.emoji_category_flags
  };

  private final LinearLayout _group_container;
  private final List<EmojiGroupButton> _buttons = new ArrayList<>();
  private EmojiGridView _emoji_grid;
  private int _selected_group = EmojiGridView.GROUP_LAST_USE;

  public EmojiGroupButtonsBar(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    setHorizontalScrollBarEnabled(false);
    setFillViewport(false);
    setOverScrollMode(View.OVER_SCROLL_NEVER);

    _group_container = new LinearLayout(context);
    _group_container.setOrientation(LinearLayout.HORIZONTAL);
    addView(_group_container, new LayoutParams(
        LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));

    Emoji.init(context.getResources());
    add_group(EmojiGridView.GROUP_LAST_USE, "\uD83D\uDD59", R.string.emoji_category_recents);
    for (int i = 0; i < Emoji.getNumGroups(); i++)
    {
      Emoji first = Emoji.getEmojisByGroup(i).get(0);
      int descRes = (i < GROUP_DESCRIPTION_RES.length)
          ? GROUP_DESCRIPTION_RES[i] : R.string.emoji_category_symbols;
      add_group(i, first.kv().getString(), descRes);
    }
  }

  @Override
  protected void onAttachedToWindow()
  {
    super.onAttachedToWindow();
    EmojiGridView grid = get_emoji_grid();
    if (grid != null)
      setSelectedGroup(grid.getCurrentGroup());
  }

  private int dp(float value)
  {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  private void add_group(int id, String symbol, int descriptionRes)
  {
    EmojiGroupButton button = new EmojiGroupButton(getContext(), id, symbol, descriptionRes);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(48), dp(48));
    params.setMargins(dp(4), 0, dp(4), 0);
    _group_container.addView(button, params);
    _buttons.add(button);
  }

  public void setSelectedGroup(int groupId)
  {
    _selected_group = groupId;
    for (EmojiGroupButton b : _buttons)
      b.updateActiveState(b._group_id == groupId);
  }

  public void clearSelection()
  {
    _selected_group = Integer.MIN_VALUE;
    for (EmojiGroupButton b : _buttons)
      b.updateActiveState(false);
  }

  private EmojiGridView get_emoji_grid()
  {
    if (_emoji_grid == null && getParent() instanceof View)
      _emoji_grid = (EmojiGridView)((View)getParent()).findViewById(R.id.emoji_grid);
    return _emoji_grid;
  }

  class EmojiGroupButton extends Button
  {
    private final int _group_id;
    private final Drawable _defaultBackground;

    EmojiGroupButton(Context context, int group_id, String symbol, int descriptionRes)
    {
      super(new ContextThemeWrapper(context, R.style.emojiTypeButton), null, 0);
      _group_id = group_id;
      _defaultBackground = resolveDefaultBackground(context);
      setText(symbol);
      setContentDescription(context.getString(descriptionRes));
      setMinWidth(dp(48));
      setMinHeight(dp(48));
      setFocusable(true);
      setClickable(true);
      setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
      setOnClickListener(view -> {
        EmojiGridView grid = get_emoji_grid();
        if (grid != null)
          grid.setEmojiGroup(_group_id);
        setSelectedGroup(_group_id);
      });
    }

    private Drawable resolveDefaultBackground(Context context)
    {
      TypedArray ta = context.obtainStyledAttributes(new int[] { R.attr.emoji_button_bg });
      Drawable d = ta.getDrawable(0);
      ta.recycle();
      return d;
    }

    void updateActiveState(boolean active)
    {
      setSelected(active);
      if (active)
      {
        setBackgroundResource(R.drawable.suggestions_pill_background);
        setAlpha(1.0f);
      }
      else
      {
        if (_defaultBackground != null && _defaultBackground.getConstantState() != null)
          setBackground(_defaultBackground.getConstantState().newDrawable(getResources()));
        else
          setBackgroundResource(R.drawable.bg_toolbar_button);
        setAlpha(0.75f);
      }
    }
  }
}
