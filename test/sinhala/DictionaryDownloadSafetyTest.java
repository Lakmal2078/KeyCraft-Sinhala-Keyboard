package com.lakmal.keycraftsinhala.dict;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;
import static org.junit.Assert.*;

public class DictionaryDownloadSafetyTest
{
  @Test
  public void acceptsDictionaryBytesWithinLimit() throws Exception
  {
    byte[] expected = "dict".getBytes(StandardCharsets.UTF_8);
    assertArrayEquals(expected, DictionaryListView.read_bounded(
        new ByteArrayInputStream(expected), expected.length));
  }

  @Test
  public void rejectsDictionaryBytesOverLimit()
  {
    byte[] input = "1234".getBytes(StandardCharsets.UTF_8);
    try
    {
      DictionaryListView.read_bounded(new ByteArrayInputStream(input), 3);
      fail("Expected a dictionary-size IOException");
    }
    catch (IOException expected)
    {
      assertTrue(expected.getMessage().contains("maximum size"));
    }
  }

  @Test
  public void bundledDictionariesMatchPinnedSha256Digests() throws Exception
  {
    for (String dictName : new String[] { "en_US", "si" })
    {
      String expectedSha256 = SupportedDictionaries.getPinnedSha256(dictName);
      assertNotNull("Expected pinned SHA-256 for " + dictName, expectedSha256);
      byte[] data = Files.readAllBytes(new File("assets/dict/" + dictName + ".dict").toPath());
      DictionaryListView.verifyDictionaryIntegrity(dictName, data, expectedSha256);
    }
  }

  @Test
  public void tamperedDictionaryBytesFailVerification()
  {
    byte[] original = "sample-dictionary-payload".getBytes(StandardCharsets.UTF_8);
    // SHA-256("sample-dictionary-payload")
    String sha256 = "65d63195461489356e9896815c4527481122e963d551e451e95cb168d9b8f8c4";
    try
    {
      byte[] tampered = original.clone();
      tampered[0] ^= 0x01;
      DictionaryListView.verifyDictionaryIntegrity("sample", tampered, sha256);
      fail("Expected SecurityException for tampered dictionary payload");
    }
    catch (SecurityException expected)
    {
      assertTrue(expected.getMessage().contains("mismatch"));
    }
  }

  @Test
  public void missingOrMalformedPinnedDigestFailsClosed()
  {
    byte[] payload = "abc".getBytes(StandardCharsets.UTF_8);
    for (String badDigest : new String[] { null, "", "   ", "deadbeef", "zz".repeat(32) })
    {
      try
      {
        DictionaryListView.verifyDictionaryIntegrity("fr", payload, badDigest);
        fail("Expected SecurityException for digest: " + badDigest);
      }
      catch (SecurityException expected)
      {
        assertNotNull(expected.getMessage());
      }
    }
  }

  @Test
  public void hasPinnedSha256MatchesSupportedLocales()
  {
    assertTrue(SupportedDictionaries.hasPinnedSha256("en_US"));
    assertTrue(SupportedDictionaries.hasPinnedSha256("si"));
    assertFalse(SupportedDictionaries.hasPinnedSha256("fr"));
    assertFalse(SupportedDictionaries.hasPinnedSha256(""));
    assertFalse(SupportedDictionaries.hasPinnedSha256(null));
  }
}
