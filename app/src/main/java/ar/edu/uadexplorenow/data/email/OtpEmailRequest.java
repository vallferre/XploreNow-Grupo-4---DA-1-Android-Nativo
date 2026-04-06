package ar.edu.uadexplorenow.data.email;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

/** Cuerpo de la llamada POST a la API de EmailJS. */
public class OtpEmailRequest {

    @SerializedName("service_id")
    public String serviceId;

    @SerializedName("template_id")
    public String templateId;

    /** Public Key de EmailJS. */
    @SerializedName("user_id")
    public String userId;

    /** Private Key — requerida solo cuando strict mode está habilitado. */
    @SerializedName("accessToken")
    public String accessToken;

    /** Variables que se inyectan en la plantilla: to_email, otp_code. */
    @SerializedName("template_params")
    public Map<String, String> templateParams;
}
