package net.ddns.anderserver.touchfadersapp;

/**
 * Created by alpaslanbak on 29/09/2017.
 * Modified by Nick Panagopoulos @npanagop on 12/05/2018.
 * Modified by Craig Anderson @theonlytechnohead on 25/11/2020.
 */

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;

import org.junit.Assert;

import static java.lang.Math.abs;

public class BoxedVertical extends View{
    private static final String TAG = BoxedVertical.class.getSimpleName();

    private static final int MAX = 100;
    private static final int MIN = 0;

    /**
     * The min value of progress value.
     */
    private int mMin = MIN;

    /**
     * The Maximum value that this SeekArc can be set to
     */
    private int mMax = MAX;

    /**
     * The increment/decrement value for each movement of progress.
     */
    private int mStep = 10;

    /**
     * The corner radius of the view.
     */
    private int mCornerRadius = 10;

    /**
     * Text size in SP.
     */
    private float mTextSize = 26;

    /**
     * Text bottom padding in pixel.
     */
    private int mtextBottomPadding = 20;

    private int mPoints;

    private boolean mEnabled = true;
    /**
     * Enable or disable text .
     */
    private boolean mTextEnabled = true;

    /**
     * Enable or disable image .
     */
    private boolean mImageEnabled = false;

    /**
     * mTouchDisabled touches will not move the slider
     * only swipe motion will activate it
     */
    private boolean mTouchDisabled = true;

    private boolean touchAllowed = true;

    private float mProgressSweep = 0;
    private float lastProgressSweep = 0;

    private final Paint drawPaint = new Paint();
    private final Path clippingPath = new Path();
    private final RectF boundingRect = new RectF();
    private LinearGradient nearClipGradient;
    private LinearGradient overUnityGradient;
    private LinearGradient normalGradient;
    private Paint mTextPaint;

    private OnValuesChangeListener mOnValuesChangeListener;
    private int gradientStart;
    private int gradientEnd;
    private int backgroundColor;
    private int mDefaultValue;
    private Bitmap mDefaultImage;
    private Bitmap mMinImage;
    private Bitmap mMaxImage;
    private final Rect dRect = new Rect();
    private boolean firstRun = true;
    private int progressOffset;
    private int touchStarted_X;
    private int touchStarted_Y;

    private String[] textValues;

    public BoxedVertical(Context context) {
        super(context);
        init(context, null);
    }

