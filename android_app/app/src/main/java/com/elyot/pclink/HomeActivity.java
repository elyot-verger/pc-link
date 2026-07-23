package com.elyot.pclink;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

public class HomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        ThemeUtils.applyTheme(this, false);

        LinearLayout btnOpenDashboard = findViewById(R.id.btnOpenDashboard);
        btnOpenDashboard.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, SshActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.btnHomeSettings).setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, NewSettingsActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.btnMusicControl).setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, MusicControlActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.btnPcStatus).setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, PcStatusActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.btnVirtualKeyboard).setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, KeyboardActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.btnTouchpad).setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, TouchpadActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        ThemeUtils.applyTheme(this, false);
    }
}
