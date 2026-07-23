package com.elyot.pclink;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.widget.CheckBox;

import androidx.appcompat.app.AppCompatActivity;

public class MusicSettingsActivity extends AppCompatActivity {

    private CheckBox cbPersistentNotification;
    private CheckBox cbHardwareVolume;
    private SharedPreferences prefs;
    private SharedPreferences.OnSharedPreferenceChangeListener prefListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_music_settings);
        ThemeUtils.applyTheme(this, true);
        
        prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        cbPersistentNotification = findViewById(R.id.cbPersistentNotification);
        cbHardwareVolume = findViewById(R.id.cbHardwareVolume);
        
        boolean isNotifEnabled = prefs.getBoolean(Constants.PREF_NOTIF_ENABLED, false);
        cbPersistentNotification.setChecked(isNotifEnabled);

        boolean isHwEnabled = prefs.getBoolean("pref_hardware_volume", true);
        cbHardwareVolume.setChecked(isHwEnabled);

        cbHardwareVolume.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("pref_hardware_volume", isChecked).apply();
            Intent intent = new Intent(this, MusicNotificationService.class);
            intent.setAction(MusicNotificationService.ACTION_UPDATE_FROM_APP);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        });

        cbPersistentNotification.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (prefs.getBoolean(Constants.PREF_NOTIF_ENABLED, false) != isChecked) {
                if (isChecked) {
                    android.media.AudioManager audioManager = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    if (audioManager != null && audioManager.isMusicActive()) {
                        android.widget.Toast.makeText(this, "Local media is currently playing.", android.widget.Toast.LENGTH_SHORT).show();
                        cbPersistentNotification.setChecked(false);
                        return;
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
                        cbPersistentNotification.setChecked(false);
                        return;
                    }
                }
                
                prefs.edit().putBoolean(Constants.PREF_NOTIF_ENABLED, isChecked).apply();
                if (isChecked) {
                    Intent intent = new Intent(this, MusicNotificationService.class);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent);
                    } else {
                        startService(intent);
                    }
                } else {
                    Intent intent = new Intent(this, MusicNotificationService.class);
                    stopService(intent);
                }
            }
        });

        // Lyrics Offset
        android.widget.SeekBar sbLyricsOffset = findViewById(R.id.sbLyricsOffset);
        android.widget.TextView tvLyricsOffsetValue = findViewById(R.id.tvLyricsOffsetValue);
        android.widget.Button btnLyricsMinus = findViewById(R.id.btnLyricsMinus);
        android.widget.Button btnLyricsPlus = findViewById(R.id.btnLyricsPlus);

        int currentOffset = prefs.getInt("pref_lyrics_offset", 1000);
        sbLyricsOffset.setProgress(currentOffset + 2000);
        tvLyricsOffsetValue.setText(currentOffset + " ms");

        sbLyricsOffset.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                int val = progress - 2000;
                tvLyricsOffsetValue.setText(val + " ms");
                if (fromUser) prefs.edit().putInt("pref_lyrics_offset", val).apply();
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });

        btnLyricsMinus.setOnClickListener(v -> {
            int newProg = Math.max(0, sbLyricsOffset.getProgress() - 50);
            sbLyricsOffset.setProgress(newProg);
            prefs.edit().putInt("pref_lyrics_offset", newProg - 2000).apply();
        });
        btnLyricsPlus.setOnClickListener(v -> {
            int newProg = Math.min(4000, sbLyricsOffset.getProgress() + 50);
            sbLyricsOffset.setProgress(newProg);
            prefs.edit().putInt("pref_lyrics_offset", newProg - 2000).apply();
        });

        // Rotation Speed
        android.widget.SeekBar sbRotationSpeed = findViewById(R.id.sbRotationSpeed);
        android.widget.TextView tvRotationSpeedValue = findViewById(R.id.tvRotationSpeedValue);
        android.widget.Button btnRotationMinus = findViewById(R.id.btnRotationMinus);
        android.widget.Button btnRotationPlus = findViewById(R.id.btnRotationPlus);
        
        int currentRpm = prefs.getInt("pref_rotation_speed", 33);
        sbRotationSpeed.setProgress(currentRpm);
        tvRotationSpeedValue.setText(currentRpm + " RPM");

        sbRotationSpeed.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                tvRotationSpeedValue.setText(progress + " RPM");
                if (fromUser) prefs.edit().putInt("pref_rotation_speed", progress).apply();
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });
        
        btnRotationMinus.setOnClickListener(v -> {
            int p = Math.max(0, sbRotationSpeed.getProgress() - 5);
            sbRotationSpeed.setProgress(p);
            prefs.edit().putInt("pref_rotation_speed", p).apply();
        });

        btnRotationPlus.setOnClickListener(v -> {
            int p = Math.min(100, sbRotationSpeed.getProgress() + 5);
            sbRotationSpeed.setProgress(p);
            prefs.edit().putInt("pref_rotation_speed", p).apply();
        });

        // Polling Interval
        android.widget.SeekBar sbPollingInterval = findViewById(R.id.sbPollingInterval);
        android.widget.TextView tvPollingIntervalValue = findViewById(R.id.tvPollingIntervalValue);
        android.widget.Button btnPollingMinus = findViewById(R.id.btnPollingMinus);
        android.widget.Button btnPollingPlus = findViewById(R.id.btnPollingPlus);
        
        int currentPolling = prefs.getInt("pref_polling_interval", 3000);
        sbPollingInterval.setProgress(currentPolling - 500);
        tvPollingIntervalValue.setText(currentPolling + " ms");

        sbPollingInterval.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                int val = progress + 500;
                tvPollingIntervalValue.setText(val + " ms");
                if (fromUser) prefs.edit().putInt("pref_polling_interval", val).apply();
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });
        
        btnPollingMinus.setOnClickListener(v -> {
            int p = Math.max(0, sbPollingInterval.getProgress() - 250);
            sbPollingInterval.setProgress(p);
            prefs.edit().putInt("pref_polling_interval", p + 500).apply();
        });

        btnPollingPlus.setOnClickListener(v -> {
            int p = Math.min(4500, sbPollingInterval.getProgress() + 250);
            sbPollingInterval.setProgress(p);
            prefs.edit().putInt("pref_polling_interval", p + 500).apply();
        });

        // Colors
        android.widget.EditText etTimeColor = findViewById(R.id.etTimeColor);
        android.view.View vTimeColorPreview = findViewById(R.id.vTimeColorPreview);
        String timeColorStr = prefs.getString("pref_time_color", "#FFFFFF");
        etTimeColor.setText(timeColorStr);
        updateColorPreview(timeColorStr, vTimeColorPreview);

        etTimeColor.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                String color = s.toString();
                if (updateColorPreview(color, vTimeColorPreview)) {
                    prefs.edit().putString("pref_time_color", color).apply();
                }
            }
        });

        android.widget.EditText etVolColor = findViewById(R.id.etVolColor);
        android.view.View vVolColorPreview = findViewById(R.id.vVolColorPreview);
        String volColorStr = prefs.getString("pref_vol_color", "#FFFFFF");
        etVolColor.setText(volColorStr);
        updateColorPreview(volColorStr, vVolColorPreview);

        etVolColor.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                String color = s.toString();
                if (updateColorPreview(color, vVolColorPreview)) {
                    prefs.edit().putString("pref_vol_color", color).apply();
                }
            }
        });

        android.widget.Button btnSaveSettings = findViewById(R.id.btnSaveSettings);
        btnSaveSettings.setOnClickListener(v -> finish());

        prefListener = (sharedPreferences, key) -> {
            if (Constants.PREF_NOTIF_ENABLED.equals(key)) {
                boolean enabled = sharedPreferences.getBoolean(key, false);
                if (cbPersistentNotification.isChecked() != enabled) {
                    cbPersistentNotification.setChecked(enabled);
                }
            } else if ("pref_hardware_volume".equals(key)) {
                boolean enabled = sharedPreferences.getBoolean(key, true);
                if (cbHardwareVolume.isChecked() != enabled) {
                    cbHardwareVolume.setChecked(enabled);
                }
            }
        };
        prefs.registerOnSharedPreferenceChangeListener(prefListener);
    }

    private boolean updateColorPreview(String hex, android.view.View previewView) {
        try {
            int color = android.graphics.Color.parseColor(hex);
            previewView.setBackgroundColor(color);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101 && grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            cbPersistentNotification.setChecked(true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (prefs != null && prefListener != null) {
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener);
        }
    }
}
