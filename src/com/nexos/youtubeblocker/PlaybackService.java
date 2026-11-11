package com.nexos.youtubeblocker;

import adrt.ADRTLogCatReader;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
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
    private String mediaTitle = "Tube Lite";
    private boolean playing = false;
    private MediaSession mediaSession;
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        ADRTLogCatReader.onContext(this, "com.aide.ui");
        super.onCreate();
        instance = this;
        createChannel();
        if (Build.VERSION.SDK_INT >= 21) {
            setupMediaSession();
        }
        try {
            PowerManager powerManager =
                    (PowerManager) getSystemService(POWER_SERVICE);
            if (powerManager != null) {
                wakeLock = powerManager.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "TubeLite:Playback");
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
                Intent restart =
                        new Intent(context, PlaybackService.class);
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(restart);
                } else {
                    context.startService(restart);
                }
            } catch (Exception e) {
                return;
            }
            return;
        }
        service.setPlayback(playing, title);
    }

    private void setupMediaSession() {
        try {
            mediaSession = new MediaSession(this, "YouTubeBlocker");
            mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                    | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
            mediaSession.setCallback(new MediaSession.Callback() {
                @Override
                public void onPlay() {
                    if (!playing) {
                        MainActivity.performMediaAction("toggle");
                    }
                }

                @Override
                public void onPause() {
                    if (playing) {
                        MainActivity.performMediaAction("toggle");
                    }
                }

                @Override
                public void onSkipToNext() {
                    MainActivity.performMediaAction("next");
                }

                @Override
                public void onSkipToPrevious() {
                    MainActivity.performMediaAction("previous");
                }
            });
            mediaSession.setActive(true);
        } catch (Exception e) {
            mediaSession = null;
        }
    }

    private void updateMediaSession() {
        if (Build.VERSION.SDK_INT < 21 || mediaSession == null) {
            return;
        }
        try {
            PlaybackState.Builder state = new PlaybackState.Builder();
            state.setActions(PlaybackState.ACTION_PLAY
                    | PlaybackState.ACTION_PAUSE
                    | PlaybackState.ACTION_SKIP_TO_NEXT
                    | PlaybackState.ACTION_SKIP_TO_PREVIOUS);
            state.setState(playing ? PlaybackState.STATE_PLAYING
                    : PlaybackState.STATE_PAUSED, 0L, 1.0f);
            mediaSession.setPlaybackState(state.build());
            MediaMetadata.Builder meta = new MediaMetadata.Builder();
            meta.putString(MediaMetadata.METADATA_KEY_TITLE, mediaTitle);
            mediaSession.setMetadata(meta.build());
        } catch (Exception e) {
        }
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
        updateMediaSession();
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
        if (Build.VERSION.SDK_INT >= 21 && mediaSession != null) {
            try {
                builder.setStyle(new Notification.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2));
            } catch (Exception e) {
            }
        }
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
            if (mediaSession != null) {
                mediaSession.setActive(false);
                mediaSession.release();
                mediaSession = null;
            }
        } catch (Exception e) {
        }
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch (Exception e) {
        }
        super.onDestroy();
    }
}
