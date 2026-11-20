package com.lakmal.keycraftsinhala.prefs;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ArrayAdapter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import com.lakmal.keycraftsinhala.*;
import org.json.JSONException;
import org.json.JSONObject;

public class LayoutsPreference extends ListGroupPreference<LayoutsPreference.Layout>
{
  static final String KEY = "layouts";
  static final List<Layout> DEFAULT =
    Collections.singletonList((Layout)new SystemLayout());
  static final ListGroupPreference.Serializer<Layout> SERIALIZER =
    new Serializer();

  /** Text displayed for each layout in the dialog list. */
  String[] _layout_display_names;

  public LayoutsPreference(Context ctx, AttributeSet attrs)
  {
    super(ctx, attrs);
    setKey(KEY);
    Resources res = ctx.getResources();
    _layout_display_names = res.getStringArray(R.array.pref_layout_entries);
  }

  /** Obtained from [res/values/layouts.xml]. */
  static List<String> _unsafe_layout_ids_str = null;
  static TypedArray _unsafe_layout_ids_res = null;

  /** Layout internal names. Contains "system" and "custom". */
  public static List<String> get_layout_names(Resources res)
  {
    if (_unsafe_layout_ids_str == null)
      _unsafe_layout_ids_str = Arrays.asList(
          res.getStringArray(R.array.pref_layout_values));
    return _unsafe_layout_ids_str;
  }

  /** Layout resource id for a layout name. [-1] if not found. */
  public static int layout_id_of_name(Resources res, String name)
  {
    if (_unsafe_layout_ids_res == null)
      _unsafe_layout_ids_res = res.obtainTypedArray(R.array.layout_ids);
    int i = get_layout_names(res).indexOf(name);
    if (i >= 0)
      return _unsafe_layout_ids_res.getResourceId(i, 0);
    return -1;
  }

  /** [null] for the "system" layout. */
  public static List<KeyboardData> load_from_preferences(Resources res, SharedPreferences prefs)
  {
    List<KeyboardData> layouts = new ArrayList<KeyboardData>();
    for (Layout l : load_from_preferences(KEY, prefs, DEFAULT, SERIALIZER))
    {
      if (l instanceof NamedLayout)
        layouts.add(layout_of_string(res, ((NamedLayout)l).name));
      else if (l instanceof CustomLayout)
        layouts.add(((CustomLayout)l).parsed);
      else // instanceof SystemLayout
        layouts.add(null);
    }
    return layouts;
  }

  /** Does not call [prefs.commit()]. */
  public static void save_to_preferences(SharedPreferences.Editor prefs, List<Layout> items)
  {
    save_to_preferences(KEY, prefs, items, SERIALIZER);
  }

  public static KeyboardData layout_of_string(Resources res, String name)
  {
    int id = layout_id_of_name(res, name);
    if (id > 0)
      return KeyboardData.load(res, id);
    // Might happen when the app is downgraded, return the system layout.
    return null;
  }

  @Override
  protected void onSetInitialValue(boolean restoreValue, Object defaultValue)
  {
    super.onSetInitialValue(restoreValue, defaultValue);
    if (_values.size() == 0)
      set_values(new ArrayList<Layout>(DEFAULT), false);
  }

  String label_of_layout(Layout l)
  {
    if (l instanceof NamedLayout)
    {
      String lname = ((NamedLayout)l).name;
      int value_i = get_layout_names(getContext().getResources()).indexOf(lname);
      return value_i < 0 ? lname : _layout_display_names[value_i];
    }
    else if (l instanceof CustomLayout)
    {
      // Use the layout's name if possible
      CustomLayout cl = (CustomLayout)l;
      if (cl.parsed != null && cl.parsed.name != null
          && !cl.parsed.name.equals(""))
        return cl.parsed.name;
      else
        return getContext().getString(R.string.pref_layout_e_custom);
    }
    else // instanceof SystemLayout
      return getContext().getString(R.string.pref_layout_e_system);
  }

  @Override
  String label_of_value(Layout value, int i)
  {
    return getContext().getString(R.string.pref_layouts_item, i + 1,
        label_of_layout(value));
  }

  @Override
  AddButton on_attach_add_button(AddButton prev_btn)
  {
    if (prev_btn == null)
      return new LayoutsAddButton(getContext());
    return prev_btn;
  }

  @Override
  boolean should_allow_remove_item(Layout value)
  {
    return (_values.size() > 1 && !(value instanceof CustomLayout));
  }

  @Override
  ListGroupPreference.Serializer<Layout> get_serializer() { return SERIALIZER; }

