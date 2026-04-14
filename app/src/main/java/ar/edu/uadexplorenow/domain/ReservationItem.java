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
import java.util.Map;

public final class ReservationItem {

    private static final Locale LOCALE = new Locale("es", "AR");
    private static final DateTimeFormatter DATE_SHORT =
            DateTimeFormatter.ofPattern("dd MMM yyyy", LOCALE);
    private static final DateTimeFormatter DATE_LONG =
            DateTimeFormatter.ofPattern("dd MMM yyyy 'a las' HH:mm", LOCALE);
    private static final DateTimeFormatter TIME_ONLY =
            DateTimeFormatter.ofPattern("HH:mm", LOCALE);

    public final String reservationId;
    public final String activityId;
    public final String activityName;
    public final String destination;
    public final String guideName;
    public final String category;
    public final long durationMinutes;
    public final int participants;
    public final String status;
    public final String scheduledAtValue;
    public final String selectedDateLabel;
    public final String selectedTimeLabel;
    public final String imageUrl;
    public final String meetingPoint;
    public final double pricePerPerson;
    public final double totalPrice;
    public final String currency;
    public final String description;
    public final String cancellationType;
    public final String cancellationDescription;
    public final long cancellationFreeHours;

    private final long sortEpochMillis;
    @Nullable
    private final LocalDate localDate;
    private final String normalizedSearchIndex;

    private ReservationItem(
            @NonNull String reservationId,
            @NonNull String activityId,
            @NonNull String activityName,
            @NonNull String destination,
            @NonNull String guideName,
            @NonNull String category,
            long durationMinutes,
            int participants,
            @NonNull String status,
            @NonNull String scheduledAtValue,
            @NonNull String selectedDateLabel,
            @NonNull String selectedTimeLabel,
            @NonNull String imageUrl,
            @NonNull String meetingPoint,
            double pricePerPerson,
            double totalPrice,
            @NonNull String currency,
            @NonNull String description,
            @NonNull String cancellationType,
            @NonNull String cancellationDescription,
            long cancellationFreeHours,
            long sortEpochMillis,
            @Nullable LocalDate localDate
    ) {
        this.reservationId = reservationId;
        this.activityId = activityId;
        this.activityName = activityName;
        this.destination = destination;
        this.guideName = guideName;
        this.category = category;
        this.durationMinutes = durationMinutes;
        this.participants = participants;
        this.status = ReservationStatus.normalize(status);
        this.scheduledAtValue = scheduledAtValue;
        this.selectedDateLabel = selectedDateLabel;
        this.selectedTimeLabel = selectedTimeLabel;
        this.imageUrl = imageUrl;
        this.meetingPoint = meetingPoint;
        this.pricePerPerson = pricePerPerson;
        this.totalPrice = totalPrice;
        this.currency = currency;
        this.description = description;
        this.cancellationType = cancellationType;
        this.cancellationDescription = cancellationDescription;
        this.cancellationFreeHours = cancellationFreeHours;
        this.sortEpochMillis = sortEpochMillis;
        this.localDate = localDate;
        this.normalizedSearchIndex = normalizeForSearch(
                activityName + " " + destination + " " + guideName + " " + category + " "
                        + selectedDateLabel + " " + selectedTimeLabel + " " + ReservationStatus.label(status));
    }

    @NonNull
    public static List<ReservationItem> buildList(
            @Nullable UserRtdbDto user,
            @NonNull Map<String, ActivityDetail> detailById
    ) {
        List<ReservationItem> out = new ArrayList<>();
        if (user != null) {
            parseReservationRoots(user.reservations, detailById, out);
        }
        if (!out.isEmpty()) {
            sort(out);
            return out;
        }

        List<ActivityHistoryItem> legacy = ActivityHistoryItem.buildHistory(user, detailById);
        for (ActivityHistoryItem item : legacy) {
            DateInfo dateInfo = resolveDateInfo(item.activityDateValue);
            out.add(new ReservationItem(
                    item.recordKey,
                    item.activityId,
                    item.name,
                    item.destination,
                    item.guideName,
                    item.category,
                    item.durationMinutes,
                    1,
                    ReservationStatus.FINISHED,
                    item.activityDateValue,
                    item.formattedDateShort(),
                    "",
                    item.imageUrl,
                    "",
                    0,
                    0,
                    "",
                    "",
                    "",
                    "",
                    0,
                    dateInfo.epochMillis,
                    dateInfo.localDate
            ));
        }
        sort(out);
        return out;
    }

