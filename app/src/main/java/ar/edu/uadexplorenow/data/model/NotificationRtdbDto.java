package ar.edu.uadexplorenow.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ar.edu.uadexplorenow.domain.NotificationItem;

import java.util.Locale;

public class NotificationRtdbDto {
    @Nullable public String id;
    @Nullable public String title;
    @Nullable public String message;
    @Nullable public String body;
    @Nullable public String text;
    @Nullable public String type;
    @Nullable public String sourceType;
    @Nullable public String source_type;
    @Nullable public String sourceId;
    @Nullable public String source_id;
    @Nullable public String activityId;
    @Nullable public String activity_id;
    @Nullable public String reservationId;
    @Nullable public String reservation_id;
    @Nullable public String newsId;
    @Nullable public String news_id;
    @Nullable public String imageUrl;
    @Nullable public String image_url;
    @Nullable public String createdAt;
    @Nullable public String created_at;
    @Nullable public String readAt;
    @Nullable public String read_at;
    @Nullable public String openedAt;
    @Nullable public String opened_at;

    @Nullable
    public NotificationItem toDomain(@NonNull String fallbackId) {
        String safeTitle = firstNonBlank(title);
        String safeMessage = firstNonBlank(message, body, text);
        if (safeTitle.isEmpty() && safeMessage.isEmpty()) {
            return null;
        }

        String rawSourceType = firstNonBlank(sourceType, source_type);
        String rawSourceId = firstNonBlank(sourceId, source_id);
        if (rawSourceId.isEmpty()) {
            rawSourceId = firstNonBlank(activityId, activity_id, reservationId, reservation_id, newsId, news_id);
        }
        if (rawSourceType.isEmpty()) {
            if (!firstNonBlank(activityId, activity_id).isEmpty()) rawSourceType = NotificationItem.SOURCE_ACTIVITY;
            else if (!firstNonBlank(reservationId, reservation_id).isEmpty()) rawSourceType = NotificationItem.SOURCE_RESERVATION;
            else if (!firstNonBlank(newsId, news_id).isEmpty()) rawSourceType = NotificationItem.SOURCE_NEWS;
        }

        return new NotificationItem(
                firstNonBlank(id, fallbackId),
                safeTitle.isEmpty() ? "Notificacion" : safeTitle,
                safeMessage,
                firstNonBlank(type),
                rawSourceType.toLowerCase(Locale.ROOT),
                rawSourceId,
                firstNonBlank(imageUrl, image_url),
                firstNonBlank(createdAt, created_at),
                firstNonBlank(readAt, read_at),
                firstNonBlank(openedAt, opened_at)
        );
    }

    @NonNull
    private static String firstNonBlank(@Nullable String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }
}