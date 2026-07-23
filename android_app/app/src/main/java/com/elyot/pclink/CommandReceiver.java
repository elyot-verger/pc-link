package com.elyot.pclink;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CommandReceiver extends BroadcastReceiver {

    public static final String ACTION_EXECUTE_CMD = "com.elyot.pclink.ACTION_EXECUTE_CMD";
    public static final String EXTRA_SLOT = "extra_slot";

    private static final String TAG = "CommandReceiver";
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        String privateKey = prefs.getString(Constants.KEY_PRIVATE_KEY, "");
        
        int slot = 0;
        if (ACTION_EXECUTE_CMD.equals(action)) {
            slot = intent.getIntExtra(EXTRA_SLOT, 0);
        } else if (SshWidgetProvider.ACTION_CMD_1.equals(action)) slot = 1;
        else if (SshWidgetProvider.ACTION_CMD_2.equals(action)) slot = 2;
        else if (SshWidgetProvider.ACTION_CMD_3.equals(action)) slot = 3;
        else if (SshWidgetProvider.ACTION_CMD_4.equals(action)) slot = 4;

        if (slot == 0) return;

        String cmdName = prefs.getString("lbl_" + slot, "Command " + slot);
        String commandToExecute = prefs.getString("ssh_cmd_" + slot, "");

        if (commandToExecute.isEmpty()) {
            // Legacy fallback
            if (slot == 1) commandToExecute = prefs.getString("ssh_cmd_1", "");
            else if (slot == 2) commandToExecute = prefs.getString("ssh_cmd_2", "");
            else if (slot == 3) commandToExecute = prefs.getString("ssh_cmd_3", "");
            else if (slot == 4) commandToExecute = prefs.getString("ssh_cmd_4", "");
        }

        if (commandToExecute.isEmpty()) {
            showNotification(context, "pc-link", "No command configured for " + cmdName);
            return;
        }

        String host = prefs.getString("host_" + slot, "");
        if (host.isEmpty()) host = prefs.getString("default_host", prefs.getString(Constants.KEY_HOST, ""));

        String portStr = prefs.getString("port_" + slot, "");
        if (portStr.isEmpty()) portStr = prefs.getString("default_port", prefs.getString(Constants.KEY_PORT, "22"));

        String username = prefs.getString("username_" + slot, "");
        if (username.isEmpty()) username = prefs.getString("default_username", prefs.getString(Constants.KEY_USERNAME, ""));

        boolean requireVpn = prefs.getBoolean("require_vpn_" + slot, true);

        if (host.isEmpty() || username.isEmpty() || privateKey.isEmpty()) {
            showNotification(context, "pc-link", "SSH Settings are incomplete. Open app to configure.");
            return;
        }

        int port = 22;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException ignored) {}

        if (requireVpn && !isVpnActive(context)) {
            NetworkUtils.activateTailscale(context);
            showNotification(context, "Démarrage VPN", "Tentative d'activation de Tailscale... La commande échouera si le délai est trop long.");
            // We don't return, we let the SSH task attempt to connect.
            // Tailscale might take 1-2 seconds, SSH timeout is usually longer.
        }

        // Optional: show a notification that we are starting, or just wait for result
        // showNotification(context, "Executing", "Executing " + cmdName + "...");

        final PendingResult pendingResult = goAsync();
        final String fCmd = commandToExecute;
        final String fCmdName = cmdName;
        final int fPort = port;
        final String fHost = host;
        final String fUsername = username;
        final boolean fRequireVpn = requireVpn;

        executorService.execute(() -> {
            try {
                if (fRequireVpn) {
                    int attempts = 0;
                    while (!isVpnActive(context) && attempts < 10) {
                        try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                        attempts++;
                    }
                    if (!isVpnActive(context)) {
                        throw new Exception("Le VPN n'a pas pu s'activer à temps.");
                    }
                }
                SshTask.executeSshCommand(fHost, fPort, fUsername, privateKey, fCmd);
                new Handler(Looper.getMainLooper()).post(() -> 
                    showNotification(context, "Succès", fCmdName + " a été exécuté avec succès !")
                );
            } catch (Exception e) {
                Log.e(TAG, "SSH Error", e);
                new Handler(Looper.getMainLooper()).post(() -> 
                    showNotification(context, "Erreur SSH", "Erreur: " + e.getMessage())
                );
            } finally {
                pendingResult.finish();
            }
        });
    }

    private boolean isVpnActive(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            Network activeNetwork = cm.getActiveNetwork();
            if (activeNetwork != null) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(activeNetwork);
                if (caps != null) {
                    return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
                }
            }
        }
        return false;
    }

    private void showNotification(Context context, String title, String message) {
        android.app.NotificationManager notificationManager = (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) return;

        String channelId = "ssh_commands_channel_high";
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                    channelId, "SSH Commands", android.app.NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }

        android.app.Notification.Builder builder;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            builder = new android.app.Notification.Builder(context, channelId);
        } else {
            builder = new android.app.Notification.Builder(context);
            builder.setPriority(android.app.Notification.PRIORITY_HIGH);
        }

        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
               .setContentTitle(title)
               .setContentText(message)
               .setAutoCancel(true);

        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }
}
