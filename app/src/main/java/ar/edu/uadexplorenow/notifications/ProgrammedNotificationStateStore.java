package ar.edu.uadexplorenow.notifications;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import dagger.hilt.android.qualifiers.ApplicationContext;

/** Owns the local baseline used to distinguish initial data from later external changes. */
@Singleton
public final class ProgrammedNotificationStateStore {
    private static final String PREFS = "xplorenow_programmed_notifications";
    private final SharedPreferences preferences;

    @Inject
    public ProgrammedNotificationStateStore(@ApplicationContext @NonNull Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @Nullable
    public String getFingerprint(@NonNull String uid, @NonNull String reservationId) {
        return preferences.getString(key(uid, reservationId), null);
    }

    public void putFingerprint(@NonNull String uid, @NonNull String reservationId,
                               @NonNull String fingerprint) {
        preferences.edit().putString(key(uid, reservationId), fingerprint).apply();
    }

    private static String key(String uid, String reservationId) {
        return "snapshot_" + uid + "_" + reservationId;
    }
}