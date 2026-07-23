package com.elyot.pclink;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class EnterKeyConcaveDrawable extends Drawable {
    private Paint paint;
    private Path path;
    private float radius;
    private int color;

    public EnterKeyConcaveDrawable(int color, float radius) {
        this.color = color;
        this.radius = radius;
        this.paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        this.paint.setColor(color);
        this.path = new Path();
    }

    public void setColor(int color) {
        this.color = color;
        this.paint.setColor(color);
        invalidateSelf();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        int width = getBounds().width();
        int height = getBounds().height();
        path.reset();
        
        // Base rounded rectangle for the bottom part
        // We want TopLeft to be SHARP (0), TopRight to be SHARP (0)
        // BottomRight rounded, BottomLeft rounded
        path.addRoundRect(new RectF(0, 0, width, height),
                new float[]{0, 0, 0, 0, radius, radius, radius, radius}, Path.Direction.CW);
                
        // Now, add the concave "fillet" in the top-left corner
        // The fillet is a tiny piece of material that extends to the left of the button?
        // No, the bottom button doesn't extend to the left. The bottom button's left edge is at X=0.
        // The concave fillet needs to be drawn OUTSIDE the bottom button?
        // Wait, if it's outside the bottom button, it's clipped!
        // We cannot draw outside the view's bounds.
    }

    @Override
    public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) { paint.setColorFilter(colorFilter); }
    @Override
    public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
