package com.escape.app.utils;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.escape.app.R;
import com.escape.app.model.AppRestriction;
import com.escape.app.ui.MainActivity;

/**klasa za upravljanje notifikacijama*/
public class AppNotificationManager {
    private static final String CHANNEL_ID_TIME_WARNING = "time_warning_channel";
    private static final String CHANNEL_ID_BREAK_REMINDER = "break_reminder_channel";
    private static final String CHANNEL_ID_SERVICE = "service_channel";

    private static final int NOTIFICATION_ID_TIME_WARNING = 1;
    private static final int NOTIFICATION_ID_BREAK_REMINDER = 2;
    public static final int NOTIFICATION_ID_SERVICE = 3;

    private final Context context;
    private final android.app.NotificationManager notificationManager;

    public AppNotificationManager(Context context) {
        this.context = context;
        this.notificationManager = (android.app.NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (this.notificationManager != null) {
            createNotificationChannels();
        }
    }

    /** napravljen bitmap nakon čega samo croppuje sliku
     */
    private IconCompat getCroppedSmallIcon() {
        try {
            Bitmap original = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_platypus);
            if (original == null) {
                return IconCompat.createWithResource(context, R.drawable.ic_platypus);
            }

            int width = original.getWidth();
            int height = original.getHeight();
            
            int cropSize = (int) (Math.min(width, height) * 0.75f);
            
            int x = Math.round((width - cropSize) / 2.0f);
            int y = Math.round((height - cropSize) / 2.0f);
            
            x = Math.max(0, Math.min(x, width - cropSize));
            y = Math.max(0, Math.min(y, height - cropSize));
            
            Bitmap cropped = Bitmap.createBitmap(original, x, y, cropSize, cropSize);
            
            int iconSize = (int) (24 * context.getResources().getDisplayMetrics().density);
            Bitmap scaled = Bitmap.createScaledBitmap(cropped, iconSize, iconSize, true);
            
            if (cropped != original) {
                cropped.recycle();
            }
            if (original != scaled) {
                original.recycle();
            }
            
            return IconCompat.createWithBitmap(scaled);
        } catch (Exception e) {
            android.util.Log.e("AppNotificationManager", "Error creating cropped small icon", e);
            return IconCompat.createWithResource(context, R.drawable.ic_platypus);
        }
    }

    /** ponovo croppujem i pravim bitmap
     */
    private Bitmap getCroppedPlatypusIcon() {
        try {
            Bitmap original = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_platypus);
            if (original == null) {
                return null;
            }

            int width = original.getWidth();
            int height = original.getHeight();
            
            int cropSize = (int) (Math.min(width, height) * 0.85f);
            
            int x = Math.round((width - cropSize) / 2.0f);
            int y = Math.round((height - cropSize) / 2.0f);
            
            // Ensure crop doesn't go out of bounds
            x = Math.max(0, Math.min(x, width - cropSize));
            y = Math.max(0, Math.min(y, height - cropSize));
            
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
            android.util.Log.e("AppNotificationManager", "Error creating cropped platypus icon", e);
            return BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_platypus);
        }
    }

    /** Pravi notification kanale za Android 8.0+*/
    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                android.util.Log.d("AppNotificationManager", "Creating notification channels...");

                // Time warning channel
                NotificationChannel timeWarningChannel = new NotificationChannel(
                    CHANNEL_ID_TIME_WARNING,
                    context.getString(R.string.time_limit_warnings_channel),
                    android.app.NotificationManager.IMPORTANCE_DEFAULT
                );
                timeWarningChannel.setDescription(context.getString(R.string.time_limit_warnings_channel_desc));

                // Break reminder channel
                NotificationChannel breakReminderChannel = new NotificationChannel(
                    CHANNEL_ID_BREAK_REMINDER,
                    context.getString(R.string.break_reminders_channel),
                    android.app.NotificationManager.IMPORTANCE_HIGH
                );
                breakReminderChannel.setDescription(context.getString(R.string.break_reminders_channel_desc));

                // Service channel
                NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID_SERVICE,
                    context.getString(R.string.app_monitoring_service_channel),
                    android.app.NotificationManager.IMPORTANCE_LOW
                );
                serviceChannel.setDescription(context.getString(R.string.app_monitoring_service_channel_desc));

                notificationManager.createNotificationChannel(timeWarningChannel);
                notificationManager.createNotificationChannel(breakReminderChannel);
                notificationManager.createNotificationChannel(serviceChannel);

                android.util.Log.d("AppNotificationManager", "Notification channels created successfully");
            } catch (Exception e) {
                android.util.Log.e("AppNotificationManager", "Error creating notification channels", e);
            }
        }
    }

    /*** Prikazuje time limit warning notifikaciju*/
    public void showTimeLimitWarning(AppRestriction restriction, int usagePercent, int remainingMinutes) {
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String title = context.getString(R.string.time_limit_warning);
        String message = context.getString(R.string.time_limit_warning_message,
            usagePercent, restriction.getAppName(), remainingMinutes);

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID_TIME_WARNING)
            .setSmallIcon(getCroppedSmallIcon())
            .setLargeIcon(getCroppedPlatypusIcon())
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build();

        notificationManager.notify(NOTIFICATION_ID_TIME_WARNING, notification);
    }

    /*** Prikazuje break reminder notifikaciju(MArko briši ovo)*/
    public void showBreakReminder(int breakDurationMinutes) {
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String title = context.getString(R.string.break_time);
        String message = context.getString(R.string.break_time_message, breakDurationMinutes);

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID_BREAK_REMINDER)
            .setSmallIcon(getCroppedSmallIcon())
            .setLargeIcon(getCroppedPlatypusIcon())
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build();

        notificationManager.notify(NOTIFICATION_ID_BREAK_REMINDER, notification);
    }

    /** Pravi persistent service notifikaciju(Za nadgledanje)*/
    public Notification createServiceNotification() {
        try {
            Intent intent = new Intent(context, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            return new NotificationCompat.Builder(context, CHANNEL_ID_SERVICE)
                .setSmallIcon(getCroppedSmallIcon())
                .setLargeIcon(getCroppedPlatypusIcon())
                .setContentTitle(context.getString(R.string.escape_active))
                .setContentText(context.getString(R.string.monitoring_app_usage))
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
        } catch (Exception e) {
            android.util.Log.e("AppNotificationManager", "Error creating service notification", e);
            return null;
        }
    }
    //otkazivači notifikacija
    public void cancelTimeLimitWarning() {
        notificationManager.cancel(NOTIFICATION_ID_TIME_WARNING);
    }

    public void cancelBreakReminder() {
        notificationManager.cancel(NOTIFICATION_ID_BREAK_REMINDER);
    }

    public void cancelAll() {
        notificationManager.cancelAll();
    }
}
