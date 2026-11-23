package com.lakmal.keycraftsinhala;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ClipboardHistoryView extends NonScrollListView
  implements ClipboardHistoryService.OnClipboardHistoryChange
{
  /** Display labels, parallel to [_entries]. */
  List<String> _history;
  List<ClipboardHistoryService.HistoryEntry> _entries;
  ClipboardHistoryService _service;
  ClipboardEntriesAdapter _adapter;

  public ClipboardHistoryView(Context ctx, AttributeSet attrs)
  {
    super(ctx, attrs);
    _history = Collections.EMPTY_LIST;
    _entries = Collections.emptyList();
    _adapter = this.new ClipboardEntriesAdapter();
    _service = ClipboardHistoryService.get_service(ctx);
    if (_service != null)
      refresh_entries();
    setAdapter(_adapter);
  }

  private void refresh_entries()
  {
    _entries = _service.get_entries();
    List<String> labels = new ArrayList<String>(_entries.size());
    for (ClipboardHistoryService.HistoryEntry entry : _entries)
      labels.add(entry.content);
    _history = labels;
  }

  private View findInPane(int viewId)
  {
    ViewParent parent = getParent();
    while (parent instanceof View)
    {
      View found = ((View)parent).findViewById(viewId);
      if (found != null)
        return found;
      parent = parent.getParent();
    }
    return null;
  }

  /** The history entry at index [pos] is removed from the history and added to
      the list of pinned clipboards. Image entries cannot be pinned. */
  public void pin_entry(int pos)
  {
    if (_service == null || pos < 0 || pos >= _entries.size())
      return;
    ClipboardHistoryService.HistoryEntry entry = _entries.get(pos);
    String clip = entry.content;
    if (entry.uri != null || !_service.contains_history_entry(clip))
    {
      update_data();
      return;
    }
    View pinObj = findInPane(R.id.clipboard_pin_view);
    if (pinObj instanceof ClipboardPinView)
      ((ClipboardPinView)pinObj).add_entry(clip);
    _service.remove_history_entry(clip);
  }

  /** Paste the specified entry while retaining it in history until cleared or expired. */
  public void paste_entry(int pos)
  {
    if (_service == null || pos < 0 || pos >= _entries.size())
      return;
    ClipboardHistoryService.HistoryEntry entry = _entries.get(pos);
    if (entry.uri != null)
    {
      ClipboardHistoryService.paste_image(entry.uri, entry.mime);
      return;
    }
    String clip = entry.content;
    if (_service.contains_history_entry(clip))
      ClipboardHistoryService.paste(clip);
    else
      update_data();
  }

  /** Delete a single entry from clipboard history. */
  public void delete_entry(int pos)
  {
    if (_service == null || pos < 0 || pos >= _entries.size())
      return;
    _service.remove_entry(_entries.get(pos));
  }

  public void refreshEmptyStates()
  {
    Config config = Config.globalConfig();
    boolean enabled = config != null && config.clipboard_history_enabled;
    boolean historyEmpty = (_history == null || _history.isEmpty());

    View disabledState = findInPane(R.id.clipboard_disabled_state);
    View emptyHistoryView = findInPane(R.id.clipboard_history_empty);
    if (disabledState != null)
      disabledState.setVisibility(enabled ? View.GONE : View.VISIBLE);
    if (emptyHistoryView != null)
      emptyHistoryView.setVisibility((enabled && historyEmpty) ? View.VISIBLE : View.GONE);
    setVisibility((enabled && !historyEmpty) ? View.VISIBLE : View.GONE);

    View clearAllBtn = findInPane(R.id.clipboard_clear_all_btn);
    if (clearAllBtn != null)
    {
      boolean canClear = enabled && !historyEmpty;
      clearAllBtn.setEnabled(canClear);
      clearAllBtn.setAlpha(canClear ? 1.0f : 0.38f);
    }

    View pinObj = findInPane(R.id.clipboard_pin_view);
    View emptyPinView = findInPane(R.id.clipboard_pin_empty);
    if (pinObj instanceof ClipboardPinView && emptyPinView != null)
    {
      boolean pinEmpty = ((ClipboardPinView)pinObj).getCount() == 0;
      emptyPinView.setVisibility(pinEmpty ? View.VISIBLE : View.GONE);
      pinObj.setVisibility(pinEmpty ? View.GONE : View.VISIBLE);
    }
  }

  private void bindPaneActions()
  {
    View clearAllBtn = findInPane(R.id.clipboard_clear_all_btn);
    if (clearAllBtn != null)
    {
      clearAllBtn.setOnClickListener(v -> {
        if (_service == null || _history.isEmpty())
          return;
        AlertDialog d = new AlertDialog.Builder(getContext())
          .setTitle(R.string.clipboard_clear_all_confirm)
          .setPositiveButton(R.string.clipboard_clear_all,
              new DialogInterface.OnClickListener()
              {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                  _service.clear_history();
                }
              })
          .setNegativeButton(android.R.string.cancel, null)
          .create();
        Utils.show_dialog_on_ime(d, v.getWindowToken());
      });
    }

    View enableBtn = findInPane(R.id.clipboard_enable_btn);
    if (enableBtn != null)
    {
      enableBtn.setOnClickListener(v -> {
        ClipboardHistoryService.set_history_enabled(true);
        View cb = findInPane(R.id.clipboard_history_checkbox);
        if (cb instanceof ClipboardHistoryCheckBox)
          ((ClipboardHistoryCheckBox)cb).refreshFromConfig();
        update_data();
      });
    }
  }

  @Override
  public void on_clipboard_history_change()
  {
    update_data();
  }

  @Override
  protected void onWindowVisibilityChanged(int visibility)
  {
    if (visibility == View.VISIBLE)
      update_data();
  }

  void update_data()
  {
    if (_service == null)
      return;
    refresh_entries();
    _adapter.notifyDataSetChanged();
    refreshEmptyStates();
    invalidate();
  }

  @Override
  protected void onAttachedToWindow()
  {
    super.onAttachedToWindow();
    bindPaneActions();
    if (_service != null)
    {
      _service.set_on_clipboard_history_change(this);
      update_data();
    }
    else
    {
      refreshEmptyStates();
    }
  }

  @Override
  protected void onDetachedFromWindow()
  {
    super.onDetachedFromWindow();
    if (_service != null)
      _service.remove_on_clipboard_history_change(this);
  }

  class ClipboardEntriesAdapter extends BaseAdapter
  {
    public ClipboardEntriesAdapter() {}

    @Override
    public int getCount() { return _history.size(); }
    @Override
    public Object getItem(int pos) { return _history.get(pos); }
    @Override
    public long getItemId(int pos) { return _history.get(pos).hashCode(); }

    @Override
    public View getView(final int pos, View v, ViewGroup _parent)
    {
      if (v == null)
        v = View.inflate(getContext(), R.layout.clipboard_history_entry, null);
      ClipboardHistoryService.HistoryEntry entry = _entries.get(pos);
      TextView textView = (TextView)v.findViewById(R.id.clipboard_entry_text);
      if (entry.uri != null)
      {
        String subtype = formatMimeSubtype(entry.mime);
        int fmtRes = "image/gif".equals(entry.mime)
            ? R.string.clipboard_gif_clip_fmt
            : R.string.clipboard_image_clip_fmt;
        textView.setText(getContext().getString(fmtRes, subtype));
      }
      else
      {
        textView.setText(entry.content);
      }

      ImageView thumb = v.findViewById(R.id.clipboard_entry_thumb);
      if (thumb != null)
      {
        if (entry.uri != null)
        {
          thumb.setClipToOutline(true);
          int px = Math.round(40 * getResources().getDisplayMetrics().density);
          ThumbnailLoader.loadInto(getContext(), thumb, entry.uri, px, R.drawable.ic_toolbar_grid);
          thumb.setVisibility(View.VISIBLE);
        }
        else
        {
          thumb.setTag(null);
          thumb.setVisibility(View.GONE);
        }
      }

      View pinBtn = v.findViewById(R.id.clipboard_entry_addpin);
      if (pinBtn != null)
      {
        pinBtn.setVisibility(entry.uri == null ? View.VISIBLE : View.GONE);
        pinBtn.setOnClickListener(view -> pin_entry(pos));
      }
      v.findViewById(R.id.clipboard_entry_paste).setOnClickListener(view -> paste_entry(pos));
      View deleteBtn = v.findViewById(R.id.clipboard_entry_delete);
      if (deleteBtn != null)
        deleteBtn.setOnClickListener(view -> delete_entry(pos));
      return v;
    }
  }

  static String formatMimeSubtype(String mime)
  {
    if (mime == null || mime.isEmpty())
      return "IMG";
    int slash = mime.indexOf('/');
    String sub = (slash >= 0 && slash + 1 < mime.length())
        ? mime.substring(slash + 1) : mime;
    int plus = sub.indexOf('+');
    if (plus > 0)
      sub = sub.substring(0, plus);
    return sub.toUpperCase(java.util.Locale.ROOT);
  }
}
