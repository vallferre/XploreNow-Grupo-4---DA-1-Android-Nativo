package ar.edu.uadexplorenow.data.local.db;

import androidx.room.Database;
import androidx.room.RoomDatabase;

/**
 * Base de datos local Room.
 *
 * Persiste en: /data/data/ar.edu.uadexplorenow/databases/xplorenow_cache
 *   xplorenow_cache       → archivo SQLite principal
 *   xplorenow_cache-shm   → shared memory (WAL mode)
 *   xplorenow_cache-wal   → write-ahead log
 *
 * Incrementar version al agregar entidades o modificar columnas.
 */
@Database(entities = {CachedActivityEntity.class, CachedReservationEntity.class, CachedFavoriteEntity.class, CachedNotificationEntity.class}, version = 6, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract CachedActivityDao cachedActivityDao();
    public abstract CachedReservationDao cachedReservationDao();
    public abstract CachedFavoriteDao cachedFavoriteDao();
    public abstract CachedNotificationDao cachedNotificationDao();
}
