package com.example.expense_tracker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

public class DonutChartView extends View {
    public static class Slice {
        public float percentage;
        public int color;
        public Slice(float percentage, int color) {
            this.percentage = percentage;
            this.color = color;
        }
    }

    private final List<Slice> slices = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rectF = new RectF();
    private float strokeWidth = 30f;

    public DonutChartView(Context context) {
        super(context);
        init();
    }

    public DonutChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        
        float density = getResources().getDisplayMetrics().density;
        strokeWidth = 14 * density; // 14dp stroke width
        paint.setStrokeWidth(strokeWidth);
    }

    public void setSlices(List<Slice> newSlices) {
        slices.clear();
        if (newSlices != null) {
            slices.addAll(newSlices);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float padding = strokeWidth / 2f + 4;
        rectF.set(padding, padding, getWidth() - padding, getHeight() - padding);

        // Background circle track
        paint.setColor(0xFFF1F3F9);
        canvas.drawArc(rectF, 0, 360, false, paint);

        if (slices.isEmpty()) {
            return;
        }

        float startAngle = -90f;
        for (Slice slice : slices) {
            float sweepAngle = (slice.percentage / 100f) * 360f;
            if (sweepAngle > 0) {
                paint.setColor(slice.color);
                canvas.drawArc(rectF, startAngle, sweepAngle, false, paint);
                startAngle += sweepAngle;
            }
        }
    }
}
