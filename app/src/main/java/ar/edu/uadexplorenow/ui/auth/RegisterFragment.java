package ar.edu.uadexplorenow.ui.auth;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import ar.edu.uadexplorenow.R;
import ar.edu.uadexplorenow.data.auth.OtpVerificationHelper;
import ar.edu.uadexplorenow.data.email.OtpEmailSender;
import ar.edu.uadexplorenow.data.model.UserRtdbDto;
import ar.edu.uadexplorenow.data.network.RealtimeDatabaseApi;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class RegisterFragment extends Fragment {

    @Inject
    RealtimeDatabaseApi realtimeDatabaseApi;

    private static final int STEP_FORM = 0;
    private static final int STEP_OTP  = 1;

    private View stepRegisterForm;
    private View stepRegisterOtp;

    private EditText etName, etEmail, etPassword, etConfirmPassword;
    private Button btnRegister;
    private ProgressBar progressForm;
    private TextView tvGoToLogin;

    private final EditText[] otpFields = new EditText[6];
    private TextView tvOtpInfo;
    private TextView tvOtpTimer;
    private Button btnOtpVerify;
    private Button btnOtpResend;
    private TextView tvOtpBackToForm;

    private FirebaseAuth mAuth;
    private CountDownTimer countDownTimer;
    private int currentStep = STEP_FORM;

    private String pendingName = "";
    private String pendingEmail = "";
    private String pendingPassword = "";

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_register, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();

        requireActivity().getOnBackPressedDispatcher()
                .addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (currentStep == STEP_OTP) {
                            if (countDownTimer != null) countDownTimer.cancel();
                            showStep(STEP_FORM);
                        } else {
                            setEnabled(false);
                            requireActivity().getOnBackPressedDispatcher().onBackPressed();
                        }
                    }
                });

        stepRegisterForm = view.findViewById(R.id.stepRegisterForm);
        stepRegisterOtp = view.findViewById(R.id.stepRegisterOtp);

        etName = view.findViewById(R.id.etName);
        etEmail = view.findViewById(R.id.etEmail);
        etPassword = view.findViewById(R.id.etPassword);
        etConfirmPassword = view.findViewById(R.id.etConfirmPassword);
        btnRegister = view.findViewById(R.id.btnRegister);
        progressForm = view.findViewById(R.id.progressForm);
        tvGoToLogin = view.findViewById(R.id.tvGoToLogin);

        otpFields[0] = view.findViewById(R.id.etOtp1);
        otpFields[1] = view.findViewById(R.id.etOtp2);
        otpFields[2] = view.findViewById(R.id.etOtp3);
        otpFields[3] = view.findViewById(R.id.etOtp4);
        otpFields[4] = view.findViewById(R.id.etOtp5);
        otpFields[5] = view.findViewById(R.id.etOtp6);
        tvOtpInfo = view.findViewById(R.id.tvOtpInfo);
        tvOtpTimer = view.findViewById(R.id.tvOtpTimer);
        btnOtpVerify = view.findViewById(R.id.btnOtpVerify);
        btnOtpResend = view.findViewById(R.id.btnOtpResend);
        tvOtpBackToForm = view.findViewById(R.id.tvOtpBackToForm);

        setupOtpFields();
        btnRegister.setOnClickListener(v -> attemptStartRegistration());
        btnOtpVerify.setOnClickListener(v -> attemptVerifyOtpAndRegister());
        btnOtpResend.setOnClickListener(v -> {
            btnOtpResend.setEnabled(false);
            sendRegistrationOtp(pendingEmail);
            clearOtpFields();
            Toast.makeText(requireContext(), R.string.otp_resent, Toast.LENGTH_SHORT).show();
            startCountdown();
        });
        tvOtpBackToForm.setOnClickListener(v -> {
            if (countDownTimer != null) countDownTimer.cancel();
            showStep(STEP_FORM);
        });
        tvGoToLogin.setOnClickListener(v -> Navigation.findNavController(view).popBackStack());

        showStep(STEP_FORM);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (countDownTimer != null) countDownTimer.cancel();
    }

    private void attemptStartRegistration() {
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();
        String confirm = etConfirmPassword.getText().toString().trim();

        if (name.isEmpty()) {
            etName.setError(getString(R.string.error_name_required));
            etName.requestFocus();
            return;
        }
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError(getString(R.string.error_email_invalid));
            etEmail.requestFocus();
            return;
        }
        if (pass.length() < 6) {
            etPassword.setError(getString(R.string.error_password_min_length));
            etPassword.requestFocus();
            return;
        }
        if (!pass.equals(confirm)) {
            etConfirmPassword.setError(getString(R.string.error_passwords_no_match));
            etConfirmPassword.requestFocus();
            return;
        }

        pendingName = name;
        pendingEmail = email;
        pendingPassword = pass;

        setLoading(true);
        String emailKey = emailToKey(email);
        realtimeDatabaseApi.getUidByEmail(emailKey).enqueue(new Callback<String>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                if (!isAdded()) return;
                setLoading(false);
                String uid = response.body();
                if (uid != null && !uid.isEmpty()) {
                    etEmail.setError(getString(R.string.error_email_already_registered));
                    etEmail.requestFocus();
                    return;
                }
                sendRegistrationOtp(pendingEmail);
                tvOtpInfo.setText(getString(R.string.otp_sent_to, pendingEmail));
                showStep(STEP_OTP);
                startCountdown();
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                setLoading(false);
                Toast.makeText(requireContext(), R.string.otp_error_network, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendRegistrationOtp(@NonNull String email) {
        String code = OtpVerificationHelper.generateCode();
        OtpVerificationHelper.storeCode(requireContext(), OtpVerificationHelper.PREFS_REGISTER, code);
        OtpVerificationHelper.sendByEmail(email, code, new OtpEmailSender.SendCallback() {
            @Override
            public void onSuccess() {
                if (!isAdded()) return;
                Toast.makeText(requireContext(),
                        getString(R.string.otp_sent_to, email),
                        Toast.LENGTH_LONG).show();
            }

            @Override
            public void onNotConfigured() {
                if (!isAdded()) return;
                Snackbar.make(requireView(),
                                getString(R.string.otp_demo_code, code),
                                Snackbar.LENGTH_INDEFINITE)
                        .setAction("OK", v -> {})
                        .show();
            }

            @Override
            public void onFailure() {
                if (!isAdded()) return;
                Snackbar.make(requireView(),
                                getString(R.string.otp_demo_code, code),
                                Snackbar.LENGTH_INDEFINITE)
                        .setAction("OK", v -> {})
                        .show();
            }
        });
    }

    private void attemptVerifyOtpAndRegister() {
        String entered = getEnteredOtp();
        OtpVerificationHelper.VerifyResult result = OtpVerificationHelper.verify(
                requireContext(),
                OtpVerificationHelper.PREFS_REGISTER,
                entered);

        switch (result) {
            case NOT_FOUND:
                Toast.makeText(requireContext(), R.string.otp_error_not_found, Toast.LENGTH_SHORT).show();
                return;
            case EXPIRED:
                Toast.makeText(requireContext(), R.string.otp_error_expired, Toast.LENGTH_SHORT).show();
                btnOtpVerify.setEnabled(false);
                btnOtpResend.setEnabled(true);
                return;
            case INVALID:
                Toast.makeText(requireContext(), R.string.otp_error_invalid, Toast.LENGTH_SHORT).show();
                clearOtpFields();
                return;
            case OK:
                break;
        }

        OtpVerificationHelper.clear(requireContext(), OtpVerificationHelper.PREFS_REGISTER);
        if (countDownTimer != null) countDownTimer.cancel();
        btnOtpVerify.setEnabled(false);
        finalizeRegistration();
    }

    private void finalizeRegistration() {
        setLoading(true);
        mAuth.createUserWithEmailAndPassword(pendingEmail, pendingPassword)
                .addOnSuccessListener(authResult -> {
                    if (!isAdded()) return;
                    String uid = authResult.getUser().getUid();
                    saveUserToRtdb(uid, pendingName, pendingEmail);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    setLoading(false);
                    btnOtpVerify.setEnabled(true);
                    Toast.makeText(requireContext(),
                            e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void saveUserToRtdb(String uid, String name, String email) {
        UserRtdbDto dto = new UserRtdbDto();
        dto.id = uid;
        dto.email = email;
        dto.name = name;
        dto.preferences = new ArrayList<>();

        realtimeDatabaseApi
                .putUser(uid, dto)
                .enqueue(new Callback<UserRtdbDto>() {
                    @Override
                    public void onResponse(@NonNull Call<UserRtdbDto> call,
                                           @NonNull Response<UserRtdbDto> response) {
                        if (!isAdded()) return;
                        setLoading(false);

                        if (!response.isSuccessful()) {
                            Toast.makeText(requireContext(),
                                    getString(R.string.error_firestore),
                                    Toast.LENGTH_LONG).show();
                            return;
                        }

                        saveEmailIndex(uid, email);
                        navigateToHome(requireView(), name, email);
                    }

                    @Override
                    public void onFailure(@NonNull Call<UserRtdbDto> call,
                                          @NonNull Throwable t) {
                        if (!isAdded()) return;
                        setLoading(false);
                        Toast.makeText(requireContext(),
                                getString(R.string.error_firestore),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void saveEmailIndex(String uid, String email) {
        String key = emailToKey(email);
        realtimeDatabaseApi
                .putEmailIndex(key, uid)
                .enqueue(new Callback<String>() {
                    @Override public void onResponse(@NonNull Call<String> call,
                                                     @NonNull Response<String> response) {}
                    @Override public void onFailure(@NonNull Call<String> call,
                                                    @NonNull Throwable t) {}
                });
    }

    static String emailToKey(String email) {
        return email.toLowerCase()
                .replace("@", "_at_")
                .replace(".", "_dot_");
    }

    private void setupOtpFields() {
        for (int i = 0; i < otpFields.length; i++) {
            final int index = i;
            otpFields[i].addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
                @Override
                public void afterTextChanged(Editable s) {
                    if (s.length() == 1 && index < otpFields.length - 1) {
                        otpFields[index + 1].requestFocus();
                    }
                    btnOtpVerify.setEnabled(isOtpComplete());
                }
            });

            otpFields[i].setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_DEL
                        && event.getAction() == KeyEvent.ACTION_DOWN
                        && otpFields[index].getText().toString().isEmpty()
                        && index > 0) {
                    otpFields[index - 1].requestFocus();
                    otpFields[index - 1].setText("");
                    return true;
                }
                return false;
            });
        }
        btnOtpVerify.setEnabled(false);
    }

    private void startCountdown() {
        if (countDownTimer != null) countDownTimer.cancel();
        btnOtpResend.setEnabled(false);

        countDownTimer = new CountDownTimer(OtpVerificationHelper.OTP_EXPIRY_MS, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (!isAdded()) return;
                long min = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished);
                long sec = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60;
                tvOtpTimer.setText(String.format(java.util.Locale.getDefault(), "%02d:%02d", min, sec));
            }

            @Override
            public void onFinish() {
                if (!isAdded()) return;
                tvOtpTimer.setText(getString(R.string.otp_expired));
                btnOtpResend.setEnabled(true);
            }
        }.start();
    }

    @NonNull
    private String getEnteredOtp() {
        StringBuilder sb = new StringBuilder();
        for (EditText field : otpFields) {
            sb.append(field.getText().toString());
        }
        return sb.toString();
    }

    private boolean isOtpComplete() {
        for (EditText field : otpFields) {
            if (field.getText().toString().isEmpty()) return false;
        }
        return true;
    }

    private void clearOtpFields() {
        for (EditText field : otpFields) {
            field.setText("");
        }
        otpFields[0].requestFocus();
        btnOtpVerify.setEnabled(false);
    }

    private void showStep(int step) {
        currentStep = step;
        stepRegisterForm.setVisibility(step == STEP_FORM ? View.VISIBLE : View.GONE);
        stepRegisterOtp.setVisibility(step == STEP_OTP ? View.VISIBLE : View.GONE);
        tvGoToLogin.setVisibility(step == STEP_FORM ? View.VISIBLE : View.GONE);
    }

    private void setLoading(boolean loading) {
        if (progressForm != null) {
            progressForm.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        btnRegister.setEnabled(!loading);
    }

    private void navigateToHome(View view, String name, String email) {
        Bundle args = new Bundle();
        args.putString("email", email);
        args.putString("name", name);
        Navigation.findNavController(view)
                .navigate(R.id.action_auth_to_home, args);
    }
}
