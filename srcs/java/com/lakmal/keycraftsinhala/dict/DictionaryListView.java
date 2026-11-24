package com.lakmal.keycraftsinhala.dict;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import javax.net.ssl.HttpsURLConnection;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import juloo.cdict.Cdict;
import com.lakmal.keycraftsinhala.DeviceLocales;
import com.lakmal.keycraftsinhala.Logs;
import com.lakmal.keycraftsinhala.R;

public class DictionaryListView extends LinearLayout
{
  List<DictView> _dict_views;
  Dictionaries _dictionaries;
  Set<String> _pending = new HashSet<String>();

  // Bound decompressed dictionary data so a bad or compromised download cannot exhaust app memory.
  static final int MAX_DICTIONARY_BYTES = 64 * 1024 * 1024;

  public DictionaryListView(Context ctx, AttributeSet attrs)
  {
    super(ctx, attrs);
    setOrientation(LinearLayout.VERTICAL);
    _dictionaries = Dictionaries.instance(ctx);
    _dict_views = new ArrayList<DictView>();
    boolean device_locales =
      attrs.getAttributeBooleanValue(null, "device_locales", true);
    if (device_locales)
      inflate_views_device_locales(ctx);
    else
      inflate_views_all(ctx);
    refresh();
  }

  private boolean shouldShowDictionary(String dict_name)
  {
    return SupportedDictionaries.hasPinnedSha256(dict_name)
        || _dictionaries.get_installed().contains(dict_name);
  }

  void inflate_views_device_locales(Context ctx)
  {
    SupportedDictionaries ds = SupportedDictionaries.get(ctx.getResources());
    DeviceLocales locales = DeviceLocales.load(ctx);
    for (DeviceLocales.Loc loc : locales.installed)
    {
      if (loc.dictionary != null && shouldShowDictionary(loc.dictionary))
      {
        int idx = ds.find(loc.dictionary);
        if (idx >= 0)
          inflate_item(ctx, ds, idx);
      }
    }
  }

  void inflate_views_all(Context ctx)
  {
    SupportedDictionaries ds = SupportedDictionaries.get(ctx.getResources());
    for (int i = 0; i < ds.length(); i++)
    {
      if (shouldShowDictionary(ds.dict_name(i)))
        inflate_item(ctx, ds, i);
    }
  }

  void inflate_item(Context ctx, SupportedDictionaries ds, int i)
  {
    View v = LayoutInflater.from(ctx)
      .inflate(R.layout.dictionary_download_item, this, false);
    _dict_views.add(this.new DictView(v, ds, i));
    addView(v);
  }

  /** Filter dictionary items by display name or locale code. Returns count of visible items. */
  public int filter(String query)
  {
    String q = (query == null) ? "" : query.trim().toLowerCase(Locale.ROOT);
    int visible = 0;
    for (DictView d : _dict_views)
    {
      boolean show = q.isEmpty()
          || d.display_name.toLowerCase(Locale.ROOT).contains(q)
          || d.dict_name.toLowerCase(Locale.ROOT).contains(q);
      d.root_view.setVisibility(show ? View.VISIBLE : View.GONE);
      if (show)
        visible++;
    }
    return visible;
  }

  /** Update the "installed" status of item views. Meaning whether the
      "download" or "delete" button is shown. */
  void refresh()
  {
    Set<String> installed = _dictionaries.get_installed();
    for (DictView d : _dict_views)
      d.refresh(installed);
  }

  enum DownloadStatus
  {
    SUCCESS,
    UNVERIFIED,
    NETWORK_ERROR
  }

  void toggle_installed(String dict_name)
  {
    run_dictionary_action(dict_name, new Runnable()
        {
          public void run()
          {
            if (_dictionaries.get_installed().contains(dict_name))
              _dictionaries.uninstall(dict_name);
            else
            {
              DownloadStatus status = install_dictionary_with_status(dict_name);
              if (status == DownloadStatus.SUCCESS)
                post_toast(R.string.dictionaries_download_success);
              else if (status == DownloadStatus.UNVERIFIED)
                post_toast(R.string.dictionaries_download_unverified);
              else
                post_toast(R.string.dictionaries_download_failed);
            }
          }
        });
  }

