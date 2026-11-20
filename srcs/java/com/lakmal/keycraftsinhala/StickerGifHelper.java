package com.lakmal.keycraftsinhala;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Wires offline emoji search, built-in Sinhala/reaction stickers (committed via FileProvider +
 * InputConnectionCompat), and copied GIF reuse from volatile clipboard history.
 */
final class StickerGifHelper
{
  private static final int TAB_EMOJI = 0;
  private static final int TAB_STICKERS = 1;
  private static final int TAB_GIFS = 2;

  private static final class StickerSpec
  {
    final String label;
    final String subtitle;
    final int bgColor;
    final int fgColor;

    StickerSpec(String label, String subtitle, int bgColor, int fgColor)
    {
      this.label = label;
      this.subtitle = subtitle;
      this.bgColor = bgColor;
      this.fgColor = fgColor;
    }
  }

  private static final StickerSpec[] STICKERS = new StickerSpec[] {
    new StickerSpec("ආයුබෝවන් 🙏", "Ayubowan", 0xFF0F766E, 0xFFF0FDFA),
    new StickerSpec("සුභ උදෑසනක් 🌞", "Good Morning", 0xFFB45309, 0xFFFFFBEB),
    new StickerSpec("නියමයි! ✨", "Awesome", 0xFF1D4ED8, 0xFFEFF6FF),
    new StickerSpec("ස්තූතියි ❤️", "Thank You", 0xFF7C3AED, 0xFFF5F3FF),
    new StickerSpec("OK 👌", "Got it", 0xFF047857, 0xFFECFDF5),
    new StickerSpec("LOL 😂", "Haha", 0xFFC2410C, 0xFFFFF7ED),
    new StickerSpec("Love ❤️", "Warm Wishes", 0xFFBE123C, 0xFFFFF1F2),
    new StickerSpec("Tea Time ☕", "Ceylon Tea", 0xFF6D28D9, 0xFFF5F3FF)
  };

  private static final String[][] QUICK_TAGS = new String[][] {
    { "❤️ Love", "love" },
    { "😂 Laugh", "laugh" },
    { "😊 Happy", "happy" },
    { "🔥 Fire", "fire" },
    { "🙏 Thanks", "thanks" },
    { "👍 OK", "ok" },
    { "🎉 Party", "party" },
    { "🌞 Morning", "sun" },
    { "☕ Tea", "coffee" },
    { "😭 Sad", "sad" }
  };

  private StickerGifHelper() {}

