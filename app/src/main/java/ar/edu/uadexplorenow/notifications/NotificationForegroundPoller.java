package ar.edu.uadexplorenow.notifications;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Singleton;
import ar.edu.uadexplorenow.data.NotificationsRepository;
import ar.edu.uadexplorenow.data.SessionStore;
import ar.edu.uadexplorenow.domain.NotificationItem;
import dagger.hilt.android.qualifiers.ApplicationContext;

@Singleton
public final class NotificationForegroundPoller {
    private static final String TAG = "NotificationPoller";
    private static final long INITIAL_POLL_DELAY_MS = 5_000L;
    private static final long POLL_INTERVAL_MS = 60_000L;
    private final Context appContext;
    private final NotificationsRepository repository;
    private final ProgrammedNotificationsRepository programmedNotificationsRepository;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService syncExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean syncInProgress = new AtomicBoolean(false);
    private boolean running;

    private final Runnable pollRunnable = new Runnable() {
        @Override public void run() {
            if (!running) return;
            pollOnce();
            handler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    @Inject
    public NotificationForegroundPoller(@ApplicationContext @NonNull Context appContext,
                                        @NonNull NotificationsRepository repository,
                                        @NonNull ProgrammedNotificationsRepository programmedNotificationsRepository) {
        this.appContext = appContext;
        this.repository = repository;
        this.programmedNotificationsRepository = programmedNotificationsRepository;
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

    public void pollNow() {
        if (running) handler.post(this::pollOnce);
    }

    private void pollOnce() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || !syncInProgress.compareAndSet(false, true)) return;
        String uid = SessionStore.getEffectiveUid(appContext, user);
        if (uid.trim().isEmpty()) {
            syncInProgress.set(false);
            return;
        }
        syncExecutor.execute(() -> {
            try {
                programmedNotificationsRepository.syncBlocking(uid);
            } catch (Exception error) {
                Log.w(TAG, "No se pudieron sincronizar cambios de reservas", error);
            }
            repository.loadAll(uid, (items, fromCache) -> {
                try {
                    notifyNewUnread(uid, items);
                } finally {
                    syncInProgress.set(false);
                }
            });
        });
    }

    private void notifyNewUnread(@NonNull String uid, @NonNull List<NotificationItem> items) {
        for (NotificationItem item : items) {
            try {
                if (!item.isRead() && !NotificationDeliveryStore.wasNotified(appContext, uid, item)
                        && LocalNotificationPresenter.show(appContext, item)) {
                    NotificationDeliveryStore.markNotified(appContext, uid, item);
                }
            } catch (RuntimeException error) {
                Log.w(TAG, "No se pudo presentar la notificacion " + item.id, error);
            }
        }
    }
}