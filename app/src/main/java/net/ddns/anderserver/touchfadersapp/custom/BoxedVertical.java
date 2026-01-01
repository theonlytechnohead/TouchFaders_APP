package net.ddns.anderserver.touchfadersapp.custom;

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

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import net.ddns.anderserver.touchfadersapp.R;

/**
 * Created by alpaslanbak on 29/09/2017.
 * Modified by Nick Panagopoulos @npanagop on 12/05/2018.
 * Modified by Craig Anderson @theonlytechnohead on 25/11/2020.
 */
public class BoxedVertical extends View {

    private static final int MAX = 1023;
    private static final int MIN = 0;

    private int min = MIN;
    private int max = MAX;

    private int cornerRadius = 10;
    private float textSize = 26;
    private int textBottomPadding = 20;

    private int value;

    private boolean enabled = true;
    private boolean muted = false;
    private boolean meter = false;
    private boolean textEnabled = true;
    private boolean touchDisabled = true;
    private boolean touchAllowed = true;

    private float fader = 0;
    private float lastFader = 0;

    private final Paint drawPaint = new Paint();
    private final Path clippingPath = new Path();
    private final RectF boundingRect = new RectF();
    private int nearClip = 923;
    private int overUnity = 824;
    private LinearGradient mutedGradient;
    private LinearGradient nearClipGradient;
    private LinearGradient overUnityGradient;
    private LinearGradient normalGradient;
    private Paint textPaint;

    private OnValuesChangeListener mOnValuesChangeListener;
    private int gradientStart;
    private int gradientEnd;
    private int backgroundColor;
    private final Rect textRect = new Rect();
    private boolean firstRun = true;
    private int valueOffset;
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
        float density = getResources().getDisplayMetrics().density;

        // Defaults, may need to link this into theme settings
        int progressColor = ContextCompat.getColor(context, R.color.color_progress);
        backgroundColor = ContextCompat.getColor(context, R.color.color_background);

        int textColor = ContextCompat.getColor(context, R.color.color_text);
        textSize = (int) (textSize * density);
        int defaultValue = 823;

        if (attrs != null) {
            final TypedArray attributes = context.obtainStyledAttributes(attrs,
                    R.styleable.BoxedVertical, 0, 0);

            value = attributes.getInteger(R.styleable.BoxedVertical_points, value);
            max = attributes.getInteger(R.styleable.BoxedVertical_max, max);
            min = attributes.getInteger(R.styleable.BoxedVertical_min, min);
            defaultValue = attributes.getInteger(R.styleable.BoxedVertical_startValue, defaultValue);
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

            if (textEnabled)
                textValues = getResources().getStringArray(R.array.fader_db);

            value = defaultValue;

            attributes.recycle();
        }

        if (meter) {
            nearClip = 120;
            overUnity = 104;
        }

        // range check
        value = Math.min(value, max);
        value = Math.max(value, min);

        textPaint = new Paint();
        textPaint.setColor(textColor);
        textPaint.setAntiAlias(true);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextSize(textSize);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (firstRun) {
            setValue(value);
            firstRun = false;
        }

        boundingRect.bottom = getHeight();
        boundingRect.right = getWidth();
        // round corners
        clippingPath.reset();
        clippingPath.addRoundRect(boundingRect, cornerRadius, cornerRadius, Path.Direction.CCW);
        canvas.clipPath(clippingPath, Region.Op.INTERSECT);
        drawPaint.setColor(backgroundColor);
        drawPaint.setAntiAlias(true);
        canvas.drawRect(0, 0, getWidth(), getHeight(), drawPaint);

        if (muted) {
            if (mutedGradient == null || lastFader != fader) {
                mutedGradient = new LinearGradient(0, fader, 0, getHeight(), ContextCompat.getColor(getContext(), R.color.grey), gradientStart, Shader.TileMode.MIRROR);
            }
            drawPaint.setShader(mutedGradient);
        } else {
            if (nearClip <= value) {
                if (nearClipGradient == null || lastFader != fader) {
                    nearClipGradient = new LinearGradient(0, fader, 0, getHeight(), ContextCompat.getColor(getContext(), R.color.red), gradientStart, Shader.TileMode.MIRROR);
                }
                drawPaint.setShader(nearClipGradient);
            } else if (overUnity < value) {
                if (overUnityGradient == null || lastFader != fader) {
                    overUnityGradient = new LinearGradient(0, fader, 0, getHeight(), ContextCompat.getColor(getContext(), R.color.yellow), gradientStart, Shader.TileMode.MIRROR);
                }
                drawPaint.setShader(overUnityGradient);
            } else {
                if (normalGradient == null || lastFader != fader) {
                    normalGradient = new LinearGradient(0, fader, 0, getHeight(), gradientEnd, gradientStart, Shader.TileMode.MIRROR);
                }
                drawPaint.setShader(normalGradient);
            }
        }
        canvas.drawRect(0, fader, getWidth(), getHeight(), drawPaint);

