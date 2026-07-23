package com.elyot.pclink;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v4.media.MediaMetadataCompat;
import androidx.media.VolumeProviderCompat;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.json.JSONObject;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;
import android.os.Handler;
import android.os.Looper;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MusicNotificationService extends Service {
    private static final String CHANNEL_ID = "music_control_channel";
    private static final int NOTIFICATION_ID = 1001;

    public static final String ACTION_PREV = "com.elyot.pclink.PREV";
    public static final String ACTION_PLAY_PAUSE = "com.elyot.pclink.PLAY_PAUSE";
    public static final String ACTION_NEXT = "com.elyot.pclink.NEXT";
    public static final String ACTION_SHUFFLE = "com.elyot.pclink.SHUFFLE";
    public static final String ACTION_LOOP = "com.elyot.pclink.LOOP";
    
    public static final String ACTION_UPDATE_FROM_APP = "com.elyot.pclink.UPDATE_FROM_APP";
    public static final String ACTION_UPDATE_FROM_NOTIF = "com.elyot.pclink.UPDATE_FROM_NOTIF";

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private MediaSessionCompat mediaSession;
    private Timer pollTimer;
    private NotificationManager notificationManager;
    private boolean isForegroundVisible = false;
    
    private String lastTitle = "No media playing";
    private String lastArtist = "";
    private long lastPosition = 0;
    private long lastLength = 0;
    private boolean lastIsPlaying = false;
    private Bitmap lastAlbumArt = null;
    private String lastShuffleState = "Unsupported";
    private String lastLoopState = "Unsupported";
    
    private VolumeProviderCompat volumeProvider;
    private int currentHwVolume = 0;
    private int systemMaxVolume = 15;
    private int currentPollingInterval = -1;

    private BroadcastReceiver powerSaveReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            checkPollingInterval();
        }
    };

    private void broadcastVolumeChange(int volumePercent) {
        Intent intent = new Intent(ACTION_UPDATE_FROM_NOTIF);
        intent.putExtra("volume", volumePercent);
        sendBroadcast(intent);
    }

    private void updateHardwareVolumeSetting() {
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        boolean useHwVol = prefs.getBoolean("pref_hardware_volume", true);

        if (useHwVol) {
            if (volumeProvider == null) {
                AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (audioManager != null) {
                    systemMaxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    if (systemMaxVolume <= 0) systemMaxVolume = 15;
                }
                
                volumeProvider = new VolumeProviderCompat(VolumeProviderCompat.VOLUME_CONTROL_ABSOLUTE, systemMaxVolume, currentHwVolume) {
                    @Override
                    public void onSetVolumeTo(int volume) {
                        currentHwVolume = volume;
                        setCurrentVolume(currentHwVolume);
                        int percentage = (int) ((currentHwVolume / (float) systemMaxVolume) * 100);
                        broadcastVolumeChange(percentage);
                        sendCommand("volume/" + (currentHwVolume / (float) systemMaxVolume));
                    }

                    @Override
                    public void onAdjustVolume(int direction) {
                        currentHwVolume += direction;
                        if (currentHwVolume > systemMaxVolume) currentHwVolume = systemMaxVolume;
                        if (currentHwVolume < 0) currentHwVolume = 0;
                        setCurrentVolume(currentHwVolume);
                        int percentage = (int) ((currentHwVolume / (float) systemMaxVolume) * 100);
                        broadcastVolumeChange(percentage);
                        sendCommand("volume/" + (currentHwVolume / (float) systemMaxVolume));
                    }
                };
            }
            if (mediaSession != null) {
                mediaSession.setPlaybackToRemote(volumeProvider);
            }
        } else {
            mediaSession.setPlaybackToLocal(android.media.AudioManager.STREAM_MUSIC);
            volumeProvider = null;
        }
    }

    private void notifyAppOfOptimisticUpdate() {
        Intent intent = new Intent(ACTION_UPDATE_FROM_NOTIF);
        intent.putExtra("isPlaying", lastIsPlaying);
        intent.putExtra("shuffle", lastShuffleState);
        intent.putExtra("loop", lastLoopState);
        sendBroadcast(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        notificationManager = getSystemService(NotificationManager.class);
        mediaSession = new MediaSessionCompat(this, "MusicNotificationService");
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                lastIsPlaying = true;
                sendCommand("play_pause");
                if (isForegroundVisible) {
                    startForeground(NOTIFICATION_ID, buildNotification(lastTitle, lastArtist, lastPosition, lastLength, lastIsPlaying, lastAlbumArt, lastLoopState, lastShuffleState));
                }
            }

            @Override
            public void onPause() {
                lastIsPlaying = false;
                sendCommand("play_pause");
                if (isForegroundVisible) {
                    startForeground(NOTIFICATION_ID, buildNotification(lastTitle, lastArtist, lastPosition, lastLength, lastIsPlaying, lastAlbumArt, lastLoopState, lastShuffleState));
                }
            }

            @Override
            public void onSkipToNext() {
                sendCommand("next");
            }

            @Override
            public void onSkipToPrevious() {
                sendCommand("prev");
            }
            
            @Override
            public void onSeekTo(long pos) {
                sendCommand("seek/" + (pos / 1000.0f));
            }

            @Override
            public void onSetShuffleMode(int shuffleMode) {
                lastShuffleState = "On".equalsIgnoreCase(lastShuffleState) ? "Off" : "On";
                sendCommand("shuffle");
                notifyAppOfOptimisticUpdate();
                if (isForegroundVisible) {
                    startForeground(NOTIFICATION_ID, buildNotification(lastTitle, lastArtist, lastPosition, lastLength, lastIsPlaying, lastAlbumArt, lastLoopState, lastShuffleState));
                }
            }

            @Override
            public void onSetRepeatMode(int repeatMode) {
                if ("None".equalsIgnoreCase(lastLoopState)) {
                    lastLoopState = "Playlist";
                } else if ("Playlist".equalsIgnoreCase(lastLoopState)) {
                    lastLoopState = "Track";
                } else {
                    lastLoopState = "None";
                }
                sendCommand("loop");
                notifyAppOfOptimisticUpdate();
                if (isForegroundVisible) {
                    startForeground(NOTIFICATION_ID, buildNotification(lastTitle, lastArtist, lastPosition, lastLength, lastIsPlaying, lastAlbumArt, lastLoopState, lastShuffleState));
                }
            }

            @Override
            public void onCustomAction(String action, android.os.Bundle extras) {
                if (ACTION_SHUFFLE.equals(action)) {
                    lastShuffleState = "On".equalsIgnoreCase(lastShuffleState) ? "Off" : "On";
                    sendCommand("shuffle");
                } else if (ACTION_LOOP.equals(action)) {
                    if ("None".equalsIgnoreCase(lastLoopState)) {
                        lastLoopState = "Playlist";
                    } else if ("Playlist".equalsIgnoreCase(lastLoopState)) {
                        lastLoopState = "Track";
                    } else {
                        lastLoopState = "None";
                    }
                    sendCommand("loop");
                }
                notifyAppOfOptimisticUpdate();
                if (isForegroundVisible) {
                    startForeground(NOTIFICATION_ID, buildNotification(lastTitle, lastArtist, lastPosition, lastLength, lastIsPlaying, lastAlbumArt, lastLoopState, lastShuffleState));
                }
            }
        });
        updateHardwareVolumeSetting();
        checkPollingInterval();
        
        IntentFilter filter = new IntentFilter(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        registerReceiver(powerSaveReceiver, filter);
        
        mediaSession.setActive(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            
            if (ACTION_UPDATE_FROM_APP.equals(action)) {
                if (intent.hasExtra("isPlaying")) lastIsPlaying = intent.getBooleanExtra("isPlaying", lastIsPlaying);
                if (intent.hasExtra("shuffle")) lastShuffleState = intent.getStringExtra("shuffle");
                if (intent.hasExtra("loop")) lastLoopState = intent.getStringExtra("loop");
                if (intent.hasExtra("volume")) {
                    int volPercent = intent.getIntExtra("volume", 0);
                    currentHwVolume = (int) ((volPercent / 100f) * systemMaxVolume);
                    if (volumeProvider != null) {
                        volumeProvider.setCurrentVolume(currentHwVolume);
                    }
                }
                updateHardwareVolumeSetting();
                checkPollingInterval();
                if (isForegroundVisible) {
                    startForeground(NOTIFICATION_ID, buildNotification(lastTitle, lastArtist, lastPosition, lastLength, lastIsPlaying, lastAlbumArt, lastLoopState, lastShuffleState));
                }
                return START_STICKY;
            }
            
            if (ACTION_PREV.equals(action)) {
                sendCommand("prev");
            } else if (ACTION_PLAY_PAUSE.equals(action)) {
                lastIsPlaying = !lastIsPlaying; // Optimistic update
                sendCommand("play_pause");
            } else if (ACTION_NEXT.equals(action)) {
                sendCommand("next");
            } else if (ACTION_SHUFFLE.equals(action)) {
                lastShuffleState = "On".equalsIgnoreCase(lastShuffleState) ? "Off" : "On";
                sendCommand("shuffle");
            } else if (ACTION_LOOP.equals(action)) {
                if ("None".equalsIgnoreCase(lastLoopState)) {
                    lastLoopState = "Playlist";
                } else if ("Playlist".equalsIgnoreCase(lastLoopState)) {
                    lastLoopState = "Track";
                } else {
                    lastLoopState = "None";
                }
                sendCommand("loop");
            }
            
            if (ACTION_PLAY_PAUSE.equals(action) || ACTION_SHUFFLE.equals(action) || ACTION_LOOP.equals(action)) {
                notifyAppOfOptimisticUpdate();
            }
            
            if (isForegroundVisible) {
                startForeground(NOTIFICATION_ID, buildNotification(lastTitle, lastArtist, lastPosition, lastLength, lastIsPlaying, lastAlbumArt, lastLoopState, lastShuffleState));
            }
            return START_STICKY;
        }
        
        startForeground(NOTIFICATION_ID, buildNotification(lastTitle, lastArtist, lastPosition, lastLength, lastIsPlaying, lastAlbumArt, lastLoopState, lastShuffleState));
        isForegroundVisible = true;
        return START_STICKY;
    }

    private Notification buildNotification(String title, String artist, long position, long length, boolean isPlaying, Bitmap albumArt, String loopState, String shuffleState) {
        this.lastTitle = title;
        this.lastArtist = artist;
        this.lastPosition = position;
        this.lastLength = length;
        this.lastIsPlaying = isPlaying;
        this.lastLoopState = loopState;
        this.lastShuffleState = shuffleState;
        
        MediaMetadataCompat.Builder metaBuilder = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, length * 1000);
                
        if (albumArt != null) {
            metaBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt);
        }
        
        mediaSession.setMetadata(metaBuilder.build());

        long actions = PlaybackStateCompat.ACTION_SEEK_TO | PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
        
        if (!"Unsupported".equalsIgnoreCase(shuffleState)) actions |= PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE;
        if (!"Unsupported".equalsIgnoreCase(loopState)) actions |= PlaybackStateCompat.ACTION_SET_REPEAT_MODE;

        int state = isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setState(state, position * 1000, 1.0f)
                .setActions(actions);
                
        if (!"Unsupported".equalsIgnoreCase(shuffleState)) {
            int shufIcon = "On".equalsIgnoreCase(shuffleState) ? R.drawable.ic_shuffle_on : R.drawable.ic_shuffle;
            stateBuilder.addCustomAction(new PlaybackStateCompat.CustomAction.Builder(
                    ACTION_SHUFFLE, "Shuffle", shufIcon).build());
        }

        if (!"Unsupported".equalsIgnoreCase(loopState)) {
            int loopIcon = R.drawable.ic_loop;
            if ("Track".equalsIgnoreCase(loopState)) {
                loopIcon = R.drawable.ic_loop_one;
            } else if ("Playlist".equalsIgnoreCase(loopState)) {
                loopIcon = R.drawable.ic_loop_on;
            }
            stateBuilder.addCustomAction(new PlaybackStateCompat.CustomAction.Builder(
                    ACTION_LOOP, "Loop", loopIcon).build());
        }
        
        mediaSession.setPlaybackState(stateBuilder.build());
                
        mediaSession.setShuffleMode("On".equalsIgnoreCase(shuffleState) ? PlaybackStateCompat.SHUFFLE_MODE_ALL : PlaybackStateCompat.SHUFFLE_MODE_NONE);
        int repeatMode = PlaybackStateCompat.REPEAT_MODE_NONE;
        if ("Track".equalsIgnoreCase(loopState)) repeatMode = PlaybackStateCompat.REPEAT_MODE_ONE;
        else if ("Playlist".equalsIgnoreCase(loopState)) repeatMode = PlaybackStateCompat.REPEAT_MODE_ALL;
        mediaSession.setRepeatMode(repeatMode);

        Intent prevIntent = new Intent(this, MusicNotificationService.class).setAction(ACTION_PREV);
        Intent playIntent = new Intent(this, MusicNotificationService.class).setAction(ACTION_PLAY_PAUSE);
        Intent nextIntent = new Intent(this, MusicNotificationService.class).setAction(ACTION_NEXT);
        Intent shuffleIntent = new Intent(this, MusicNotificationService.class).setAction(ACTION_SHUFFLE);
        Intent loopIntent = new Intent(this, MusicNotificationService.class).setAction(ACTION_LOOP);
        
        PendingIntent pPrev, pPlay, pNext, pShuffle, pLoop;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            pPrev = PendingIntent.getForegroundService(this, 0, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            pPlay = PendingIntent.getForegroundService(this, 1, playIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            pNext = PendingIntent.getForegroundService(this, 2, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            pShuffle = PendingIntent.getForegroundService(this, 3, shuffleIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            pLoop = PendingIntent.getForegroundService(this, 4, loopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            pPrev = PendingIntent.getService(this, 0, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            pPlay = PendingIntent.getService(this, 1, playIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            pNext = PendingIntent.getService(this, 2, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            pShuffle = PendingIntent.getService(this, 3, shuffleIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            pLoop = PendingIntent.getService(this, 4, loopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }

        Intent contentIntent = new Intent(this, MusicControlActivity.class);
        PendingIntent pContent = PendingIntent.getActivity(this, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        int playPauseIcon = isPlaying ? R.drawable.ic_round_pause : R.drawable.ic_round_play;
        String playPauseTitle = isPlaying ? "Pause" : "Play";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSmallIcon(R.drawable.ic_round_play)
                .setContentTitle(title)
                .setContentText(artist)
                .setContentIntent(pContent);

        int playIndex = 0;
        if (!"Unsupported".equalsIgnoreCase(shuffleState)) {
            int shufIcon = "On".equalsIgnoreCase(shuffleState) ? R.drawable.ic_shuffle_on : R.drawable.ic_shuffle;
            builder.addAction(shufIcon, "Shuffle", pShuffle);
            playIndex++;
        }
        
        builder.addAction(R.drawable.ic_round_skip_previous, "Previous", pPrev);
        playIndex++;
        
        builder.addAction(playPauseIcon, playPauseTitle, pPlay);
        
        builder.addAction(R.drawable.ic_round_skip_next, "Next", pNext);
        
        if (!"Unsupported".equalsIgnoreCase(loopState)) {
            int loopIcon = R.drawable.ic_loop;
            if ("Track".equalsIgnoreCase(loopState)) {
                loopIcon = R.drawable.ic_loop_one;
            } else if ("Playlist".equalsIgnoreCase(loopState)) {
                loopIcon = R.drawable.ic_loop_on;
            }
            builder.addAction(loopIcon, "Loop", pLoop);
        }

        int[] compactView = new int[3];
        compactView[0] = playIndex - 1; // Prev
        compactView[1] = playIndex;     // Play
        compactView[2] = playIndex + 1; // Next

        builder.setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(compactView))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setOngoing(true);
                
        if (albumArt != null) {
            builder.setLargeIcon(albumArt);
        }
        
        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Music Control",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Persistent notification for controlling PC music");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void sendCommand(String endpoint) {
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        String ip = prefs.getString("music_target_ip", "");
        
        if (ip.isEmpty()) {
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
                if (responseCode == 200) {
                    InputStream is = connection.getInputStream();
                    is.close();
                } else {
                    InputStream es = connection.getErrorStream();
                    if (es != null) es.close();
                }
                connection.disconnect();
            } catch (Exception e) {
                Log.e("MusicService", "Error sending command: " + e.getMessage());
            }
        });
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void checkPollingInterval() {
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        int newInterval = prefs.getInt("pref_polling_interval", 3000);
        
        android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null && pm.isPowerSaveMode()) {
            if (newInterval < 2500) {
                newInterval = 2500;
            }
        }
        
        if (currentPollingInterval != newInterval || pollTimer == null) {
            currentPollingInterval = newInterval;
            if (pollTimer != null) {
                pollTimer.cancel();
            }
            pollTimer = new Timer();
            pollTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    pollStatus();
                }
            }, 0, currentPollingInterval);
        }
    }

    private void pollStatus() {
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        String ip = prefs.getString("music_target_ip", "");
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
                boolean isPlaying = "Playing".equalsIgnoreCase(state);
                boolean noMedia = title.isEmpty();

                mainHandler.post(() -> {
                    if (volume >= 0 && volumeProvider != null) {
                        currentHwVolume = (int) (volume * systemMaxVolume);
                        volumeProvider.setCurrentVolume(currentHwVolume);
                    }
                    if (noMedia) {
                        if (isForegroundVisible) {
                            stopForeground(true);
                            isForegroundVisible = false;
                        }
                        if (mediaSession != null) {
                            mediaSession.setActive(false);
                        }
                        lastTitle = "No media playing";
                        lastArtist = "";
                        lastAlbumArt = null;
                        lastLoopState = "Unsupported";
                        lastShuffleState = "Unsupported";
                    } else {
                        if (mediaSession != null && !mediaSession.isActive()) {
                            mediaSession.setActive(true);
                        }
                        boolean titleChanged = !title.equals(lastTitle);
                        if (titleChanged) {
                            lastAlbumArt = null;
                            fetchAlbumArt(ip, title, artist, (long)position, (long)length, isPlaying, loopState, shuffleState);
                        }
                        
                        Notification notif = buildNotification(title, artist, (long)position, (long)length, isPlaying, lastAlbumArt, loopState, shuffleState);
                        if (!isForegroundVisible) {
                            startForeground(NOTIFICATION_ID, notif);
                            isForegroundVisible = true;
                        } else {
                            notificationManager.notify(NOTIFICATION_ID, notif);
                        }
                    }
                });
            }
            connection.disconnect();
        } catch (Exception e) {
            // Ignore errors
        }
    }

    private void fetchAlbumArt(String ip, String title, String artist, long position, long length, boolean isPlaying, String loopState, String shuffleState) {
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
                            if (title.equals(lastTitle)) {
                                lastAlbumArt = bitmap;
                                if (isForegroundVisible) {
                                    Notification notif = buildNotification(title, artist, position, length, isPlaying, bitmap, loopState, shuffleState);
                                    notificationManager.notify(NOTIFICATION_ID, notif);
                                }
                            }
                        });
                    }
                }
                connection.disconnect();
            } catch (Exception e) {
                // Ignore
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (pollTimer != null) {
            pollTimer.cancel();
            pollTimer = null;
        }
        try {
            unregisterReceiver(powerSaveReceiver);
        } catch (Exception e) {}
        if (mediaSession != null) {
            mediaSession.release();
        }
    }
}
