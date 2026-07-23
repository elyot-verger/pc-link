package com.elyot.pclink;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class SshTileService extends TileService {
    
    @Override
    public void onClick() {
        super.onClick();
        
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        int slot = prefs.getInt("tile_slot", 1);
        
        Intent intent = new Intent(this, CommandReceiver.class);
        intent.setAction(CommandReceiver.ACTION_EXECUTE_CMD);
        intent.putExtra(CommandReceiver.EXTRA_SLOT, slot);
        sendBroadcast(intent);
        
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        Tile tile = getQsTile();
        if (tile != null) {
            tile.setState(Tile.STATE_INACTIVE); // Indicates it's just a stateless button
            
            SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
            String tileIcon = prefs.getString("tile_icon", "auto");
            if (tileIcon.equals("auto")) {
                int slot = prefs.getInt("tile_slot", 1);
                java.io.File btnIconFile = new java.io.File(getFilesDir(), "icon_" + slot + ".png");
                if (btnIconFile.exists()) {
                    android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(btnIconFile.getAbsolutePath());
                    if (bitmap != null) {
                        tile.setIcon(android.graphics.drawable.Icon.createWithBitmap(bitmap));
                    } else {
                        tile.setIcon(android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_square));
                    }
                } else {
                    tile.setIcon(android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_square));
                }
            } else if (tileIcon.equals("custom")) {
                java.io.File iconFile = new java.io.File(getFilesDir(), "qs_tile_icon.png");
                if (iconFile.exists()) {
                    android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(iconFile.getAbsolutePath());
                    if (bitmap != null) {
                        tile.setIcon(android.graphics.drawable.Icon.createWithBitmap(bitmap));
                    } else {
                        tile.setIcon(android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_square));
                    }
                } else {
                    tile.setIcon(android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_square));
                }
            } else {
                int iconRes = R.drawable.ic_square; // Default fallback for unknown
                if (tileIcon.equals("power")) iconRes = R.drawable.ic_power;
                else if (tileIcon.equals("lock")) iconRes = R.drawable.ic_lock;
                else if (tileIcon.equals("unlock")) iconRes = R.drawable.ic_unlock;
                else if (tileIcon.equals("play")) iconRes = R.drawable.ic_play;
                
                tile.setIcon(android.graphics.drawable.Icon.createWithResource(this, iconRes));
            }
            
            tile.updateTile();
        }
    }
}
