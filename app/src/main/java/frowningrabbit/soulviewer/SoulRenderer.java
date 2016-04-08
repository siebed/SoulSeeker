package frowningrabbit.soulviewer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.support.v8.renderscript.Allocation;
import android.support.v8.renderscript.Element;
import android.support.v8.renderscript.RenderScript;
import android.support.v8.renderscript.ScriptIntrinsicBlur;
import android.util.Log;

import java.util.Arrays;

class SoulRenderer {

    private static final int PARTICALS = 8000;
    public static final float DRAW_FULL_ARM_THRESHOLD = 0.8f;
    public static final float DRAW_NO_ARM_THRESHOLD = 0.1f;
    public static final float FULL_MOOD_THRESHOLD = 0.8f;
    public static final float NO_MOOD_THRESHOLD = 0.2f;
    public static final int RENDER_DIVIDER = 3;
    public static final PorterDuff.Mode DEFAULT_XFER_MODE = PorterDuff.Mode.LIGHTEN;
    private final Context mContext;
    private int[][] pixelColors;
    private int[] pixels;
    private int originalWidth;
    private int originalHeight;
    private int renderWidth;
    private int renderHeight;
    private int[][] coral;
    private int[] drawPointsX;
    private int[] drawPointsY;
    private float scaleRate = 1.2f;
    private boolean initialized;
    double colorCounter = 300;
    int[] colorPallet = new int[256];
    double radius = 1.5;
    int intx = 0;
    int inty = 0;
    int intz = 0;
    float scaleFactor = 1;
    long startTime = System.currentTimeMillis();
    long timingStart, timingEnd;
    int frames = 0;
    long cumuTime = 0;

    //vars controlled by prefs
    private boolean shouldBlur = true;
    private boolean shouldScale = true;
    float drawLeftArm = 1f;
    float drawRightArm = 1f;
    private float rotationSpeed = 1f;
    private boolean useForegroundColor;
    private int foregroundColor;
    private SurfaceInfo sourceBuffer = new SurfaceInfo();
    private SurfaceInfo destinationBuffer = new SurfaceInfo();
//    private Allocation mBlurInput, mBlurOutput;
    private Paint paint;
    private Paint blendPaint;
    private Paint centerPointpaint;
    RenderScript rs;
    private ScriptIntrinsicBlur theIntrinsic;
    private float moodIndex = 1;
    private Rect sourceRect;
    private Rect destCanvasRect;
    private Rect scaleSubRect;
    private PorterDuff.Mode porterDuffMode = DEFAULT_XFER_MODE;
    private Bitmap blendBitmap;

    public void setDrawLeftArm(float drawLeftArm) {
        this.drawLeftArm = drawLeftArm;
    }

    public void setDrawRightArm(float drawRightArm) {
        this.drawRightArm = drawRightArm;
    }

    public void setMoodIndex(float moodIndex) {

        this.moodIndex = Math.abs(moodIndex);
    }

    public void changeXferMode(PorterDuff.Mode mode) {
        porterDuffMode = mode;
        setXferMode();
    }

    public void setToNeutral() {
        moodIndex = 1;
        drawLeftArm = 1f;
        drawRightArm = 1f;
        porterDuffMode = DEFAULT_XFER_MODE;
    }

    public void setShouldBlur(boolean shouldBlur) {
        this.shouldBlur = shouldBlur;
    }

    public void setShouldScale(boolean shouldScale) {
        this.shouldScale = shouldScale;
    }

    public void setRotationSpeed(float rotationSpeed) {
        this.rotationSpeed = rotationSpeed;
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
     * Handle to the surface manager object we interact with
     */

    public SoulRenderer(Context context) {
        // get handles to some important objects
        mContext = context;

//            processPreferences();
        initializeData();
        make3DBrownian();
        initColor();
        startTime = System.currentTimeMillis();
    }

    public void initializeData() {
        coral = new int[PARTICALS][3];
        drawPointsX = new int[2 * PARTICALS];
        drawPointsY = new int[2 * PARTICALS];

        initialized = true;

        centerPointpaint = new Paint();
        centerPointpaint.setColor(Color.BLACK);
        centerPointpaint.setStrokeWidth(2);

        blendPaint = new Paint();

        paint = new Paint();
        paint.setStrokeWidth(scaleFactor);
        setXferMode();

        rs = RenderScript.create(mContext);
        theIntrinsic = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
    }

    private void setXferMode() {
        paint.setXfermode(new PorterDuffXfermode(porterDuffMode));
        blendPaint.setXfermode(new PorterDuffXfermode(porterDuffMode));
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

    /* Callback invoked when the surface dimensions change. */
    public void setSurfaceSize(int width, int height) {
        // synchronized to make sure these all change atomically
        originalWidth = width;
        originalHeight = height;
        renderWidth = originalWidth / RENDER_DIVIDER;
        renderHeight = originalHeight / RENDER_DIVIDER;
        pixelColors = new int[renderWidth][renderHeight];
        pixels = new int[renderWidth * renderHeight];
        sourceBuffer.setBitmap(Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888));
        destinationBuffer.setBitmap(Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888));
        blendBitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888);

