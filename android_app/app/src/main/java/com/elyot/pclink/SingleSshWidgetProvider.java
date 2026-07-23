package com.elyot.pclink;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class SingleSshWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_single_layout);

        android.content.SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);

        int slot = prefs.getInt("widget_" + appWidgetId + "_slot", -1);
        
        if (slot == -1) {
            views.setTextViewText(R.id.widgetSingleText, "Setup");
            views.setViewVisibility(R.id.widgetSingleIcon, android.view.View.GONE);
        } else {
            String lblKey = "lbl_" + slot;
            String defaultLbl = "Cmd " + slot;
            String lbl = prefs.getString(lblKey, defaultLbl).trim();
            if (lbl.isEmpty()) {
                views.setViewVisibility(R.id.widgetSingleText, android.view.View.GONE);
            } else {
                views.setViewVisibility(R.id.widgetSingleText, android.view.View.VISIBLE);
                views.setTextViewText(R.id.widgetSingleText, lbl);
            }

            java.io.File iconFile = new java.io.File(context.getFilesDir(), "icon_" + slot + ".png");
            if (iconFile.exists()) {
                views.setViewVisibility(R.id.widgetSingleIcon, android.view.View.VISIBLE);
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(iconFile.getAbsolutePath());
                if (bitmap != null) {
                    int MAX_SIZE = 150;
                    if (bitmap.getWidth() > MAX_SIZE || bitmap.getHeight() > MAX_SIZE) {
                        float ratio = Math.min((float) MAX_SIZE / bitmap.getWidth(), (float) MAX_SIZE / bitmap.getHeight());
                        android.graphics.Bitmap scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, Math.round(ratio * bitmap.getWidth()), Math.round(ratio * bitmap.getHeight()), true);
                        views.setImageViewBitmap(R.id.widgetSingleIcon, scaled);
                    } else {
                        views.setImageViewBitmap(R.id.widgetSingleIcon, bitmap);
                    }
                }
            } else {
                views.setViewVisibility(R.id.widgetSingleIcon, android.view.View.GONE);
            }

            Intent intent = new Intent(context, CommandReceiver.class);
            intent.setAction(CommandReceiver.ACTION_EXECUTE_CMD);
            intent.putExtra(CommandReceiver.EXTRA_SLOT, slot);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, appWidgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widgetSingleBtn, pendingIntent);
        }

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
    
    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        android.content.SharedPreferences.Editor editor = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE).edit();
        for (int appWidgetId : appWidgetIds) {
            editor.remove("widget_" + appWidgetId + "_slot");
        }
        editor.apply();
        super.onDeleted(context, appWidgetIds);
    }
}