  /** Run action [r] for dictionary [name] if no action is already running for
      that dictionary. Calls [refresh] after the action completed. */
  void run_dictionary_action(String name, Runnable r)
  {
    if (_pending.contains(name))
      return;
    _pending.add(name);
    (new Thread()
     {
       public void run()
       {
         try
         {
           r.run();
         }
         finally
         {
           post(new Runnable()
               {
                 public void run()
                 {
                   _pending.remove(name);
                   refresh();
                 }
               });
         }
       }
     }).start();
    refresh();
  }

  final class DictView implements View.OnClickListener
  {
    public final View root_view;
    public final String dict_name;
    public final String display_name;
    public final ImageView download_button;
    public final ProgressBar download_progress;

    public DictView(View view, SupportedDictionaries ds, int dict_index)
    {
      root_view = view;
      dict_name = ds.dict_name(dict_index);
      display_name = ds.display_name(dict_index);
      float size_mb = ds.size(dict_index) / 1048576.f;
      ((TextView)view.findViewById(R.id.dictionary_download_locale))
        .setText(display_name);
      ((TextView)view.findViewById(R.id.dictionary_download_size))
        .setText(getContext().getString(R.string.dictionary_download_size_format,
            NumberFormat.getInstance().format(size_mb)));
      download_button =
        (ImageView)view.findViewById(R.id.dictionary_download_button);
      download_progress =
        (ProgressBar)view.findViewById(R.id.dictionary_download_progress);
      if (download_progress != null)
        download_progress.setContentDescription(
            getContext().getString(R.string.dictionaries_downloading_desc));
      download_button.setOnClickListener(this);
    }

    public void refresh(Set<String> installed)
    {
      boolean pending = _pending.contains(dict_name);
      boolean is_installed = installed.contains(dict_name);
      if (download_progress != null)
        download_progress.setVisibility(pending ? View.VISIBLE : View.GONE);
      download_button.setVisibility(pending ? View.GONE : View.VISIBLE);
      if (!pending)
      {
        download_button.setImageResource(is_installed ? R.drawable.ic_delete : R.drawable.ic_download);
        download_button.setContentDescription(
            download_button.getContext().getString(
                is_installed ? R.string.dictionaries_delete_button : R.string.dictionaries_download_button));
      }
    }

    @Override
    public void onClick(View v)
    {
      toggle_installed(dict_name);
    }
  }

  static final String DICT_REPO_URL =
    "https://raw.githubusercontent.com/Julow/Unexpected-Keyboard-dictionaries/refs/heads/main";

  static URL url_of_dictionary(String dict_name)
      throws MalformedURLException
  {
    int format_version = 1;
    try
    {
      format_version = Cdict.format_version();
    }
    catch (Throwable t) {}
    return new URL(DICT_REPO_URL + "/v" + format_version + "/" + dict_name
        + ".dict");
  }

  /** Returns [true] on success. Fails closed unless the dictionary SHA-256 matches a pinned digest. */
  boolean install_dictionary_from_internet(String dict_name)
  {
    return install_dictionary_with_status(dict_name) == DownloadStatus.SUCCESS;
  }

