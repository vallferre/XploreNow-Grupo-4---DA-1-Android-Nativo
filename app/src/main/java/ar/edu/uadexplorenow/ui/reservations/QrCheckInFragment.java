package ar.edu.uadexplorenow.ui.reservations;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import ar.edu.uadexplorenow.R;
import ar.edu.uadexplorenow.data.ReservationRepository;
import ar.edu.uadexplorenow.data.SessionStore;
import ar.edu.uadexplorenow.data.local.db.CachedReservationDao;
import ar.edu.uadexplorenow.data.local.db.CachedReservationEntity;
import ar.edu.uadexplorenow.data.network.RealtimeDatabaseApi;
import ar.edu.uadexplorenow.domain.ReservationItem;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.zxing.ResultPoint;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class QrCheckInFragment extends Fragment {
    private static final long AUTO_CLOSE_DELAY_MS = 1600L;

    public static final String ARG_RESERVATION_ID = "reservation_id";
    public static final String RESULT_REQUEST_KEY = "qr_check_in_result";
    public static final String RESULT_RESERVATION_ID = "reservation_id";
    public static final String RESULT_SUCCESS = "success";
    public static final String RESULT_MESSAGE = "message";

    @Inject RealtimeDatabaseApi realtimeDatabaseApi;
    @Inject CachedReservationDao cachedReservationDao;

    @Nullable private DecoratedBarcodeView barcodeView;
    @Nullable private MaterialCardView cardResult;
    @Nullable private TextView tvResultTitle;
    @Nullable private TextView tvResultMessage;
    @Nullable private MaterialButton btnRetry;
    @Nullable private MaterialButton btnClose;
    @Nullable private String effectiveUid;
    @Nullable private ReservationItem reservation;
    @Nullable private ReservationRepository reservationRepository;
    @Nullable private String reservationId;
    private boolean scanLocked;
    private final Handler handler = new Handler(Looper.getMainLooper());
    @Nullable private Runnable pendingAutoClose;

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!isAdded()) return;
                if (granted) {
                    startScanning();
                } else {
                    showErrorState(getString(R.string.reservation_qr_permission_denied));
                }
            });

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_qr_check_in, container, false);
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
        reservationId = getArguments() != null ? getArguments().getString(ARG_RESERVATION_ID) : null;
        if (reservationId == null || reservationId.isEmpty()) {
            Navigation.findNavController(view).popBackStack();
            return;
        }

        barcodeView = view.findViewById(R.id.barcodeView);
        cardResult = view.findViewById(R.id.cardResult);
        tvResultTitle = view.findViewById(R.id.tvResultTitle);
        tvResultMessage = view.findViewById(R.id.tvResultMessage);
        btnRetry = view.findViewById(R.id.btnRetry);
        btnClose = view.findViewById(R.id.btnClose);
        ImageButton btnBack = view.findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> Navigation.findNavController(view).popBackStack());
        btnClose.setOnClickListener(v -> Navigation.findNavController(view).popBackStack());
        btnRetry.setOnClickListener(v -> {
            hideResult();
            startScanning();
        });

        loadReservation();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!scanLocked && barcodeView != null) {
            barcodeView.resume();
        }
    }

    @Override
    public void onPause() {
        if (barcodeView != null) {
            barcodeView.pause();
        }
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        clearPendingAutoClose();
        barcodeView = null;
        cardResult = null;
        tvResultTitle = null;
        tvResultMessage = null;
        btnRetry = null;
        btnClose = null;
        super.onDestroyView();
    }

    private void loadReservation() {
        new Thread(() -> {
            ReservationItem loaded = null;
            if (reservationId != null) {
                CachedReservationEntity cached = cachedReservationDao.getByReservationId(reservationId);
                if (cached != null) {
                    loaded = cached.toDomain();
                }
            }
            ReservationItem finalLoaded = loaded;
            android.app.Activity activity = getActivity();
            if (activity == null) return;
            activity.runOnUiThread(() -> {
                if (!isAdded()) return;
                reservation = finalLoaded;
                if (reservation == null) {
                    Toast.makeText(requireContext(), R.string.reservation_detail_missing, Toast.LENGTH_LONG).show();
                    Navigation.findNavController(requireView()).popBackStack();
                    return;
                }
                requestCameraAndScan();
            });
        }).start();
    }

    private void requestCameraAndScan() {
        if (!requireContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            showErrorState(getString(R.string.reservation_qr_no_camera));
            return;
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startScanning();
        } else {
            showErrorState(getString(R.string.reservation_qr_permission_needed));
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startScanning() {
        if (barcodeView == null || reservation == null) return;
        scanLocked = false;
        hideResult();
        barcodeView.resume();
        barcodeView.decodeContinuous(new BarcodeCallback() {
            @Override
            public void barcodeResult(BarcodeResult result) {
                if (!isAdded() || scanLocked || result == null || result.getText() == null) {
                    return;
                }
                scanLocked = true;
                barcodeView.pause();
                confirmAttendance(result.getText());
            }

            @Override
            public void possibleResultPoints(List<ResultPoint> resultPoints) {
            }
        });
    }

    private void confirmAttendance(@NonNull String qrText) {
        if (reservationRepository == null || reservation == null || effectiveUid == null) return;
        reservationRepository.confirmAttendanceByQr(effectiveUid, reservation, qrText, new ReservationRepository.ActionCallback() {
            @Override
            public void onSuccess() {
                if (!isAdded()) return;
                Bundle result = new Bundle();
                result.putString(RESULT_RESERVATION_ID, reservation.reservationId);
                result.putBoolean(RESULT_SUCCESS, true);
                result.putString(RESULT_MESSAGE, getString(R.string.reservation_qr_result_success_desc));
                getParentFragmentManager().setFragmentResult(RESULT_REQUEST_KEY, result);
                showSuccessState(getString(R.string.reservation_qr_result_success_desc));
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isAdded()) return;
                showErrorState(message);
            }
        });
    }

    private void hideResult() {
        if (cardResult != null) {
            cardResult.setVisibility(View.GONE);
        }
    }

    private void showSuccessState(@NonNull String message) {
        if (cardResult == null || tvResultTitle == null || tvResultMessage == null || btnRetry == null || btnClose == null) return;
        cardResult.setVisibility(View.VISIBLE);
        cardResult.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.detail_checkin_success_bg));
        tvResultTitle.setText(R.string.reservation_qr_result_success_title);
        tvResultTitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.detail_checkin_success_text));
        tvResultMessage.setText(message);
        btnRetry.setVisibility(View.GONE);
        btnClose.setVisibility(View.GONE);
        scheduleAutoClose();
    }

    private void showErrorState(@NonNull String message) {
        if (cardResult == null || tvResultTitle == null || tvResultMessage == null || btnRetry == null || btnClose == null) return;
        cardResult.setVisibility(View.VISIBLE);
        cardResult.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.detail_checkin_error_bg));
        tvResultTitle.setText(R.string.reservation_qr_result_error_title);
        tvResultTitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.detail_checkin_error_text));
        tvResultMessage.setText(message);
        btnRetry.setVisibility(View.GONE);
        btnClose.setVisibility(View.GONE);
        scanLocked = true;
        scheduleAutoClose();
    }

    private void scheduleAutoClose() {
        clearPendingAutoClose();
        pendingAutoClose = () -> {
            if (!isAdded()) {
                return;
            }
            Navigation.findNavController(requireView()).popBackStack();
        };
        handler.postDelayed(pendingAutoClose, AUTO_CLOSE_DELAY_MS);
    }

    private void clearPendingAutoClose() {
        if (pendingAutoClose != null) {
            handler.removeCallbacks(pendingAutoClose);
            pendingAutoClose = null;
        }
    }
}
