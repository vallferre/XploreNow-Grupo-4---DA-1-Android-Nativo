package ar.edu.uadexplorenow.ui.reservations;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import ar.edu.uadexplorenow.R;
import ar.edu.uadexplorenow.data.ReservationRepository;
import ar.edu.uadexplorenow.data.SessionStore;
import ar.edu.uadexplorenow.data.model.ActivityRtdbMapper;
import ar.edu.uadexplorenow.data.model.UserRtdbDto;
import ar.edu.uadexplorenow.data.network.RealtimeDatabaseApi;
import ar.edu.uadexplorenow.domain.ActivityDetail;
import ar.edu.uadexplorenow.domain.ReservationItem;
import ar.edu.uadexplorenow.domain.ReservationStatus;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.gson.Gson;
import com.google.gson.JsonElement;

import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class ReservationDetailFragment extends Fragment {

    public static final String ARG_RESERVATION_ID = "reservation_id";

    @Inject
    RealtimeDatabaseApi realtimeDatabaseApi;
    @Inject
    Gson gson;

    @Nullable
    private String effectiveUid;
    @Nullable
    private ReservationItem loadedReservation;
    @Nullable
    private MaterialButton btnCancelReservation;
    @Nullable
    private MaterialButton btnFinishReservation;
    @Nullable
    private ProgressBar progress;
    @Nullable
    private View contentRoot;
    @Nullable
    private ReservationRepository reservationRepository;

    private boolean userDone;
    private boolean activitiesDone;
    @Nullable
    private UserRtdbDto loadedUser;
    private Map<String, ActivityDetail> detailById = java.util.Collections.emptyMap();

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_reservation_detail, container, false);
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

        String reservationId = getArguments() != null ? getArguments().getString(ARG_RESERVATION_ID) : null;
        if (reservationId == null || reservationId.isEmpty()) {
            Navigation.findNavController(view).popBackStack();
            return;
        }

        ImageButton btnBack = view.findViewById(R.id.btnBack);
        contentRoot = view.findViewById(R.id.contentRoot);
        progress = view.findViewById(R.id.progress);
        btnCancelReservation = view.findViewById(R.id.btnCancelReservation);
        btnFinishReservation = view.findViewById(R.id.btnFinishReservation);

        btnBack.setOnClickListener(v -> Navigation.findNavController(view).popBackStack());
        btnCancelReservation.setOnClickListener(v -> promptCancelReservation());
        btnFinishReservation.setOnClickListener(v -> finishReservation());

        loadReservation(reservationId);
    }

    private void loadReservation(@NonNull String reservationId) {
        setLoading(true);
        userDone = false;
        activitiesDone = false;

        realtimeDatabaseApi.getUser(effectiveUid).enqueue(new Callback<UserRtdbDto>() {
            @Override
            public void onResponse(@NonNull Call<UserRtdbDto> call, @NonNull Response<UserRtdbDto> response) {
                if (!isAdded()) return;
                loadedUser = response.isSuccessful() ? response.body() : null;
                userDone = true;
                maybeBindReservation(reservationId);
            }

            @Override
            public void onFailure(@NonNull Call<UserRtdbDto> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                loadedUser = null;
                userDone = true;
                maybeBindReservation(reservationId);
            }
        });

        realtimeDatabaseApi.getActivities().enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(@NonNull Call<JsonElement> call, @NonNull Response<JsonElement> response) {
                if (!isAdded()) return;
                detailById = response.isSuccessful() && response.body() != null
                        ? ActivityRtdbMapper.toActivityDetails(response.body(), gson)
                        : java.util.Collections.emptyMap();
                activitiesDone = true;
                maybeBindReservation(reservationId);
            }

            @Override
            public void onFailure(@NonNull Call<JsonElement> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                detailById = java.util.Collections.emptyMap();
                activitiesDone = true;
                maybeBindReservation(reservationId);
            }
        });
    }

    private void maybeBindReservation(@NonNull String reservationId) {
        if (!userDone || !activitiesDone || !isAdded()) {
            return;
        }
        setLoading(false);

        List<ReservationItem> reservations = ReservationItem.buildList(loadedUser, detailById);
        for (ReservationItem item : reservations) {
            if (reservationId.equals(item.reservationId)) {
                loadedReservation = item;
                bindReservation(item);
                return;
            }
        }

        Toast.makeText(requireContext(), R.string.reservation_detail_missing, Toast.LENGTH_LONG).show();
        Navigation.findNavController(requireView()).popBackStack();
    }

    private void bindReservation(@NonNull ReservationItem reservation) {
        if (contentRoot == null) return;

        ImageView ivHero = requireView().findViewById(R.id.ivHero);
        TextView tvStatus = requireView().findViewById(R.id.tvStatus);
        TextView tvTitle = requireView().findViewById(R.id.tvTitle);
        TextView tvDestination = requireView().findViewById(R.id.tvDestination);
        TextView tvWhen = requireView().findViewById(R.id.tvWhen);
        TextView tvParticipants = requireView().findViewById(R.id.tvParticipants);
        TextView tvDuration = requireView().findViewById(R.id.tvDuration);
        TextView tvGuide = requireView().findViewById(R.id.tvGuide);
        TextView tvTotal = requireView().findViewById(R.id.tvTotal);
        TextView tvMeetingTitle = requireView().findViewById(R.id.tvMeetingTitle);
        View cardMeeting = requireView().findViewById(R.id.cardMeeting);
        TextView tvMeetingPoint = requireView().findViewById(R.id.tvMeetingPoint);
        TextView tvCancellationTitle = requireView().findViewById(R.id.tvCancellationTitle);
        View cardCancellation = requireView().findViewById(R.id.cardCancellation);
        TextView tvCancellationType = requireView().findViewById(R.id.tvCancellationType);
        TextView tvCancellationDesc = requireView().findViewById(R.id.tvCancellationDesc);
        TextView tvDescriptionTitle = requireView().findViewById(R.id.tvDescriptionTitle);
        TextView tvDescription = requireView().findViewById(R.id.tvDescription);

        if (!reservation.imageUrl.isEmpty()) {
            Glide.with(this)
                    .load(reservation.imageUrl)
                    .centerCrop()
                    .placeholder(R.color.explore_search_bg)
                    .into(ivHero);
        } else {
            ivHero.setImageResource(R.color.explore_search_bg);
        }

        tvStatus.setText(reservation.statusLabel());
        bindStatusChip(tvStatus, reservation.status);
        tvTitle.setText(reservation.activityName);
        tvDestination.setText(reservation.destinationLabel());
        tvWhen.setText(reservation.formattedDateLong());
        tvParticipants.setText(reservation.participantsLabel());
        tvDuration.setText(reservation.formattedDuration());
        tvGuide.setText(reservation.guideName.isEmpty() ? "—" : reservation.guideName);
        tvTotal.setText(reservation.formattedTotalPrice());

        if (reservation.meetingPoint.isEmpty()) {
            tvMeetingTitle.setVisibility(View.GONE);
            cardMeeting.setVisibility(View.GONE);
        } else {
            tvMeetingTitle.setVisibility(View.VISIBLE);
            cardMeeting.setVisibility(View.VISIBLE);
            tvMeetingPoint.setText("Punto de encuentro: " + reservation.meetingPoint);
        }

        String cancellationType = reservation.cancellationTypeLabel();
        String cancellationSummary = reservation.cancellationSummary();
        if (cancellationType.isEmpty() && cancellationSummary.isEmpty()) {
            tvCancellationTitle.setVisibility(View.GONE);
            cardCancellation.setVisibility(View.GONE);
        } else {
            tvCancellationTitle.setVisibility(View.VISIBLE);
            cardCancellation.setVisibility(View.VISIBLE);
            tvCancellationType.setVisibility(cancellationType.isEmpty() ? View.GONE : View.VISIBLE);
            if (!cancellationType.isEmpty()) {
                tvCancellationType.setText(getString(R.string.detail_cancel_type_fmt, cancellationType));
            }
            tvCancellationDesc.setText(cancellationSummary);
        }

        if (reservation.description.isEmpty()) {
            tvDescriptionTitle.setVisibility(View.GONE);
            tvDescription.setVisibility(View.GONE);
        } else {
            tvDescriptionTitle.setVisibility(View.VISIBLE);
            tvDescription.setVisibility(View.VISIBLE);
            tvDescription.setText(reservation.description);
        }

        updateActionButtons(reservation);
        contentRoot.setVisibility(View.VISIBLE);
    }

    private void bindStatusChip(@NonNull TextView tvStatus, @NonNull String status) {
        int bgColor;
        int textColor;
        switch (ReservationStatus.normalize(status)) {
            case ReservationStatus.CANCELLED:
                bgColor = ContextCompat.getColor(requireContext(), R.color.detail_cancel_card_bg);
                textColor = ContextCompat.getColor(requireContext(), R.color.detail_cupos_low);
                break;
            case ReservationStatus.FINISHED:
                bgColor = ContextCompat.getColor(requireContext(), R.color.detail_meeting_bg);
                textColor = ContextCompat.getColor(requireContext(), R.color.explore_price_green);
                break;
            case ReservationStatus.CONFIRMED:
            default:
                bgColor = ContextCompat.getColor(requireContext(), R.color.filter_primary_light);
                textColor = ContextCompat.getColor(requireContext(), R.color.filter_primary);
                break;
        }
        tvStatus.setBackgroundTintList(ColorStateList.valueOf(bgColor));
        tvStatus.setTextColor(textColor);
    }

    private void updateActionButtons(@NonNull ReservationItem reservation) {
        if (btnCancelReservation == null || btnFinishReservation == null) return;

        boolean canCancel = reservation.canCancel();
        boolean canFinish = reservation.canFinish();
        btnCancelReservation.setEnabled(canCancel);
        btnFinishReservation.setEnabled(canFinish);

        if (!canCancel) {
            btnCancelReservation.setText(
                    ReservationStatus.CANCELLED.equals(ReservationStatus.normalize(reservation.status))
                            ? R.string.reservation_detail_cancelled
                            : R.string.reservation_detail_cancel);
        } else {
            btnCancelReservation.setText(R.string.reservation_detail_cancel);
        }

        if (!canFinish) {
            btnFinishReservation.setText(
                    ReservationStatus.FINISHED.equals(ReservationStatus.normalize(reservation.status))
                            ? R.string.reservation_detail_finished
                            : R.string.reservation_detail_finish);
        } else {
            btnFinishReservation.setText(R.string.reservation_detail_finish);
        }
    }

    private void promptCancelReservation() {
        if (loadedReservation == null || reservationRepository == null) return;
        if (!loadedReservation.canCancel()) return;

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.reservation_detail_cancel)
                .setMessage(getString(
                        R.string.reservation_detail_cancel_message,
                        loadedReservation.cancellationSummary()))
                .setNegativeButton(R.string.detail_booking_cancel, null)
                .setPositiveButton(R.string.reservation_detail_cancel_confirm, null)
                .create();
        dialog.setOnShowListener(dlg -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            setActionButtonsEnabled(false);
            reservationRepository.cancelReservation(effectiveUid, loadedReservation, new ReservationRepository.ActionCallback() {
                @Override
                public void onSuccess() {
                    if (!isAdded()) return;
                    dialog.dismiss();
                    Toast.makeText(requireContext(), R.string.reservation_detail_cancel_success, Toast.LENGTH_SHORT).show();
                    Navigation.findNavController(requireView()).popBackStack();
                }

                @Override
                public void onError() {
                    if (!isAdded()) return;
                    dialog.dismiss();
                    setActionButtonsEnabled(true);
                    Toast.makeText(requireContext(), R.string.reservation_detail_action_error, Toast.LENGTH_LONG).show();
                }
            });
        }));
        dialog.show();
    }

    private void finishReservation() {
        if (loadedReservation == null || reservationRepository == null) return;
        if (!loadedReservation.canFinish()) return;

        setActionButtonsEnabled(false);
        reservationRepository.finishReservation(effectiveUid, loadedReservation, new ReservationRepository.ActionCallback() {
            @Override
            public void onSuccess() {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), R.string.reservation_detail_finish_success, Toast.LENGTH_SHORT).show();
                Navigation.findNavController(requireView()).popBackStack();
            }

            @Override
            public void onError() {
                if (!isAdded()) return;
                setActionButtonsEnabled(true);
                Toast.makeText(requireContext(), R.string.reservation_detail_action_error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setActionButtonsEnabled(boolean enabled) {
        if (btnCancelReservation != null) {
            btnCancelReservation.setEnabled(enabled && loadedReservation != null && loadedReservation.canCancel());
        }
        if (btnFinishReservation != null) {
            btnFinishReservation.setEnabled(enabled && loadedReservation != null && loadedReservation.canFinish());
        }
    }

    private void setLoading(boolean loading) {
        if (progress != null) {
            progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (contentRoot != null) {
            contentRoot.setVisibility(loading ? View.GONE : contentRoot.getVisibility());
        }
        if (btnCancelReservation != null) {
            btnCancelReservation.setEnabled(!loading);
        }
        if (btnFinishReservation != null) {
            btnFinishReservation.setEnabled(!loading);
        }
    }
}
