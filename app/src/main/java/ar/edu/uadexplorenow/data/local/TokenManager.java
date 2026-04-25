package ar.edu.uadexplorenow.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * Encapsula el almacenamiento del token de autenticación.
 *
 * Usa {@link EncryptedSharedPreferences} para cifrar tanto la clave como el
 * valor en disco (AES-256-SIV para claves, AES-256-GCM para valores). La clave
 * maestra vive en el Android Keystore y nunca sale del hardware.
 *
 * El archivo cifrado queda en:
 *   /data/data/ar.edu.uadexplorenow/shared_prefs/auth_prefs_secure.xml
 *
 * Si el Keystore no está disponible (emulador sin hardware), se usa
 * SharedPreferences normal como fallback en:
 *   /data/data/ar.edu.uadexplorenow/shared_prefs/auth_prefs.xml
 */
@Singleton
public class TokenManager {

    private static final String ENCRYPTED_PREF_NAME = "auth_prefs_secure";
    private static final String FALLBACK_PREF_NAME  = "auth_prefs";
    private static final String KEY_TOKEN           = "token";

    private final SharedPreferences prefs;

    @Inject
    public TokenManager(@ApplicationContext Context context) {
        this.prefs = buildPrefs(context);
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

    private static SharedPreferences buildPrefs(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    context,
                    ENCRYPTED_PREF_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            return context.getSharedPreferences(FALLBACK_PREF_NAME, Context.MODE_PRIVATE);
        }
    }
}
