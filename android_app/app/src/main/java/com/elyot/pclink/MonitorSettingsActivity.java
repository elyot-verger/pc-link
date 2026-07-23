package com.elyot.pclink;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MonitorSettingsActivity extends AppCompatActivity {
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private TopologyView topologyView;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtils.applyTheme(this, true);
        setContentView(R.layout.activity_monitor_settings);

        tvStatus = findViewById(R.id.tvStatus);
        topologyView = findViewById(R.id.topologyView);
        
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        String themeColorHex = prefs.getString("pref_theme_color", "#C01C28");
        topologyView.setThemeColor(android.graphics.Color.parseColor(themeColorHex));
        
        Button btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());
        
        Button btnSave = findViewById(R.id.btnSaveTopology);
        btnSave.setOnClickListener(v -> saveTopology());

        fetchTopology();
    }

    private String getBaseUrl() {
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        String ip = prefs.getString("music_target_ip", "");
        if (ip.isEmpty()) {
            ip = prefs.getString(Constants.KEY_HOST, "");
        }
        if (ip.isEmpty()) return null;
        return "http://" + ip + ":5000";
    }

    private void fetchTopology() {
        String baseUrl = getBaseUrl();
        if (baseUrl == null) {
            tvStatus.setText("IP is missing");
            if (topologyView != null) topologyView.setDebugString("IP is missing in settings");
            return;
        }
        tvStatus.setText("Loading...");

        executorService.execute(() -> {
            try {
                URL url = new URL(baseUrl + "/topology_list");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setUseCaches(false);
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);

                if (conn.getResponseCode() == 200) {
                    InputStream is = conn.getInputStream();
                    Scanner s = new Scanner(is).useDelimiter("\\A");
                    String res = s.hasNext() ? s.next() : "";
                    is.close();

                    JSONObject json = new JSONObject(res);
                    android.util.Log.d("TopologyList", "Response: " + res);
                    JSONArray mons = json.optJSONArray("monitors");
                    List<MonitorConfig> parsed = new ArrayList<>();
                    if (mons != null) {
                        for (int i = 0; i < mons.length(); i++) {
                            JSONObject m = mons.optJSONObject(i);
                            MonitorConfig config = new MonitorConfig();
                            config.id = m.optString("id");
                            config.name = m.optString("name");
                            config.isActive = m.optBoolean("is_active", true);
                            config.isPrimary = m.optBoolean("is_primary", false);
                            config.x = m.optInt("x", 0);
                            config.y = m.optInt("y", 0);
                            config.width = m.optInt("width", 1920);
                            config.height = m.optInt("height", 1080);
                            parsed.add(config);
                        }
                    }
                    
                    mainHandler.post(() -> {
                        tvStatus.setVisibility(android.view.View.GONE);
                        topologyView.setDebugString("");
                        topologyView.setMonitors(parsed);
                    });
                } else {
                    int code = conn.getResponseCode();
                    mainHandler.post(() -> {
                        tvStatus.setVisibility(android.view.View.VISIBLE);
                        tvStatus.setText("Server error: " + code);
                        topologyView.setDebugString("Error: " + code);
                    });
                }
                conn.disconnect();
            } catch (Exception e) {
                mainHandler.post(() -> {
                    tvStatus.setVisibility(android.view.View.VISIBLE);
                    tvStatus.setText("Failed to fetch: " + e.getMessage());
                    topologyView.setDebugString("Exc: " + e.getMessage());
                });
            }
        });
    }

    private void saveTopology() {
        if (topologyView == null) return;
        String baseUrl = getBaseUrl();
        if (baseUrl == null) return;
        
        tvStatus.setText("Saving...");
        
        List<MonitorConfig> configs = topologyView.getSnappedMonitors();
        
        executorService.execute(() -> {
            try {
                URL url = new URL(baseUrl + "/apply_topology");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; utf-8");
                conn.setDoOutput(true);
                
                JSONArray arr = new JSONArray();
                for (MonitorConfig c : configs) {
                    JSONObject o = new JSONObject();
                    o.put("id", c.id);
                    o.put("name", c.name);
                    o.put("is_active", c.isActive);
                    o.put("is_primary", c.isPrimary);
                    o.put("x", c.x);
                    o.put("y", c.y);
                    arr.put(o);
                }
                
                JSONObject payload = new JSONObject();
                payload.put("monitors", arr);
                
                try(OutputStream os = conn.getOutputStream()) {
                    byte[] input = payload.toString().getBytes("utf-8");
                    os.write(input, 0, input.length);
                }
                
                if (conn.getResponseCode() == 200) {
                    mainHandler.post(() -> {
                        Toast.makeText(this, "Topology applied!", Toast.LENGTH_SHORT).show();
                        tvStatus.setText("");
                        fetchTopology(); // refresh
                    });
                } else {
                    mainHandler.post(() -> tvStatus.setText("Error applying topology"));
                }
            } catch(Exception e) {
                mainHandler.post(() -> tvStatus.setText("Failed to save: " + e.getMessage()));
            }
        });
    }

    static class MonitorConfig {
        String id;
        String name;
        boolean isActive;
        boolean isPrimary;
        int x, y, width, height;
    }
}
