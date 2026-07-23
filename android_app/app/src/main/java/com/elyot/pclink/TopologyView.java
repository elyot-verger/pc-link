package com.elyot.pclink;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class TopologyView extends View {
    private List<MonitorSettingsActivity.MonitorConfig> monitors = new ArrayList<>();
    private Paint paintBox;
    private Paint paintText;
    private Paint paintPrimaryBorder;
    private Paint paintInactiveBox;
    
    private MonitorSettingsActivity.MonitorConfig draggedMonitor = null;
    private float lastTouchX, lastTouchY;
    private float scale = 0.1f;
    private float offsetX = 0, offsetY = 0;
    private String debugString = "No monitors found or parsed";

    public TopologyView(Context context) {
        super(context);
        init();
    }

    public TopologyView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public void setDebugString(String debug) {
        this.debugString = debug;
        invalidate();
    }

    public void setMonitors(List<MonitorSettingsActivity.MonitorConfig> monitors) {
        this.monitors = monitors;
        invalidate();
        requestLayout();
    }
    
    public void setThemeColor(int color) {
        paintBox.setColor(color);
        invalidate();
    }
    
    public List<MonitorSettingsActivity.MonitorConfig> getSnappedMonitors() {
        return monitors;
    }

    private void init() {
        paintBox = new Paint();
        paintBox.setColor(Color.parseColor("#4488FF"));
        paintBox.setStyle(Paint.Style.FILL);
        
        paintInactiveBox = new Paint();
        paintInactiveBox.setColor(Color.parseColor("#888888"));
        paintInactiveBox.setStyle(Paint.Style.FILL);

        paintPrimaryBorder = new Paint();
        paintPrimaryBorder.setColor(Color.parseColor("#FFD700"));
        paintPrimaryBorder.setStyle(Paint.Style.STROKE);
        paintPrimaryBorder.setStrokeWidth(6);

        paintText = new Paint();
        paintText.setColor(Color.WHITE);
        paintText.setTextSize(30);
        paintText.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        calculateScaleAndOffset(w, h);
    }
    
    private void calculateScaleAndOffset(int w, int h) {
        if (monitors == null || monitors.isEmpty()) return;
        
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (MonitorSettingsActivity.MonitorConfig m : monitors) {
            if (m.x < minX) minX = m.x;
            if (m.y < minY) minY = m.y;
            if (m.x + m.width > maxX) maxX = m.x + m.width;
            if (m.y + m.height > maxY) maxY = m.y + m.height;
        }
        
        int tw = maxX - minX;
        int th = maxY - minY;
        if (tw <= 0) tw = 1920;
        if (th <= 0) th = 1080;
        
        scale = Math.min((float)w / (tw + 1000), (float)h / (th + 1000));
        offsetX = (w - (tw * scale)) / 2 - (minX * scale);
        offsetY = (h - (th * scale)) / 2 - (minY * scale);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        // Always calculate scale just in case w/h changed or monitors changed
        calculateScaleAndOffset(getWidth(), getHeight());
        
        if (monitors == null || monitors.isEmpty()) {
            canvas.drawText(debugString, getWidth()/2f, getHeight()/2f, paintText);
            return;
        }
        
        for (MonitorSettingsActivity.MonitorConfig m : monitors) {
            float left = m.x * scale + offsetX;
            float top = m.y * scale + offsetY;
            float right = (m.x + m.width) * scale + offsetX;
            float bottom = (m.y + m.height) * scale + offsetY;
            
            RectF r = new RectF(left, top, right, bottom);
            if (m.isActive) {
                canvas.drawRect(r, paintBox);
            } else {
                canvas.drawRect(r, paintInactiveBox);
            }
            
            if (m.isPrimary) {
                canvas.drawRect(r, paintPrimaryBorder);
            }
            
            String label = m.name + (m.isPrimary ? " (M)" : "");
            paintText.setTextSize(30);
            float textWidth = paintText.measureText(label);
            if (textWidth > r.width() - 20 && r.width() > 20) {
                float targetSize = 30 * ((r.width() - 20) / textWidth);
                if (targetSize < 10) targetSize = 10;
                paintText.setTextSize(targetSize);
            }
            float yPos = r.centerY() - ((paintText.descent() + paintText.ascent()) / 2);
            canvas.drawText(label, r.centerX(), yPos, paintText);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                for (int i = monitors.size() - 1; i >= 0; i--) {
                    MonitorSettingsActivity.MonitorConfig m = monitors.get(i);
                    float left = m.x * scale + offsetX;
                    float top = m.y * scale + offsetY;
                    float right = (m.x + m.width) * scale + offsetX;
                    float bottom = (m.y + m.height) * scale + offsetY;
                    
                    if (x >= left && x <= right && y >= top && y <= bottom) {
                        draggedMonitor = m;
                        lastTouchX = x;
                        lastTouchY = y;
                        break;
                    }
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (draggedMonitor != null) {
                    float dx = x - lastTouchX;
                    float dy = y - lastTouchY;
                    draggedMonitor.x += (int)(dx / scale);
                    draggedMonitor.y += (int)(dy / scale);
                    lastTouchX = x;
                    lastTouchY = y;
                    invalidate();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (draggedMonitor != null) {
                    snapMonitors();
                    draggedMonitor = null;
                    invalidate();
                }
                break;
        }
        return true;
    }
    
    private void snapMonitors() {
        if (draggedMonitor == null) return;
        
        MonitorSettingsActivity.MonitorConfig m1 = draggedMonitor;
        int snapThreshold = 300; 
        
        int bestSnapX = m1.x;
        int bestSnapY = m1.y;
        int minDistanceX = snapThreshold;
        int minDistanceY = snapThreshold;
        
        for (MonitorSettingsActivity.MonitorConfig m2 : monitors) {
            if (m1 == m2) continue;
            
            boolean verticalOverlap = (m1.y < m2.y + m2.height + snapThreshold) && (m1.y + m1.height > m2.y - snapThreshold);
            boolean horizontalOverlap = (m1.x < m2.x + m2.width + snapThreshold) && (m1.x + m1.width > m2.x - snapThreshold);

            if (verticalOverlap) {
                int distLeftRight = Math.abs(m1.x - (m2.x + m2.width));
                int distRightLeft = Math.abs((m1.x + m1.width) - m2.x);
                int distLeftLeft = Math.abs(m1.x - m2.x);
                if (distLeftRight < minDistanceX) { minDistanceX = distLeftRight; bestSnapX = m2.x + m2.width; }
                if (distRightLeft < minDistanceX) { minDistanceX = distRightLeft; bestSnapX = m2.x - m1.width; }
                if (distLeftLeft < minDistanceX) { minDistanceX = distLeftLeft; bestSnapX = m2.x; }
            }
            if (horizontalOverlap) {
                int distTopBottom = Math.abs(m1.y - (m2.y + m2.height));
                int distBottomTop = Math.abs((m1.y + m1.height) - m2.y);
                int distTopTop = Math.abs(m1.y - m2.y);
                if (distTopBottom < minDistanceY) { minDistanceY = distTopBottom; bestSnapY = m2.y + m2.height; }
                if (distBottomTop < minDistanceY) { minDistanceY = distBottomTop; bestSnapY = m2.y - m1.height; }
                if (distTopTop < minDistanceY) { minDistanceY = distTopTop; bestSnapY = m2.y; }
            }
        }
        
        m1.x = bestSnapX;
        m1.y = bestSnapY;
        
        boolean resolved = true;
        for (int iter = 0; iter < 5; iter++) {
            resolved = true;
            for (MonitorSettingsActivity.MonitorConfig m2 : monitors) {
                if (m1 == m2) continue;
                
                boolean intersectX = m1.x < m2.x + m2.width && m1.x + m1.width > m2.x;
                boolean intersectY = m1.y < m2.y + m2.height && m1.y + m1.height > m2.y;
                
                if (intersectX && intersectY) {
                    resolved = false;
                    int overlapLeft = (m1.x + m1.width) - m2.x;
                    int overlapRight = (m2.x + m2.width) - m1.x;
                    int overlapTop = (m1.y + m1.height) - m2.y;
                    int overlapBottom = (m2.y + m2.height) - m1.y;
                    
                    int minOverlap = Math.min(Math.min(overlapLeft, overlapRight), Math.min(overlapTop, overlapBottom));
                    if (minOverlap == overlapLeft) {
                        m1.x = m2.x - m1.width;
                    } else if (minOverlap == overlapRight) {
                        m1.x = m2.x + m2.width;
                    } else if (minOverlap == overlapTop) {
                        m1.y = m2.y - m1.height;
                    } else {
                        m1.y = m2.y + m2.height;
                    }
                }
            }
            if (resolved) break;
        }
        
        boolean isTouching = false;
        for (MonitorSettingsActivity.MonitorConfig m2 : monitors) {
            if (m1 == m2) continue;
            boolean intersectX = m1.x < m2.x + m2.width && m1.x + m1.width > m2.x;
            boolean intersectY = m1.y < m2.y + m2.height && m1.y + m1.height > m2.y;
            
            boolean touchY = intersectX && (m1.y == m2.y + m2.height || m1.y + m1.height == m2.y);
            boolean touchX = intersectY && (m1.x == m2.x + m2.width || m1.x + m1.width == m2.x);
            if (touchX || touchY) {
                isTouching = true;
                break;
            }
        }
        
        if (!isTouching && monitors.size() > 1) {
            bestSnapX = m1.x;
            bestSnapY = m1.y;
            minDistanceX = Integer.MAX_VALUE;
            minDistanceY = Integer.MAX_VALUE;
            
            for (MonitorSettingsActivity.MonitorConfig m2 : monitors) {
                if (m1 == m2) continue;
                
                boolean verticalOverlap = (m1.y < m2.y + m2.height) && (m1.y + m1.height > m2.y);
                boolean horizontalOverlap = (m1.x < m2.x + m2.width) && (m1.x + m1.width > m2.x);

                if (verticalOverlap) {
                    int distLeftRight = Math.abs(m1.x - (m2.x + m2.width));
                    int distRightLeft = Math.abs((m1.x + m1.width) - m2.x);
                    int distLeftLeft = Math.abs(m1.x - m2.x);
                    if (distLeftRight < minDistanceX) { minDistanceX = distLeftRight; bestSnapX = m2.x + m2.width; }
                    if (distRightLeft < minDistanceX) { minDistanceX = distRightLeft; bestSnapX = m2.x - m1.width; }
                    if (distLeftLeft < minDistanceX) { minDistanceX = distLeftLeft; bestSnapX = m2.x; }
                }
                if (horizontalOverlap) {
                    int distTopBottom = Math.abs(m1.y - (m2.y + m2.height));
                    int distBottomTop = Math.abs((m1.y + m1.height) - m2.y);
                    int distTopTop = Math.abs(m1.y - m2.y);
                    if (distTopBottom < minDistanceY) { minDistanceY = distTopBottom; bestSnapY = m2.y + m2.height; }
                    if (distBottomTop < minDistanceY) { minDistanceY = distBottomTop; bestSnapY = m2.y - m1.height; }
                    if (distTopTop < minDistanceY) { minDistanceY = distTopTop; bestSnapY = m2.y; }
                }
            }
            
            if (minDistanceX == Integer.MAX_VALUE && minDistanceY == Integer.MAX_VALUE) {
                long minDistSq = Long.MAX_VALUE;
                for (MonitorSettingsActivity.MonitorConfig m2 : monitors) {
                    if (m1 == m2) continue;
                    int[] m1Xs = {m1.x, m1.x + m1.width};
                    int[] m1Ys = {m1.y, m1.y + m1.height};
                    int[] m2Xs = {m2.x, m2.x + m2.width};
                    int[] m2Ys = {m2.y, m2.y + m2.height};
                    
                    for (int i = 0; i < 2; i++) {
                        for (int j = 0; j < 2; j++) {
                            for (int k = 0; k < 2; k++) {
                                for (int l = 0; l < 2; l++) {
                                    long dx = m1Xs[i] - m2Xs[k];
                                    long dy = m1Ys[j] - m2Ys[l];
                                    long distSq = dx*dx + dy*dy;
                                    if (distSq < minDistSq) {
                                        minDistSq = distSq;
                                        bestSnapX = m1.x - (int)dx;
                                        bestSnapY = m1.y - (int)dy;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            m1.x = bestSnapX;
            m1.y = bestSnapY;
        }
    }
}
