package com.lakmal.keycraftsinhala.suggestions;

import com.lakmal.keycraftsinhala.Config;
import com.lakmal.keycraftsinhala.EditorConfig;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import juloo.cdict.Cdict;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReviewedSinhalaAliasTest
{
  @Test public void reviewedExactCandidateAndLiteralEnglishAreBothVisible() throws Exception
  {
    Config config = org.mockito.Mockito.mock(Config.class);
    config.editor_config = new EditorConfig();
    config.editor_config.should_show_candidates_view = true;
    config.suggestions_enabled = true;
    config.sinhala_transliteration = true;
    config.literal_singlish = true;
    config.current_dictionary_name = "si";
    config.current_dictionary = Cdict.of_bytes(Files.readAllBytes(Paths.get("assets/dict/si.dict")))[0];
    Suggestions suggestions = new Suggestions(ignored -> {}, config);

    // Test fixture only. This alias is not included in the bundled reviewed TSV.
    suggestions.loadReviewedAliases(new ByteArrayInputStream("download\tගන්න\n".getBytes(StandardCharsets.UTF_8)));
    suggestions.started();
    suggestions.currently_typed_word("Download");

    assertEquals("ගන්න", suggestions.suggestions[0]);
    assertTrue(Arrays.asList(suggestions.suggestions).subList(0, Suggestions.VISIBLE_COUNT).contains("Download"));
    assertEquals(1, Arrays.stream(suggestions.suggestions).filter("Download"::equals).count());
  }
}
