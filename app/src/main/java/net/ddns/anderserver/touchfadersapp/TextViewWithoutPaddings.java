package net.ddns.anderserver.touchfadersapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;

import androidx.annotation.NonNull;

public class TextViewWithoutPaddings extends androidx.appcompat.widget.AppCompatTextView {

    private final Paint paint = new Paint();

    private final Rect bounds = new Rect();

    public TextViewWithoutPaddings(Context context) {
        super(context);
    }

    public TextViewWithoutPaddings(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TextViewWithoutPaddings(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        final String text = calculateTextParams();

        final int left = bounds.left;
        final int bottom = bounds.bottom;
        bounds.offset(-bounds.left, -bounds.top);
        paint.setAntiAlias(true);
        paint.setColor(getCurrentTextColor());
        canvas.drawText(text, -left, bounds.bottom - bottom, paint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        calculateTextParams();
        setMeasuredDimension(bounds.width() + 1, -bounds.top + 1);
    }

    private String calculateTextParams() {
        final String text = getText().toString();
        final int textLength = text.length();
        paint.setTextSize(getTextSize());
        paint.getTextBounds(text, 0, textLength, bounds);
        if (textLength == 0) {
            bounds.right = bounds.left;
        }
        return text;
    }
}
