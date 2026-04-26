package ar.edu.uadexplorenow.data.local;

import android.content.Context;

/**
 * Persistencia de la preferencia de autenticación biométrica.
 *
 * La biometría es un "desbloqueo rápido" para sesiones Firebase activas.
 * Si la sesión vence, el usuario debe autenticarse con email y contraseña.
 */
public final class BiometricPrefs {

    private static final String PREFS           = "biometric_prefs";
    private static final String KEY_ENABLED     = "biometric_enabled";
    private static final String KEY_DECLINED    = "biometric_offer_declined";

    private BiometricPrefs() {}

    public static boolean isEnabled(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                  .getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(Context ctx, boolean enabled) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
           .edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    /** true si el usuario eligió "Ahora no" cuando se le ofreció activar la biometría. */
    public static boolean wasDeclined(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                  .getBoolean(KEY_DECLINED, false);
    }

    public static void setDeclined(Context ctx, boolean declined) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
           .edit().putBoolean(KEY_DECLINED, declined).apply();
    }
}
