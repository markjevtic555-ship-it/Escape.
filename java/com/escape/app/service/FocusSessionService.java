package com.escape.app.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.escape.app.R;
import com.escape.app.ui.FocusSessionActivity;

/**
 * Foreground koji upravlja fokus sesijom
 */
public class FocusSessionService extends Service {
    private static final String TAG = "FocusSessionService";
    private static final String CHANNEL_ID = "focus_session_channel";
    private static final int NOTIFICATION_ID = 2001;

    private final IBinder binder = new FocusSessionBinder();
    private Handler timerHandler;
    private Runnable timerRunnable;

    private long sessionStartTime;
    private boolean isSessionActive = false;
    private int previousDndMode = -1;

    private OnTimerUpdateListener timerUpdateListener;
    private int kairosAwardedCount = 0;
    private static final long KAIROS_INTERVAL_MILLIS = 45 * 60 * 1000; // 45 minutes

    public interface OnTimerUpdateListener {
        void onTimerUpdate(long elapsedMillis);
        void onKairosEarned(int totalKairosThisSession);
    }

    public class FocusSessionBinder extends Binder {
        public FocusSessionService getService() {
            return FocusSessionService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        timerHandler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // startForeground requires API 5, but minSdk is 26, so this is safe
        startForeground(NOTIFICATION_ID, createNotification());
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    /**
     * ZAPOČNI fokus sesiju
     */
    public void startSession() {
        if (isSessionActive) return;

        isSessionActive = true;
        sessionStartTime = SystemClock.elapsedRealtime();
        kairosAwardedCount = 0;

        // Save and enable DND
        enableDoNotDisturb();

        // Start timer updates
        startTimerUpdates();

        // Update notification
        updateNotification();
    }

    /**
     * zaustavi fokus sesiju
     */
    public void stopSession() {
        if (!isSessionActive) return;

        isSessionActive = false;

        // Stop timer updates
        if (timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }

        // Restore DND state
        restoreDoNotDisturb();

        // Stop foreground service
        stopForeground(true);
        stopSelf();
    }


    public long getElapsedMillis() {
        if (!isSessionActive) return 0;
        return SystemClock.elapsedRealtime() - sessionStartTime;
    }


    public boolean isSessionActive() {
        return isSessionActive;
    }


    public void setTimerUpdateListener(OnTimerUpdateListener listener) {
        this.timerUpdateListener = listener;
    }


    public void removeTimerUpdateListener() {
        this.timerUpdateListener = null;
    }

    private void startTimerUpdates() {
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (isSessionActive) {
                    long elapsed = getElapsedMillis();
                    if (timerUpdateListener != null) {
                        timerUpdateListener.onTimerUpdate(elapsed);
                    }
                    

                    int expectedKairos = (int) (elapsed / KAIROS_INTERVAL_MILLIS);
                    if (expectedKairos > kairosAwardedCount) {
                        kairosAwardedCount = expectedKairos;
                        if (timerUpdateListener != null) {
                            timerUpdateListener.onKairosEarned(kairosAwardedCount);
                        }
                    }
                    

                    timerHandler.postDelayed(this, 1000);
                }
            }
        };
        timerHandler.post(timerRunnable);
    }

    @SuppressLint("WrongConstant")
    private void enableDoNotDisturb() {
        NotificationManager notificationManager = 
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        
        if (notificationManager != null && notificationManager.isNotificationPolicyAccessGranted()) {

            previousDndMode = notificationManager.getCurrentInterruptionFilter();
            

            notificationManager.setInterruptionFilter(
                NotificationManager.INTERRUPTION_FILTER_NONE);
        }
    }

    @SuppressLint("WrongConstant")
    private void restoreDoNotDisturb() {
        NotificationManager notificationManager = 
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        
        if (notificationManager != null && notificationManager.isNotificationPolicyAccessGranted()) {
            if (previousDndMode != -1) {

                notificationManager.setInterruptionFilter(previousDndMode);
            } else {

                notificationManager.setInterruptionFilter(
                    NotificationManager.INTERRUPTION_FILTER_ALL);
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.focus_session_channel_name),
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(getString(R.string.focus_session_channel_description));
            channel.setShowBadge(false);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }


    private Bitmap getCroppedPlatypusIcon() {
        try {
            Bitmap original = BitmapFactory.decodeResource(getResources(), R.drawable.ic_platypus);
            if (original == null) {
                return null;
            }

            int width = original.getWidth();
            int height = original.getHeight();
            

            int cropSize = (int) (Math.min(width, height) * 0.85f);
            

            int x = (width - cropSize) / 2;
            int y = (height - cropSize) / 2;
            

            Bitmap cropped = Bitmap.createBitmap(original, x, y, cropSize, cropSize);
            

            int notificationSize = 256;
            Bitmap scaled = Bitmap.createScaledBitmap(cropped, notificationSize, notificationSize, true);
             if (cropped != original) {
                cropped.recycle();
            }
            if (original != scaled) {
                original.recycle();
            }
            
            return scaled;
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error creating cropped platypus icon", e);
            return BitmapFactory.decodeResource(getResources(), R.drawable.ic_platypus);
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, FocusSessionActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.focus_session_notification_title))
            .setContentText(getString(R.string.focus_session_notification_text))
            .setSmallIcon(R.drawable.ic_platypus)
            .setLargeIcon(getCroppedPlatypusIcon())
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build();
    }

    private void updateNotification() {
        NotificationManager notificationManager = 
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, createNotification());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
        if (isSessionActive) {
            restoreDoNotDisturb();
        }
    }
}