  void select_dialog(final SelectionCallback callback)
  {
    final Context ctx = getContext();
    final List<String> names = get_layout_names(ctx.getResources());
    final int count = Math.min(names.size(), _layout_display_names.length);

    final List<Integer> filteredIndices = new ArrayList<>();
    final List<String> filteredLabels = new ArrayList<>();
    final ArrayAdapter<String> adapter = new ArrayAdapter<>(
        ctx, android.R.layout.simple_list_item_1, filteredLabels);

    final int[] activeFilter = new int[] { 0 }; // 0=All, 1=Sinhala, 2=Latin, 3=Dev
    final String[] queryHolder = new String[] { "" };

    final Runnable rebuildFilter = () -> {
      filteredIndices.clear();
      filteredLabels.clear();
      String q = queryHolder[0].toLowerCase(java.util.Locale.ROOT).trim();
      for (int i = 0; i < count; i++)
      {
        String internalName = names.get(i);
        String label = _layout_display_names[i];
        boolean matchesCat = true;
        switch (activeFilter[0])
        {
          case 1:
            matchesCat = internalName.contains("sinhala") || internalName.contains("tamil")
                || label.contains("සිංහල") || label.contains("தமிழ்");
            break;
          case 2:
            matchesCat = internalName.startsWith("latn_") || internalName.startsWith("latin_")
                || "system".equals(internalName);
            break;
          case 3:
            matchesCat = internalName.contains("termux") || internalName.contains("apl")
                || internalName.contains("bqn") || "custom".equals(internalName)
                || internalName.contains("dvorak") || internalName.contains("colemak")
                || internalName.contains("workman");
            break;
        }
        boolean matchesQuery = q.isEmpty()
            || label.toLowerCase(java.util.Locale.ROOT).contains(q)
            || internalName.toLowerCase(java.util.Locale.ROOT).contains(q);
        if (matchesCat && matchesQuery)
        {
          filteredIndices.add(i);
          filteredLabels.add(label);
        }
      }
      adapter.notifyDataSetChanged();
    };

    float density = ctx.getResources().getDisplayMetrics().density;
    int dp8 = Math.round(8 * density);
    int dp12 = Math.round(12 * density);

    android.widget.LinearLayout root = new android.widget.LinearLayout(ctx);
    root.setOrientation(android.widget.LinearLayout.VERTICAL);
    root.setPadding(dp12, dp8, dp12, dp8);

    android.widget.EditText searchInput = new android.widget.EditText(ctx);
    searchInput.setHint(R.string.pref_layouts_search_hint);
    searchInput.setSingleLine(true);
    searchInput.setMinHeight(Math.round(48 * density));
    searchInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
    searchInput.addTextChangedListener(new android.text.TextWatcher()
    {
      @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
      @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
      @Override
      public void afterTextChanged(android.text.Editable s)
      {
        queryHolder[0] = s == null ? "" : s.toString();
        rebuildFilter.run();
      }
    });
    root.addView(searchInput, new android.widget.LinearLayout.LayoutParams(-1, -2));

    android.widget.HorizontalScrollView chipScroll = new android.widget.HorizontalScrollView(ctx);
    chipScroll.setHorizontalScrollBarEnabled(false);
    android.widget.LinearLayout chipRow = new android.widget.LinearLayout(ctx);
    chipRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
    chipRow.setPadding(0, dp8, 0, dp8);
    int[] filterLabels = new int[] {
      R.string.pref_layouts_filter_all,
      R.string.pref_layouts_filter_sinhala,
      R.string.pref_layouts_filter_latin,
      R.string.pref_layouts_filter_dev
    };
    final List<android.widget.Button> filterBtns = new ArrayList<>();
    for (int f = 0; f < filterLabels.length; f++)
    {
      final int idx = f;
      android.widget.Button b = new android.widget.Button(ctx);
      b.setText(filterLabels[f]);
      b.setAllCaps(false);
      b.setMinHeight(Math.round(48 * density));
      b.setMinWidth(Math.round(48 * density));
      b.setAlpha(f == 0 ? 1.0f : 0.65f);
      b.setOnClickListener(v -> {
        activeFilter[0] = idx;
        for (int j = 0; j < filterBtns.size(); j++)
          filterBtns.get(j).setAlpha(j == idx ? 1.0f : 0.65f);
        rebuildFilter.run();
      });
      filterBtns.add(b);
      chipRow.addView(b);
    }
    chipScroll.addView(chipRow);
    root.addView(chipScroll, new android.widget.LinearLayout.LayoutParams(-1, -2));

    android.widget.ListView listView = new android.widget.ListView(ctx);
    listView.setAdapter(adapter);
    root.addView(listView, new android.widget.LinearLayout.LayoutParams(-1, 0, 1f));

    rebuildFilter.run();

    final AlertDialog dialog = new AlertDialog.Builder(ctx)
      .setView(root)
      .setNegativeButton(android.R.string.cancel, null)
      .create();
    listView.setOnItemClickListener((parent, view, pos, id) -> {
      if (pos < 0 || pos >= filteredIndices.size())
        return;
      int origIdx = filteredIndices.get(pos);
      String name = names.get(origIdx);
      dialog.dismiss();
      switch (name)
      {
        case "system":
          callback.select(new SystemLayout());
          break;
        case "custom":
          select_custom(callback, read_initial_custom_layout());
          break;
        default:
          callback.select(new NamedLayout(name));
          break;
      }
    });
    dialog.show();
  }

