package frowningrabbit.soulviewer;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class WallpaperPrefsActivity extends PreferenceActivity {

    @Override
    protected void onCreate (Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.prefs);
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(DemoWallpaperService.demoWallpaperEngine);
    }

}
