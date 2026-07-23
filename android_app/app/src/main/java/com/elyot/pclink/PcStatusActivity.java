package com.elyot.pclink;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import java.util.List;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PcStatusActivity extends AppCompatActivity {

    private ImageView ivSessionState;
    private ImageView ivDesktopEnv;
    private ImageView ivNetwork;
    private ImageView ivBluetooth;
    private ImageView ivVolumeTop;
    private TextView tvVolumePct;
    private TextView tvBatteryState;
    private BatteryIconView ivBatteryState;
    private View flVolumePopup;
    private SeekBar sbPcVolumeVertical;
    private TextView tvPcError;

    private View vPowerDot1;
    private View vPowerDot2;
    private View vPowerDot3;
    private LinearLayout llBatteryContainer;
    private LinearLayout llPowerPopup;
    private LinearLayout llNetworkPopup;
    private ImageButton btnPowerPerf;
    private ImageButton btnPowerBal;
    private ImageButton btnPowerSav;
    private ImageButton btnNetworkWifiToggle;
    private String currentNetworkType = "none";
    private int themeColor = android.graphics.Color.parseColor("#C01C28");

    private LinearLayout llWifiList;
    private TextView tvWifiStatus;
    private ImageButton btnRefreshWifi;

    private LinearLayout llBluetoothList;
    private TextView tvBluetoothStatus;
    private ImageButton btnRefreshBluetooth;

    private LinearLayout llMonitorList;
    private TextView tvMonitorStatus;
    private ImageButton btnRefreshMonitors;
    private ImageButton btnMonitorSettings;

    private Timer pollTimer;
    private int pollingIntervalMs = 3000;
    private boolean isUserAdjustingVolume = false;
    private boolean isMuted = false;
    private float lastUnmutedVolume = 0.5f;

    private boolean lastWifiState = false;
    private boolean lastBluetoothState = false;
    private boolean firstPoll = true;
    private String lastConnectedWifiSsid = "";
    private int lastConnectedBtCount = -1;
    private int lastMonitorCount = -1;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pc_status);
        ThemeUtils.applyTheme(this, false);

        ivSessionState = findViewById(R.id.ivSessionState);
        ivDesktopEnv = findViewById(R.id.ivDesktopEnv);
        ivNetwork = findViewById(R.id.ivNetwork);
        ivBluetooth = findViewById(R.id.ivBluetooth);
        ivVolumeTop = findViewById(R.id.ivVolumeTop);
        tvVolumePct = findViewById(R.id.tvVolumePct);
        tvBatteryState = findViewById(R.id.tvBatteryState);
        ivBatteryState = findViewById(R.id.ivBatteryState);
        flVolumePopup = findViewById(R.id.flVolumePopup);
        sbPcVolumeVertical = findViewById(R.id.sbPcVolumeVertical);
        tvPcError = findViewById(R.id.tvPcError);

        llWifiList = findViewById(R.id.llWifiList);
        tvWifiStatus = findViewById(R.id.tvWifiStatus);
        btnRefreshWifi = findViewById(R.id.btnRefreshWifi);

        llBluetoothList = findViewById(R.id.llBluetoothList);
        tvBluetoothStatus = findViewById(R.id.tvBluetoothStatus);
        btnRefreshBluetooth = findViewById(R.id.btnRefreshBluetooth);

        llMonitorList = findViewById(R.id.llMonitorList);
        tvMonitorStatus = findViewById(R.id.tvMonitorStatus);
        btnRefreshMonitors = findViewById(R.id.btnRefreshMonitors);
        btnMonitorSettings = findViewById(R.id.btnMonitorSettings);

        if (btnMonitorSettings != null) {
            btnMonitorSettings.setOnClickListener(v -> {
                startActivity(new Intent(PcStatusActivity.this, MonitorSettingsActivity.class));
            });
        }

        ImageButton btnSettings = findViewById(R.id.btnPcSettings);
        btnSettings.setOnClickListener(v -> {
            startActivity(new Intent(this, PcSettingsActivity.class));
        });

        ivSessionState.setOnClickListener(v -> sendCommand("lock_pc"));

        ivVolumeTop.setOnClickListener(v -> {
            if (flVolumePopup.getVisibility() == View.VISIBLE) {
                flVolumePopup.setVisibility(View.GONE);
            } else {
                flVolumePopup.setVisibility(View.VISIBLE);
            }
        });

        ivVolumeTop.setOnLongClickListener(v -> {
            sendCommand("pc_volume/mute");
            isMuted = !isMuted;
            if (isMuted) {
                ivVolumeTop.setImageResource(R.drawable.ic_volume_off);
            } else {
                ivVolumeTop.setImageResource(R.drawable.ic_volume_up);
            }
            return true;
        });

        sbPcVolumeVertical.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {}
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserAdjustingVolume = true;
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isUserAdjustingVolume = false;
                float vol = seekBar.getProgress() / 100f;
                sendCommand("pc_volume/" + vol);
            }
        });

        btnRefreshWifi.setOnClickListener(v -> {
            if (lastWifiState) fetchWifiList();
        });

        btnRefreshBluetooth.setOnClickListener(v -> {
            if (lastBluetoothState) fetchBluetoothList();
        });

        btnRefreshMonitors.setOnClickListener(v -> {
            fetchMonitorList();
        });

        ivBluetooth.setOnClickListener(v -> sendCommand("pc_bluetooth_toggle"));

        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        String colorStr = prefs.getString("pref_theme_color", "#C01C28");
        try {
            themeColor = android.graphics.Color.parseColor(colorStr);
            btnRefreshWifi.setColorFilter(themeColor, android.graphics.PorterDuff.Mode.SRC_IN);
            btnRefreshBluetooth.setColorFilter(themeColor, android.graphics.PorterDuff.Mode.SRC_IN);
            btnRefreshMonitors.setColorFilter(themeColor, android.graphics.PorterDuff.Mode.SRC_IN);
        } catch (Exception e) {}

        vPowerDot1 = findViewById(R.id.vPowerDot1);
        vPowerDot2 = findViewById(R.id.vPowerDot2);
        vPowerDot3 = findViewById(R.id.vPowerDot3);
        llBatteryContainer = findViewById(R.id.llBatteryContainer);
        llPowerPopup = findViewById(R.id.llPowerPopup);
        llNetworkPopup = findViewById(R.id.llNetworkPopup);

        ivNetwork.setOnClickListener(v -> {
            if (!"wifi".equals(currentNetworkType)) {
                if (llNetworkPopup.getVisibility() == View.VISIBLE) {
                    llNetworkPopup.setVisibility(View.GONE);
                } else {
                    llNetworkPopup.setVisibility(View.VISIBLE);
                }
            }
        });
        
        btnNetworkWifiToggle = findViewById(R.id.btnNetworkWifiToggle);
        if (btnNetworkWifiToggle != null) {
            btnNetworkWifiToggle.setOnClickListener(v -> {
                sendCommand("pc_wifi_toggle");
                if (llNetworkPopup != null) llNetworkPopup.setVisibility(View.GONE);
            });
        }

        llBatteryContainer.setOnClickListener(v -> {
            if (llPowerPopup.getVisibility() == View.VISIBLE) {
                llPowerPopup.setVisibility(View.GONE);
            } else {
                llPowerPopup.setVisibility(View.VISIBLE);
            }
        });
        
        btnPowerPerf = findViewById(R.id.btnPowerPerformance);
        btnPowerBal = findViewById(R.id.btnPowerBalanced);
        btnPowerSav = findViewById(R.id.btnPowerSaver);
        
        btnPowerPerf.setOnClickListener(v -> {
            sendCommand("pc_power_profile/performance");
            llPowerPopup.setVisibility(View.GONE);
        });
        btnPowerBal.setOnClickListener(v -> {
            sendCommand("pc_power_profile/balanced");
            llPowerPopup.setVisibility(View.GONE);
        });
        btnPowerSav.setOnClickListener(v -> {
            sendCommand("pc_power_profile/power-saver");
            llPowerPopup.setVisibility(View.GONE);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        ThemeUtils.applyTheme(this, false);

        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        int rate = prefs.getInt("pc_refresh_rate", 3000);
        if (rate < 50) rate *= 1000;
        pollingIntervalMs = rate;
        
        restartPollTimer();
        fetchMonitorList();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPolling();
    }

    private void restartPollTimer() {
        stopPolling();
        pollTimer = new Timer();
        pollTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                pollStatus();
            }
        }, 0, pollingIntervalMs);
    }

    private void stopPolling() {
        if (pollTimer != null) {
            pollTimer.cancel();
            pollTimer = null;
        }
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

    private void pollStatus() {
        String baseUrl = getBaseUrl();
        if (baseUrl == null) {
            mainHandler.post(() -> tvPcError.setText("Please configure target IP in settings"));
            return;
        }

        String urlString = baseUrl + "/pc_status";

        executorService.execute(() -> {
            try {
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(2000);
                connection.setReadTimeout(2000);

                if (connection.getResponseCode() == 200) {
                    InputStream is = connection.getInputStream();
                    Scanner s = new Scanner(is).useDelimiter("\\A");
                    String result = s.hasNext() ? s.next() : "";
                    is.close();

                    JSONObject json = new JSONObject(result);
                    double volume = json.optDouble("volume", 0.0);
                    boolean serverIsMuted = json.optBoolean("is_muted", false);
                    String batState = json.optString("battery_state", "unknown");
                    String batPct = json.optString("battery_percentage", "0%");
                    boolean locked = json.optBoolean("screen_locked", false);
                    String desktopEnv = json.optString("desktop_env", "Unknown");
                    String networkType = json.optString("network_type", "none");
                    currentNetworkType = networkType;
                    boolean bluetoothOn = json.optBoolean("bluetooth_on", false);
                    String powerProfile = json.optString("power_profile", "balanced");

                    mainHandler.post(() -> {
                        tvPcError.setText("");
                        
                        if (desktopEnv.equalsIgnoreCase("i3")) {
                            if (ivDesktopEnv != null) ivDesktopEnv.setImageResource(R.drawable.ic_i3);
                        } else if (desktopEnv.equalsIgnoreCase("GNOME")) {
                            if (ivDesktopEnv != null) ivDesktopEnv.setImageResource(R.drawable.ic_gnome);
                        } else {
                            if (ivDesktopEnv != null) ivDesktopEnv.setImageResource(R.drawable.ic_computer);
                        }

                        if (vPowerDot1 != null && vPowerDot2 != null && vPowerDot3 != null) {
                            vPowerDot1.setBackgroundColor(android.graphics.Color.WHITE);
                            vPowerDot2.setBackgroundColor(android.graphics.Color.WHITE);
                            vPowerDot3.setBackgroundColor(android.graphics.Color.WHITE);
                            
                            int grayColor = android.graphics.Color.parseColor("#888888");
                            if (btnPowerPerf != null && btnPowerBal != null && btnPowerSav != null) {
                                btnPowerPerf.setColorFilter(grayColor, android.graphics.PorterDuff.Mode.SRC_IN);
                                btnPowerBal.setColorFilter(grayColor, android.graphics.PorterDuff.Mode.SRC_IN);
                                btnPowerSav.setColorFilter(grayColor, android.graphics.PorterDuff.Mode.SRC_IN);
                            }
                            
                            if (powerProfile.equals("power-saver")) {
                                vPowerDot1.setBackgroundColor(themeColor);
                                if (btnPowerSav != null) btnPowerSav.setColorFilter(themeColor, android.graphics.PorterDuff.Mode.SRC_IN);
                            } else if (powerProfile.equals("balanced")) {
                                vPowerDot1.setBackgroundColor(themeColor);
                                vPowerDot2.setBackgroundColor(themeColor);
                                if (btnPowerBal != null) btnPowerBal.setColorFilter(themeColor, android.graphics.PorterDuff.Mode.SRC_IN);
                            } else if (powerProfile.equals("performance")) {
                                vPowerDot1.setBackgroundColor(themeColor);
                                vPowerDot2.setBackgroundColor(themeColor);
                                vPowerDot3.setBackgroundColor(themeColor);
                                if (btnPowerPerf != null) btnPowerPerf.setColorFilter(themeColor, android.graphics.PorterDuff.Mode.SRC_IN);
                            }
                        }
                        
                        if (locked) ivSessionState.setImageResource(R.drawable.ic_lock);
                        else ivSessionState.setImageResource(R.drawable.ic_lock_open);

                        boolean wifiOn = json.optBoolean("wifi_on", false);
                        
                        if (!networkType.equals("none")) {
                            ivNetwork.setVisibility(View.VISIBLE);
                            if (!"wifi".equals(networkType)) {
                                ivNetwork.setImageResource(R.drawable.ic_ethernet);
                            } else {
                                ivNetwork.setImageResource(R.drawable.ic_gnome_wifi);
                            }
                        } else {
                            ivNetwork.setVisibility(View.GONE);
                        }
                        
                        if (btnNetworkWifiToggle != null) {
                            if (wifiOn) {
                                btnNetworkWifiToggle.setImageResource(R.drawable.ic_gnome_wifi);
                            } else {
                                btnNetworkWifiToggle.setImageResource(R.drawable.ic_gnome_wifi_disabled);
                            }
                        }

                        String currentWifiSsid = json.optString("connected_wifi_ssid", "");
                        if (firstPoll || wifiOn != lastWifiState || !currentWifiSsid.equals(lastConnectedWifiSsid)) {
                            lastWifiState = wifiOn;
                            lastConnectedWifiSsid = currentWifiSsid;
                            if (wifiOn) fetchWifiList();
                            else {
                                tvWifiStatus.setVisibility(View.VISIBLE);
                                tvWifiStatus.setText("Wi-Fi not activated");
                                llWifiList.removeAllViews();
                                llWifiList.addView(tvWifiStatus);
                            }
                        }

                        ivBluetooth.setVisibility(View.VISIBLE);
                        if (bluetoothOn) {
                            ivBluetooth.setImageResource(R.drawable.ic_bluetooth);
                        } else {
                            ivBluetooth.setImageResource(R.drawable.ic_bluetooth_disabled);
                        }

                        int currentBtCount = json.optInt("connected_bluetooth_count", 0);
                        if (firstPoll || bluetoothOn != lastBluetoothState || currentBtCount != lastConnectedBtCount) {
                            lastBluetoothState = bluetoothOn;
                            lastConnectedBtCount = currentBtCount;
                            if (bluetoothOn) fetchBluetoothList();
                            else {
                                tvBluetoothStatus.setVisibility(View.VISIBLE);
                                tvBluetoothStatus.setText("Bluetooth not activated");
                                llBluetoothList.removeAllViews();
                                llBluetoothList.addView(tvBluetoothStatus);
                            }
                        }
                        firstPoll = false;

                        int currentMonitorCount = json.optInt("connected_monitor_count", -1);
                        if (currentMonitorCount != -1 && currentMonitorCount != lastMonitorCount) {
                            if (lastMonitorCount != -1) {
                                fetchMonitorList();
                            }
                            lastMonitorCount = currentMonitorCount;
                        }

                        tvBatteryState.setText(batPct);
                        SharedPreferences prefsLocal = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
                        boolean showBatteryPct = prefsLocal.getBoolean("show_battery_pct", true);
                        tvBatteryState.setVisibility(showBatteryPct ? View.VISIBLE : View.GONE);

                        String lowerBatState = batState.toLowerCase();
                        boolean isCharging = lowerBatState.contains("charging") && !lowerBatState.contains("discharging");
                        int pct = 50;
                        try {
                            pct = Integer.parseInt(batPct.replace("%", "").trim());
                        } catch (Exception e) {}
                        ivBatteryState.setBaseColor(tvBatteryState.getCurrentTextColor());
                        ivBatteryState.setBatteryLevel(pct, isCharging);

                        boolean showVolumePct = prefsLocal.getBoolean("show_volume_pct", true);
                        if (!isUserAdjustingVolume) {
                            int volInt = (int)(volume * 100);
                            sbPcVolumeVertical.setProgress(volInt);
                            tvVolumePct.setText(volInt + "%");
                            tvVolumePct.setVisibility(showVolumePct ? View.VISIBLE : View.GONE);
                            
                            isMuted = serverIsMuted || volume <= 0.01f;
                            if (isMuted) {
                                ivVolumeTop.setImageResource(R.drawable.ic_volume_off);
                            } else {
                                lastUnmutedVolume = (float) volume;
                                if (volume < 0.33f) {
                                    ivVolumeTop.setImageResource(R.drawable.ic_volume_mute);
                                } else if (volume < 0.66f) {
                                    ivVolumeTop.setImageResource(R.drawable.ic_volume_down);
                                } else {
                                    ivVolumeTop.setImageResource(R.drawable.ic_volume_up);
                                }
                            }
                        }
                    });
                }
                connection.disconnect();
            } catch (Exception e) {
                mainHandler.post(() -> tvPcError.setText("Error: Cannot reach server. " + e.getMessage()));
            }
        });
    }

    private void fetchWifiList() {
        String baseUrl = getBaseUrl();
        if (baseUrl == null) return;

        mainHandler.post(() -> {
            tvWifiStatus.setVisibility(View.VISIBLE);
            tvWifiStatus.setText("Loading...");
            llWifiList.removeAllViews();
            llWifiList.addView(tvWifiStatus);
        });

        executorService.execute(() -> {
            try {
                URL url = new URL(baseUrl + "/wifi_list");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);

                if (conn.getResponseCode() == 200) {
                    InputStream is = conn.getInputStream();
                    Scanner s = new Scanner(is).useDelimiter("\\A");
                    String res = s.hasNext() ? s.next() : "";
                    is.close();

                    JSONObject json = new JSONObject(res);
                    JSONArray nets = json.optJSONArray("networks");
                    
                    mainHandler.post(() -> {
                        llWifiList.removeAllViews();
                        if (nets != null && nets.length() > 0) {
                            for (int i = 0; i < nets.length(); i++) {
                                JSONObject n = nets.optJSONObject(i);
                                if (n != null) {
                                    LinearLayout row = new LinearLayout(PcStatusActivity.this);
                                    row.setOrientation(LinearLayout.HORIZONTAL);
                                    row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                                    row.setPadding(0, 8, 0, 8);
                                    LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                                            LinearLayout.LayoutParams.MATCH_PARENT, 
                                            LinearLayout.LayoutParams.WRAP_CONTENT);
                                    rowParams.setMargins(0, 8, 0, 8); // Margin between connections
                                    row.setLayoutParams(rowParams);

                                    int signal = n.optInt("signal", 100);
                                    ImageView icon = new ImageView(PcStatusActivity.this);
                                    if (signal > 75) {
                                        icon.setImageResource(R.drawable.ic_wifi_4);
                                    } else if (signal > 50) {
                                        icon.setImageResource(R.drawable.ic_wifi_3);
                                    } else if (signal > 25) {
                                        icon.setImageResource(R.drawable.ic_wifi_2);
                                    } else {
                                        icon.setImageResource(R.drawable.ic_wifi_1);
                                    }
                                    int size = (int) (20 * getResources().getDisplayMetrics().density);
                                    LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(size, size);
                                    iconParams.setMarginEnd((int) (12 * getResources().getDisplayMetrics().density));
                                    icon.setLayoutParams(iconParams);
                                    
                                    icon.setColorFilter(tvBatteryState.getCurrentTextColor(), android.graphics.PorterDuff.Mode.SRC_IN);

                                    TextView tv = new TextView(PcStatusActivity.this);
                                    tv.setText(n.optString("ssid"));
                                    tv.setTextSize(14f);
                                    tv.setTextColor(tvBatteryState.getCurrentTextColor());
                                    
                                    row.addView(icon);
                                    row.addView(tv);

                                    if (n.optBoolean("connected", false)) {
                                        ImageView checkIcon = new ImageView(PcStatusActivity.this);
                                        checkIcon.setImageResource(R.drawable.ic_check);
                                        LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(size, size);
                                        checkParams.setMarginStart((int) (8 * getResources().getDisplayMetrics().density));
                                        checkIcon.setLayoutParams(checkParams);
                                        checkIcon.setColorFilter(tvBatteryState.getCurrentTextColor(), android.graphics.PorterDuff.Mode.SRC_IN);
                                        row.addView(checkIcon);
                                    }

                                    llWifiList.addView(row);
                                }
                            }
                        } else {
                            tvWifiStatus.setText("No networks found");
                            llWifiList.addView(tvWifiStatus);
                        }
                    });
                }
                conn.disconnect();
            } catch (Exception e) {
                mainHandler.post(() -> {
                    tvWifiStatus.setText("Failed to load Wi-Fi");
                    llWifiList.removeAllViews();
                    llWifiList.addView(tvWifiStatus);
                });
            }
        });
    }

    private void fetchBluetoothList() {
        String baseUrl = getBaseUrl();
        if (baseUrl == null) return;

        mainHandler.post(() -> {
            tvBluetoothStatus.setVisibility(View.VISIBLE);
            tvBluetoothStatus.setText("Loading...");
            llBluetoothList.removeAllViews();
            llBluetoothList.addView(tvBluetoothStatus);
        });

        executorService.execute(() -> {
            try {
                URL url = new URL(baseUrl + "/bluetooth_list");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);

                if (conn.getResponseCode() == 200) {
                    InputStream is = conn.getInputStream();
                    Scanner s = new Scanner(is).useDelimiter("\\A");
                    String res = s.hasNext() ? s.next() : "";
                    is.close();

                    JSONObject json = new JSONObject(res);
                    JSONArray devs = json.optJSONArray("devices");
                    
                    mainHandler.post(() -> {
                        llBluetoothList.removeAllViews();
                        if (devs != null && devs.length() > 0) {
                            for (int i = 0; i < devs.length(); i++) {
                                JSONObject d = devs.optJSONObject(i);
                                if (d != null) {
                                    LinearLayout row = new LinearLayout(PcStatusActivity.this);
                                    row.setOrientation(LinearLayout.HORIZONTAL);
                                    row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                                    row.setPadding(0, 8, 0, 8);
                                    LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                                            LinearLayout.LayoutParams.MATCH_PARENT, 
                                            LinearLayout.LayoutParams.WRAP_CONTENT);
                                    rowParams.setMargins(0, 8, 0, 8);
                                    row.setLayoutParams(rowParams);

                                    ImageView icon = new ImageView(PcStatusActivity.this);
                                    String iconType = d.optString("icon", "bluetooth");
                                    if (iconType.contains("headset") || iconType.contains("audio")) {
                                        icon.setImageResource(R.drawable.ic_headphones);
                                    } else if (iconType.contains("phone") || iconType.contains("smartphone")) {
                                        icon.setImageResource(R.drawable.ic_smartphone);
                                    } else if (iconType.contains("computer") || iconType.contains("pc")) {
                                        icon.setImageResource(R.drawable.ic_computer);
                                    } else {
                                        icon.setImageResource(R.drawable.ic_bluetooth);
                                    }
                                    
                                    icon.setColorFilter(tvBatteryState.getCurrentTextColor(), android.graphics.PorterDuff.Mode.SRC_IN);
                                    
                                    int size = (int) (20 * getResources().getDisplayMetrics().density);
                                    LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(size, size);
                                    iconParams.setMarginEnd((int) (8 * getResources().getDisplayMetrics().density));
                                    icon.setLayoutParams(iconParams);

                                    TextView tv = new TextView(PcStatusActivity.this);
                                    tv.setText(d.optString("name"));
                                    tv.setTextSize(14f);
                                    tv.setTextColor(tvBatteryState.getCurrentTextColor());
                                    
                                    row.addView(icon);
                                    row.addView(tv);

                                    if (d.optBoolean("connected", false)) {
                                        ImageView checkIcon = new ImageView(PcStatusActivity.this);
                                        checkIcon.setImageResource(R.drawable.ic_check);
                                        LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(size, size);
                                        checkParams.setMarginStart((int) (8 * getResources().getDisplayMetrics().density));
                                        checkIcon.setLayoutParams(checkParams);
                                        checkIcon.setColorFilter(tvBatteryState.getCurrentTextColor(), android.graphics.PorterDuff.Mode.SRC_IN);
                                        row.addView(checkIcon);
                                    }

                                    llBluetoothList.addView(row);
                                }
                            }
                        } else {
                            tvBluetoothStatus.setText("No devices found");
                            llBluetoothList.addView(tvBluetoothStatus);
                        }
                    });
                }
                conn.disconnect();
            } catch (Exception e) {
                mainHandler.post(() -> {
                    tvBluetoothStatus.setText("Failed to load Bluetooth");
                    llBluetoothList.removeAllViews();
                    llBluetoothList.addView(tvBluetoothStatus);
                });
            }
        });
    }

    private void fetchMonitorList() {
        String baseUrl = getBaseUrl();
        if (baseUrl == null) return;

        mainHandler.post(() -> {
            tvMonitorStatus.setText("Loading...");
            llMonitorList.removeAllViews();
            llMonitorList.addView(tvMonitorStatus);
        });

        executorService.execute(() -> {
            try {
                URL url = new URL(baseUrl + "/monitor_list");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setUseCaches(false);
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);

                if (conn.getResponseCode() == 200) {
                    InputStream in = conn.getInputStream();
                    Scanner scanner = new Scanner(in);
                    scanner.useDelimiter("\\A");
                    String response = scanner.hasNext() ? scanner.next() : "";
                    scanner.close();

                    JSONArray jsonArray = new JSONArray(response);

                    mainHandler.post(() -> {
                        llMonitorList.removeAllViews();
                        List<CheckBox> allPrimaryCbs = new ArrayList<>();
                        if (jsonArray.length() > 0) {
                            for (int i = 0; i < jsonArray.length(); i++) {
                                JSONObject d = jsonArray.optJSONObject(i);
                                if (d != null) {
                                    String id = d.optString("id");
                                    String name = d.optString("name");
                                    int brightness = d.optInt("brightness");
                                    boolean isPrimary = d.optBoolean("is_primary", false);
                                    boolean isActive = d.optBoolean("is_active", true);

                                    LinearLayout row = new LinearLayout(PcStatusActivity.this);
                                    row.setOrientation(LinearLayout.VERTICAL);
                                    row.setPadding(0, 8, 0, 8);

                                    LinearLayout headerRow = new LinearLayout(PcStatusActivity.this);
                                    headerRow.setOrientation(LinearLayout.HORIZONTAL);
                                    headerRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
                                    headerRow.setPadding(16, 0, 16, 8);

                                    TextView tv = new TextView(PcStatusActivity.this);
                                    tv.setText(name);
                                    tv.setTextSize(14f);
                                    tv.setTextColor(tvBatteryState.getCurrentTextColor());
                                    LinearLayout.LayoutParams tvParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                                    tv.setLayoutParams(tvParams);
                                    headerRow.addView(tv);

                                    CheckBox cbActive = new CheckBox(PcStatusActivity.this);
                                    cbActive.setText("On");
                                    cbActive.setChecked(isActive);
                                    cbActive.setTextColor(tvBatteryState.getCurrentTextColor());
                                    
                                    CheckBox cbPrimary = new CheckBox(PcStatusActivity.this);
                                    cbPrimary.setText("Main");
                                    cbPrimary.setChecked(isPrimary);
                                                                        cbPrimary.setTextColor(tvBatteryState.getCurrentTextColor());
                                    allPrimaryCbs.add(cbPrimary);
                                    
                                    cbActive.setOnCheckedChangeListener((btn, isChecked) -> {
                                        sendCommand("set_monitor_state/" + id + "/" + isChecked + "/" + cbPrimary.isChecked());
                                    });
                                    cbPrimary.setOnCheckedChangeListener((btn, isChecked) -> {
                                        if (isChecked) {
                                            for (CheckBox cb : allPrimaryCbs) {
                                                if (cb != cbPrimary) {
                                                    cb.setChecked(false);
                                                }
                                            }
                                        }
                                        sendCommand("set_monitor_state/" + id + "/" + cbActive.isChecked() + "/" + isChecked);
                                    });

                                    headerRow.addView(cbActive);
                                    headerRow.addView(cbPrimary);

                                    SeekBar sb = new SeekBar(PcStatusActivity.this);
                                    sb.setMax(100);
                                    sb.setProgress(brightness);
                                    sb.setPadding(16, 0, 16, 16);
                                    
                                    if (themeColor != android.graphics.Color.parseColor("#C01C28")) {
                                        sb.setProgressTintList(android.content.res.ColorStateList.valueOf(themeColor));
                                        sb.setThumbTintList(android.content.res.ColorStateList.valueOf(themeColor));
                                        cbActive.setButtonTintList(android.content.res.ColorStateList.valueOf(themeColor));
                                        cbPrimary.setButtonTintList(android.content.res.ColorStateList.valueOf(themeColor));
                                    } else {
                                        sb.setProgressTintList(android.content.res.ColorStateList.valueOf(themeColor));
                                        sb.setThumbTintList(android.content.res.ColorStateList.valueOf(themeColor));
                                        cbActive.setButtonTintList(android.content.res.ColorStateList.valueOf(themeColor));
                                        cbPrimary.setButtonTintList(android.content.res.ColorStateList.valueOf(themeColor));
                                    }

                                    sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                                        @Override
                                        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {}
                                        @Override
                                        public void onStartTrackingTouch(SeekBar seekBar) {}
                                        @Override
                                        public void onStopTrackingTouch(SeekBar seekBar) {
                                            sendCommand("set_brightness/" + id + "/" + seekBar.getProgress());
                                        }
                                    });

                                    row.addView(headerRow);
                                    row.addView(sb);
                                    llMonitorList.addView(row);
                                }
                            }
                        } else {
                            tvMonitorStatus.setText("No monitors found");
                            llMonitorList.addView(tvMonitorStatus);
                        }
                    });
                }
                conn.disconnect();
            } catch (Exception e) {
                mainHandler.post(() -> {
                    tvMonitorStatus.setText("Failed to load monitors");
                    llMonitorList.removeAllViews();
                    llMonitorList.addView(tvMonitorStatus);
                });
            }
        });
    }

    private void sendCommand(String path) {
        String baseUrl = getBaseUrl();
        if (baseUrl == null) return;

        executorService.execute(() -> {
            try {
                URL url = new URL(baseUrl + "/" + path);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(2000);
                connection.setReadTimeout(2000);
                connection.getResponseCode();
                connection.disconnect();
                
                pollStatus();
            } catch (Exception e) {
                mainHandler.post(() -> tvPcError.setText("Send Error: " + e.getMessage()));
            }
        });
    }
}