  /** Dialog for specifying a custom layout. [initial_text] is the layout
      description when modifying a layout. */
  void select_custom(final SelectionCallback callback, String initial_text)
  {
    boolean allow_remove = callback.allow_remove() && _values.size() > 1;
    CustomLayoutEditDialog.show(getContext(), initial_text, allow_remove,
        new CustomLayoutEditDialog.Callback()
        {
          public void select(String text)
          {
            if (text == null)
              callback.select(null);
            else
              callback.select(CustomLayout.parse(text));
          }

          public String validate(String text)
          {
            try
            {
              KeyboardData.load_string_exn(text);
              return null; // Validation passed
            }
            catch (Exception e)
            {
              return e.getMessage();
            }
          }
        });
  }

  /** Called when modifying a layout. Custom layouts behave differently. */
  @Override
  void select(final SelectionCallback callback, Layout prev_layout)
  {
    if (prev_layout != null && prev_layout instanceof CustomLayout)
      select_custom(callback, ((CustomLayout)prev_layout).xml);
    else
      select_dialog(callback);
  }

  /** The initial text for the custom layout entry box. The qwerty_us layout is
      a good default and contains a bit of documentation. */
  String read_initial_custom_layout()
  {
    try
    {
      Resources res = getContext().getResources();
      return Utils.read_all_utf8(res.openRawResource(R.raw.latn_qwerty_us));
    }
    catch (Exception _e)
    {
      return "";
    }
  }

  class LayoutsAddButton extends AddButton
  {
    public LayoutsAddButton(Context ctx)
    {
      super(ctx);
      setLayoutResource(R.layout.pref_layouts_add_btn);
    }
  }

  /** A layout selected by the user. The only implementations are
      [NamedLayout], [SystemLayout] and [CustomLayout]. */
  public interface Layout {}

  public static final class SystemLayout implements Layout
  {
    public SystemLayout() {}
  }

  /** The name of a layout defined in [srcs/layouts]. */
  public static final class NamedLayout implements Layout
  {
    public final String name;
    public NamedLayout(String n) { name = n; }
  }

  /** The XML description of a custom layout. */
  public static final class CustomLayout implements Layout
  {
    public final String xml;
    /** Might be null. */
    public final KeyboardData parsed;
    public CustomLayout(String xml_, KeyboardData k) { xml = xml_; parsed = k; }
    public static CustomLayout parse(String xml)
    {
      KeyboardData parsed = null;
      try { parsed = KeyboardData.load_string_exn(xml); }
      catch (Exception e) {}
      return new CustomLayout(xml, parsed);
    }
  }

  /** Named layouts are serialized to strings and custom layouts to JSON
      objects with a [kind] field. */
  public static class Serializer implements ListGroupPreference.Serializer<Layout>
  {
    public Layout load_item(Object obj) throws JSONException
    {
      if (obj instanceof String)
      {
        String name = (String)obj;
        if (name.equals("system"))
          return new SystemLayout();
        return new NamedLayout(name);
      }
      JSONObject obj_ = (JSONObject)obj;
      switch (obj_.getString("kind"))
      {
        case "custom": return CustomLayout.parse(obj_.getString("xml"));
        case "system": default: return new SystemLayout();
      }
    }

    public Object save_item(Layout v) throws JSONException
    {
      if (v instanceof NamedLayout)
        return ((NamedLayout)v).name;
      if (v instanceof CustomLayout)
        return new JSONObject().put("kind", "custom")
          .put("xml", ((CustomLayout)v).xml);
      return new JSONObject().put("kind", "system");
    }
  }
}
