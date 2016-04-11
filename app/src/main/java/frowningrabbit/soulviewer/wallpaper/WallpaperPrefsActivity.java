package frowningrabbit.soulviewer.wallpaper;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;

import frowningrabbit.soulviewer.R;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class WallpaperPrefsActivity extends PreferenceActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new DreamPreferenceFragment()).commit();
    }

    public static class DreamPreferenceFragment extends PreferenceFragment {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.prefs);
            getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(WallpaperRenderService.wallpaperEngine);
        }

    }

}
