package ar.edu.uadexplorenow.data.email;

/**
 * Credenciales de EmailJS para envío de OTP.
 *
 * ── CONFIGURACIÓN (una sola vez) ─────────────────────────────────────────────
 * 1. Creá una cuenta gratis en https://www.emailjs.com  (200 emails/mes gratis)
 * 2. En "Email Services" → conectá tu cuenta de Gmail/Outlook → copiá el Service ID
 * 3. En "Email Templates" → creá una plantilla con estas variables:
 *       Asunto : Tu código de acceso - XploreNow
 *       Cuerpo :
 *           Hola,
 *           Tu código de verificación es: {{otp_code}}
 *           Expira en 5 minutos.
 *    Guardá y copiá el Template ID.
 * 4. En "Account" → copiá la Public Key.
 * 5. Pegá los tres valores aquí abajo.
 *
 * Mientras SERVICE_ID empiece con "YOUR_", la app funciona en modo demo
 * (muestra el código en un Snackbar en lugar de enviarlo por email).
 * ─────────────────────────────────────────────────────────────────────────────
 */
public final class EmailConfig {

    /** Ej: "service_abc1234" */
    public static final String SERVICE_ID  = "service_4fmyx7s";

    /** Ej: "template_xyz5678" */
    public static final String TEMPLATE_ID = "template_v8l79qz";

    /** Clave pública de tu cuenta EmailJS. Ej: "aBcDeFgH1234567890" */
    public static final String PUBLIC_KEY  = "H5UMmxvesz23SafBH";

    /** Clave privada (solo necesaria si "strict mode" está activado en EmailJS). */
    public static final String PRIVATE_KEY = "YOUR_PRIVATE_KEY";

    private EmailConfig() {}

    /** {@code true} cuando las credenciales reales están configuradas. */
    public static boolean isConfigured() {
        return !SERVICE_ID.startsWith("YOUR_");
    }
}
