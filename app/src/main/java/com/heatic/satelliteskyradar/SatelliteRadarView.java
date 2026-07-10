package com.heatic.satelliteskyradar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class SatelliteRadarView extends View {

    public interface OnSatelliteClickListener {
        void onSatelliteClick(SatelliteInfo satellite);
    }

    private Paint gridPaint, textPaint, satellitePaint, usedSatellitePaint;
    private Paint smallTextPaint, nameBackgroundPaint, headingPaint;
    private List<SatelliteInfo> satellites = new ArrayList<>();
    private float centerX, centerY, radius;
    private float headingDeg = 0f;
    private final float[] elevationSteps = {0, 30, 60, 90};
    private OnSatelliteClickListener clickListener;

    public SatelliteRadarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(Color.argb(100, 0, 255, 0));
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(dpToPx(1.5f));

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(dpToPx(14));
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setTextAlign(Paint.Align.CENTER);

        smallTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        smallTextPaint.setColor(Color.argb(180, 0, 255, 0));
        smallTextPaint.setTextSize(dpToPx(10));
        smallTextPaint.setTextAlign(Paint.Align.CENTER);

        satellitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        satellitePaint.setColor(Color.argb(255, 0, 255, 0));
        satellitePaint.setStyle(Paint.Style.FILL);

        usedSatellitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        usedSatellitePaint.setColor(Color.argb(255, 0, 200, 255));
        usedSatellitePaint.setStyle(Paint.Style.FILL);

        nameBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        nameBackgroundPaint.setColor(Color.argb(180, 0, 0, 0));
        nameBackgroundPaint.setStyle(Paint.Style.FILL);

        headingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        headingPaint.setColor(Color.argb(255, 0, 150, 255));
        headingPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        headingPaint.setStrokeWidth(dpToPx(2));
    }

    public void setOnSatelliteClickListener(OnSatelliteClickListener listener) {
        this.clickListener = listener;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2f;
        centerY = h / 2f;
        radius = Math.min(w, h) / 2f - dpToPx(40);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (radius <= 0) return;

        canvas.drawColor(Color.BLACK);

        // Elevation circles
        for (float step : elevationSteps) {
            float r = (90f - step) / 90f * radius;
            canvas.drawCircle(centerX, centerY, r, gridPaint);
            if (step > 0) {
                canvas.drawText((int) step + "°", centerX, centerY - r - dpToPx(6), smallTextPaint);
            }
        }

        // Cardinal directions
        String[] dirs = {"N", "E", "S", "W"};
        float[] azAngles = {0, 90, 180, 270};
        for (int i = 0; i < azAngles.length; i++) {
            float rad = (float) Math.toRadians(azAngles[i] - 90);
            float ex = centerX + radius * (float) Math.cos(rad);
            float ey = centerY + radius * (float) Math.sin(rad);
            canvas.drawLine(centerX, centerY, ex, ey, gridPaint);
            float lx = centerX + (radius + dpToPx(16)) * (float) Math.cos(rad);
            float ly = centerY + (radius + dpToPx(16)) * (float) Math.sin(rad);
            canvas.drawText(dirs[i], lx, ly + dpToPx(4), textPaint);
        }

        // Draw satellites
        for (SatelliteInfo sat : satellites) {
            float elev = sat.elevation;
            float azim = sat.azimuth;
            if (elev < 0) elev = 0;
            if (elev > 90) elev = 90;
            float r = (90f - elev) / 90f * radius;
            float azimRad = (float) Math.toRadians(azim - 90);
            float x = centerX + r * (float) Math.cos(azimRad);
            float y = centerY + r * (float) Math.sin(azimRad);

            Paint dotPaint = sat.usedInFix ? usedSatellitePaint : satellitePaint;
            float dotRadius = dpToPx(7);
            canvas.drawCircle(x, y, dotRadius, dotPaint);

            String name = sat.getName();
            String speedStr = (Math.abs(sat.rangeRateMps) > 0.1f) ?
                    String.format(" %.0f m/s", sat.rangeRateMps) : " ?";
            String label = name + speedStr;

            Rect bounds = new Rect();
            smallTextPaint.getTextBounds(name, 0, name.length(), bounds);
            float nameX = x + dotRadius + dpToPx(4);
            float nameY = y + bounds.height() / 2f;
            float bgPad = dpToPx(2);
            canvas.drawRect(nameX - bgPad, nameY - bounds.height() / 2f - bgPad,
                    nameX + smallTextPaint.measureText(label) + bgPad,
                    nameY + bounds.height() / 2f + bgPad, nameBackgroundPaint);
            smallTextPaint.setColor(Color.WHITE);
            canvas.drawText(label, nameX, nameY, smallTextPaint);
        }

        // Heading arrow
        drawHeadingArrow(canvas);

        // Centre dot
        Paint centerDot = new Paint(Paint.ANTI_ALIAS_FLAG);
        centerDot.setColor(Color.WHITE);
        canvas.drawCircle(centerX, centerY, dpToPx(3), centerDot);
    }

    private void drawHeadingArrow(Canvas canvas) {
        float rad = (float) Math.toRadians(headingDeg - 90);
        float arrowLength = radius * 0.85f;
        float tipX = centerX + arrowLength * (float) Math.cos(rad);
        float tipY = centerY + arrowLength * (float) Math.sin(rad);

        headingPaint.setStyle(Paint.Style.STROKE);
        headingPaint.setStrokeWidth(dpToPx(3));
        canvas.drawLine(centerX, centerY, tipX, tipY, headingPaint);

        headingPaint.setStyle(Paint.Style.FILL);
        float arrowHeadSize = dpToPx(12);
        Path path = new Path();
        path.moveTo(tipX, tipY);
        float leftX = centerX + (arrowLength - arrowHeadSize) * (float) Math.cos(rad - 0.3f);
        float leftY = centerY + (arrowLength - arrowHeadSize) * (float) Math.sin(rad - 0.3f);
        float rightX = centerX + (arrowLength - arrowHeadSize) * (float) Math.cos(rad + 0.3f);
        float rightY = centerY + (arrowLength - arrowHeadSize) * (float) Math.sin(rad + 0.3f);
        path.lineTo(leftX, leftY);
        path.lineTo(rightX, rightY);
        path.close();
        canvas.drawPath(path, headingPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN && clickListener != null) {
            float touchX = event.getX();
            float touchY = event.getY();
            float touchThreshold = dpToPx(28);

            SatelliteInfo closest = null;
            float closestDist = Float.MAX_VALUE;

            for (SatelliteInfo sat : satellites) {
                float elev = sat.elevation;
                float azim = sat.azimuth;
                if (elev < 0) elev = 0;
                if (elev > 90) elev = 90;
                float r = (90f - elev) / 90f * radius;
                float azimRad = (float) Math.toRadians(azim - 90);
                float satX = centerX + r * (float) Math.cos(azimRad);
                float satY = centerY + r * (float) Math.sin(azimRad);

                float dist = (float) Math.hypot(touchX - satX, touchY - satY);
                if (dist < touchThreshold && dist < closestDist) {
                    closest = sat;
                    closestDist = dist;
                }
            }

            if (closest != null) {
                clickListener.onSatelliteClick(closest);
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    public void setSatellites(List<SatelliteInfo> satellites) {
        this.satellites = new ArrayList<>(satellites);
        invalidate();
    }

    public void setHeading(float headingDeg) {
        this.headingDeg = headingDeg;
        invalidate();
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}