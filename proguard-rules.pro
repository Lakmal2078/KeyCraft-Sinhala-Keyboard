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

# Strip all android.util.Log calls from release DEX bytecode.
-assumenosideeffects class android.util.Log {
  public static boolean isLoggable(java.lang.String, int);
  public static int v(...);
  public static int d(...);
  public static int i(...);
  public static int w(...);
  public static int e(...);
  public static int wtf(...);
}

