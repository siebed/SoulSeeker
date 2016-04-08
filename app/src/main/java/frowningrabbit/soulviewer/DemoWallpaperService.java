package frowningrabbit.soulviewer;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

public class DemoWallpaperService extends WallpaperService {

    public static DemoWallpaperEngine demoWallpaperEngine;

    @Override
    public Engine onCreateEngine() {
        demoWallpaperEngine = new DemoWallpaperEngine(getBaseContext());
        return demoWallpaperEngine;
    }

    private class DemoWallpaperEngine extends Engine implements OnSharedPreferenceChangeListener {
        SoulRenderer renderer;

        private boolean mVisible = false;
        private final Handler mHandler = new Handler();
        private final Runnable mUpdateDisplay = new Runnable() {
            @Override
            public void run() {
                draw();
            }
        };

        public DemoWallpaperEngine(Context baseContext) {
            renderer = new SoulRenderer(baseContext);
            processPreferences();
        }



        private void draw() {
            SurfaceHolder holder = getSurfaceHolder();
            Canvas c = null;
            try {
                c = holder.lockCanvas(null);
                renderer.doDraw(c);
            } finally {
                if (c != null) {
                    holder.unlockCanvasAndPost(c);
                }
            }
            mHandler.removeCallbacks(mUpdateDisplay);
            if (mVisible) {
                mHandler.postDelayed(mUpdateDisplay, 10);
            }
        }


        @Override
        public void onVisibilityChanged(boolean visible) {
            mVisible = visible;
            if (visible) {
                draw();
            } else {
                mHandler.removeCallbacks(mUpdateDisplay);
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format,
                                     int width, int height) {
            renderer.setSurfaceSize(width, height);
            draw();
        }


        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            mVisible = false;
            mHandler.removeCallbacks(mUpdateDisplay);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            mVisible = false;
            mHandler.removeCallbacks(mUpdateDisplay);
        }


        private void processPreferences() {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(DemoWallpaperService.this);
            renderer.setShouldBlur(prefs.getBoolean(getString(R.string.shouldBlur), true));
            renderer.setShouldScale(prefs.getBoolean(getString(R.string.shouldScale), true));
            renderer.setRotationSpeed(prefs.getInt(getString(R.string.rotationSpeed), 100) / 100f);
            renderer.setRotationSpeed(prefs.getInt(getString(R.string.rotationSpeed), 100) / 100f);
            renderer.changeXferMode(PorterDuff.Mode.valueOf(prefs.getString(getString(R.string.drawXferMode), "SRC")));
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
//            if (key.equalsIgnoreCase(getString(R.string.shouldBlur))) {
//                shouldBlur = sharedPreferences.getBoolean(key, true);
//                //clear the screen of there is no blurring
//                if (!shouldBlur) {
//                    pixelValues = new int[width * height];
//                }
//            } else if (key.equalsIgnoreCase(getString(R.string.rotationSpeed))) {
//                rotationSpeed = sharedPreferences.getInt(key, 100) / 100f;
//            } else if (key.equalsIgnoreCase(getString(R.string.useForegroundColor))) {
//                useForegroundColor = sharedPreferences.getBoolean(key, true);
//            } else if (key.equalsIgnoreCase(getString(R.string.foregroundColor))) {
//                foregroundColor = sharedPreferences.getInt(key, 0xff00ff);
//            }
            processPreferences();

        }

    }
}
