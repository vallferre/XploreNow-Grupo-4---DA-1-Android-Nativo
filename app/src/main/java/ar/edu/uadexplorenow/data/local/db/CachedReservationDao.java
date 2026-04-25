package ar.edu.uadexplorenow.data.local.db;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface CachedReservationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<CachedReservationEntity> reservations);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(CachedReservationEntity reservation);

    @Query("SELECT * FROM cached_reservations WHERE uid = :uid ORDER BY scheduled_at_value DESC")
    List<CachedReservationEntity> getAllByUid(String uid);

    @Nullable
    @Query("SELECT * FROM cached_reservations WHERE reservation_id = :reservationId LIMIT 1")
    CachedReservationEntity getByReservationId(String reservationId);

    @Query("UPDATE cached_reservations SET status = :status WHERE reservation_id = :reservationId")
    void updateStatus(String reservationId, String status);

    @Query("DELETE FROM cached_reservations WHERE uid = :uid")
    void deleteByUid(String uid);
}
