package ar.edu.uadexplorenow.notifications;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import ar.edu.uadexplorenow.data.NotificationsRepository;
import ar.edu.uadexplorenow.data.SessionStore;
import ar.edu.uadexplorenow.domain.NotificationItem;
import dagger.hilt.android.qualifiers.ApplicationContext;

@Singleton
public final class NotificationForegroundPoller {

    private static final long INITIAL_POLL_DELAY_MS = 5_000L;
    private static final long POLL_INTERVAL_MS = 60_000L;

    private final Context appContext;
    private final NotificationsRepository repository;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running;

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            pollOnce();
            handler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    @Inject
    public NotificationForegroundPoller(
            @ApplicationContext @NonNull Context appContext,
            @NonNull NotificationsRepository repository
    ) {
        this.appContext = appContext;
        this.repository = repository;
    }

    public void start() {
        if (running) return;
        running = true;
        handler.postDelayed(pollRunnable, INITIAL_POLL_DELAY_MS);
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(pollRunnable);
    }

    private void pollOnce() {
        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) return;
            String uid = SessionStore.getEffectiveUid(appContext, user);
            repository.loadAll(uid, (items, fromCache) -> notifyNewUnread(items));
        } catch (RuntimeException ignored) {
        }
    }

    private void notifyNewUnread(@NonNull List<NotificationItem> items) {
        for (NotificationItem item : items) {
            try {
                if (!item.isRead() && !NotificationDeliveryStore.wasNotified(appContext, item)) {
                    LocalNotificationPresenter.show(appContext, item);
                    NotificationDeliveryStore.markNotified(appContext, item);
                }
            } catch (RuntimeException ignored) {
            }
        }
    }
}