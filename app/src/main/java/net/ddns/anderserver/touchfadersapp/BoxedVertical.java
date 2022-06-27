package net.ddns.anderserver.touchfadersapp;

/**
 * Created by alpaslanbak on 29/09/2017.
 * Modified by Nick Panagopoulos @npanagop on 12/05/2018.
 * Modified by Craig Anderson @theonlytechnohead on 25/11/2020.
 */

import static java.lang.Math.abs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;

public class BoxedVertical extends View {
    private static final String TAG = BoxedVertical.class.getSimpleName();

    private static final int MAX = 1023;
    private static final int MIN = 0;

    private int min = MIN;
    private int max = MAX;
    private int step = 1;

    private int cornerRadius = 10;
    private float textSize = 26;
    private int textBottomPadding = 20;

    private int mPoints;

    private boolean enabled = true;
    private boolean muted = false;
    private boolean meter = false;
    private boolean textEnabled = true;
    private boolean touchDisabled = true;
    private boolean touchAllowed = true;

    private float mProgressSweep = 0;
    private float lastProgressSweep = 0;

    private final Paint drawPaint = new Paint();
    private final Path clippingPath = new Path();
    private final RectF boundingRect = new RectF();
    private int nearClip = 923;
    private int overUnity = 824;
    private LinearGradient mutedGradient;
    private LinearGradient nearClipGradient;
    private LinearGradient overUnityGradient;
    private LinearGradient normalGradient;
    private Paint mTextPaint;

    private OnValuesChangeListener mOnValuesChangeListener;
    private int gradientStart;
    private int gradientEnd;
    private int backgroundColor;
    private int mDefaultValue;
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

    private void resetGradients() {
        mutedGradient = null;
        nearClipGradient = null;
        overUnityGradient = null;
        normalGradient = null;
    }

