package com.elyot.pclink;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Spinner;
import android.widget.ArrayAdapter;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class GlobalSettingsActivity extends AppCompatActivity {

    private EditText editHost, editPort, editUsername, editButtonsPerLine;
    private Spinner spinnerTileSlot;
    private TextView tvKeyStatus;
    private android.widget.ImageView ivTileCustomIconPreview;
    private String loadedPrivateKey = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_global_settings);
        setTitle("Distant Commands Settings");
        ThemeUtils.applyTheme(this, true);

        editHost = findViewById(R.id.editDefaultHost);
        editPort = findViewById(R.id.editDefaultPort);
        editUsername = findViewById(R.id.editDefaultUsername);
        editButtonsPerLine = findViewById(R.id.editButtonsPerLine);
        spinnerTileSlot = findViewById(R.id.spinnerTileSlot);
        tvKeyStatus = findViewById(R.id.tvGlobalKeyStatus);
        ivTileCustomIconPreview = findViewById(R.id.ivTileCustomIconPreview);

        Button btnLoadKey = findViewById(R.id.btnLoadGlobalKey);
        Button btnSave = findViewById(R.id.btnSaveGlobalSettings);

        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        editHost.setText(prefs.getString("default_host", prefs.getString(Constants.KEY_HOST, "")));
        editPort.setText(prefs.getString("default_port", prefs.getString(Constants.KEY_PORT, "22")));
        editUsername.setText(prefs.getString("default_username", prefs.getString(Constants.KEY_USERNAME, "")));
        editButtonsPerLine.setText(String.valueOf(prefs.getInt("buttons_per_line", 2)));

        int buttonCount = prefs.getInt("button_count", 0);
        List<String> buttonNames = new ArrayList<>();
        if (buttonCount == 0) {
            buttonNames.add("No buttons available");
        } else {
            for (int i = 1; i <= buttonCount; i++) {
                String lbl = prefs.getString("lbl_" + i, "Button " + i).trim();
                if (lbl.isEmpty()) lbl = "Button " + i;
                buttonNames.add(i + " - " + lbl);
            }
        }
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, buttonNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTileSlot.setAdapter(adapter);
        
        int savedSlot = prefs.getInt("tile_slot", 1);
        if (savedSlot >= 1 && savedSlot <= buttonCount) {
            spinnerTileSlot.setSelection(savedSlot - 1);
        }

        updateCustomIconPreview();

        loadedPrivateKey = prefs.getString(Constants.KEY_PRIVATE_KEY, "");

        if (!loadedPrivateKey.isEmpty()) {
            tvKeyStatus.setText("Private Key Loaded");
            tvKeyStatus.setTextColor(0xFF00AA00);
        }

        Button btnPickCustomTileIcon = findViewById(R.id.btnPickCustomTileIcon);
        btnPickCustomTileIcon.setOnClickListener(v -> openImagePicker());
        
        Button btnClearCustomTileIcon = findViewById(R.id.btnClearCustomTileIcon);
        btnClearCustomTileIcon.setOnClickListener(v -> clearCustomTileIcon());

        btnLoadKey.setOnClickListener(v -> openFilePicker());
        btnSave.setOnClickListener(v -> saveSettings());
    }

    private void updateCustomIconPreview() {
        java.io.File iconFile = new java.io.File(getFilesDir(), "qs_tile_icon.png");
        if (iconFile.exists()) {
            android.graphics.Bitmap b = android.graphics.BitmapFactory.decodeFile(iconFile.getAbsolutePath());
            ivTileCustomIconPreview.setImageBitmap(b);
        } else {
            ivTileCustomIconPreview.setImageDrawable(null);
        }
    }

    private void clearCustomTileIcon() {
        java.io.File iconFile = new java.io.File(getFilesDir(), "qs_tile_icon.png");
        if (iconFile.exists()) {
            iconFile.delete();
            updateCustomIconPreview();
            Toast.makeText(this, "Custom tile icon cleared", Toast.LENGTH_SHORT).show();
            // Automatically reset preference to auto if it was set to custom
            getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE).edit().putString("tile_icon", "auto").apply();
        }
    }

    private void saveSettings() {
        SharedPreferences.Editor editor = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putString("default_host", editHost.getText().toString().trim());
        editor.putString("default_port", editPort.getText().toString().trim());
        editor.putString("default_username", editUsername.getText().toString().trim());
        editor.putString(Constants.KEY_PRIVATE_KEY, loadedPrivateKey);
        
        int bpl = 2;
        try {
            bpl = Integer.parseInt(editButtonsPerLine.getText().toString().trim());
            if (bpl < 1) bpl = 1;
            if (bpl > 4) bpl = 4;
        } catch (NumberFormatException ignored) {}
        editor.putInt("buttons_per_line", bpl);
        
        int buttonCount = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE).getInt("button_count", 0);
        if (buttonCount > 0) {
            int selectedSlot = spinnerTileSlot.getSelectedItemPosition() + 1;
            editor.putInt("tile_slot", selectedSlot);
        }
        

        
        editor.apply();

        Toast.makeText(this, "Global defaults saved", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        try (InputStream is = getContentResolver().openInputStream(uri);
                             FileOutputStream fos = new FileOutputStream(new java.io.File(getFilesDir(), "qs_tile_icon.png"))) {
                            byte[] buffer = new byte[1024];
                            int read;
                            while ((read = is.read(buffer)) != -1) {
                                fos.write(buffer, 0, read);
                            }
                            updateCustomIconPreview();
                            // implicitly switch to custom
                            getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE).edit().putString("tile_icon", "custom").apply();
                            Toast.makeText(this, "Custom Tile Icon saved", Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            Toast.makeText(this, "Error saving icon", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            }
    );

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        filePickerLauncher.launch(intent);
    }

    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        try (InputStream is = getContentResolver().openInputStream(uri);
                             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                sb.append(line).append("\n");
                            }
                            loadedPrivateKey = sb.toString();
                            tvKeyStatus.setText("Private Key Loaded");
                            tvKeyStatus.setTextColor(0xFF00AA00);
                            Toast.makeText(this, "Key loaded successfully", Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            Toast.makeText(this, "Error reading key file", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            }
    );
}