    public BoxedVertical(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        //System.out.println("INIT");
        float density = getResources().getDisplayMetrics().density;

        // Defaults, may need to link this into theme settings
        int progressColor = ContextCompat.getColor(context, R.color.color_progress);
        backgroundColor = ContextCompat.getColor(context, R.color.color_background);
        backgroundColor = ContextCompat.getColor(context, R.color.color_background);

        int textColor = ContextCompat.getColor(context, R.color.color_text);
        mTextSize = (int) (mTextSize * density);
        mDefaultValue = mMax/2;

        if (attrs != null) {
            final TypedArray a = context.obtainStyledAttributes(attrs,
                    R.styleable.BoxedVertical, 0, 0);

            mPoints = a.getInteger(R.styleable.BoxedVertical_points, mPoints);
            mMax = a.getInteger(R.styleable.BoxedVertical_max, mMax);
            mMin = a.getInteger(R.styleable.BoxedVertical_min, mMin);
            mStep = a.getInteger(R.styleable.BoxedVertical_step, mStep);
            mDefaultValue = a.getInteger(R.styleable.BoxedVertical_startValue, mDefaultValue);
            mCornerRadius = a.getInteger(R.styleable.BoxedVertical_corner, mCornerRadius);
            mtextBottomPadding = a.getInteger(R.styleable.BoxedVertical_textBottomPadding, mtextBottomPadding);
            //Images
            mImageEnabled = a.getBoolean(R.styleable.BoxedVertical_imageEnabled, mImageEnabled);

            if (mImageEnabled){
                Assert.assertNotNull("When images are enabled, defaultImage can not be null. Please assign a drawable in the layout XML file", a.getDrawable(R.styleable.BoxedVertical_defaultImage));
                Assert.assertNotNull("When images are enabled, minImage can not be null. Please assign a drawable in the layout XML file", a.getDrawable(R.styleable.BoxedVertical_minImage));
                Assert.assertNotNull("When images are enabled, maxImage can not be null. Please assign a drawable in the layout XML file", a.getDrawable(R.styleable.BoxedVertical_maxImage));

                mDefaultImage = ((BitmapDrawable) a.getDrawable(R.styleable.BoxedVertical_defaultImage)).getBitmap();
                mMinImage = ((BitmapDrawable) a.getDrawable(R.styleable.BoxedVertical_minImage)).getBitmap();
                mMaxImage = ((BitmapDrawable) a.getDrawable(R.styleable.BoxedVertical_maxImage)).getBitmap();
            }

            progressColor = a.getColor(R.styleable.BoxedVertical_progressColor, progressColor);
            gradientStart = a.getColor(R.styleable.BoxedVertical_gradientStart, progressColor);
            gradientEnd = a.getColor(R.styleable.BoxedVertical_gradientEnd, progressColor);
            backgroundColor = a.getColor(R.styleable.BoxedVertical_backgroundColor, backgroundColor);

            mTextSize = (int) a.getDimension(R.styleable.BoxedVertical_textSize, mTextSize);
            textColor = a.getColor(R.styleable.BoxedVertical_textColor, textColor);

            mEnabled = a.getBoolean(R.styleable.BoxedVertical_enabled, mEnabled);
            mTouchDisabled = a.getBoolean(R.styleable.BoxedVertical_touchDisabled, mTouchDisabled);
            mTextEnabled = a.getBoolean(R.styleable.BoxedVertical_textEnabled, mTextEnabled);

            textValues = getResources().getStringArray(R.array.fader_db);

            mPoints = mDefaultValue;

            a.recycle();
        }

        // range check
        mPoints = Math.min(mPoints, mMax);
        mPoints = Math.max(mPoints, mMin);

        //mProgressPaint = new Paint();
        //mProgressPaint.setColor(progressColor);
        //mProgressPaint.setAntiAlias(true);
        //mProgressPaint.setStyle(Paint.Style.STROKE);

        mTextPaint = new Paint();
        mTextPaint.setColor(textColor);
        mTextPaint.setAntiAlias(true);
        mTextPaint.setStyle(Paint.Style.FILL);
        mTextPaint.setTextSize(mTextSize);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (firstRun){
            setValue(mPoints);
            firstRun = false;
        }

        boundingRect.bottom = getHeight();
        boundingRect.right = getWidth();
        clippingPath.addRoundRect(boundingRect, mCornerRadius, mCornerRadius, Path.Direction.CCW);
        canvas.clipPath(clippingPath, Region.Op.INTERSECT);
        drawPaint.setColor(backgroundColor);
        drawPaint.setAntiAlias(true);
        canvas.drawRect(0, 0, getWidth(), getHeight(), drawPaint);

        if (mPoints >= 0.95f * mMax) {
            if (nearClipGradient == null || lastProgressSweep != mProgressSweep) {
                nearClipGradient = new LinearGradient(0, mProgressSweep, 0, getHeight(), ContextCompat.getColor(getContext(), R.color.red), gradientStart, Shader.TileMode.MIRROR);
            }
            drawPaint.setShader(nearClipGradient);
        } else if (0.8f * mMax <= mPoints && mPoints < 0.95f * mMax) {
            if (overUnityGradient == null || lastProgressSweep != mProgressSweep) {
                overUnityGradient = new LinearGradient(0, mProgressSweep, 0, getHeight(), ContextCompat.getColor(getContext(), R.color.yellow), gradientStart, Shader.TileMode.MIRROR);
            }
            drawPaint.setShader(overUnityGradient);
        } else {
            if (normalGradient == null || lastProgressSweep != mProgressSweep) {
                normalGradient = new LinearGradient(0, mProgressSweep, 0, getHeight(), gradientEnd, gradientStart, Shader.TileMode.MIRROR);
            }
            drawPaint.setShader(normalGradient);
        }
        canvas.drawRect(0, mProgressSweep, getWidth(), getHeight(), drawPaint);

        drawPaint.reset();
        drawPaint.setColor(ContextCompat.getColor(getContext(), R.color.grey));
        canvas.drawRect(getWidth()*0.1f, getHeight()*0.190f, getWidth()*0.9f, getHeight()*0.202f, drawPaint); // 0dB
        canvas.drawRect(getWidth()*0.175f, getHeight()*0.387f, getWidth()*0.825f, getHeight()*0.395f, drawPaint); // -10dB
        canvas.drawRect(getWidth()*0.35f, getHeight()*0.582f, getWidth()*0.65f, getHeight()*0.590f, drawPaint); // -20dB
        canvas.drawRect(getWidth()*0.35f, getHeight()*0.778f, getWidth()*0.65f, getHeight()*0.786f, drawPaint); // -40dB

        if (mImageEnabled && mDefaultImage != null && mMinImage != null && mMaxImage != null){
            //If image is enabled, text will not be shown
            if (mPoints == mMax){
                drawIcon(mMaxImage, canvas);
            }
            else if (mPoints == mMin){
                drawIcon(mMinImage, canvas);
            }
            else{
                drawIcon(mDefaultImage, canvas);
            }
        }
        else{
            //If image is disabled and text is enabled show text
            if (mTextEnabled){
//                String strPoint = String.valueOf(mPoints);
                drawText(canvas, mTextPaint, textValues[mPoints]);
            }
        }
        lastProgressSweep = mProgressSweep;
    }

