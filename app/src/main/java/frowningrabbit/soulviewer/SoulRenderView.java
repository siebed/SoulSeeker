package frowningrabbit.soulviewer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.TextView;

import java.util.Arrays;


/**
 * View that draws, takes keystrokes, etc. for a simple LunarLander game.
 * <p/>
 * Has a mode which RUNNING, PAUSED, etc. Has a x, y, dx, dy, ... capturing the
 * current ship physics. All x/y etc. are measured with (0,0) at the lower left.
 * updatePhysics() advances the physics based on realtime. draw() renders the
 * ship, and does an invalidate() to prompt another draw() as soon as possible
 * by the system.
 */
class SoulRenderView extends SurfaceView implements SurfaceHolder.Callback {
    class LunarThread extends Thread {
        public static final int STATE_LOSE = 1;
        public static final int STATE_PAUSE = 2;
        public static final int STATE_READY = 3;
        public static final int STATE_RUNNING = 4;
        public static final int STATE_WIN = 5;

        private static final int PARTICALS = 8000;
        public static final float DRAW_FULL_ARM_THRESHOLD = 0.8f;
        public static final float DRAW_NO_ARM_THRESHOLD = 0.1f;
        public static final float FULL_MOOD_THRESHOLD = 0.8f;
        public static final float NO_MOOD_THRESHOLD = 0.2f;
        private boolean mVisible = false;
        //        private final Handler mHandler = new Handler();
//        private final Runnable mUpdateDisplay = new Runnable() {
//            @Override
//            public void run() {
//                draw();
//            }
//        };
        private int[][] pixelColors;
        private int width;
        private int height;
        private int[][] coral;
        private int[][] rotatedCoral;
        private float scaleRate = 1.2f;
        private int scaleX[], scaleY[];
        private boolean initialized;
        double colorCounter = 300;
        int[] colorPallet = new int[256];
        double radius = 1.5;
        int intx = 0;
        int inty = 0;
        int intz = 0;
        float scaleFactor = 1;
        long startTime = System.currentTimeMillis();
        int frames = 0;
        long cumuTime = 0;

        //vars controlled by prefs
        private boolean shouldBlur = true;
        float drawLeftArm = 1f;
        float drawRightArm = 1f;
        private float rotationSpeed = 1f;
        private boolean useForegroundColor;
        private int foregroundColor;
        private SurfaceInfo sourceBuffer = new SurfaceInfo();
        private SurfaceInfo destinationBuffer = new SurfaceInfo();
        private Paint paint;
        private Paint centerPointpaint;
        RenderScript rs;
        private ScriptIntrinsicBlur theIntrinsic;
        private float moodIndex = 1;

        public void setDrawLeftArm(float drawLeftArm) {
            this.drawLeftArm = drawLeftArm;
        }

        public void setDrawRightArm(float drawRightArm) {
            this.drawRightArm = drawRightArm;
        }

        public void setMoodIndex(float moodIndex) {

            this.moodIndex = moodIndex;
        }

        public void setToNeutral() {
            moodIndex = 1;
            drawLeftArm = 1f;
            drawRightArm = 1f;
        }

        private class SurfaceInfo {

            public void setBitmap(Bitmap bitmap) {
                this.bitmap = bitmap;
                canvas = new Canvas(bitmap);
            }

            public Bitmap bitmap;
            public Canvas canvas;
        }

        /**
         * Message handler used by thread to interact with TextView
         */
        private Handler mHandler;

        /**
         * The state of the game. One of READY, RUNNING, PAUSE, LOSE, or WIN
         */
        private int mMode = STATE_RUNNING;
        /**
         * Indicate whether the surface has been created & is ready to draw
         */
        private boolean mRun = false;

        private final Object mRunLock = new Object();

        /**
         * Handle to the surface manager object we interact with
         */
        private SurfaceHolder mSurfaceHolder;

        public LunarThread(SurfaceHolder surfaceHolder, Context context,
                           Handler handler) {
            // get handles to some important objects
            mSurfaceHolder = surfaceHolder;
            mHandler = handler;
            mContext = context;

//            processPreferences();
            initializeData();
            make3DBrownian();
            initColor();
            startTime = System.currentTimeMillis();
        }

        /**
         * Starts the game, setting parameters for the current difficulty.
         */
        public void doStart() {
            synchronized (mSurfaceHolder) {
                // First set the game for Medium difficulty
                setState(STATE_RUNNING);
            }
        }

