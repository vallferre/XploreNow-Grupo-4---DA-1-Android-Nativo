package ar.edu.uadexplorenow.data;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

import ar.edu.uadexplorenow.data.local.db.CachedNotificationDao;
import ar.edu.uadexplorenow.data.local.db.CachedNotificationEntity;
import ar.edu.uadexplorenow.data.model.NotificationRtdbDto;
import ar.edu.uadexplorenow.data.network.RealtimeDatabaseApi;
import ar.edu.uadexplorenow.domain.NotificationItem;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@Singleton
public final class NotificationsRepository {

    private static final String TAG = "NotificationsRepository";

    public interface NotificationsCallback {
        void onResult(@NonNull List<NotificationItem> items, boolean fromCache);
    }

    public interface CountCallback {
        void onResult(int count);
    }

    public interface VoidCallback {
        void onDone(@Nullable String errorMessage);
    }

    private final RealtimeDatabaseApi api;
    private final Gson gson;
    private final CachedNotificationDao cachedNotificationDao;
    private final ExecutorService cacheExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Inject
    public NotificationsRepository(
            @NonNull RealtimeDatabaseApi api,
            @NonNull Gson gson,
            @NonNull CachedNotificationDao cachedNotificationDao
    ) {
        this.api = api;
        this.gson = gson;
        this.cachedNotificationDao = cachedNotificationDao;
    }

    public void loadAll(@NonNull String uid, @NonNull NotificationsCallback callback) {
        api.getNotifications(uid).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(@NonNull Call<JsonElement> call, @NonNull Response<JsonElement> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    loadFromCache(uid, callback);
                    return;
                }
                try {
                    List<NotificationItem> items = parseNotifications(response.body());
                    sortNewestFirst(items);
                    saveToCache(uid, items);
                    callback.onResult(items, false);
                } catch (RuntimeException e) {
                    Log.e(TAG, "No se pudieron parsear las notificaciones", e);
                    loadFromCache(uid, callback);
                }
            }

            @Override
            public void onFailure(@NonNull Call<JsonElement> call, @NonNull Throwable t) {
                loadFromCache(uid, callback);
            }
        });
    }

    public void unreadCount(@NonNull String uid, @NonNull CountCallback callback) {
        loadAll(uid, (items, fromCache) -> {
            int count = 0;
            for (NotificationItem item : items) {
                if (!item.isRead()) count++;
            }
            callback.onResult(count);
        });
    }

    @NonNull
    public List<NotificationItem> refreshBlocking(@NonNull String uid) throws IOException {
        Response<JsonElement> response = api.getNotifications(uid).execute();
        if (!response.isSuccessful() || response.body() == null) {
            throw new IOException("HTTP " + response.code());
        }
        try {
            List<NotificationItem> items = parseNotifications(response.body());
            sortNewestFirst(items);
            replaceCache(uid, items);
            return items;
        } catch (RuntimeException e) {
            throw new IOException("No se pudieron parsear las notificaciones", e);
        }
    }

    @NonNull
    public List<NotificationItem> loadCachedBlocking(@NonNull String uid) {
        try {
            return CachedNotificationEntity.toList(cachedNotificationDao.getAllByUid(uid));
        } catch (RuntimeException e) {
            Log.e(TAG, "No se pudo leer el cache de notificaciones", e);
            return Collections.emptyList();
        }
    }

    public void markRead(@NonNull String uid, @NonNull NotificationItem item, @NonNull VoidCallback callback) {
        if (item.isRead()) {
            callback.onDone(null);
            return;
        }
        patchTimestamp(uid, item.id, "read_at", Instant.now().toString(), false, callback);
    }

    public void markOpened(@NonNull String uid, @NonNull NotificationItem item, @NonNull VoidCallback callback) {
        String now = Instant.now().toString();
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("read_at", now);
        updates.put("readAt", now);
        updates.put("opened_at", now);
        updates.put("openedAt", now);
        api.patchNotification(uid, item.id, updates).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                cacheExecutor.execute(() -> cachedNotificationDao.markOpened(uid, item.id, now));
                callback.onDone(response.isSuccessful() ? null : "HTTP " + response.code());
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                cacheExecutor.execute(() -> cachedNotificationDao.markOpened(uid, item.id, now));
                callback.onDone(t.getMessage() != null ? t.getMessage() : "network");
            }
        });
    }

    private void patchTimestamp(
            @NonNull String uid,
            @NonNull String notificationId,
            @NonNull String key,
            @NonNull String value,
            boolean opened,
            @NonNull VoidCallback callback
    ) {
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put(key, value);
        if ("read_at".equals(key)) updates.put("readAt", value);
        api.patchNotification(uid, notificationId, updates).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                cacheExecutor.execute(() -> {
                    if (opened) cachedNotificationDao.markOpened(uid, notificationId, value);
                    else cachedNotificationDao.markRead(uid, notificationId, value);
                });
                callback.onDone(response.isSuccessful() ? null : "HTTP " + response.code());
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                cacheExecutor.execute(() -> cachedNotificationDao.markRead(uid, notificationId, value));
                callback.onDone(t.getMessage() != null ? t.getMessage() : "network");
            }
        });
    }

    private void saveToCache(@NonNull String uid, @NonNull List<NotificationItem> items) {
        List<NotificationItem> snapshot = new ArrayList<>(items);
        cacheExecutor.execute(() -> {
            try {
                replaceCache(uid, snapshot);
            } catch (RuntimeException e) {
                Log.e(TAG, "No se pudo guardar el cache de notificaciones", e);
            }
        });
    }

    private void replaceCache(@NonNull String uid, @NonNull List<NotificationItem> items) {
        cachedNotificationDao.deleteByUid(uid);
        cachedNotificationDao.insertAll(CachedNotificationEntity.fromList(uid, items));
    }

    private void loadFromCache(@NonNull String uid, @NonNull NotificationsCallback callback) {
        cacheExecutor.execute(() -> {
            try {
                List<NotificationItem> cached = CachedNotificationEntity.toList(cachedNotificationDao.getAllByUid(uid));
                sortNewestFirst(cached);
                mainHandler.post(() -> callback.onResult(cached, true));
            } catch (RuntimeException e) {
                Log.e(TAG, "No se pudo leer el cache de notificaciones", e);
                mainHandler.post(() -> callback.onResult(Collections.emptyList(), true));
            }
        });
    }

    @NonNull
    private List<NotificationItem> parseNotifications(@NonNull JsonElement root) {
        if (!root.isJsonObject()) {
            return Collections.emptyList();
        }
        JsonObject obj = root.getAsJsonObject();
        List<NotificationItem> out = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            try {
                NotificationRtdbDto dto = gson.fromJson(entry.getValue(), NotificationRtdbDto.class);
                if (dto == null) continue;
                NotificationItem item = dto.toDomain(entry.getKey());
                if (item != null) out.add(item);
            } catch (RuntimeException e) {
                Log.w(TAG, "Notificacion ignorada por formato invalido: " + entry.getKey(), e);
            }
        }
        return out;
    }

    private static void sortNewestFirst(@NonNull List<NotificationItem> items) {
        items.sort(Comparator.comparingLong(NotificationItem::createdAtMillis).reversed());
    }
}
