package ar.edu.uadexplorenow.data;

import androidx.annotation.NonNull;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import ar.edu.uadexplorenow.data.model.ActivityRtdbDto;
import ar.edu.uadexplorenow.data.network.RealtimeDatabaseApi;
import ar.edu.uadexplorenow.domain.ActivityDetail;
import ar.edu.uadexplorenow.domain.ReservationItem;
import ar.edu.uadexplorenow.domain.ReservationStatus;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class ReservationRepository {

    public interface ActionCallback {
        void onSuccess();
        void onError();
    }

    private final RealtimeDatabaseApi realtimeDatabaseApi;

    public ReservationRepository(@NonNull RealtimeDatabaseApi realtimeDatabaseApi) {
        this.realtimeDatabaseApi = realtimeDatabaseApi;
    }

    public void createReservation(
            @NonNull String uid,
            @NonNull String activityId,
            @NonNull ActivityDetail.BookingSlot selectedSlot,
            int participants,
            @NonNull ActionCallback callback
    ) {
        realtimeDatabaseApi.getActivity(activityId).enqueue(new Callback<ActivityRtdbDto>() {
            @Override
            public void onResponse(@NonNull Call<ActivityRtdbDto> call, @NonNull Response<ActivityRtdbDto> response) {
                ActivityRtdbDto body = response.body();
                ActivityDetail detail = response.isSuccessful() && body != null
                        ? ActivityDetail.fromRtdbDto(body, activityId)
                        : null;
                if (detail == null) {
                    callback.onError();
                    return;
                }

                ActivityDetail.BookingSlot latestSlot = detail.findBookingSlot(selectedSlot.slotId, selectedSlot.startAtIso);
                long currentSpots = latestSlot != null && latestSlot.availableSpots > 0
                        ? latestSlot.availableSpots
                        : detail.availableSpots;
                if (participants <= 0 || currentSpots < participants) {
                    callback.onError();
                    return;
                }

                Map<String, Object> activityUpdates = new LinkedHashMap<>();
                activityUpdates.put("available_spots", Math.max(0, detail.availableSpots - participants));
                realtimeDatabaseApi.patchActivity(activityId, activityUpdates)
                        .enqueue(new Callback<Void>() {
                            @Override
                            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> patchResponse) {
                                if (!patchResponse.isSuccessful()) {
                                    callback.onError();
                                    return;
                                }
                                String reservationId = "res_" + UUID.randomUUID().toString().replace("-", "");
                                Map<String, Object> reservation = buildReservationPayload(
                                        reservationId,
                                        detail,
                                        latestSlot != null ? latestSlot : selectedSlot,
                                        participants);
                                realtimeDatabaseApi.putReservation(uid, reservationId, reservation)
                                        .enqueue(new Callback<Void>() {
                                            @Override
                                            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> saveResponse) {
                                                if (saveResponse.isSuccessful()) {
                                                    callback.onSuccess();
                                                } else {
                                                    rollbackActivitySpots(activityId, detail.availableSpots);
                                                    callback.onError();
                                                }
                                            }

                                            @Override
                                            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                                                rollbackActivitySpots(activityId, detail.availableSpots);
                                                callback.onError();
                                            }
                                        });
                            }

                            @Override
                            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                                callback.onError();
                            }
                        });
            }

            @Override
            public void onFailure(@NonNull Call<ActivityRtdbDto> call, @NonNull Throwable t) {
                callback.onError();
            }
        });
    }

    public void cancelReservation(
            @NonNull String uid,
            @NonNull ReservationItem reservation,
            @NonNull ActionCallback callback
    ) {
        if (!reservation.canCancel() || reservation.activityId.isEmpty()) {
            callback.onError();
            return;
        }

        realtimeDatabaseApi.getActivity(reservation.activityId).enqueue(new Callback<ActivityRtdbDto>() {
            @Override
            public void onResponse(@NonNull Call<ActivityRtdbDto> call, @NonNull Response<ActivityRtdbDto> response) {
                ActivityRtdbDto body = response.body();
                ActivityDetail detail = response.isSuccessful() && body != null
                        ? ActivityDetail.fromRtdbDto(body, reservation.activityId)
                        : null;
                if (detail == null) {
                    callback.onError();
                    return;
                }

                long restoredSpots = detail.availableSpots + Math.max(1, reservation.participants);
                Map<String, Object> activityUpdates = new LinkedHashMap<>();
                activityUpdates.put("available_spots", restoredSpots);
                realtimeDatabaseApi.patchActivity(reservation.activityId, activityUpdates)
                        .enqueue(new Callback<Void>() {
                            @Override
                            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> patchResponse) {
                                if (!patchResponse.isSuccessful()) {
                                    callback.onError();
                                    return;
                                }
                                String now = Instant.now().toString();
                                Map<String, Object> reservationUpdates = new LinkedHashMap<>();
                                reservationUpdates.put("status", ReservationStatus.CANCELLED);
                                reservationUpdates.put("updated_at", now);
                                reservationUpdates.put("cancelled_at", now);
                                realtimeDatabaseApi.patchReservation(uid, reservation.reservationId, reservationUpdates)
                                        .enqueue(new Callback<Void>() {
                                            @Override
                                            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> saveResponse) {
                                                if (saveResponse.isSuccessful()) {
                                                    callback.onSuccess();
                                                } else {
                                                    rollbackActivitySpots(reservation.activityId, detail.availableSpots);
                                                    callback.onError();
                                                }
                                            }

                                            @Override
                                            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                                                rollbackActivitySpots(reservation.activityId, detail.availableSpots);
                                                callback.onError();
                                            }
                                        });
                            }

                            @Override
                            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                                callback.onError();
                            }
                        });
            }

            @Override
            public void onFailure(@NonNull Call<ActivityRtdbDto> call, @NonNull Throwable t) {
                callback.onError();
            }
        });
    }

    public void finishReservation(
            @NonNull String uid,
            @NonNull ReservationItem reservation,
            @NonNull ActionCallback callback
    ) {
        if (!reservation.canFinish()) {
            callback.onError();
            return;
        }
        String now = Instant.now().toString();
        Map<String, Object> reservationUpdates = new LinkedHashMap<>();
        reservationUpdates.put("status", ReservationStatus.FINISHED);
        reservationUpdates.put("updated_at", now);
        reservationUpdates.put("finished_at", now);
        realtimeDatabaseApi.patchReservation(uid, reservation.reservationId, reservationUpdates)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                        if (response.isSuccessful()) {
                            callback.onSuccess();
                        } else {
                            callback.onError();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                        callback.onError();
                    }
                });
    }

    private void rollbackActivitySpots(@NonNull String activityId, long previousSpots) {
        Map<String, Object> rollback = new LinkedHashMap<>();
        rollback.put("available_spots", previousSpots);
        realtimeDatabaseApi.patchActivity(activityId, rollback).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {}

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {}
        });
    }

    @NonNull
    private Map<String, Object> buildReservationPayload(
            @NonNull String reservationId,
            @NonNull ActivityDetail detail,
            @NonNull ActivityDetail.BookingSlot slot,
            int participants
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", reservationId);
        payload.put("activity_id", detail.id);
        payload.put("activity_name", detail.name);
        payload.put("destination", detail.destination);
        payload.put("guide_name", detail.guideName);
        payload.put("category", detail.category);
        payload.put("duration_minutes", detail.durationMinutes);
        payload.put("participants", participants);
        payload.put("status", ReservationStatus.CONFIRMED);
        payload.put("scheduled_at", slot.startAtIso);
        payload.put("selected_date", slot.formattedDate());
        payload.put("selected_time", slot.formattedTime());
        payload.put("slot_id", slot.slotId);
        payload.put("meeting_point", detail.meetingPoint);
        payload.put("image_url", detail.imageUrls.isEmpty() ? "" : detail.imageUrls.get(0));
        payload.put("price", detail.price);
        payload.put("currency", detail.currency);
        payload.put("total_price", detail.price * participants);
        payload.put("description", detail.description);
        String now = Instant.now().toString();
        payload.put("created_at", now);
        payload.put("updated_at", now);

        if (detail.cancellationPolicy != null && detail.cancellationPolicy.hasContent()) {
            Map<String, Object> policy = new LinkedHashMap<>();
            policy.put("type", detail.cancellationPolicy.type);
            policy.put("description", detail.cancellationPolicy.description);
            policy.put("free_cancel_hours", detail.cancellationPolicy.freeCancelHours);
            payload.put("cancellation_policy", policy);
        }
        return payload;
    }
}