    private void drawText(Canvas canvas, Paint paint, String text) {
        canvas.save();

        canvas.getClipBounds(dRect);
        int cWidth = dRect.width();
        paint.setTextAlign(Paint.Align.LEFT);
        paint.getTextBounds(text, 0, text.length(), dRect);
        float x = cWidth / 2f - dRect.width() / 2f - dRect.left;
        int textColor = mTextPaint.getColor();

        Rect r_white = new Rect((int) x, (int) mProgressSweep, cWidth, getHeight());
        Rect r_black = new Rect((int) x, 0, (int) (x + cWidth), (int) mProgressSweep);

        canvas.save();
        canvas.clipRect(r_black);
        paint.setColor(Color.WHITE);
        canvas.drawText(text, x, canvas.getHeight() - mtextBottomPadding, paint);
        canvas.restore();

        canvas.save();
        canvas.clipRect(r_white);
        paint.setColor(textColor);
        canvas.drawText(text, x, canvas.getHeight() - mtextBottomPadding, paint);
        canvas.restore();

        canvas.restore();
    }

    private void drawIcon(Bitmap bitmap, Canvas canvas){
        bitmap = getResizedBitmap(bitmap,canvas.getWidth()/2, canvas.getWidth()/2);
        canvas.drawBitmap(bitmap, null, new RectF((canvas.getWidth()/2f)-(bitmap.getWidth()/2f), canvas.getHeight()-bitmap.getHeight(), (canvas.getWidth()/3)+bitmap.getWidth(), canvas.getHeight()), null);
    }

