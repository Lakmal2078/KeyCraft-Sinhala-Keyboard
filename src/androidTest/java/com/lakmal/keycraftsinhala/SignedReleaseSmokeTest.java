package com.lakmal.keycraftsinhala;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.UiAutomation;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.function.Predicate;
import org.junit.Test;

public class SignedReleaseSmokeTest
{
  private static final String RELEASE = "com.lakmal.keycraftsinhala";
  private static final String EDITOR = RELEASE + ":id/launcher_tryhere_area";

  @Test
  public void signedKeyboardCommitsInputInItsLauncher()
  {
    assumeTrue("Requires the separately installed signed release",
        "true".equals(InstrumentationRegistry.getArguments().getString("signedReleaseSmoke")));
    UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
    AccessibilityServiceInfo info = automation.getServiceInfo();
    info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
    automation.setServiceInfo(info);

    AccessibilityNodeInfo editor = await(automation, n ->
        (RELEASE + ":id/launcher_scroll_view").equals(n.getViewIdResourceName()));
    AccessibilityNodeInfo scroll = editor;
    editor = find(scroll, n -> EDITOR.equals(n.getViewIdResourceName()));
    if (editor == null)
    {
      scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
      editor = await(automation, n -> EDITOR.equals(n.getViewIdResourceName()));
    }
    Rect bounds = new Rect();
    editor.getBoundsInScreen(bounds);
    long downTime = SystemClock.uptimeMillis();
    for (int action : new int[] {MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP})
    {
      MotionEvent event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
          bounds.centerX(), bounds.centerY(), 0);
      event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
      try
      {
        assertTrue("Could not tap the release typing editor", automation.injectInputEvent(event, true));
      }
      finally
      {
        event.recycle();
      }
    }

    AccessibilityNodeInfo key = await(automation, n -> n.isClickable()
        && "a".equalsIgnoreCase(String.valueOf(n.getText()))
        && RELEASE.equals(String.valueOf(n.getPackageName())));
    assertTrue("Release key could not be activated",
        key.performAction(AccessibilityNodeInfo.ACTION_CLICK));
    await(automation, n -> EDITOR.equals(n.getViewIdResourceName())
        && "a".equalsIgnoreCase(String.valueOf(n.getText())));
  }

  private static AccessibilityNodeInfo await(UiAutomation automation,
      Predicate<AccessibilityNodeInfo> predicate)
  {
    long deadline = SystemClock.uptimeMillis() + 20_000;
    do
    {
      for (AccessibilityWindowInfo window : automation.getWindows())
      {
        AccessibilityNodeInfo found = find(window.getRoot(), predicate);
        if (found != null)
          return found;
      }
      SystemClock.sleep(250);
    }
    while (SystemClock.uptimeMillis() < deadline);
    throw new AssertionError("Expected release keyboard accessibility node was unavailable");
  }

  private static AccessibilityNodeInfo find(AccessibilityNodeInfo node,
      Predicate<AccessibilityNodeInfo> predicate)
  {
    if (node == null)
      return null;
    if (node.isVisibleToUser() && predicate.test(node))
      return node;
    for (int index = 0; index < node.getChildCount(); index++)
    {
      AccessibilityNodeInfo found = find(node.getChild(index), predicate);
      if (found != null)
        return found;
    }
    return null;
  }
}
