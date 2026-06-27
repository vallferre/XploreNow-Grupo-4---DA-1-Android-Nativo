package ar.edu.uadexplorenow.data.local.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;

import java.util.ArrayList;
import java.util.List;

import ar.edu.uadexplorenow.domain.NotificationItem;

@Entity(
        tableName = "cached_notifications",
        primaryKeys = {"uid", "notification_id"},
        indices = {@Index(value = "uid")}
)
public class CachedNotificationEntity {
    @NonNull
    @ColumnInfo(name = "notification_id")
    public String notificationId = "";
    @NonNull
    @ColumnInfo(name = "uid")
    public String uid = "";

    @ColumnInfo(name = "title")
    public String title = "";

    @ColumnInfo(name = "message")
    public String message = "";

    @ColumnInfo(name = "type")
    public String type = "";

    @ColumnInfo(name = "source_type")
    public String sourceType = "";

    @ColumnInfo(name = "source_id")
    public String sourceId = "";

    @ColumnInfo(name = "image_url")
    public String imageUrl = "";

    @ColumnInfo(name = "created_at")
    public String createdAt = "";

    @ColumnInfo(name = "read_at")
    public String readAt = "";

    @ColumnInfo(name = "opened_at")
    public String openedAt = "";

    public static CachedNotificationEntity from(@NonNull String uid, @NonNull NotificationItem item) {
        CachedNotificationEntity entity = new CachedNotificationEntity();
        entity.notificationId = item.id;
        entity.uid = uid;
        entity.title = item.title;
        entity.message = item.message;
        entity.type = item.type;
        entity.sourceType = NotificationItem.normalizeSourceType(item.sourceType);
        entity.sourceId = item.sourceId;
        entity.imageUrl = item.imageUrl;
        entity.createdAt = item.createdAt;
        entity.readAt = item.readAt;
        entity.openedAt = item.openedAt;
        return entity;
    }

    public NotificationItem toDomain() {
        return new NotificationItem(
                notificationId,
                title,
                message,
                type,
                sourceType,
                sourceId,
                imageUrl,
                createdAt,
                readAt,
                openedAt
        );
    }

    public static List<CachedNotificationEntity> fromList(
            @NonNull String uid,
            @NonNull List<NotificationItem> items
    ) {
        List<CachedNotificationEntity> out = new ArrayList<>(items.size());
        for (NotificationItem item : items) {
            out.add(from(uid, item));
        }
        return out;
    }

    public static List<NotificationItem> toList(@NonNull List<CachedNotificationEntity> entities) {
        List<NotificationItem> out = new ArrayList<>(entities.size());
        for (CachedNotificationEntity entity : entities) {
            out.add(entity.toDomain());
        }
        return out;
    }
}
