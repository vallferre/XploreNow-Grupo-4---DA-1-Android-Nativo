package ar.edu.uadexplorenow.data;

import androidx.annotation.NonNull;

import android.util.Log;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import ar.edu.uadexplorenow.data.local.db.CachedReservationDao;
import ar.edu.uadexplorenow.data.local.db.CachedReservationEntity;
import ar.edu.uadexplorenow.data.model.ActivityRtdbDto;
import ar.edu.uadexplorenow.data.network.RealtimeDatabaseApi;
import ar.edu.uadexplorenow.domain.ActivityDetail;
import ar.edu.uadexplorenow.domain.ReservationItem;
import ar.edu.uadexplorenow.domain.ReservationStatus;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class ReservationRepository {

    private static final String TAG = "ReservationRepository";

    public interface ActionCallback {
        void onSuccess();
        void onError(@NonNull String message);
    }

    private final RealtimeDatabaseApi realtimeDatabaseApi;
    private final CachedReservationDao cachedReservationDao;

    public ReservationRepository(
            @NonNull RealtimeDatabaseApi realtimeDatabaseApi,
            @NonNull CachedReservationDao cachedReservationDao
    ) {
        this.realtimeDatabaseApi = realtimeDatabaseApi;
        this.cachedReservationDao = cachedReservationDao;
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
                    callback.onError("No se pudo validar la actividad antes de reservar.");
                    return;
                }

                ActivityDetail.BookingSlot latestSlot = detail.findBookingSlot(selectedSlot.slotId, selectedSlot.startAtIso);
                long currentSpots = latestSlot != null && latestSlot.availableSpots > 0
                        ? latestSlot.availableSpots
                        : detail.availableSpots;
                if (participants <= 0 || currentSpots < participants) {
                    callback.onError("No hay cupos suficientes para esa reserva.");
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
                                if (!saveResponse.isSuccessful()) {
                                    Log.w(TAG, "No se pudo guardar la reserva. HTTP " + saveResponse.code());
                                    callback.onError("No se pudo guardar la reserva. HTTP " + saveResponse.code());
                                    return;
                                }
                                syncActivitySpotsBestEffort(activityId, Math.max(0, detail.availableSpots - participants));
                                cacheNewReservation(uid, reservationId, detail, latestSlot != null ? latestSlot : selectedSlot, participants);
                                callback.onSuccess();
                            }

                            @Override
                            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                                Log.e(TAG, "Error de red al guardar la reserva", t);
                                callback.onError("Error de red al guardar la reserva.");
                            }
                        });
            }

            @Override
            public void onFailure(@NonNull Call<ActivityRtdbDto> call, @NonNull Throwable t) {
                Log.e(TAG, "Error al consultar la actividad antes de reservar", t);
                callback.onError("No se pudo consultar la actividad antes de reservar.");
            }
        });
    }

    public void cancelReservation(
            @NonNull String uid,
            @NonNull ReservationItem reservation,
            @NonNull ActionCallback callback
    ) {
        if (!reservation.canCancel() || reservation.activityId.isEmpty()) {
            callback.onError("La reserva no se puede cancelar.");
            return;
        }

        realtimeDatabaseApi.getActivity(reservation.activityId).enqueue(new Callback<ActivityRtdbDto>() {
            @Override
            public void onResponse(@NonNull Call<ActivityRtdbDto> call, @NonNull Response<ActivityRtdbDto> response) {
                ActivityRtdbDto body = response.body();
                ActivityDetail detail = response.isSuccessful() && body != null
                        ? ActivityDetail.fromRtdbDto(body, reservation.activityId)
                        : null;

                String now = Instant.now().toString();
                Map<String, Object> reservationUpdates = new LinkedHashMap<>();
                reservationUpdates.put("status", ReservationStatus.CANCELLED);
                reservationUpdates.put("updated_at", now);
                reservationUpdates.put("cancelled_at", now);
                realtimeDatabaseApi.patchReservation(uid, reservation.reservationId, reservationUpdates)
                                        .enqueue(new Callback<Void>() {
                                            @Override
                                            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> saveResponse) {
                                                if (!saveResponse.isSuccessful()) {
                                                    Log.w(TAG, "No se pudo cancelar la reserva. HTTP " + saveResponse.code());
                                                    callback.onError("No se pudo cancelar la reserva. HTTP " + saveResponse.code());
                                                    return;
                                                }
                                                if (detail != null) {
                                                    long restoredSpots = detail.availableSpots + Math.max(1, reservation.participants);
                                                    syncActivitySpotsBestEffort(reservation.activityId, restoredSpots);
                                                }
                                                updateCachedStatus(reservation.reservationId, ReservationStatus.CANCELLED);
                                                callback.onSuccess();
                                            }

                                            @Override
                                            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                                                Log.e(TAG, "Error de red al cancelar la reserva", t);
                                                callback.onError("Error de red al cancelar la reserva.");
                                            }
                                        });
            }

            @Override
            public void onFailure(@NonNull Call<ActivityRtdbDto> call, @NonNull Throwable t) {
                Log.w(TAG, "No se pudo consultar la actividad al cancelar. Se intentara cancelar igual.", t);
                String now = Instant.now().toString();
                Map<String, Object> reservationUpdates = new LinkedHashMap<>();
                reservationUpdates.put("status", ReservationStatus.CANCELLED);
                reservationUpdates.put("updated_at", now);
                reservationUpdates.put("cancelled_at", now);
                realtimeDatabaseApi.patchReservation(uid, reservation.reservationId, reservationUpdates)
                        .enqueue(new Callback<Void>() {
                            @Override
                            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                                if (response.isSuccessful()) {
                                    updateCachedStatus(reservation.reservationId, ReservationStatus.CANCELLED);
                                    callback.onSuccess();
                                } else {
                                    callback.onError("No se pudo cancelar la reserva. HTTP " + response.code());
                                }
                            }

                            @Override
                            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable error) {
                                callback.onError("Error de red al cancelar la reserva.");
                            }
                        });
            }
        });
    }

    public void finishReservation(
            @NonNull String uid,
            @NonNull ReservationItem reservation,
            @NonNull ActionCallback callback
    ) {
        if (!reservation.canFinish()) {
            callback.onError("La reserva no se puede finalizar.");
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
                            updateCachedStatusAndFinishedAt(
                                    reservation.reservationId,
                                    ReservationStatus.FINISHED,
                                    now);
                            callback.onSuccess();
                        } else {
                            Log.w(TAG, "No se pudo marcar la reserva como finalizada. HTTP " + response.code());
                            callback.onError("No se pudo marcar la reserva como finalizada. HTTP " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                        Log.e(TAG, "Error de red al finalizar la reserva", t);
                        callback.onError("Error de red al finalizar la reserva.");
                    }
                });
    }

    public void submitReview(
            @NonNull String uid,
            @NonNull ReservationItem reservation,
            double activityRating,
            double guideRating,
            @NonNull String reviewComment,
            @NonNull ActionCallback callback
    ) {
        if (!reservation.canReviewNow()) {
            callback.onError("La reserva ya no esta disponible para calificar.");
            return;
        }

        String ratedAt = Instant.now().toString();
        double userRating = (activityRating + guideRating) / 2d;
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("activity_rating", activityRating);
        updates.put("activityRating", activityRating);
        updates.put("guide_rating", guideRating);
        updates.put("guideRating", guideRating);
        updates.put("user_rating", userRating);
        updates.put("userRating", userRating);
        updates.put("review_comment", reviewComment);
        updates.put("reviewComment", reviewComment);
        updates.put("rated_at", ratedAt);
        updates.put("ratedAt", ratedAt);
        updates.put("updated_at", ratedAt);

        realtimeDatabaseApi.patchReservation(uid, reservation.reservationId, updates)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                        if (!response.isSuccessful()) {
                            callback.onError("No se pudo guardar la calificacion. HTTP " + response.code());
                            return;
                        }
                        updateCachedReview(
                                reservation.reservationId,
                                userRating,
                                activityRating,
                                guideRating,
                                reviewComment,
                                ratedAt);
                        callback.onSuccess();
                    }

                    @Override
                    public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                        Log.e(TAG, "Error de red al guardar la calificacion", t);
                        callback.onError("Error de red al guardar la calificacion.");
                    }
                });
    }

    private void syncActivitySpotsBestEffort(@NonNull String activityId, long targetSpots) {
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("available_spots", targetSpots);
        realtimeDatabaseApi.patchActivity(activityId, updates).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (!response.isSuccessful()) {
                    Log.w(TAG, "No se pudieron sincronizar cupos para " + activityId + ". HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                Log.w(TAG, "No se pudieron sincronizar cupos para " + activityId, t);
            }
        });
    }

    private void cacheNewReservation(
            @NonNull String uid,
            @NonNull String reservationId,
            @NonNull ActivityDetail detail,
            @NonNull ActivityDetail.BookingSlot slot,
            int participants
    ) {
        ReservationItem item = ReservationItem.fromCache(
                reservationId,
                detail.id,
                detail.name,
                detail.destination,
                detail.guideName,
                detail.category,
                detail.durationMinutes,
                participants,
                ReservationStatus.CONFIRMED,
                slot.startAtIso,
                slot.formattedDate(),
                slot.formattedTime(),
                detail.imageUrls.isEmpty() ? "" : detail.imageUrls.get(0),
                detail.meetingPoint,
                null,
                null,
                null,
                "",
                "",
                "",
                detail.price,
                detail.price * participants,
                detail.currency,
                detail.description,
                detail.cancellationPolicy != null ? detail.cancellationPolicy.type : "",
                detail.cancellationPolicy != null ? detail.cancellationPolicy.description : "",
                detail.cancellationPolicy != null ? detail.cancellationPolicy.freeCancelHours : 0
        );
        CachedReservationEntity entity = CachedReservationEntity.fromDomain(uid, item);
        new Thread(() -> cachedReservationDao.insert(entity)).start();
    }

    private void updateCachedStatus(@NonNull String reservationId, @NonNull String status) {
        new Thread(() -> cachedReservationDao.updateStatus(reservationId, status)).start();
    }

    private void updateCachedStatusAndFinishedAt(
            @NonNull String reservationId,
            @NonNull String status,
            @NonNull String finishedAtValue
    ) {
        new Thread(() -> cachedReservationDao.updateStatusAndFinishedAt(
                reservationId,
                status,
                finishedAtValue)).start();
    }

    private void updateCachedReview(
            @NonNull String reservationId,
            double userRating,
            double activityRating,
            double guideRating,
            @NonNull String reviewComment,
            @NonNull String ratedAtValue
    ) {
        new Thread(() -> cachedReservationDao.updateReview(
                reservationId,
                userRating,
                activityRating,
                guideRating,
                reviewComment,
                ratedAtValue)).start();
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
        payload.put("activityId", detail.id);
        payload.put("activity_name", detail.name);
        payload.put("activityName", detail.name);
        payload.put("destination", detail.destination);
        payload.put("guide_name", detail.guideName);
        payload.put("guideName", detail.guideName);
        payload.put("category", detail.category);
        payload.put("duration_minutes", detail.durationMinutes);
        payload.put("durationMinutes", detail.durationMinutes);
        payload.put("participants", participants);
        payload.put("people", participants);
        payload.put("status", ReservationStatus.CONFIRMED);
        payload.put("scheduled_at", slot.startAtIso);
        payload.put("date", slot.startAtIso);
        payload.put("selected_date", slot.formattedDate());
        payload.put("selectedDate", slot.formattedDate());
        payload.put("selected_time", slot.formattedTime());
        payload.put("selectedTime", slot.formattedTime());
        payload.put("slot_id", slot.slotId);
        payload.put("meeting_point", detail.meetingPoint);
        payload.put("meetingPoint", detail.meetingPoint);
        payload.put("image_url", detail.imageUrls.isEmpty() ? "" : detail.imageUrls.get(0));
        payload.put("imageUrl", detail.imageUrls.isEmpty() ? "" : detail.imageUrls.get(0));
        payload.put("price", detail.price);
        payload.put("currency", detail.currency);
        payload.put("total_price", detail.price * participants);
        payload.put("totalPrice", detail.price * participants);
        payload.put("description", detail.description);
        String now = Instant.now().toString();
        payload.put("created_at", now);
        payload.put("createdAt", now);
        payload.put("updated_at", now);
        payload.put("updatedAt", now);

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
