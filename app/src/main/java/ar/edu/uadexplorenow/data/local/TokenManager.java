package ar.edu.uadexplorenow.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * Encapsula el almacenamiento del token de autenticación en SharedPreferences.
 *
 * Como tiene {@code @Inject} en el constructor, Hilt la crea y la gestiona
 * automáticamente como Singleton — no necesita un {@code @Provides} en NetworkModule.
 *
 * Uso:
 *   tokenManager.saveToken("abc123");   // guardar
 *   tokenManager.getToken();            // leer (null si no hay)
 *   tokenManager.clearToken();          // borrar al hacer logout
 */
@Singleton
public class TokenManager {

    private static final String PREF_NAME = "auth_prefs";
    private static final String KEY_TOKEN = "token";

    private final SharedPreferences prefs;

    @Inject
    public TokenManager(@ApplicationContext Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveToken(String token) {
        prefs.edit().putString(KEY_TOKEN, token).apply();
    }

    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public void clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply();
    }
}
