package com.lakmal.keycraftsinhala;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONException;

public final class ClipboardPinView extends NonScrollListView
{
  private List<String> _entries = new ArrayList<>();
  private final ClipboardPinEntriesAdapter _adapter;
  private final PinnedClipboardStore _store;

  public ClipboardPinView(Context ctx, AttributeSet attrs)
  {
    super(ctx, attrs);
    _store = new PinnedClipboardStore(ctx);
    _adapter = new ClipboardPinEntriesAdapter();
    setAdapter(_adapter);
  }

  @Override
  protected void onAttachedToWindow()
  {
    super.onAttachedToWindow();
    try
    {
      _entries = _store.load();
    }
    catch (GeneralSecurityException | IOException | JSONException | RuntimeException e)
    {
      _entries = new ArrayList<>();
    }
    _adapter.notifyDataSetChanged();
    notifySiblingEmptyStates();
  }

  /** Pin a clipboard and report whether the change was saved. */
  public boolean add_entry(String text)
  {
    List<String> updated = new ArrayList<>(_entries);
    updated.add(0, text);
    return persist(updated);
  }

  /** Remove the entry at index [pos] and persist the change. */
  public void remove_entry(int pos)
  {
    if (pos < 0 || pos >= _entries.size())
      return;
    List<String> updated = new ArrayList<>(_entries);
    updated.remove(pos);
    persist(updated);
  }

  private boolean persist(List<String> updated)
  {
    try
    {
      // Check that encrypted or legacy entries are readable before replacing them.
      if (!_store.load().equals(_entries))
        throw new IOException("Pinned clipboard changed");
      _store.save(updated);
    }
    catch (GeneralSecurityException | IOException | JSONException | RuntimeException e)
    {
      Toast.makeText(getContext(), R.string.clipboard_pin_storage_error, Toast.LENGTH_SHORT).show();
      return false;
    }
    _entries = updated;
    _adapter.notifyDataSetChanged();
    notifySiblingEmptyStates();
    invalidate();
    return true;
  }

  private void notifySiblingEmptyStates()
  {
    if (getParent() instanceof View)
    {
      View hv = ((View)getParent()).findViewById(R.id.clipboard_history_view);
      if (hv instanceof ClipboardHistoryView)
        ((ClipboardHistoryView)hv).refreshEmptyStates();
    }
  }

  /** Send the specified entry to the editor. */
  public void paste_entry(int pos)
  {
    ClipboardHistoryService.paste(_entries.get(pos));
  }

  class ClipboardPinEntriesAdapter extends BaseAdapter
  {
    public ClipboardPinEntriesAdapter() {}

    @Override
    public int getCount() { return _entries.size(); }
    @Override
    public Object getItem(int pos) { return _entries.get(pos); }
    @Override
    public long getItemId(int pos) { return _entries.get(pos).hashCode(); }

    @Override
    public View getView(final int pos, View v, ViewGroup _parent)
    {
      if (v == null)
        v = View.inflate(getContext(), R.layout.clipboard_pin_entry, null);
      ((TextView)v.findViewById(R.id.clipboard_pin_text))
        .setText(_entries.get(pos));
      v.findViewById(R.id.clipboard_pin_paste).setOnClickListener(
          new View.OnClickListener()
          {
            @Override
            public void onClick(View v) { paste_entry(pos); }
          });
      v.findViewById(R.id.clipboard_pin_remove).setOnClickListener(
          new View.OnClickListener()
          {
            @Override
            public void onClick(View v)
            {
              AlertDialog d = new AlertDialog.Builder(getContext())
                .setTitle(R.string.clipboard_remove_confirm)
                .setPositiveButton(R.string.clipboard_remove_confirmed,
                    new DialogInterface.OnClickListener(){
                      public void onClick(DialogInterface _dialog, int _which)
                      {
                        remove_entry(pos);
                      }
                    })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
              Utils.show_dialog_on_ime(d, v.getWindowToken());
            }
          });
      return v;
    }
  }
}
