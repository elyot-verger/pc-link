package com.elyot.pclink;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class KeyboardSettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtils.applyTheme(this, true);
        setContentView(R.layout.activity_keyboard_settings);

        TextView tvKeyboardConnection = findViewById(R.id.tvKeyboardConnection);
        tvKeyboardConnection.setText("Current Connection: " + NetworkManager.getConnectionType(this));

        android.widget.Button btnRefreshConnection = findViewById(R.id.btnRefreshConnection);
        btnRefreshConnection.setOnClickListener(v -> {
            tvKeyboardConnection.setText("Vérification...");
            NetworkManager.reportNetworkError();
            new Thread(() -> {
                NetworkManager.getBaseUrl(this);
                runOnUiThread(() -> {
                    new android.os.Handler().postDelayed(() -> {
                        tvKeyboardConnection.setText("Current Connection: " + NetworkManager.getConnectionType(this));
                    }, 1500);
                });
            }).start();
        });

        android.content.SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        android.widget.CheckBox cbForceTailscale = findViewById(R.id.cbForceTailscale);
        cbForceTailscale.setChecked(prefs.getBoolean("force_tailscale", false));
        cbForceTailscale.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("force_tailscale", isChecked).apply();
            NetworkManager.reportNetworkError(); // Force re-evaluating the connection
            tvKeyboardConnection.setText("Current Connection: " + NetworkManager.getConnectionType(this));
        });

        android.widget.SeekBar sbSensitivity = findViewById(R.id.sbSensitivity);
        // We map 1-100 to 0.1 - 10.0. Default progress 25 -> 2.5
        float currentSensitivity = prefs.getFloat("touchpad_sensitivity", 2.5f);
        sbSensitivity.setProgress((int) (currentSensitivity * 10f));
        
        sbSensitivity.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                if (progress < 1) progress = 1; // min 0.1
                float sensitivity = progress / 10f;
                prefs.edit().putFloat("touchpad_sensitivity", sensitivity).apply();
            }

            @Override
            public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });
    }
}
