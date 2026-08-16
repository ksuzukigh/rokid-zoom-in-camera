package io.github.ksuzukigh.rokidzoomincamera;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

public final class ButtonSafetyService extends Service {
    static final String ACTION_CLAIM = "io.github.ksuzukigh.rokidzoomincamera.CLAIM";
    static final String ACTION_HEARTBEAT = "io.github.ksuzukigh.rokidzoomincamera.HEARTBEAT";
    static final String ACTION_RELEASE = "io.github.ksuzukigh.rokidzoomincamera.RELEASE";
    private static final String CHANNEL = "button_safety";
    private static final int NOTIFICATION_ID = 41;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastHeartbeat;
    private boolean claimed;

    private final Runnable watchdog = new Runnable() {
        @Override public void run() {
            if (claimed && SystemClock.elapsedRealtime() - lastHeartbeat > 6500L) {
                releaseAndStop();
            } else if (claimed) {
                handler.postDelayed(this, 2000L);
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(
                CHANNEL, getString(R.string.safety_channel), NotificationManager.IMPORTANCE_MIN));
        Notification notification = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.safety_notification))
                .setOngoing(true)
                .build();
        startForeground(NOTIFICATION_ID, notification);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_RELEASE.equals(action)) {
            releaseAndStop();
            return START_NOT_STICKY;
        }
        lastHeartbeat = SystemClock.elapsedRealtime();
        if (ACTION_CLAIM.equals(action) || !claimed) {
            claimed = true;
            RokidButtonControl.claim(this);
            handler.removeCallbacks(watchdog);
            handler.postDelayed(watchdog, 2000L);
        }
        return START_STICKY;
    }

    @Override public void onTaskRemoved(Intent rootIntent) {
        releaseAndStop();
        super.onTaskRemoved(rootIntent);
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(watchdog);
        if (claimed) RokidButtonControl.restore(this);
        claimed = false;
        super.onDestroy();
    }

    private void releaseAndStop() {
        handler.removeCallbacks(watchdog);
        if (claimed) RokidButtonControl.restore(this);
        claimed = false;
        stopForeground(true);
        stopSelf();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }
}
