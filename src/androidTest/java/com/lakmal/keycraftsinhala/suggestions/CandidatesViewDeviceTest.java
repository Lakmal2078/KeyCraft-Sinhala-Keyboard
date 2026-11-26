package com.lakmal.keycraftsinhala.suggestions;

import android.content.Context;
import android.text.InputType;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.atomic.AtomicReference;
import com.lakmal.keycraftsinhala.R;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class CandidatesViewDeviceTest
{
  @Test
  public void rendersOnlyAvailableWordCandidates()
  {
    Context context = ApplicationProvider.getApplicationContext();
    AtomicReference<TextView[]> itemViews = new AtomicReference<>();
    InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
      CandidatesView candidatesView = createView(context);

      Suggestions suggestions = new Suggestions(null, null);
      suggestions.suggestions[0] = "අම්මා";
      suggestions.suggestions[1] = "සිංහල";
      suggestions.count = 2;
      candidatesView.set_candidates(suggestions);
      itemViews.set(new TextView[] {
        candidatesView.findViewById(R.id.candidates_middle),
        candidatesView.findViewById(R.id.candidates_right),
        candidatesView.findViewById(R.id.candidates_left),
        candidatesView.findViewById(R.id.candidates_emoji)
      });
    });

    TextView[] views = itemViews.get();
    assertEquals("අම්මා", views[0].getText().toString());
    assertEquals(View.VISIBLE, views[0].getVisibility());
    assertEquals("සිංහල", views[1].getText().toString());
    assertEquals(View.VISIBLE, views[1].getVisibility());
    assertEquals(View.GONE, views[2].getVisibility());
    assertEquals(View.GONE, views[3].getVisibility());
  }

  @Test
  public void hidesCandidatesForPasswordEditors()
  {
    int[] passwordTypes = {
      InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
      InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
      InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
      InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD
    };

    for (int inputType : passwordTypes)
    {
      EditorInfo editorInfo = new EditorInfo();
      editorInfo.inputType = inputType;
      assertFalse(CandidatesView.should_show(editorInfo));
    }
  }

  @Test
  public void expandsExtraCandidatesAndCommitsTheSelectedWord()
  {
    Context context = ApplicationProvider.getApplicationContext();
    InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
      CandidatesView candidatesView = createView(context);
      Suggestions suggestions = new Suggestions(null, null);
      String[] words = {"අම්මා", "සිංහල", "අලුත්", "ආයුබෝවන්", "සතුටුයි"};
      System.arraycopy(words, 0, suggestions.suggestions, 0, words.length);
      suggestions.count = words.length;
      AtomicReference<String> selected = new AtomicReference<>();
      candidatesView.setCandidateListener(selected::set);
      candidatesView.set_candidates(suggestions);

      View more = candidatesView.findViewById(R.id.candidates_more_toggle);
      View scroll = candidatesView.findViewById(R.id.candidates_extra_scroll);
      LinearLayout extra = candidatesView.findViewById(R.id.candidates_extra_items);
      assertEquals(View.VISIBLE, more.getVisibility());
      assertEquals(View.GONE, scroll.getVisibility());
      assertTrue(more.performClick());
      assertEquals(View.VISIBLE, scroll.getVisibility());
      assertEquals(2, extra.getChildCount());
      assertEquals("සතුටුයි", ((TextView)extra.getChildAt(1)).getText().toString());
      assertTrue(extra.getChildAt(1).performClick());
      assertEquals("සතුටුයි", selected.get());
      assertEquals(View.GONE, scroll.getVisibility());
      assertEquals(View.VISIBLE, candidatesView.findViewById(R.id.candidates_middle).getVisibility());

      assertTrue(more.performClick());
      suggestions.clear();
      candidatesView.set_candidates(suggestions);
      assertEquals(View.GONE, more.getVisibility());
      assertEquals(View.GONE, scroll.getVisibility());
      assertEquals(0, extra.getChildCount());
    });
  }

  private static CandidatesView createView(Context appContext)
  {
    Context context = new ContextThemeWrapper(appContext, R.style.Light);
    CandidatesView view = new CandidatesView(context, null);
    addItemView(view, context, R.id.candidates_middle);
    addItemView(view, context, R.id.candidates_right);
    addItemView(view, context, R.id.candidates_left);
    addItemView(view, context, R.id.candidates_emoji);
    HorizontalScrollView scroll = new HorizontalScrollView(context);
    scroll.setId(R.id.candidates_extra_scroll);
    LinearLayout extra = new LinearLayout(context);
    extra.setId(R.id.candidates_extra_items);
    scroll.addView(extra);
    view.addView(scroll);
    Button more = new Button(context);
    more.setId(R.id.candidates_more_toggle);
    view.addView(more);
    view.onFinishInflate();
    return view;
  }

  private static void addItemView(CandidatesView parent, Context context, int id)
  {
    TextView view = new TextView(context);
    view.setId(id);
    parent.addView(view);
  }
}
