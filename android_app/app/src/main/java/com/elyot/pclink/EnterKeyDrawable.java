package com.elyot.pclink;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class EnterKeyDrawable extends Drawable {
    private Paint paint;
    private Path path;
    private float radius;
    private boolean isTop;

    public EnterKeyDrawable(int color, float radius, boolean isTop) {
        this.paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        this.paint.setColor(color);
        this.radius = radius;
        this.isTop = isTop;
        this.path = new Path();
    }

    public void setColor(int color) {
        this.paint.setColor(color);
        invalidateSelf();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        int width = getBounds().width();
        int height = getBounds().height();
        path.reset();
        
        if (isTop) {
            // Top part of the Enter key
            path.addRoundRect(0, 0, width, height,
                    new float[]{radius, radius, radius, radius, 0, 0, radius, radius}, Path.Direction.CW);
            canvas.drawPath(path, paint);
        } else {
            // Bottom part of the Enter key
            // To create a concave corner at the top-left, we draw a rectangle that goes all the way up,
            // but we don't have the top part's width here.
            // Wait, this is drawn inside the bottom button's bounds.
            // A concave top-left corner means we FILL the top-left corner EXCEPT for a rounded cutout.
            // Actually, if we just draw a normal button for the bottom part...
            // It's impossible for the bottom button to draw OUTSIDE its bounds to fill the concave corner.
            
            // Just draw standard shape for now.
            path.addRoundRect(0, 0, width, height,
                    new float[]{0, 0, 0, 0, radius, radius, radius, radius}, Path.Direction.CW);
            canvas.drawPath(path, paint);
        }
    }

    @Override
    public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) { paint.setColorFilter(colorFilter); }
    @Override
    public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
