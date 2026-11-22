package com.lakmal.keycraftsinhala.dict;

import android.content.res.Resources;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import com.lakmal.keycraftsinhala.R;

/** Access arrays in [dictionaries.xml]. */
public class SupportedDictionaries
{
  private static final Map<String, String> PINNED_SHA256;

  static
  {
    Map<String, String> hashes = new HashMap<>();
    hashes.put("en_US", "1e41164c29ca4fa1e338d7f0feab8bf64d3a6893c4ae510cede1b14ae9025b6d");
    hashes.put("si", "1e1bef532de5cfc34c0b492b938159fb17bac97a58f92a8aae3ac80bb856a59a");
    PINNED_SHA256 = Collections.unmodifiableMap(hashes);
  }

  public String[] locales;
  public String[] names;
  public int[] sizes;

  SupportedDictionaries(Resources res)
  {
    locales = res.getStringArray(R.array.dictionaries_locale);
    names = res.getStringArray(R.array.dictionaries_name);
    sizes = res.getIntArray(R.array.dictionaries_size);
  }

  public static SupportedDictionaries get(Resources res)
  {
    if (_cached == null)
      _cached = new SupportedDictionaries(res);
    return _cached;
  }
  static SupportedDictionaries _cached = null;

  /** Find the index for a given dictionary name. Return [-1] if not found. */
  public int find(String dict_name)
  {
    if (dict_name == null)
      return -1;
    int i = Arrays.binarySearch(locales, dict_name);
    return (i < 0) ? -1 : i;
  }

  public int length() { return locales.length; }

  public String dict_name(int i) { return locales[i]; }
  public String display_name(int i) { return names[i]; }
  public int size(int i) { return sizes[i]; }

  public String get_display_name(String dict_name)
  {
    int i = find(dict_name);
    return (i >= 0) ? names[i] : dict_name;
  }

  /**
   * Returns the trusted lowercase SHA-256 hex digest for [dict_name], or null if
   * no digest has been pinned for that locale.
   */
  public static String getPinnedSha256(String dict_name)
  {
    return (dict_name == null) ? null : PINNED_SHA256.get(dict_name);
  }
}