        sourceRect = new Rect(0, 0, renderWidth, renderHeight);
        destCanvasRect = new Rect(0, 0, originalWidth, originalHeight);

        int xScaleOffset = (int) (renderWidth - (renderWidth / scaleRate)) / 2;
        int yScaleOffset = (int) (renderHeight - (renderHeight / scaleRate)) / 2;
        scaleSubRect = new Rect(xScaleOffset, yScaleOffset, renderWidth - xScaleOffset, renderHeight - yScaleOffset);

//        mBlurInput = Allocation.createFromBitmap(rs, sourceBuffer.bitmap, Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);
//        mBlurOutput = Allocation.createTyped(rs, mBlurInput.getType());
    }


    /**
     * Draw the visualization
     */
    public void doDraw(Canvas canvas) {
        StringBuilder timingString = new StringBuilder("[Rendertime] ");

        frames++;
        startTime = System.currentTimeMillis();

        timingStart = startTime;
        if (shouldScale) {
            scale(sourceBuffer, destinationBuffer);
        } else {
            swapBufferRefs();
        }
        timingEnd = System.currentTimeMillis();
        timingString.append(" scale: " + (timingEnd - timingStart));

        timingStart = timingEnd;
        rotateCoral(radius, radius * 2.72);
        timingEnd = System.currentTimeMillis();
        timingString.append(" rotateCoral: " + (timingEnd - timingStart));

        radius += (6.248 / 256) * rotationSpeed;

        timingStart = timingEnd;
        initColor();
        timingEnd = System.currentTimeMillis();
        timingString.append(" initColor: " + (timingEnd - timingStart));

        timingStart = timingEnd;
        renderColors(destinationBuffer);
        timingEnd = System.currentTimeMillis();
        timingString.append(" renderColors: " + (timingEnd - timingStart));

        if (shouldBlur) {
            //switch the buffers
            swapBufferRefs();
            timingStart = System.currentTimeMillis();
            blur(sourceBuffer, destinationBuffer);
            timingEnd = System.currentTimeMillis();
            timingString.append(" blur: " + (timingEnd - timingStart));
        }

        timingStart = System.currentTimeMillis();
        canvas.drawBitmap(destinationBuffer.bitmap, sourceRect, destCanvasRect, null);
        timingEnd = System.currentTimeMillis();
        timingString.append(" canvas.drawBitmap: " + (timingEnd - timingStart));

        //switch the buffers, so we add to the final version next iteration
        swapBufferRefs();
//        timingStart = System.currentTimeMillis();
//        destinationBuffer.bitmap.getPixels(pixels, 0,renderWidth,0,0,renderWidth, renderHeight);
//        destinationBuffer.bitmap.setPixels(pixels,0,renderWidth,0,0, renderWidth, renderHeight);
//        destinationBuffer.canvas.drawBitmap(sourceBuffer.bitmap, 0,0, blendPaint);
//        timingEnd = System.currentTimeMillis();
//        timingString.append(" get/set pixels: " + (timingEnd - timingStart));

        cumuTime += System.currentTimeMillis() - startTime;
//        Log.i("draw average", "" + (cumuTime / frames));
        Log.i("timing", timingString.toString());
        Log.i("total frame time: ", (System.currentTimeMillis() - startTime) + " ms");

    }

    private void swapBufferRefs() {
        SurfaceInfo tmp = sourceBuffer;
        sourceBuffer = destinationBuffer;
        destinationBuffer = tmp;
    }

    private void renderColors(SurfaceInfo buffer) {
        for(int i = 0; i < drawPointsCounter; i++) {
            //paint.setColor(colorPallet[Math.min(255, pixelColors[drawPointsX[i]][drawPointsY[i]])]);
            //buffer.canvas.drawPoint(drawPointsX[i], drawPointsY[i], paint);

            pixels[(drawPointsY[i] * renderWidth) + drawPointsX[i]] = colorPallet[Math.min(255, pixelColors[drawPointsX[i]][drawPointsY[i]])];
        }

        blendBitmap.setPixels(pixels,0,renderWidth,0,0, renderWidth, renderHeight);
        buffer.canvas.drawBitmap(blendBitmap, 0,0, blendPaint);
        Arrays.fill(pixels, 0);

//        for (int j = 0; j < renderHeight; j++) {
//            for (int i = 0; i < renderWidth; i++) {
//                // get value from pixel in scaled image, and store
//                if (pixelColors[i][j] > 0) {
//                    paint.setColor(colorPallet[Math.min(255, pixelColors[i][j])]);
//                    buffer.canvas.drawPoint(i, j, paint);
////                    buffer.bitmap.setPixel(i, j, Color.GREEN); //colorPallet[Math.min(255, pixelColors[i][j])]);
////                    pixels[(j * renderWidth) + i] = colorPallet[Math.min(255, pixelColors[i][j])];
//               // } else {
////                    if(pixels[(j * renderWidth) + i] != 0) {
////                        pixels[(j * renderWidth) + i] = 0;
////                    }
//                }
//            }
//        }
        buffer.canvas.drawPoint(renderWidth / 2, renderHeight / 2, centerPointpaint);
    }

    private void clearColors() {
        for (int i = 0; i < drawPointsCounter; i++) {
            pixelColors[drawPointsX[i]][ drawPointsY[i]] = 0;
        }

//        for (int j = 0; j < pixelColors.length; j++) {
//            Arrays.fill(pixelColors[j], 0);
//        }
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
        destination.canvas.drawBitmap(source.bitmap, scaleSubRect, sourceRect, null);
    }


    /**
     * Init the color pallet for this frame. Depending on 'useForegroundColor' it will cycle through all colors of the spectrum
     * or a set foreground color. To give the effect of glowing particles the color will go from black to a set color to white.
     * The higher the startIntensity the brighter the colours will be.
     */
    private void initColor() {
        int red, green, blue;
        int endIntensity = 256;
        if (!useForegroundColor) {
            int startIntensity = moodIndex > FULL_MOOD_THRESHOLD ? 128 : (int) (Math.max(NO_MOOD_THRESHOLD, moodIndex * 1.6f) * 128);
            endIntensity = 2 * startIntensity;
            red = Math.max(0, (int) (startIntensity + (127 * Math.cos(colorCounter++ / 150))));
            green = Math.max(0, (int) (startIntensity + (127 * Math.cos(colorCounter / 80))));
            blue = Math.max(0, (int) (startIntensity + (127 * Math.cos(colorCounter / 220))));

        } else {
            red = (foregroundColor >> 16) & 0x000000ff;
            green = (foregroundColor >> 8) & 0x000000ff;
            blue = foregroundColor & 0x000000ff;
        }

        for (int i = 0; i < 128; i++) {
            colorPallet[i] = Color.rgb(((red * i) / 128), ((green * i) / 128), ((blue * i) / 128));

            colorPallet[128 + i] = Color.rgb((red + (((endIntensity - red) * i) / 128)),
                    (green + (((endIntensity - green) * i) / 128)),
                    (blue + (((endIntensity - blue) * i) / 128)));
        }
    }

    double cosX, sinX, cosY, sinY;
    double x, y, z, newx, newy, newz;
    int centerX, centerY;
    int finalX, finalY, finalZ;
    int drawPointsCounter;

    private void rotateCoral(double angelX, double angelY) {
        centerX = renderWidth / 2;
        centerY = renderHeight / 2;

        cosX = Math.cos(angelX);
        sinX = Math.sin(angelX);
        cosY = Math.cos(angelY);
        sinY = Math.sin(angelY);
        clearColors();
        drawPointsCounter = 0;
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

            int luminus = (int) (4 + (moodIndex * 4));
            if (centerX - Math.abs(finalX) > 0 && centerY - Math.abs(finalY) > 0) {
                if (drawLeftArm > DRAW_FULL_ARM_THRESHOLD || (drawLeftArm > DRAW_NO_ARM_THRESHOLD && (i * drawLeftArm) % 1f < drawLeftArm)) {
                    if(pixelColors[centerX + finalX][centerY + finalY] == 0) {
                        drawPointsX[drawPointsCounter] = centerX + finalX;
                        drawPointsY[drawPointsCounter] = centerY + finalY;
                        drawPointsCounter++;
                    }
                    pixelColors[centerX + finalX][centerY + finalY] += luminus; //add to the color
                }

                if (drawRightArm > DRAW_FULL_ARM_THRESHOLD || (drawLeftArm > DRAW_NO_ARM_THRESHOLD && (i * drawRightArm) % 1f < drawRightArm)) {
                    if(pixelColors[centerX - finalX][centerY - finalY] == 0) {
                        drawPointsX[drawPointsCounter] = centerX - finalX;
                        drawPointsY[drawPointsCounter] = centerY - finalY;
                        drawPointsCounter++;
                    }
                    pixelColors[centerX - finalX][centerY - finalY] += luminus; //add to the color
                }
            }
        }
    }

}