  DownloadStatus install_dictionary_with_status(String dict_name)
  {
    String expectedSha256 = SupportedDictionaries.getPinnedSha256(dict_name);
    try
    {
      if (expectedSha256 == null || expectedSha256.isEmpty())
        throw new SecurityException("No pinned SHA-256 digest configured for dictionary: " + dict_name);
      // Remote files are compressed with gzip at rest. Do not use server side
      // compression and force decompression.
      URLConnection con = url_of_dictionary(dict_name).openConnection();
      if (!(con instanceof HttpsURLConnection))
        throw new SecurityException("Dictionary download requires HTTPS");
      ((HttpsURLConnection)con).setInstanceFollowRedirects(false);
      con.setConnectTimeout(15000);
      con.setReadTimeout(30000);
      con.setRequestProperty("Accept-Encoding", "identity");
      byte[] data;
      try (InputStream response = con.getInputStream();
           InputStream stream = new GZIPInputStream(response)) {
        data = read_bounded(stream, MAX_DICTIONARY_BYTES);
      }
      verifyDictionaryIntegrity(dict_name, data, expectedSha256);
      Cdict.of_bytes(data); // Structural and cyclic safety validation
      _dictionaries.install(dict_name, data);
      return DownloadStatus.SUCCESS;
    }
    catch (Exception e)
    {
      // If remote download is unavailable, check if the dictionary is bundled in assets
      try
      {
        byte[] data;
        try (InputStream inp = getContext().getAssets().open("dict/" + dict_name + ".dict")) {
          data = read_bounded(inp, MAX_DICTIONARY_BYTES);
        }
        verifyDictionaryIntegrity(dict_name, data, expectedSha256);
        Cdict.of_bytes(data);
        _dictionaries.install(dict_name, data);
        return DownloadStatus.SUCCESS;
      }
      catch (Exception ignored) {}
      Logs.exn("", e);
      return (e instanceof SecurityException)
          ? DownloadStatus.UNVERIFIED
          : DownloadStatus.NETWORK_ERROR;
    }
  }

  /**
   * Verifies [data] against a pinned lowercase/uppercase 64-character SHA-256 hex digest using a
   * constant-time comparison. Throws [SecurityException] if the digest is missing, malformed, or
   * does not match.
   */
  static void verifyDictionaryIntegrity(String dictName, byte[] data, String expectedSha256Hex)
  {
    if (data == null)
      throw new SecurityException("Dictionary payload is null for: " + dictName);
    if (expectedSha256Hex == null || expectedSha256Hex.trim().isEmpty())
      throw new SecurityException("No trusted SHA-256 digest pinned for dictionary: " + dictName);
    byte[] expectedBytes = decodeHexSha256(expectedSha256Hex.trim());
    if (expectedBytes == null)
      throw new SecurityException("Malformed pinned SHA-256 digest for dictionary: " + dictName);
    try
    {
      java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
      byte[] actualBytes = md.digest(data);
      if (!java.security.MessageDigest.isEqual(actualBytes, expectedBytes))
        throw new SecurityException("SHA-256 checksum mismatch for dictionary: " + dictName);
    }
    catch (java.security.NoSuchAlgorithmException e)
    {
      throw new SecurityException("SHA-256 unavailable for dictionary verification", e);
    }
  }

  private static byte[] decodeHexSha256(String hex)
  {
    if (hex == null || hex.length() != 64)
      return null;
    byte[] out = new byte[32];
    for (int i = 0; i < 32; i++)
    {
      int hi = Character.digit(hex.charAt(i * 2), 16);
      int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
      if (hi < 0 || lo < 0)
        return null;
      out[i] = (byte) ((hi << 4) | lo);
    }
    return out;
  }

  /** Read a dictionary with a caller-supplied hard byte limit. Does not close [input]. */
  static byte[] read_bounded(InputStream input, int maxBytes) throws IOException
  {
    if (maxBytes < 0)
      throw new IllegalArgumentException("maxBytes must not be negative");
    ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxBytes, 8192));
    byte[] buffer = new byte[8192];
    int total = 0;
    int read;
    while ((read = input.read(buffer)) != -1)
    {
      if (read > maxBytes - total)
        throw new IOException("Dictionary exceeds maximum size of " + maxBytes + " bytes");
      output.write(buffer, 0, read);
      total += read;
    }
    return output.toByteArray();
  }

  void post_toast(int msg_id)
  {
    post(new Runnable()
        {
          public void run()
          {
            Toast.makeText(getContext(), msg_id, Toast.LENGTH_SHORT).show();
          }
        });
  }
}
