package com.lakmal.keycraftsinhala;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads scaled clipboard and GIF image thumbnails asynchronously off the UI
 * thread with a bounded LRU cache and view-tag verification to prevent stale
 * recycled row flashes or OOM leaks.
 */
final class ThumbnailLoader
{
  private static final int MAX_CACHE_ENTRIES = 24;
  private static final LruCache<String, Bitmap> CACHE = new LruCache<>(MAX_CACHE_ENTRIES);
  private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, r -> {
    Thread t = new Thread(r, "KeyCraft-ThumbLoader");
    t.setDaemon(true);
    return t;
  });
  private static final Handler MAIN = new Handler(Looper.getMainLooper());

  private ThumbnailLoader() {}

  static void loadInto(Context context, ImageView imageView, Uri uri, int targetPx,
      int fallbackResId)
  {
    if (imageView == null)
      return;
    if (uri == null || context == null)
    {
      imageView.setTag(null);
      imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
      imageView.setImageResource(fallbackResId);
      return;
    }

    final int px = Math.max(1, targetPx);
    final String cacheKey = uri.toString() + "@" + px;
    imageView.setTag(cacheKey);

    Bitmap cached = CACHE.get(cacheKey);
    if (cached != null && !cached.isRecycled())
    {
      imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
      imageView.setImageBitmap(cached);
      return;
    }

    imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
    imageView.setImageResource(fallbackResId);

    Context app = context.getApplicationContext();
    final Context appCtx = (app != null) ? app : context;
    EXECUTOR.execute(() -> {
      Bitmap bmp = decodeScaledBitmap(appCtx, uri, px);
      if (bmp != null)
        CACHE.put(cacheKey, bmp);
      MAIN.post(() -> {
        if (!cacheKey.equals(imageView.getTag()))
          return;
        if (bmp != null && !bmp.isRecycled())
        {
          imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
          imageView.setImageBitmap(bmp);
        }
      });
    });
  }

  static Bitmap decodeScaledBitmap(Context ctx, Uri uri, int targetPx)
  {
    try (InputStream is = ctx.getContentResolver().openInputStream(uri))
    {
      if (is == null)
        return null;
      BitmapFactory.Options opts = new BitmapFactory.Options();
      opts.inSampleSize = 4;
      Bitmap raw = BitmapFactory.decodeStream(is, null, opts);
      if (raw == null)
        return null;
      int px = Math.max(1, targetPx);
      Bitmap scaled = Bitmap.createScaledBitmap(raw, px, px, true);
      if (scaled != raw)
        raw.recycle();
      return scaled;
    }
    catch (Exception | OutOfMemoryError _e)
    {
      return null;
    }
  }
}
