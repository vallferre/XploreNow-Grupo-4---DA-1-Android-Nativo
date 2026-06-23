package ar.edu.uadexplorenow.ui.reservations;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import ar.edu.uadexplorenow.R;

import com.google.android.material.button.MaterialButton;

public class BookingConfirmationFragment extends Fragment {

    public static final String ARG_RESERVATION_ID = "reservation_id";
    public static final String ARG_ACTIVITY_NAME = "activity_name";
    public static final String ARG_SELECTED_DATE = "selected_date";
    public static final String ARG_SELECTED_TIME = "selected_time";
    public static final String ARG_PARTICIPANTS = "participants";
    public static final String ARG_TOTAL = "total";

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_booking_confirmation, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        if (args == null) {
            Navigation.findNavController(view).popBackStack();
            return;
        }

        String reservationId = safe(args.getString(ARG_RESERVATION_ID));
        String activityName = safe(args.getString(ARG_ACTIVITY_NAME));
        String selectedDate = safe(args.getString(ARG_SELECTED_DATE));
        String selectedTime = safe(args.getString(ARG_SELECTED_TIME));
        int participants = args.getInt(ARG_PARTICIPANTS, 1);
        String total = safe(args.getString(ARG_TOTAL));

        TextView tvConfirmActivityName = view.findViewById(R.id.tvConfirmActivityName);
        TextView tvConfirmDate = view.findViewById(R.id.tvConfirmDate);
        TextView tvConfirmParticipants = view.findViewById(R.id.tvConfirmParticipants);
        TextView tvConfirmTotal = view.findViewById(R.id.tvConfirmTotal);
        MaterialButton btnViewReservation = view.findViewById(R.id.btnViewReservation);
        MaterialButton btnContinueExplore = view.findViewById(R.id.btnContinueExplore);

        tvConfirmActivityName.setText(activityName);

        if (!selectedTime.isEmpty()) {
            tvConfirmDate.setText(getString(R.string.booking_confirmation_date_time_fmt, selectedDate, selectedTime));
        } else {
            tvConfirmDate.setText(selectedDate);
        }

        String personWord = participants == 1
                ? getString(R.string.detail_booking_person_singular)
                : getString(R.string.detail_booking_person_plural);
        tvConfirmParticipants.setText(getString(
                R.string.booking_confirmation_participants_fmt,
                participants,
                personWord));

        if (!total.isEmpty()) {
            tvConfirmTotal.setVisibility(View.VISIBLE);
            tvConfirmTotal.setText(total);
        } else {
            tvConfirmTotal.setVisibility(View.GONE);
        }

        btnViewReservation.setEnabled(!reservationId.isEmpty());
        btnViewReservation.setOnClickListener(v -> {
            if (reservationId.isEmpty()) return;
            Bundle detailArgs = new Bundle();
            detailArgs.putString("reservation_id", reservationId);
            Navigation.findNavController(v)
                    .navigate(R.id.action_bookingConfirmationFragment_to_reservationDetailFragment, detailArgs);
        });

        btnContinueExplore.setOnClickListener(v ->
                Navigation.findNavController(v)
                        .navigate(R.id.action_bookingConfirmationFragment_to_exploreFragment));
    }

    @NonNull
    private static String safe(@Nullable String value) {
        return value != null ? value.trim() : "";
    }
}
