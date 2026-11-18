package com.lakmal.keycraftsinhala;

import android.content.ClipDescription;
import android.net.Uri;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.core.view.inputmethod.InputConnectionCompat;
import androidx.core.view.inputmethod.InputContentInfoCompat;

/**
 * Sends image content (GIF, PNG, JPEG, WebP) to the focused app through
 * {@link InputConnection#commitContent}.
 *
 * The focused app must advertise the MIME type in
 * {@link EditorInfo#contentMimeTypes}; otherwise nothing is committed.
 * Only {@code content://} URIs are accepted so that arbitrary file paths are
 * never exposed to other apps.
 */
public final class ContentCommitter
{
  public static final String[] SUPPORTED_MIME_TYPES = {
    "image/gif", "image/png", "image/jpeg", "image/webp"
  };

  private ContentCommitter() {}

  public static boolean is_supported_mime(String mime)
  {
    if (mime == null)
      return false;
    for (String supported : SUPPORTED_MIME_TYPES)
    {
      if (supported.equals(mime))
        return true;
    }
    return false;
  }

  /** Whether the focused app advertised support for [mime]. */
  public static boolean target_accepts(EditorInfo info, String mime)
  {
    if (info == null || !is_supported_mime(mime))
      return false;
    String[] accepted = EditorInfoCompat.getContentMimeTypes(info);
    if (accepted == null)
      return false;
    for (String type : accepted)
    {
      if (type == null)
        continue;
      if (type.equals(mime))
        return true;
      // Wildcard such as "image/*".
      if (type.endsWith("/*") && mime.startsWith(type.substring(0, type.length() - 1)))
        return true;
    }
    return false;
  }

  /**
   * Commits [uri] to the focused app. Returns [false] when the connection is
   * missing, the URI is not content://, the MIME type is unsupported, or the
   * app did not advertise support.
   */
  public static boolean commit(InputConnection conn, EditorInfo info, Uri uri,
      String mime)
  {
    if (conn == null || uri == null || !"content".equals(uri.getScheme()))
      return false;
    if (!target_accepts(info, mime))
      return false;
    InputContentInfoCompat content = new InputContentInfoCompat(
        uri, new ClipDescription("KeyCraft content", new String[] {mime}), null);
    return InputConnectionCompat.commitContent(conn, info, content,
        InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null);
  }
}
