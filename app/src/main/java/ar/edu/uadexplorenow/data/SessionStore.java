package ar.edu.uadexplorenow.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.auth.FirebaseUser;

/**
 * Centraliza la sesión efectiva del usuario.
 *
 * Cuando el usuario inicia sesión con OTP, Firebase Auth crea una sesión
 * anónima (el único método passwordless disponible sin backend). En ese caso
 * el UID anónimo no coincide con el UID registrado en la RTDB. SessionStore
 * guarda el UID y email reales para que ProfileFragment (y cualquier otro
 * componente) pueda usar las credenciales correctas independientemente del
 * método de login.
 *
 * Para login clásico (email + contraseña), {@link #getEffectiveUid} y
 * {@link #getEffectiveEmail} devuelven directamente los valores de Firebase Auth.
 */
public final class SessionStore {

    private static final String PREFS     = "xplorenow_session";
    private static final String KEY_UID   = "otp_uid";
    private static final String KEY_EMAIL = "otp_email";

    private SessionStore() {}

    // ── Escritura ─────────────────────────────────────────────────────────────

    /** Llama después de verificar el OTP para guardar el UID y email reales. */
    public static void saveOtpSession(Context ctx, String realUid, String email) {
        prefs(ctx).edit()
                .putString(KEY_UID, realUid)
                .putString(KEY_EMAIL, email)
                .apply();
    }

    /** Limpia la sesión OTP. Llamar al hacer logout. */
    public static void clear(Context ctx) {
        prefs(ctx).edit().clear().apply();
    }

    // ── Lectura ───────────────────────────────────────────────────────────────

    /**
     * Devuelve el UID que debe usarse para operaciones en la RTDB.
     * - Login OTP:     UID real del usuario (guardado en SharedPreferences).
     * - Login clásico: UID de Firebase Auth ({@code currentUser.getUid()}).
     */
    public static String getEffectiveUid(Context ctx, FirebaseUser currentUser) {
        String stored = prefs(ctx).getString(KEY_UID, null);
        return (stored != null && !stored.isEmpty()) ? stored : currentUser.getUid();
    }

    /**
     * Devuelve el email que debe usarse para mostrar/guardar el perfil.
     * - Login OTP:     email guardado en SharedPreferences (el usuario anónimo no tiene email).
     * - Login clásico: email de Firebase Auth.
     */
    public static String getEffectiveEmail(Context ctx, FirebaseUser currentUser) {
        String stored = prefs(ctx).getString(KEY_EMAIL, null);
        if (stored != null && !stored.isEmpty()) return stored;
        String authEmail = currentUser.getEmail();
        return authEmail != null ? authEmail : "";
    }

    /** {@code true} si la sesión actual fue creada vía OTP (anónima). */
    public static boolean isOtpSession(Context ctx) {
        return prefs(ctx).getString(KEY_UID, null) != null;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