        /**
         * Pauses the physics update & animation.
         */
        public void pause() {
            synchronized (mSurfaceHolder) {
                if (mMode == STATE_RUNNING) setState(STATE_PAUSE);
            }
        }

        @Override
        public void run() {
            while (mRun) {
                Canvas c = null;
                try {
                    c = mSurfaceHolder.lockCanvas(null);
                    synchronized (mSurfaceHolder) {
                        if (mMode == STATE_RUNNING) {
                            //updatePhysics();

                            // Critical section. Do not allow mRun to be set false until
                            // we are sure all canvas draw operations are complete.
                            //
                            // If mRun has been toggled false, inhibit canvas operations.
                            synchronized (mRunLock) {
                                if (mRun) doDraw(c);
                            }
                        }
                    }
                } finally {
                    // do this in a finally so that if an exception is thrown
                    // during the above, we don't leave the Surface in an
                    // inconsistent state
                    if (c != null) {
                        mSurfaceHolder.unlockCanvasAndPost(c);
                    }
                }
            }
        }

        /**
         * Dump game state to the provided Bundle. Typically called when the
         * Activity is being suspended.
         *
         * @return Bundle with this view's state
         */
        public Bundle saveState(Bundle map) {
            synchronized (mSurfaceHolder) {
                if (map != null) {
                }
            }
            return map;
        }

        /**
         * Used to signal the thread whether it should be running or not.
         * Passing true allows the thread to run; passing false will shut it
         * down if it's already running. Calling start() after this was most
         * recently called with false will result in an immediate shutdown.
         *
         * @param b true to run, false to shut down
         */
        public void setRunning(boolean b) {
            // Do not allow mRun to be modified while any canvas operations
            // are potentially in-flight. See doDraw().
            synchronized (mRunLock) {
                mRun = b;
            }
        }

        /**
         * Sets the game mode. That is, whether we are running, paused, in the
         * failure state, in the victory state, etc.
         *
         * @param mode one of the STATE_* constants
         * @see #setState(int, CharSequence)
         */
        public void setState(int mode) {
            synchronized (mSurfaceHolder) {
                setState(mode, null);
            }
        }

        /**
         * Sets the game mode. That is, whether we are running, paused, in the
         * failure state, in the victory state, etc.
         *
         * @param mode    one of the STATE_* constants
         * @param message string to add to screen or null
         */
        public void setState(int mode, CharSequence message) {
            /*
             * This method optionally can cause a text message to be displayed
             * to the user when the mode changes. Since the View that actually
             * renders that text is part of the main View hierarchy and not
             * owned by this thread, we can't touch the state of that View.
             * Instead we use a Message + Handler to relay commands to the main
             * thread, which updates the user-text View.
             */
            synchronized (mSurfaceHolder) {
                mMode = mode;

                if (mMode == STATE_RUNNING) {
                } else {
                }
            }
        }

