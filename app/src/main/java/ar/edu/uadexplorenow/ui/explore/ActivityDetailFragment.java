package ar.edu.uadexplorenow.ui.explore;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.viewpager2.widget.ViewPager2;

import ar.edu.uadexplorenow.R;
import ar.edu.uadexplorenow.data.FavoritesRepository;
import ar.edu.uadexplorenow.data.ReservationRepository;
import ar.edu.uadexplorenow.data.SessionStore;
import ar.edu.uadexplorenow.data.local.db.CachedReservationDao;
import ar.edu.uadexplorenow.data.model.ActivityRtdbDto;
import ar.edu.uadexplorenow.data.network.RealtimeDatabaseApi;
import ar.edu.uadexplorenow.domain.ActivityDetail;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class ActivityDetailFragment extends Fragment {

    private static final String TAG = "ActivityDetailFragment";

    @Inject
    RealtimeDatabaseApi realtimeDatabaseApi;
    @Inject
    FavoritesRepository favoritesRepository;
    @Inject
    CachedReservationDao cachedReservationDao;

    public static final String ARG_ACTIVITY_ID = "activity_id";

    private static final int CUPOS_LOW = 5;
    private static final DateTimeFormatter BOOKING_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy", new Locale("es", "AR"));
    private static final DateTimeFormatter BOOKING_TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault());

    private String effectiveUid = "";
    private boolean isFavorite;
    @Nullable
    private ImageButton btnFavorite;
    @Nullable
    private ActivityDetail loadedDetail;
    @Nullable
    private MaterialButton btnReserve;
    @Nullable
    private ReservationRepository reservationRepository;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.activity_activity_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Navigation.findNavController(view).popBackStack();
            return;
        }
        effectiveUid = SessionStore.getEffectiveUid(requireContext(), currentUser);
        reservationRepository = new ReservationRepository(realtimeDatabaseApi, cachedReservationDao);

        String id = null;
        if (getArguments() != null) {
            id = getArguments().getString(ARG_ACTIVITY_ID);
        }
        if (id == null || id.isEmpty()) {
            Navigation.findNavController(view).popBackStack();
            return;
        }

        View contentRoot = view.findViewById(R.id.contentRoot);
        ProgressBar progress = view.findViewById(R.id.progress);
        ViewPager2 photoPager = view.findViewById(R.id.photoPager);
        LinearLayout dotsContainer = view.findViewById(R.id.dotsContainer);
        ImageButton btnBack = view.findViewById(R.id.btnBack);
        btnFavorite = view.findViewById(R.id.btnFavorite);
        TextView tvHeroCategory = view.findViewById(R.id.tvHeroCategory);
        TextView tvTitle = view.findViewById(R.id.tvTitle);
        TextView tvSubtitle = view.findViewById(R.id.tvSubtitle);
        TextView tvDuration = view.findViewById(R.id.tvDuration);
        TextView tvLanguage = view.findViewById(R.id.tvLanguage);
        TextView tvCupos = view.findViewById(R.id.tvCupos);
        TextView tvGuide = view.findViewById(R.id.tvGuide);
        TextView tvDescription = view.findViewById(R.id.tvDescription);
        TextView tvIncludesTitle = view.findViewById(R.id.tvIncludesTitle);
        LinearLayout includesContainer = view.findViewById(R.id.includesContainer);
        TextView tvMeetingTitle = view.findViewById(R.id.tvMeetingTitle);
        View cardMeeting = view.findViewById(R.id.cardMeetingPoint);
        TextView tvMeetingPoint = view.findViewById(R.id.tvMeetingPoint);
        MaterialButton btnViewLocation = view.findViewById(R.id.btnViewLocation);
        TextView tvWhenLabel = view.findViewById(R.id.tvWhenLabel);
        TextView tvActivityWhen = view.findViewById(R.id.tvActivityWhen);
        TextView tvCancellationTitle = view.findViewById(R.id.tvCancellationTitle);
        View cardCancellation = view.findViewById(R.id.cardCancellation);
        TextView tvCancellationType = view.findViewById(R.id.tvCancellationType);
        TextView tvCancellationDesc = view.findViewById(R.id.tvCancellationDesc);
        TextView tvBottomPrice = view.findViewById(R.id.tvBottomPrice);
        btnReserve = view.findViewById(R.id.btnReserve);

        final String activityId = id;
        btnBack.setOnClickListener(v -> Navigation.findNavController(view).popBackStack());
        if (btnFavorite != null) {
            btnFavorite.setOnClickListener(v -> onFavoriteButtonClicked(activityId));
        }
        btnReserve.setOnClickListener(v -> {
            if (loadedDetail == null) return;
            showReservationDialog(loadedDetail);
        });

        DetailPhotoAdapter photoAdapter = new DetailPhotoAdapter();
        photoPager.setAdapter(photoAdapter);

        realtimeDatabaseApi.getActivity(activityId).enqueue(new Callback<ActivityRtdbDto>() {
            @Override
            public void onResponse(
                    @NonNull Call<ActivityRtdbDto> call,
                    @NonNull Response<ActivityRtdbDto> response
            ) {
                if (!isAdded()) return;
                ActivityRtdbDto body = response.body();
                if (!response.isSuccessful() || body == null) {
                    progress.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), R.string.detail_load_error, Toast.LENGTH_LONG).show();
                    Navigation.findNavController(view).popBackStack();
                    return;
                }
                ActivityDetail detail = ActivityDetail.fromRtdbDto(body, activityId);
                if (detail == null) {
                    progress.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), R.string.detail_load_error, Toast.LENGTH_LONG).show();
                    Navigation.findNavController(view).popBackStack();
                    return;
                }
                loadedDetail = detail;
                bindDetail(
                        detail, photoPager, dotsContainer, photoAdapter,
                        tvHeroCategory, tvTitle, tvSubtitle, tvWhenLabel, tvActivityWhen,
                        tvDuration, tvLanguage, tvCupos, tvGuide, tvDescription,
                        tvIncludesTitle, includesContainer,
                        tvMeetingTitle, cardMeeting, tvMeetingPoint, btnViewLocation,
                        tvCancellationTitle, cardCancellation, tvCancellationType, tvCancellationDesc,
                        tvBottomPrice, btnReserve);
                setupFavoriteAfterLoad(activityId, detail);
                progress.setVisibility(View.GONE);
                contentRoot.setVisibility(View.VISIBLE);
            }

            @Override
            public void onFailure(@NonNull Call<ActivityRtdbDto> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                progress.setVisibility(View.GONE);
                Toast.makeText(requireContext(), R.string.detail_load_error, Toast.LENGTH_LONG).show();
                Navigation.findNavController(view).popBackStack();
            }
        });
    }

    private void setupFavoriteAfterLoad(@NonNull String activityId, @NonNull ActivityDetail detail) {
        favoritesRepository.loadOne(effectiveUid, activityId, dto -> {
            if (!isAdded()) return;
            isFavorite = dto != null;
            reflectFavoriteIcon();
            if (isFavorite) {
                favoritesRepository.syncBaseline(
                        effectiveUid,
                        activityId,
                        detail.price,
                        detail.availableSpots,
                        err -> { });
            }
        });
    }

    private void reflectFavoriteIcon() {
        if (btnFavorite == null || !isAdded()) return;
        if (isFavorite) {
            btnFavorite.setImageResource(R.drawable.ic_favorite_filled_24);
            ImageViewCompat.setImageTintList(btnFavorite, null);
        } else {
            btnFavorite.setImageResource(R.drawable.ic_favorite_border_24);
            ImageViewCompat.setImageTintList(btnFavorite, ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), android.R.color.white)));
        }
    }

    private void onFavoriteButtonClicked(@NonNull String activityId) {
        if (loadedDetail == null) return;
        if (isFavorite) {
            favoritesRepository.removeFavorite(effectiveUid, activityId, err -> {
                if (!isAdded()) return;
                if (err == null) {
                    isFavorite = false;
                    reflectFavoriteIcon();
                } else {
                    Toast.makeText(requireContext(), R.string.favorites_load_error, Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            favoritesRepository.addFavorite(effectiveUid, loadedDetail, err -> {
                if (!isAdded()) return;
                if (err == null) {
                    isFavorite = true;
                    reflectFavoriteIcon();
                } else {
                    Toast.makeText(requireContext(), R.string.favorites_load_error, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void bindDetail(
            @NonNull ActivityDetail detail,
            @NonNull ViewPager2 photoPager,
            @NonNull LinearLayout dotsContainer,
            @NonNull DetailPhotoAdapter photoAdapter,
            @NonNull TextView tvHeroCategory,
            @NonNull TextView tvTitle,
            @NonNull TextView tvSubtitle,
            @NonNull TextView tvWhenLabel,
            @NonNull TextView tvActivityWhen,
            @NonNull TextView tvDuration,
            @NonNull TextView tvLanguage,
            @NonNull TextView tvCupos,
            @NonNull TextView tvGuide,
            @NonNull TextView tvDescription,
            @NonNull TextView tvIncludesTitle,
            @NonNull LinearLayout includesContainer,
            @NonNull TextView tvMeetingTitle,
            @NonNull View cardMeeting,
            @NonNull TextView tvMeetingPoint,
            @NonNull MaterialButton btnViewLocation,
            @NonNull TextView tvCancellationTitle,
            @NonNull View cardCancellation,
            @NonNull TextView tvCancellationType,
            @NonNull TextView tvCancellationDesc,
            @NonNull TextView tvBottomPrice,
            @NonNull MaterialButton reserveButton
    ) {
        List<String> urls = detail.imageUrls;
        photoAdapter.submit(urls.isEmpty() ? null : urls);
        int photoCount = urls.isEmpty() ? 1 : urls.size();
        photoPager.setOffscreenPageLimit(Math.min(photoCount, 3));

        setupDots(dotsContainer, photoPager, photoCount);

        tvHeroCategory.setText(detail.categoryLabel());
        tvTitle.setText(detail.name);
        tvSubtitle.setText(getString(R.string.detail_subtitle_fmt,
                detail.destination.isEmpty() ? "—" : detail.destination,
                detail.rating,
                detail.reviewCount));

        String whenLine = detail.formattedWhenLine();
        if (whenLine.isEmpty()) {
            tvWhenLabel.setVisibility(View.GONE);
            tvActivityWhen.setVisibility(View.GONE);
        } else {
            tvWhenLabel.setVisibility(View.VISIBLE);
            tvActivityWhen.setVisibility(View.VISIBLE);
            tvActivityWhen.setText("Fecha: " + whenLine);
        }

        tvDuration.setText(detail.formattedDurationLong());
        tvLanguage.setText(detail.languagesDisplay());
        tvCupos.setText(getString(R.string.detail_spots_fmt, (int) detail.availableSpots));
        if (detail.availableSpots > 0 && detail.availableSpots <= CUPOS_LOW) {
            tvCupos.setTextColor(ContextCompat.getColor(requireContext(), R.color.detail_cupos_low));
        } else {
            tvCupos.setTextColor(ContextCompat.getColor(requireContext(), R.color.explore_title));
        }

        tvGuide.setText(detail.guideName.isEmpty() ? "—" : detail.guideName);
        tvDescription.setText(detail.description);

        includesContainer.removeAllViews();
        if (detail.includes.isEmpty()) {
            tvIncludesTitle.setVisibility(View.GONE);
            includesContainer.setVisibility(View.GONE);
        } else {
            tvIncludesTitle.setVisibility(View.VISIBLE);
            includesContainer.setVisibility(View.VISIBLE);
            for (String line : detail.includes) {
                TextView row = new TextView(requireContext());
                row.setText("✓ " + line);
                row.setTextColor(ContextCompat.getColor(requireContext(), R.color.explore_muted));
                row.setTextSize(15);
                int sp = (int) (6 * getResources().getDisplayMetrics().density);
                row.setPadding(0, sp, 0, sp);
                includesContainer.addView(row);
            }
        }

        if (detail.meetingPoint.isEmpty()) {
            tvMeetingTitle.setVisibility(View.GONE);
            cardMeeting.setVisibility(View.GONE);
            btnViewLocation.setVisibility(View.GONE);
        } else {
            tvMeetingTitle.setVisibility(View.VISIBLE);
            cardMeeting.setVisibility(View.VISIBLE);
            btnViewLocation.setVisibility(View.VISIBLE);
            tvMeetingPoint.setText(getString(R.string.detail_meeting_point_fmt, detail.meetingPoint));
            btnViewLocation.setOnClickListener(v -> openMeetingPoint(detail));
        }

        bindCancellation(detail, tvCancellationTitle, cardCancellation, tvCancellationType, tvCancellationDesc);
        tvBottomPrice.setText(detail.priceLarge());
        updateReserveButton(detail, reserveButton);
    }

    private void openMeetingPoint(@NonNull ActivityDetail detail) {
        if (detail.meetingPoint.isEmpty()) {
            return;
        }
        ActivityDetail.GeoPoint meetingPointCoords = detail.meetingPointCoords;
        if (meetingPointCoords != null) {
            openMeetingPointWithCoordinates(detail.meetingPoint, meetingPointCoords);
            return;
        }

        String encodedMeetingPoint = Uri.encode(detail.meetingPoint);

        Intent googleMapsIntent = new Intent(
                Intent.ACTION_VIEW,
                Uri.parse("geo:0,0?q=" + encodedMeetingPoint + "(" + encodedMeetingPoint + ")")
        );
        googleMapsIntent.setPackage("com.google.android.apps.maps");
        if (startExternalIntent(googleMapsIntent)) {
            return;
        }

        Intent genericMapsIntent = new Intent(
                Intent.ACTION_VIEW,
                Uri.parse("geo:0,0?q=" + encodedMeetingPoint + "(" + encodedMeetingPoint + ")")
        );
        if (startExternalIntent(genericMapsIntent)) {
            return;
        }

        Intent browserFallbackIntent = new Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/maps/search/?api=1&query=" + encodedMeetingPoint)
        );
        if (startExternalIntent(browserFallbackIntent)) {
            return;
        }

        Toast.makeText(requireContext(), R.string.detail_map_app_missing, Toast.LENGTH_LONG).show();
    }

    private void openMeetingPointWithCoordinates(
            @NonNull String meetingPoint,
            @NonNull ActivityDetail.GeoPoint meetingPointCoords
    ) {
        String encodedLabel = Uri.encode(meetingPoint);
        String latLng = meetingPointCoords.lat + "," + meetingPointCoords.lng;

        Intent googleMapsIntent = new Intent(
                Intent.ACTION_VIEW,
                Uri.parse("geo:" + latLng + "?q=" + latLng + "(" + encodedLabel + ")")
        );
        googleMapsIntent.setPackage("com.google.android.apps.maps");
        if (startExternalIntent(googleMapsIntent)) {
            return;
        }

        Intent genericMapsIntent = new Intent(
                Intent.ACTION_VIEW,
                Uri.parse("geo:" + latLng + "?q=" + latLng + "(" + encodedLabel + ")")
        );
        if (startExternalIntent(genericMapsIntent)) {
            return;
        }

        Intent browserFallbackIntent = new Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/maps/search/?api=1&query=" + latLng)
        );
        if (startExternalIntent(browserFallbackIntent)) {
            return;
        }

        Toast.makeText(requireContext(), R.string.detail_map_app_missing, Toast.LENGTH_LONG).show();
    }

    private boolean startExternalIntent(@NonNull Intent intent) {
        if (!isAdded() || intent.resolveActivity(requireContext().getPackageManager()) == null) {
            return false;
        }
        Toast.makeText(requireContext(), R.string.detail_opening_location, Toast.LENGTH_SHORT).show();
        startActivity(intent);
        return true;
    }

    private void updateReserveButton(@NonNull ActivityDetail detail, @NonNull MaterialButton reserveButton) {
        if (detail.availableSpots <= 0) {
            reserveButton.setEnabled(false);
            reserveButton.setText(R.string.detail_no_spots);
            return;
        }
        if (!hasBookableDates(detail)) {
            reserveButton.setEnabled(false);
            reserveButton.setText(R.string.detail_no_schedule);
            return;
        }
        reserveButton.setEnabled(true);
        reserveButton.setText(R.string.detail_book);
    }

    private void showReservationDialog(@NonNull ActivityDetail detail) {
        if (reservationRepository == null) return;
        LocalDate today = LocalDate.now();
        LocalDate initialDate = detail.nextAvailableBookingDateFrom(today);
        if (initialDate == null) {
            Log.w(TAG, "No hay fechas reservables para activityId=" + detail.id + " day=" + detail.day + " dateIso=" + detail.dateIso);
            Toast.makeText(requireContext(), R.string.detail_no_schedule, Toast.LENGTH_SHORT).show();
            return;
        }
        Log.d(TAG, "Abriendo dialogo de reserva para activityId=" + detail.id
                + " day=" + detail.day
                + " initialDate=" + initialDate);

        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_activity_booking, null, false);
        MaterialButton btnBookingDate = dialogView.findViewById(R.id.btnBookingDate);
        TextView tvBookingDateHint = dialogView.findViewById(R.id.tvBookingDateHint);
        TextView tvBookingTimeLabel = dialogView.findViewById(R.id.tvBookingTimeLabel);
        Spinner spinnerTime = dialogView.findViewById(R.id.spinnerBookingTime);
        MaterialButton btnMinus = dialogView.findViewById(R.id.btnParticipantsMinus);
        MaterialButton btnPlus = dialogView.findViewById(R.id.btnParticipantsPlus);
        TextView tvParticipantsCount = dialogView.findViewById(R.id.tvParticipantsCount);
        TextView tvAvailability = dialogView.findViewById(R.id.tvBookingAvailability);
        TextView tvPolicy = dialogView.findViewById(R.id.tvBookingPolicy);

        int[] participants = {1};
        LocalDate[] selectedDate = {initialDate};
        List<LocalTime> availableTimes = detail.bookingTimes();
        LocalTime[] selectedTime = {availableTimes.isEmpty() ? null : availableTimes.get(0)};

        btnBookingDate.setText(formatBookingDate(selectedDate[0]));
        tvBookingDateHint.setText(buildBookingDateHint(detail, selectedDate[0]));

        Runnable renderState = () -> {
            ActivityDetail.BookingSlot slot = detail.buildCalendarBookingSlot(selectedDate[0], selectedTime[0]);
            long available = slot.availableSpots > 0 ? slot.availableSpots : detail.availableSpots;
            if (participants[0] > available && available > 0) {
                participants[0] = (int) available;
            }
            if (participants[0] < 1) {
                participants[0] = 1;
            }

            tvParticipantsCount.setText(String.valueOf(participants[0]));
            tvAvailability.setText(getString(
                    R.string.detail_booking_spots_fmt,
                    Math.max(0, available),
                    Math.max(0, available - participants[0])));
            btnMinus.setEnabled(participants[0] > 1);
            btnPlus.setEnabled(participants[0] < available);
        };

        if (availableTimes.isEmpty()) {
            tvBookingTimeLabel.setText(R.string.detail_booking_time_not_required);
            spinnerTime.setVisibility(View.GONE);
        } else {
            List<String> timeLabels = new ArrayList<>();
            for (LocalTime time : availableTimes) {
                timeLabels.add(time.format(BOOKING_TIME_FORMAT));
            }
            ArrayAdapter<String> timeAdapter = new ArrayAdapter<>(
                    requireContext(), android.R.layout.simple_spinner_item, timeLabels);
            timeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerTime.setAdapter(timeAdapter);
            spinnerTime.setSelection(0);
            spinnerTime.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    if (position >= 0 && position < availableTimes.size()) {
                        selectedTime[0] = availableTimes.get(position);
                        renderState.run();
                    }
                }

                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });
        }

        btnBookingDate.setOnClickListener(v -> {
            MaterialDatePicker.Builder<Long> builder = MaterialDatePicker.Builder.datePicker()
                    .setTitleText(R.string.detail_booking_date_picker_title)
                    .setSelection(toUtcMillis(selectedDate[0]));
            CalendarConstraints.Builder constraintsBuilder = new CalendarConstraints.Builder()
                    .setValidator(buildBookingDateValidator(detail, today))
                    .setStart(toUtcMillis(today));
            builder.setCalendarConstraints(constraintsBuilder.build());

            MaterialDatePicker<Long> picker = builder.build();
            picker.addOnPositiveButtonClickListener(selection -> {
                if (selection == null) {
                    return;
                }
                selectedDate[0] = fromUtcMillis(selection);
                btnBookingDate.setText(formatBookingDate(selectedDate[0]));
                tvBookingDateHint.setText(buildBookingDateHint(detail, selectedDate[0]));
                renderState.run();
            });
            picker.show(getChildFragmentManager(), "booking_date_picker");
        });

        btnMinus.setOnClickListener(v -> {
            if (participants[0] > 1) {
                participants[0]--;
                renderState.run();
            }
        });
        btnPlus.setOnClickListener(v -> {
            ActivityDetail.BookingSlot slot = detail.buildCalendarBookingSlot(selectedDate[0], selectedTime[0]);
            long available = slot.availableSpots > 0 ? slot.availableSpots : detail.availableSpots;
            if (participants[0] < available) {
                participants[0]++;
                renderState.run();
            }
        });

        if (detail.cancellationPolicy != null && detail.cancellationPolicy.hasContent()) {
            String type = detail.cancellationTypeLabel();
            String summary = detail.cancellationPolicy.description;
            if (summary.isEmpty() && detail.cancellationPolicy.freeCancelHours > 0) {
                summary = getString(R.string.detail_cancel_hours_only, detail.cancellationPolicy.freeCancelHours);
            }
            if (!type.isEmpty()) {
                tvPolicy.setText(getString(R.string.detail_booking_policy_fmt, type, summary));
            } else {
                tvPolicy.setText(summary);
            }
        } else {
            tvPolicy.setText(R.string.detail_booking_policy_fallback);
        }

        renderState.run();

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setNegativeButton(R.string.detail_booking_cancel, null)
                .setPositiveButton(R.string.detail_booking_confirm, null)
                .create();
        dialog.setOnShowListener(dlg -> {
            android.widget.Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(v -> {
                if (selectedDate[0] == null) {
                    Log.w(TAG, "Intento de reserva sin fecha seleccionada. activityId=" + detail.id);
                    Toast.makeText(requireContext(), R.string.detail_booking_date_missing, Toast.LENGTH_SHORT).show();
                    return;
                }
                ActivityDetail.BookingSlot slot = detail.buildCalendarBookingSlot(selectedDate[0], selectedTime[0]);
                Log.d(TAG, "Confirmando reserva activityId=" + detail.id
                        + " selectedDate=" + selectedDate[0]
                        + " selectedTime=" + (selectedTime[0] != null ? selectedTime[0] : "sin_hora")
                        + " participants=" + participants[0]
                        + " slotIso=" + slot.startAtIso);
                long available = slot.availableSpots > 0 ? slot.availableSpots : detail.availableSpots;
                if (participants[0] > available || available <= 0) {
                    Log.w(TAG, "Reserva rechazada por cupos. activityId=" + detail.id + " available=" + available + " requested=" + participants[0]);
                    Toast.makeText(requireContext(), R.string.detail_booking_insufficient_spots, Toast.LENGTH_SHORT).show();
                    renderState.run();
                    return;
                }

                setDialogEnabled(dialog, false);
                reservationRepository.createReservation(
                        effectiveUid,
                        detail.id,
                        slot,
                        participants[0],
                        new ReservationRepository.ActionCallback() {
                            @Override
                            public void onSuccess() {
                                if (!isAdded()) return;
                                Log.d(TAG, "Reserva creada con exito para activityId=" + detail.id);
                                dialog.dismiss();
                                Toast.makeText(requireContext(), R.string.detail_booking_success, Toast.LENGTH_SHORT).show();
                                Navigation.findNavController(requireView()).navigate(R.id.activityHistoryFragment);
                            }

                            @Override
                            public void onError(@NonNull String message) {
                                if (!isAdded()) return;
                                Log.w(TAG, "Fallo la reserva para activityId=" + detail.id + ": " + message);
                                setDialogEnabled(dialog, true);
                                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                            }
                        });
            });
        });
        dialog.show();
    }

    private boolean hasBookableDates(@NonNull ActivityDetail detail) {
        return detail.nextAvailableBookingDateFrom(LocalDate.now()) != null;
    }

    @NonNull
    private String buildBookingDateHint(@NonNull ActivityDetail detail, @NonNull LocalDate initialDate) {
        DayOfWeek bookingDay = detail.bookingDayOfWeek();
        if (bookingDay != null) {
            return getString(R.string.detail_booking_date_helper_day_fmt, weekdayPluralLabel(bookingDay));
        }
        return getString(R.string.detail_booking_date_helper_specific_fmt, formatBookingDate(initialDate));
    }

    @NonNull
    private static String formatBookingDate(@NonNull LocalDate date) {
        return date.format(BOOKING_DATE_FORMAT);
    }

    @NonNull
    private CalendarConstraints.DateValidator buildBookingDateValidator(
            @NonNull ActivityDetail detail,
            @NonNull LocalDate today
    ) {
        DayOfWeek bookingDay = detail.bookingDayOfWeek();
        if (bookingDay != null) {
            return new WeekdayFromTodayValidator(bookingDay.getValue(), today.toEpochDay());
        }

        List<Long> allowedEpochDays = new ArrayList<>();
        for (ActivityDetail.BookingSlot slot : detail.bookingSlots) {
            LocalDate date = slot.localDate();
            if (date != null && !date.isBefore(today) && !allowedEpochDays.contains(date.toEpochDay())) {
                allowedEpochDays.add(date.toEpochDay());
            }
        }
        if (allowedEpochDays.isEmpty()) {
            LocalDate fallbackDate = detail.nextAvailableBookingDateFrom(today);
            if (fallbackDate != null) {
                allowedEpochDays.add(fallbackDate.toEpochDay());
            }
        }
        return new SpecificDatesValidator(allowedEpochDays, today.toEpochDay());
    }

    @NonNull
    private static String weekdayPluralLabel(@NonNull DayOfWeek dayOfWeek) {
        switch (dayOfWeek) {
            case MONDAY:
                return "lunes";
            case TUESDAY:
                return "martes";
            case WEDNESDAY:
                return "miercoles";
            case THURSDAY:
                return "jueves";
            case FRIDAY:
                return "viernes";
            case SATURDAY:
                return "sabados";
            case SUNDAY:
            default:
                return "domingos";
        }
    }

    private static long toUtcMillis(@NonNull LocalDate date) {
        return date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    @NonNull
    private static LocalDate fromUtcMillis(long millis) {
        return java.time.Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate();
    }

    private void setDialogEnabled(@NonNull AlertDialog dialog, boolean enabled) {
        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(enabled);
        }
        if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(enabled);
        }
    }

    private void bindCancellation(
            @NonNull ActivityDetail detail,
            @NonNull TextView tvCancellationTitle,
            @NonNull View cardCancellation,
            @NonNull TextView tvCancellationType,
            @NonNull TextView tvCancellationDesc
    ) {
        ActivityDetail.CancellationPolicy policy = detail.cancellationPolicy;
        if (policy == null || !policy.hasContent()) {
            tvCancellationTitle.setVisibility(View.GONE);
            cardCancellation.setVisibility(View.GONE);
            return;
        }

        String typeLabel = detail.cancellationTypeLabel();
        String descText = policy.description;
        if (descText.isEmpty() && policy.freeCancelHours > 0) {
            descText = getString(R.string.detail_cancel_hours_only, policy.freeCancelHours);
        }

        boolean showType = !typeLabel.isEmpty();
        boolean showDesc = !descText.isEmpty();
        if (!showType && !showDesc) {
            tvCancellationTitle.setVisibility(View.GONE);
            cardCancellation.setVisibility(View.GONE);
            return;
        }

        tvCancellationTitle.setVisibility(View.VISIBLE);
        cardCancellation.setVisibility(View.VISIBLE);
        tvCancellationType.setVisibility(showType ? View.VISIBLE : View.GONE);
        if (showType) {
            tvCancellationType.setText(getString(R.string.detail_cancel_type_fmt, typeLabel));
        }
        tvCancellationDesc.setVisibility(showDesc ? View.VISIBLE : View.GONE);
        if (showDesc) {
            tvCancellationDesc.setText(descText);
        }
    }

    private void setupDots(@NonNull LinearLayout dotsContainer, @NonNull ViewPager2 pager, int count) {
        dotsContainer.removeAllViews();
        if (count <= 1) {
            dotsContainer.setVisibility(View.GONE);
            return;
        }
        dotsContainer.setVisibility(View.VISIBLE);
        float d = getResources().getDisplayMetrics().density;
        int sel = (int) (8 * d);
        int unsel = (int) (6 * d);
        int margin = (int) (4 * d);

        for (int i = 0; i < count; i++) {
            View dot = new View(requireContext());
            boolean first = i == 0;
            int w = first ? sel : unsel;
            int h = first ? sel : unsel;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
            lp.setMargins(margin, 0, margin, 0);
            dot.setLayoutParams(lp);
            dot.setBackgroundResource(first ? R.drawable.dot_detail_selected : R.drawable.dot_detail_unselected);
            dotsContainer.addView(dot);
        }

        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                for (int i = 0; i < dotsContainer.getChildCount(); i++) {
                    View dot = dotsContainer.getChildAt(i);
                    ViewGroup.LayoutParams lp = dot.getLayoutParams();
                    if (i == position) {
                        lp.width = sel;
                        lp.height = sel;
                        dot.setBackgroundResource(R.drawable.dot_detail_selected);
                    } else {
                        lp.width = unsel;
                        lp.height = unsel;
                        dot.setBackgroundResource(R.drawable.dot_detail_unselected);
                    }
                    dot.setLayoutParams(lp);
                }
            }
        });
    }

    private static final class WeekdayFromTodayValidator implements CalendarConstraints.DateValidator {
        private final int dayOfWeekValue;
        private final long minEpochDay;

        WeekdayFromTodayValidator(int dayOfWeekValue, long minEpochDay) {
            this.dayOfWeekValue = dayOfWeekValue;
            this.minEpochDay = minEpochDay;
        }

        private WeekdayFromTodayValidator(@NonNull Parcel source) {
            this.dayOfWeekValue = source.readInt();
            this.minEpochDay = source.readLong();
        }

        @Override
        public boolean isValid(long date) {
            LocalDate localDate = fromUtcMillis(date);
            return localDate.toEpochDay() >= minEpochDay
                    && localDate.getDayOfWeek().getValue() == dayOfWeekValue;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeInt(dayOfWeekValue);
            dest.writeLong(minEpochDay);
        }

        public static final Parcelable.Creator<WeekdayFromTodayValidator> CREATOR =
                new Parcelable.Creator<WeekdayFromTodayValidator>() {
                    @Override
                    public WeekdayFromTodayValidator createFromParcel(Parcel source) {
                        return new WeekdayFromTodayValidator(source);
                    }

                    @Override
                    public WeekdayFromTodayValidator[] newArray(int size) {
                        return new WeekdayFromTodayValidator[size];
                    }
                };
    }

    private static final class SpecificDatesValidator implements CalendarConstraints.DateValidator {
        @NonNull
        private final long[] allowedEpochDays;
        private final long minEpochDay;

        SpecificDatesValidator(@NonNull List<Long> allowedEpochDays, long minEpochDay) {
            this.allowedEpochDays = new long[allowedEpochDays.size()];
            for (int i = 0; i < allowedEpochDays.size(); i++) {
                this.allowedEpochDays[i] = allowedEpochDays.get(i);
            }
            this.minEpochDay = minEpochDay;
        }

        private SpecificDatesValidator(@NonNull Parcel source) {
            this.allowedEpochDays = source.createLongArray();
            this.minEpochDay = source.readLong();
        }

        @Override
        public boolean isValid(long date) {
            LocalDate localDate = fromUtcMillis(date);
            long epochDay = localDate.toEpochDay();
            if (epochDay < minEpochDay) {
                return false;
            }
            for (long allowedEpochDay : allowedEpochDays) {
                if (allowedEpochDay == epochDay) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeLongArray(allowedEpochDays);
            dest.writeLong(minEpochDay);
        }

        public static final Parcelable.Creator<SpecificDatesValidator> CREATOR =
                new Parcelable.Creator<SpecificDatesValidator>() {
                    @Override
                    public SpecificDatesValidator createFromParcel(Parcel source) {
                        return new SpecificDatesValidator(source);
                    }

                    @Override
                    public SpecificDatesValidator[] newArray(int size) {
                        return new SpecificDatesValidator[size];
                    }
                };
    }
}
