package com.lakmal.keycraftsinhala;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import androidx.test.platform.app.InstrumentationRegistry;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;
import org.json.JSONArray;
import org.junit.Test;

public class PinnedClipboardStoreTest
{
  @Test
  public void migratesLegacyPinsAndAuthenticatesFreshCiphertext() throws Exception
  {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    SharedPreferences prefs = context.getApplicationContext()
        .getSharedPreferences("pinned_clipboards", Context.MODE_PRIVATE);
    String encryptedKey = "pinned_aes_gcm_v1";
    List<String> entries = Arrays.asList("synthetic clipboard", "සිංහල");
    try
    {
      prefs.edit().clear().putString("pinned", new JSONArray(entries).toString()).commit();
      PinnedClipboardStore store = new PinnedClipboardStore(context);
      assertEquals(entries, store.load());
      assertFalse(prefs.contains("pinned"));
      String first = prefs.getString(encryptedKey, "");
      assertFalse(first.contains(entries.get(0)));

      store.save(entries);
      String second = prefs.getString(encryptedKey, "");
      assertNotEquals(first, second);
      assertEquals(entries, new PinnedClipboardStore(context).load());

      byte[] envelope = Base64.decode(second, Base64.NO_WRAP);
      envelope[envelope.length - 1] ^= 1;
      prefs.edit().putString(encryptedKey, Base64.encodeToString(envelope, Base64.NO_WRAP)).commit();
      org.junit.Assert.assertThrows(GeneralSecurityException.class, store::load);
    }
    finally
    {
      prefs.edit().clear().commit();
    }
  }
}
