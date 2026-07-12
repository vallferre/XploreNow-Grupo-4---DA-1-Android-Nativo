package ar.edu.uadexplorenow.notifications;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.List;

import ar.edu.uadexplorenow.data.NotificationsRepository;
import ar.edu.uadexplorenow.data.SessionStore;
import ar.edu.uadexplorenow.domain.NotificationItem;
import dagger.hilt.EntryPoint;
import dagger.hilt.InstallIn;
import dagger.hilt.android.EntryPointAccessors;
import dagger.hilt.components.SingletonComponent;

public final class NotificationPollingWorker extends Worker {

    @EntryPoint
    @InstallIn(SingletonComponent.class)
    public interface Dependencies {
        NotificationsRepository notificationsRepository();
        ProgrammedNotificationsRepository programmedNotificationsRepository();
    }

    public NotificationPollingWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return Result.success();
        }

        Context context = getApplicationContext();
        String uid = SessionStore.getEffectiveUid(context, user);
        if (uid.trim().isEmpty()) {
            return Result.success();
        }

        try {
            Dependencies deps = EntryPointAccessors.fromApplication(context, Dependencies.class);
            deps.programmedNotificationsRepository().syncBlocking(uid);
            List<NotificationItem> items = deps.notificationsRepository().refreshBlocking(uid);
            for (NotificationItem item : items) {
                if (!item.isRead() && !NotificationDeliveryStore.wasNotified(context, uid, item)) {
                    if (LocalNotificationPresenter.show(context, item)) {
                        NotificationDeliveryStore.markNotified(context, uid, item);
                    }
                }
            }
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }
}