  static void bindEmojiPane(ViewGroup emojiPane, Keyboard2 service)
  {
    if (emojiPane == null || service == null)
      return;

    Button tabEmoji = emojiPane.findViewById(R.id.emoji_tab_emoji);
    Button tabStickers = emojiPane.findViewById(R.id.emoji_tab_stickers);
    Button tabGifs = emojiPane.findViewById(R.id.emoji_tab_gifs);

    View searchStrip = emojiPane.findViewById(R.id.emoji_search_strip);
    LinearLayout searchChips = emojiPane.findViewById(R.id.emoji_search_chips);
    View searchInputRow = emojiPane.findViewById(R.id.emoji_search_input_row);
    EditText searchInput = emojiPane.findViewById(R.id.emoji_search_input);
    View searchClear = emojiPane.findViewById(R.id.emoji_search_clear);

    EmojiGroupButtonsBar groupBar = emojiPane.findViewById(R.id.emoji_group_bar);
    EmojiGridView emojiGrid = emojiPane.findViewById(R.id.emoji_grid);
    GridView stickerGrid = emojiPane.findViewById(R.id.sticker_grid);
    ListView gifList = emojiPane.findViewById(R.id.gif_list);
    TextView emptyView = emojiPane.findViewById(R.id.emoji_empty_view);

    if (emojiGrid != null && emptyView != null)
      emojiGrid.setEmptyStateViews(emptyView, R.string.emoji_recents_empty, R.string.emoji_search_empty);

    int textColor = resolveAttrColor(emojiPane.getContext(), R.attr.colorLabel, Color.WHITE);
    int subColor = resolveAttrColor(emojiPane.getContext(), R.attr.colorSubLabel, Color.LTGRAY);
    float density = emojiPane.getResources().getDisplayMetrics().density;

    // Populate offline search chips + toggleable text input
    final String[] activeChipQuery = new String[] { null };
    final List<Button> tagButtons = new ArrayList<>();
    if (searchChips != null)
    {
      searchChips.removeAllViews();
      Button searchToggle = createChip(emojiPane.getContext(),
          "🔍 " + emojiPane.getContext().getString(R.string.emoji_search_chip_search),
          textColor, density);
      searchToggle.setOnClickListener(v -> {
        if (searchInputRow == null)
          return;
        boolean show = searchInputRow.getVisibility() != View.VISIBLE;
        searchInputRow.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show && searchInput != null)
          searchInput.setText("");
      });
      searchChips.addView(searchToggle, chipLayoutParams(density));

      for (String[] pair : QUICK_TAGS)
      {
        String label = pair[0];
        String query = pair[1];
        Button chip = createChip(emojiPane.getContext(), label, textColor, density);
        tagButtons.add(chip);
        chip.setOnClickListener(v -> {
          boolean clearing = query.equals(activeChipQuery[0]);
          activeChipQuery[0] = clearing ? null : query;
          for (Button b : tagButtons)
          {
            boolean selected = b == chip && !clearing;
            b.setBackgroundResource(selected
                ? R.drawable.suggestions_pill_background
                : R.drawable.bg_toolbar_button);
          }
          if (searchInput != null)
            searchInput.setText(clearing ? "" : query);
          applyEmojiSearch(emojiGrid, groupBar, clearing ? "" : query);
        });
        searchChips.addView(chip, chipLayoutParams(density));
      }
    }

    if (searchInput != null)
    {
      searchInput.addTextChangedListener(new TextWatcher()
      {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override
        public void afterTextChanged(Editable s)
        {
          String q = s == null ? "" : s.toString();
          if (q.trim().isEmpty())
          {
            activeChipQuery[0] = null;
            for (Button b : tagButtons)
              b.setBackgroundResource(R.drawable.bg_toolbar_button);
          }
          applyEmojiSearch(emojiGrid, groupBar, q);
        }
      });
    }

    if (searchClear != null)
    {
      searchClear.setOnClickListener(v -> {
        activeChipQuery[0] = null;
        for (Button b : tagButtons)
          b.setBackgroundResource(R.drawable.bg_toolbar_button);
        if (searchInput != null)
          searchInput.setText("");
        applyEmojiSearch(emojiGrid, groupBar, "");
      });
    }

    if (stickerGrid != null)
    {
      stickerGrid.setAdapter(new StickerAdapter(emojiPane.getContext(), density));
      stickerGrid.setOnItemClickListener((parent, view, pos, id) -> {
        if (pos < 0 || pos >= STICKERS.length)
          return;
        Uri uri = getOrCreateStickerUri(emojiPane.getContext(), pos, STICKERS[pos]);
        if (uri == null || !service.commitMediaContent(uri, "image/png"))
          Toast.makeText(emojiPane.getContext(),
              R.string.stickers_commit_unsupported, Toast.LENGTH_SHORT).show();
      });
    }

    final int[] currentTab = new int[] { TAB_EMOJI };
    final Runnable refreshGifList = () -> {
      if (currentTab[0] != TAB_GIFS)
        return;
      bindGifList(emojiPane.getContext(), gifList, emptyView, service, textColor, subColor, density);
    };

