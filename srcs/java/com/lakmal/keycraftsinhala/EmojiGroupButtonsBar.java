package com.lakmal.keycraftsinhala;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

public class EmojiGroupButtonsBar extends HorizontalScrollView
{
  private final LinearLayout _group_container;
  private EmojiGridView _emoji_grid;

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
    add_group(EmojiGridView.GROUP_LAST_USE, "\uD83D\uDD59");
    for (int i = 0; i < Emoji.getNumGroups(); i++)
    {
      Emoji first = Emoji.getEmojisByGroup(i).get(0);
      add_group(i, first.kv().getString());
    }
  }

  private int dp(float value)
  {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  private void add_group(int id, String symbol)
  {
    EmojiGroupButton button = new EmojiGroupButton(getContext(), id, symbol);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(48), dp(48));
    params.setMargins(dp(4), 0, dp(4), 0);
    _group_container.addView(button, params);
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

    EmojiGroupButton(Context context, int group_id, String symbol)
    {
      super(new ContextThemeWrapper(context, R.style.emojiTypeButton), null, 0);
      _group_id = group_id;
      setText(symbol);
      setMinWidth(dp(48));
      setMinHeight(dp(48));
      setFocusable(true);
      setClickable(true);
      setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
      setOnClickListener(view -> {
        EmojiGridView grid = get_emoji_grid();
        if (grid != null)
          grid.setEmojiGroup(_group_id);
      });
    }
  }
}
