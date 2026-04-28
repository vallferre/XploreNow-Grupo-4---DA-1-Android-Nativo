package ar.edu.uadexplorenow.data.local.db;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import ar.edu.uadexplorenow.domain.ActivityItem;

/**
 * Fila en la tabla "cached_activities" (Room / SQLite).
 *
 * Persiste en: /data/data/ar.edu.uadexplorenow/databases/xplorenow_cache
 *
 * Se usa para mostrar el catálogo cuando no hay conexión a internet.
 * La tabla se reemplaza completamente después de cada carga exitosa
 * desde la API (OnConflictStrategy.REPLACE + deleteAll antes de insertar).
 */
@Entity(tableName = "cached_activities")
public class CachedActivityEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id = "";

    @ColumnInfo(name = "name")          public String name;
    @ColumnInfo(name = "destination")   public String destination;
    @ColumnInfo(name = "category")      public String category;
    @ColumnInfo(name = "duration_min")  public long durationMinutes;
    @ColumnInfo(name = "price")         public double price;
    @ColumnInfo(name = "currency")      public String currency;
    @ColumnInfo(name = "spots")         public long availableSpots;
    @ColumnInfo(name = "rating")        public double rating;
    @ColumnInfo(name = "review_count")  public long reviewCount;
    @ColumnInfo(name = "featured")      public boolean featured;
    @ColumnInfo(name = "cover_url")     public String coverImageUrl;
    @ColumnInfo(name = "day")           public String day;

    public static CachedActivityEntity fromDomain(ActivityItem item) {
        CachedActivityEntity e = new CachedActivityEntity();
        e.id             = item.id;
        e.name           = item.name;
        e.destination    = item.destination;
        e.category       = item.category;
        e.durationMinutes = item.durationMinutes;
        e.price          = item.price;
        e.currency       = item.currency;
        e.availableSpots = item.availableSpots;
        e.rating         = item.rating;
        e.reviewCount    = item.reviewCount;
        e.featured       = item.featured;
        e.coverImageUrl  = item.coverImageUrl;
        e.day            = item.day;
        return e;
    }

    public ActivityItem toDomain() {
        return new ActivityItem(
                id, name, destination, category,
                durationMinutes, price, currency,
                availableSpots, rating, reviewCount, featured, coverImageUrl, day
        );
    }

    public static List<CachedActivityEntity> fromList(List<ActivityItem> items) {
        List<CachedActivityEntity> out = new ArrayList<>(items.size());
        for (ActivityItem item : items) out.add(fromDomain(item));
        return out;
    }

    public static List<ActivityItem> toList(List<CachedActivityEntity> entities) {
        List<ActivityItem> out = new ArrayList<>(entities.size());
        for (CachedActivityEntity e : entities) out.add(e.toDomain());
        return out;
    }
}
