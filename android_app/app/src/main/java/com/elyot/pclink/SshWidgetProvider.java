package com.elyot.pclink;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class SshWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_CMD_1 = "com.elyot.pclink.CMD_1";
    public static final String ACTION_CMD_2 = "com.elyot.pclink.CMD_2";
    public static final String ACTION_CMD_3 = "com.elyot.pclink.CMD_3";
    public static final String ACTION_CMD_4 = "com.elyot.pclink.CMD_4";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);

        android.content.SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);

        setupButton(context, views, prefs, R.id.widgetBtn1, R.id.widgetText1, R.id.widgetIcon1, "lbl_1", "ssh_cmd_1", "Cmd 1", 1);
        setupButton(context, views, prefs, R.id.widgetBtn2, R.id.widgetText2, R.id.widgetIcon2, "lbl_2", "ssh_cmd_2", "Cmd 2", 2);
        setupButton(context, views, prefs, R.id.widgetBtn3, R.id.widgetText3, R.id.widgetIcon3, "lbl_3", "ssh_cmd_3", "Cmd 3", 3);
        setupButton(context, views, prefs, R.id.widgetBtn4, R.id.widgetText4, R.id.widgetIcon4, "lbl_4", "ssh_cmd_4", "Cmd 4", 4);

        // Keep rows visible so the 2x2 grid remains stable
        views.setViewVisibility(R.id.widgetRow1, android.view.View.VISIBLE);
        views.setViewVisibility(R.id.widgetRow2, android.view.View.VISIBLE);

        views.setOnClickPendingIntent(R.id.widgetBtn1, getPendingIntent(context, ACTION_CMD_1, 1));
        views.setOnClickPendingIntent(R.id.widgetBtn2, getPendingIntent(context, ACTION_CMD_2, 2));
        views.setOnClickPendingIntent(R.id.widgetBtn3, getPendingIntent(context, ACTION_CMD_3, 3));
        views.setOnClickPendingIntent(R.id.widgetBtn4, getPendingIntent(context, ACTION_CMD_4, 4));

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private static boolean setupButton(Context context, RemoteViews views, android.content.SharedPreferences prefs, int btnId, int textId, int iconId, String lblKey, String cmdKey, String defaultLbl, int slot) {
        String cmd = prefs.getString(cmdKey, "").trim();
        if (cmd.isEmpty()) {
            // Use INVISIBLE instead of GONE so it leaves an empty hole in the layout
            views.setViewVisibility(btnId, android.view.View.INVISIBLE);
            return false;
        }

        views.setViewVisibility(btnId, android.view.View.VISIBLE);

        String lbl = prefs.getString(lblKey, defaultLbl).trim();
        if (lbl.isEmpty()) {
            views.setViewVisibility(textId, android.view.View.GONE);
        } else {
            views.setViewVisibility(textId, android.view.View.VISIBLE);
            views.setTextViewText(textId, lbl);
        }

        java.io.File iconFile = new java.io.File(context.getFilesDir(), "icon_" + slot + ".png");
        if (iconFile.exists()) {
            views.setViewVisibility(iconId, android.view.View.VISIBLE);
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(iconFile.getAbsolutePath());
            if (bitmap != null) {
                int MAX_SIZE = 150;
                if (bitmap.getWidth() > MAX_SIZE || bitmap.getHeight() > MAX_SIZE) {
                    float ratio = Math.min((float) MAX_SIZE / bitmap.getWidth(), (float) MAX_SIZE / bitmap.getHeight());
                    android.graphics.Bitmap scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, Math.round(ratio * bitmap.getWidth()), Math.round(ratio * bitmap.getHeight()), true);
                    views.setImageViewBitmap(iconId, scaled);
                } else {
                    views.setImageViewBitmap(iconId, bitmap);
                }
            }
        } else {
            views.setViewVisibility(iconId, android.view.View.GONE);
        }
        return true;
    }

    private static PendingIntent getPendingIntent(Context context, String action, int requestCode) {
        Intent intent = new Intent(context, CommandReceiver.class);
        intent.setAction(action);
        return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
