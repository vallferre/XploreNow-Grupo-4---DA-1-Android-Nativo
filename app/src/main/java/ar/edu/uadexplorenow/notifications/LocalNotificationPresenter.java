package ar.edu.uadexplorenow.notifications;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import ar.edu.uadexplorenow.R;
import ar.edu.uadexplorenow.domain.NotificationItem;
import ar.edu.uadexplorenow.ui.MainActivity;

public final class LocalNotificationPresenter {

    public static final String CHANNEL_ID = "xplorenow_notifications";
    public static final String EXTRA_NOTIFICATION_ID = "notification_id";
    public static final String EXTRA_SOURCE_TYPE = "source_type";
    public static final String EXTRA_SOURCE_ID = "source_id";

    private LocalNotificationPresenter() {}

    public static void ensureChannel(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notifications_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription(context.getString(R.string.notifications_channel_description));
        manager.createNotificationChannel(channel);
    }

    public static boolean canPostNotifications(@NonNull Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    public static void show(@NonNull Context context, @NonNull NotificationItem item) {
        ensureChannel(context);
        if (!canPostNotifications(context)) return;

        Intent intent = new Intent(context, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_NOTIFICATION_ID, item.id)
                .putExtra(EXTRA_SOURCE_TYPE, item.sourceType)
                .putExtra(EXTRA_SOURCE_ID, item.sourceId);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                item.id.hashCode(),
                intent,
                flags
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_24)
                .setContentTitle(item.title)
                .setContentText(item.message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(item.message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManagerCompat.from(context).notify(item.id.hashCode(), builder.build());
    }
}