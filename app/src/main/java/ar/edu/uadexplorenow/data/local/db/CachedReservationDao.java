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

    @Query("UPDATE cached_reservations SET status = :status, finished_at_value = :finishedAtValue WHERE reservation_id = :reservationId")
    void updateStatusAndFinishedAt(String reservationId, String status, String finishedAtValue);

    @Query("UPDATE cached_reservations SET user_rating = :userRating, activity_rating = :activityRating, guide_rating = :guideRating, review_comment = :reviewComment, rated_at_value = :ratedAtValue WHERE reservation_id = :reservationId")
    void updateReview(String reservationId, @Nullable Double userRating, @Nullable Double activityRating, @Nullable Double guideRating, String reviewComment, String ratedAtValue);

    @Query("DELETE FROM cached_reservations WHERE uid = :uid")
    void deleteByUid(String uid);
}
