-keep public class juloo.cdict.* {
  public protected private *;
}

# Keep manifest components and XML-instantiated custom views/preferences under R8.
-keep class com.lakmal.keycraftsinhala.Keyboard2 {
  *;
}

-keep class com.lakmal.keycraftsinhala.LauncherActivity {
  public void *(android.view.View);
}

-keep class com.lakmal.keycraftsinhala.SettingsActivity {
  *;
}

-keep class com.lakmal.keycraftsinhala.dict.DictionariesActivity {
  *;
}

-keep class * extends android.preference.Preference {
  public <init>(android.content.Context, android.util.AttributeSet);
  public <init>(android.content.Context, android.util.AttributeSet, int);
}

-keep class com.lakmal.keycraftsinhala.** extends android.view.View {
  public <init>(android.content.Context);
  public <init>(android.content.Context, android.util.AttributeSet);
  public <init>(android.content.Context, android.util.AttributeSet, int);
}