        /* Callback invoked when the surface dimensions change. */
        public void setSurfaceSize(int width, int height) {
            // synchronized to make sure these all change atomically
            synchronized (mSurfaceHolder) {
                width = width / 4;
                height = height / 4;
                this.width = width;
                this.height = height;
                pixelColors = new int[width][height];
                sourceBuffer.setBitmap(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888));
                destinationBuffer.setBitmap(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888));
                scaleX = new int[width];
                scaleY = new int[height];
                int sx = width / 2;
                int sy = height / 2;
                for (int i = 0; i < width; i++) {
                    scaleX[i] = (int) (sx + ((i - sx) * (7.5 / 10)));
                }
                for (int i = 0; i < height; i++) {
                    scaleY[i] = (int) (sy + ((i - sy) * (7.5 / 10)));
                }
            }
        }


        /**
         * Resumes from a pause.
         */
        public void unpause() {
            // Move the real time clock up to now
            synchronized (mSurfaceHolder) {
            }
            setState(STATE_RUNNING);
        }


        /**
         * Draws the ship, fuel/speed bars, and background to the provided
         * Canvas.
         */
        private void doDraw(Canvas canvas) {
            startTime = System.currentTimeMillis();
            frames++;
            rotateCoral(radius, radius * 2.72);
            radius += (6.248 / 256) * rotationSpeed;
            initColor();

            scale(sourceBuffer, destinationBuffer); // A -> B

            rendercolors(destinationBuffer); //B

            if (shouldBlur) {
                //switch the buffers
                SurfaceInfo tmp = sourceBuffer;
                sourceBuffer = destinationBuffer;
                destinationBuffer = tmp;
                blur(sourceBuffer, destinationBuffer); // B -> A
            }


            Rect srcRect = new Rect(0, 0, width, height);
            Rect destRect = new Rect(0, 0, canvas.getWidth(), canvas.getHeight());
            canvas.drawBitmap(destinationBuffer.bitmap, srcRect, destRect, null);

            //switch the buffers, so we add to the final version next iteration
            SurfaceInfo tmp = sourceBuffer;
            sourceBuffer = destinationBuffer;
            destinationBuffer = tmp;

            cumuTime += System.currentTimeMillis() - startTime;
            Log.i("draw average", "" + (cumuTime / frames));

        }

        private void rendercolors(SurfaceInfo buffer) {
            for (int j = 0; j < height; j++) {
                for (int i = 0; i < width; i++) {
                    // get value from pixel in scaled image, and store
                    if (pixelColors[i][j] > 0) {
                        paint.setColor(colorPallet[Math.min(255, pixelColors[i][j])]);
                        buffer.canvas.drawPoint(i, j, paint);
                    }
                }
            }

            buffer.canvas.drawPoint(width / 2, height / 2, centerPointpaint);
        }

        private void clearColors() {
            for (int j = 0; j < pixelColors.length; j++) {
                Arrays.fill(pixelColors[j], 0);
            }
        }

        public void blur(SurfaceInfo inputBitmap, SurfaceInfo outputBitmap) {
            Allocation tmpIn = Allocation.createFromBitmap(rs, inputBitmap.bitmap, Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);
            Allocation tmpOut = Allocation.createFromBitmap(rs, outputBitmap.bitmap);
            theIntrinsic.setRadius(1.0f);
            theIntrinsic.setInput(tmpIn);
            theIntrinsic.forEach(tmpOut);
            tmpOut.copyTo(outputBitmap.bitmap);
        }

        void scale(SurfaceInfo source, SurfaceInfo destination) {
            int xScaleOffset = (int) (width - (width / scaleRate)) / 2;
            int yScaleOffset = (int) (height - (height / scaleRate)) / 2;
            Rect srcRect = new Rect(xScaleOffset, yScaleOffset, width - xScaleOffset, height - yScaleOffset);
            Rect destRect = new Rect(0, 0, destination.bitmap.getWidth(), destination.bitmap.getHeight());
            destination.canvas.drawBitmap(source.bitmap, srcRect, destRect, null);
        }

        public void initializeData() {
            /* init buffers */
            coral = new int[PARTICALS][3];
            rotatedCoral = new int[PARTICALS][3];

            initialized = true;

            centerPointpaint = new Paint();
            centerPointpaint.setColor(Color.BLACK);
            centerPointpaint.setStrokeWidth(4);

            paint = new Paint();
            paint.setStrokeWidth(scaleFactor);
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));

            rs = RenderScript.create(getContext());
            theIntrinsic = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
        }

        private void make3DBrownian() {
            int randnum;
            for (int i = 0; i < PARTICALS; i++) {
                randnum = (int) Math.floor(Math.random() * 6);
                if (randnum == 0) {
                    inty--;
                } else if (randnum == 1) {
                    intx++;// 0| /4
                } else if (randnum == 2) {
                    inty++;// |/
                } else if (randnum == 3) {
                    intx--;// 3-----1
                } else if (randnum == 4) {
                    intz--;// /|
                } else if (randnum == 5) {
                    intz++;// 5/ |2
                }

                coral[i][0] = intx;
                coral[i][1] = inty;
                coral[i][2] = intz;
            }
        }

        /**
         * Init the color pallet for this frame. Depending on 'useForegroundColor' it will cycle through all colors of the spectrum
         * or a set foreground color. To give the effect of glowing particles the color will go from black to a set color to white.
         * The higher the startIntensity the brighter the colours will be.
         */
        private void initColor() {
            int red, green, blue;
            if (!useForegroundColor) {
                int startIntensity = moodIndex > FULL_MOOD_THRESHOLD ? 128 : (int) (Math.max(NO_MOOD_THRESHOLD, moodIndex) * 128);
                Log.i("draw average", "startIntens: " + startIntensity + " moodIndex: " + moodIndex);
                red = (int) (startIntensity + (127 * Math.cos(colorCounter++ / 150)));
                green = (int) (startIntensity + (127 * Math.cos(colorCounter / 80)));
                blue = (int) (startIntensity + (127 * Math.cos(colorCounter / 220)));

            } else {
                red = (foregroundColor >> 16) & 0x000000ff;
                green = (foregroundColor >> 8) & 0x000000ff;
                blue = foregroundColor & 0x000000ff;
            }

            for (int i = 0; i < 128; i++) {
                colorPallet[i] = Color.rgb(((red * i) / 128), ((green * i) / 128), ((blue * i) / 128));

                colorPallet[128 + i] = Color.rgb((red + (((256 - red) * i) / 128)), (green + (((256 - green) * i) / 128)), (blue + (((256 - blue) * i) / 128)));
            }
        }

        double cosX, sinX, cosY, sinY;
        double x, y, z, newx, newy, newz;
        int centerX, centerY;
        int finalX, finalY, finalZ

        private void rotateCoral(double angelX, double angelY) {
            centerX = width / 2;
            centerY = height / 2;

            cosX = Math.cos(angelX);
            sinX = Math.sin(angelX);
            cosY = Math.cos(angelY);
            sinY = Math.sin(angelY);
            clearColors();
            for (int i = 0; i < PARTICALS; i++) {
                x = coral[i][0];
                y = coral[i][1];
                z = coral[i][2];

                newx = x;
                newy = (y * cosX) + (z * sinX);
                newz = (z * cosX) - (y * sinX);

                finalX = (int) ((newx * cosY) + (newz * sinY));
                finalY = (int) (newy);
                finalZ = (int) ((newz * cosY) - (newx * sinY));


                if (drawLeftArm > DRAW_FULL_ARM_THRESHOLD || (drawLeftArm > DRAW_NO_ARM_THRESHOLD && (i * drawLeftArm) % 1f < drawLeftArm)) {
                    pixelColors[centerX + rotatedCoral[i][0]][centerY + rotatedCoral[i][1]] += 8; //add to the color
                }

                if (drawRightArm > DRAW_FULL_ARM_THRESHOLD || (drawLeftArm > DRAW_NO_ARM_THRESHOLD && (i * drawRightArm) % 1f < drawRightArm)) {
                    pixelColors[centerX - rotatedCoral[i][0]][centerY - rotatedCoral[i][1]] += 8; //add to the color
                }
            }
        }

    }


    /**
     * Handle to the application context, used to e.g. fetch Drawables.
     */
    private Context mContext;

    /**
     * Pointer to the text view to display "Paused.." etc.
     */
    private TextView mStatusText;

    /**
     * The thread that actually draws the animation
     */
    private LunarThread thread;

    public SoulRenderView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // register our interest in hearing about changes to our surface
        SurfaceHolder holder = getHolder();
        holder.addCallback(this);

        // create thread only; it's started in surfaceCreated()
        thread = new LunarThread(holder, context, new Handler() {
            @Override
            public void handleMessage(Message m) {
            }
        });

        setFocusable(true); // make sure we get key events
    }

    /**
     * Fetches the animation thread corresponding to this LunarView.
     *
     * @return the animation thread
     */
    public LunarThread getThread() {
        return thread;
    }


    /**
     * Standard window-focus override. Notice focus lost so we can pause on
     * focus lost. e.g. user switches to take a call.
     */
    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        if (!hasWindowFocus) thread.pause();
    }

    /* Callback invoked when the surface dimensions change. */
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {
        thread.setSurfaceSize(width, height);
    }

    /*
     * Callback invoked when the Surface has been created and is ready to be
     * used.
     */
    public void surfaceCreated(SurfaceHolder holder) {
        // start the thread here so that we don't busy-wait in run()
        // waiting for the surface to be created
        thread.setRunning(true);
        thread.start();
    }

    /*
     * Callback invoked when the Surface has been destroyed and must no longer
     * be touched. WARNING: after this method returns, the Surface/Canvas must
     * never be touched again!
     */
    public void surfaceDestroyed(SurfaceHolder holder) {
        // we have to tell thread to shut down & wait for it to finish, or else
        // it might touch the Surface after we return and explode
        boolean retry = true;
        thread.setRunning(false);
        while (retry) {
            try {
                thread.join();
                retry = false;
            } catch (InterruptedException e) {
            }
        }
    }
}