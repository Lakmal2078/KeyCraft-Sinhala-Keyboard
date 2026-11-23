package com.lakmal.keycraftsinhala.dict;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import com.lakmal.keycraftsinhala.R;

public class DictionariesActivity extends Activity
{
  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.dictionaries_activity);
    com.lakmal.keycraftsinhala.Utils.applySafeSystemBarInsets(
        findViewById(R.id.dictionaries_scroll_view), true, true);

    EditText searchInput = findViewById(R.id.dictionaries_search_input);
    DictionaryListView deviceList = findViewById(R.id.dictionaries_device_list);
    DictionaryListView otherList = findViewById(R.id.dictionaries_other_list);
    View emptyView = findViewById(R.id.dictionaries_empty_view);

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
          int visible = 0;
          if (deviceList != null) visible += deviceList.filter(q);
          if (otherList != null) visible += otherList.filter(q);
          if (emptyView != null)
            emptyView.setVisibility(visible == 0 ? View.VISIBLE : View.GONE);
        }
      });
    }
  }
}
