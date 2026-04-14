package ar.edu.uadexplorenow.ui.explore;

import android.os.Bundle;
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
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.viewpager2.widget.ViewPager2;

import ar.edu.uadexplorenow.R;
import ar.edu.uadexplorenow.data.ReservationRepository;
import ar.edu.uadexplorenow.data.SessionStore;
import ar.edu.uadexplorenow.data.model.ActivityRtdbDto;
import ar.edu.uadexplorenow.data.network.RealtimeDatabaseApi;
import ar.edu.uadexplorenow.domain.ActivityDetail;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class ActivityDetailFragment extends Fragment {

    @Inject
    RealtimeDatabaseApi realtimeDatabaseApi;

    public static final String ARG_ACTIVITY_ID = "activity_id";

    private static final int CUPOS_LOW = 5;

    private String effectiveUid = "";
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
        reservationRepository = new ReservationRepository(realtimeDatabaseApi);

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
                        tvMeetingTitle, cardMeeting, tvMeetingPoint,
                        tvCancellationTitle, cardCancellation, tvCancellationType, tvCancellationDesc,
                        tvBottomPrice, btnReserve);
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
        } else {
            tvMeetingTitle.setVisibility(View.VISIBLE);
            cardMeeting.setVisibility(View.VISIBLE);
            tvMeetingPoint.setText("Punto de encuentro: " + detail.meetingPoint);
        }

        bindCancellation(detail, tvCancellationTitle, cardCancellation, tvCancellationType, tvCancellationDesc);
        tvBottomPrice.setText(detail.priceLarge());
        updateReserveButton(detail, reserveButton);
    }

    private void updateReserveButton(@NonNull ActivityDetail detail, @NonNull MaterialButton reserveButton) {
        if (detail.availableSpots <= 0) {
            reserveButton.setEnabled(false);
            reserveButton.setText(R.string.detail_no_spots);
            return;
        }
        if (detail.bookingSlots.isEmpty()) {
            reserveButton.setEnabled(false);
            reserveButton.setText(R.string.detail_no_schedule);
            return;
        }
        reserveButton.setEnabled(true);
        reserveButton.setText(R.string.detail_book);
    }

    private void showReservationDialog(@NonNull ActivityDetail detail) {
        if (reservationRepository == null) return;
        if (detail.bookingSlots.isEmpty()) {
            Toast.makeText(requireContext(), R.string.detail_no_schedule, Toast.LENGTH_SHORT).show();
            return;
        }

        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_activity_booking, null, false);
        Spinner spinnerDate = dialogView.findViewById(R.id.spinnerBookingDate);
        Spinner spinnerTime = dialogView.findViewById(R.id.spinnerBookingTime);
        MaterialButton btnMinus = dialogView.findViewById(R.id.btnParticipantsMinus);
        MaterialButton btnPlus = dialogView.findViewById(R.id.btnParticipantsPlus);
        TextView tvParticipantsCount = dialogView.findViewById(R.id.tvParticipantsCount);
        TextView tvAvailability = dialogView.findViewById(R.id.tvBookingAvailability);
        TextView tvPolicy = dialogView.findViewById(R.id.tvBookingPolicy);

        LinkedHashMap<String, List<ActivityDetail.BookingSlot>> slotsByDate = new LinkedHashMap<>();
        for (ActivityDetail.BookingSlot slot : detail.bookingSlots) {
            String dateLabel = slot.formattedDate();
            if (!slotsByDate.containsKey(dateLabel)) {
                slotsByDate.put(dateLabel, new ArrayList<>());
            }
            slotsByDate.get(dateLabel).add(slot);
        }

        List<String> dateLabels = new ArrayList<>(slotsByDate.keySet());
        ArrayAdapter<String> dateAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_item, dateLabels);
        dateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDate.setAdapter(dateAdapter);

        int[] participants = {1};
        ActivityDetail.BookingSlot[] selectedSlot = {detail.bookingSlots.get(0)};
        List<ActivityDetail.BookingSlot>[] visibleSlots = new List[]{detail.bookingSlots};

        Runnable renderState = () -> {
            ActivityDetail.BookingSlot slot = selectedSlot[0];
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

        Runnable syncTimeSpinner = () -> {
            List<ActivityDetail.BookingSlot> slotsForDate = slotsByDate.get(
                    dateLabels.get(Math.max(0, spinnerDate.getSelectedItemPosition())));
            if (slotsForDate == null || slotsForDate.isEmpty()) {
                slotsForDate = detail.bookingSlots;
            }
            visibleSlots[0] = slotsForDate;
            List<String> timeLabels = new ArrayList<>();
            for (ActivityDetail.BookingSlot slot : slotsForDate) {
                String time = slot.formattedTime();
                timeLabels.add(time.isEmpty() ? getString(R.string.detail_booking_single_time) : time);
            }
            ArrayAdapter<String> timeAdapter = new ArrayAdapter<>(
                    requireContext(), android.R.layout.simple_spinner_item, timeLabels);
            timeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerTime.setAdapter(timeAdapter);
            spinnerTime.setSelection(0);
            selectedSlot[0] = slotsForDate.get(0);
            renderState.run();
        };

        spinnerDate.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                syncTimeSpinner.run();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        spinnerTime.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                List<ActivityDetail.BookingSlot> currentSlots = visibleSlots[0];
                if (position >= 0 && position < currentSlots.size()) {
                    selectedSlot[0] = currentSlots.get(position);
                    renderState.run();
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        btnMinus.setOnClickListener(v -> {
            if (participants[0] > 1) {
                participants[0]--;
                renderState.run();
            }
        });
        btnPlus.setOnClickListener(v -> {
            ActivityDetail.BookingSlot slot = selectedSlot[0];
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

        syncTimeSpinner.run();

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setNegativeButton(R.string.detail_booking_cancel, null)
                .setPositiveButton(R.string.detail_booking_confirm, null)
                .create();
        dialog.setOnShowListener(dlg -> {
            android.widget.Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(v -> {
                ActivityDetail.BookingSlot slot = selectedSlot[0];
                long available = slot.availableSpots > 0 ? slot.availableSpots : detail.availableSpots;
                if (participants[0] > available || available <= 0) {
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
                                dialog.dismiss();
                                Toast.makeText(requireContext(), R.string.detail_booking_success, Toast.LENGTH_SHORT).show();
                                Navigation.findNavController(requireView()).navigate(R.id.activityHistoryFragment);
                            }

                            @Override
                            public void onError() {
                                if (!isAdded()) return;
                                setDialogEnabled(dialog, true);
                                Toast.makeText(requireContext(), R.string.detail_booking_error, Toast.LENGTH_LONG).show();
                            }
                        });
            });
        });
        dialog.show();
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
}
