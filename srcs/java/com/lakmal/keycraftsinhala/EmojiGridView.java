package com.lakmal.keycraftsinhala;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.os.Build.VERSION;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EmojiGridView extends GridView
  implements GridView.OnItemClickListener, GridView.OnItemLongClickListener
{
  public static final int GROUP_LAST_USE = -1;

  private static final String LAST_USE_PREF = "emoji_last_use";
  private static final String[] SKIN_TONE_MODIFIERS = {
    "\uD83C\uDFFB", "\uD83C\uDFFC", "\uD83C\uDFFD", "\uD83C\uDFFE", "\uD83C\uDFFF"
  };
  private static final Paint SKIN_TONE_PAINT = new Paint();

  private List<Emoji> _emojiArray;
  private HashMap<Emoji, Integer> _lastUsed;
  private boolean _last_used_dirty = false;
  private int _currentGroup = 0;
  private String _searchQuery = null;
  private TextView _emptyView;
  private int _recentsEmptyRes = R.string.emoji_recents_empty;
  private int _searchEmptyRes = R.string.emoji_search_empty;
  private PopupWindow _skinTonePopup;

  public EmojiGridView(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    Emoji.init(context.getResources());
    setOnItemClickListener(this);
    setOnItemLongClickListener(this);
    loadLastUsed();
    setEmojiGroup((_lastUsed.size() == 0) ? 0 : GROUP_LAST_USE);
  }

  public int getCurrentGroup()
  {
    return _currentGroup;
  }

  public void setEmptyStateViews(TextView emptyView, int recentsEmptyRes, int searchEmptyRes)
  {
    _emptyView = emptyView;
    _recentsEmptyRes = recentsEmptyRes;
    _searchEmptyRes = searchEmptyRes;
    updateEmptyState();
  }

  public void setEmojiGroup(int group)
  {
    _currentGroup = group;
    _searchQuery = null;
    _emojiArray = (group == GROUP_LAST_USE) ? getLastEmojis() : Emoji.getEmojisByGroup(group);
    setAdapter(new EmojiViewAdpater(getContext(), _emojiArray));
    updateEmptyState();
  }

  public void setSearchQuery(String query)
  {
    if (query == null || query.trim().isEmpty())
    {
      setEmojiGroup(_currentGroup);
      return;
    }
    _searchQuery = query;
    _emojiArray = Emoji.searchEmojis(query);
    setAdapter(new EmojiViewAdpater(getContext(), _emojiArray));
    updateEmptyState();
  }

  private void updateEmptyState()
  {
    if (_emptyView == null)
      return;
    boolean isEmpty = (_emojiArray == null || _emojiArray.isEmpty());
    if (!isEmpty)
    {
      _emptyView.setVisibility(View.GONE);
      if (getVisibility() != View.VISIBLE)
        setVisibility(View.VISIBLE);
      return;
    }
    if (_searchQuery != null && !_searchQuery.trim().isEmpty())
    {
      _emptyView.setText(_searchEmptyRes);
      _emptyView.setVisibility(View.VISIBLE);
      setVisibility(View.GONE);
    }
    else if (_currentGroup == GROUP_LAST_USE)
    {
      _emptyView.setText(_recentsEmptyRes);
      _emptyView.setVisibility(View.VISIBLE);
      setVisibility(View.GONE);
    }
    else
    {
      _emptyView.setVisibility(View.GONE);
      setVisibility(View.VISIBLE);
    }
  }

  @Override
  public void onItemClick(AdapterView<?> parent, View v, int pos, long id)
  {
    if (_emojiArray == null || pos < 0 || pos >= _emojiArray.size())
      return;
    Emoji emoji = _emojiArray.get(pos);
    commitEmoji(emoji.kv(), emoji);
  }

  @Override
  public boolean onItemLongClick(AdapterView<?> parent, View v, int pos, long id)
  {
    if (_emojiArray == null || pos < 0 || pos >= _emojiArray.size())
      return false;
    Emoji base = _emojiArray.get(pos);
    List<String> variants = buildSkinToneVariants(base.kv().getString());
    if (variants == null || variants.isEmpty())
      return false;
    showSkinTonePopup(v, base, variants);
    return true;
  }

  private void commitEmoji(KeyValue kv, Emoji recentsTarget)
  {
    if (recentsTarget != null)
    {
      Integer used = _lastUsed.get(recentsTarget);
      _lastUsed.put(recentsTarget, (used == null) ? 1 : used.intValue() + 1);
      _last_used_dirty = true;
      saveLastUsed();
    }
    Config config = Config.globalConfig();
    if (config != null && config.handler != null)
      config.handler.key_up(kv, Pointers.Modifiers.EMPTY);
  }

  private List<String> buildSkinToneVariants(String emojiStr)
  {
    if (emojiStr == null || emojiStr.isEmpty())
      return null;
    int firstCp = emojiStr.codePointAt(0);
    if (!isSkinToneBase(firstCp))
      return null;

    String testVariant = applySkinTone(emojiStr, SKIN_TONE_MODIFIERS[2]);
    if (VERSION.SDK_INT >= 23 && !SKIN_TONE_PAINT.hasGlyph(testVariant))
      return null;

    List<String> variants = new ArrayList<>(SKIN_TONE_MODIFIERS.length + 1);
    variants.add(stripSkinTone(emojiStr));
    for (String mod : SKIN_TONE_MODIFIERS)
      variants.add(applySkinTone(emojiStr, mod));
    return variants;
  }

  private static String stripSkinTone(String s)
  {
    String out = s;
    for (String mod : SKIN_TONE_MODIFIERS)
      out = out.replace(mod, "");
    return out;
  }

  private static String applySkinTone(String emojiStr, String modifier)
  {
    String base = stripSkinTone(emojiStr);
    int firstLen = Character.charCount(base.codePointAt(0));
    String head = base.substring(0, firstLen);
    String tail = base.substring(firstLen);
    if (tail.startsWith("\uFE0F"))
      tail = tail.substring(1);
    return head + modifier + tail;
  }

  private static boolean isSkinToneBase(int cp)
  {
    return cp == 0x1F385
        || (cp >= 0x1F3C2 && cp <= 0x1F3C4)
        || cp == 0x1F3C7
        || (cp >= 0x1F3CA && cp <= 0x1F3CC)
        || cp == 0x1F442 || cp == 0x1F443
        || (cp >= 0x1F446 && cp <= 0x1F450)
        || (cp >= 0x1F466 && cp <= 0x1F478)
        || cp == 0x1F47C
        || (cp >= 0x1F481 && cp <= 0x1F483)
        || (cp >= 0x1F485 && cp <= 0x1F487)
        || cp == 0x1F4AA
        || cp == 0x1F574 || cp == 0x1F575 || cp == 0x1F57A
        || cp == 0x1F590 || cp == 0x1F595 || cp == 0x1F596
        || (cp >= 0x1F645 && cp <= 0x1F647)
        || (cp >= 0x1F64B && cp <= 0x1F64F)
        || cp == 0x1F6A3
        || (cp >= 0x1F6B4 && cp <= 0x1F6B6)
        || cp == 0x1F6C0 || cp == 0x1F6CC
        || cp == 0x1F90C || cp == 0x1F90F
        || (cp >= 0x1F918 && cp <= 0x1F91F)
        || cp == 0x1F926
        || (cp >= 0x1F930 && cp <= 0x1F939)
        || (cp >= 0x1F93C && cp <= 0x1F93E)
        || cp == 0x1F977
        || cp == 0x1F9B5 || cp == 0x1F9B6
        || cp == 0x1F9B8 || cp == 0x1F9B9 || cp == 0x1F9BB
        || (cp >= 0x1F9CD && cp <= 0x1F9CF)
        || (cp >= 0x1F9D1 && cp <= 0x1F9DD)
        || cp == 0x261D || cp == 0x26F9
        || (cp >= 0x270A && cp <= 0x270D)
        || (cp >= 0x1FAF0 && cp <= 0x1FAF8);
  }

  private void showSkinTonePopup(View anchor, Emoji base, List<String> variants)
  {
    dismissSkinTonePopup();
    Context ctx = getContext();
    float density = getResources().getDisplayMetrics().density;
    int cellSize = Math.round(48 * density);
    int pad = Math.round(4 * density);

    LinearLayout row = new LinearLayout(ctx);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setBackgroundResource(R.drawable.bg_card_surface);
    row.setPadding(pad, pad, pad, pad);

    String desc = ctx.getString(R.string.emoji_skin_tone_desc);
    for (String variant : variants)
    {
      TextView item = new TextView(new ContextThemeWrapper(ctx, R.style.emojiGridButton));
      item.setText(variant);
      item.setContentDescription(desc);
      item.setGravity(Gravity.CENTER);
      item.setBackgroundResource(R.drawable.bg_toolbar_button);
      item.setClickable(true);
      item.setFocusable(true);
      item.setOnClickListener(btn -> {
        commitEmoji(new KeyValue(variant, KeyValue.Kind.String, 0, 0), base);
        dismissSkinTonePopup();
      });
      row.addView(item, new LinearLayout.LayoutParams(cellSize, cellSize));
    }

    row.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    int popupW = row.getMeasuredWidth();
    int popupH = row.getMeasuredHeight();

    PopupWindow popup = new PopupWindow(row, popupW, popupH, true);
    popup.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
    popup.setOutsideTouchable(true);
    int xOff = Math.round((anchor.getWidth() - popupW) / 2f);
    int yOff = -(anchor.getHeight() + popupH + Math.round(6 * density));
    try
    {
      popup.showAsDropDown(anchor, xOff, yOff);
      _skinTonePopup = popup;
    }
    catch (Exception _e) {}
  }

  private void dismissSkinTonePopup()
  {
    if (_skinTonePopup != null)
    {
      try { _skinTonePopup.dismiss(); } catch (Exception _e) {}
      _skinTonePopup = null;
    }
  }

  @Override
  protected void onDetachedFromWindow()
  {
    dismissSkinTonePopup();
    super.onDetachedFromWindow();
  }

  private List<Emoji> getLastEmojis()
  {
    List<Emoji> list = new ArrayList<>(_lastUsed.keySet());
    Collections.sort(list, new Comparator<Emoji>()
        {
          public int compare(Emoji a, Emoji b)
          {
            return _lastUsed.get(b) - _lastUsed.get(a);
          }
        });
    return list;
  }

  private void saveLastUsed()
  {
    if (!_last_used_dirty) return;
    SharedPreferences.Editor edit;
    try { edit = emojiSharedPreferences().edit(); }
    catch (Exception _e) { return; }
    HashSet<String> set = new HashSet<String>(_lastUsed.size());
    for (java.util.Map.Entry<Emoji, Integer> entry : _lastUsed.entrySet())
    {
      set.add(entry.getValue() + "-" + entry.getKey().kv().getString());
    }
    edit.putStringSet(LAST_USE_PREF, set);
    edit.apply();
    _last_used_dirty = false;
  }

  private void loadLastUsed()
  {
    _lastUsed = new HashMap<Emoji, Integer>();
    SharedPreferences prefs;
    // Storage might not be available (eg. the device is locked), avoid
    // crashing.
    try { prefs = emojiSharedPreferences(); }
    catch (Exception _e) { return; }
    Set<String> lastUseSet = prefs.getStringSet(LAST_USE_PREF, null);
    if (lastUseSet != null)
      for (String emojiData : lastUseSet)
      {
        String[] data = emojiData.split("-", 2);
        Emoji emoji;
        if (data.length != 2)
          continue ;
        emoji = Emoji.getEmojiByString(data[1]);
        if (emoji == null)
          continue ;
        int count = parseLastUsedCount(data[0]);
        if (count >= 0)
          _lastUsed.put(emoji, count);
      }
  }

  static int parseLastUsedCount(String raw)
  {
    if (raw == null)
      return -1;
    try
    {
      int value = Integer.parseInt(raw.trim());
      return value >= 0 ? value : -1;
    }
    catch (NumberFormatException _e)
    {
      return -1;
    }
  }

  SharedPreferences emojiSharedPreferences()
  {
    return getContext().getSharedPreferences("emoji_last_use", Context.MODE_PRIVATE);
  }

  static class EmojiView extends TextView
  {
    public EmojiView(Context context)
    {
      super(context);
      int minTouchPx = Math.round(48 * context.getResources().getDisplayMetrics().density);
      setMinimumWidth(minTouchPx);
      setMinimumHeight(minTouchPx);
    }

    public void setEmoji(Emoji emoji)
    {
      String s = emoji.kv().getString();
      setText(s);
      setContentDescription(s);
    }
  }

  static class EmojiViewAdpater extends BaseAdapter
  {
    Context _button_context;

    List<Emoji> _emojiArray;

    public EmojiViewAdpater(Context context, List<Emoji> emojiArray)
    {
      _button_context = new ContextThemeWrapper(context, R.style.emojiGridButton);
      _emojiArray = emojiArray;
    }

    public int getCount()
    {
      if (_emojiArray == null)
        return (0);
      return (_emojiArray.size());
    }

    public Object getItem(int pos)
    {
      return (_emojiArray.get(pos));
    }

    public long getItemId(int pos)
    {
      return (pos);
    }

    public View getView(int pos, View convertView, ViewGroup parent)
    {
      EmojiView view = (EmojiView)convertView;

      if (view == null)
        view = new EmojiView(_button_context);
      view.setEmoji(_emojiArray.get(pos));
      return view;
    }
  }
}
