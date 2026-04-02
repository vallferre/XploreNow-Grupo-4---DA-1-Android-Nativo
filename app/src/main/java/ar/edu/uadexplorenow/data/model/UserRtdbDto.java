package ar.edu.uadexplorenow.data.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class UserRtdbDto {

    @SerializedName("id")
    public String id;

    @SerializedName("email")
    public String email;

    @SerializedName("name")
    public String name;

    @SerializedName("preferences")
    public List<String> preferences;

    @SerializedName("created_at")
    public String createdAt;
}