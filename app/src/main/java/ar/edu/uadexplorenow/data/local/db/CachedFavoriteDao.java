package ar.edu.uadexplorenow.data.local.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface CachedFavoriteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<CachedFavoriteEntity> favorites);

    @Query("SELECT * FROM cached_favorites WHERE uid = :uid")
    List<CachedFavoriteEntity> getAllByUid(String uid);

    @Query("DELETE FROM cached_favorites WHERE uid = :uid")
    void deleteByUid(String uid);
}
