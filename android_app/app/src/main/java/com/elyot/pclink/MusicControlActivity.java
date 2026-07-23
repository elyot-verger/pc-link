package com.elyot.pclink;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.widget.TextView;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.os.Build;
import android.widget.CheckBox;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.app.ActivityManager;
import android.media.AudioManager;
import android.view.View;
import org.json.JSONObject;
import android.widget.SeekBar;
import android.widget.ImageButton;
import androidx.palette.graphics.Palette;
import android.graphics.Color;
import androidx.core.graphics.ColorUtils;
import android.view.View;
import android.widget.ImageView;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.util.Timer;
import java.util.TimerTask;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.animation.LinearInterpolator;

public class MusicControlActivity extends AppCompatActivity {

    private static final String PREF_TARGET_IP = "music_target_ip";
    private View rootLayout;
    
    private TextView tvTrackTitle;
    private TextView tvTrackArtist;
    private TextView tvTimeCurrent;
    private TextView tvTimeTotal;
    private SeekBar seekBar;
    private TextView tvErrorLog;
    private ImageView ivAlbumArt;
    private TextView tvLyrics;
    private SeekBar sbVolume;
    private ImageButton btnMute;
    private android.view.GestureDetector gestureDetector;
    private boolean isUserAdjustingVolume = false;
    private boolean isMuted = false;
    private float lastUnmutedVolume = 0.5f;
    private long lastSeekTime = 0;
    private long lastVolumeAdjustTime = 0;
    private long lastCommandTime = 0;
    
    private static class LyricLine {
        long timeMs;
        String text;
        LyricLine(long timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text;
        }
    }
    private java.util.List<LyricLine> lyricsList = new java.util.ArrayList<>();
    
    private Timer pollTimer;
    private boolean isUserSeeking = false;
    private boolean isPlaying = false;
    private int currentPositionMs = 0;
    private int totalLengthMs = 0;
    private String lastArtTitle = "";
    private String currentShuffleState = "Unsupported";
    private String currentLoopState = "Unsupported";
    private String currentAlbum = "";
    private String currentYear = "";
    private boolean showInfoOverride = false;
    
    private int lyricsOffset = 1000;
    private int rotationSpeedRpm = 33;
    private ObjectAnimator rotationAnimator;
    private int pollingIntervalMs = 3000;
    
