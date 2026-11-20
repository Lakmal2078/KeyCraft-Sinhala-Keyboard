package com.lakmal.keycraftsinhala;

import android.content.res.Resources;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Emoji
{
  private final KeyValue _kv;

  protected Emoji(String bytecode)
  {
    this._kv = new KeyValue(bytecode, KeyValue.Kind.String, 0, 0);
  }

  public KeyValue kv()
  {
    return _kv;
  }

  private final static List<Emoji> _all = new ArrayList<>();
  private final static List<List<Emoji>> _groups = new ArrayList<>();
  private final static HashMap<String, Emoji> _stringMap = new HashMap<>();
  private final static Map<String, String> KEYWORD_SYNONYMS = buildKeywordSynonyms();

  private static Map<String, String> buildKeywordSynonyms()
  {
    Map<String, String> m = new HashMap<>();
    m.put("❤️", "heart love red romantic");
    m.put("❤", "heart love red romantic");
    m.put("💕", "heart love pink");
    m.put("💖", "heart love sparkle");
    m.put("😍", "heart love eyes crush");
    m.put("🥰", "love happy hearts");
    m.put("😘", "kiss love heart");
    m.put("😂", "laugh lol funny tears joy haha");
    m.put("🤣", "laugh lol rofl funny haha");
    m.put("😊", "happy smile blush cute");
    m.put("😁", "grin happy smile");
    m.put("😄", "smile happy laugh");
    m.put("🙂", "smile happy");
    m.put("😉", "wink flirt");
    m.put("😭", "cry sad sob tears");
    m.put("😢", "sad cry tear");
    m.put("😔", "sad sorry down");
    m.put("😡", "angry mad");
    m.put("🔥", "fire lit hot flame");
    m.put("👍", "thumbs up good ok yes like");
    m.put("👎", "thumbs down bad no");
    m.put("👌", "ok good perfect");
    m.put("🙏", "pray thanks thank namaste ayubowan please");
    m.put("👏", "clap applause congrats");
    m.put("👋", "wave hi hello bye");
    m.put("🎉", "party celebrate congrats");
    m.put("✨", "sparkle star magic shine");
    m.put("⭐", "star favorite");
    m.put("🌟", "star glow");
    m.put("🌞", "sun morning good");
    m.put("☀", "sun sunny weather");
    m.put("🌙", "moon night sleep");
    m.put("☕", "coffee tea morning cup drink");
    m.put("🍵", "tea green cup drink");
    m.put("🎂", "cake birthday party");
    m.put("🌺", "flower hibiscus");
    m.put("🪷", "lotus flower");
    m.put("🇱🇰", "sri lanka lk flag");
    m.put("✅", "check done ok yes");
    m.put("⚠", "warning alert caution");
    return Collections.unmodifiableMap(m);
  }

  public static void init(Resources res)
  {
    if (!_all.isEmpty())
      return;

    try
    {
      InputStream inputStream = res.openRawResource(R.raw.emojis);
      BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
      String line;

      // Read emoji (until empty line)
      while (!(line = reader.readLine()).isEmpty())
      {
        Emoji e = new Emoji(line);
        _all.add(e);
        _stringMap.put(line, e);
      }

      // Read group indices
      if ((line = reader.readLine()) != null)
      {
        String[] tokens = line.split(" ");
        int last = 0;
        for (int i = 1; i < tokens.length; i++)
        {
          int next = Integer.parseInt(tokens[i]);
          _groups.add(_all.subList(last, next));
          last = next;
        }
        _groups.add(_all.subList(last, _all.size()));
      }
    }
    catch (IOException e) { Logs.exn("Emoji.init() failed", e); }
  }

  public static int getNumGroups()
  {
    return _groups.size();
  }

  public static List<Emoji> getEmojisByGroup(int groupIndex)
  {
    return _groups.get(groupIndex);
  }

  public static List<Emoji> getAllEmojis()
  {
    return Collections.unmodifiableList(_all);
  }

  public static Emoji getEmojiByString(String value)
  {
    return _stringMap.get(value);
  }

  /** Offline emoji search across Unicode codepoint names and curated keyword synonyms. */
  public static List<Emoji> searchEmojis(String query)
  {
    if (query == null || query.trim().isEmpty())
      return getAllEmojis();
    List<Emoji> matches = new ArrayList<>();
    for (Emoji e : _all)
    {
      if (matchesEmoji(e.kv().getString(), query))
        matches.add(e);
    }
    return matches;
  }

  static boolean matchesEmoji(String emojiStr, String query)
  {
    if (emojiStr == null || emojiStr.isEmpty() || query == null)
      return false;
    String normalized = query.trim().toLowerCase(Locale.ROOT);
    if (normalized.isEmpty())
      return true;
    if (emojiStr.contains(normalized))
      return true;

    StringBuilder searchable = new StringBuilder();
    String synonym = KEYWORD_SYNONYMS.get(emojiStr);
    if (synonym != null)
      searchable.append(synonym).append(' ');
    // Also check base codepoint without variation selector U+FE0F
    if (emojiStr.indexOf('\uFE0F') >= 0)
    {
      String stripped = emojiStr.replace("\uFE0F", "");
      String strippedSynonym = KEYWORD_SYNONYMS.get(stripped);
      if (strippedSynonym != null)
        searchable.append(strippedSynonym).append(' ');
    }

    int i = 0;
    while (i < emojiStr.length())
    {
      int cp = emojiStr.codePointAt(i);
      i += Character.charCount(cp);
      String name = Character.getName(cp);
      if (name != null)
        searchable.append(name.toLowerCase(Locale.ROOT)).append(' ');
    }

    String haystack = searchable.toString();
    String[] tokens = normalized.split("\\s+");
    for (String token : tokens)
    {
      if (!token.isEmpty() && !haystack.contains(token))
        return false;
    }
    return true;
  }
}