    private void init(Context context, AttributeSet attrs) {
        //System.out.println("INIT");
        float density = getResources().getDisplayMetrics().density;

        // Defaults, may need to link this into theme settings
        int progressColor = ContextCompat.getColor(context, R.color.color_progress);
        backgroundColor = ContextCompat.getColor(context, R.color.color_background);
        backgroundColor = ContextCompat.getColor(context, R.color.color_background);

        int textColor = ContextCompat.getColor(context, R.color.color_text);
        textSize = (int) (textSize * density);
        mDefaultValue = 823;

        if (attrs != null) {
            final TypedArray attributes = context.obtainStyledAttributes(attrs,
                    R.styleable.BoxedVertical, 0, 0);

            mPoints = attributes.getInteger(R.styleable.BoxedVertical_points, mPoints);
            max = attributes.getInteger(R.styleable.BoxedVertical_max, max);
            min = attributes.getInteger(R.styleable.BoxedVertical_min, min);
            step = attributes.getInteger(R.styleable.BoxedVertical_step, step);
            mDefaultValue = attributes.getInteger(R.styleable.BoxedVertical_startValue, mDefaultValue);
            cornerRadius = attributes.getInteger(R.styleable.BoxedVertical_corner, cornerRadius);
            textBottomPadding = attributes.getInteger(R.styleable.BoxedVertical_textBottomPadding, textBottomPadding);

            progressColor = attributes.getColor(R.styleable.BoxedVertical_progressColor, progressColor);
            gradientStart = attributes.getColor(R.styleable.BoxedVertical_gradientStart, progressColor);
            gradientEnd = attributes.getColor(R.styleable.BoxedVertical_gradientEnd, progressColor);
            backgroundColor = attributes.getColor(R.styleable.BoxedVertical_backgroundColor, backgroundColor);

            textSize = (int) attributes.getDimension(R.styleable.BoxedVertical_textSize, textSize);
            textColor = attributes.getColor(R.styleable.BoxedVertical_textColor, textColor);

            enabled = attributes.getBoolean(R.styleable.BoxedVertical_enabled, enabled);
            muted = attributes.getBoolean(R.styleable.BoxedVertical_muted, muted);
            meter = attributes.getBoolean(R.styleable.BoxedVertical_meter, meter);
            touchDisabled = attributes.getBoolean(R.styleable.BoxedVertical_touchDisabled, touchDisabled);
            textEnabled = attributes.getBoolean(R.styleable.BoxedVertical_textEnabled, textEnabled);

            textValues = getResources().getStringArray(R.array.fader_db);

            mPoints = mDefaultValue;

            attributes.recycle();
        }

        if (meter) {
            nearClip = 120;
            overUnity = 104;
        }

        // range check
        mPoints = Math.min(mPoints, max);
        mPoints = Math.max(mPoints, min);

        //mProgressPaint = new Paint();
        //mProgressPaint.setColor(progressColor);
        //mProgressPaint.setAntiAlias(true);
        //mProgressPaint.setStyle(Paint.Style.STROKE);

        mTextPaint = new Paint();
        mTextPaint.setColor(textColor);
        mTextPaint.setAntiAlias(true);
        mTextPaint.setStyle(Paint.Style.FILL);
        mTextPaint.setTextSize(textSize);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (firstRun) {
            setValue(mPoints);
            firstRun = false;
        }

        boundingRect.bottom = getHeight();
        boundingRect.right = getWidth();
        clippingPath.reset();
        clippingPath.addRoundRect(boundingRect, cornerRadius, cornerRadius, Path.Direction.CCW);
        canvas.clipPath(clippingPath, Region.Op.INTERSECT);
        drawPaint.setColor(backgroundColor);
        drawPaint.setAntiAlias(true);
        canvas.drawRect(0, 0, getWidth(), getHeight(), drawPaint);

        if (muted) {
            if (mutedGradient == null || lastProgressSweep != mProgressSweep) {
                mutedGradient = new LinearGradient(0, mProgressSweep, 0, getHeight(), ContextCompat.getColor(getContext(), R.color.grey), gradientStart, Shader.TileMode.MIRROR);
            }
            drawPaint.setShader(mutedGradient);
        } else {
            if (nearClip <= mPoints) {
                if (nearClipGradient == null || lastProgressSweep != mProgressSweep) {
                    nearClipGradient = new LinearGradient(0, mProgressSweep, 0, getHeight(), ContextCompat.getColor(getContext(), R.color.red), gradientStart, Shader.TileMode.MIRROR);
                }
                drawPaint.setShader(nearClipGradient);
            } else if (overUnity < mPoints) {
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
        }
        canvas.drawRect(0, mProgressSweep, getWidth(), getHeight(), drawPaint);

        drawPaint.reset();
        drawPaint.setColor(ContextCompat.getColor(getContext(), R.color.grey));
        canvas.drawRect(getWidth() * 0.1f, getHeight() * 0.190f, getWidth() * 0.9f, getHeight() * 0.202f, drawPaint); // 0dB
        canvas.drawRect(getWidth() * 0.175f, getHeight() * 0.387f, getWidth() * 0.825f, getHeight() * 0.395f, drawPaint); // -10dB
        canvas.drawRect(getWidth() * 0.35f, getHeight() * 0.582f, getWidth() * 0.65f, getHeight() * 0.590f, drawPaint); // -20dB
        canvas.drawRect(getWidth() * 0.35f, getHeight() * 0.778f, getWidth() * 0.65f, getHeight() * 0.786f, drawPaint); // -40dB

        if (textEnabled) {
            drawText(canvas, mTextPaint, textValues[mPoints]);
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
        canvas.drawText(text, x, canvas.getHeight() - textBottomPadding, paint);
        canvas.restore();

        canvas.save();
        canvas.clipRect(r_white);
        paint.setColor(textColor);
        canvas.drawText(text, x, canvas.getHeight() - textBottomPadding, paint);
        canvas.restore();

        canvas.restore();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (enabled) {

            this.getParent().requestDisallowInterceptTouchEvent(true);

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    touchAllowed = true;
                    touchStarted_X = (int) event.getAxisValue(MotionEvent.AXIS_X);
                    touchStarted_Y = (int) event.getAxisValue(MotionEvent.AXIS_Y);
                    if (!touchDisabled) updateOnTouch(event);
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
                        touchStarted_Y = max;
                        updateOnTouch(event);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
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
        double mTouch = event.getY();
        int progress = (int) Math.round(mTouch);
        updateProgress(progress);
    }

    private void updateProgress(int progress) {
        float adjustedProgress = progress - progressOffset;
        adjustedProgress = Math.min(adjustedProgress, getHeight());
        adjustedProgress = Math.max(adjustedProgress, 0);

        // convert progress to min-max range
        mPoints = (int) (adjustedProgress * (max - min) / (float) getHeight() + min);
        //reverse value because progress is descending
        mPoints = max + min - mPoints;
        // if value is not max or min, apply step
        if (mPoints != max && mPoints != min) {
            mPoints = mPoints - (mPoints % step) + (min % step);
        }

        mProgressSweep = adjustedProgress;

        if (mOnValuesChangeListener != null) {
            mOnValuesChangeListener.onPointsChanged(this, mPoints);
        }

        invalidate();
    }

    /**
     * Gets a value, converts it to progress for the seekBar and updates it.
     *
     * @param value The value given
     */
    private void updateProgressByValue(int value) {
        mPoints = value;

        mPoints = Math.min(mPoints, max);
        mPoints = Math.max(mPoints, min);

        //convert min-max range to progress
        mProgressSweep = (mPoints - min) * getHeight() / (float) (max - min);
        //reverse value because progress is descending
        mProgressSweep = getHeight() - mProgressSweep;

        invalidate();
    }

    public interface OnValuesChangeListener {
        /**
         * Notification that the point value has changed.
         *
         * @param boxedPoints The SwagPoints view whose value has changed
         * @param points      The current point value.
         */
        void onPointsChanged(BoxedVertical boxedPoints, int points);
    }

    public void setValue(int points) {
        points = Math.min(points, max);
        points = Math.max(points, min);

        updateProgressByValue(points);
        resetGradients();
    }

    public int getValue() {
        return mPoints;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setTouchDisabled(boolean disabled) {
        this.touchDisabled = disabled;
    }

    public int getMax() {
        return max;
    }

    public void setMax(int mMax) {
        if (mMax <= min)
            throw new IllegalArgumentException("Max should not be less than min");
        this.max = mMax;
    }

    public void setCornerRadius(int mRadius) {
        this.cornerRadius = mRadius;
        invalidate();
    }

    public void setGradientStart(int colour) {
        gradientStart = colour;
        resetGradients();
    }

    public void setGradientEnd(int colour) {
        gradientEnd = colour;
        resetGradients();
    }

    public int getCornerRadius() {
        return cornerRadius;
    }

    public int getDefaultValue() {
        return mDefaultValue;
    }

    public void setDefaultValue(int mDefaultValue) {
        if (mDefaultValue > max)
            throw new IllegalArgumentException("Default value should not be bigger than max value.");
        this.mDefaultValue = mDefaultValue;

    }

    public int getStep() {
        return step;
    }

    public void setStep(int step) {
        this.step = step;
    }

    public boolean getMute() {
        return muted;
    }

    public void setMute(boolean state) {
        muted = state;
        resetGradients();
    }

    public void setOnBoxedPointsChangeListener(OnValuesChangeListener onValuesChangeListener) {
        mOnValuesChangeListener = onValuesChangeListener;
    }
}
