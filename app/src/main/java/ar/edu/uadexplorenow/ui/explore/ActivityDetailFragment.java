package ar.edu.uadexplorenow.ui.explore;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
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
import ar.edu.uadexplorenow.data.local.cache.UserProfileFileCache;
import ar.edu.uadexplorenow.data.local.db.CachedReservationDao;
import ar.edu.uadexplorenow.data.model.ActivityRtdbDto;
import ar.edu.uadexplorenow.data.model.UserRtdbDto;
import ar.edu.uadexplorenow.data.network.RealtimeDatabaseApi;
import ar.edu.uadexplorenow.domain.ActivityDetail;
import ar.edu.uadexplorenow.ui.reservations.BookingConfirmationFragment;

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
import java.time.YearMonth;
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
    @Inject
    UserProfileFileCache userProfileFileCache;

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

    /** Para refrescar rating/reseñas al volver desde otras pantallas. */
    @Nullable
    private TextView tvSubtitleRef;
    private String loadedActivityId = "";

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
        tvSubtitleRef = tvSubtitle;
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
                loadedActivityId = activityId;
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

    @Override
    public void onResume() {
        super.onResume();
        if (loadedActivityId.isEmpty() || tvSubtitleRef == null) {
            return;
        }
        realtimeDatabaseApi.getActivity(loadedActivityId).enqueue(new Callback<ActivityRtdbDto>() {
            @Override
            public void onResponse(@NonNull Call<ActivityRtdbDto> call, @NonNull Response<ActivityRtdbDto> response) {
                if (!isAdded() || tvSubtitleRef == null) return;
                ActivityRtdbDto body = response.body();
                if (!response.isSuccessful() || body == null) return;
                ActivityDetail detail = ActivityDetail.fromRtdbDto(body, loadedActivityId);
                if (detail == null) return;
                loadedDetail = detail;
                bindSubtitleLine(tvSubtitleRef, detail);
            }

            @Override
            public void onFailure(@NonNull Call<ActivityRtdbDto> call, @NonNull Throwable t) {}
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
        bindSubtitleLine(tvSubtitle, detail);

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

    private void bindSubtitleLine(@NonNull TextView tvSubtitle, @NonNull ActivityDetail detail) {
        String dest = detail.destination.isEmpty() ? "—" : detail.destination;
        if (detail.reviewCount <= 0) {
            tvSubtitle.setText(getString(R.string.detail_subtitle_no_reviews_yet, dest));
        } else {
            tvSubtitle.setText(getString(R.string.detail_subtitle_fmt,
                    dest,
                    detail.rating,
                    detail.reviewCount));
        }
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

        UserRtdbDto cachedProfile = userProfileFileCache.load();
        realtimeDatabaseApi.getUser(effectiveUid).enqueue(new Callback<UserRtdbDto>() {
            @Override
            public void onResponse(@NonNull Call<UserRtdbDto> call, @NonNull Response<UserRtdbDto> response) {
                if (!isAdded()) return;
                UserRtdbDto profile = response.isSuccessful() && response.body() != null
                        ? response.body()
                        : cachedProfile;
                presentReservationDialog(detail, initialDate, profile != null ? profile : cachedProfile);
            }

            @Override
            public void onFailure(@NonNull Call<UserRtdbDto> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                presentReservationDialog(detail, initialDate, cachedProfile);
            }
        });
    }

    private void presentReservationDialog(
            @NonNull ActivityDetail detail,
            @NonNull LocalDate initialDate,
            @Nullable UserRtdbDto profile
    ) {
        if (reservationRepository == null || !isAdded()) return;

        Log.d(TAG, "Abriendo dialogo de reserva para activityId=" + detail.id
                + " day=" + detail.day
                + " initialDate=" + initialDate);

        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_activity_booking, null, false);
        MaterialButton btnBookingDate = dialogView.findViewById(R.id.btnBookingDate);
        btnBookingDate.setTextColor(ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.explore_title)));
        TextView tvBookingDateHint = dialogView.findViewById(R.id.tvBookingDateHint);
        TextView tvBookingTimeLabel = dialogView.findViewById(R.id.tvBookingTimeLabel);
        Spinner spinnerTime = dialogView.findViewById(R.id.spinnerBookingTime);
        MaterialButton btnMinus = dialogView.findViewById(R.id.btnParticipantsMinus);
        MaterialButton btnPlus = dialogView.findViewById(R.id.btnParticipantsPlus);
        TextView tvParticipantsCount = dialogView.findViewById(R.id.tvParticipantsCount);
        TextView tvAvailability = dialogView.findViewById(R.id.tvBookingAvailability);
        TextView tvPolicy = dialogView.findViewById(R.id.tvBookingPolicy);
        View blockPayment = dialogView.findViewById(R.id.blockPayment);
        TextView tvBookingTotalHint = dialogView.findViewById(R.id.tvBookingTotalHint);
        TextView tvBookingTotalAmount = dialogView.findViewById(R.id.tvBookingTotalAmount);
        EditText etPayerFullName = dialogView.findViewById(R.id.etPayerFullName);
        EditText etPayerEmail = dialogView.findViewById(R.id.etPayerEmail);
        EditText etPayerPhone = dialogView.findViewById(R.id.etPayerPhone);
        EditText etPayerDocument = dialogView.findViewById(R.id.etPayerDocument);
        EditText etCardNumber = dialogView.findViewById(R.id.etCardNumber);
        EditText etCardExpiry = dialogView.findViewById(R.id.etCardExpiry);
        EditText etCardCvv = dialogView.findViewById(R.id.etCardCvv);
        TextView tvBookingPayerSummary = dialogView.findViewById(R.id.tvBookingPayerSummary);
        View layoutPayerName = dialogView.findViewById(R.id.layoutPayerName);
        View layoutPayerEmail = dialogView.findViewById(R.id.layoutPayerEmail);
        View layoutPayerPhone = dialogView.findViewById(R.id.layoutPayerPhone);
        ScrollView bookingScroll = dialogView.findViewById(R.id.bookingDialogScroll);
        TextView tvErrPayerFullName = dialogView.findViewById(R.id.tvErrPayerFullName);
        TextView tvErrPayerEmail = dialogView.findViewById(R.id.tvErrPayerEmail);
        TextView tvErrPayerPhone = dialogView.findViewById(R.id.tvErrPayerPhone);
        TextView tvErrPayerDocument = dialogView.findViewById(R.id.tvErrPayerDocument);
        TextView tvErrCardNumber = dialogView.findViewById(R.id.tvErrCardNumber);
        TextView tvErrCardExpiry = dialogView.findViewById(R.id.tvErrCardExpiry);
        TextView tvErrCardCvv = dialogView.findViewById(R.id.tvErrCardCvv);
        attachCardExpiryMmYyFormatter(etCardExpiry);

        final boolean requiresPayment = detail.price > 0;
        applyAuthenticatedPayerProfile(
                profile,
                etPayerFullName,
                etPayerEmail,
                etPayerPhone,
                tvBookingPayerSummary,
                layoutPayerName,
                layoutPayerEmail,
                layoutPayerPhone,
                requiresPayment
        );

        BookingPaymentRefs paymentRefs = new BookingPaymentRefs(
                bookingScroll,
                etPayerFullName, etPayerEmail, etPayerPhone, etPayerDocument,
                etCardNumber, etCardExpiry, etCardCvv,
                tvErrPayerFullName, tvErrPayerEmail, tvErrPayerPhone, tvErrPayerDocument,
                tvErrCardNumber, tvErrCardExpiry, tvErrCardCvv);
        Runnable syncPaymentRealtime = () ->
                applyBookingPaymentInlineStates(paymentRefs, requiresPayment, false);

        EditText[] paymentEditTexts = paymentRefs.paymentFields();
        TextWatcher realtimeWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                syncPaymentRealtime.run();
            }
        };
        if (requiresPayment) {
            for (EditText et : paymentEditTexts) {
                et.addTextChangedListener(realtimeWatcher);
            }
        }
        blockPayment.setVisibility(requiresPayment ? View.VISIBLE : View.GONE);

        int[] participants = {1};
        LocalDate[] selectedDate = {initialDate};
        LocalDate today = LocalDate.now();
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
            if (requiresPayment) {
                String personWord = participants[0] == 1
                        ? getString(R.string.detail_booking_person_singular)
                        : getString(R.string.detail_booking_person_plural);
                tvBookingTotalHint.setText(getString(
                        R.string.detail_booking_total_hint_fmt,
                        participants[0],
                        personWord));
                tvBookingTotalAmount.setText(formatBookingTotal(detail, participants[0]));
            }
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

                if (!applyBookingPaymentInlineStates(paymentRefs, requiresPayment, true)) {
                    bookingScroll.post(() -> focusFirstVisibleBookingPaymentIssue(paymentRefs));
                    return;
                }

                setBookingDialogBusy(dialog, dialogView, false);
                reservationRepository.createReservation(
                        effectiveUid,
                        detail.id,
                        slot,
                        participants[0],
                        new ReservationRepository.CreateReservationCallback() {
                            @Override
                            public void onSuccess(@NonNull String reservationId) {
                                if (!isAdded()) return;
                                Log.d(TAG, "Reserva creada con exito para activityId=" + detail.id);
                                dialog.dismiss();
                                navigateToBookingConfirmation(
                                        reservationId,
                                        detail,
                                        selectedDate[0],
                                        selectedTime[0],
                                        participants[0]
                                );
                            }

                            @Override
                            public void onError(@NonNull String message) {
                                if (!isAdded()) return;
                                Log.w(TAG, "Fallo la reserva para activityId=" + detail.id + ": " + message);
                                setBookingDialogBusy(dialog, dialogView, true);
                                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                            }
                        });
            });
        });
        dialog.show();
    }

    @NonNull
    private String formatBookingTotal(@NonNull ActivityDetail d, int participants) {
        int p = Math.max(1, participants);
        double total = d.price * p;
        String currency = d.currency != null ? d.currency.trim() : "";
        if (currency.isEmpty() || "ARS".equalsIgnoreCase(currency) || "$".equals(currency)) {
            return String.format(Locale.getDefault(), "$%.0f", total);
        }
        return String.format(Locale.getDefault(), "%.0f %s", total, currency);
    }

    private void applyAuthenticatedPayerProfile(
            @Nullable UserRtdbDto profile,
            @NonNull EditText etName,
            @NonNull EditText etEmail,
            @NonNull EditText etPhone,
            @NonNull TextView tvSummary,
            @NonNull View layoutName,
            @NonNull View layoutEmail,
            @NonNull View layoutPhone,
            boolean requiresPayment
    ) {
        if (!requiresPayment) {
            layoutName.setVisibility(View.GONE);
            layoutEmail.setVisibility(View.GONE);
            layoutPhone.setVisibility(View.GONE);
            tvSummary.setVisibility(View.GONE);
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String name = profile != null && profile.name != null ? profile.name.trim() : "";
        String email = profile != null && profile.email != null ? profile.email.trim() : "";
        if (email.isEmpty() && user != null) {
            String effectiveEmail = SessionStore.getEffectiveEmail(requireContext(), user);
            email = effectiveEmail != null ? effectiveEmail.trim() : "";
        }
        String phone = profile != null && profile.phone != null ? profile.phone.trim() : "";

        boolean hasName = name.length() >= 3;
        boolean hasEmail = !email.isEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches();
        boolean hasPhone = digitsOnly(phone).length() >= 8;

        if (hasName) {
            etName.setText(name);
        }
        if (hasEmail) {
            etEmail.setText(email);
        }
        if (hasPhone) {
            etPhone.setText(phone);
            layoutPhone.setVisibility(View.GONE);
        }

        if (hasName && hasEmail) {
            tvSummary.setVisibility(View.VISIBLE);
        } else {
            tvSummary.setVisibility(View.GONE);
        }
    }

    private void navigateToBookingConfirmation(
            @NonNull String reservationId,
            @NonNull ActivityDetail detail,
            @NonNull LocalDate selectedDate,
            @Nullable LocalTime selectedTime,
            int participants
    ) {
        Bundle args = new Bundle();
        args.putString(BookingConfirmationFragment.ARG_RESERVATION_ID, reservationId);
        args.putString(BookingConfirmationFragment.ARG_ACTIVITY_NAME, detail.name);
        args.putString(BookingConfirmationFragment.ARG_SELECTED_DATE, formatBookingDate(selectedDate));
        args.putString(
                BookingConfirmationFragment.ARG_SELECTED_TIME,
                selectedTime != null ? selectedTime.format(BOOKING_TIME_FORMAT) : "");
        args.putInt(BookingConfirmationFragment.ARG_PARTICIPANTS, participants);
        if (detail.price > 0) {
            args.putString(BookingConfirmationFragment.ARG_TOTAL, formatBookingTotal(detail, participants));
        }
        Navigation.findNavController(requireView())
                .navigate(R.id.action_activityDetailFragment_to_bookingConfirmationFragment, args);
    }

    /**
     * Referencias a campos de pago del diálogo de reserva (validación en línea).
     */
    private static final class BookingPaymentRefs {
        @NonNull final ScrollView scrollView;
        @NonNull final EditText etName;
        @NonNull final EditText etEmail;
        @NonNull final EditText etPhone;
        @NonNull final EditText etDocument;
        @NonNull final EditText etCardNumber;
        @NonNull final EditText etExpiry;
        @NonNull final EditText etCvv;
        @NonNull final TextView tvErrName;
        @NonNull final TextView tvErrEmail;
        @NonNull final TextView tvErrPhone;
        @NonNull final TextView tvErrDocument;
        @NonNull final TextView tvErrCardNumber;
        @NonNull final TextView tvErrExpiry;
        @NonNull final TextView tvErrCvv;

        BookingPaymentRefs(
                @NonNull ScrollView scrollView,
                @NonNull EditText etName,
                @NonNull EditText etEmail,
                @NonNull EditText etPhone,
                @NonNull EditText etDocument,
                @NonNull EditText etCardNumber,
                @NonNull EditText etExpiry,
                @NonNull EditText etCvv,
                @NonNull TextView tvErrName,
                @NonNull TextView tvErrEmail,
                @NonNull TextView tvErrPhone,
                @NonNull TextView tvErrDocument,
                @NonNull TextView tvErrCardNumber,
                @NonNull TextView tvErrExpiry,
                @NonNull TextView tvErrCvv
        ) {
            this.scrollView = scrollView;
            this.etName = etName;
            this.etEmail = etEmail;
            this.etPhone = etPhone;
            this.etDocument = etDocument;
            this.etCardNumber = etCardNumber;
            this.etExpiry = etExpiry;
            this.etCvv = etCvv;
            this.tvErrName = tvErrName;
            this.tvErrEmail = tvErrEmail;
            this.tvErrPhone = tvErrPhone;
            this.tvErrDocument = tvErrDocument;
            this.tvErrCardNumber = tvErrCardNumber;
            this.tvErrExpiry = tvErrExpiry;
            this.tvErrCvv = tvErrCvv;
        }

        @NonNull
        EditText[] paymentFields() {
            return new EditText[]{etName, etEmail, etPhone, etDocument, etCardNumber, etExpiry, etCvv};
        }
    }

    private static void setBookingFieldError(@NonNull TextView label, boolean showError, @NonNull CharSequence message) {
        if (!showError) {
            label.setVisibility(View.GONE);
            label.setText("");
        } else {
            label.setVisibility(View.VISIBLE);
            label.setText(message);
        }
    }

    private static void clearAllBookingPaymentErrors(@NonNull BookingPaymentRefs r) {
        setBookingFieldError(r.tvErrName, false, "");
        setBookingFieldError(r.tvErrEmail, false, "");
        setBookingFieldError(r.tvErrPhone, false, "");
        setBookingFieldError(r.tvErrDocument, false, "");
        setBookingFieldError(r.tvErrCardNumber, false, "");
        setBookingFieldError(r.tvErrExpiry, false, "");
        setBookingFieldError(r.tvErrCvv, false, "");
    }

    /**
     * Valida datos de pago y muestra mensajes bajo cada campo.
     *
     * @param strictEmpty si es true, los campos vacíos se marcan como error (al confirmar).
     */
    private boolean applyBookingPaymentInlineStates(
            @NonNull BookingPaymentRefs r,
            boolean required,
            boolean strictEmpty
    ) {
        if (!required) {
            clearAllBookingPaymentErrors(r);
            return true;
        }
        boolean allOk = true;

        String name = r.etName.getText().toString().trim();
        if (name.isEmpty()) {
            if (strictEmpty) {
                setBookingFieldError(r.tvErrName, true, getString(R.string.detail_booking_payment_name_invalid));
                allOk = false;
            } else {
                setBookingFieldError(r.tvErrName, false, "");
            }
        } else if (name.length() < 3) {
            setBookingFieldError(r.tvErrName, true, getString(R.string.detail_booking_payment_name_invalid));
            allOk = false;
        } else {
            setBookingFieldError(r.tvErrName, false, "");
        }

        String email = r.etEmail.getText().toString().trim();
        if (email.isEmpty()) {
            if (strictEmpty) {
                setBookingFieldError(r.tvErrEmail, true, getString(R.string.detail_booking_payment_email_invalid));
                allOk = false;
            } else {
                setBookingFieldError(r.tvErrEmail, false, "");
            }
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            setBookingFieldError(r.tvErrEmail, true, getString(R.string.detail_booking_payment_email_invalid));
            allOk = false;
        } else {
            setBookingFieldError(r.tvErrEmail, false, "");
        }

        String phoneDigits = digitsOnly(r.etPhone.getText().toString());
        if (phoneDigits.isEmpty()) {
            if (strictEmpty) {
                setBookingFieldError(r.tvErrPhone, true, getString(R.string.detail_booking_payment_phone_invalid));
                allOk = false;
            } else {
                setBookingFieldError(r.tvErrPhone, false, "");
            }
        } else if (phoneDigits.length() < 8) {
            setBookingFieldError(r.tvErrPhone, true, getString(R.string.detail_booking_payment_phone_invalid));
            allOk = false;
        } else {
            setBookingFieldError(r.tvErrPhone, false, "");
        }

        String document = r.etDocument.getText().toString().trim().replace(".", "").replace(" ", "");
        if (document.isEmpty()) {
            if (strictEmpty) {
                setBookingFieldError(r.tvErrDocument, true, getString(R.string.detail_booking_payment_document_invalid));
                allOk = false;
            } else {
                setBookingFieldError(r.tvErrDocument, false, "");
            }
        } else if (document.length() < 7) {
            setBookingFieldError(r.tvErrDocument, true, getString(R.string.detail_booking_payment_document_invalid));
            allOk = false;
        } else {
            setBookingFieldError(r.tvErrDocument, false, "");
        }

        String cardDigits = digitsOnly(r.etCardNumber.getText().toString());
        if (cardDigits.isEmpty()) {
            if (strictEmpty) {
                setBookingFieldError(r.tvErrCardNumber, true, getString(R.string.detail_booking_payment_card_invalid));
                allOk = false;
            } else {
                setBookingFieldError(r.tvErrCardNumber, false, "");
            }
        } else if (cardDigits.length() < 13) {
            setBookingFieldError(r.tvErrCardNumber, true, getString(R.string.detail_booking_payment_card_short));
            allOk = false;
        } else if (cardDigits.length() > 19) {
            setBookingFieldError(r.tvErrCardNumber, true, getString(R.string.detail_booking_payment_card_invalid));
            allOk = false;
        } else {
            setBookingFieldError(r.tvErrCardNumber, false, "");
        }

        String expiryRaw = r.etExpiry.getText().toString().trim();
        String expiryDigits = digitsOnly(expiryRaw);
        if (expiryDigits.isEmpty()) {
            if (strictEmpty) {
                setBookingFieldError(r.tvErrExpiry, true, getString(R.string.detail_booking_payment_expiry_invalid));
                allOk = false;
            } else {
                setBookingFieldError(r.tvErrExpiry, false, "");
            }
        } else if (expiryDigits.length() < 4) {
            if (strictEmpty) {
                setBookingFieldError(r.tvErrExpiry, true, getString(R.string.detail_booking_payment_expiry_invalid));
                allOk = false;
            } else {
                setBookingFieldError(r.tvErrExpiry, false, "");
            }
        } else if (!isCardExpiryValid(expiryRaw)) {
            setBookingFieldError(r.tvErrExpiry, true, getString(R.string.detail_booking_payment_expiry_invalid));
            allOk = false;
        } else {
            setBookingFieldError(r.tvErrExpiry, false, "");
        }

        String cvvDigits = digitsOnly(r.etCvv.getText().toString());
        if (cvvDigits.isEmpty()) {
            if (strictEmpty) {
                setBookingFieldError(r.tvErrCvv, true, getString(R.string.detail_booking_payment_cvv_invalid));
                allOk = false;
            } else {
                setBookingFieldError(r.tvErrCvv, false, "");
            }
        } else if (cvvDigits.length() < 3 || cvvDigits.length() > 4) {
            setBookingFieldError(r.tvErrCvv, true, getString(R.string.detail_booking_payment_cvv_invalid));
            allOk = false;
        } else {
            setBookingFieldError(r.tvErrCvv, false, "");
        }

        return allOk;
    }

    private void focusFirstVisibleBookingPaymentIssue(@NonNull BookingPaymentRefs p) {
        if (p.tvErrName.getVisibility() == View.VISIBLE) {
            p.etName.requestFocus();
            return;
        }
        if (p.tvErrEmail.getVisibility() == View.VISIBLE) {
            p.etEmail.requestFocus();
            return;
        }
        if (p.tvErrPhone.getVisibility() == View.VISIBLE) {
            p.etPhone.requestFocus();
            return;
        }
        if (p.tvErrDocument.getVisibility() == View.VISIBLE) {
            p.etDocument.requestFocus();
            return;
        }
        if (p.tvErrCardNumber.getVisibility() == View.VISIBLE) {
            p.etCardNumber.requestFocus();
            return;
        }
        if (p.tvErrExpiry.getVisibility() == View.VISIBLE) {
            p.etExpiry.requestFocus();
            return;
        }
        if (p.tvErrCvv.getVisibility() == View.VISIBLE) {
            p.etCvv.requestFocus();
        }
    }

    /**
     * Formato MM/AA: inserta la barra tras el mes y acepta como máximo 4 dígitos (5 caracteres con /).
     */
    private static void attachCardExpiryMmYyFormatter(@NonNull EditText et) {
        et.addTextChangedListener(new TextWatcher() {
            boolean selfChange;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (selfChange) {
                    return;
                }
                String digits = digitsOnly(s.toString());
                if (digits.length() > 4) {
                    digits = digits.substring(0, 4);
                }
                StringBuilder out = new StringBuilder();
                for (int i = 0; i < digits.length(); i++) {
                    if (i == 2) {
                        out.append('/');
                    }
                    out.append(digits.charAt(i));
                }
                String formatted = out.toString();
                if (!formatted.contentEquals(s)) {
                    selfChange = true;
                    s.replace(0, s.length(), formatted);
                    selfChange = false;
                }
            }
        });
    }

    private static boolean isCardExpiryValid(@NonNull String raw) {
        String t = raw.trim();
        if (!t.matches("^(0[1-9]|1[0-2])/([0-9]{2})$")) {
            return false;
        }
        int mm = Integer.parseInt(t.substring(0, 2));
        int yy = Integer.parseInt(t.substring(3, 5));
        YearMonth exp = YearMonth.of(2000 + yy, mm);
        return !exp.isBefore(YearMonth.now());
    }

    @NonNull
    private static String digitsOnly(@Nullable String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                b.append(c);
            }
        }
        return b.toString();
    }

    private void setBookingDialogBusy(@NonNull AlertDialog dialog, @NonNull View dialogView, boolean interactive) {
        setDialogEnabled(dialog, interactive);
        int[] ids = {
                R.id.btnBookingDate,
                R.id.btnParticipantsMinus,
                R.id.btnParticipantsPlus,
                R.id.spinnerBookingTime,
                R.id.etPayerFullName,
                R.id.etPayerEmail,
                R.id.etPayerPhone,
                R.id.etPayerDocument,
                R.id.etCardNumber,
                R.id.etCardExpiry,
                R.id.etCardCvv
        };
        for (int id : ids) {
            View v = dialogView.findViewById(id);
            if (v != null) {
                v.setEnabled(interactive);
            }
        }
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
