package com.elyot.pclink;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class ButtonSettingsActivity extends AppCompatActivity {

    private int slot;
    private EditText editLabel, editCmd, editHost, editPort, editUsername;
    private Switch switchVpn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_button_settings);
        ThemeUtils.applyTheme(this, true);

        slot = getIntent().getIntExtra("slot", 1);
        setTitle("Configure Button " + slot);

        TextView tvTitle = findViewById(R.id.tvTitle);
        tvTitle.setText("Command " + slot + " Settings");

        editLabel = findViewById(R.id.editLabel);
        editCmd = findViewById(R.id.editCmd);
        editHost = findViewById(R.id.editHost);
        editPort = findViewById(R.id.editPort);
        editUsername = findViewById(R.id.editUsername);
        switchVpn = findViewById(R.id.switchVpn);

        Button btnPickIcon = findViewById(R.id.btnPickIcon);
        Button btnClearIcon = findViewById(R.id.btnClearIcon);
        Button btnDelete = findViewById(R.id.btnDelete);
        Button btnSave = findViewById(R.id.btnSave);

        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        
        editLabel.setText(prefs.getString("lbl_" + slot, "Cmd " + slot));
        editCmd.setText(prefs.getString("ssh_cmd_" + slot, ""));
        editHost.setText(prefs.getString("host_" + slot, ""));
        editPort.setText(prefs.getString("port_" + slot, ""));
        editUsername.setText(prefs.getString("username_" + slot, ""));
        switchVpn.setChecked(prefs.getBoolean("require_vpn_" + slot, true));

        // Hints to show defaults
        editHost.setHint("Default: " + prefs.getString("default_host", ""));
        editPort.setHint("Default: " + prefs.getString("default_port", "22"));
        editUsername.setHint("Default: " + prefs.getString("default_username", ""));

        btnPickIcon.setOnClickListener(v -> openImagePicker());
        btnClearIcon.setOnClickListener(v -> clearImage());
        btnDelete.setOnClickListener(v -> deleteButton());
        btnSave.setOnClickListener(v -> saveSettings());
    }

    private void saveSettings() {
        SharedPreferences.Editor editor = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putString("lbl_" + slot, editLabel.getText().toString().trim());
        editor.putString("ssh_cmd_" + slot, editCmd.getText().toString().trim());
        editor.putString("host_" + slot, editHost.getText().toString().trim());
        editor.putString("port_" + slot, editPort.getText().toString().trim());
        editor.putString("username_" + slot, editUsername.getText().toString().trim());
        editor.putBoolean("require_vpn_" + slot, switchVpn.isChecked());
        editor.apply();

        Toast.makeText(this, "Button settings saved", Toast.LENGTH_SHORT).show();

        // Broadcast to update widgets
        Intent intent = new Intent(this, SshWidgetProvider.class);
        intent.setAction(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        int[] ids = android.appwidget.AppWidgetManager.getInstance(getApplication()).getAppWidgetIds(new android.content.ComponentName(getApplication(), SshWidgetProvider.class));
        intent.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        sendBroadcast(intent);

        Intent intentSingle = new Intent(this, SingleSshWidgetProvider.class);
        intentSingle.setAction(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        int[] singleIds = android.appwidget.AppWidgetManager.getInstance(getApplication()).getAppWidgetIds(new android.content.ComponentName(getApplication(), SingleSshWidgetProvider.class));
        intentSingle.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, singleIds);
        sendBroadcast(intentSingle);

        finish();
    }

    private void deleteButton() {
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        int buttonCount = prefs.getInt("button_count", 0);
        
        if (slot <= buttonCount) {
            // Shift all subsequent buttons down by 1
            for (int i = slot; i < buttonCount; i++) {
                int next = i + 1;
                editor.putString("lbl_" + i, prefs.getString("lbl_" + next, ""));
                editor.putString("ssh_cmd_" + i, prefs.getString("ssh_cmd_" + next, ""));
                editor.putString("host_" + i, prefs.getString("host_" + next, ""));
                editor.putString("port_" + i, prefs.getString("port_" + next, ""));
                editor.putString("username_" + i, prefs.getString("username_" + next, ""));
                editor.putBoolean("require_vpn_" + i, prefs.getBoolean("require_vpn_" + next, true));
                
                File nextIcon = new File(getFilesDir(), "icon_" + next + ".png");
                File currIcon = new File(getFilesDir(), "icon_" + i + ".png");
                if (nextIcon.exists()) {
                    nextIcon.renameTo(currIcon);
                } else if (currIcon.exists()) {
                    currIcon.delete();
                }
            }
            
            // Remove the last button's data
            editor.remove("lbl_" + buttonCount);
            editor.remove("ssh_cmd_" + buttonCount);
            editor.remove("host_" + buttonCount);
            editor.remove("port_" + buttonCount);
            editor.remove("username_" + buttonCount);
            editor.remove("require_vpn_" + buttonCount);
            
            File lastIcon = new File(getFilesDir(), "icon_" + buttonCount + ".png");
            if (lastIcon.exists()) lastIcon.delete();
            
            editor.putInt("button_count", buttonCount - 1);
            
            // Adjust Quick Settings Tile selection if necessary
            int tileSlot = prefs.getInt("tile_slot", 1);
            if (tileSlot == slot) {
                editor.putInt("tile_slot", 1); // Reset to first button if its bound button was deleted
            } else if (tileSlot > slot) {
                editor.putInt("tile_slot", tileSlot - 1); // Shift tile slot down
            }
            
            editor.apply();
            
            Toast.makeText(this, "Button deleted", Toast.LENGTH_SHORT).show();
            
            // Broadcast to update widgets
            Intent intent = new Intent(this, SshWidgetProvider.class);
            intent.setAction(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            int[] ids = android.appwidget.AppWidgetManager.getInstance(getApplication()).getAppWidgetIds(new android.content.ComponentName(getApplication(), SshWidgetProvider.class));
            intent.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
            sendBroadcast(intent);

            Intent intentSingle = new Intent(this, SingleSshWidgetProvider.class);
            intentSingle.setAction(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            int[] singleIds = android.appwidget.AppWidgetManager.getInstance(getApplication()).getAppWidgetIds(new android.content.ComponentName(getApplication(), SingleSshWidgetProvider.class));
            intentSingle.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, singleIds);
            sendBroadcast(intentSingle);
        }
        
        finish();
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void clearImage() {
        File file = new File(getFilesDir(), "icon_" + slot + ".png");
        if (file.exists()) file.delete();
        Toast.makeText(this, "Icon cleared", Toast.LENGTH_SHORT).show();
    }

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        try (InputStream is = getContentResolver().openInputStream(uri);
                             FileOutputStream fos = new FileOutputStream(new File(getFilesDir(), "icon_" + slot + ".png"))) {
                            byte[] buffer = new byte[1024];
                            int read;
                            while ((read = is.read(buffer)) != -1) {
                                fos.write(buffer, 0, read);
                            }
                            Toast.makeText(this, "Icon saved", Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            Toast.makeText(this, "Error saving icon", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            }
    );
}
