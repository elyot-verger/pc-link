package com.elyot.pclink;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class KeyboardActivity extends AppCompatActivity {

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private int themeColor;
    
    private String currentLayout = "NUMPAD";
    private LinearLayout mainContainer;
    
    
    private boolean isCapsActive = false;
    private long lastCapsToggleTime = 0;
    private android.os.Handler capslockHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable capslockRunnable = new Runnable() {
        @Override
        public void run() {
            checkCapsLockState();
        }
    };
    
    private void checkCapsLockState() {
        executorService.execute(() -> {
            try {
                String baseUrl = NetworkManager.getBaseUrl(KeyboardActivity.this);
                java.net.URL url = new java.net.URL(baseUrl + "/capslock_state");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(500);
                conn.setReadTimeout(500);
                if (conn.getResponseCode() == 200) {
                    java.io.InputStream is = conn.getInputStream();
                    java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
                    String res = s.hasNext() ? s.next() : "";
                    is.close();
                    org.json.JSONObject json = new org.json.JSONObject(res);
                    boolean newCaps = json.optBoolean("capslock", false);
                    if (newCaps != isCapsActive && (System.currentTimeMillis() - lastCapsToggleTime > 1500)) {
                        isCapsActive = newCaps;
                        runOnUiThread(() -> {
                            if (currentLayout.equals("QWERTY") || currentLayout.equals("AZERTY")) {
                                showKeyboard(currentLayout);
                            }
                        });
                    }
                }
            } catch (Exception e) {}
            // Schedule the next check only after this one completes
            capslockHandler.postDelayed(capslockRunnable, 1500);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        capslockHandler.post(capslockRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        capslockHandler.removeCallbacks(capslockRunnable);
    }

    private List<String> activeModifiers = new ArrayList<>();
    

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtils.applyTheme(this, true);
        
        String themeColorHex = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
                .getString("pref_theme_color", "#C01C28");
        try {
            themeColor = Color.parseColor(themeColorHex);
        } catch (IllegalArgumentException e) {
            themeColor = Color.parseColor("#C01C28");
        }

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                java.io.File file = new java.io.File(getExternalFilesDir(null), "crash.txt");
                java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(file));
                throwable.printStackTrace(pw);
                pw.flush();
                pw.close();
            } catch (Exception e) {}
            System.exit(1);
        });

        initMainContainer();
        sendLayout("azerty");
        showKeyboard("AZERTY");
        setContentView(mainContainer);
        
        enableImmersiveMode();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enableImmersiveMode();
        }
    }

    private void enableImmersiveMode() {
        androidx.core.view.WindowInsetsControllerCompat windowInsetsController =
                androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        windowInsetsController.setSystemBarsBehavior(
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars());
    }

    private int themeAccentColor = Color.parseColor("#03DAC5");

    private void initMainContainer() {
        if (mainContainer != null) return;
        
        android.content.SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, android.content.Context.MODE_PRIVATE);
        try {
            themeAccentColor = Color.parseColor(prefs.getString("pref_theme_color", "#03DAC5"));
        } catch (Exception e) {}

        mainContainer = new LinearLayout(this);
        mainContainer.setOrientation(LinearLayout.VERTICAL);
        mainContainer.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
        mainContainer.setBackgroundColor(Color.parseColor("#111111"));
        mainContainer.setClipChildren(false);
        
        android.util.TypedValue typedValue = new android.util.TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.colorBackground, typedValue, true)) {
            mainContainer.setBackgroundColor(typedValue.data);
        } else {
            mainContainer.setBackgroundColor(Color.parseColor("#121212"));
        }
        
        mainContainer.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void setupNumpad() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        initMainContainer();
        currentLayout = "NUMPAD";
        mainContainer.removeAllViews();
        clearModifiers();

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        grid.setRowCount(5);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        
        // Add standard app title bar
        LinearLayout titleBar = new LinearLayout(this);
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleBarParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(48));
        titleBarParams.setMargins(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(8));
        titleBar.setLayoutParams(titleBarParams);

        android.widget.TextView titleView = new android.widget.TextView(this);
        titleView.setText("Virtual Keyboard");
        titleView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20f);
        titleView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        // Remove setTextColor so it defaults to the theme's text color Primary
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleView.setLayoutParams(titleParams);
        titleBar.addView(titleView);

        android.widget.ImageView settingsBtn = new android.widget.ImageView(this);
        settingsBtn.setImageResource(R.drawable.ic_settings);
        settingsBtn.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
        
        android.util.TypedValue tv = new android.util.TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.textColorPrimary, tv, true)) {
            settingsBtn.setColorFilter(tv.data);
        }

        settingsBtn.setOnClickListener(v -> {
            startActivity(new android.content.Intent(KeyboardActivity.this, KeyboardSettingsActivity.class));
        });
        titleBar.addView(settingsBtn);

        mainContainer.addView(titleBar);

        android.view.View separator = new android.view.View(this);
        separator.setBackgroundColor(Color.parseColor("#33888888"));
        LinearLayout.LayoutParams sepParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1));
        sepParams.setMargins(0, 0, 0, dpToPx(16));
        separator.setLayoutParams(sepParams);
        mainContainer.addView(separator);

        // Center wrapper for the Numpad grid
        LinearLayout centerContainer = new LinearLayout(this);
        centerContainer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        centerContainer.setGravity(Gravity.CENTER);
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        grid.setLayoutParams(params);

        String[][] padLabels = {
                {"FR", "/", "*", "-"},
                {"1", "2", "3", "+"},
                {"4", "5", "6", "↵"},
                {"7", "8", "9", "↵"},
                {"0", "0", ".", "⌫"}
        };
        String[][] padCodes = {
                {"US", "KPDIV", "KPMULT", "KPMINUS"},
                {"KP1", "KP2", "KP3", "KPPLUS"},
                {"KP4", "KP5", "KP6", "KPENTER"},
                {"KP7", "KP8", "KP9", "KPENTER"},
                {"KP0", "KP0", "KPDOT", "BACKSPACE"}
        };

        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 4; c++) {
                if (r == 3 && c == 3) continue; 
                if (r == 4 && c == 1) continue; 

                String label = padLabels[r][c];
                String code = padCodes[r][c];
                Button b = new Button(this);
                b.setText(label);
                b.setTextSize(24f);
                b.setPadding(0, 0, 0, 0);
                
                android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                gd.setColor(Color.parseColor("#444444"));
                gd.setCornerRadius(dpToPx(6));
                b.setBackground(gd);
                
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                int spanW = label.equals("0") ? 2 : 1;
                int spanH = label.equals("↵") ? 2 : 1;
                lp.rowSpec = GridLayout.spec(r, spanH);
                lp.columnSpec = GridLayout.spec(c, spanW);
                lp.width = dpToPx(90) * spanW + (spanW - 1) * dpToPx(2);
                lp.height = dpToPx(90) * spanH + (spanH - 1) * dpToPx(2);
                lp.setMargins(dpToPx(1), dpToPx(1), dpToPx(1), dpToPx(1)); 
                b.setOnTouchListener((v, event) -> {
                    if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                        gd.setColor(Color.parseColor("#777777"));
                        v.setBackground(gd);
                        if (code.equals("US")) {
                            cycleLayout();
                        } else {
                            handleKey(label, code);
                        }
                    } else if (event.getAction() == android.view.MotionEvent.ACTION_UP || event.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                        gd.setColor(Color.parseColor("#444444"));
                        v.setBackground(gd);
                    }
                    return true;
                });
                b.setLayoutParams(lp);
                grid.addView(b);
            }
        }
        centerContainer.addView(grid);
        mainContainer.addView(centerContainer);
        setContentView(mainContainer);
    }

    private boolean isFnActive = false;

    
    private String getDynamicLabel(String label, String code, boolean isShift, boolean isAltGr, boolean isCaps, String layout) {
        
        if (layout.equals("AZERTY")) {
            if (isAltGr) {
                switch(label) {
                    case "é": return "~";
                    case "\"": return "#";
                    case "'": return "{";
                    case "(": return "[";
                    case "-": return "|";
                    case "è": return "`";
                    case "_": return "\\";
                    case "ç": return "^";
                    case "à": return "@";
                    case ")": return "]";
                    case "=": return "}";
                    case "E": case "e": return "€";
                    case "^": return "¨";
                    case "$": return "¤";
                    case "ù": return "^";
                    case "*": return "`";
                }
            }
            if (isShift) {
                switch(label) {
                    case "&": return "1";
                    case "é": return "2";
                    case "\"": return "3";
                    case "'": return "4";
                    case "(": return "5";
                    case "-": return "6";
                    case "è": return "7";
                    case "_": return "8";
                    case "ç": return "9";
                    case "à": return "0";
                    case ")": return "°";
                    case "=": return "+";
                    case ",": return "?";
                    case ";": return ".";
                    case ":": return "/";
                    case "!": return "§";
                    case "<": return ">";
                    case "^": return "¨";
                    case "$": return "£";
                    case "*": return "µ";
                    case "ù": return "%";
                }
            }
        } else if (layout.equals("QWERTY")) {
            if (isShift) {
                switch(label) {
                    case "`": return "~";
                    case "1": return "!";
                    case "2": return "@";
                    case "3": return "#";
                    case "4": return "$";
                    case "5": return "%";
                    case "6": return "^";
                    case "7": return "&";
                    case "8": return "*";
                    case "9": return "(";
                    case "0": return ")";
                    case "-": return "_";
                    case "=": return "+";
                    case "[": return "{";
                    case "]": return "}";
                    case "\\": return "|";
                    case ";": return ":";
                    case "'": return "\"";
                    case ",": return "<";
                    case ".": return ">";
                    case "/": return "?";
                }
            }
        }
        
        if (label.length() == 1 && Character.isLetter(label.charAt(0))) {
            boolean upper = isShift ^ isCaps;
            return upper ? label.toUpperCase() : label.toLowerCase();
        }
        return label;
    }

    private class EnterFilletDrawable extends android.graphics.drawable.Drawable {
        private android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private android.graphics.Path path = new android.graphics.Path();
        private int radius;
        private float dx = 0, dy = 0;

        public EnterFilletDrawable(int radius) {
            this.radius = radius;
            p.setColor(android.graphics.Color.parseColor("#444444"));
            path.moveTo(0, 0);
            path.lineTo(-radius, 0);
            path.arcTo(new android.graphics.RectF(-radius * 2, 0, 0, radius * 2), -90, 90, false);
            path.close();
        }

        public void setPosition(float x, float y) {
            this.dx = x;
            this.dy = y;
            invalidateSelf();
        }

        public void setColor(int color) {
            p.setColor(color);
            invalidateSelf();
        }

        @Override
        public void draw(@androidx.annotation.NonNull android.graphics.Canvas canvas) {
            canvas.save();
            canvas.translate(dx, dy);
            canvas.drawPath(path, p);
            canvas.restore();
        }

        @Override
        public void setAlpha(int alpha) { p.setAlpha(alpha); }

        @Override
        public void setColorFilter(android.graphics.ColorFilter cf) { p.setColorFilter(cf); }

        @Override
        public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    private void cycleLayout() {
        activeModifiers.clear();
        switch (currentLayout) {
            case "NUMPAD": 
                sendLayout("azerty");
                showKeyboard("AZERTY"); 
                break;
            case "AZERTY": 
                sendLayout("qwerty");
                showKeyboard("QWERTY"); 
                break;
            case "QWERTY": showEmoji(); break;
            case "EMOJI": setupNumpad(); break;
        }
    }

    private void sendLayout(String layout) {
        executorService.execute(() -> {
            boolean success = false;
            int retries = 0;
            while (retries < 2 && !success) {
                try {
                    String baseUrl = NetworkManager.getBaseUrl(this);
                    URL url = new URL(baseUrl + "/keyboard/layout");
                    JSONObject json = new JSONObject();
                    json.put("layout", layout);
                    
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(3000);
                    conn.setReadTimeout(3000);
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);
                    
                    OutputStream os = conn.getOutputStream();
                    os.write(json.toString().getBytes());
                    os.flush();
                    os.close();
                    
                    if (conn.getResponseCode() == 200) {
                        success = true;
                    }
                    conn.disconnect();
                } catch (Exception e) {
                    Log.e("Keyboard", "Error sending layout, retrying...", e);
                    NetworkManager.reportNetworkError();
                }
                retries++;
            }
        });
    }

    private android.widget.Button topEnterBtn;
    private android.graphics.drawable.GradientDrawable topEnterGd;
    private Button bottomEnterBtn;
    private android.graphics.drawable.GradientDrawable bottomEnterGd;
    private EnterFilletDrawable enterFillet;

    private void showKeyboard(String type) {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        currentLayout = type;
        if (mainContainer == null) initMainContainer();
        mainContainer.removeAllViews();
        topEnterBtn = null;
        topEnterGd = null;
        bottomEnterBtn = null;
        bottomEnterGd = null;
        
        String[] l1, l2, l3, l4, l5;
        String[] c1, c2, c3, c4, c5;

        if (isFnActive) {
            c1 = new String[]{"OPEN_TOUCHPAD", "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12", "DELETE"};
            l1 = new String[]{"TP", "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12", "Suppr"};
        } else {
            c1 = new String[]{"ESC", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "MINUS", "EQUAL", "BACKSPACE"};
            if (type.equals("AZERTY")) {
                l1 = new String[]{"Esc", "&", "é", "\"", "'", "(", "-", "è", "_", "ç", "à", ")", "=", "⌫"};
            } else {
                l1 = new String[]{"Esc", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "-", "=", "⌫"};
            }
        }

        c2 = new String[]{"TAB", "Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P", "LEFTBRACE", "RIGHTBRACE", "ENTER"};
        c3 = new String[]{"CAPSLOCK", "A", "S", "D", "F", "G", "H", "J", "K", "L", "SEMICOLON", "APOSTROPHE", "BACKSLASH", "ENTER"};
        c5 = new String[]{"CTRL", "FN", "LWIN", "ALT", "SPACE", "ALTGR", "RWIN", "LEFT", "UP_DOWN", "RIGHT"};

        if (type.equals("AZERTY")) {
            c4 = new String[]{"SHIFT", "102ND", "Z", "X", "C", "V", "B", "N", "M", "COMMA", "DOT", "SLASH", "SHIFT"};
            l2 = new String[]{"↹", "A", "Z", "E", "R", "T", "Y", "U", "I", "O", "P", "^", "$", "↵"};
            l3 = new String[]{"⇪", "Q", "S", "D", "F", "G", "H", "J", "K", "L", "M", "ù", "*", "↵"};
            l4 = new String[]{"⇧", "<", "W", "X", "C", "V", "B", "N", ",", ";", ":", "!", "⇧"};
        } else {
            c4 = new String[]{"SHIFT", "BACKSLASH", "Z", "X", "C", "V", "B", "N", "M", "COMMA", "DOT", "SLASH", "SHIFT"};
            l2 = new String[]{"↹", "Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P", "[", "]", "↵"};
            l3 = new String[]{"⇪", "A", "S", "D", "F", "G", "H", "J", "K", "L", ";", "'", "\\", "↵"};
            l4 = new String[]{"⇧", "\\", "Z", "X", "C", "V", "B", "N", "M", ",", ".", "/", "⇧"};
        }
        l5 = new String[]{"Ctrl", "Fn", "Win", "Alt", "Space", "AltGr", type.equals("AZERTY") ? "EN" : "☺", "◀", "", "▶"};

        mainContainer.addView(buildRow(0, l1, c1));
        mainContainer.addView(buildRow(1, l2, c2));
        mainContainer.addView(buildRow(2, l3, c3));
        mainContainer.addView(buildRow(3, l4, c4));
        mainContainer.addView(buildRow(4, l5, c5));
        
        mainContainer.requestLayout();
        setContentView(mainContainer);

        mainContainer.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (bottomEnterBtn != null && topEnterBtn != null && mainContainer != null) {
                    int[] mainLoc = new int[2];
                    mainContainer.getLocationInWindow(mainLoc);
                    int[] btnLoc = new int[2];
                    bottomEnterBtn.getLocationInWindow(btnLoc);
                    float x = btnLoc[0] - mainLoc[0];
                    float y = btnLoc[1] - mainLoc[1];
                    if (enterFillet == null) {
                        enterFillet = new EnterFilletDrawable(dpToPx(4));
                        mainContainer.getOverlay().add(enterFillet);
                    }
                    enterFillet.setPosition(x, y);
                    enterFillet.setBounds(0, 0, mainContainer.getWidth(), mainContainer.getHeight());
                }
            }
        });
    }

    private LinearLayout buildRow(int rowIndex, String[] labels, String[] codes) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                0, 1.0f));

        boolean isShift = activeModifiers.contains("SHIFT");
        boolean isAltGr = activeModifiers.contains("ALTGR");

        for (int i = 0; i < labels.length; i++) {
            String originalLabel = labels[i];
            String code = codes[i];
            
            String label = getDynamicLabel(originalLabel, code, isShift, isAltGr, isCapsActive, currentLayout);

            if (code.equals("SPACE")) {
                LinearLayout col = new LinearLayout(this);
                col.setLayoutParams(new LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.MATCH_PARENT, 4.5f));
                col.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));

                Button b = new Button(this);
                android.graphics.drawable.GradientDrawable spGd = new android.graphics.drawable.GradientDrawable();
                spGd.setColor(android.graphics.Color.parseColor("#444444"));
                spGd.setCornerRadius(dpToPx(8));
                spGd.setStroke(dpToPx(1), android.graphics.Color.parseColor("#222222"));
                b.setBackground(spGd);
                b.setLayoutParams(new LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT));
                b.setOnClickListener(v -> handleKey(" ", "SPACE"));
                col.addView(b);
                row.addView(col);
                continue;
            }

            Button b = new Button(this);
            if (code.equals("LWIN")) {
                android.graphics.drawable.Drawable d = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_tux);
                if (d != null) {
                    d = androidx.core.graphics.drawable.DrawableCompat.wrap(d).mutate();
                    d.setBounds(0, 0, dpToPx(20), dpToPx(20));
                    b.setForeground(d);
                    b.setForegroundGravity(android.view.Gravity.CENTER);
                    b.setText("");
                    b.setTag(d); // store it
                }
            } else if (code.equals("UP_DOWN")) {
                // Handled below
            } else {
                b.setText(label);
            }

            b.setAllCaps(false);
            b.setTextColor(Color.WHITE);
            if (label.equals("↵")) {
                b.setTextSize(32f);
            } else if (label.equals("↹") || label.equals("⇪") || label.equals("⇧") || label.equals("⌫") || label.equals("◀") || label.equals("▶")) {
                b.setTextSize(22f);
            } else {
                b.setTextSize(16f);
            }
            b.setPadding(0, 0, 0, 0);

            // Re-apply tint if active
            if (activeModifiers.contains(code) || (code.equals("CAPSLOCK") && isCapsActive) || (code.equals("FN") && isFnActive)) {
                b.setTextColor(themeAccentColor);
                if (b.getTag() instanceof android.graphics.drawable.Drawable) {
                    androidx.core.graphics.drawable.DrawableCompat.setTint((android.graphics.drawable.Drawable) b.getTag(), themeAccentColor);
                }
            }

            float weight = 1.0f;
            if (code.equals("BACKSPACE")) weight = 2.0f;
            else if (code.equals("ENTER")) {
                weight = (rowIndex == 1) ? 1.5f : 1.25f;
            }
            else if (code.equals("SHIFT")) {
                weight = (i == 0) ? 1.25f : 2.75f;
            }
            else if (code.equals("CAPSLOCK")) weight = 1.75f;
            else if (code.equals("TAB")) weight = 1.5f;
            else if (code.equals("CTRL") || code.equals("ALT") || code.equals("LWIN") || code.equals("RWIN") || code.equals("ALTGR") || code.equals("FN")) weight = 1.25f;
            else if (code.equals("ESC")) weight = 1.0f;

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.MATCH_PARENT, weight);

            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setColor(Color.parseColor("#444444"));
            gd.setCornerRadius(dpToPx(8));

            LinearLayout col = new LinearLayout(this);
            col.setLayoutParams(lp);
            col.setClipChildren(false);
            col.setPadding(dpToPx(2), dpToPx(4), dpToPx(2), dpToPx(4));

            if (code.equals("ENTER")) {
                if (rowIndex == 1) { // Row 2
                    col.setPadding(dpToPx(2), dpToPx(4), dpToPx(2), 0);
                    gd.setCornerRadii(new float[]{dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8), 0, 0, dpToPx(8), dpToPx(8)});
                    b.setText("↵");
                    topEnterBtn = b;
                    topEnterGd = gd;
                } else if (rowIndex == 2) { // Row 3
                    col.setPadding(dpToPx(2), 0, dpToPx(2), dpToPx(4));
                    gd.setCornerRadii(new float[]{0, 0, 0, 0, dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8)});
                    b.setText("");
                    bottomEnterBtn = b;
                    bottomEnterGd = gd;
                }
            } else {
                gd.setStroke(dpToPx(1), Color.parseColor("#222222"));
            }

            if (code.equals("UP_DOWN")) {
                col.setOrientation(LinearLayout.VERTICAL);
                Button up = new Button(this);
                up.setText("↑");
                up.setTextColor(Color.WHITE);
                up.setBackground(gd);
                up.setLayoutParams(new LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
                up.setOnClickListener(v -> handleKey("↑", "UP"));
                
                Button down = new Button(this);
                down.setText("↓");
                down.setTextColor(Color.WHITE);
                down.setBackground(gd);
                LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
                dLp.setMargins(0, dpToPx(2), 0, 0);
                down.setLayoutParams(dLp);
                down.setOnClickListener(v -> handleKey("↓", "DOWN"));
                
                col.addView(up);
                col.addView(down);
                row.addView(col);
                continue;
            }

            b.setBackground(gd);
            b.setLayoutParams(new LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT));
            
            final android.graphics.drawable.GradientDrawable finalGd = gd;
            b.setOnTouchListener((v, event) -> {
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    finalGd.setColor(Color.parseColor("#777777"));
                    v.setBackground(finalGd);
                    if (code.equals("ENTER")) {
                        if (rowIndex == 1 && bottomEnterGd != null && bottomEnterBtn != null) {
                            bottomEnterGd.setColor(Color.parseColor("#777777"));
                            bottomEnterBtn.setBackground(bottomEnterGd);
                            if (enterFillet != null) enterFillet.setColor(Color.parseColor("#777777"));
                        } else if (rowIndex == 2 && topEnterGd != null && topEnterBtn != null) {
                            topEnterGd.setColor(Color.parseColor("#777777"));
                            topEnterBtn.setBackground(topEnterGd);
                            if (enterFillet != null) enterFillet.setColor(Color.parseColor("#777777"));
                        }
                    }
                } else if (event.getAction() == android.view.MotionEvent.ACTION_UP || event.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                    finalGd.setColor(Color.parseColor("#444444"));
                    v.setBackground(finalGd);
                    if (code.equals("ENTER")) {
                        if (rowIndex == 1 && bottomEnterGd != null && bottomEnterBtn != null) {
                            bottomEnterGd.setColor(Color.parseColor("#444444"));
                            bottomEnterBtn.setBackground(bottomEnterGd);
                            if (enterFillet != null) enterFillet.setColor(Color.parseColor("#444444"));
                        } else if (rowIndex == 2 && topEnterGd != null && topEnterBtn != null) {
                            topEnterGd.setColor(Color.parseColor("#444444"));
                            topEnterBtn.setBackground(topEnterGd);
                            if (enterFillet != null) enterFillet.setColor(Color.parseColor("#444444"));
                        }
                    }
                }
                return false;
            });

            b.setOnClickListener(v -> {
                if (code.equals("RWIN")) {
                    v.post(() -> cycleLayout());
                } else if (code.equals("OPEN_TOUCHPAD")) {
                    isFnActive = false;
                    currentLayout = "AZERTY";
                    sendLayout("azerty");
                    Intent intent = new Intent(KeyboardActivity.this, TouchpadActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    startActivity(intent);
                    finish();
                } else if (code.equals("FN")) {
                    isFnActive = !isFnActive;
                    v.post(() -> showKeyboard(currentLayout));
                } else if (code.equals("CAPSLOCK")) {
                    isCapsActive = !isCapsActive;
                    lastCapsToggleTime = System.currentTimeMillis();
                    handleKey(label, code);
                    v.post(() -> showKeyboard(currentLayout));
                } else if (code.equals("CTRL") || code.equals("ALT") || code.equals("SHIFT") || code.equals("LWIN") || code.equals("ALTGR")) {
                    if (activeModifiers.contains(code)) {
                        activeModifiers.remove(code);
                    } else {
                        activeModifiers.add(code);
                    }
                    if (code.equals("SHIFT") || code.equals("ALTGR")) {
                        v.post(() -> showKeyboard(currentLayout));
                    } else {
                        // Just update color without full redraw
                        if (activeModifiers.contains(code)) {
                            b.setTextColor(themeAccentColor);
                            if (b.getTag() instanceof android.graphics.drawable.Drawable) {
                                androidx.core.graphics.drawable.DrawableCompat.setTint((android.graphics.drawable.Drawable) b.getTag(), themeAccentColor);
                                b.invalidate();
                            }
                        } else {
                            b.setTextColor(Color.WHITE); // Default
                            if (b.getTag() instanceof android.graphics.drawable.Drawable) {
                                androidx.core.graphics.drawable.DrawableCompat.setTint((android.graphics.drawable.Drawable) b.getTag(), Color.WHITE);
                                b.invalidate();
                            }
                        }
                    }
                } else {
                    handleKey(label, code);
                }
            });

            b.setOnLongClickListener(v -> {
                if (code.equals("CTRL") || code.equals("ALT") || code.equals("SHIFT") || code.equals("LWIN") || code.equals("ALTGR")) {
                    handleKey(label, code);
                    return true;
                }
                return false;
            });

            col.addView(b);
            row.addView(col);
        }
        return row;
    }

    private static final String[] EMOJI_SMILEYS = {"😀","😃","😄","😁","😆","😅","🤣","😂","🙂","🙃","🫠","😉","😊","😇","🥰","😍","🤩","😘","😗","☺️","😚","😋","😛","😜","🤪","😝","🤑","🤗","🤭","🫢","🫣","🤫","🤔","🫡","🤐","🤨","😐","😑","😶","🫥","😏","😒","🙄","😬","😮‍💨","🤥","😌","👋","🤚","🖐","✋","🖖","👌","🤌","🤏","✌","🤞","🫰","🤟","🤘","🤙","👈","👉","👆","🖕","👇","☝","👍","👎","✊","👊","🤛","🤜","👏","🙌","🫶","👐","🤲","🤝","🙏","✍","💅","🤳","💪","🦾","🦿","🦵","🦶","👂","🦻","👃","🧠","🫀","🫁","🦷","🦴","👀","👁","👅","👄","💋","🩸","😔","😪","🤤","😴","😷","🤒","🤕","🤢","🤮","🤧","🥵","🥶","🥴","😵","😵‍💫","🤯","🤠","🥳","🥸","😎","🤓","🧐","😕","🫤","😟","🙁","☹️","😮","😯","😲","😳","🥺","🥹","😦","😧","😨","😰","😥","😢","😭","😱","😖","😣","😞","😓","😩","😫","🥱","😤","😡","😠","🤬","😈","👿","💀","☠️","💩","🤡","👹","👺","👻","👽","👾","🤖"};
    private static final String[] EMOJI_ANIMALS = {"🐵","🐒","🦍","🦧","🐶","🐕","🦮","🐕‍🦺","🐩","🐺","🦊","🦝","🐱","🐈","🐈‍⬛","🦁","🐯","🐅","🐆","🐴","🫎","🫏","🐎","🦄","🦓","🦌","🦬","🐮","🐂","🐃","🐄","🐷","🐖","🐗","🐽","🐏","🐑","🐐","🐪","🐫","🦙","🦒","🐘","🦣","🦏","🦛","🐭","🐁","🐀","🐹","🐰","🐇","🐿️","🦫","🦔","🦇","🐻","🐻‍❄️","🐨","🐼","🦥","🦦","🦨","🦘","🦡","🐾","🦃","🐔","🐓","🐣","🐤","🐥","🐦","🐧","🕊️","🦅","🦆","🦢","🦉","🦤","🪶","🦩","🦚","🦜","🐸","🐊","🐢","🦎","🐍","🐲","🐉","🦕","🦖","🐳","🐋","🐬","🦭","🐟","🐠","🐡","🦈","🐙","🐚","🪸","🐌","🦋","🐛","🐜","🐝","🪲","🐞","🦗","🪳","🕷️","🕸️","🦂","🦟","🪰","🪱","🦠"};
    private static final String[] EMOJI_FOOD = {"🍏","🍎","🍐","🍊","🍋","🍌","🍉","🍇","🍓","🫐","🍈","🍒","🍑","🥭","🍍","🥥","🥝","🍅","🍆","🥑","🥦","🥬","🥒","🌶️","🫑","🌽","🥕","🫒","🧄","🧅","🥔","🍠","🥐","🥯","🍞","🥖","🥨","🧀","🥚","🍳","🧈","🥞","🧇","🥓","🥩","🍗","🍖","🦴","🌭","🍔","🍟","🍕","🫓","🥪","🥙","🧆","🌮","🌯","🫔","🥗","🥘","🫕","🥫","🍝","🍜","🍲","🍛","🍣","🍱","🥟","🦪","🍤","🍙","🍚","🍘","🍥","🥠","🥮","🍢","🍡","🍧","🍨","🍦","🥧","🧁","🍰","🎂","🍮","🍭","🍬","🍫","🍿","🍩","🍪","🌰","🥜","🫘","🍯","🥛","🍼","🫖","☕","🍵","🧃","🥤","🧋","🍶","🍺","🍻","🥂","🍷","🥃","🍸","🍹","🧉","🍾","🧊","🥄","🍴","🍽️","🥣","🥡","🥢","🧂"};
    private static final String[] EMOJI_ACTIVITIES = {"⚽","⚾","🥎","🏀","🏐","🏈","🏉","🎾","🥏","🎳","🏏","🏑","🏒","🥍","🏓","🏸","🥊","🥋","🥅","⛳","⛸️","🎣","🤿","🎽","🎿","🛷","🥌","🎯","🪀","🪁","🔫","🎱","🔮","🪄","🎮","🕹️","🎰","🎲","🧩","🧸","🪅","🪩","🪆","♠️","♥️","♦️","♣️","♟️","🃏","🀄","🎴","🎭","🖼️","🎨","🧵","🪡","🧶","🪢"};
    private static final String[] EMOJI_OBJECTS = {"⌚","📱","📲","💻","⌨️","🖥️","🖨️","🖱️","🖲️","🕹️","🗜️","💽","💾","💿","📀","📼","📷","📸","📹","🎥","📽️","🎞️","📞","☎️","📟","📠","📺","📻","🎙️","🎚️","🎛️","🧭","⏱️","⏲️","⏰","🕰️","⌛","⏳","📡","🔋","🔌","💡","🔦","🕯️","🪔","🧯","🛢️","💸","💵","💴","💶","💷","🪙","💰","💳","💎","⚖️","🪜","🧰","🪛","🔧","🔨","⚒️","🛠️","⛏️","🪚","🔩","⚙️","🪤","🧱","⛓️","🧲","🔫","💣","🧨","🪓","🔪","🗡️","⚔️","🛡️","🚬","⚰️","🪦","⚱️","🏺","🔮","📿","🧿","💈","⚗️","🔭","🔬","🕳️","🩹","🩺","💊","💉","🩸","🧬","🦠","🧫","🧪","🌡️","🧹","🪠","🧺","🧻","🚽","🚰","🚿","🛁","🛀","🧼","🪥","🪒","🧽","🪣","🧴","🛎️","🔑","🗝️","🚪","🪑","🛋️","🛏️","🛌","🧸","🪆","🖼️","🪞","🪟","🛍️","🛒","🎁","🎈","🎏","🎀","🪄","🪅","🎊","🎉","🎎","🏮","🎐","🧧","✉️","📩","📨","📧","💌","📥","📤","📦","🏷️","🪧","📪","📫","📬","📭","📮","📯","📜","📃","📄","📑","🧾","📊","📈","📉","🗒️","🗓️","📆","📅","🗑️","📇","🗃️","🗳️","🗄️","📋","📁","📂","🗂️","🗞️","📰","📓","📔","📒","📕","📗","📘","📙","📚","📖","🔖","🧷","🔗","📎","🖇️","📐","📏","🧮","📌","📍","✂️","🖊️","🖋️","✒️","🖌️","🖍️","📝","✏️","🔍","🔎","🔏","🔐","🔒","🔓"};
    private static final String[] EMOJI_SYMBOLS = {"❤️","🧡","💛","💚","💙","💜","🖤","🤍","🤎","💔","❣️","💕","💞","💓","💗","💖","💘","💝","💟","☮️","✝️","☪️","🕉️","☸️","✡️","🔯","🕎","☯️","☦️","🛐","⛎","♈","♉","♊","♋","♌","♍","♎","♏","♐","♑","♒","♓","🆔","⚛️","🉑","☢️","☣️","📴","📳","🈶","🈚","🈸","🈺","🈷️","✴️","🆚","💮","🉐","㊙️","㊗️","🈴","🈵","🈹","🈲","🅰️","🅱️","🆎","🆑","🅾️","🆘","❌","⭕","🛑","⛔","📛","🚫","💯","💢","♨️","🚷","🚯","🚳","🚱","🔞","📵","🚭","❗","❕","❓","❔","‼️","⁉️","🔅","🔆","〽️","⚠️","🚸","🔱","⚜️","🔰","♻️","✅","🈯","💹","❇️","✳️","❎","🌐","💠","Ⓜ️","🌀","💤","🏧","🚾","♿","🅿️","🈳","🈂️","🛂","🛃","🛄","🛅","🚹","🚺","🚼","🚻","🚮","🎦","📶","🈁","🔣","ℹ️","🔤","🔡","🔠","🆖","🆗","🆙","🆒","🆕","🆓","0️⃣","1️⃣","2️⃣","3️⃣","4️⃣","5️⃣","6️⃣","7️⃣","8️⃣","9️⃣","🔟","🔢","#️⃣","*️⃣","⏏️","▶️","⏸️","⏯️","⏹️","⏺️","⏭️","⏮️","⏩","⏪","⏫","⏬","◀️","🔼","🔽","➡️","⬅️","⬆️","⬇️","↗️","↘️","↙️","↖️","↕️","↔️","↪️","↩️","⤴️","⤵️","🔀","🔁","🔂","🔄","🔃","🎵","🎶","➕","➖","➗","✖️","🟰","♾️","💲","💱","™️","©️","®️","〰️","➰","➿","🔚","🔙","🔛","🔝","🔜","✔️","☑️","🔘","🔴","🟠","🟡","🟢","🔵","🟣","⚫","⚪","🟤","🔺","🔻","🔸","🔹","🔶","🔷","🔳","🔲","▪️","▫️","◾","◽","◼️","◻️","🟥","🟧","🟨","🟩","🟦","🟪","⬛","⬜","🟫"};
    private static final String[] EMOJI_TRAVEL = {"🚗","🚕","🚙","🚌","🚎","🏎️","🚓","🚑","🚒","🚐","🛻","🚚","🚛","🚜","🦯","🦽","🦼","🛴","🚲","🛵","🏍️","🛺","🚨","🚔","🚍","🚘","🚖","🚡","🚠","🚟","🚃","🚋","🚞","🚝","🚄","🚅","🚈","🚂","🚆","🚇","🚊","🚉","✈️","🛫","🛬","🛩️","💺","🛰️","🚀","🛸","🚁","🛶","⛵","🚤","🛥️","🛳️","⛴️","🚢","⚓","🪝","⛽","🚧","🚦","🚥","🚏","🗺️","🗿","🗽","🗼","🏰","🏯","🏟️","🎡","🎢","🎠","⛲","⛱️","🏖️","🏝️","🏜️","🌋","⛰️","🏔️","🗻","🏕️","⛺","🛖","🏠","🏡","🏘️","🏚️","🏗️","🏭","🏢","🏬","🏣","🏤","🏥","🏦","🏨","🏪","🏫","🏩","💒","🏛️","⛪","🕌","🕍","🛕","🕋","⛩️","🛤️","🛣️","🗾","🎑","🏞️","🌅","🌄","🌠","🎇","🎆","🌇","🌆","🏙️","🌃","🌌","🌉","🌁"};
    private static final String[] EMOJI_FLAGS = {"🏁","🚩","🎌","🏴","🏳️","🏳️‍🌈","🏳️‍⚧️","🏴‍☠️","🇦🇨","🇦🇩","🇦🇪","🇦🇫","🇦🇬","🇦🇮","🇦🇱","🇦🇲","🇦🇴","🇦🇶","🇦🇷","🇦🇸","🇦🇹","🇦🇺","🇦🇼","🇦🇽","🇦🇿","🇧🇦","🇧🇧","🇧🇩","🇧🇪","🇧🇫","🇧🇬","🇧🇭","🇧🇮","🇧🇯","🇧🇱","🇧🇲","🇧🇳","🇧🇴","🇧🇶","🇧🇷","🇧🇸","🇧🇹","🇧🇻","🇧🇼","🇧🇾","🇧🇿","🇨🇦","🇨🇨","🇨🇩","🇨🇫","🇨🇬","🇨🇭","🇨🇮","🇨🇰","🇨🇱","🇨🇲","🇨🇳","🇨🇴","🇨🇵","🇨🇷","🇨🇺","🇨🇻","🇨🇼","🇨🇽","🇨🇾","🇨🇿","🇩🇪","🇩🇬","🇩🇯","🇩🇰","🇩🇲","🇩🇴","🇩🇿","🇪🇦","🇪🇨","🇪🇪","🇪🇬","🇪🇭","🇪🇷","🇪🇸","🇪🇹","🇪🇺","🇫🇮","🇫🇯","🇫🇰","🇫🇲","🇫🇴","🇫🇷","🇬🇦","🇬🇧","🇬🇩","🇬🇪","🇬🇫","🇬🇬","🇬🇭","🇬🇮","🇬🇱","🇬🇲","🇬🇳","🇬🇵","🇬🇶","🇬🇷","🇬🇸","🇬🇹","🇬🇺","🇬🇼","🇬🇾","🇭🇰","🇭🇲","🇭🇳","🇭🇷","🇭🇹","🇭🇺","🇮🇨","🇮🇩","🇮🇪","🇮🇱","🇮🇲","🇮🇳","🇮🇴","🇮🇶","🇮🇷","🇮🇸","🇮🇹","🇯🇪","🇯🇲","🇯🇴","🇯🇵","🇰🇪","🇰🇬","🇰🇭","🇰🇮","🇰🇲","🇰🇳","🇰🇵","🇰🇷","🇰🇼","🇰🇾","🇰🇿","🇱🇦","🇱🇧","🇱🇨","🇱🇮","🇱🇰","🇱🇷","🇱🇸","🇱🇹","🇱🇺","🇱🇻","🇱🇾","🇲🇦","🇲🇨","🇲🇩","🇲🇪","🇲🇫","🇲🇬","🇲🇭","🇲🇰","🇲🇱","🇲🇲","🇲🇳","🇲🇴","🇲🇵","🇲🇶","🇲🇷","🇲🇸","🇲🇹","🇲🇺","🇲🇻","🇲🇼","🇲🇽","🇲🇾","🇲🇿","🇳🇦","🇳🇨","🇳🇪","🇳🇫","🇳🇬","🇳🇮","🇳🇱","🇳🇴","🇳🇵","🇳🇷","🇳🇺","🇳🇿","🇴🇲","🇵🇦","🇵🇪","🇵🇫","🇵🇬","🇵🇭","🇵🇰","🇵🇱","🇵🇲","🇵🇳","🇵🇷","🇵🇸","🇵🇹","🇵🇼","🇵🇾","🇶🇦","🇷🇪","🇷🇴","🇷🇸","🇷🇺","🇷🇼","🇸🇦","🇸🇧","🇸🇨","🇸🇩","🇸🇪","🇸🇬","🇸🇭","🇸🇮","🇸🇯","🇸🇰","🇸🇱","🇸🇲","🇸🇳","🇸🇴","🇸🇷","🇸🇸","🇸🇹","🇸🇻","🇸🇽","🇸🇾","🇸🇿","🇹🇦","🇹🇨","🇹🇩","🇹🇫","🇹🇬","🇹🇭","🇹🇯","🇹🇰","🇹🇱","🇹🇲","🇹🇳","🇹🇴","🇹🇷","🇹🇹","🇹🇻","🇹🇼","🇹🇿","🇺🇦","🇺🇬","🇺🇲","🇺🇳","🇺🇸","🇺🇾","🇺🇿","🇻🇦","🇻🇨","🇻🇪","🇻🇬","🇻🇮","🇻🇳","🇻🇺","🇼🇫","🇼🇸","🇽🇰","🇾🇪","🇾🇹","🇿🇦","🇿🇲","🇿🇼"};

    private HorizontalScrollView emojiScrollView;
    private LinearLayout emojiContainer;
    private java.util.List<View> categoryViews = new java.util.ArrayList<>();

    private void showEmoji() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        currentLayout = "EMOJI";
        if (mainContainer == null) initMainContainer();
        mainContainer.removeAllViews();
        clearModifiers();

        // Top Navigation Bar
        LinearLayout navBar = new LinearLayout(this);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setGravity(Gravity.CENTER);
        navBar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(50)));
        navBar.setBackgroundColor(Color.parseColor("#1E1E1E"));

        String[] catLabels = {"123", "😀", "🐻", "🍔", "⚽", "🚗", "💡", "❤️", "🏁", "⌫"};
        String[][] catArrays = {null, EMOJI_SMILEYS, EMOJI_ANIMALS, EMOJI_FOOD, EMOJI_ACTIVITIES, EMOJI_TRAVEL, EMOJI_OBJECTS, EMOJI_SYMBOLS, EMOJI_FLAGS, null};
        
        emojiScrollView = new HorizontalScrollView(this);
        emojiScrollView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        emojiScrollView.setFillViewport(true);
        
        emojiContainer = new LinearLayout(this);
        emojiContainer.setOrientation(LinearLayout.HORIZONTAL);
        emojiContainer.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        emojiScrollView.addView(emojiContainer);
        
        categoryViews.clear();
        int emojiSize = dpToPx(60);

        for (int c = 0; c < catArrays.length; c++) {
            if (catArrays[c] == null) {
                categoryViews.add(null);
                continue;
            }
            GridLayout grid = new GridLayout(this);
            grid.setOrientation(GridLayout.VERTICAL);
            grid.setRowCount(5);
            grid.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            categoryViews.add(grid);
            emojiContainer.addView(grid);
        }

        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        handler.post(new Runnable() {
            int c = 0;
            @Override
            public void run() {
                if (!currentLayout.equals("EMOJI")) return;

                while (c < catArrays.length && catArrays[c] == null) {
                    c++;
                }
                if (c >= catArrays.length) return;

                String[] arrToUse = catArrays[c];
                GridLayout grid = (GridLayout) categoryViews.get(c);
                
                for (String emoji : arrToUse) {
                    Button b = new Button(KeyboardActivity.this);
                    b.setText(emoji);
                    b.setTextSize(24f);
                    b.setBackgroundColor(Color.TRANSPARENT);
                    GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                    lp.width = emojiSize;
                    lp.height = emojiSize;
                    b.setLayoutParams(lp);
                    b.setOnClickListener(v -> handleKey(emoji, ""));
                    grid.addView(b);
                }
                c++;
                handler.postDelayed(this, 10);
            }
        });

        for (int i = 0; i < catLabels.length; i++) {
            String label = catLabels[i];
            Button b = new Button(this);
            b.setText(label);
            b.setTextSize(20f);
            b.setBackgroundColor(Color.TRANSPARENT);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            b.setLayoutParams(lp);
            
            final int catIndex = i;
            b.setOnClickListener(v -> {
                if (label.equals("123")) {
                    cycleLayout(); // The Back button
                } else if (label.equals("⌫")) {
                    handleKey("BACKSPACE", "BACKSPACE");
                } else {
                    if (catIndex < categoryViews.size() && categoryViews.get(catIndex) != null) {
                        View target = categoryViews.get(catIndex);
                        emojiScrollView.smoothScrollTo(target.getLeft(), 0);
                    }
                }
            });
            navBar.addView(b);
        }

        mainContainer.addView(navBar);
        mainContainer.addView(emojiScrollView);
        mainContainer.requestLayout();
        setContentView(mainContainer);
    }
    
    private void clearModifiers() {
        if (!activeModifiers.isEmpty()) {
            activeModifiers.clear();
            runOnUiThread(() -> showKeyboard(currentLayout));
        }
    }

    private void handleKey(String label, String code) {
        executorService.execute(() -> {
            boolean success = false;
            int retries = 0;
            while (retries < 2 && !success) {
                try {
                    String baseUrl = NetworkManager.getBaseUrl(this);
                    URL url;
                    JSONObject json = new JSONObject();
                    
                    if (currentLayout.equals("EMOJI") && !code.equals("BACKSPACE")) {
                        url = new URL(baseUrl + "/keyboard/type");
                        json.put("text", label);
                    } else {
                        url = new URL(baseUrl + "/keyboard/key");
                        json.put("key", code);
                        JSONArray mods = new JSONArray();
                        for(String m : activeModifiers) {
                            mods.put(m);
                        }
                        json.put("modifiers", mods);
                    }

                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(3000);
                    conn.setReadTimeout(3000);
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);
                    
                    OutputStream os = conn.getOutputStream();
                    os.write(json.toString().getBytes());
                    os.flush();
                    os.close();
                    
                    if (conn.getResponseCode() == 200) {
                        success = true;
                    }
                    conn.disconnect();
                } catch (Exception e) {
                    Log.e("Keyboard", "Error sending key, retrying...", e);
                    NetworkManager.reportNetworkError();
                }
                retries++;
            }
            
            if (success && !currentLayout.equals("EMOJI") && !activeModifiers.isEmpty()) {
                runOnUiThread(this::clearModifiers);
            }
        });
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
}