    private BroadcastReceiver powerSaveReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            restartPollTimer();
        }
    };
    
    private BroadcastReceiver notifStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.hasExtra("isPlaying")) {
                isPlaying = intent.getBooleanExtra("isPlaying", isPlaying);
                int playIcon = isPlaying ? R.drawable.ic_round_pause : R.drawable.ic_round_play;
                ((ImageButton) findViewById(R.id.btnPlayPause)).setImageResource(playIcon);
            }
            if (intent.hasExtra("shuffle")) {
                currentShuffleState = intent.getStringExtra("shuffle");
                ((ImageButton) findViewById(R.id.btnShuffle)).setImageResource("On".equalsIgnoreCase(currentShuffleState) ? R.drawable.ic_shuffle_on : R.drawable.ic_shuffle);
            }
            if (intent.hasExtra("loop")) {
                currentLoopState = intent.getStringExtra("loop");
                ImageButton btnLoop = findViewById(R.id.btnLoop);
                if ("Track".equalsIgnoreCase(currentLoopState)) {
                    btnLoop.setImageResource(R.drawable.ic_loop_one);
                } else if ("Playlist".equalsIgnoreCase(currentLoopState)) {
                    btnLoop.setImageResource(R.drawable.ic_loop_on);
                } else {
                    btnLoop.setImageResource(R.drawable.ic_loop);
                }
            }
            if (intent.hasExtra("volume")) {
                int vol = intent.getIntExtra("volume", -1);
                if (vol >= 0) {
                    sbVolume.setProgress(vol);
                    if (vol == 0) {
                        isMuted = true;
                        btnMute.setImageResource(R.drawable.ic_round_volume_off);
                    } else {
                        isMuted = false;
                        btnMute.setImageResource(R.drawable.ic_round_volume_up);
                        lastUnmutedVolume = vol / 100f;
                    }
                    lastVolumeAdjustTime = System.currentTimeMillis();
                }
            }
        }
    };

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    private Runnable smoothUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (isPlaying && !isUserSeeking && totalLengthMs > 0) {
                currentPositionMs += 50;
                if (currentPositionMs > totalLengthMs) {
                    currentPositionMs = totalLengthMs;
                }
                seekBar.setProgress(currentPositionMs);
                tvTimeCurrent.setText(formatTime(currentPositionMs / 1000));
                updateActiveLyric();
            }
            mainHandler.postDelayed(this, 50);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_music_control);
        ThemeUtils.applyTheme(this, false);

        // Initialize root layout for dynamic background
        rootLayout = findViewById(R.id.rootLayout);

        tvErrorLog = findViewById(R.id.tvErrorLog);
        
        tvTrackTitle = findViewById(R.id.tvTrackTitle);
        tvTrackArtist = findViewById(R.id.tvTrackArtist);
        tvTimeCurrent = findViewById(R.id.tvTimeCurrent);
        tvTimeTotal = findViewById(R.id.tvTimeTotal);
        seekBar = findViewById(R.id.seekBar);
        ivAlbumArt = findViewById(R.id.ivAlbumArt);
        tvLyrics = findViewById(R.id.tvLyrics);

        // Setup gesture detector for lyrics box swipe
        gestureDetector = new android.view.GestureDetector(this, new android.view.GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 50;
            private static final int SWIPE_VELOCITY_THRESHOLD = 50;
            
            @Override
            public boolean onFling(android.view.MotionEvent e1, android.view.MotionEvent e2, float velocityX, float velocityY) {
                float diffX = e2.getX() - e1.getX();
                if (Math.abs(diffX) > Math.abs(e2.getY() - e1.getY())) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX < 0) {
                            showInfoOverride = true; // swipe left -> show info
                        } else {
                            showInfoOverride = false; // swipe right -> show lyrics
                        }
                        updateActiveLyric();
                        return true;
                    }
                }
                return false;
            }
        });
        
        findViewById(R.id.llLyricsContainer).setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return true;
        });

        ImageButton btnMusicSettings = findViewById(R.id.btnMusicSettings);
        btnMusicSettings.setOnClickListener(v -> startActivity(new Intent(this, MusicSettingsActivity.class)));

        findViewById(R.id.btnShuffle).setOnClickListener(v -> sendCommand("shuffle"));
        findViewById(R.id.btnPrev).setOnClickListener(v -> sendCommand("prev"));
        findViewById(R.id.btnPlayPause).setOnClickListener(v -> sendCommand("play_pause"));
        findViewById(R.id.btnNext).setOnClickListener(v -> sendCommand("next"));
        findViewById(R.id.btnLoop).setOnClickListener(v -> sendCommand("loop"));

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    tvTimeCurrent.setText(formatTime(progress / 1000));
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isUserSeeking = false;
                lastSeekTime = System.currentTimeMillis();
                currentPositionMs = seekBar.getProgress();
                sendCommand("seek/" + (seekBar.getProgress() / 1000.0f));
            }
        });

        sbVolume = findViewById(R.id.sbVolume);
        btnMute = findViewById(R.id.btnMute);
        
        btnMute.setOnClickListener(v -> {
            if (isMuted) {
                sendCommand("volume/" + lastUnmutedVolume);
                isMuted = false;
                btnMute.setImageResource(R.drawable.ic_round_volume_up);
                int volInt = (int)(lastUnmutedVolume * 100);
                sbVolume.setProgress(volInt);
                updateServiceVolume(volInt);
            } else {
                float currentVol = sbVolume.getProgress() / 100f;
                if (currentVol > 0) lastUnmutedVolume = currentVol;
                sendCommand("volume/0.0");
                isMuted = true;
                btnMute.setImageResource(R.drawable.ic_round_volume_off);
                sbVolume.setProgress(0);
                updateServiceVolume(0);
            }
        });

        sbVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserAdjustingVolume = true;
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isUserAdjustingVolume = false;
                lastVolumeAdjustTime = System.currentTimeMillis();
                float vol = seekBar.getProgress() / 100f;
                if (vol > 0) lastUnmutedVolume = vol;
                sendCommand("volume/" + vol);
                updateServiceVolume(seekBar.getProgress());
            }
        });

    }

    private void updateServiceVolume(int volume) {
        Intent svcIntent = new Intent(this, MusicNotificationService.class);
        svcIntent.setAction(MusicNotificationService.ACTION_UPDATE_FROM_APP);
        svcIntent.putExtra("volume", volume);
        startService(svcIntent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ThemeUtils.applyTheme(this, true);
        
        if (!NetworkUtils.isVpnActive(this)) {
            NetworkUtils.activateTailscale(this);
            android.widget.Toast.makeText(this, "Démarrage de Tailscale...", android.widget.Toast.LENGTH_SHORT).show();
        }
        
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        lyricsOffset = prefs.getInt("pref_lyrics_offset", 1000);
        rotationSpeedRpm = prefs.getInt("pref_rotation_speed", 33);
        String tCol = prefs.getString("pref_time_color", "#FFFFFF");
        String vCol = prefs.getString("pref_vol_color", "#FFFFFF");
        applySeekBarColors(tCol, vCol);

        IntentFilter filter = new IntentFilter(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        registerReceiver(powerSaveReceiver, filter);

        restartPollTimer();
        
        IntentFilter notifFilter = new IntentFilter(MusicNotificationService.ACTION_UPDATE_FROM_NOTIF);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(notifStateReceiver, notifFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(notifStateReceiver, notifFilter);
        }
        mainHandler.post(smoothUpdateRunnable);
    }

    private void restartPollTimer() {
        if (pollTimer != null) {
            pollTimer.cancel();
        }
        
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        pollingIntervalMs = prefs.getInt("pref_polling_interval", 3000);
        
        android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null && pm.isPowerSaveMode()) {
            if (pollingIntervalMs < 2500) {
                pollingIntervalMs = 2500;
            }
        }
        
        pollTimer = new Timer();
        pollTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                pollStatus();
            }
        }, 0, pollingIntervalMs);
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPolling();
        try {
            unregisterReceiver(powerSaveReceiver);
        } catch (Exception e) {}
        unregisterReceiver(notifStateReceiver);
        mainHandler.removeCallbacks(smoothUpdateRunnable);
    }

    private void stopPolling() {
        if (pollTimer != null) {
            pollTimer.cancel();
            pollTimer = null;
        }
    }

    private void applySeekBarColors(String timeHex, String volHex) {
        try {
            int timeC = Color.parseColor(timeHex);
            seekBar.setProgressTintList(android.content.res.ColorStateList.valueOf(timeC));
            seekBar.setThumbTintList(android.content.res.ColorStateList.valueOf(timeC));
        } catch (Exception e) {}
        try {
            int volC = Color.parseColor(volHex);
            sbVolume.setProgressTintList(android.content.res.ColorStateList.valueOf(volC));
            sbVolume.setThumbTintList(android.content.res.ColorStateList.valueOf(volC));
        } catch (Exception e) {}
    }

    private void pollStatus() {
        if (!NetworkUtils.isVpnActive(this)) {
            mainHandler.post(() -> {
                tvErrorLog.setVisibility(android.view.View.VISIBLE);
                tvErrorLog.setText("En attente du VPN Tailscale...");
            });
            return;
        }
        
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        String ip = prefs.getString(PREF_TARGET_IP, "");
        if (ip.isEmpty()) return;

        String urlString = "http://" + ip + ":5000/status";

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
                String title = json.optString("title", "");
                String artist = json.optString("artist", "");
                String state = json.optString("state", "");
                double length = json.optDouble("length", 0.0);
                double position = json.optDouble("position", 0.0);
                String loopState = json.optString("loop", "Unsupported");
                String shuffleState = json.optString("shuffle", "Unsupported");

                double volume = json.optDouble("volume", -1.0);
                String album = json.optString("album", "");
                String year = json.optString("year", "");

                mainHandler.post(() -> updateUI(title, artist, state, (int)length, (int)position, loopState, shuffleState, (float)volume, album, year));
            }
            connection.disconnect();
        } catch (Exception e) {
            // Ignore
        }
    }

    private void updateUI(String title, String artist, String state, int length, int position, String loopState, String shuffleState, float volume, String album, String year) {
        boolean noMedia = title.isEmpty();
        
        ImageButton btnShuffle = findViewById(R.id.btnShuffle);
        ImageButton btnLoop = findViewById(R.id.btnLoop);
        
        btnShuffle.setEnabled(!noMedia);
        findViewById(R.id.btnPrev).setEnabled(!noMedia);
        findViewById(R.id.btnPlayPause).setEnabled(!noMedia);
        findViewById(R.id.btnNext).setEnabled(!noMedia);
        btnLoop.setEnabled(!noMedia);
        seekBar.setEnabled(!noMedia);
        sbVolume.setEnabled(!noMedia);
        btnMute.setEnabled(!noMedia);
        
        if (volume >= 0 && !isUserAdjustingVolume && System.currentTimeMillis() - lastVolumeAdjustTime > 2000) {
            sbVolume.setProgress((int)(volume * 100));
            if (volume <= 0.01f) {
                isMuted = true;
                btnMute.setImageResource(R.drawable.ic_round_volume_off);
            } else {
                isMuted = false;
                btnMute.setImageResource(R.drawable.ic_round_volume_up);
                lastUnmutedVolume = volume;
            }
        }

        if (noMedia) {
            tvTrackTitle.setText("Aucun média en lecture");
            tvTrackArtist.setText("");
            seekBar.setProgress(0);
            seekBar.setMax(0);
            tvTimeCurrent.setText("0:00");
            tvTimeTotal.setText("0:00");
            isPlaying = false;
            ((ImageButton) findViewById(R.id.btnPlayPause)).setImageResource(R.drawable.ic_round_play);
            ivAlbumArt.setImageResource(android.R.color.darker_gray);
            updateRotationState();
            lastArtTitle = "";
            btnShuffle.setVisibility(View.GONE);
            btnLoop.setVisibility(View.GONE);
            
            // Reset background to default when no media
            rootLayout.setBackgroundColor(Color.BLACK);
            lyricsList.clear();
            
            // Use lyrics container as "Lancer Deezer" button
            tvLyrics.setText("Lancer Deezer");
            tvLyrics.setTextColor(Color.WHITE);
            tvLyrics.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_deezer, 0, 0, 0);
            tvLyrics.setCompoundDrawablePadding(24);
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setColor(Color.parseColor("#333333"));
            gd.setCornerRadius(32f);
            findViewById(R.id.llLyricsContainer).setBackground(gd);
            
            findViewById(R.id.llPageIndicators).setVisibility(View.GONE);
            findViewById(R.id.llLyricsContainer).setVisibility(View.VISIBLE);
            
            findViewById(R.id.llLyricsContainer).setOnTouchListener(null);
            findViewById(R.id.llLyricsContainer).setOnClickListener(v -> {
                sendCommand("open_deezer");
                android.widget.Toast.makeText(MusicControlActivity.this, "Lancement de Deezer...", android.widget.Toast.LENGTH_SHORT).show();
            });
            
        } else {
            findViewById(R.id.llLyricsContainer).setOnClickListener(null);
            findViewById(R.id.llLyricsContainer).setOnTouchListener((v, event) -> {
                gestureDetector.onTouchEvent(event);
                return true;
            });
            tvLyrics.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            tvLyrics.setCompoundDrawablePadding(0);
            tvTrackTitle.setText(title);
            tvTrackArtist.setText(artist);
            currentAlbum = album;
            currentYear = year;
            totalLengthMs = length * 1000;
            seekBar.setMax(totalLengthMs);
            
            if (System.currentTimeMillis() - lastCommandTime > 2000) {
                isPlaying = "Playing".equalsIgnoreCase(state);
            }
            
            updateRotationState();
            
            if (!title.equals(lastArtTitle)) {
                lastArtTitle = title;
                fetchAlbumArt();
                fetchLyrics();
            }
            
            int playIcon = isPlaying ? R.drawable.ic_round_pause : R.drawable.ic_round_play;
            ((ImageButton) findViewById(R.id.btnPlayPause)).setImageResource(playIcon);
            
            if (!isUserSeeking && System.currentTimeMillis() - lastSeekTime > 2000) {
                currentPositionMs = position * 1000;
                seekBar.setProgress(currentPositionMs);
                tvTimeCurrent.setText(formatTime(position));
                updateActiveLyric();
            }
            tvTimeTotal.setText(formatTime(length));
            
            if (System.currentTimeMillis() - lastCommandTime > 2000) {
                currentShuffleState = shuffleState;
                currentLoopState = loopState;
            }
            
            if ("Unsupported".equalsIgnoreCase(shuffleState)) {
                btnShuffle.setVisibility(View.GONE);
            } else {
                btnShuffle.setVisibility(View.VISIBLE);
                btnShuffle.setImageResource("On".equalsIgnoreCase(shuffleState) ? R.drawable.ic_shuffle_on : R.drawable.ic_shuffle);
            }

            if ("Unsupported".equalsIgnoreCase(loopState)) {
                btnLoop.setVisibility(View.GONE);
            } else {
                btnLoop.setVisibility(View.VISIBLE);
                if ("Track".equalsIgnoreCase(loopState)) {
                    btnLoop.setImageResource(R.drawable.ic_loop_one);
                } else if ("Playlist".equalsIgnoreCase(loopState)) {
                    btnLoop.setImageResource(R.drawable.ic_loop_on);
                } else {
                    btnLoop.setImageResource(R.drawable.ic_loop);
                }
            }
        }
    }

    private String formatTime(long seconds) {
        int m = (int) (seconds / 60);
        int s = (int) (seconds % 60);
        return String.format("%d:%02d", m, s);
    }

    private void updateRotationState() {
        if (isPlaying && rotationSpeedRpm > 0) {
            ivAlbumArt.animate().cancel();
            if (rotationAnimator == null) {
                float currentRot = ivAlbumArt.getRotation() % 360f;
                if (currentRot < 0) currentRot += 360f;
                rotationAnimator = ObjectAnimator.ofFloat(ivAlbumArt, "rotation", currentRot, currentRot + 360f);
                rotationAnimator.setRepeatCount(ValueAnimator.INFINITE);
                rotationAnimator.setRepeatMode(ValueAnimator.RESTART);
                rotationAnimator.setInterpolator(new LinearInterpolator());
            }
            long duration = (long) ((60f / rotationSpeedRpm) * 1000f);
            rotationAnimator.setDuration(duration);
            if (!rotationAnimator.isStarted()) {
                rotationAnimator.start();
            }
        } else {
            if (rotationAnimator != null) {
                rotationAnimator.cancel();
                rotationAnimator = null;
            }
            float currentRot = ivAlbumArt.getRotation() % 360f;
            if (currentRot < 0) currentRot += 360f;
            ivAlbumArt.setRotation(currentRot);
            
            float targetRot = currentRot > 180f ? 360f : 0f;
            long duration = (long) (Math.abs(targetRot - currentRot) / 360f * 1000f);
            duration = Math.max(200, Math.min(600, duration));
            
            ivAlbumArt.animate()
                .rotation(targetRot)
                .setDuration(duration)
                .setInterpolator(new LinearInterpolator())
                .withEndAction(() -> ivAlbumArt.setRotation(0f))
                .start();
        }
    }

    private void fetchAlbumArt() {
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        String ip = prefs.getString(PREF_TARGET_IP, "");
        if (ip.isEmpty()) return;

        String artUrlString = "http://" + ip + ":5000/art";
        executorService.execute(() -> {
            try {
                URL url = new URL(artUrlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(3000);

                if (connection.getResponseCode() == 200) {
                    InputStream is = connection.getInputStream();
                    Bitmap bitmap = BitmapFactory.decodeStream(is);
                    is.close();
                    if (bitmap != null) {
                        mainHandler.post(() -> {
                            ivAlbumArt.setImageBitmap(bitmap);
                            applyPalette(bitmap);
                        });
                    }
                }
                connection.disconnect();
            } catch (Exception e) {
                // Ignore silent errors for art fetch
            }
        });
    }

    private void fetchLyrics() {
        lyricsList.clear();
        mainHandler.post(() -> tvLyrics.setText(""));
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        String ip = prefs.getString(PREF_TARGET_IP, "");
        if (ip.isEmpty()) return;

        String lyricsUrl = "http://" + ip + ":5000/lyrics";
        executorService.execute(() -> {
            try {
                URL url = new URL(lyricsUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(3000);

                if (connection.getResponseCode() == 200) {
                    InputStream is = connection.getInputStream();
                    Scanner s = new Scanner(is).useDelimiter("\\A");
                    String result = s.hasNext() ? s.next() : "";
                    is.close();
                    
                    JSONObject json = new JSONObject(result);
                    String syncedLyrics = json.optString("syncedLyrics", "");
                    if (!syncedLyrics.isEmpty()) {
                        parseSyncedLyrics(syncedLyrics);
                    }
                }
                connection.disconnect();
            } catch (Exception e) {
                // Ignore silent errors for lyrics fetch
            }
        });
    }

    private void parseSyncedLyrics(String lrc) {
        java.util.List<LyricLine> parsed = new java.util.ArrayList<>();
        String[] lines = lrc.split("\n");
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\[(\\d+):(\\d+\\.\\d+)\\](.*)");
        for (String line : lines) {
            java.util.regex.Matcher m = pattern.matcher(line);
            if (m.matches()) {
                try {
                    int min = Integer.parseInt(m.group(1));
                    float sec = Float.parseFloat(m.group(2));
                    long timeMs = (long) ((min * 60 + sec) * 1000);
                    String text = m.group(3).trim();
                    parsed.add(new LyricLine(timeMs, text));
                } catch (Exception e) { }
            }
        }
        mainHandler.post(() -> {
            lyricsList.clear();
            lyricsList.addAll(parsed);
            updateActiveLyric();
        });
    }

    private void updateActiveLyric() {
        if (lyricsList.isEmpty()) {
            findViewById(R.id.llPageIndicators).setVisibility(View.GONE);
            String infoText = "";
            if (!currentAlbum.isEmpty()) {
                infoText += "Album: " + currentAlbum;
            }
            if (!currentYear.isEmpty()) {
                infoText += (infoText.isEmpty() ? "" : " (") + currentYear + (infoText.isEmpty() ? "" : ")");
            }
            if (infoText.isEmpty()) {
                infoText = "No lyrics found";
            }
            
            tvLyrics.setText(infoText);
            findViewById(R.id.llLyricsContainer).setVisibility(View.VISIBLE);
            return;
        }

        findViewById(R.id.llPageIndicators).setVisibility(View.VISIBLE);
        findViewById(R.id.dotLyrics).setAlpha(showInfoOverride ? 0.4f : 1.0f);
        findViewById(R.id.dotInfo).setAlpha(showInfoOverride ? 1.0f : 0.4f);

        if (showInfoOverride) {
            String infoText = "";
            if (!currentAlbum.isEmpty()) {
                infoText += "Album: " + currentAlbum;
            }
            if (!currentYear.isEmpty()) {
                infoText += (infoText.isEmpty() ? "" : " (") + currentYear + (infoText.isEmpty() ? "" : ")");
            }
            if (infoText.isEmpty()) {
                infoText = "No lyrics found";
            }
            
            tvLyrics.setText(infoText);
            findViewById(R.id.llLyricsContainer).setVisibility(View.VISIBLE);
            return;
        }
        
        String activeText = "";
        long effectivePosition = currentPositionMs + lyricsOffset; 
        for (int i = 0; i < lyricsList.size(); i++) {
            if (effectivePosition >= lyricsList.get(i).timeMs) {
                activeText = lyricsList.get(i).text;
            } else {
                break;
            }
        }
        tvLyrics.setText(activeText);
        findViewById(R.id.llLyricsContainer).setVisibility(View.VISIBLE);
    }

    /**
     * Extract a dominant color from the album art and apply it as the background.
     * Falls back to a dark default if palette generation fails.
     */
    private void applyPalette(Bitmap bitmap) {
        Palette.from(bitmap).generate(palette -> {
            int defaultColor = Color.BLACK;
            if (palette != null) {
                int color = palette.getDominantColor(defaultColor);
                rootLayout.setBackgroundColor(color);
                // Adjust title and artist text color for contrast
                int textColor = (ColorUtils.calculateLuminance(color) < 0.5) ? Color.WHITE : Color.BLACK;
                tvTrackTitle.setTextColor(textColor);
                tvTrackArtist.setTextColor(textColor);
                tvLyrics.setTextColor(textColor);
                tvTimeCurrent.setTextColor(textColor);
                tvTimeTotal.setTextColor(textColor);
                
                android.content.res.ColorStateList csl = android.content.res.ColorStateList.valueOf(textColor);
                seekBar.setProgressTintList(csl);
                seekBar.setThumbTintList(csl);
                sbVolume.setProgressTintList(csl);
                sbVolume.setThumbTintList(csl);
                
                ((android.widget.ImageButton) findViewById(R.id.btnPlayPause)).setColorFilter(textColor);
                ((android.widget.ImageButton) findViewById(R.id.btnPrev)).setColorFilter(textColor);
                ((android.widget.ImageButton) findViewById(R.id.btnNext)).setColorFilter(textColor);
                ((android.widget.ImageButton) findViewById(R.id.btnMusicSettings)).setColorFilter(textColor);
                ((android.widget.ImageButton) findViewById(R.id.btnShuffle)).setColorFilter(textColor);
                ((android.widget.ImageButton) findViewById(R.id.btnLoop)).setColorFilter(textColor);
                ((android.widget.ImageButton) findViewById(R.id.btnMute)).setColorFilter(textColor);
                
                android.graphics.drawable.GradientDrawable dotActive = new android.graphics.drawable.GradientDrawable();
                dotActive.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                dotActive.setColor(textColor);
                findViewById(R.id.dotLyrics).setBackground(dotActive);
                findViewById(R.id.dotInfo).setBackground(dotActive);

                int lyricsBgColor = (ColorUtils.calculateLuminance(color) < 0.5) ? 
                    ColorUtils.blendARGB(color, Color.WHITE, 0.15f) : 
                    ColorUtils.blendARGB(color, Color.BLACK, 0.15f);

                android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                gd.setColor(lyricsBgColor);
                gd.setCornerRadius(32f);
                findViewById(R.id.llLyricsContainer).setBackground(gd);
            } else {
                rootLayout.setBackgroundColor(defaultColor);
                tvTrackTitle.setTextColor(Color.WHITE);
                tvTrackArtist.setTextColor(Color.WHITE);
                tvLyrics.setTextColor(Color.WHITE);
                tvTimeCurrent.setTextColor(Color.WHITE);
                tvTimeTotal.setTextColor(Color.WHITE);
                
                android.content.res.ColorStateList csl = android.content.res.ColorStateList.valueOf(Color.WHITE);
                seekBar.setProgressTintList(csl);
                seekBar.setThumbTintList(csl);
                sbVolume.setProgressTintList(csl);
                sbVolume.setThumbTintList(csl);
                
                ((android.widget.ImageButton) findViewById(R.id.btnPlayPause)).setColorFilter(Color.WHITE);
                ((android.widget.ImageButton) findViewById(R.id.btnPrev)).setColorFilter(Color.WHITE);
                ((android.widget.ImageButton) findViewById(R.id.btnNext)).setColorFilter(Color.WHITE);
                ((android.widget.ImageButton) findViewById(R.id.btnMusicSettings)).setColorFilter(Color.WHITE);
                ((android.widget.ImageButton) findViewById(R.id.btnShuffle)).setColorFilter(Color.WHITE);
                ((android.widget.ImageButton) findViewById(R.id.btnLoop)).setColorFilter(Color.WHITE);
                ((android.widget.ImageButton) findViewById(R.id.btnMute)).setColorFilter(Color.WHITE);
                
                android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                gd.setColor(ColorUtils.blendARGB(defaultColor, Color.WHITE, 0.15f));
                gd.setCornerRadius(32f);
                findViewById(R.id.llLyricsContainer).setBackground(gd);
            }
        });
    }






    private void sendCommand(String endpoint) {
        lastCommandTime = System.currentTimeMillis();
        if ("play_pause".equals(endpoint)) {
            isPlaying = !isPlaying;
            int playIcon = isPlaying ? R.drawable.ic_round_pause : R.drawable.ic_round_play;
            ((ImageButton) findViewById(R.id.btnPlayPause)).setImageResource(playIcon);
            updateRotationState();
        } else if ("shuffle".equals(endpoint)) {
            ImageButton btnS = findViewById(R.id.btnShuffle);
            currentShuffleState = "On".equalsIgnoreCase(currentShuffleState) ? "Off" : "On";
            btnS.setImageResource("On".equalsIgnoreCase(currentShuffleState) ? R.drawable.ic_shuffle_on : R.drawable.ic_shuffle);
        } else if ("loop".equals(endpoint)) {
            ImageButton btnL = findViewById(R.id.btnLoop);
            if ("None".equalsIgnoreCase(currentLoopState)) {
                currentLoopState = "Playlist";
                btnL.setImageResource(R.drawable.ic_loop_on);
            } else if ("Playlist".equalsIgnoreCase(currentLoopState)) {
                currentLoopState = "Track";
                btnL.setImageResource(R.drawable.ic_loop_one);
            } else {
                currentLoopState = "None";
                btnL.setImageResource(R.drawable.ic_loop);
            }
        }
        
        Intent svcIntent = new Intent(this, MusicNotificationService.class);
        svcIntent.setAction(MusicNotificationService.ACTION_UPDATE_FROM_APP);
        svcIntent.putExtra("isPlaying", isPlaying);
        svcIntent.putExtra("shuffle", currentShuffleState);
        svcIntent.putExtra("loop", currentLoopState);
        startService(svcIntent);
        
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        String ip = prefs.getString(PREF_TARGET_IP, "");
        
        if (ip.isEmpty()) {
            Toast.makeText(this, "Please set the Tailscale IP in the main settings", Toast.LENGTH_SHORT).show();
            return;
        }

        String urlString = "http://" + ip + ":5000/" + endpoint;

        executorService.execute(() -> {
            try {
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(3000);

                int responseCode = connection.getResponseCode();
                if (responseCode != 200) {
                    InputStream es = connection.getErrorStream();
                    if (es != null) {
                        Scanner s = new Scanner(es).useDelimiter("\\A");
                        String result = s.hasNext() ? s.next() : "";
                        throw new Exception("HTTP " + responseCode + ": " + result);
                    } else {
                        throw new Exception("HTTP " + responseCode);
                    }
                }

                // Close stream
                InputStream is = connection.getInputStream();
                is.close();
                connection.disconnect();

            } catch (Exception e) {
                // Ignore silent errors for music control so it feels fast, 
                // but for debugging it's useful to show a toast
                mainHandler.post(() -> {
                    Toast.makeText(MusicControlActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    tvErrorLog.setText(e.toString());
                });
            }
        });
    }
}