    private Bitmap getResizedBitmap(Bitmap bm, int newHeight, int newWidth) {
        //Thanks Piyush
        int width = bm.getWidth();
        int height = bm.getHeight();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        // create a matrix for the manipulation
        Matrix matrix = new Matrix();
        // resize the bit map
        matrix.postScale(scaleWidth, scaleHeight);
        // recreate the new Bitmap
        return Bitmap.createBitmap(bm, 0, 0, width, height, matrix, false);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mEnabled) {

            this.getParent().requestDisallowInterceptTouchEvent(true);

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    touchAllowed = true;
                    touchStarted_X = (int) event.getAxisValue(MotionEvent.AXIS_X);
                    touchStarted_Y = (int) event.getAxisValue(MotionEvent.AXIS_Y);
                    //if (mOnValuesChangeListener != null) mOnValuesChangeListener.onStartTrackingTouch(this);
                    if (!mTouchDisabled) updateOnTouch(event);
                    progressOffset = (int) (Math.round(event.getY()) - mProgressSweep);
                    break;
                case MotionEvent.ACTION_MOVE:
                    int difference_X = abs((int) event.getAxisValue(MotionEvent.AXIS_X) - touchStarted_X);
                    if (25 <= difference_X && touchAllowed) {
                        this.getParent().requestDisallowInterceptTouchEvent(false);
                    }
                    int difference_Y = abs((int) event.getAxisValue(MotionEvent.AXIS_Y) - touchStarted_Y);
                    if (15 <= difference_Y) {
                        touchAllowed = false;
                        touchStarted_Y = mMax;
                        updateOnTouch(event);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    //if (mOnValuesChangeListener != null) mOnValuesChangeListener.onStopTrackingTouch(this);
                    setPressed(false);
                    this.getParent().requestDisallowInterceptTouchEvent(false);
                    break;
                case MotionEvent.ACTION_CANCEL:
                    //if (mOnValuesChangeListener != null) mOnValuesChangeListener.onStopTrackingTouch(this);
                    setPressed(false);
                    this.getParent().requestDisallowInterceptTouchEvent(false);
                    break;
            }
            return true;
        }
        return false;
    }

    /**
     * Update the UI components on touch events.
     *
     * @param event MotionEvent
     */
    private void updateOnTouch(MotionEvent event) {
        setPressed(true);
        //double mTouch = convertTouchEventPoint(event.getY());
        double mTouch = event.getY();
        int progress = (int) Math.round(mTouch);
        updateProgress(progress);
    }

    private double convertTouchEventPoint(float yPos) {
        float wReturn;

        if (yPos > (getHeight() *2)) {
            wReturn = getHeight() *2;
            return wReturn;
        }
        else if(yPos < 0){
            wReturn = 0;
        }
        else {
            wReturn =  yPos;
        }

        return wReturn;
    }

    private void updateProgress(int progress) {
        float adjustedProgress = progress - progressOffset;
        adjustedProgress = Math.min(adjustedProgress, getHeight());
        adjustedProgress = Math.max(adjustedProgress, 0);

        // convert progress to min-max range
        mPoints = (int) (adjustedProgress * (mMax - mMin) / (float) getHeight() + mMin);
        //reverse value because progress is descending
        mPoints = mMax + mMin - mPoints;
        // if value is not max or min, apply step
        if (mPoints != mMax && mPoints != mMin) {
            mPoints = mPoints - (mPoints % mStep) + (mMin % mStep);
        }

        mProgressSweep = adjustedProgress;

        if (mOnValuesChangeListener != null) {
            mOnValuesChangeListener.onPointsChanged(this, mPoints);
        }

        invalidate();
    }

    /**
     * Gets a value, converts it to progress for the seekBar and updates it.
     * @param value The value given
     */
    private void updateProgressByValue(int value) {
        mPoints = value;

        mPoints = Math.min(mPoints, mMax);
        mPoints = Math.max(mPoints, mMin);

        //convert min-max range to progress
        mProgressSweep = (mPoints - mMin) * getHeight() / (float)(mMax - mMin);
        //reverse value because progress is descending
        mProgressSweep = getHeight() - mProgressSweep;

        if (mOnValuesChangeListener != null) {
            // Avoid OSC loopback stuff
            //mOnValuesChangeListener.onPointsChanged(this, mPoints);
        }

        invalidate();
    }

    public interface OnValuesChangeListener {
        /**
         * Notification that the point value has changed.
         *
         * @param boxedPoints The SwagPoints view whose value has changed
         * @param points     The current point value.
         */
        void onPointsChanged(BoxedVertical boxedPoints, int points);
        //void onStartTrackingTouch(BoxedVertical boxedPoints);
        //void onStopTrackingTouch(BoxedVertical boxedPoints);
    }

    public void setValue(int points) {
        points = Math.min(points, mMax);
        points = Math.max(points, mMin);

        updateProgressByValue(points);
    }

    public int getValue() {
        return mPoints;
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    public void setEnabled(boolean enabled) {
        this.mEnabled = enabled;
    }

    public int getMax() {
        return mMax;
    }

    public void setMax(int mMax) {
        if (mMax <= mMin)
            throw new IllegalArgumentException("Max should not be less than min");
        this.mMax = mMax;
    }

    public void setCornerRadius(int mRadius) {
        this.mCornerRadius = mRadius;
        invalidate();
    }

    public void setGradientStart(int colour) {
        gradientStart = colour;
        invalidate();
    }

    public void setGradientEnd(int colour) {
        gradientEnd = colour;
        invalidate();
    }

    public int getCornerRadius() {
        return mCornerRadius;
    }

    public int getDefaultValue() {
        return mDefaultValue;
    }

    public void setDefaultValue(int mDefaultValue) {
        if (mDefaultValue > mMax)
            throw new IllegalArgumentException("Default value should not be bigger than max value.");
        this.mDefaultValue = mDefaultValue;

    }

    public int getStep() {
        return mStep;
    }

    public void setStep(int step) {
        mStep = step;
    }

    public boolean isImageEnabled() {
        return mImageEnabled;
    }

    public void setImageEnabled(boolean mImageEnabled) {
        this.mImageEnabled = mImageEnabled;
    }

    public void setOnBoxedPointsChangeListener(OnValuesChangeListener onValuesChangeListener) {
        mOnValuesChangeListener = onValuesChangeListener;
    }
}
