package com.elyot.pclink;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.content.pm.PackageManager;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;

public class SshActivity extends AppCompatActivity {



    private GridLayout dashGrid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ssh);
        setTitle(R.string.distant_commands_dashboard);
        ThemeUtils.applyTheme(this, false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        ImageButton btnGlobalSettings = findViewById(R.id.btnGlobalSettings);
        btnGlobalSettings.setOnClickListener(v -> startActivity(new Intent(this, GlobalSettingsActivity.class)));

        dashGrid = findViewById(R.id.dashGrid);

        FloatingActionButton fabAddButton = findViewById(R.id.fabAddButton);

        fabAddButton.setOnClickListener(v -> {
            SharedPreferences p = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
            int count = p.getInt("button_count", 0);
            count++;
            p.edit().putInt("button_count", count).apply();
            
            // Open settings for the newly created button
            Intent intent = new Intent(this, ButtonSettingsActivity.class);
            intent.putExtra("slot", count);
            startActivity(intent);
        });

        runMigrationIfNeeded();
    }

    private void runMigrationIfNeeded() {
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.contains("button_count")) {
            // Check if user has existing legacy commands
            String cmd1 = prefs.getString("ssh_cmd_1", "");
            String cmd2 = prefs.getString("ssh_cmd_2", "");
            String cmd3 = prefs.getString("ssh_cmd_3", "");
            String cmd4 = prefs.getString("ssh_cmd_4", "");
            
            if (!cmd1.isEmpty() || !cmd2.isEmpty() || !cmd3.isEmpty() || !cmd4.isEmpty()) {
                // User has legacy commands, migrate them to 4 buttons
                prefs.edit().putInt("button_count", 4).apply();
            } else {
                // Fresh install, start with 0 buttons
                prefs.edit().putInt("button_count", 0).apply();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        ThemeUtils.applyTheme(this, false);
        refreshDashboard();
    }

    private void refreshDashboard() {
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        
        FloatingActionButton fabAddButton = findViewById(R.id.fabAddButton);
        String fabColorStr = prefs.getString("pref_theme_color", "#03DAC5").trim();
        if (!fabColorStr.startsWith("#")) fabColorStr = "#" + fabColorStr;
        int parsedColor = Color.parseColor("#03DAC5");
        try {
            parsedColor = Color.parseColor(fabColorStr);
        } catch (IllegalArgumentException e) {}
        
        fabAddButton.setBackgroundTintList(ColorStateList.valueOf(parsedColor));
        
        // Calculate relative luminance to determine text/icon color (W3C formula)
        double luminance = (0.299 * Color.red(parsedColor) + 0.587 * Color.green(parsedColor) + 0.114 * Color.blue(parsedColor)) / 255.0;
        if (luminance > 0.5) {
            fabAddButton.setImageTintList(ColorStateList.valueOf(Color.BLACK));
        } else {
            fabAddButton.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        }
        
        int columns = prefs.getInt("buttons_per_line", 2);
        if (columns < 1) columns = 1;
        dashGrid.removeAllViews();
        dashGrid.setColumnCount(columns);

        int buttonCount = prefs.getInt("button_count", 0);
        LayoutInflater inflater = LayoutInflater.from(this);

        for (int i = 0; i < buttonCount; i++) {
            int slot = i + 1;
            
            // Check if button is empty
            String cmd = prefs.getString("ssh_cmd_" + slot, "");
            
            // Fallback for old settings migration
            if (cmd.isEmpty()) {
                if (slot == 1) cmd = prefs.getString("ssh_cmd_1", "");
                else if (slot == 2) cmd = prefs.getString("ssh_cmd_2", "");
                else if (slot == 3) cmd = prefs.getString("ssh_cmd_3", "");
                else if (slot == 4) cmd = prefs.getString("ssh_cmd_4", "");
            }

            View btnView = inflater.inflate(R.layout.item_dashboard_button, dashGrid, false);
            ImageView iconView = btnView.findViewById(R.id.dashIcon);
            TextView textView = btnView.findViewById(R.id.dashText);

            String lbl = prefs.getString("lbl_" + slot, "Cmd " + slot);
            if (lbl.isEmpty()) {
                textView.setVisibility(View.GONE);
            } else {
                textView.setVisibility(View.VISIBLE);
                textView.setText(lbl);
            }

            File file = new File(getFilesDir(), "icon_" + slot + ".png");
            if (file.exists()) {
                Bitmap b = BitmapFactory.decodeFile(file.getAbsolutePath());
                iconView.setImageBitmap(b);
            } else {
                // Default fallback icon
                iconView.setImageResource(R.drawable.ic_square);
            }

            String finalCmd = cmd;
            btnView.setOnClickListener(v -> {
                if (finalCmd.isEmpty()) {
                    // Open settings if empty
                    Intent intent = new Intent(this, ButtonSettingsActivity.class);
                    intent.putExtra("slot", slot);
                    startActivity(intent);
                } else {
                    Intent intent = new Intent(this, CommandReceiver.class);
                    intent.setAction(CommandReceiver.ACTION_EXECUTE_CMD);
                    intent.putExtra(CommandReceiver.EXTRA_SLOT, slot);
                    sendBroadcast(intent);
                }
            });

            btnView.setOnLongClickListener(v -> {
                Intent intent = new Intent(this, ButtonSettingsActivity.class);
                intent.putExtra("slot", slot);
                startActivity(intent);
                return true;
            });

            dashGrid.addView(btnView);
        }

        // Add spacer views to pad the grid so the last row doesn't stretch unexpectedly
        if (buttonCount > 0) {
            int remainder = buttonCount % columns;
            if (remainder != 0) {
                int spacersNeeded = columns - remainder;
                for (int i = 0; i < spacersNeeded; i++) {
                    android.view.View space = inflater.inflate(R.layout.item_dashboard_spacer, dashGrid, false);
                    dashGrid.addView(space);
                }
            }
        }
    }
}
