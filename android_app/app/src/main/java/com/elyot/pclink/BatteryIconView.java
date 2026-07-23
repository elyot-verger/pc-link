package com.elyot.pclink;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

public class BatteryIconView extends View {

    private int batteryLevel = 50; // 0-100
    private boolean isCharging = false;
    
    private Paint outlinePaint;
    private Paint fillPaint;
    private Paint chargingPaint;
    private Path boltPath;
    
    private int baseColor = Color.WHITE;
    
    public BatteryIconView(Context context) {
        super(context);
        init();
    }
    
    public BatteryIconView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public BatteryIconView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(dpToPx(2));
        
        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);
        
        chargingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        chargingPaint.setColor(Color.parseColor("#4CAF50")); // Green for charging
        chargingPaint.setStyle(Paint.Style.FILL);
        
        boltPath = new Path();
        updateColors();
    }
    
    public void setBaseColor(int color) {
        this.baseColor = color;
        updateColors();
    }
    
    private void updateColors() {
        outlinePaint.setColor(baseColor);
        if (batteryLevel <= 20 && !isCharging) {
            fillPaint.setColor(Color.parseColor("#F44336")); // Red for low battery
        } else {
            fillPaint.setColor(baseColor);
        }
        invalidate();
    }
    
    public void setBatteryLevel(int level, boolean charging) {
        this.batteryLevel = Math.max(0, Math.min(100, level));
        this.isCharging = charging;
        updateColors();
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        int width = getWidth();
        int height = getHeight();
        
        float p = dpToPx(2);
        
        float bodyWidth = width - p * 3 - dpToPx(3); // Leave room for cap
        float bodyHeight = height - p * 2;
        float bodyLeft = p;
        float bodyTop = p;
        float bodyRight = bodyLeft + bodyWidth;
        float bodyBottom = bodyTop + bodyHeight;
        float cornerRadius = dpToPx(2);
        
        float capWidth = dpToPx(3);
        float capHeight = bodyHeight * 0.4f;
        float capLeft = bodyRight;
        float capTop = height / 2f - capHeight / 2f;
        float capRight = capLeft + capWidth;
        float capBottom = capTop + capHeight;
        
        // Draw Outline
        canvas.drawRoundRect(bodyLeft, bodyTop, bodyRight, bodyBottom, cornerRadius, cornerRadius, outlinePaint);
        // Draw Cap
        Paint capPaint = new Paint(fillPaint);
        capPaint.setColor(baseColor);
        canvas.drawRoundRect(capLeft, capTop, capRight, capBottom, dpToPx(1), dpToPx(1), capPaint);
        
        // Draw Fill
        if (!isCharging) {
            float fillPadding = dpToPx(1.5f);
            float maxFillWidth = bodyWidth - (2 * fillPadding);
            float actualFillWidth = maxFillWidth * (batteryLevel / 100f);
            
            if (actualFillWidth > 0) {
                canvas.drawRect(bodyLeft + fillPadding, bodyTop + fillPadding, 
                              bodyLeft + fillPadding + actualFillWidth, bodyBottom - fillPadding, fillPaint);
            }
        } else {
            // Draw Charging Fill semi-transparent
            fillPaint.setAlpha(80);
            float fillPadding = dpToPx(1.5f);
            float maxFillWidth = bodyWidth - (2 * fillPadding);
            float actualFillWidth = maxFillWidth * (batteryLevel / 100f);
            
            if (actualFillWidth > 0) {
                canvas.drawRect(bodyLeft + fillPadding, bodyTop + fillPadding, 
                              bodyLeft + fillPadding + actualFillWidth, bodyBottom - fillPadding, fillPaint);
            }
            fillPaint.setAlpha(255);
            
            // Draw Charging Bolt centered
            float cx = bodyLeft + bodyWidth / 2f;
            float cy = height / 2f;
            
            float boltW = bodyWidth * 0.4f;
            float boltH = bodyHeight * 0.8f;
            
            boltPath.reset();
            boltPath.moveTo(cx + boltW * 0.1f, cy - boltH * 0.4f);
            boltPath.lineTo(cx - boltW * 0.3f, cy + boltH * 0.1f);
            boltPath.lineTo(cx, cy + boltH * 0.1f);
            boltPath.lineTo(cx - boltW * 0.1f, cy + boltH * 0.5f);
            boltPath.lineTo(cx + boltW * 0.3f, cy);
            boltPath.lineTo(cx, cy);
            boltPath.close();
            
            canvas.drawPath(boltPath, chargingPaint);
        }
    }
    
    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}
