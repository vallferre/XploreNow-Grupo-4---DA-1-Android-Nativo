package ar.edu.uadexplorenow.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ar.edu.uadexplorenow.data.model.UserRtdbDto;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class ActivityHistoryItem {

    private static final Locale HISTORY_LOCALE = new Locale("es", "AR");
    private static final DateTimeFormatter DATE_SHORT =
            DateTimeFormatter.ofPattern("dd MMM yyyy", HISTORY_LOCALE);
    private static final DateTimeFormatter DATE_LONG =
            DateTimeFormatter.ofPattern("dd MMM yyyy 'a las' HH:mm", HISTORY_LOCALE);

    public final String recordKey;
    public final String activityId;
    public final String name;
    public final String destination;
    public final String guideName;
    public final String category;
    public final long durationMinutes;
    @Nullable
    public final Double userRating;
    public final String activityDateValue;
    public final String imageUrl;
    public final boolean hasDetail;

    private final long sortEpochMillis;
    @Nullable
    private final LocalDate localDate;
    private final String normalizedSearchIndex;

    private ActivityHistoryItem(
            @NonNull String recordKey,
            @NonNull String activityId,
            @NonNull String name,
            @NonNull String destination,
            @NonNull String guideName,
            @NonNull String category,
            long durationMinutes,
            @Nullable Double userRating,
            @NonNull String activityDateValue,
            @NonNull String imageUrl,
            boolean hasDetail,
            long sortEpochMillis,
            @Nullable LocalDate localDate
    ) {
        this.recordKey = recordKey;
        this.activityId = activityId;
        this.name = name;
        this.destination = destination;
        this.guideName = guideName;
        this.category = category;
        this.durationMinutes = durationMinutes;
        this.userRating = userRating;
        this.activityDateValue = activityDateValue;
        this.imageUrl = imageUrl;
        this.hasDetail = hasDetail;
        this.sortEpochMillis = sortEpochMillis;
        this.localDate = localDate;
        this.normalizedSearchIndex = normalizeForSearch(
                name + " " + destination + " " + guideName + " " + category + " " + formattedDateShort());
    }

    @NonNull
    public static List<ActivityHistoryItem> buildHistory(
            @Nullable UserRtdbDto user,
            @NonNull java.util.Map<String, ActivityDetail> detailById
    ) {
        List<Seed> seeds = parseHistorySeeds(user);
        List<ActivityHistoryItem> out = new ArrayList<>();
        for (Seed seed : seeds) {
            ActivityDetail detail = !seed.activityId.isEmpty() ? detailById.get(seed.activityId) : null;
            ActivityHistoryItem item = fromSeed(seed, detail);
            if (item != null) {
                out.add(item);
            }
        }
        out.sort(Comparator.comparingLong(ActivityHistoryItem::sortEpochMillis).reversed()
                .thenComparing(item -> item.name.toLowerCase(Locale.ROOT)));
        return out;
    }

    private static long sortEpochMillis(@NonNull ActivityHistoryItem item) {
        return item.sortEpochMillis;
    }

    @Nullable
    private static ActivityHistoryItem fromSeed(@NonNull Seed seed, @Nullable ActivityDetail detail) {
        String activityId = firstNonBlank(seed.activityId, detail != null ? detail.id : null);
        String recordKey = !seed.recordKey.isEmpty()
                ? seed.recordKey
                : (!activityId.isEmpty() ? activityId : "history_" + System.nanoTime());
        String name = firstNonBlank(seed.name, detail != null ? detail.name : null);
        if (name.isEmpty()) {
            return null;
        }

        String destination = firstNonBlank(seed.destination, detail != null ? detail.destination : null);
        String guideName = firstNonBlank(seed.guideName, detail != null ? detail.guideName : null);
        String category = firstNonBlank(seed.category, detail != null ? detail.category : null);
        long duration = seed.durationMinutes != null
                ? seed.durationMinutes
                : (detail != null ? detail.durationMinutes : 0L);
        String dateValue = firstNonBlank(
                seed.activityDateValue,
                detail != null ? detail.dateIso : null,
                seed.recordedAtValue);
        String imageUrl = detail != null && !detail.imageUrls.isEmpty() ? detail.imageUrls.get(0) : "";
        DateInfo dateInfo = resolveDateInfo(dateValue);

        return new ActivityHistoryItem(
                recordKey,
                activityId,
                name,
                destination,
                guideName,
                category,
                duration,
                seed.userRating,
                dateValue,
                imageUrl,
                detail != null && !activityId.isEmpty(),
                dateInfo.epochMillis,
                dateInfo.localDate
        );
    }

    @NonNull
    private static List<Seed> parseHistorySeeds(@Nullable UserRtdbDto user) {
        List<Seed> out = new ArrayList<>();
        if (user == null) {
            return out;
        }

        parseRawHistory(user.activityHistory, "", out);
        if (!out.isEmpty()) {
            return out;
        }

        if (user.completedActivityIds != null) {
            for (int i = 0; i < user.completedActivityIds.size(); i++) {
                String id = safe(user.completedActivityIds.get(i));
                if (id.isEmpty()) continue;
                Seed seed = new Seed();
                seed.recordKey = "completed_" + i + "_" + id;
                seed.activityId = id;
                out.add(seed);
            }
        }
        return out;
    }

    private static void parseRawHistory(
            @Nullable JsonElement raw,
            @NonNull String pathKey,
            @NonNull List<Seed> out
    ) {
        if (raw == null || raw.isJsonNull()) {
            return;
        }

        if (raw.isJsonArray()) {
            JsonArray array = raw.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                parseRawHistory(array.get(i), pathKey + "_" + i, out);
            }
            return;
        }

        if (raw.isJsonObject()) {
            JsonObject obj = raw.getAsJsonObject();
            if (looksLikeHistoryEntry(obj)) {
                Seed seed = parseSeed(obj, pathKey);
                if (seed != null) {
                    out.add(seed);
                }
                return;
            }

            for (String key : obj.keySet()) {
                parseRawHistory(obj.get(key), key, out);
            }
            return;
        }

        if (raw.isJsonPrimitive()) {
            Seed seed = parsePrimitiveSeed(raw.getAsJsonPrimitive(), pathKey);
            if (seed != null) {
                out.add(seed);
            }
        }
    }

    private static boolean looksLikeHistoryEntry(@NonNull JsonObject obj) {
        return hasAny(obj,
                "activity_id", "activityId", "id",
                "name", "activity_name", "activityName",
                "date", "activity_date", "activityDate", "completed_at", "completedAt",
                "guide_name", "guideName", "guide",
                "rating", "user_rating", "userRating", "calificacion");
    }

    @Nullable
    private static Seed parseSeed(@NonNull JsonObject obj, @NonNull String pathKey) {
        Seed seed = new Seed();
        seed.recordKey = safe(pathKey);
        seed.activityId = firstNonBlank(
                stringOrNumber(obj, "activity_id"),
                stringOrNumber(obj, "activityId"),
                stringOrNumber(obj, "id"),
                safe(pathKey));
        seed.name = firstNonBlank(
                stringOrNumber(obj, "activity_name"),
                stringOrNumber(obj, "activityName"),
                stringOrNumber(obj, "name"));
        seed.destination = firstNonBlank(
                stringOrNumber(obj, "destination"),
                stringOrNumber(obj, "city"));
        seed.guideName = firstNonBlank(
                stringOrNumber(obj, "guide_name"),
                stringOrNumber(obj, "guideName"),
                stringOrNumber(obj, "guide"));
        seed.category = firstNonBlank(
                stringOrNumber(obj, "category"),
                stringOrNumber(obj, "activity_category"),
                stringOrNumber(obj, "activityCategory"));
        seed.activityDateValue = firstNonBlank(
                stringOrNumber(obj, "activity_date"),
                stringOrNumber(obj, "activityDate"),
                stringOrNumber(obj, "date"));
        seed.recordedAtValue = firstNonBlank(
                stringOrNumber(obj, "completed_at"),
                stringOrNumber(obj, "completedAt"),
                stringOrNumber(obj, "recorded_at"),
                stringOrNumber(obj, "recordedAt"));
        seed.userRating = firstDouble(
                doubleValue(obj, "user_rating"),
                doubleValue(obj, "userRating"),
                doubleValue(obj, "rating"),
                doubleValue(obj, "calificacion"));
        seed.durationMinutes = firstLong(
                longValue(obj, "duration_minutes"),
                longValue(obj, "durationMinutes"),
                longValue(obj, "duration"));
        return seed.hasUsefulContent() ? seed : null;
    }

    @Nullable
    private static Seed parsePrimitiveSeed(@NonNull JsonPrimitive primitive, @NonNull String pathKey) {
        if (primitive.isBoolean()) {
            if (!primitive.getAsBoolean()) {
                return null;
            }
            Seed seed = new Seed();
            seed.recordKey = safe(pathKey);
            seed.activityId = safe(pathKey);
            return seed;
        }

        if (primitive.isString() || primitive.isNumber()) {
            String value = safe(primitive.getAsString());
            if (value.isEmpty() && pathKey.isEmpty()) {
                return null;
            }
            Seed seed = new Seed();
            seed.recordKey = safe(pathKey);
            if (!pathKey.isEmpty()) {
                seed.activityId = pathKey;
                seed.activityDateValue = value;
            } else {
                seed.activityId = value;
            }
            return seed;
        }
        return null;
    }

    @Nullable
    private static Double doubleValue(@NonNull JsonObject obj, @NonNull String key) {
        JsonElement value = obj.get(key);
        if (value == null || value.isJsonNull()) return null;
        try {
            if (value.isJsonPrimitive()) {
                JsonPrimitive primitive = value.getAsJsonPrimitive();
                if (primitive.isNumber()) return primitive.getAsDouble();
                if (primitive.isString()) return Double.parseDouble(primitive.getAsString().trim());
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    @Nullable
    private static Long longValue(@NonNull JsonObject obj, @NonNull String key) {
        JsonElement value = obj.get(key);
        if (value == null || value.isJsonNull()) return null;
        try {
            if (value.isJsonPrimitive()) {
                JsonPrimitive primitive = value.getAsJsonPrimitive();
                if (primitive.isNumber()) return primitive.getAsLong();
                if (primitive.isString()) return Long.parseLong(primitive.getAsString().trim());
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    @Nullable
    private static String stringOrNumber(@NonNull JsonObject obj, @NonNull String key) {
        JsonElement value = obj.get(key);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) return null;
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (primitive.isString() || primitive.isNumber()) {
            return safe(primitive.getAsString());
        }
        return null;
    }

    private static boolean hasAny(@NonNull JsonObject obj, @NonNull String... keys) {
        for (String key : keys) {
            if (obj.has(key)) return true;
        }
        return false;
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

    @Nullable
    private static Double firstDouble(@Nullable Double... values) {
        if (values == null) return null;
        for (Double value : values) {
            if (value != null) return value;
        }
        return null;
    }

    @Nullable
    private static Long firstLong(@Nullable Long... values) {
        if (values == null) return null;
        for (Long value : values) {
            if (value != null) return value;
        }
        return null;
    }

    @NonNull
    private static String safe(@Nullable String value) {
        return value != null ? value.trim() : "";
    }

    @NonNull
    private static DateInfo resolveDateInfo(@Nullable String raw) {
        String value = safe(raw);
        if (value.isEmpty()) {
            return new DateInfo(0L, null);
        }

        try {
            if (value.matches("^\\d{12,}$")) {
                long millis = Long.parseLong(value);
                ZonedDateTime zonedDateTime = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault());
                return new DateInfo(millis, zonedDateTime.toLocalDate());
            }
        } catch (NumberFormatException ignored) {
        }

        try {
            ZonedDateTime zonedDateTime = Instant.parse(value).atZone(ZoneId.systemDefault());
            return new DateInfo(zonedDateTime.toInstant().toEpochMilli(), zonedDateTime.toLocalDate());
        } catch (DateTimeParseException ignored) {
        }

        try {
            ZonedDateTime zonedDateTime = OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault());
            return new DateInfo(zonedDateTime.toInstant().toEpochMilli(), zonedDateTime.toLocalDate());
        } catch (DateTimeParseException ignored) {
        }

        try {
            LocalDateTime localDateTime = LocalDateTime.parse(value);
            ZonedDateTime zonedDateTime = localDateTime.atZone(ZoneId.systemDefault());
            return new DateInfo(zonedDateTime.toInstant().toEpochMilli(), zonedDateTime.toLocalDate());
        } catch (DateTimeParseException ignored) {
        }

        try {
            LocalDate localDate = LocalDate.parse(value);
            return new DateInfo(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), localDate);
        } catch (DateTimeParseException ignored) {
        }

        return new DateInfo(0L, null);
    }

    @NonNull
    private static ZonedDateTime parseDisplayDate(@NonNull String value) {
        DateInfo info = resolveDateInfo(value);
        if (info.localDate == null) {
            return ZonedDateTime.now();
        }
        if (info.epochMillis > 0) {
            return Instant.ofEpochMilli(info.epochMillis).atZone(ZoneId.systemDefault());
        }
        return info.localDate.atStartOfDay(ZoneId.systemDefault());
    }

    @Nullable
    public LocalDate localDate() {
        return localDate;
    }

    public boolean matchesQuery(@Nullable String rawQuery) {
        String normalized = normalizeForSearch(rawQuery);
        return normalized.isEmpty() || normalizedSearchIndex.contains(normalized);
    }

    @NonNull
    public String formattedDateShort() {
        if (activityDateValue.isEmpty()) return "Sin fecha";
        DateInfo info = resolveDateInfo(activityDateValue);
        if (info.localDate == null) return activityDateValue;
        return info.localDate.format(DATE_SHORT);
    }

    @NonNull
    public String formattedDateLong() {
        if (activityDateValue.isEmpty()) return "Sin fecha";
        DateInfo info = resolveDateInfo(activityDateValue);
        if (info.localDate == null) return activityDateValue;
        return parseDisplayDate(activityDateValue).format(DATE_LONG);
    }

    @NonNull
    public String formattedDuration() {
        if (durationMinutes <= 0) return "Duracion no informada";
        if (durationMinutes % 60 == 0) {
            long hours = durationMinutes / 60;
            return hours == 1 ? "1 hora" : hours + " horas";
        }
        long hours = durationMinutes / 60;
        long minutes = durationMinutes % 60;
        if (hours == 0) return minutes + " min";
        return (hours == 1 ? "1 hora" : hours + " horas") + " " + minutes + " min";
    }

    @NonNull
    public String guideLabel() {
        return guideName.isEmpty() ? "Guia no informada" : "Guia: " + guideName;
    }

    @NonNull
    public String ratingLabel() {
        if (userRating == null || userRating < 0) {
            return "Sin calificacion";
        }
        return String.format(Locale.getDefault(), "Tu calificacion: %.1f", userRating);
    }

    @NonNull
    public String destinationLabel() {
        return destination.isEmpty() ? "Destino no informado" : destination;
    }

    @NonNull
    public String categoryLabel() {
        return category.isEmpty() ? "" : ActivityItem.categoryLabel(category);
    }

    @NonNull
    private static String normalizeForSearch(@Nullable String raw) {
        if (raw == null) return "";
        String normalized = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static final class Seed {
        String recordKey = "";
        String activityId = "";
        String name = "";
        String destination = "";
        String guideName = "";
        String category = "";
        String activityDateValue = "";
        String recordedAtValue = "";
        @Nullable
        Long durationMinutes;
        @Nullable
        Double userRating;

        boolean hasUsefulContent() {
            return !activityId.isEmpty()
                    || !name.isEmpty()
                    || !destination.isEmpty()
                    || !guideName.isEmpty()
                    || !activityDateValue.isEmpty()
                    || !recordedAtValue.isEmpty()
                    || userRating != null
                    || durationMinutes != null;
        }
    }

    private static final class DateInfo {
        final long epochMillis;
        @Nullable
        final LocalDate localDate;

        DateInfo(long epochMillis, @Nullable LocalDate localDate) {
            this.epochMillis = epochMillis;
            this.localDate = localDate;
        }
    }
}
