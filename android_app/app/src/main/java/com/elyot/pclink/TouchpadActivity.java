package com.elyot.pclink;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.MotionEvent;
import android.content.Intent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TouchpadActivity extends AppCompatActivity {

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private DatagramSocket udpSocket;
    private InetAddress targetAddress;

    private float lastX, lastY;
    private float accDx = 0;
    private float accDy = 0;
    private long lastSendTime = 0;
    private float sensitivity = 2.5f;

    private long actionDownTime = 0;
    private int maxPointersInGesture = 1;
    private boolean isTap = false;
    
    private float accGestureX = 0;
    private float accGestureY = 0;
    private boolean gestureTriggered = false;
    
    private Handler longPressHandler = new Handler(Looper.getMainLooper());
    private Runnable longPressRunnable;
    private Vibrator vibrator;
    
    private ScaleGestureDetector scaleGestureDetector;
    private float accScale = 1.0f;

    private long lastTapUpTime = 0;
    private boolean isDoubleTapDrag = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtils.applyTheme(this, true);
        setContentView(R.layout.activity_touchpad);

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        // Hide action bar if present to maximize space
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Enable fullscreen immersive mode
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN);

        ImageButton btnKeyboard = findViewById(R.id.btnOpenKeyboard);
        if (btnKeyboard != null) {
            btnKeyboard.setOnClickListener(v -> {
                Intent intent = new Intent(TouchpadActivity.this, KeyboardActivity.class);
                startActivity(intent);
            });
        }

        android.content.SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        sensitivity = prefs.getFloat("touchpad_sensitivity", 2.5f);

        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                accScale = 1.0f;
                return super.onScaleBegin(detector);
            }

            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                accScale *= detector.getScaleFactor();
                if (accScale > 1.02f) {
                    sendUdpMessage("Z:1");
                    accScale = 1.0f;
                } else if (accScale < 0.98f) {
                    sendUdpMessage("Z:-1");
                    accScale = 1.0f;
                }
                return true;
            }
        });

        executorService.execute(() -> {
            try {
                udpSocket = new DatagramSocket();
                String baseUrl = NetworkManager.getBaseUrl(this);
                // Extract raw IP
                String ip = baseUrl.replace("http://", "").split(":")[0];
                targetAddress = InetAddress.getByName(ip);
                Log.d("Touchpad", "UDP Target configured: " + ip);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        View touchArea = findViewById(R.id.touchArea);
        touchArea.setOnTouchListener((v, event) -> {
            scaleGestureDetector.onTouchEvent(event);
            int action = event.getActionMasked();
            int pointerCount = event.getPointerCount();

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    long nowDown = System.currentTimeMillis();
                    if (nowDown - lastTapUpTime < 300) {
                        isDoubleTapDrag = true;
                        sendUdpMessage("MD:left");
                    } else {
                        isDoubleTapDrag = false;
                    }
                    lastX = event.getX();
                    lastY = event.getY();
                    actionDownTime = nowDown;
                    maxPointersInGesture = 1;
                    gestureTriggered = false;
                    isTap = true;
                    accGestureX = 0;
                    accGestureY = 0;
                    accDy = 0;
                    break;

                case MotionEvent.ACTION_POINTER_DOWN:
                    lastX = event.getX();
                    lastY = event.getY(); // Reset for scrolling/gestures
                    maxPointersInGesture = Math.max(maxPointersInGesture, pointerCount);
                    
                    if (pointerCount == 2) {
                        longPressHandler.removeCallbacksAndMessages(null);
                        longPressRunnable = () -> {
                            if (!gestureTriggered && Math.abs(accGestureX) < 10 && Math.abs(accDy) < 10) {
                                sendUdpMessage("G:up");
                                gestureTriggered = true;
                                if (vibrator != null) vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
                            }
                        };
                        longPressHandler.postDelayed(longPressRunnable, 600);
                    }
                    break;

                case MotionEvent.ACTION_MOVE:
                    float currentX = event.getX();
                    float currentY = event.getY();
                    float dx = (currentX - lastX) * sensitivity;
                    float dy = (currentY - lastY) * sensitivity;
                    
                    if (Math.abs(currentX - lastX) > 5 || Math.abs(currentY - lastY) > 5) {
                        isTap = false;
                    }

                    maxPointersInGesture = Math.max(maxPointersInGesture, pointerCount);

                    if (pointerCount == 1) {
                        sendUdpMessage("M:" + dx + ":" + dy);
                    } else if (pointerCount == 2) {
                        accDy += dy;
                        accGestureX += dx;
                        long now = System.currentTimeMillis();
                        if (now - lastSendTime >= 16 && !scaleGestureDetector.isInProgress()) {
                            if (Math.abs(accDy) > Math.abs(accGestureX) * 1.5f) {
                                // Vertical movement -> Scroll
                                if (Math.abs(accDy) > 2) {
                                    sendUdpMessage("S:" + (accDy / 5f)); // INVERTED direction
                                    accDy = 0;
                                }
                            } else {
                                // Horizontal movement -> Workspace switch
                                if (!gestureTriggered) {
                                    if (accGestureX < -80) {
                                        sendUdpMessage("G:right"); // INVERTED direction
                                        gestureTriggered = true;
                                        if (vibrator != null) vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE));
                                    } else if (accGestureX > 80) {
                                        sendUdpMessage("G:left"); // INVERTED direction
                                        gestureTriggered = true;
                                        if (vibrator != null) vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE));
                                    }
                                }
                            }
                            lastSendTime = now;
                        }
                    } else if (pointerCount >= 3) {
                        if (!gestureTriggered) {
                            accGestureX += dx;
                            accGestureY += dy;
                            // Threshold for swipe (scaled by sensitivity, so effectively a bit less movement required)
                            if (accGestureY < -80) {
                                sendUdpMessage("G:up");
                                gestureTriggered = true;
                            } else if (accGestureX < -80) {
                                sendUdpMessage("G:left");
                                gestureTriggered = true;
                            } else if (accGestureX > 80) {
                                sendUdpMessage("G:right");
                                gestureTriggered = true;
                            }
                        }
                    }

                    lastX = currentX;
                    lastY = currentY;
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    longPressHandler.removeCallbacksAndMessages(null);
                    long duration = System.currentTimeMillis() - actionDownTime;

                    if (isDoubleTapDrag) {
                        sendUdpMessage("MU:left");
                        isDoubleTapDrag = false;
                        isTap = false; // Prevents triggering standard click
                    } else if (isTap && duration < 250) {
                        if (maxPointersInGesture == 1) {
                            sendUdpMessage("C:left");
                            lastTapUpTime = System.currentTimeMillis();
                        } else if (maxPointersInGesture == 2) {
                            sendUdpMessage("C:right");
                        }
                    }
                    
                    gestureTriggered = false;
                    isTap = false;
                    accGestureX = 0;
                    accGestureY = 0;
                    break;
            }
            return true;
        });

        // Setup physical buttons
        Button btnLeft = findViewById(R.id.btnLeftClick);
        Button btnMiddle = findViewById(R.id.btnMiddleClick);
        Button btnRight = findViewById(R.id.btnRightClick);

        setupPhysicalButton(btnLeft, "left");
        setupPhysicalButton(btnMiddle, "middle");
        setupPhysicalButton(btnRight, "right");
    }

    private void setupPhysicalButton(Button button, String btnName) {
        button.setOnTouchListener((v, event) -> {
            int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN) {
                sendUdpMessage("MD:" + btnName);
                v.setPressed(true);
                return true;
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                sendUdpMessage("MU:" + btnName);
                v.setPressed(false);
                return true;
            }
            return false;
        });
    }

    private void sendUdpMessage(String message) {
        if (targetAddress == null || udpSocket == null) return;
        executorService.execute(() -> {
            try {
                byte[] buffer = message.getBytes();
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, targetAddress, 5001);
                udpSocket.send(packet);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (udpSocket != null) {
            udpSocket.close();
        }
        executorService.shutdown();
    }
}
