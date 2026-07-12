package ar.edu.uadexplorenow.notifications;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import ar.edu.uadexplorenow.domain.NotificationItem;

public final class NotificationDeliveryStore {

    private static final String PREFS = "xplorenow_notification_delivery";
    private static final String KEY_PREFIX = "notified_";

    private NotificationDeliveryStore() {}

    public static boolean wasNotified(@NonNull Context context, @NonNull String uid, @NonNull NotificationItem item) {
        return prefs(context).getBoolean(KEY_PREFIX + uid + "_" + item.id, false);
    }

    public static void markNotified(@NonNull Context context, @NonNull String uid, @NonNull NotificationItem item) {
        prefs(context).edit().putBoolean(KEY_PREFIX + uid + "_" + item.id, true).apply();
    }

    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}