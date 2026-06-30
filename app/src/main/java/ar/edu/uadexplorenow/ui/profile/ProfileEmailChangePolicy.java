package ar.edu.uadexplorenow.ui.profile;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reglas puras del cambio de email del perfil.
 *
 * Mantenerlas fuera del Fragment permite probar que un cambio pendiente no
 * reemplaza el email confirmado ni pierde los demás datos del usuario.
 */
final class ProfileEmailChangePolicy {

    private ProfileEmailChangePolicy() {}

    @NonNull
    static String emailToKey(@Nullable String email) {
        return safe(email).trim().toLowerCase(Locale.ROOT)
                .replace("@", "_at_")
                .replace(".", "_dot_");
    }

    static boolean canUseEmailIndex(@Nullable String indexedUid, @NonNull String effectiveUid) {
        String owner = safe(indexedUid).trim();
        return owner.isEmpty() || owner.equals(effectiveUid);
    }

    static boolean canRequestFirebaseEmailChange(boolean otpSession) {
        return !otpSession;
    }

    @NonNull
    static String resolveDisplayedEmail(
            @Nullable String storedEmail,
            @Nullable String effectiveEmail,
            boolean otpSession
    ) {
        String sessionEmail = safe(effectiveEmail).trim();
        if (otpSession && !sessionEmail.isEmpty()) {
            return sessionEmail;
        }
        return safe(storedEmail);
    }

    @NonNull
    static Map<String, Object> buildProfileUpdates(
            @NonNull String uid,
            @NonNull String confirmedEmail,
            @NonNull String name,
            @NonNull String phone,
            @NonNull String photoUrl,
            @NonNull List<String> preferences,
            @NonNull List<String> legacyPreferences
    ) {
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("id", uid);
        updates.put("email", confirmedEmail);
        updates.put("name", name);
        updates.put("phone", phone);
        updates.put("photoUrl", photoUrl);
        updates.put("preferences", preferences);
        if (!legacyPreferences.isEmpty()) {
            updates.put("legacy_preferences", legacyPreferences);
        }
        return updates;
    }

    @NonNull
    private static String safe(@Nullable String value) {
        return value != null ? value : "";
    }
}
