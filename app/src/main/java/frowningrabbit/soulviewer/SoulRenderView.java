package frowningrabbit.soulviewer;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Bundle;
import android.support.annotation.MainThread;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;


/**
 * View that draws the visualization. It passes on the features of the face if detected
 * <p/>
 * draw() renders the particles through the renderer.
 * It does an invalidate() to prompt another draw() as soon as possible
 * by the system.
 */
class SoulRenderView extends SurfaceView implements SurfaceHolder.Callback {



    class SoulRenderThread extends Thread {
        SoulRenderer renderer;


        public static final int STATE_PAUSED = 1;
        public static final int STATE_RUNNING = 2;

        public void setDrawLeftArm(float drawLeftArm) {
            renderer.setDrawLeftArm(drawLeftArm);
        }

        public void setDrawRightArm(float drawRightArm) {
            renderer.setDrawRightArm(drawRightArm);
        }

        public void setMoodIndex(float moodIndex) {
            renderer.setMoodIndex(moodIndex);
        }

        public void setToNeutral() {
            renderer.setToNeutral();
        }

        /**
         * The state of the animation
         */
        private int mMode = STATE_RUNNING;

        /**
         * Are we running the animation or not
         */
        private boolean mRun = false;

        /**
         * Handle to the surface manager object we interact with
         */
        private SurfaceHolder surfaceHolder;

        public SoulRenderThread(SurfaceHolder surfaceHolder, Context context) {
            this.surfaceHolder = surfaceHolder;

            renderer = new SoulRenderer(context);
        }


        /**
         * Pauses the animation
         */
        public void pause() {
            synchronized (surfaceHolder) {
                mMode = STATE_PAUSED;
            }
        }

        @Override
        public void run() {
            try {
                Thread.sleep(500);
            }
            catch (Exception ignored) {
            }

            while (mRun) {
                Canvas c = null;
                try {
                    c = surfaceHolder.lockCanvas();
                    synchronized (surfaceHolder) {
                        if (mMode == STATE_RUNNING) {
                            // Do not render if we are not running anymore
                            if (mRun && c != null) {
                                renderer.doDraw(c);
                            }
                        }
                    }
                } finally {
                    //Now, be a good boy and always unlock the canvas
                    if (c != null) {
                        surfaceHolder.unlockCanvasAndPost(c);
                    }
                }
            }
        }

        public Bundle saveState(Bundle map) {
            synchronized (surfaceHolder) {
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
            mRun = b;
            mMode = b ? STATE_RUNNING : STATE_PAUSED;
        }

        public void setState(int state) {
            mMode = state;
        }

        /* Callback invoked when the surface dimensions change. */
        public void setSurfaceSize(int width, int height) {
            synchronized (surfaceHolder) {
                renderer.setSurfaceSize(width, height);
            }
        }

    }

    /**
     * The thread that actually draws the animation
     */
    private SoulRenderThread thread;

    public SoulRenderView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // register our interest in hearing about changes to our surface
        SurfaceHolder holder = getHolder();
        holder.addCallback(this);

        // create thread only; it's started in surfaceCreated()
        thread = new SoulRenderThread(holder, context);
    }

    /**
     * Get the thread rendering the visualization
     *
     * @return the thread
     */
    public SoulRenderThread getThread() {
        return thread;
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {
        thread.setSurfaceSize(width, height);
    }

    public void surfaceCreated(SurfaceHolder holder) {
        if (thread.getState() == Thread.State.TERMINATED) {
            thread = new SoulRenderThread(holder, getContext());
        }
        //3...2...1....GO!
        thread.setRunning(true);
        if (!thread.isAlive()) {
            thread.start();
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        // Make sure we pause the thread before we return.
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

    public void pause() {
        thread.setState(SoulRenderThread.STATE_PAUSED);
    }

    public void resume() {
        thread.setState(SoulRenderThread.STATE_RUNNING);
    }

}