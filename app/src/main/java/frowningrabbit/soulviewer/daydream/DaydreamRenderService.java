package frowningrabbit.soulviewer.daydream;

import android.annotation.TargetApi;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.service.dreams.DreamService;

import frowningrabbit.soulviewer.R;
import frowningrabbit.soulviewer.SoulRenderView;


@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class DaydreamRenderService extends DreamService  implements SharedPreferences.OnSharedPreferenceChangeListener {

    private SoulRenderView mSoulView;
    public static DaydreamRenderService daydreamService;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        // Hide system UI?
        setFullscreen(true);

        // Keep screen at full brightness?
        setScreenBright(true);

        setContentView(R.layout.main);

        mSoulView = (SoulRenderView) findViewById(R.id.soulRenderView);
        mSoulView.setPreferences(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));

        daydreamService = this;
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();

        mSoulView.resume();
    }

    @Override
    public void onDreamingStopped() {
        super.onDreamingStopped();

        mSoulView.pause();
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        mSoulView.setPreferences(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));
    }
}
