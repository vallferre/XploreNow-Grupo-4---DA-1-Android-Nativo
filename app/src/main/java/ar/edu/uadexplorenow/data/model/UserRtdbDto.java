package ar.edu.uadexplorenow.data.model;

import com.google.gson.JsonElement;
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

    @SerializedName("legacy_preferences")
    public List<String> legacyPreferences;

    @SerializedName("phone")
    public String phone;

    @SerializedName("photoUrl")
    public String photoUrl;

    @SerializedName("reserved_activity_ids")
    public List<String> reservedActivityIds;

    @SerializedName("completed_activity_ids")
    public List<String> completedActivityIds;

    @SerializedName("reserved_activities_count")
    public Long reservedActivitiesCount;

    @SerializedName("completed_activities_count")
    public Long completedActivitiesCount;

    @SerializedName(
            value = "activity_history",
            alternate = {
                    "activityHistory",
                    "history",
                    "completed_activities",
                    "completedActivities"
            }
    )
    public JsonElement activityHistory;

    @SerializedName("created_at")
    public String createdAt;
}
