package ar.edu.uadexplorenow.data.email;

import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.POST;

/**
 * Envía el código OTP al email del usuario a través de la API de EmailJS.
 *
 * Si {@link EmailConfig#isConfigured()} es {@code false} (las credenciales
 * todavía tienen los valores "YOUR_*"), el envío se omite y se llama a
 * {@link SendCallback#onNotConfigured()} para que el fragment muestre el
 * código en pantalla (modo demo).
 */
public final class OtpEmailSender {

    private static final String BASE_URL = "https://api.emailjs.com/api/";
    private static final String TAG      = "OtpEmailSender";

    // ── Interfaz Retrofit interna ─────────────────────────────────────────────

    private interface EmailJsApi {
        @POST("v1.0/email/send")
        Call<ResponseBody> send(@Body OtpEmailRequest request);
    }

    // ── Singleton del cliente ─────────────────────────────────────────────────

    private static EmailJsApi api;

    private static EmailJsApi api() {
        if (api == null) {
            api = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(EmailJsApi.class);
        }
        return api;
    }

    private OtpEmailSender() {}

    // ── Callback ──────────────────────────────────────────────────────────────

    public interface SendCallback {
        /** El email fue enviado con éxito. */
        void onSuccess();
        /** EmailJS no está configurado (credenciales con "YOUR_*"). */
        void onNotConfigured();
        /** El envío falló por error de red o respuesta no 2xx. */
        void onFailure();
    }

    // ── Método principal ──────────────────────────────────────────────────────

    /**
     * Envía el OTP al email indicado.
     *
     * @param toEmail  Destinatario (variable {{to_email}} en la plantilla).
     * @param otpCode  Código de 6 dígitos (variable {{otp_code}} en la plantilla).
     * @param callback Resultado en el hilo principal (Retrofit lo garantiza).
     */
    public static void send(String toEmail, String otpCode, SendCallback callback) {
        if (!EmailConfig.isConfigured()) {
            callback.onNotConfigured();
            return;
        }

        Map<String, String> params = new HashMap<>();
        params.put("to_email", toEmail);
        params.put("otp_code", otpCode);

        OtpEmailRequest request = new OtpEmailRequest();
        request.serviceId      = EmailConfig.SERVICE_ID;
        request.templateId     = EmailConfig.TEMPLATE_ID;
        request.userId         = EmailConfig.PUBLIC_KEY;
        request.accessToken    = EmailConfig.PRIVATE_KEY;
        request.templateParams = params;

        api().send(request).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call,
                                   @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Log.d(TAG, "Email enviado OK (" + response.code() + ")");
                    callback.onSuccess();
                } else {
                    String errorBody = "";
                    try {
                        if (response.errorBody() != null)
                            errorBody = response.errorBody().string();
                    } catch (IOException ignored) {}
                    Log.e(TAG, "Error EmailJS " + response.code() + ": " + errorBody);
                    callback.onFailure();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call,
                                  @NonNull Throwable t) {
                Log.e(TAG, "Fallo de red: " + t.getMessage(), t);
                callback.onFailure();
            }
        });
    }
}
