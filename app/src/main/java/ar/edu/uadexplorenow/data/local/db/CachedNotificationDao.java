package ar.edu.uadexplorenow.data.local.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface CachedNotificationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<CachedNotificationEntity> notifications);

    @Query("SELECT * FROM cached_notifications WHERE uid = :uid ORDER BY created_at DESC")
    List<CachedNotificationEntity> getAllByUid(String uid);

    @Query("SELECT * FROM cached_notifications WHERE uid = :uid AND (read_at = '' OR read_at IS NULL) AND (opened_at = '' OR opened_at IS NULL)")
    List<CachedNotificationEntity> getUnreadByUid(String uid);

    @Query("DELETE FROM cached_notifications WHERE uid = :uid")
    void deleteByUid(String uid);

    @Query("UPDATE cached_notifications SET read_at = :readAt WHERE uid = :uid AND notification_id = :notificationId")
    void markRead(String uid, String notificationId, String readAt);

    @Query("UPDATE cached_notifications SET read_at = :openedAt, opened_at = :openedAt WHERE uid = :uid AND notification_id = :notificationId")
    void markOpened(String uid, String notificationId, String openedAt);
}