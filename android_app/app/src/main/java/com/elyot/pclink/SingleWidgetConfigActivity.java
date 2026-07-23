package com.elyot.pclink;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.view.ViewGroup;

public class SingleWidgetConfigActivity extends Activity {

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setResult(RESULT_CANCELED);

        setContentView(R.layout.activity_widget_config);

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            appWidgetId = extras.getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        LinearLayout container = findViewById(R.id.configContainer);
        
        int buttonCount = prefs.getInt("button_count", 0);
        
        if (buttonCount == 0) {
            Button btn = new Button(this);
            btn.setText("No commands available. Please open the app and create one first.");
            btn.setEnabled(false);
            container.addView(btn);
            return;
        }

        for (int i = 0; i < buttonCount; i++) {
            int slot = i + 1;
            String cmd = prefs.getString("ssh_cmd_" + slot, "");
            
            // Skip empty/deleted buttons
            if (cmd.isEmpty()) continue;
            
            String lbl = prefs.getString("lbl_" + slot, "Command " + slot);
            
            Button btn = new Button(this);
            btn.setText(lbl);
            btn.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            
            btn.setOnClickListener(v -> selectSlot(slot));
            container.addView(btn);
        }
    }

    private void selectSlot(int slot) {
        SharedPreferences.Editor editor = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putInt("widget_" + appWidgetId + "_slot", slot);
        editor.apply();

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        SingleSshWidgetProvider.updateAppWidget(this, appWidgetManager, appWidgetId);

        Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_OK, resultValue);
        finish();
    }
}
