package ar.edu.uadexplorenow.data.model;

/**
 * Registro OTP almacenado en Firebase Realtime Database.
 * Ruta: otp_codes/{uid}.json
 *
 * Reglas RTDB recomendadas:
 * "otp_codes": {
 *   "$uid": {
 *     ".read":  "auth != null && auth.uid == $uid",
 *     ".write": "auth != null && auth.uid == $uid"
 *   }
 * }
 */
public class OtpRecord {
    public String code;       // código de 6 dígitos
    public long   expiresAt;  // epoch millis
    public boolean used;
}
