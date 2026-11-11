package com.nexos.youtubeblocker;

import adrt.ADRTLogCatReader;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

public class PlaybackService extends Service {
    public static final String ACTION_NEXT =
            "com.nexos.youtubeblocker.NEXT";
    public static final String ACTION_OPEN =
            "com.nexos.youtubeblocker.OPEN";
    public static final String ACTION_PLAY_PAUSE =
            "com.nexos.youtubeblocker.PLAY_PAUSE";
    public static final String ACTION_PREVIOUS =
            "com.nexos.youtubeblocker.PREVIOUS";
    private static final String CHANNEL_ID = "ytb_playback";
    private static final int NOTIFICATION_ID = 4107;
    private static PlaybackService instance;
    private String mediaTitle = "YouTube Blocker";
    private boolean playing = false;
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        ADRTLogCatReader.onContext(this, "com.aide.ui");
        super.onCreate();
        instance = this;
        createChannel();
        try {
            PowerManager powerManager =
                    (PowerManager) getSystemService(POWER_SERVICE);
            if (powerManager != null) {
                wakeLock = powerManager.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "YouTubeBlocker:Playback");
            }
        } catch (Exception e) {
            wakeLock = null;
        }
        startForeground(NOTIFICATION_ID, buildNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_OPEN.equals(intent.getAction())) {
            Intent open = new Intent(this, MainActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(open);
        }
        return START_STICKY;
    }

    public static void updatePlayback(Context context, boolean playing,
            String title) {
        PlaybackService service = instance;
        if (service == null) {
            try {
                context.startService(
                        new Intent(context, PlaybackService.class));
            } catch (Exception e) {
                return;
            }
            return;
        }
        service.setPlayback(playing, title);
    }

    private void setPlayback(boolean playing, String title) {
        this.playing = playing;
        if (title != null && title.trim().length() > 0) {
            mediaTitle = title.trim();
        }
        try {
            if (wakeLock != null) {
                if (playing && !wakeLock.isHeld()) {
                    wakeLock.acquire();
                } else if (!playing && wakeLock.isHeld()) {
                    wakeLock.release();
                }
            }
        } catch (Exception e) {
        }
        NotificationManager manager = (NotificationManager)
                getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification());
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "Reproducción", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(
                    "Controles persistentes de reproducción de YouTube");
            channel.setShowBadge(false);
            NotificationManager manager = (NotificationManager)
                    getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private PendingIntent actionIntent(String action, int requestCode) {
        Intent intent = new Intent(this, MediaActionReceiver.class);
        intent.setAction(action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(this, requestCode, intent, flags);
    }

    private Notification buildNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent openApp =
                PendingIntent.getActivity(this, 1, intent, flags);
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        builder.setContentTitle(mediaTitle)
                .setContentText(playing
                        ? "Reproduciendo · controles disponibles"
                        : "Reproducción lista · toca para volver")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(openApp)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(android.R.drawable.ic_media_previous, "Anterior",
                        actionIntent(ACTION_PREVIOUS, 2))
                .addAction(playing ? android.R.drawable.ic_media_pause
                        : android.R.drawable.ic_media_play,
                        playing ? "Pausar" : "Reproducir",
                        actionIntent(ACTION_PLAY_PAUSE, 3))
                .addAction(android.R.drawable.ic_media_next, "Siguiente",
                        actionIntent(ACTION_NEXT, 4));
        if (Build.VERSION.SDK_INT >= 21) {
            builder.setVisibility(Notification.VISIBILITY_PUBLIC);
        }
        if (Build.VERSION.SDK_INT < 26) {
            builder.setPriority(Notification.PRIORITY_LOW);
        }
        return builder.build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        instance = null;
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch (Exception e) {
        }
        super.onDestroy();
    }
}
