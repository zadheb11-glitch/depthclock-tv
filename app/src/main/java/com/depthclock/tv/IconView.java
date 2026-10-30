package com.depthclock.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Small vector icons drawn in code, so they never depend on image files. */
final class IconView extends View {
    static final int WIFI = 0;
    static final int WALLPAPER = 1;
    static final int SETTINGS = 2;

    private final int type;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private int color = Color.WHITE;

    IconView(Context context, int type) {
        super(context);
        this.type = type;
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    void setColor(int color) {
        this.color = color;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        float h = getHeight();
        float s = Math.min(w, h);
        float u = s / 24f;
        c.save();
        c.translate((w - s) / 2f, (h - s) / 2f);
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.2f * u);

        if (type == WIFI) {
            float cx = 12 * u;
            float cy = 19 * u;
            float[] radii = {5f, 9f, 13f};
            for (float r : radii) {
                rect.set(cx - r * u, cy - r * u, cx + r * u, cy + r * u);
                c.drawArc(rect, 225f, 90f, false, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            c.drawCircle(cx, 19.2f * u, 1.7f * u, paint);
        } else if (type == WALLPAPER) {
            rect.set(3f * u, 4.5f * u, 21f * u, 19.5f * u);
            c.drawRoundRect(rect, 3f * u, 3f * u, paint);
            path.reset();
            path.moveTo(3.8f * u, 17f * u);
            path.lineTo(9f * u, 12f * u);
            path.lineTo(13f * u, 15.8f * u);
            path.lineTo(16f * u, 13f * u);
            path.lineTo(20.2f * u, 17f * u);
            c.drawPath(path, paint);
            paint.setStyle(Paint.Style.FILL);
            c.drawCircle(15.5f * u, 8.6f * u, 1.9f * u, paint);
        } else {
            float cx = 12 * u;
            float cy = 12 * u;
            c.drawCircle(cx, cy, 6.2f * u, paint);
            c.drawCircle(cx, cy, 2.6f * u, paint);
            paint.setStrokeWidth(2.8f * u);
            for (int k = 0; k < 8; k++) {
                double a = k * Math.PI / 4.0;
                float cos = (float) Math.cos(a);
                float sin = (float) Math.sin(a);
                c.drawLine(cx + cos * 7.6f * u, cy + sin * 7.6f * u,
                        cx + cos * 10.2f * u, cy + sin * 10.2f * u, paint);
            }
        }
        c.restore();
    }
}
