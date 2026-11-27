package com.lakmal.keycraftsinhala.suggestions;

import com.lakmal.keycraftsinhala.Config;
import com.lakmal.keycraftsinhala.EditorConfig;
import com.lakmal.keycraftsinhala.SinhalaPredictionEngine;
import com.lakmal.keycraftsinhala.SinhalaText;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import juloo.cdict.Cdict;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

public class SinhalaTopThreeBenchmarkTest
{
  @Test public void measureVisibleSuggestionsOnHundredSentences() throws Exception
  {
    Path assets = Paths.get("assets/dict");
    Cdict dictionary = Cdict.of_bytes(Files.readAllBytes(assets.resolve("si.dict")))[0];
    Config config = mock(Config.class);
    config.editor_config = new EditorConfig();
    config.editor_config.should_show_candidates_view = true;
    config.suggestions_enabled = true;
    config.literal_singlish = true;
    config.current_dictionary_name = "si";
    config.current_dictionary = dictionary;
    Suggestions suggestions = new Suggestions(ignored -> {}, config);
    suggestions.loadReviewedAliases(Files.newInputStream(assets.resolve("si_aliases.tsv")));

    List<String> lines = Files.readAllLines(Paths.get("test/resources/sinhala/suggestions-top3.tsv"));
    assertEquals("mode\tgroup\tbefore\tinput\texpected\tafter", lines.get(0));
    assertEquals(101, lines.size());
    Set<String> sentences = new HashSet<>();
    Map<String, Score> modes = new LinkedHashMap<>();
    Map<String, Score> groups = new LinkedHashMap<>();
    Score total = new Score();
    for (int i = 1; i < lines.size(); i++)
    {
      String[] row = lines.get(i).split("\t", -1);
      assertEquals("Columns on fixture row " + i, 6, row.length);
      String mode = row[0], group = row[1], before = row[2], input = row[3], target = row[4];
      assertTrue("Invalid target on row " + i, SinhalaText.isSinhalaWord(target));
      assertTrue("Duplicate sentence on row " + i, sentences.add(before + " " + target + " " + row[5]));
      switch (mode)
      {
        case "direct":
          assertTrue("Direct input must be a strict prefix on row " + i,
              !input.isEmpty() && !target.equals(input) && target.startsWith(input));
          break;
        case "singlish":
          assertTrue("Expected ASCII input on row " + i, input.matches("[A-Za-z]+"));
          break;
        case "next":
          assertEquals("", input);
          break;
        default:
          fail("Unknown mode on row " + i + ": " + mode);
      }

      config.sinhala_transliteration = mode.equals("singlish");
      suggestions.started();
      suggestions.set_predictions(SinhalaPredictionEngine.getPredictions(before, null));
      if (!mode.equals("next"))
        suggestions.currently_typed_word(input);

      List<String> visible = new ArrayList<>();
      for (int slot = 0; slot < Math.min(Suggestions.VISIBLE_COUNT, suggestions.count); slot++)
        visible.add(Normalizer.normalize(suggestions.suggestions[slot], Normalizer.Form.NFC));
      boolean hit = visible.contains(Normalizer.normalize(target, Normalizer.Form.NFC));
      total.record(hit);
      modes.computeIfAbsent(mode, ignored -> new Score()).record(hit);
      groups.computeIfAbsent(group, ignored -> new Score()).record(hit);
      if (!hit)
        System.out.printf(Locale.ROOT, "MISS\t%d\t%s\t%s\t%s\t%s\t%b\t%s%n",
            i, mode, group, input, target, dictionary.find(target).found, visible);
    }
    assertEquals(40, modes.get("direct").count);
    assertEquals(40, modes.get("singlish").count);
    assertEquals(20, modes.get("next").count);
    modes.forEach((label, score) -> score.print("mode/" + label));
    groups.forEach((label, score) -> score.print("group/" + label));
    total.print("overall");
    int[] current = {total.hits, modes.get("direct").hits, modes.get("singlish").hits, modes.get("next").hits};
    int[] baseline = {58, 23, 32, 3};
    String[] labels = {"overall", "direct", "singlish", "next"};
    List<String> regressions = new ArrayList<>();
    for (int i = 0; i < baseline.length; i++)
    {
      int delta = current[i] - baseline[i];
      System.out.printf(Locale.ROOT, "TOP3_DELTA\t%s\t%d\t%d\t%+d%n",
          labels[i], baseline[i], current[i], delta);
      if (delta < 0) regressions.add(labels[i] + " " + baseline[i] + " -> " + current[i]);
    }
    assertTrue("Top-3 regressions: " + regressions, regressions.isEmpty());
  }

  private static class Score
  {
    int count;
    int hits;

    void record(boolean hit)
    {
      count++;
      if (hit) hits++;
    }

    void print(String label)
    {
      System.out.printf(Locale.ROOT, "TOP3\t%s\t%d\t%d\t%.1f%%%n",
          label, hits, count, 100.0 * hits / count);
    }
  }
}
