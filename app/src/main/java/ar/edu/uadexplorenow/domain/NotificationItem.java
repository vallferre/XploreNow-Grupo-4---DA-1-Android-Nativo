package ar.edu.uadexplorenow.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;

public final class NotificationItem {

    public static final String SOURCE_ACTIVITY = "activity";
    public static final String SOURCE_RESERVATION = "reservation";
    public static final String SOURCE_NEWS = "news";

    @NonNull public final String id;
    @NonNull public final String title;
    @NonNull public final String message;
    @NonNull public final String type;
    @NonNull public final String sourceType;
    @NonNull public final String sourceId;
    @NonNull public final String imageUrl;
    @NonNull public final String createdAt;
    @NonNull public final String readAt;
    @NonNull public final String openedAt;

    public NotificationItem(
            @NonNull String id,
            @NonNull String title,
            @NonNull String message,
            @NonNull String type,
            @NonNull String sourceType,
            @NonNull String sourceId,
            @NonNull String imageUrl,
            @NonNull String createdAt,
            @NonNull String readAt,
            @NonNull String openedAt
    ) {
        this.id = id;
        this.title = title;
        this.message = message;
        this.type = type;
        this.sourceType = normalizeSourceType(sourceType);
        this.sourceId = sourceId;
        this.imageUrl = imageUrl;
        this.createdAt = createdAt;
        this.readAt = readAt;
        this.openedAt = openedAt;
    }

    public boolean isRead() {
        return !readAt.trim().isEmpty() || !openedAt.trim().isEmpty();
    }

    public boolean isOpened() {
        return !openedAt.trim().isEmpty();
    }

    public long createdAtMillis() {
        Instant instant = parseInstant(createdAt);
        return instant != null ? instant.toEpochMilli() : 0L;
    }

    @NonNull
    public String relativeTimeLabel() {
        Instant instant = parseInstant(createdAt);
        if (instant == null) {
            return "Hace un momento";
        }
        long minutes = Math.max(0, Duration.between(instant, Instant.now()).toMinutes());
        if (minutes < 1) return "Hace un momento";
        if (minutes < 60) return "Hace " + minutes + " min";
        long hours = minutes / 60;
        if (hours < 24) return "Hace " + hours + " h";
        long days = hours / 24;
        if (days < 7) return "Hace " + days + " d";
        long weeks = days / 7;
        return "Hace " + weeks + " sem";
    }

    @NonNull
    public String sourceLabel() {
        switch (normalizeSourceType(sourceType)) {
            case SOURCE_ACTIVITY:
                return "Actividad";
            case SOURCE_RESERVATION:
                return "Reserva";
            case SOURCE_NEWS:
                return "Novedad";
            default:
                return type.trim().isEmpty() ? "XploreNow" : type.trim();
        }
    }

    @NonNull
    public static String normalizeSourceType(@Nullable String rawSourceType) {
        if (rawSourceType == null) return "";
        String normalized = rawSourceType.trim()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        switch (normalized) {
            case "activity":
            case "activities":
            case "activity_detail":
            case "actividad":
            case "actividades":
                return SOURCE_ACTIVITY;
            case "reservation":
            case "reservations":
            case "reservation_detail":
            case "booking":
            case "bookings":
            case "reserva":
            case "reservas":
                return SOURCE_RESERVATION;
            case "news":
            case "news_detail":
            case "novedad":
            case "novedades":
                return SOURCE_NEWS;
            default:
                return normalized;
        }
    }

    @Nullable
    private static Instant parseInstant(@Nullable String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
