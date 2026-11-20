package com.lakmal.keycraftsinhala;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewParent;
import android.widget.CheckBox;
import android.widget.CompoundButton;

final class ClipboardHistoryCheckBox extends CheckBox
  implements CompoundButton.OnCheckedChangeListener
{
  public ClipboardHistoryCheckBox(Context ctx, AttributeSet attrs)
  {
    super(ctx, attrs);
    setChecked(Config.globalConfig().clipboard_history_enabled);
    setOnCheckedChangeListener(this);
  }

  /** Refresh the cached clipboard pane after settings are reset externally. */
  void refreshFromConfig()
  {
    boolean enabled = Config.globalConfig().clipboard_history_enabled;
    setOnCheckedChangeListener(null);
    setChecked(enabled);
    setOnCheckedChangeListener(this);
  }

  @Override
  public void onCheckedChanged(CompoundButton _v, boolean isChecked)
  {
    ClipboardHistoryService.set_history_enabled(isChecked);
    ViewParent p = getParent();
    while (p instanceof View)
    {
      View hv = ((View)p).findViewById(R.id.clipboard_history_view);
      if (hv instanceof ClipboardHistoryView)
      {
        ((ClipboardHistoryView)hv).refreshEmptyStates();
        break;
      }
      p = p.getParent();
    }
  }
}
