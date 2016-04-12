package frowningrabbit.soulviewer;

import android.content.Context;
import android.content.SharedPreferences;
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

import java.util.Arrays;

public class SoulRenderer {

    private static final int PARTICALS = 8000;
    public static final float DRAW_FULL_ARM_THRESHOLD = 0.8f;
    public static final float DRAW_NO_ARM_THRESHOLD = 0.1f;
    public static final float FULL_MOOD_THRESHOLD = 0.8f;
    public static final float NO_MOOD_THRESHOLD = 0.8f;
    public static final int RENDER_DIVIDER = 4;
    public static final PorterDuff.Mode DEFAULT_XFER_MODE = PorterDuff.Mode.LIGHTEN;

    private int[][] pixelColors;
    private int[] pixels;
    private int originalWidth;
    private int originalHeight;
    private int renderWidth;
    private int renderHeight;
    private int renderDetail = RENDER_DIVIDER;
    private int newRenderDetail = RENDER_DIVIDER;
    private int[][] coral;
    private int[] drawPointsX;
    private int[] drawPointsY;
    private float scaleRate = 1.2f; //rate at which we scale previous frames
    double colorCounter = 300;
    int[] colorPallet = new int[256];
    double radius = 1.5;
    int intx = 0;
    int inty = 0;
    int intz = 0;
    long startTime;
    long frameTime;

    //vars controlled by prefs
    private boolean shouldBlur = true;
    private boolean useForegroundColor;
    private int foregroundColor;
    private float rotationSpeed = 1f;


    float drawLeftArm = 1f; //What percentage of the arm we draw
    float drawRightArm = 1f;
    private SurfaceInfo sourceBuffer = new SurfaceInfo();
    private SurfaceInfo destinationBuffer = new SurfaceInfo();
    private SurfaceInfo drawlessBuffer = new SurfaceInfo(); //Buffer we keep for when we detect nothing. Without it the smoke would not fade out (and in)
    private Paint blendPaint; //paint for blending the drawing into the frame
    private Paint centerPointpaint; //paint for making a dot in the middle of the screen. This gives a better scaling effect
    RenderScript rs;
    private ScriptIntrinsicBlur theIntrinsic; //used for blurring the frame
    private boolean moodIsSet = false; //wether or not we are detecting a mood
    private float moodIndex = 1; //how happy is the user
    private int moodlessCounter = 0; //counter to keep track of the frames without a detected mood
    private Rect sourceRect;
    private Rect destCanvasRect;
    private Rect scaleSubRect;
    private PorterDuff.Mode porterDuffMode = DEFAULT_XFER_MODE;
    private Bitmap blendBitmap;

    public void setDrawLeftArm(float drawLeftArm) {
        if (drawLeftArm > 0 && Math.abs(this.drawLeftArm - drawLeftArm) > 0.1f) {
            this.drawLeftArm = drawLeftArm;
        }
    }

    public void setDrawRightArm(float drawRightArm) {
        if (drawRightArm > 0 && Math.abs(this.drawRightArm - drawRightArm) > 0.1f) {
            this.drawRightArm = drawRightArm;
        }
    }

    public void setMoodIndex(float moodIndex) {
        //only react on 'mood swings'
        if (moodIndex > 0 && (this.moodIndex == 0 || Math.abs(this.moodIndex - moodIndex) > 0.1f)) {
            this.moodIsSet = true;
            this.moodIndex = Math.abs(moodIndex);
            if (moodIndex > 0.35) {
                changeXferMode(PorterDuff.Mode.ADD);
            } else {
                changeXferMode(DEFAULT_XFER_MODE);
            }
        }
    }

    public void changeXferMode(PorterDuff.Mode mode) {
        porterDuffMode = mode;
        setXferMode();
    }

    public void setToNeutral() {
        moodIsSet = false;
        moodIndex = 0;
        moodlessCounter = 0;
        drawLeftArm = 1f;
        drawRightArm = 1f;
        changeXferMode(DEFAULT_XFER_MODE);
    }

    public void setShouldBlur(boolean shouldBlur) {
        this.shouldBlur = shouldBlur;
    }

    public void setShouldScale(boolean shouldScale) {
        moodIndex = shouldScale ? 1 : 0; //We stop or start the smoke with the mood setting
    }

    public void setUseForegroundColor(boolean useForegroundColor) {
        this.useForegroundColor = useForegroundColor;
    }

