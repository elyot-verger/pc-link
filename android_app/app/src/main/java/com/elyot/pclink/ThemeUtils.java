package com.elyot.pclink;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.SeekBar;

public class ThemeUtils {
    public static void applyTheme(Activity activity, boolean tintViews) {
        SharedPreferences prefs = activity.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        String colorStr = prefs.getString("pref_theme_color", "#03DAC5");
        try {
            int color = Color.parseColor(colorStr);
            
            // Status bar color
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Window window = activity.getWindow();
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                window.setStatusBarColor(color);
            }
            
            // Tint all buttons and sliders if required (for settings pages)
            if (tintViews) {
                View root = activity.findViewById(android.R.id.content);
                tintAll(root, color);
            }
        } catch (Exception e) {}
    }
    
    private static void tintAll(View view, int color) {
        if (view instanceof android.widget.Switch) {
            ((android.widget.Switch) view).setThumbTintList(ColorStateList.valueOf(color));
            ((android.widget.Switch) view).setTrackTintList(ColorStateList.valueOf(color));
        } else if (view instanceof android.widget.CompoundButton) {
            ((android.widget.CompoundButton) view).setButtonTintList(ColorStateList.valueOf(color));
        } else if (view instanceof Button) {
            view.setBackgroundTintList(ColorStateList.valueOf(color));
            
            // Calculate luminance to determine text color
            double luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0;
            int textColor = luminance > 0.5 ? Color.BLACK : Color.WHITE;
            ((Button) view).setTextColor(textColor);
            
        } else if (view instanceof SeekBar) {
            ((SeekBar) view).setProgressTintList(ColorStateList.valueOf(color));
            ((SeekBar) view).setThumbTintList(ColorStateList.valueOf(color));
        } else if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                tintAll(vg.getChildAt(i), color);
            }
        }
    }
}
