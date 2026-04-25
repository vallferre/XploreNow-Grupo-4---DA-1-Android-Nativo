package ar.edu.uadexplorenow.data.local.cache;

import android.content.Context;

import androidx.annotation.Nullable;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import ar.edu.uadexplorenow.data.model.UserRtdbDto;
import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * Caché de perfil de usuario en el sistema de archivos privado de la app.
 *
 * Persiste en: /data/data/ar.edu.uadexplorenow/files/user_profile_cache.json
 *
 * Guarda únicamente los campos necesarios para el modo offline:
 * nombre, foto de perfil y preferencias de viaje (usadas para mostrar
 * actividades recomendadas cuando no hay conexión).
 *
 * Usa java.io directamente — sin dependencias adicionales.
 */
@Singleton
public class UserProfileFileCache {

    private static final String FILE_NAME = "user_profile_cache.json";

    private final Context context;
    private final Gson gson;

    @Inject
    public UserProfileFileCache(@ApplicationContext Context context, Gson gson) {
        this.context = context;
        this.gson    = gson;
    }

    /**
     * Guarda los datos relevantes del perfil en disco.
     * Se llama después de cada carga exitosa desde la RTDB.
     */
    public void save(UserRtdbDto user) {
        Snapshot snapshot = new Snapshot();
        snapshot.name             = user.name;
        snapshot.photoUrl         = user.photoUrl;
        snapshot.preferences      = user.preferences;
        snapshot.legacyPreferences = user.legacyPreferences;

        File file = new File(context.getFilesDir(), FILE_NAME);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(gson.toJson(snapshot).getBytes("UTF-8"));
        } catch (Exception ignored) {}
    }

    /**
     * Devuelve los datos del perfil guardados en disco, o {@code null} si no existen.
     * Se llama cuando la carga desde la RTDB falla (sin conexión).
     */
    @Nullable
    public UserRtdbDto load() {
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (!file.exists()) return null;

        try (FileInputStream fis = new FileInputStream(file);
             BufferedReader reader = new BufferedReader(new InputStreamReader(fis, "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);

            Snapshot snapshot = gson.fromJson(sb.toString(), Snapshot.class);
            if (snapshot == null) return null;

            UserRtdbDto dto = new UserRtdbDto();
            dto.name              = snapshot.name;
            dto.photoUrl          = snapshot.photoUrl;
            dto.preferences       = snapshot.preferences;
            dto.legacyPreferences = snapshot.legacyPreferences;
            return dto;
        } catch (Exception e) {
            return null;
        }
    }

    /** POJO interno para serialización — solo los campos necesarios para el modo offline. */
    private static class Snapshot {
        String name;
        String photoUrl;
        List<String> preferences;
        List<String> legacyPreferences;
    }
}
