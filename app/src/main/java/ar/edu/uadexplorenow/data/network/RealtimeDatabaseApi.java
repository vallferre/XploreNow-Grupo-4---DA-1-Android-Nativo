package ar.edu.uadexplorenow.data.network;

import ar.edu.uadexplorenow.data.model.ActivityRtdbDto;
import ar.edu.uadexplorenow.data.model.OtpRecord;

import com.google.gson.JsonElement;

import ar.edu.uadexplorenow.data.model.UserRtdbDto;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.PUT;
import retrofit2.http.Path;

import java.util.Map;

/**
 * REST de Firebase Realtime Database (rutas terminadas en {@code .json}).
 */
public interface RealtimeDatabaseApi {

    @GET("activities.json")
    Call<JsonElement> getActivities();

    @GET("activities/{id}.json")
    Call<ActivityRtdbDto> getActivity(@Path("id") String id);

    @GET("users/{uid}.json")
    Call<UserRtdbDto> getUser(@Path("uid") String uid);

    @PUT("users/{uid}.json")
    Call<UserRtdbDto> putUser(@Path("uid") String uid, @Body UserRtdbDto user);

    @PATCH("users/{uid}.json")
    Call<Void> patchUser(@Path("uid") String uid, @Body Map<String, Object> updates);

    // ── OTP (registro) ──────────────────────────────────────────────────────
    // Ruta: otp_codes/{uid}.json  (el uid es el del usuario ya autenticado)

    @PUT("otp_codes/{uid}.json")
    Call<OtpRecord> putOtpCode(@Path("uid") String uid, @Body OtpRecord record);

    @GET("otp_codes/{uid}.json")
    Call<OtpRecord> getOtpCode(@Path("uid") String uid);

    @DELETE("otp_codes/{uid}.json")
    Call<Void> deleteOtpCode(@Path("uid") String uid);

    // ── Índice email → uid (para login OTP sin contraseña) ──────────────────
    // Ruta: user_emails/{emailKey}.json
    // Permite buscar el UID de un usuario por su email después de verificar OTP,
    // sin necesidad de escanear toda la colección "users".
    // La clave se sanitiza: reemplazar "@" → "_at_" y "." → "_dot_"

    @PUT("user_emails/{emailKey}.json")
    Call<String> putEmailIndex(@Path("emailKey") String emailKey, @Body String uid);

    @GET("user_emails/{emailKey}.json")
    Call<String> getUidByEmail(@Path("emailKey") String emailKey);
}
