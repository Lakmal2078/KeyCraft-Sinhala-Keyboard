package com.lakmal.keycraftsinhala;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.UserManager;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONArray;
import org.json.JSONException;

/** Credential-protected storage for explicitly pinned clipboard text. */
final class PinnedClipboardStore
{
  private static final String PREFS = "pinned_clipboards";
  private static final String LEGACY = "pinned";
  private static final String ENCRYPTED = "pinned_aes_gcm_v1";
  private static final String KEY_ALIAS = "keycraft.pinned_clipboard.v1";
  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;

  private final Context context;

  PinnedClipboardStore(Context context)
  {
    this.context = context.getApplicationContext();
  }

  List<String> load() throws GeneralSecurityException, IOException, JSONException
  {
    SharedPreferences prefs = preferences();
    String encrypted = prefs.getString(ENCRYPTED, null);
    if (encrypted != null)
      return decode(decrypt(encrypted));
    String legacy = prefs.getString(LEGACY, null);
    if (legacy == null)
      return new ArrayList<>();
    List<String> entries = decode(legacy);
    save(entries);
    return entries;
  }

  void save(List<String> entries) throws GeneralSecurityException, IOException
  {
    SharedPreferences prefs = preferences();
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE, key(true));
    byte[] iv = cipher.getIV();
    if (iv.length != IV_BYTES)
      throw new GeneralSecurityException("Unexpected GCM IV length");
    byte[] plaintext = new JSONArray(entries).toString().getBytes(StandardCharsets.UTF_8);
    byte[] ciphertext = cipher.doFinal(plaintext);
    byte[] envelope = new byte[iv.length + ciphertext.length];
    System.arraycopy(iv, 0, envelope, 0, iv.length);
    System.arraycopy(ciphertext, 0, envelope, iv.length, ciphertext.length);
    // One preference transaction replaces the legacy plaintext with authenticated ciphertext.
    if (!prefs.edit().putString(ENCRYPTED, Base64.encodeToString(envelope, Base64.NO_WRAP))
        .remove(LEGACY).commit())
      throw new IOException("Could not persist pinned clipboard");
  }

  private SharedPreferences preferences() throws IOException
  {
    UserManager user = context.getSystemService(UserManager.class);
    if (context.isDeviceProtectedStorage() || user == null || !user.isUserUnlocked())
      throw new IOException("Pinned clipboard requires an unlocked user");
    return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }

  private static String decrypt(String encrypted) throws GeneralSecurityException, IOException
  {
    byte[] envelope = Base64.decode(encrypted, Base64.NO_WRAP);
    if (envelope.length < IV_BYTES + TAG_BITS / 8)
      throw new GeneralSecurityException("Invalid pinned clipboard envelope");
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.DECRYPT_MODE, key(false), new GCMParameterSpec(TAG_BITS, envelope, 0, IV_BYTES));
    byte[] plaintext = cipher.doFinal(envelope, IV_BYTES, envelope.length - IV_BYTES);
    return new String(plaintext, StandardCharsets.UTF_8);
  }

  private static synchronized SecretKey key(boolean create)
      throws GeneralSecurityException, IOException
  {
    KeyStore store = KeyStore.getInstance("AndroidKeyStore");
    store.load(null);
    SecretKey existing = (SecretKey)store.getKey(KEY_ALIAS, null);
    if (existing != null)
      return existing;
    if (!create)
      throw new GeneralSecurityException("Pinned clipboard key unavailable");
    KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
    generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .build());
    return generator.generateKey();
  }

  private static List<String> decode(String json) throws JSONException
  {
    JSONArray array = new JSONArray(json);
    List<String> entries = new ArrayList<>(array.length());
    for (int i = 0; i < array.length(); i++)
      entries.add(array.getString(i));
    return entries;
  }
}
