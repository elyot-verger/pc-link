package com.elyot.pclink;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;

public class MusicTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        boolean isRunning = isServiceRunning(MusicNotificationService.class);
        android.content.SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);

        if (isRunning) {
            Intent serviceIntent = new Intent(this, MusicNotificationService.class);
            stopService(serviceIntent);
            prefs.edit().putBoolean(Constants.PREF_NOTIF_ENABLED, false).apply();
        } else {
            if (!NetworkUtils.isVpnActive(this)) {
                NetworkUtils.activateTailscale(this);
                Toast.makeText(this, "Démarrage de Tailscale...", Toast.LENGTH_SHORT).show();
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Intent intent = new Intent(this, HomeActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivityAndCollapse(intent);
                Toast.makeText(this, "Please enable notification permission in the app first", Toast.LENGTH_LONG).show();
                return;
            }
            
            Intent serviceIntent = new Intent(this, MusicNotificationService.class);
            ContextCompat.startForegroundService(this, serviceIntent);
            prefs.edit().putBoolean(Constants.PREF_NOTIF_ENABLED, true).apply();
        }

        Tile tile = getQsTile();
        if (tile != null) {
            tile.setState(!isRunning ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            tile.updateTile();
        }
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile != null) {
            boolean isRunning = isServiceRunning(MusicNotificationService.class);
            tile.setState(isRunning ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            tile.updateTile();
        }
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (serviceClass.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        }
        return false;
    }
}
