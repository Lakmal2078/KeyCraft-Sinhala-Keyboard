package com.lakmal.keycraftsinhala;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
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
    ClipboardPinView v = (ClipboardPinView)((ViewGroup)getParent().getParent()).findViewById(R.id.clipboard_pin_view);
    v.add_entry(clip);
    _service.remove_history_entry(clip);
  }

  /** Paste the specified entry, then remove it from clipboard history. An
      image is removed only when the focused app accepts it. */
  public void paste_entry(int pos)
  {
    if (_service == null || pos < 0 || pos >= _entries.size())
      return;
    ClipboardHistoryService.HistoryEntry entry = _entries.get(pos);
    if (entry.uri != null)
    {
      if (ClipboardHistoryService.paste_image(entry.uri, entry.mime))
        _service.remove_entry(entry);
      return;
    }
    String clip = entry.content;
    if (_service.contains_history_entry(clip))
    {
      ClipboardHistoryService.paste(clip);
      _service.remove_history_entry(clip);
    }
    else
      update_data();
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
    invalidate();
  }

  @Override
  protected void onAttachedToWindow()
  {
    super.onAttachedToWindow();
    if (_service != null)
    {
      _service.set_on_clipboard_history_change(this);
      update_data();
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
      ((TextView)v.findViewById(R.id.clipboard_entry_text))
        .setText(_history.get(pos));
      v.findViewById(R.id.clipboard_entry_addpin).setOnClickListener(
          new View.OnClickListener()
          {
            @Override
            public void onClick(View v) { pin_entry(pos); }
          });
      v.findViewById(R.id.clipboard_entry_paste).setOnClickListener(
          new View.OnClickListener()
          {
            @Override
            public void onClick(View v) { paste_entry(pos); }
          });
      return v;
    }
  }
}
