package ar.edu.uadexplorenow.notifications;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import ar.edu.uadexplorenow.data.model.UserRtdbDto;
import ar.edu.uadexplorenow.data.network.RealtimeDatabaseApi;
import ar.edu.uadexplorenow.domain.NotificationItem;
import ar.edu.uadexplorenow.domain.ActivityDetail;
import ar.edu.uadexplorenow.domain.ReservationStatus;
import retrofit2.Response;
import retrofit2.Call;
import retrofit2.Callback;

/** Generates reminder/update notifications on-device for this backend-less MVP. */
@Singleton
public final class ProgrammedNotificationsRepository {
    private static final List<String> RESERVATION_FIELDS = Arrays.asList(
            "activity_id", "activityId", "activity_name", "activityName", "scheduled_at", "scheduledAt",
            "date", "selected_date", "selectedDate", "selected_time", "selectedTime", "slot_id", "slotId",
            "guide_name", "guideName", "meeting_point", "meetingPoint");
    private static final List<String> ACTIVITY_FIELDS = Arrays.asList(
            "name", "destination", "guide_name", "guideName", "meeting_point", "meetingPoint", "date",
            "schedule", "slots", "time_slots", "timeSlots", "description", "duration_minutes");
    private final ProgrammedNotificationStateStore stateStore;
    private final RealtimeDatabaseApi api;

    @Inject
    public ProgrammedNotificationsRepository(@NonNull RealtimeDatabaseApi api,
                                              @NonNull ProgrammedNotificationStateStore stateStore) {
        this.api = api;
        this.stateStore = stateStore;
    }

