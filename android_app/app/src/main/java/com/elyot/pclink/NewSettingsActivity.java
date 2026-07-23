package com.elyot.pclink;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Button;
import android.widget.Toast;
import com.google.android.material.textfield.TextInputEditText;

public class NewSettingsActivity extends AppCompatActivity {

    private TextInputEditText editTailscaleIp;
    private android.widget.EditText etThemeColor;
    private android.view.View vThemeColorPreview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_settings);
        ThemeUtils.applyTheme(this, true);

        editTailscaleIp = findViewById(R.id.editTailscaleIp);
        etThemeColor = findViewById(R.id.etThemeColor);
        vThemeColorPreview = findViewById(R.id.vThemeColorPreview);

        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        String savedIp = prefs.getString("music_target_ip", "");
        editTailscaleIp.setText(savedIp);

        String currentTheme = prefs.getString("pref_theme_color", "#03DAC5");
        etThemeColor.setText(currentTheme);
        try {
            vThemeColorPreview.setBackgroundColor(android.graphics.Color.parseColor(currentTheme));
        } catch (Exception e) {}
        
        etThemeColor.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                try {
                    vThemeColorPreview.setBackgroundColor(android.graphics.Color.parseColor(s.toString()));
                } catch (Exception e) {}
            }
        });

        Button btnSave = findViewById(R.id.btnSaveNewSettings);
        btnSave.setOnClickListener(v -> saveSettings());
    }

    private void saveSettings() {
        String currentIp = editTailscaleIp.getText() != null ? editTailscaleIp.getText().toString().trim() : "";
        String themeColor = etThemeColor.getText() != null ? etThemeColor.getText().toString().trim() : "#03DAC5";
        getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("music_target_ip", currentIp)
                .putString("pref_theme_color", themeColor)
                .apply();
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
        finish();
    }
}
