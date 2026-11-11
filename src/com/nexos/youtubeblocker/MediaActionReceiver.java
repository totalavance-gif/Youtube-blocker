package com.nexos.youtubeblocker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class MediaActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        if (PlaybackService.ACTION_PLAY_PAUSE.equals(action)) {
            MainActivity.performMediaAction("toggle");
        } else if (PlaybackService.ACTION_PREVIOUS.equals(action)) {
            MainActivity.performMediaAction("previous");
        } else if (PlaybackService.ACTION_NEXT.equals(action)) {
            MainActivity.performMediaAction("next");
        }
    }
}
