package ar.edu.uadexplorenow.data.local.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * DAO para {@link CachedActivityEntity}.
 *
 * Room genera la implementación en tiempo de compilación y valida
 * las queries SQL contra el esquema de la base de datos.
 */
@Dao
public interface CachedActivityDao {

    /**
     * Reemplaza las actividades existentes si el id ya está en la tabla
     * (para manejar recargas parciales sin duplicados).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<CachedActivityEntity> activities);

    @Query("SELECT * FROM cached_activities ORDER BY name ASC")
    List<CachedActivityEntity> getAll();

    @Query("DELETE FROM cached_activities")
    void deleteAll();
}
