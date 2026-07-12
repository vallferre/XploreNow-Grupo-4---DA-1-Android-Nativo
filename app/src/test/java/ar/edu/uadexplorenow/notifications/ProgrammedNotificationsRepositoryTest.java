package ar.edu.uadexplorenow.notifications;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import com.google.gson.JsonObject;
import java.time.Instant;
import ar.edu.uadexplorenow.notifications.ProgrammedNotificationsRepository.NotificationPayload;
import ar.edu.uadexplorenow.notifications.ProgrammedNotificationsRepository.ReservationSnapshot;

public class ProgrammedNotificationsRepositoryTest {
    private static ReservationSnapshot reservation(String scheduledAt, String status, String fingerprint) {
        return new ReservationSnapshot("res.1", "Kayak", status, scheduledAt,
                "13/07/2026", "10:00", "Ana", "Muelle 2", fingerprint);
    }

    @Test
    public void reminderIsCreatedInside24HourWindow() {
        long now = Instant.parse("2026-07-12T12:00:00Z").toEpochMilli();
        NotificationPayload payload = ProgrammedNotificationsRepository.reminderFor(
                reservation("2026-07-13T11:59:00Z", "confirmed", "abcdef0123456789"), now);
        assertNotNull(payload);
        assertEquals("reservation", payload.body.get("source_type"));
        assertEquals("res.1", payload.body.get("source_id"));
        assertTrue(payload.body.get("message").toString().contains("10:00"));
    }

    @Test
    public void demoReminderIsCreatedEvenBefore24HourWindow() {
        long now = Instant.parse("2026-07-12T12:00:00Z").toEpochMilli();
        assertNotNull(ProgrammedNotificationsRepository.reminderFor(
                reservation("2026-08-13T12:01:00Z", "confirmed", "abcdef0123456789"), now));
    }

    @Test
    public void reminderIsNotCreatedAfterStart() {
        long now = Instant.parse("2026-07-12T12:00:00Z").toEpochMilli();
        assertNull(ProgrammedNotificationsRepository.reminderFor(
                reservation("2026-07-12T11:59:00Z", "confirmed", "abcdef0123456789"), now));
    }

    @Test
    public void cancelledReservationDoesNotProduceReminder() {
        long now = Instant.parse("2026-07-12T12:00:00Z").toEpochMilli();
        assertNull(ProgrammedNotificationsRepository.reminderFor(
                reservation("2026-07-13T10:00:00Z", "cancelled", "abcdef0123456789"), now));
    }

    @Test
    public void deterministicIdsPointToReservation() {
        ReservationSnapshot item = reservation("2026-07-13T10:00:00Z", "confirmed", "abcdef0123456789");
        NotificationPayload first = ProgrammedNotificationsRepository.modificationFor(item);
        NotificationPayload second = ProgrammedNotificationsRepository.modificationFor(item);
        assertEquals(first.id, second.id);
        assertEquals("res.1", first.body.get("source_id"));
    }

    @Test
    public void ratingAndAvailabilityDoNotChangeFingerprint() {
        JsonObject reservation = reservationJson();
        JsonObject before = activityJson("Muelle 2");
        JsonObject after = activityJson("Muelle 2");
        after.addProperty("rating", 4.9);
        after.addProperty("available_spots", 1);
        assertEquals(
                ProgrammedNotificationsRepository.fingerprintFor(reservation, before),
                ProgrammedNotificationsRepository.fingerprintFor(reservation, after));
    }

    @Test
    public void meetingPointChangesFingerprint() {
        JsonObject reservation = reservationJson();
        String before = ProgrammedNotificationsRepository.fingerprintFor(reservation, activityJson("Muelle 2"));
        String after = ProgrammedNotificationsRepository.fingerprintFor(reservation, activityJson("Muelle 4"));
        org.junit.Assert.assertNotEquals(before, after);
    }

    @Test
    public void userStatusChangeDoesNotChangeFingerprint() {
        JsonObject activity = activityJson("Muelle 2");
        JsonObject before = reservationJson();
        JsonObject after = reservationJson();
        after.addProperty("status", "cancelled");
        assertEquals(
                ProgrammedNotificationsRepository.fingerprintFor(before, activity),
                ProgrammedNotificationsRepository.fingerprintFor(after, activity));
    }

    private static JsonObject reservationJson() {
        JsonObject value = new JsonObject();
        value.addProperty("activity_id", "activity-1");
        value.addProperty("scheduled_at", "2026-08-13T12:01:00Z");
        value.addProperty("status", "confirmed");
        return value;
    }

    private static JsonObject activityJson(String meetingPoint) {
        JsonObject value = new JsonObject();
        value.addProperty("name", "Kayak");
        value.addProperty("meeting_point", meetingPoint);
        return value;
    }
    @Test
    public void initialBaselineDoesNotCountAsChange() {
        assertTrue(!ProgrammedNotificationsRepository.hasRelevantChange(null, "first"));
    }

    @Test
    public void unchangedFingerprintDoesNotCountAsChange() {
        assertTrue(!ProgrammedNotificationsRepository.hasRelevantChange("same", "same"));
    }

    @Test
    public void laterFingerprintCountsAsChange() {
        assertTrue(ProgrammedNotificationsRepository.hasRelevantChange("before", "after"));
    }}