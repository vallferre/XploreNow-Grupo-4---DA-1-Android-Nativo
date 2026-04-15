package ar.edu.uadexplorenow.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ar.edu.uadexplorenow.data.model.FavoriteRtdbDto;
import ar.edu.uadexplorenow.data.network.RealtimeDatabaseApi;
import ar.edu.uadexplorenow.domain.ActivityDetail;
import ar.edu.uadexplorenow.domain.ActivityItem;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@Singleton
public final class FavoritesRepository {

    private final RealtimeDatabaseApi api;
    private final Gson gson;

    public interface VoidCallback {
        void onDone(@Nullable String errorMessage);
    }

    public interface FavoritesMapCallback {
        void onResult(@NonNull Map<String, FavoriteRtdbDto> favorites);
    }

    public interface OneFavoriteCallback {
        void onResult(@Nullable FavoriteRtdbDto favorite);
    }

    @Inject
    public FavoritesRepository(@NonNull RealtimeDatabaseApi api, @NonNull Gson gson) {
        this.api = api;
        this.gson = gson;
    }

    public void loadAll(@NonNull String uid, @NonNull FavoritesMapCallback callback) {
        api.getFavorites(uid).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(@NonNull Call<JsonElement> call, @NonNull Response<JsonElement> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    callback.onResult(Collections.emptyMap());
                    return;
                }
                callback.onResult(parseFavoritesMap(response.body()));
            }

            @Override
            public void onFailure(@NonNull Call<JsonElement> call, @NonNull Throwable t) {
                callback.onResult(Collections.emptyMap());
            }
        });
    }

    public void loadOne(@NonNull String uid, @NonNull String activityId, @NonNull OneFavoriteCallback callback) {
        api.getFavorite(uid, activityId).enqueue(new Callback<FavoriteRtdbDto>() {
            @Override
            public void onResponse(
                    @NonNull Call<FavoriteRtdbDto> call,
                    @NonNull Response<FavoriteRtdbDto> response
            ) {
                if (!response.isSuccessful()) {
                    callback.onResult(null);
                    return;
                }
                callback.onResult(response.body());
            }

            @Override
            public void onFailure(@NonNull Call<FavoriteRtdbDto> call, @NonNull Throwable t) {
                callback.onResult(null);
            }
        });
    }

    public void addFavorite(
            @NonNull String uid,
            @NonNull ActivityDetail detail,
            @NonNull VoidCallback callback
    ) {
        FavoriteRtdbDto body = new FavoriteRtdbDto(detail.price, detail.availableSpots);
        api.putFavorite(uid, detail.id, body).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (!response.isSuccessful()) {
                    callback.onDone(parseErrorMessage(response));
                    return;
                }
                callback.onDone(null);
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                callback.onDone(t.getMessage() != null ? t.getMessage() : "network");
            }
        });
    }

    public void addFavorite(
            @NonNull String uid,
            @NonNull ActivityItem activity,
            @NonNull VoidCallback callback
    ) {
        FavoriteRtdbDto body = new FavoriteRtdbDto(activity.price, activity.availableSpots);
        api.putFavorite(uid, activity.id, body).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (!response.isSuccessful()) {
                    callback.onDone(parseErrorMessage(response));
                    return;
                }
                callback.onDone(null);
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                callback.onDone(t.getMessage() != null ? t.getMessage() : "network");
            }
        });
    }

    public void removeFavorite(@NonNull String uid, @NonNull String activityId, @NonNull VoidCallback callback) {
        api.deleteFavorite(uid, activityId).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (!response.isSuccessful()) {
                    callback.onDone(parseErrorMessage(response));
                    return;
                }
                callback.onDone(null);
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                callback.onDone(t.getMessage() != null ? t.getMessage() : "network");
            }
        });
    }

    /**
     * Actualiza la línea base guardada (p. ej. al abrir el detalle) para limpiar el indicador de novedades.
     */
    public void syncBaseline(
            @NonNull String uid,
            @NonNull String activityId,
            double price,
            long spots,
            @NonNull VoidCallback callback
    ) {
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("last_seen_price", price);
        patch.put("last_seen_spots", spots);
        api.patchFavorite(uid, activityId, patch).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (!response.isSuccessful()) {
                    callback.onDone(parseErrorMessage(response));
                    return;
                }
                callback.onDone(null);
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                callback.onDone(t.getMessage() != null ? t.getMessage() : "network");
            }
        });
    }

    @NonNull
    private Map<String, FavoriteRtdbDto> parseFavoritesMap(@NonNull JsonElement root) {
        if (!root.isJsonObject()) {
            return Collections.emptyMap();
        }
        JsonObject obj = root.getAsJsonObject();
        Map<String, FavoriteRtdbDto> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            if (!e.getValue().isJsonObject()) {
                continue;
            }
            FavoriteRtdbDto dto = gson.fromJson(e.getValue(), FavoriteRtdbDto.class);
            if (dto != null) {
                out.put(e.getKey(), dto);
            }
        }
        return out;
    }

    @Nullable
    private static String parseErrorMessage(@NonNull Response<?> response) {
        return "HTTP " + response.code();
    }

    /** Hay cambio de precio respecto a lo guardado al marcar favorito / última visita al detalle. */
    public static boolean hasPriceNovelty(@Nullable FavoriteRtdbDto dto, double currentPrice) {
        if (dto == null || dto.lastSeenPrice == null) {
            return false;
        }
        return Math.abs(dto.lastSeenPrice - currentPrice) > 0.009;
    }

    /**
     * Antes había cupos según la línea base y ahora el catálogo indica cero (o menos).
     */
    public static boolean spotsBecameUnavailable(@Nullable FavoriteRtdbDto dto, long currentSpots) {
        if (dto == null || dto.lastSeenSpots == null) {
            return false;
        }
        return dto.lastSeenSpots > 0 && currentSpots <= 0;
    }

    /**
     * Cuántos cupos se agregaron respecto a la línea base; {@code 0} si no hubo aumento.
     */
    public static long spotsIncreaseDelta(@Nullable FavoriteRtdbDto dto, long currentSpots) {
        if (dto == null || dto.lastSeenSpots == null) {
            return 0L;
        }
        long last = dto.lastSeenSpots;
        if (currentSpots > last) {
            return currentSpots - last;
        }
        return 0L;
    }
}
