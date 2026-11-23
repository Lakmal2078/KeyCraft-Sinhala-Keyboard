package com.lakmal.keycraftsinhala;

import android.app.AlertDialog;
import android.inputmethodservice.InputMethodService;
import android.os.IBinder;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Locale;

public final class Utils
{
  /** Turn the first letter of a string uppercase. */
  public static String capitalize_string(String s)
  {
    if (s.length() < 1)
      return s;
    // Make sure not to cut a code point in half
    int i = s.offsetByCodePoints(0, 1);
    return s.substring(0, i).toUpperCase(Locale.getDefault()) + s.substring(i);
  }

  /** Like [dialog.show()] but properly configure layout params when called
      from an IME. [token] is the input view's [getWindowToken()]. */
  public static void show_dialog_on_ime(AlertDialog dialog,
      InputMethodService ims)
  {
    if (dialog == null || ims == null)
      return;
    android.app.Dialog imeDialog = ims.getWindow();
    Window imeWin = (imeDialog != null) ? imeDialog.getWindow() : null;
    View decor = (imeWin != null) ? imeWin.getDecorView() : null;
    IBinder token = (decor != null) ? decor.getWindowToken() : null;
    if (token == null)
      return;
    show_dialog_on_ime(dialog, token);
  }

  public static void show_dialog_on_ime(AlertDialog dialog, IBinder token)
  {
    if (dialog == null || token == null)
      return;
    Window win = dialog.getWindow();
    if (win == null)
      return;
    WindowManager.LayoutParams lp = win.getAttributes();
    lp.token = token;
    lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
    win.setAttributes(lp);
    win.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
    dialog.show();
  }

  public static String read_all_utf8(InputStream inp) throws Exception
  {
    InputStreamReader reader = new InputStreamReader(inp, "UTF-8");
    StringBuilder out = new StringBuilder();
    int buff_length = 8000;
    char[] buff = new char[buff_length];
    int l;
    while ((l = reader.read(buff, 0, buff_length)) != -1)
      out.append(buff, 0, l);
    return out.toString();
  }

  public static byte[] read_all_bytes(InputStream inp) throws IOException
  {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buff = new byte[128000];
    int l;
    while ((l = inp.read(buff)) != -1)
      out.write(buff, 0, l);
    return out.toByteArray();
  }

  /**
   * Apply WindowInsetsCompat padding for system bars and display cutouts while preserving the
   * view's initial XML padding across repeated inset dispatches.
   */
  public static void applySafeSystemBarInsets(View target, boolean clipToPaddingFalse)
  {
    applySafeSystemBarInsets(target, clipToPaddingFalse, false);
  }

  public static void applySafeSystemBarInsets(
      View target, boolean clipToPaddingFalse, boolean includeIme)
  {
    if (target == null)
      return;
    if (clipToPaddingFalse && target instanceof ViewGroup)
      ((ViewGroup)target).setClipToPadding(false);
    final int baseLeft = target.getPaddingLeft();
    final int baseTop = target.getPaddingTop();
    final int baseRight = target.getPaddingRight();
    final int baseBottom = target.getPaddingBottom();
    final int mask = WindowInsetsCompat.Type.systemBars()
        | WindowInsetsCompat.Type.displayCutout()
        | (includeIme ? WindowInsetsCompat.Type.ime() : 0);
    ViewCompat.setOnApplyWindowInsetsListener(target, (v, windowInsets) -> {
      Insets insets = windowInsets.getInsets(mask);
      v.setPadding(
          baseLeft + insets.left,
          baseTop + insets.top,
          baseRight + insets.right,
          baseBottom + insets.bottom);
      return windowInsets;
    });
    if (target.isAttachedToWindow())
    {
      ViewCompat.requestApplyInsets(target);
    }
    else
    {
      target.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener()
      {
        @Override
        public void onViewAttachedToWindow(View v)
        {
          v.removeOnAttachStateChangeListener(this);
          ViewCompat.requestApplyInsets(v);
        }

        @Override
        public void onViewDetachedFromWindow(View v) {}
      });
    }
  }
}
