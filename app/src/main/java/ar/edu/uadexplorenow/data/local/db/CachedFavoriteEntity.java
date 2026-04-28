package ar.edu.uadexplorenow.data.local.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import ar.edu.uadexplorenow.data.model.FavoriteRtdbDto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Entity(
        tableName = "cached_favorites",
        indices = {@Index(value = "uid")}
)
public class CachedFavoriteEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "activity_id")
    public String activityId = "";

    @ColumnInfo(name = "uid")
    public String uid = "";

    @ColumnInfo(name = "last_seen_price")
    @Nullable
    public Double lastSeenPrice;

    @ColumnInfo(name = "last_seen_spots")
    @Nullable
    public Long lastSeenSpots;

    public static CachedFavoriteEntity from(
            @NonNull String uid,
            @NonNull String activityId,
            @NonNull FavoriteRtdbDto dto
    ) {
        CachedFavoriteEntity e = new CachedFavoriteEntity();
        e.activityId = activityId;
        e.uid = uid;
        e.lastSeenPrice = dto.lastSeenPrice;
        e.lastSeenSpots = dto.lastSeenSpots;
        return e;
    }

    public FavoriteRtdbDto toDto() {
        FavoriteRtdbDto dto = new FavoriteRtdbDto();
        dto.lastSeenPrice = lastSeenPrice;
        dto.lastSeenSpots = lastSeenSpots;
        return dto;
    }

    public static List<CachedFavoriteEntity> fromMap(
            @NonNull String uid,
            @NonNull Map<String, FavoriteRtdbDto> favMap
    ) {
        List<CachedFavoriteEntity> out = new ArrayList<>(favMap.size());
        for (Map.Entry<String, FavoriteRtdbDto> e : favMap.entrySet()) {
            out.add(from(uid, e.getKey(), e.getValue()));
        }
        return out;
    }

    public static Map<String, FavoriteRtdbDto> toMap(@NonNull List<CachedFavoriteEntity> entities) {
        Map<String, FavoriteRtdbDto> out = new LinkedHashMap<>();
        for (CachedFavoriteEntity e : entities) {
            out.put(e.activityId, e.toDto());
        }
        return out;
    }
}
