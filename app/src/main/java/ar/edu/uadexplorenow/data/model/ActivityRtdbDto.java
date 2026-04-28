package ar.edu.uadexplorenow.data.model;

import ar.edu.uadexplorenow.domain.ActivityItem;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * JSON de una actividad en Realtime Database (mismos nombres de campos que en Firestore).
 */
@SuppressWarnings("unused")
public class ActivityRtdbDto {

    @SerializedName("id")
    public String id;

    @SerializedName("name")
    public String name;

    @SerializedName("destination")
    public String destination;

    @SerializedName("category")
    public String category;

    @SerializedName("duration_minutes")
    public Long durationMinutes;

    @SerializedName("price")
    public Double price;

    @SerializedName("currency")
    public String currency;

    @SerializedName("available_spots")
    public Long availableSpots;

    @SerializedName("rating")
    public Double rating;

    @SerializedName("review_count")
    public Long reviewCount;

    @SerializedName("is_featured")
    public Boolean isFeatured;

    @SerializedName("cover_image_url")
    public String coverImageUrl;

    @SerializedName("day")
    public String day;

    @SerializedName("guide_name")
    public String guideName;

    @SerializedName("meeting_point")
    public String meetingPoint;

    @SerializedName(
            value = "meeting_point_coords",
            alternate = {"meetingPointCoords"}
    )
    public CoordinatesDto meetingPointCoords;

    @SerializedName(
            value = "itinerary_points",
            alternate = {"itineraryPoints"}
    )
    public JsonElement itineraryPoints;

    @SerializedName("description")
    public String description;

    @SerializedName("date")
    public String date;

    @SerializedName(
            value = "schedule",
            alternate = {
                    "slots",
                    "time_slots",
                    "timeSlots",
                    "available_dates",
                    "availableDates"
            }
    )
    public JsonElement schedule;

    @SerializedName("includes")
    public List<String> includes;

    @SerializedName("language")
    public List<String> language;

    @SerializedName("photos")
    public List<PhotoDto> photos;

    @SerializedName("cancellation_policy")
    public CancellationDto cancellationPolicy;

    public static class PhotoDto {
        @SerializedName("url")
        public String url;

        @SerializedName("order")
        public Long order;
    }

    public static class CancellationDto {
        @SerializedName("type")
        public String type;

        @SerializedName("description")
        public String description;

        @SerializedName("free_cancel_hours")
        public Long freeCancelHours;
    }

    public static class CoordinatesDto {
        @SerializedName("lat")
        public Double lat;

        @SerializedName("lng")
        public Double lng;
    }

    /** {@code rtdbPathKey} es la clave real en {@code activities/…} para REST; el campo JSON {@code id} puede diferir. */
    ActivityItem toActivityItem(String rtdbPathKey) {
        String rid = (rtdbPathKey != null && !rtdbPathKey.isEmpty())
                ? rtdbPathKey
                : (id != null ? id : "");
        long dur = durationMinutes != null ? durationMinutes : 0L;
        double pr = price != null ? price : 0;
        long spots = availableSpots != null ? availableSpots : 0L;
        double rat = rating != null ? rating : 0;
        boolean feat = Boolean.TRUE.equals(isFeatured);
        long revCount = reviewCount != null ? reviewCount : 0L;
        return new ActivityItem(
                rid,
                name != null ? name : "",
                destination != null ? destination : "",
                category != null ? category : "",
                dur,
                pr,
                currency != null ? currency : "",
                spots,
                rat,
                revCount,
                feat,
                coverImageUrl != null ? coverImageUrl : "",
                day != null ? day : ""
        );
    }
}