    public void createImmediateReminder(@NonNull String uid, @NonNull String reservationId,
                                        @NonNull ActivityDetail detail,
                                        @NonNull ActivityDetail.BookingSlot slot,
                                        @NonNull Runnable onCreated) {
        ReservationSnapshot reservation = new ReservationSnapshot(
                reservationId, detail.name, ReservationStatus.CONFIRMED, slot.startAtIso,
                slot.formattedDate(), slot.formattedTime(), detail.guideName, detail.meetingPoint,
                sha256(reservationId + "|" + slot.startAtIso));
        NotificationPayload payload = reminderFor(reservation, System.currentTimeMillis());
        if (payload == null) return;
        api.putNotification(uid, payload.id, payload.body).enqueue(new Callback<Void>() {
            @Override public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (response.isSuccessful()) {
                    onCreated.run();
                } else {
                    Log.w("ProgrammedNotifications", "No se pudo crear recordatorio. HTTP " + response.code());
                }
            }
            @Override public void onFailure(@NonNull Call<Void> call, @NonNull Throwable error) {
                Log.w("ProgrammedNotifications", "No se pudo crear recordatorio inmediato", error);
            }
        });
    }
    public synchronized int syncBlocking(@NonNull String uid) throws IOException {
        Response<UserRtdbDto> userResponse = api.getUser(uid).execute();
        if (!userResponse.isSuccessful()) throw new IOException("User HTTP " + userResponse.code());
        UserRtdbDto user = userResponse.body();
        if (user == null) return 0;
        JsonObject activities = loadReservedActivities(user.reservations);
        int created = 0;
        for (ReservationSnapshot reservation : parseReservations(user.reservations, activities)) {
            String previous = stateStore.getFingerprint(uid, reservation.id);
            if (hasRelevantChange(previous, reservation.fingerprint)) {
                put(uid, modificationFor(reservation));
                created++;
            }
            stateStore.putFingerprint(uid, reservation.id, reservation.fingerprint);
        }
        return created;
    }

    static boolean hasRelevantChange(@Nullable String previous, @NonNull String current) {
        return previous != null && !previous.equals(current);
    }

    private boolean put(String uid, NotificationPayload payload) throws IOException {
        Response<Void> response = api.putNotification(uid, payload.id, payload.body).execute();
        if (!response.isSuccessful()) throw new IOException("Notification HTTP " + response.code());
        return true;
    }

    @Nullable
    static NotificationPayload reminderFor(ReservationSnapshot reservation, long nowMillis) {
        if (!ReservationStatus.CONFIRMED.equals(ReservationStatus.normalize(reservation.status))) return null;
        long scheduledMillis = parseEpochMillis(reservation.scheduledAt);
        long remaining = scheduledMillis - nowMillis;
        if (scheduledMillis <= 0L || remaining <= 0L) return null;
        String id = "reservation_reminder_" + safeKey(reservation.id) + "_" + scheduledMillis;
        StringBuilder message = new StringBuilder("Te esperamos el ").append(reservation.scheduleLabel());
        if (!reservation.meetingPoint.isEmpty()) message.append(" en ").append(reservation.meetingPoint);
        if (!reservation.guideName.isEmpty()) message.append(". Guia: ").append(reservation.guideName);
        message.append('.');
        return payload(id, "Hola, Te esperamos en " + fallback(reservation.name, "tu reserva"),
                message.toString(), "reservation_reminder", reservation.id);
    }

    static NotificationPayload modificationFor(ReservationSnapshot reservation) {
        String id = "reservation_changed_" + safeKey(reservation.id) + "_"
                + reservation.fingerprint.substring(0, Math.min(16, reservation.fingerprint.length()));
        String message = "Hubo cambios en " + fallback(reservation.name, "tu reserva")
                + ". Revisa la fecha, horario y punto de encuentro en el detalle.";
        return payload(id, "Tu reserva fue actualizada", message, "reservation_updated", reservation.id);
    }

    private static NotificationPayload payload(String id, String title, String message, String type, String sourceId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id); body.put("title", title); body.put("message", message); body.put("type", type);
        body.put("source_type", NotificationItem.SOURCE_RESERVATION); body.put("source_id", sourceId);
        body.put("created_at", Instant.now().toString()); body.put("read_at", ""); body.put("opened_at", "");
        return new NotificationPayload(id, body);
    }

    private static List<ReservationSnapshot> parseReservations(@Nullable JsonElement root, JsonObject activities) {
        if (root == null || root.isJsonNull()) return Collections.emptyList();
        List<ReservationSnapshot> result = new ArrayList<>();
        if (root.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject().entrySet())
                if (entry.getValue().isJsonObject()) result.add(snapshot(entry.getKey(), entry.getValue().getAsJsonObject(), activities));
        } else if (root.isJsonArray()) {
            JsonArray array = root.getAsJsonArray();
            for (int i = 0; i < array.size(); i++)
                if (array.get(i).isJsonObject()) result.add(snapshot("reservation_" + i, array.get(i).getAsJsonObject(), activities));
        }
        return result;
    }

    private static ReservationSnapshot snapshot(String key, JsonObject reservation, JsonObject activities) {
        String id = first(reservation, "id", "reservation_id", "reservationId");
        if (id.isEmpty()) id = key;
        String activityId = first(reservation, "activity_id", "activityId");
        JsonObject activity = activities.has(activityId) && activities.get(activityId).isJsonObject()
                ? activities.getAsJsonObject(activityId) : new JsonObject();
        String fingerprint = fingerprintFor(reservation, activity);
        return new ReservationSnapshot(id,
                fallback(first(reservation, "activity_name", "activityName", "name"), first(activity, "name")),
                first(reservation, "status"), first(reservation, "scheduled_at", "scheduledAt", "date"),
                first(reservation, "selected_date", "selectedDate"), first(reservation, "selected_time", "selectedTime"),
                fallback(first(reservation, "guide_name", "guideName"), first(activity, "guide_name", "guideName")),
                fallback(first(reservation, "meeting_point", "meetingPoint"), first(activity, "meeting_point", "meetingPoint")),
                fingerprint);
    }

    private JsonObject loadReservedActivities(@Nullable JsonElement reservations) throws IOException {
        JsonObject activities = new JsonObject();
        if (reservations == null || reservations.isJsonNull()) return activities;
        List<JsonObject> rows = new ArrayList<>();
        if (reservations.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : reservations.getAsJsonObject().entrySet()) {
                if (entry.getValue().isJsonObject()) rows.add(entry.getValue().getAsJsonObject());
            }
        } else if (reservations.isJsonArray()) {
            for (JsonElement value : reservations.getAsJsonArray()) {
                if (value.isJsonObject()) rows.add(value.getAsJsonObject());
            }
        }
        for (JsonObject row : rows) {
            String activityId = first(row, "activity_id", "activityId");
            if (!activityId.isEmpty() && !activities.has(activityId)) {
                Response<JsonElement> response = api.getActivityJson(activityId).execute();
                if (!response.isSuccessful()) throw new IOException("Activity HTTP " + response.code());
                JsonElement body = response.body();
                activities.add(activityId, body != null && body.isJsonObject() ? body : new JsonObject());
            }
        }
        return activities;
    }

    static String fingerprintFor(JsonObject reservation, JsonObject activity) {
        return sha256(project(reservation, RESERVATION_FIELDS) + "|" + project(activity, ACTIVITY_FIELDS));
    }
    private static String project(JsonObject object, List<String> keys) {
        JsonObject projected = new JsonObject();
        for (String key : keys) if (object.has(key)) projected.add(key, object.get(key));
        return projected.toString();
    }
    private static String first(JsonObject object, String... keys) {
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value != null && !value.isJsonNull() && value.isJsonPrimitive()) {
                String text = value.getAsString().trim();
                if (!text.isEmpty()) return text;
            }
        }
        return "";
    }
    static long parseEpochMillis(@Nullable String value) {
        if (value == null || value.trim().isEmpty()) return 0L;
        String text = value.trim();
        try { return Instant.parse(text).toEpochMilli(); } catch (DateTimeParseException ignored) {}
        try { return OffsetDateTime.parse(text).toInstant().toEpochMilli(); } catch (DateTimeParseException ignored) {}
        try { return LocalDateTime.parse(text).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); }
        catch (DateTimeParseException ignored) { return 0L; }
    }
    private static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b));
            return out.toString();
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static String safeKey(String value) { return value.replaceAll("[^A-Za-z0-9_-]", "_"); }
    private static String fallback(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    static final class ReservationSnapshot {
        final String id, name, status, scheduledAt, selectedDate, selectedTime, guideName, meetingPoint, fingerprint;
        ReservationSnapshot(String id, String name, String status, String scheduledAt, String selectedDate,
                            String selectedTime, String guideName, String meetingPoint, String fingerprint) {
            this.id = id; this.name = name; this.status = status; this.scheduledAt = scheduledAt;
            this.selectedDate = selectedDate; this.selectedTime = selectedTime; this.guideName = guideName;
            this.meetingPoint = meetingPoint; this.fingerprint = fingerprint;
        }
        String scheduleLabel() {
            if (!selectedDate.isEmpty()) return selectedTime.isEmpty() ? selectedDate : selectedDate + " a las " + selectedTime;
            return scheduledAt.isEmpty() ? "horario indicado" : scheduledAt;
        }
    }
    static final class NotificationPayload {
        final String id;
        final Map<String, Object> body;
        NotificationPayload(String id, Map<String, Object> body) { this.id = id; this.body = body; }
    }
}