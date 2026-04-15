package ar.edu.uadexplorenow.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * Entrada en {@code users/{uid}/favorites/{activityId}} (Realtime Database).
 * Los valores "vistos" sirven para detectar cambios de precio o nuevos cupos.
 */
@SuppressWarnings("unused")
public final class FavoriteRtdbDto {

    @SerializedName("last_seen_price")
    public Double lastSeenPrice;

    @SerializedName("last_seen_spots")
    public Long lastSeenSpots;

    public FavoriteRtdbDto() {}

    public FavoriteRtdbDto(double lastSeenPrice, long lastSeenSpots) {
        this.lastSeenPrice = lastSeenPrice;
        this.lastSeenSpots = lastSeenSpots;
    }
}