    final android.view.View.OnClickListener switchTabEmoji = v -> {
      currentTab[0] = TAB_EMOJI;
      updateTabAppearance(tabEmoji, tabStickers, tabGifs, TAB_EMOJI);
      if (searchStrip != null) searchStrip.setVisibility(View.VISIBLE);
      if (groupBar != null) groupBar.setVisibility(View.VISIBLE);
      if (stickerGrid != null) stickerGrid.setVisibility(View.GONE);
      if (gifList != null) gifList.setVisibility(View.GONE);
      if (emojiGrid != null)
      {
        String q = searchInput != null && searchInput.getText() != null
            ? searchInput.getText().toString() : "";
        applyEmojiSearch(emojiGrid, groupBar, q);
      }
    };

    if (tabEmoji != null)
      tabEmoji.setOnClickListener(switchTabEmoji);

    if (tabStickers != null)
    {
      tabStickers.setOnClickListener(v -> {
        currentTab[0] = TAB_STICKERS;
        updateTabAppearance(tabEmoji, tabStickers, tabGifs, TAB_STICKERS);
        if (searchStrip != null) searchStrip.setVisibility(View.GONE);
        if (searchInputRow != null) searchInputRow.setVisibility(View.GONE);
        if (groupBar != null) groupBar.setVisibility(View.GONE);
        if (emojiGrid != null) emojiGrid.setVisibility(View.GONE);
        if (gifList != null) gifList.setVisibility(View.GONE);
        if (emptyView != null) emptyView.setVisibility(View.GONE);
        if (stickerGrid != null) stickerGrid.setVisibility(View.VISIBLE);
      });
    }

    if (tabGifs != null)
    {
      tabGifs.setOnClickListener(v -> {
        currentTab[0] = TAB_GIFS;
        updateTabAppearance(tabEmoji, tabStickers, tabGifs, TAB_GIFS);
        if (searchStrip != null) searchStrip.setVisibility(View.GONE);
        if (searchInputRow != null) searchInputRow.setVisibility(View.GONE);
        if (groupBar != null) groupBar.setVisibility(View.GONE);
        if (emojiGrid != null) emojiGrid.setVisibility(View.GONE);
        if (stickerGrid != null) stickerGrid.setVisibility(View.GONE);
        refreshGifList.run();
      });
    }

