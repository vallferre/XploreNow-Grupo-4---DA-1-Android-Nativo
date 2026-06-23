package ar.edu.uadexplorenow.data.auth;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.util.Locale;
import java.util.Random;

import ar.edu.uadexplorenow.data.email.OtpEmailSender;

/**
 * Generación, envío y verificación de códigos OTP reutilizable en login y registro.
 */
public final class OtpVerificationHelper {

    public static final long OTP_EXPIRY_MS = 5 * 60 * 1000L;

    public static final String PREFS_LOGIN    = "otp_login_prefs";
    public static final String PREFS_REGISTER = "otp_register_prefs";

    private static final String KEY_CODE    = "code";
    private static final String KEY_EXPIRES = "expires_at";

    public enum VerifyResult {
        OK,
        NOT_FOUND,
        EXPIRED,
        INVALID
    }

    private OtpVerificationHelper() {}

    @NonNull
    public static String generateCode() {
        return String.format(Locale.getDefault(), "%06d", new Random().nextInt(1_000_000));
    }

    public static void storeCode(@NonNull Context context, @NonNull String prefsName, @NonNull String code) {
        long expiresAt = System.currentTimeMillis() + OTP_EXPIRY_MS;
        prefs(context, prefsName)
                .edit()
                .putString(KEY_CODE, code)
                .putLong(KEY_EXPIRES, expiresAt)
                .apply();
    }

    @NonNull
    public static VerifyResult verify(
            @NonNull Context context,
            @NonNull String prefsName,
            @NonNull String enteredCode
    ) {
        SharedPreferences prefs = prefs(context, prefsName);
        String storedCode = prefs.getString(KEY_CODE, null);
        long expiresAt = prefs.getLong(KEY_EXPIRES, 0L);

        if (storedCode == null) {
            return VerifyResult.NOT_FOUND;
        }
        if (System.currentTimeMillis() > expiresAt) {
            return VerifyResult.EXPIRED;
        }
        if (!enteredCode.equals(storedCode)) {
            return VerifyResult.INVALID;
        }
        return VerifyResult.OK;
    }

    public static void clear(@NonNull Context context, @NonNull String prefsName) {
        prefs(context, prefsName).edit().clear().apply();
    }

    public static void sendByEmail(
            @NonNull String email,
            @NonNull String code,
            @NonNull OtpEmailSender.SendCallback callback
    ) {
        OtpEmailSender.send(email, code, callback);
    }

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context, @NonNull String prefsName) {
        return context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
    }
}