    private static void sort(@NonNull List<ReservationItem> items) {
        items.sort(Comparator.comparingLong(ReservationItem::sortEpochMillis).reversed()
                .thenComparing(item -> item.activityName.toLowerCase(Locale.ROOT)));
    }

    private static long sortEpochMillis(@NonNull ReservationItem item) {
        return item.sortEpochMillis;
    }

    private static void parseReservationRoots(
            @Nullable JsonElement raw,
            @NonNull Map<String, ActivityDetail> detailById,
            @NonNull List<ReservationItem> out
    ) {
        if (raw == null || raw.isJsonNull()) return;
        if (raw.isJsonObject()) {
            JsonObject obj = raw.getAsJsonObject();
            for (String key : obj.keySet()) {
                ReservationItem item = fromJson(obj.get(key), key, detailById);
                if (item != null) {
                    out.add(item);
                }
            }
            return;
        }
        if (raw.isJsonArray()) {
            JsonArray array = raw.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                ReservationItem item = fromJson(array.get(i), "reservation_" + i, detailById);
                if (item != null) {
                    out.add(item);
                }
            }
        }
    }

    @Nullable
    private static ReservationItem fromJson(
            @Nullable JsonElement raw,
            @NonNull String fallbackId,
            @NonNull Map<String, ActivityDetail> detailById
    ) {
        if (raw == null || raw.isJsonNull() || !raw.isJsonObject()) {
            return null;
        }
        JsonObject obj = raw.getAsJsonObject();
        String reservationId = firstNonBlank(
                stringOrNumber(obj, "id"),
                stringOrNumber(obj, "reservation_id"),
                stringOrNumber(obj, "reservationId"),
                fallbackId);
        String activityId = firstNonBlank(
                stringOrNumber(obj, "activity_id"),
                stringOrNumber(obj, "activityId"));
        ActivityDetail detail = !activityId.isEmpty() ? detailById.get(activityId) : null;

        String activityName = firstNonBlank(
                stringOrNumber(obj, "activity_name"),
                stringOrNumber(obj, "activityName"),
                stringOrNumber(obj, "name"),
                detail != null ? detail.name : null);
        if (activityName.isEmpty()) {
            return null;
        }

        String destination = firstNonBlank(
                stringOrNumber(obj, "destination"),
                detail != null ? detail.destination : null);
        String guideName = firstNonBlank(
                stringOrNumber(obj, "guide_name"),
                stringOrNumber(obj, "guideName"),
                detail != null ? detail.guideName : null);
        String category = firstNonBlank(
                stringOrNumber(obj, "category"),
                detail != null ? detail.category : null);
        long durationMinutes = firstLong(
                longValue(obj, "duration_minutes"),
                longValue(obj, "durationMinutes"),
                detail != null ? detail.durationMinutes : null,
                0L);
        int participants = (int) firstLong(
                longValue(obj, "participants"),
                longValue(obj, "people_count"),
                longValue(obj, "peopleCount"),
                1L);
        String status = firstNonBlank(
                stringOrNumber(obj, "status"),
                stringOrNumber(obj, "reservation_status"),
                stringOrNumber(obj, "reservationStatus"),
                ReservationStatus.CONFIRMED);
        String scheduledAt = firstNonBlank(
                stringOrNumber(obj, "scheduled_at"),
                stringOrNumber(obj, "scheduledAt"),
                stringOrNumber(obj, "activity_date"),
                stringOrNumber(obj, "activityDate"),
                stringOrNumber(obj, "date"),
                detail != null ? detail.dateIso : null);
        DateInfo dateInfo = resolveDateInfo(scheduledAt);

        String selectedDate = firstNonBlank(
                stringOrNumber(obj, "selected_date"),
                stringOrNumber(obj, "selectedDate"),
                dateInfo.localDate != null ? dateInfo.localDate.format(DATE_SHORT) : null);
        String selectedTime = firstNonBlank(
                stringOrNumber(obj, "selected_time"),
                stringOrNumber(obj, "selectedTime"),
                dateInfo.zonedDateTime != null ? dateInfo.zonedDateTime.format(TIME_ONLY) : null);

        String imageUrl = firstNonBlank(
                stringOrNumber(obj, "image_url"),
                stringOrNumber(obj, "imageUrl"),
                detail != null && !detail.imageUrls.isEmpty() ? detail.imageUrls.get(0) : null);
        String meetingPoint = firstNonBlank(
                stringOrNumber(obj, "meeting_point"),
                stringOrNumber(obj, "meetingPoint"),
                detail != null ? detail.meetingPoint : null);
        double pricePerPerson = firstDouble(
                doubleValue(obj, "price"),
                doubleValue(obj, "price_per_person"),
                detail != null ? detail.price : null,
                0d);
        double totalPrice = firstDouble(
                doubleValue(obj, "total_price"),
                doubleValue(obj, "totalPrice"),
                null,
                pricePerPerson * Math.max(1, participants));
        String currency = firstNonBlank(
                stringOrNumber(obj, "currency"),
                detail != null ? detail.currency : null);
        String description = firstNonBlank(
                stringOrNumber(obj, "description"),
                detail != null ? detail.description : null);

        ActivityDetail.CancellationPolicy policy = detail != null ? detail.cancellationPolicy : null;
        JsonObject policyObj = nestedObject(obj, "cancellation_policy", "cancellationPolicy");
        String cancellationType = firstNonBlank(
                policyObj != null ? stringOrNumber(policyObj, "type") : null,
                policy != null ? policy.type : null);
        String cancellationDescription = firstNonBlank(
                policyObj != null ? stringOrNumber(policyObj, "description") : null,
                policy != null ? policy.description : null);
        long cancellationFreeHours = firstLong(
                policyObj != null ? longValue(policyObj, "free_cancel_hours") : null,
                policyObj != null ? longValue(policyObj, "freeCancelHours") : null,
                policy != null ? policy.freeCancelHours : null,
                0L);

        return new ReservationItem(
                reservationId,
                activityId,
                activityName,
                destination,
                guideName,
                category,
                durationMinutes,
                Math.max(1, participants),
                status,
                scheduledAt,
                selectedDate,
                selectedTime,
                imageUrl,
                meetingPoint,
                pricePerPerson,
                totalPrice,
                currency,
                description,
                cancellationType,
                cancellationDescription,
                cancellationFreeHours,
                dateInfo.epochMillis,
                dateInfo.localDate
        );
    }

    @Nullable
    private static JsonObject nestedObject(@NonNull JsonObject obj, @NonNull String... keys) {
        for (String key : keys) {
            JsonElement value = obj.get(key);
            if (value != null && value.isJsonObject()) {
                return value.getAsJsonObject();
            }
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

    @NonNull
    private static String safe(@Nullable String value) {
        return value != null ? value.trim() : "";
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

    private static long firstLong(@Nullable Long a, @Nullable Long b, @Nullable Long c, long fallback) {
        if (a != null) return a;
        if (b != null) return b;
        if (c != null) return c;
        return fallback;
    }

    private static double firstDouble(@Nullable Double a, @Nullable Double b, @Nullable Double c, double fallback) {
        if (a != null) return a;
        if (b != null) return b;
        if (c != null) return c;
        return fallback;
    }

    @NonNull
    public String statusLabel() {
        return ReservationStatus.label(status);
    }

    public boolean canCancel() {
        return ReservationStatus.canCancel(status);
    }

    public boolean canFinish() {
        return ReservationStatus.canFinish(status);
    }

    public boolean matchesQuery(@Nullable String rawQuery) {
        String normalized = normalizeForSearch(rawQuery);
        return normalized.isEmpty() || normalizedSearchIndex.contains(normalized);
    }

    @Nullable
    public LocalDate localDate() {
        return localDate;
    }

    @NonNull
    public String formattedDateShort() {
        if (!selectedDateLabel.isEmpty()) return selectedDateLabel;
        if (scheduledAtValue.isEmpty()) return "Sin fecha";
        DateInfo info = resolveDateInfo(scheduledAtValue);
        if (info.localDate == null) return scheduledAtValue;
        return info.localDate.format(DATE_SHORT);
    }

    @NonNull
    public String formattedDateLong() {
        if (scheduledAtValue.isEmpty()) {
            return selectedDateLabel.isEmpty() ? "Sin fecha" : selectedDateLabel;
        }
        DateInfo info = resolveDateInfo(scheduledAtValue);
        if (info.zonedDateTime == null) {
            return selectedDateLabel.isEmpty() ? scheduledAtValue : selectedDateLabel;
        }
        return info.zonedDateTime.format(DATE_LONG);
    }

    @NonNull
    public String formattedScheduleCompact() {
        String date = formattedDateShort();
        if (selectedTimeLabel.isEmpty()) return date;
        return date + " · " + selectedTimeLabel;
    }

    @NonNull
    public String participantsLabel() {
        return participants == 1 ? "1 participante" : participants + " participantes";
    }

    @NonNull
    public String guideLabel() {
        return guideName.isEmpty() ? "Guia no informada" : "Guia: " + guideName;
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
    public String formattedTotalPrice() {
        double amount = totalPrice > 0 ? totalPrice : pricePerPerson * Math.max(1, participants);
        if (amount <= 0) return "Gratis";
        if ("ARS".equalsIgnoreCase(currency)) {
            return String.format(Locale.getDefault(), "$%.0f", amount);
        }
        String safeCurrency = currency.isEmpty() ? "USD" : currency;
        return String.format(Locale.getDefault(), "%s %.0f", safeCurrency, amount);
    }

    @NonNull
    public String cancellationSummary() {
        if (!cancellationDescription.isEmpty()) {
            return cancellationDescription;
        }
        if (cancellationFreeHours > 0) {
            return "Cancelacion sin cargo hasta " + cancellationFreeHours + " h antes.";
        }
        if (!cancellationType.isEmpty()) {
            return "Politica " + cancellationType + ".";
        }
        return "La politica de cancelacion se informara al momento de gestionar la reserva.";
    }

    @NonNull
    public String cancellationTypeLabel() {
        if (cancellationType.isEmpty()) return "";
        return ActivityDetail.cancellationTypeLabel(cancellationType);
    }

    @NonNull
    private static String normalizeForSearch(@Nullable String raw) {
        if (raw == null) return "";
        String normalized = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return normalized.toLowerCase(Locale.ROOT);
    }

    @NonNull
    private static DateInfo resolveDateInfo(@Nullable String raw) {
        String value = safe(raw);
        if (value.isEmpty()) {
            return new DateInfo(0L, null, null);
        }

        try {
            if (value.matches("^\\d{12,}$")) {
                long millis = Long.parseLong(value);
                ZonedDateTime zonedDateTime = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault());
                return new DateInfo(millis, zonedDateTime.toLocalDate(), zonedDateTime);
            }
        } catch (NumberFormatException ignored) {
        }

        try {
            ZonedDateTime zonedDateTime = Instant.parse(value).atZone(ZoneId.systemDefault());
            return new DateInfo(zonedDateTime.toInstant().toEpochMilli(), zonedDateTime.toLocalDate(), zonedDateTime);
        } catch (DateTimeParseException ignored) {
        }

        try {
            ZonedDateTime zonedDateTime = OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault());
            return new DateInfo(zonedDateTime.toInstant().toEpochMilli(), zonedDateTime.toLocalDate(), zonedDateTime);
        } catch (DateTimeParseException ignored) {
        }

        try {
            LocalDateTime localDateTime = LocalDateTime.parse(value);
            ZonedDateTime zonedDateTime = localDateTime.atZone(ZoneId.systemDefault());
            return new DateInfo(zonedDateTime.toInstant().toEpochMilli(), zonedDateTime.toLocalDate(), zonedDateTime);
        } catch (DateTimeParseException ignored) {
        }

        try {
            LocalDate localDate = LocalDate.parse(value);
            ZonedDateTime zonedDateTime = localDate.atStartOfDay(ZoneId.systemDefault());
            return new DateInfo(zonedDateTime.toInstant().toEpochMilli(), localDate, zonedDateTime);
        } catch (DateTimeParseException ignored) {
        }

        return new DateInfo(0L, null, null);
    }

    private static final class DateInfo {
        final long epochMillis;
        @Nullable
        final LocalDate localDate;
        @Nullable
        final ZonedDateTime zonedDateTime;

        DateInfo(long epochMillis, @Nullable LocalDate localDate, @Nullable ZonedDateTime zonedDateTime) {
            this.epochMillis = epochMillis;
            this.localDate = localDate;
            this.zonedDateTime = zonedDateTime;
        }
    }
}
