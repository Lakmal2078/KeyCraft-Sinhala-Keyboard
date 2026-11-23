package com.lakmal.keycraftsinhala;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.view.View;
import android.widget.Toast;

public class SettingsActivity extends PreferenceActivity
{
  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    // Migrate once, then let Settings and the keyboard share the same store.
    try
    {
      DirectBootAwarePreferences.get_shared_preferences(this);
      if (Build.VERSION.SDK_INT >= 24)
        getPreferenceManager().setStorageDeviceProtected();
      Config.migrate(getPreferenceManager().getSharedPreferences());
    }
    catch (Exception _e) { fallbackEncrypted(); return; }
    addPreferencesFromResource(R.xml.settings);
    Utils.applySafeSystemBarInsets(findViewById(android.R.id.list), true);

    boolean foldableDevice = FoldStateTracker.isFoldableDevice(this);
    String[] foldableKeys = new String[] {
      "margin_bottom_portrait_unfolded",
      "margin_bottom_landscape_unfolded",
      "horizontal_margin_portrait_unfolded",
      "horizontal_margin_landscape_unfolded",
      "keyboard_height_unfolded",
      "keyboard_height_landscape_unfolded"
    };
    for (String key : foldableKeys)
    {
      Preference p = findPreference(key);
      if (p != null)
      {
        p.setEnabled(foldableDevice);
        if (!foldableDevice)
          p.setSummary(R.string.pref_foldable_disabled_summary);
      }
    }

    Preference themePref = findPreference("theme");
    if (themePref != null)
    {
      themePref.setOnPreferenceClickListener(preference -> {
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        AlertDialog d = ThemePicker.create(new AlertDialog.Builder(this), getResources(), prefs);
        d.setOnDismissListener(dialog -> {
          if (preference instanceof android.preference.ListPreference)
          {
            String updated = prefs.getString("theme", Config.DEFAULT_THEME);
            ((android.preference.ListPreference)preference).setValue(updated);
          }
        });
        d.show();
        return true;
      });
    }

    Preference resetSettings = findPreference("reset_keyboard_settings");
    if (resetSettings != null)
    {
      resetSettings.setOnPreferenceClickListener(preference -> {
        new AlertDialog.Builder(this)
            .setTitle(R.string.pref_reset_keyboard_settings_confirm_title)
            .setMessage(R.string.pref_reset_keyboard_settings_confirm_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.pref_reset_keyboard_settings_action, (dialog, which) -> {
              SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
              boolean reset = resetKeyboardSettings(prefs, Build.VERSION.SDK_INT >= 24);
              Toast.makeText(this, reset
                      ? R.string.pref_reset_keyboard_settings_done
                      : R.string.pref_reset_keyboard_settings_failed,
                  reset ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
              if (reset)
                recreate();
            })
            .show();
        return true;
      });
    }

    Preference clearLearnedData = findPreference("clear_sinhala_learned_data");
    if (clearLearnedData != null)
    {
      clearLearnedData.setOnPreferenceClickListener(preference -> {
        new AlertDialog.Builder(this)
            .setTitle(R.string.pref_clear_sinhala_learned_data_confirm_title)
            .setMessage(R.string.pref_clear_sinhala_learned_data_confirm_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.pref_clear_sinhala_learned_data_action, (dialog, which) -> {
              try
              {
                int message = SinhalaLearner.clearAllLearnedData(this)
                    ? R.string.pref_clear_sinhala_learned_data_done
                    : R.string.pref_clear_sinhala_learned_data_failed;
                Toast.makeText(this, message,
                    message == R.string.pref_clear_sinhala_learned_data_done
                        ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
              }
              catch (Exception e)
              {
                Toast.makeText(this, R.string.pref_clear_sinhala_learned_data_failed,
                    Toast.LENGTH_LONG).show();
              }
            })
            .show();
        return true;
      });
    }
  }

  static boolean resetKeyboardSettings(SharedPreferences prefs, boolean preserveMigrationMarker)
  {
    if (prefs == null)
      return false;
    SharedPreferences.Editor editor = prefs.edit().clear();
    // Prevent legacy credential-protected preferences from being copied back after reset.
    if (preserveMigrationMarker)
      editor.putBoolean("need_migration", false);
    if (!editor.commit())
      return false;
    Config.migrate(prefs);
    return true;
  }

  @Override
  @SuppressWarnings("deprecation")
  public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference)
  {
    boolean handled = super.onPreferenceTreeClick(preferenceScreen, preference);
    if (preference instanceof PreferenceScreen)
    {
      Dialog dialog = ((PreferenceScreen)preference).getDialog();
      if (dialog != null && dialog.getWindow() != null)
      {
        View list = dialog.findViewById(android.R.id.list);
        Utils.applySafeSystemBarInsets(
            list != null ? list : dialog.getWindow().getDecorView(),
            list != null);
      }
    }
    return handled;
  }

  void fallbackEncrypted()
  {
    // Can't communicate with the user here.
    finish();
  }

}