        drawPaint.reset();
        drawPaint.setColor(ContextCompat.getColor(getContext(), R.color.grey));
        canvas.drawRect(getWidth() * 0.1f, getHeight() * 0.190f, getWidth() * 0.9f, getHeight() * 0.202f, drawPaint);       // 0dB
        canvas.drawRect(getWidth() * 0.175f, getHeight() * 0.387f, getWidth() * 0.825f, getHeight() * 0.395f, drawPaint);   // -10dB
        canvas.drawRect(getWidth() * 0.35f, getHeight() * 0.582f, getWidth() * 0.65f, getHeight() * 0.590f, drawPaint);     // -20dB
        canvas.drawRect(getWidth() * 0.35f, getHeight() * 0.778f, getWidth() * 0.65f, getHeight() * 0.786f, drawPaint);     // -40dB

        if (textEnabled) {
            drawText(canvas, textPaint, textValues[value]);
        }

        lastFader = fader;
    }

    private void drawText(Canvas canvas, Paint paint, String text) {
        canvas.save();

        canvas.getClipBounds(textRect);
        int textWidth = textRect.width();
        paint.setTextAlign(Paint.Align.LEFT);
        paint.getTextBounds(text, 0, text.length(), textRect);
        float x = textWidth / 2f - textRect.width() / 2f - textRect.left;
        int textColor = textPaint.getColor();

        Rect r_white = new Rect((int) x, (int) fader, textWidth, getHeight());
        Rect r_black = new Rect((int) x, 0, (int) (x + textWidth), (int) fader);

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
                case MotionEvent.ACTION_DOWN -> {
                    touchAllowed = true;
                    touchStarted_X = (int) event.getAxisValue(MotionEvent.AXIS_X);
                    touchStarted_Y = (int) event.getAxisValue(MotionEvent.AXIS_Y);
                    if (!touchDisabled) updateOnTouch(event);
                    valueOffset = (int) (Math.round(event.getY()) - fader);
                }
                case MotionEvent.ACTION_MOVE -> {
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
                }
                case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    setPressed(false);
                    this.getParent().requestDisallowInterceptTouchEvent(false);
                }
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
        float adjustedProgress = progress - valueOffset;
        adjustedProgress = Math.min(adjustedProgress, getHeight());
        adjustedProgress = Math.max(adjustedProgress, 0);

        // convert progress to min-max range
        value = (int) (adjustedProgress * (max - min) / (float) getHeight() + min);
        //reverse value because progress is descending
        value = max + min - value;

        fader = adjustedProgress;

        if (mOnValuesChangeListener != null) {
            mOnValuesChangeListener.onPointsChanged(this, value);
        }

        invalidate();
    }

    /**
     * Gets a value, converts it to progress for the seekBar and updates it.
     *
     * @param value The value given
     */
    private void updateProgressByValue(int value) {
        this.value = value;

        this.value = Math.min(this.value, max);
        this.value = Math.max(this.value, min);

        //convert min-max range to progress
        fader = (this.value - min) * getHeight() / (float) (max - min);
        //reverse value because progress is descending
        fader = getHeight() - fader;

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
        return this.value;
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

    public void setGradientStart(int colour) {
        gradientStart = colour;
        resetGradients();
    }

    public void setGradientEnd(int colour) {
        gradientEnd = colour;
        resetGradients();
    }

    public void setMute(boolean state) {
        muted = state;
        resetGradients();
    }

    public void setOnBoxedPointsChangeListener(OnValuesChangeListener onValuesChangeListener) {
        mOnValuesChangeListener = onValuesChangeListener;
    }
}
