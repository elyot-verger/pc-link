package com.elyot.pclink;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class PcSettingsActivity extends AppCompatActivity {

    private android.widget.SeekBar sbRefreshRate;
    private android.widget.TextView tvRefreshRateValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pc_settings);
        ThemeUtils.applyTheme(this, true);

        sbRefreshRate = findViewById(R.id.sbRefreshRate);
        tvRefreshRateValue = findViewById(R.id.tvRefreshRateValue);
        Button btnRateMinus = findViewById(R.id.btnRateMinus);
        Button btnRatePlus = findViewById(R.id.btnRatePlus);
        android.widget.Switch swShowVolumePct = findViewById(R.id.swShowVolumePct);
        android.widget.Switch swShowBatteryPct = findViewById(R.id.swShowBatteryPct);
        Button btnSave = findViewById(R.id.btnSavePcSettings);

        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        int refreshRate = prefs.getInt("pc_refresh_rate", 3000);
        
        // Convert old second-based values to ms
        if (refreshRate < 50) {
            refreshRate *= 1000;
        }

        sbRefreshRate.setMax(18);
        
        int currentProgress = (refreshRate - 500) / 250;
        currentProgress = Math.max(0, Math.min(18, currentProgress));
        sbRefreshRate.setProgress(currentProgress);
        tvRefreshRateValue.setText(((currentProgress * 250) + 500) + " ms");

        swShowVolumePct.setChecked(prefs.getBoolean("show_volume_pct", true));
        swShowBatteryPct.setChecked(prefs.getBoolean("show_battery_pct", true));

        sbRefreshRate.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                int rate = (progress * 250) + 500;
                tvRefreshRateValue.setText(rate + " ms");
            }
            @Override
            public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });

        btnRateMinus.setOnClickListener(v -> {
            int progress = sbRefreshRate.getProgress();
            progress = Math.max(0, progress - 1);
            sbRefreshRate.setProgress(progress);
        });

        btnRatePlus.setOnClickListener(v -> {
            int progress = sbRefreshRate.getProgress();
            progress = Math.min(sbRefreshRate.getMax(), progress + 1);
            sbRefreshRate.setProgress(progress);
        });

        btnSave.setOnClickListener(v -> {
            try {
                int newRate = (sbRefreshRate.getProgress() * 250) + 500;
                prefs.edit()
                    .putInt("pc_refresh_rate", newRate)
                    .putBoolean("show_volume_pct", swShowVolumePct.isChecked())
                    .putBoolean("show_battery_pct", swShowBatteryPct.isChecked())
                    .apply();
                Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
                finish();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Please enter a valid number", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