    emojiPane.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener()
    {
      @Override
      public void onViewAttachedToWindow(View v)
      {
        if (currentTab[0] == TAB_GIFS)
          refreshGifList.run();
      }

      @Override
      public void onViewDetachedFromWindow(View v) {}
    });
  }

  private static void applyEmojiSearch(EmojiGridView grid, EmojiGroupButtonsBar groupBar, String query)
  {
    if (grid == null)
      return;
    grid.setSearchQuery(query);
    if (groupBar != null)
    {
      if (query == null || query.trim().isEmpty())
        groupBar.setSelectedGroup(grid.getCurrentGroup());
      else
        groupBar.clearSelection();
    }
  }

  private static void updateTabAppearance(Button emoji, Button stickers, Button gifs, int activeTab)
  {
    if (emoji != null)
      emoji.setBackgroundResource(activeTab == TAB_EMOJI
          ? R.drawable.suggestions_pill_background : R.drawable.bg_toolbar_button);
    if (stickers != null)
      stickers.setBackgroundResource(activeTab == TAB_STICKERS
          ? R.drawable.suggestions_pill_background : R.drawable.bg_toolbar_button);
    if (gifs != null)
      gifs.setBackgroundResource(activeTab == TAB_GIFS
          ? R.drawable.suggestions_pill_background : R.drawable.bg_toolbar_button);
  }

  private static Button createChip(Context ctx, String label, int textColor, float density)
  {
    Button b = new Button(ctx);
    b.setText(label);
    b.setAllCaps(false);
    b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
    b.setTextColor(textColor);
    b.setBackgroundResource(R.drawable.bg_toolbar_button);
    b.setMinHeight(Math.round(36 * density));
    b.setMinWidth(Math.round(48 * density));
    int hPad = Math.round(10 * density);
    b.setPadding(hPad, 0, hPad, 0);
    return b;
  }

  private static LinearLayout.LayoutParams chipLayoutParams(float density)
  {
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, Math.round(36 * density));
    lp.setMargins(Math.round(2 * density), 0, Math.round(2 * density), 0);
    return lp;
  }

  private static void bindGifList(Context ctx, ListView gifList, TextView emptyView,
      Keyboard2 service, int textColor, int subColor, float density)
  {
    List<ClipboardHistoryService.HistoryEntry> gifs = new ArrayList<>();
    ClipboardHistoryService cs = ClipboardHistoryService.get_service(ctx);
    if (cs != null)
    {
      for (ClipboardHistoryService.HistoryEntry entry : cs.get_entries())
      {
        if (entry.uri != null && "image/gif".equals(entry.mime))
          gifs.add(entry);
      }
    }
    if (gifs.isEmpty())
    {
      if (gifList != null) gifList.setVisibility(View.GONE);
      if (emptyView != null)
      {
        emptyView.setText(R.string.gif_empty_state);
        emptyView.setVisibility(View.VISIBLE);
      }
      return;
    }
    if (emptyView != null) emptyView.setVisibility(View.GONE);
    if (gifList == null)
      return;
    gifList.setVisibility(View.VISIBLE);
    gifList.setAdapter(new GifAdapter(ctx, gifs, textColor, subColor, density));
    gifList.setOnItemClickListener((parent, view, pos, id) -> {
      if (pos < 0 || pos >= gifs.size())
        return;
      ClipboardHistoryService.HistoryEntry item = gifs.get(pos);
      if (!service.commitMediaContent(item.uri, item.mime))
        Toast.makeText(ctx, R.string.stickers_commit_unsupported, Toast.LENGTH_SHORT).show();
    });
  }

  private static Uri getOrCreateStickerUri(Context ctx, int index, StickerSpec spec)
  {
    try
    {
      File dir = new File(ctx.getCacheDir(), "stickers");
      if (!dir.exists() && !dir.mkdirs())
        return null;
      File out = new File(dir, "keycraft_sticker_" + index + ".png");
      if (!out.exists() || out.length() == 0)
      {
        Bitmap bmp = renderStickerBitmap(spec);
        try (FileOutputStream fos = new FileOutputStream(out))
        {
          bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
        finally
        {
          bmp.recycle();
        }
      }
      return FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", out);
    }
    catch (IOException | IllegalArgumentException | SecurityException e)
    {
      return null;
    }
  }

  private static Bitmap renderStickerBitmap(StickerSpec spec)
  {
    int width = 420;
    int height = 180;
    Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bmp);

    Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    bgPaint.setColor(spec.bgColor);
    RectF rect = new RectF(6f, 6f, width - 6f, height - 6f);
    canvas.drawRoundRect(rect, 36f, 36f, bgPaint);

    Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    titlePaint.setColor(spec.fgColor);
    titlePaint.setTextSize(42f);
    titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
    titlePaint.setTextAlign(Paint.Align.CENTER);
    canvas.drawText(spec.label, width / 2f, height * 0.48f, titlePaint);

    Paint subPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    subPaint.setColor((spec.fgColor & 0x00FFFFFF) | 0xCC000000);
    subPaint.setTextSize(24f);
    subPaint.setTextAlign(Paint.Align.CENTER);
    canvas.drawText(spec.subtitle, width / 2f, height * 0.76f, subPaint);
    return bmp;
  }

  private static int resolveAttrColor(Context ctx, int attr, int fallback)
  {
    TypedValue tv = new TypedValue();
    if (ctx.getTheme().resolveAttribute(attr, tv, true))
      return tv.data;
    return fallback;
  }

  private static final class StickerAdapter extends BaseAdapter
  {
    private final Context _ctx;
    private final float _density;

    StickerAdapter(Context ctx, float density)
    {
      _ctx = ctx;
      _density = density;
    }

    @Override public int getCount() { return STICKERS.length; }
    @Override public Object getItem(int pos) { return STICKERS[pos]; }
    @Override public long getItemId(int pos) { return pos; }

    @Override
    public View getView(int pos, View convertView, ViewGroup parent)
    {
      LinearLayout card = (convertView instanceof LinearLayout)
          ? (LinearLayout)convertView : new LinearLayout(_ctx);
      if (convertView == null)
      {
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setMinimumHeight(Math.round(72 * _density));
        int pad = Math.round(10 * _density);
        card.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(_ctx);
        title.setId(android.R.id.text1);
        title.setGravity(Gravity.CENTER);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(title);

        TextView sub = new TextView(_ctx);
        sub.setId(android.R.id.text2);
        sub.setGravity(Gravity.CENTER);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        card.addView(sub);
      }

      StickerSpec spec = STICKERS[pos];
      GradientDrawable bg = new GradientDrawable();
      bg.setColor(spec.bgColor);
      bg.setCornerRadius(14f * _density);
      card.setBackground(bg);

      TextView title = card.findViewById(android.R.id.text1);
      title.setText(spec.label);
      title.setTextColor(spec.fgColor);

      TextView sub = card.findViewById(android.R.id.text2);
      sub.setText(spec.subtitle);
      sub.setTextColor((spec.fgColor & 0x00FFFFFF) | 0xCC000000);
      return card;
    }
  }

  private static final class GifAdapter extends BaseAdapter
  {
    private final Context _ctx;
    private final List<ClipboardHistoryService.HistoryEntry> _entries;
    private final int _textColor;
    private final int _subColor;
    private final float _density;

    GifAdapter(Context ctx, List<ClipboardHistoryService.HistoryEntry> entries,
        int textColor, int subColor, float density)
    {
      _ctx = ctx;
      _entries = entries;
      _textColor = textColor;
      _subColor = subColor;
      _density = density;
    }

    @Override public int getCount() { return _entries.size(); }
    @Override public Object getItem(int pos) { return _entries.get(pos); }
    @Override public long getItemId(int pos) { return pos; }

    @Override
    public View getView(int pos, View convertView, ViewGroup parent)
    {
      LinearLayout row = (convertView instanceof LinearLayout)
          ? (LinearLayout)convertView : new LinearLayout(_ctx);
      if (convertView == null)
      {
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Math.round(56 * _density));
        int pad = Math.round(8 * _density);
        row.setPadding(pad, pad, pad, pad);

        ImageView thumb = new ImageView(_ctx);
        thumb.setId(android.R.id.icon);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        int size = Math.round(40 * _density);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMarginEnd(Math.round(12 * _density));
        row.addView(thumb, lp);

        TextView label = new TextView(_ctx);
        label.setId(android.R.id.text1);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        row.addView(label, new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
      }

      ClipboardHistoryService.HistoryEntry item = _entries.get(pos);
      TextView label = row.findViewById(android.R.id.text1);
      label.setText(_ctx.getString(R.string.gif_item_label,
          item.uri.getLastPathSegment() != null ? item.uri.getLastPathSegment() : "image/gif"));
      label.setTextColor(_textColor);

      ImageView thumb = row.findViewById(android.R.id.icon);
      Bitmap bmp = loadThumbnail(_ctx, item.uri, Math.round(40 * _density));
      if (bmp != null)
        thumb.setImageBitmap(bmp);
      else
        thumb.setImageResource(R.drawable.ic_toolbar_grid);
      thumb.setContentDescription(_ctx.getString(R.string.clipboard_image_thumb_desc));
      return row;
    }

    private static Bitmap loadThumbnail(Context ctx, Uri uri, int maxPx)
    {
      try (InputStream is = ctx.getContentResolver().openInputStream(uri))
      {
        if (is == null) return null;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = 4;
        Bitmap raw = BitmapFactory.decodeStream(is, null, opts);
        if (raw == null) return null;
        return Bitmap.createScaledBitmap(raw, Math.max(1, maxPx), Math.max(1, maxPx), true);
      }
      catch (Exception | OutOfMemoryError _e)
      {
        return null;
      }
    }
  }
}