    public void setForegroundColor(int foregroundColor) {
        this.foregroundColor = foregroundColor;
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

    public SoulRenderer(Context context) {
        initializeData(context);
        make3DBrownian();
        initColor();
        startTime = System.currentTimeMillis();
    }

    public void initializeData(Context context) {
        colorCounter = Math.random() * 6000; //start with a random color
        coral = new int[PARTICALS][3];
        drawPointsX = new int[2 * PARTICALS];
        drawPointsY = new int[2 * PARTICALS];

        centerPointpaint = new Paint();
        centerPointpaint.setColor(Color.BLACK);
        centerPointpaint.setStrokeWidth(8);

        blendPaint = new Paint();
        setXferMode();

        rs = RenderScript.create(context);
        theIntrinsic = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
    }

    private void setXferMode() {
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
        originalWidth = width;
        originalHeight = height;
        reinitSurfaceValues();
    }

    private void reinitSurfaceValues() {
        renderWidth = originalWidth / renderDetail;
        renderHeight = originalHeight / renderDetail;
        pixelColors = new int[renderWidth][renderHeight];
        pixels = new int[renderWidth * renderHeight];

        //recycle our bitmaps if needed
        if (sourceBuffer.bitmap != null) {
            sourceBuffer.bitmap.recycle();
        }
        if (destinationBuffer.bitmap != null) {
            destinationBuffer.bitmap.recycle();
        }
        if (blendBitmap != null) {
            blendBitmap.recycle();
        }
        if (drawlessBuffer.bitmap != null) {
            drawlessBuffer.bitmap.recycle();
        }

        //create bitmaps for the current screen size
        sourceBuffer.setBitmap(Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888));
        destinationBuffer.setBitmap(Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888));
        drawlessBuffer.setBitmap(Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888));
        blendBitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888);

        //Create rectangles to make copies from bitmap to bitmap
        sourceRect = new Rect(0, 0, renderWidth, renderHeight);
        destCanvasRect = new Rect(0, 0, originalWidth, originalHeight);

        int xScaleOffset = (int) (renderWidth - (renderWidth / scaleRate)) / 2;
        int yScaleOffset = (int) (renderHeight - (renderHeight / scaleRate)) / 2;
        scaleSubRect = new Rect(xScaleOffset, yScaleOffset, renderWidth - xScaleOffset, renderHeight - yScaleOffset);

        //reset needed fields
        frameTime = System.currentTimeMillis();
        drawPointsCounter = 0;
    }


    /**
     * Draw the visualization
     */
    public void doDraw(Canvas canvas) {
        if (canvas == null) {
            return;
        }

        //recreate al data if user tinkered with the settings
        if (newRenderDetail != renderDetail) {
            renderDetail = newRenderDetail;
            reinitSurfaceValues();
        }

        if (moodIndex == 0) {
            //we skip 1 frame, as we need the moodlessBuffer to be filled first with a frame
            if (moodlessCounter > 0) {
                //at the start we draw in the middle to prevent scaling artifacts
                if (moodlessCounter > 5 && moodlessCounter < 15) {
                    drawlessBuffer.canvas.drawCircle(renderWidth / 2, renderHeight / 2, 10, centerPointpaint);
                }
                //after a while just make everything black
                if (moodlessCounter > 100) {
                    sourceBuffer.canvas.drawColor(Color.BLACK);
                } else {
                    sourceBuffer.canvas.drawBitmap(drawlessBuffer.bitmap, 0, 0, null);
                }

            }
            moodlessCounter++;
        }

        scale(sourceBuffer, destinationBuffer);
        swapBufferRefs();

        //we copy the scaled version without any new drawing into our buffer for the next frame
        if (moodIndex == 0) {
            drawlessBuffer.canvas.drawBitmap(sourceBuffer.bitmap, 0, 0, null);
        }

        rotateCoral(radius, radius * 2.72);

        //If mood is set rotation is more active, like an excited puppy ;)
        if (moodIsSet) {
            radius += (System.currentTimeMillis() - frameTime) * ((6.248 / 25000) * (1.5f + moodIndex));
        } else {
            radius += (System.currentTimeMillis() - frameTime) * ((6.248 / 25000) * rotationSpeed);
        }
        frameTime = System.currentTimeMillis();

        initColor();

        renderColors(sourceBuffer);

        if (shouldBlur) {
            //switch the buffers
            blur(sourceBuffer, destinationBuffer);
            swapBufferRefs();
        }

        canvas.drawBitmap(sourceBuffer.bitmap, sourceRect, destCanvasRect, null);
    }

    private void swapBufferRefs() {
        SurfaceInfo tmp = sourceBuffer;
        sourceBuffer = destinationBuffer;
        destinationBuffer = tmp;
    }

    private void renderColors(SurfaceInfo buffer) {
        for (int i = 0; i < drawPointsCounter; i++) {
            pixels[(drawPointsY[i] * renderWidth) + drawPointsX[i]] = colorPallet[Math.min(255, pixelColors[drawPointsX[i]][drawPointsY[i]])];
        }

        blendBitmap.setPixels(pixels, 0, renderWidth, 0, 0, renderWidth, renderHeight);
        buffer.canvas.drawBitmap(blendBitmap, 0, 0, blendPaint);
        Arrays.fill(pixels, 0);

        buffer.canvas.drawCircle(renderWidth / 2, renderHeight / 2, 3, centerPointpaint);
    }

    private void clearColors() {
        for (int i = 0; i < drawPointsCounter; i++) {
            pixelColors[drawPointsX[i]][drawPointsY[i]] = 0;
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
        destination.canvas.drawBitmap(source.bitmap, scaleSubRect, sourceRect, null);
    }


    int startIntensity;
    int red, green, blue;
    int endIntensity = 256;

    /**
     * Init the color pallet for this frame. Depending on 'useForegroundColor' it will cycle through all colors of the spectrum
     * or a set foreground color. To give the effect of glowing particles the color will go from black to a set color to white.
     * The higher the startIntensity the brighter the colours will be.
     */
    private void initColor() {

        if (!useForegroundColor) {
            startIntensity = 128;
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

        endIntensity = 256;
    }

    double cosX, sinX, cosY, sinY;
    double x, y, z, newx, newy, newz;
    int centerX, centerY;
    int finalX, finalY, finalZ;
    int drawPointsCounter;
    int luminus;

    /**
     * Rotate our structure around the center point given an x and y angle. The final position of each
     * particle is used to add intensity of the color at that point of the screen, creating a 'glowing' effect.
     * Depending on the information about possible eyes and how far they are closed we draw only part
     * of the corresponding 'arm' of the structure.
     * For speed optimisation we keep track of which coordinates we draw to so we don't have to touch the whole bitmap every time.
     */
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

            luminus = PorterDuff.Mode.ADD.equals(porterDuffMode) ? 6 : 12; // Reduce luminus a bit when xfer mode is "ADD"

            if (moodIsSet) {
                luminus += (moodIndex * 8); //increase a bit depending on users happiness
            }

            if (centerX - Math.abs(finalX) > 0 && centerY - Math.abs(finalY) > 0) {
                if (drawLeftArm > DRAW_FULL_ARM_THRESHOLD || (drawLeftArm > DRAW_NO_ARM_THRESHOLD && (i * drawLeftArm) % 1f < drawLeftArm)) {
                    if (pixelColors[centerX + finalX][centerY + finalY] == 0) {
                        drawPointsX[drawPointsCounter] = centerX + finalX;
                        drawPointsY[drawPointsCounter] = centerY + finalY;
                        drawPointsCounter++;
                    }
                    pixelColors[centerX + finalX][centerY + finalY] += luminus; //add to the color
                }

                if (drawRightArm > DRAW_FULL_ARM_THRESHOLD || (drawLeftArm > DRAW_NO_ARM_THRESHOLD && (i * drawRightArm) % 1f < drawRightArm)) {
                    if (pixelColors[centerX - finalX][centerY - finalY] == 0) {
                        drawPointsX[drawPointsCounter] = centerX - finalX;
                        drawPointsY[drawPointsCounter] = centerY - finalY;
                        drawPointsCounter++;
                    }
                    pixelColors[centerX - finalX][centerY - finalY] += luminus; //add to the color
                }
            }
        }
    }

    public void processPreferences(SharedPreferences prefs, Context context) {
        setShouldBlur(prefs.getBoolean(context.getString(R.string.shouldBlur), true));
        setShouldScale(prefs.getBoolean(context.getString(R.string.shouldScale), true));
        setRotationSpeed(prefs.getInt(context.getString(R.string.rotationSpeed), 100) / 100f);
        setRotationSpeed(prefs.getInt(context.getString(R.string.rotationSpeed), 100) / 100f);
        changeXferMode(PorterDuff.Mode.valueOf(prefs.getString(context.getString(R.string.drawXferMode), "SRC")));
        setUseForegroundColor(prefs.getBoolean(context.getString(R.string.useForegroundColor), false));
        String foregroundCol = prefs.getString(context.getString(R.string.foregroundColorHex), "FF4D00");
        setForegroundColor(Color.parseColor("#" + foregroundCol.replace("#", "")));
        int detail = Integer.parseInt(prefs.getString(context.getString(R.string.renderDetail), "2"));
        if (detail != renderDetail) {
            newRenderDetail = detail;
        }

    }

}