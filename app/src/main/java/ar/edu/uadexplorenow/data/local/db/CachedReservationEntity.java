package ar.edu.uadexplorenow.data.local.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import ar.edu.uadexplorenow.domain.ReservationItem;

import java.util.ArrayList;
import java.util.List;

@Entity(
        tableName = "cached_reservations",
        indices = {@Index(value = "uid")}
)
public class CachedReservationEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "reservation_id")
    public String reservationId = "";

    @ColumnInfo(name = "uid")
    public String uid = "";

    @ColumnInfo(name = "activity_id")
    public String activityId = "";

    @ColumnInfo(name = "activity_name")
    public String activityName = "";

    @ColumnInfo(name = "destination")
    public String destination = "";

    @ColumnInfo(name = "guide_name")
    public String guideName = "";

    @ColumnInfo(name = "category")
    public String category = "";

    @ColumnInfo(name = "duration_minutes")
    public long durationMinutes = 0;

    @ColumnInfo(name = "participants")
    public int participants = 1;

    @ColumnInfo(name = "status")
    public String status = "";

    @ColumnInfo(name = "scheduled_at_value")
    public String scheduledAtValue = "";

    @ColumnInfo(name = "selected_date_label")
    public String selectedDateLabel = "";

    @ColumnInfo(name = "selected_time_label")
    public String selectedTimeLabel = "";

    @ColumnInfo(name = "image_url")
    public String imageUrl = "";

    @ColumnInfo(name = "meeting_point")
    public String meetingPoint = "";

    @ColumnInfo(name = "user_rating")
    @Nullable
    public Double userRating;

    @ColumnInfo(name = "activity_rating")
    @Nullable
    public Double activityRating;

    @ColumnInfo(name = "guide_rating")
    @Nullable
    public Double guideRating;

    @ColumnInfo(name = "review_comment")
    public String reviewComment = "";

    @ColumnInfo(name = "finished_at_value")
    public String finishedAtValue = "";

    @ColumnInfo(name = "rated_at_value")
    public String ratedAtValue = "";

    @ColumnInfo(name = "price_per_person")
    public double pricePerPerson = 0;

    @ColumnInfo(name = "total_price")
    public double totalPrice = 0;

    @ColumnInfo(name = "currency")
    public String currency = "";

    @ColumnInfo(name = "description")
    public String description = "";

    @ColumnInfo(name = "cancellation_type")
    public String cancellationType = "";

    @ColumnInfo(name = "cancellation_description")
    public String cancellationDescription = "";

    @ColumnInfo(name = "cancellation_free_hours")
    public long cancellationFreeHours = 0;

    @ColumnInfo(name = "slot_id")
    public String slotId = "";

    @ColumnInfo(name = "check_in_status")
    public String checkInStatus = ReservationItem.CHECK_IN_PENDING;

    @ColumnInfo(name = "checked_in_at_value")
    public String checkedInAtValue = "";

    @ColumnInfo(name = "check_in_message")
    public String checkInMessage = "";

    public ReservationItem toDomain() {
        return ReservationItem.fromCache(
                reservationId, activityId, activityName, destination, guideName, category,
                durationMinutes, participants, status, scheduledAtValue, selectedDateLabel,
                selectedTimeLabel, imageUrl, meetingPoint, userRating, activityRating,
                guideRating, reviewComment, finishedAtValue, ratedAtValue, pricePerPerson,
                totalPrice, currency, description, cancellationType, cancellationDescription,
                cancellationFreeHours, slotId, checkInStatus, checkedInAtValue, checkInMessage
        );
    }

    public static CachedReservationEntity fromDomain(@NonNull String uid, @NonNull ReservationItem item) {
        CachedReservationEntity e = new CachedReservationEntity();
        e.reservationId = item.reservationId;
        e.uid = uid;
        e.activityId = item.activityId;
        e.activityName = item.activityName;
        e.destination = item.destination;
        e.guideName = item.guideName;
        e.category = item.category;
        e.durationMinutes = item.durationMinutes;
        e.participants = item.participants;
        e.status = item.status;
        e.scheduledAtValue = item.scheduledAtValue;
        e.selectedDateLabel = item.selectedDateLabel;
        e.selectedTimeLabel = item.selectedTimeLabel;
        e.imageUrl = item.imageUrl;
        e.meetingPoint = item.meetingPoint;
        e.userRating = item.userRating;
        e.activityRating = item.activityRating;
        e.guideRating = item.guideRating;
        e.reviewComment = item.reviewComment;
        e.finishedAtValue = item.finishedAtValue;
        e.ratedAtValue = item.ratedAtValue;
        e.pricePerPerson = item.pricePerPerson;
        e.totalPrice = item.totalPrice;
        e.currency = item.currency;
        e.description = item.description;
        e.cancellationType = item.cancellationType;
        e.cancellationDescription = item.cancellationDescription;
        e.cancellationFreeHours = item.cancellationFreeHours;
        e.slotId = item.slotId;
        e.checkInStatus = item.checkInStatus;
        e.checkedInAtValue = item.checkedInAtValue;
        e.checkInMessage = item.checkInMessage;
        return e;
    }

    public static List<CachedReservationEntity> fromList(@NonNull String uid, @NonNull List<ReservationItem> items) {
        List<CachedReservationEntity> out = new ArrayList<>(items.size());
        for (ReservationItem item : items) {
            out.add(fromDomain(uid, item));
        }
        return out;
    }

    public static List<ReservationItem> toList(@NonNull List<CachedReservationEntity> entities) {
        List<ReservationItem> out = new ArrayList<>(entities.size());
        for (CachedReservationEntity e : entities) {
            out.add(e.toDomain());
        }
        return out;
    }
}
