package com.elyot.pclink;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class NetworkManager {
    private static String optimalIp = null;
    private static boolean isChecking = false;

    public static void reportNetworkError() {
        if (optimalIp != null) {
            Log.d("NetworkManager", "Network error reported, clearing optimal IP");
            optimalIp = null;
            isChecking = false;
        }
    }

    public static String getConnectionType(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        boolean forceTailscale = prefs.getBoolean("force_tailscale", false);
        String tailscaleIp = prefs.getString("music_target_ip", "");
        if (tailscaleIp.isEmpty()) {
            tailscaleIp = prefs.getString(Constants.KEY_HOST, "");
        }

        if (forceTailscale && !tailscaleIp.isEmpty()) {
            return "Forced Tailscale (" + tailscaleIp + ")";
        }

        if (optimalIp != null) {
            return "Local IP (" + optimalIp + ")";
        }
        
        if (!tailscaleIp.isEmpty()) {
            return "Global/Tailscale (" + tailscaleIp + ")";
        }
        return "Not Connected";
    }

    public static String getBaseUrl(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        boolean forceTailscale = prefs.getBoolean("force_tailscale", false);
        String tailscaleIp = prefs.getString("music_target_ip", "");
        if (tailscaleIp.isEmpty()) {
            tailscaleIp = prefs.getString(Constants.KEY_HOST, "");
        }
        
        if (forceTailscale) {
            return "http://" + tailscaleIp + ":5000";
        }

        if (optimalIp != null) {
            return "http://" + optimalIp + ":5000";
        }
        
        if (!isChecking && !tailscaleIp.isEmpty()) {
            checkLocalIps(context, tailscaleIp);
        }
        
        return "http://" + tailscaleIp + ":5000";
    }

    private static void checkLocalIps(Context context, String tailscaleIp) {
        isChecking = true;
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                URL url = new URL("http://" + tailscaleIp + ":5000/network_info");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(2000);
                conn.setReadTimeout(2000);
                
                if (conn.getResponseCode() == 200) {
                    InputStream is = conn.getInputStream();
                    Scanner s = new Scanner(is).useDelimiter("\\A");
                    String res = s.hasNext() ? s.next() : "";
                    is.close();
                    
                    JSONObject json = new JSONObject(res);
                    JSONArray ips = json.optJSONArray("ips");
                    if (ips != null && ips.length() > 0) {
                        List<String> localIps = new ArrayList<>();
                        for (int i = 0; i < ips.length(); i++) {
                            localIps.add(ips.getString(i));
                        }
                        
                        String bestIp = pingIps(localIps);
                        if (bestIp != null) {
                            optimalIp = bestIp;
                            Log.d("NetworkManager", "Switched to local IP: " + optimalIp);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                isChecking = false;
            }
        });
    }

    private static String pingIps(List<String> ips) {
        ExecutorService pingExecutor = Executors.newFixedThreadPool(ips.size());
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> winner = new AtomicReference<>(null);

        for (String ip : ips) {
            pingExecutor.execute(() -> {
                try {
                    URL url = new URL("http://" + ip + ":5000/ping");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(500); 
                    conn.setReadTimeout(500);
                    
                    if (conn.getResponseCode() == 200) {
                        if (winner.compareAndSet(null, ip)) {
                            latch.countDown();
                        }
                    }
                } catch (Exception ignored) {
                }
            });
        }

        try {
            latch.await(600, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        pingExecutor.shutdownNow();
        return winner.get();
    }
}
