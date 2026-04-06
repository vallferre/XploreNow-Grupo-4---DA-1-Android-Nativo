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
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import ar.edu.uadexplorenow.R;
import ar.edu.uadexplorenow.data.model.OtpRecord;
import ar.edu.uadexplorenow.data.model.UserRtdbDto;
import ar.edu.uadexplorenow.data.network.RealtimeRetrofitClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterFragment extends Fragment {

    // ─── Pasos ────────────────────────────────────────────────────────────────
    private static final int STEP_FORM = 0;
    private static final int STEP_OTP  = 1;
    private int currentStep = STEP_FORM;

    // ─── Step FORM ────────────────────────────────────────────────────────────
    private View     stepForm;
    private EditText etName, etEmail, etPassword, etConfirmPassword;
    private Button   btnSendOtp;
    private ProgressBar progressForm;

    // ─── Step OTP ─────────────────────────────────────────────────────────────
    private View             stepOtp;
    private final EditText[] otpFields = new EditText[6];
    private TextView         tvOtpEmail, tvTimer;
    private Button           btnVerify, btnResend;
    private ProgressBar      progressOtp;

    // ─── Estado ───────────────────────────────────────────────────────────────
    private FirebaseAuth   mAuth;
    private CountDownTimer countDownTimer;

    private String registeredName  = "";
    private String registeredEmail = "";
    private String registeredUid   = "";   // UID del usuario recién creado en Firebase Auth

    private static final long OTP_EXPIRY_MS = 5 * 60 * 1000L;

    // ─── Ciclo de vida ────────────────────────────────────────────────────────

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

        initViews(view);
        setupOtpFields();
        setListeners(view);
        showStep(STEP_FORM);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (countDownTimer != null) countDownTimer.cancel();
    }

    // ─── Inicialización ───────────────────────────────────────────────────────

    private void initViews(View view) {
        stepForm = view.findViewById(R.id.stepForm);
        stepOtp  = view.findViewById(R.id.stepOtp);

        etName            = view.findViewById(R.id.etName);
        etEmail           = view.findViewById(R.id.etEmail);
        etPassword        = view.findViewById(R.id.etPassword);
        etConfirmPassword = view.findViewById(R.id.etConfirmPassword);
        btnSendOtp        = view.findViewById(R.id.btnSendOtp);

        otpFields[0] = view.findViewById(R.id.etOtp1);
        otpFields[1] = view.findViewById(R.id.etOtp2);
        otpFields[2] = view.findViewById(R.id.etOtp3);
        otpFields[3] = view.findViewById(R.id.etOtp4);
        otpFields[4] = view.findViewById(R.id.etOtp5);
        otpFields[5] = view.findViewById(R.id.etOtp6);
        tvOtpEmail   = view.findViewById(R.id.tvOtpEmail);
        tvTimer      = view.findViewById(R.id.tvTimer);
        btnVerify    = view.findViewById(R.id.btnVerify);
        btnResend    = view.findViewById(R.id.btnResend);
    }

    // ─── OTP fields ───────────────────────────────────────────────────────────

    private void setupOtpFields() {
        for (int i = 0; i < otpFields.length; i++) {
            final int index = i;

            otpFields[i].addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
                @Override
                public void afterTextChanged(Editable s) {
                    if (s.length() == 1 && index < otpFields.length - 1)
                        otpFields[index + 1].requestFocus();
                    btnVerify.setEnabled(isOtpComplete());
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
        btnVerify.setEnabled(false);
    }

    private void setListeners(View view) {
        btnSendOtp.setOnClickListener(v -> attemptRegister());
        btnVerify.setOnClickListener(v -> attemptVerifyOtp(view));
        btnResend.setOnClickListener(v -> {
            btnResend.setEnabled(false);
            generateAndSaveOtp(registeredUid, true);
        });
        view.findViewById(R.id.tvGoToLogin).setOnClickListener(v ->
                Navigation.findNavController(view).popBackStack());
    }

    // ─── Paso 1: validar form ─────────────────────────────────────────────────

    private void attemptRegister() {
        String name    = etName.getText().toString().trim();
        String email   = etEmail.getText().toString().trim();
        String pass    = etPassword.getText().toString().trim();
        String confirm = etConfirmPassword.getText().toString().trim();

        if (name.isEmpty()) {
            etName.setError(getString(R.string.error_name_required));
            etName.requestFocus(); return;
        }
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError(getString(R.string.error_email_invalid));
            etEmail.requestFocus(); return;
        }
        if (pass.length() < 6) {
            etPassword.setError(getString(R.string.error_password_min_length));
            etPassword.requestFocus(); return;
        }
        if (!pass.equals(confirm)) {
            etConfirmPassword.setError(getString(R.string.error_passwords_no_match));
            etConfirmPassword.requestFocus(); return;
        }

        setFormLoading(true);

        mAuth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener(authResult -> {
                    if (!isAdded()) return;
                    String uid = authResult.getUser().getUid();
                    saveUserToRtdb(uid, name, email);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    setFormLoading(false);
                    Toast.makeText(requireContext(),
                            e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                });
    }

    // ─── Paso 2: PUT /users/{uid}.json ────────────────────────────────────────

    private void saveUserToRtdb(String uid, String name, String email) {
        UserRtdbDto dto = new UserRtdbDto();
        dto.id          = uid;
        dto.email       = email;
        dto.name        = name;
        dto.preferences = new ArrayList<>();

        RealtimeRetrofitClient.getApi()
                .putUser(uid, dto)
                .enqueue(new Callback<UserRtdbDto>() {

                    @Override
                    public void onResponse(@NonNull Call<UserRtdbDto> call,
                                           @NonNull Response<UserRtdbDto> response) {
                        if (!isAdded()) return;
                        setFormLoading(false);

                        if (!response.isSuccessful()) {
                            Toast.makeText(requireContext(),
                                    getString(R.string.error_firestore),
                                    Toast.LENGTH_LONG).show();
                            return;
                        }

                        registeredName  = name;
                        registeredEmail = email;
                        registeredUid   = uid;

                        // Guardar índice email → uid para el flujo de login con OTP
                        saveEmailIndex(uid, email);

                        tvOtpEmail.setText(getString(R.string.otp_sent_to, email));
                        generateAndSaveOtp(uid, false);
                    }

                    @Override
                    public void onFailure(@NonNull Call<UserRtdbDto> call,
                                          @NonNull Throwable t) {
                        if (!isAdded()) return;
                        setFormLoading(false);
                        Toast.makeText(requireContext(),
                                getString(R.string.error_firestore),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    // ─── Índice email → uid ───────────────────────────────────────────────────

    /** Guarda la relación email → uid en RTDB para poder encontrar el perfil
     *  de un usuario por su email durante el login con OTP.
     *  Falla silenciosamente: no es crítico para completar el registro. */
    private void saveEmailIndex(String uid, String email) {
        String key = emailToKey(email);
        RealtimeRetrofitClient.getApi()
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

    // ─── Paso 3: generar y guardar OTP en RTDB ────────────────────────────────

    /**
     * Genera un código OTP de 6 dígitos, lo almacena en Firebase RTDB bajo
     * otp_codes/{uid}.json y lo muestra en un Snackbar (modo demo).
     *
     * En producción reemplazar el Snackbar por el envío real del código por email
     * (ej: Firebase Cloud Functions + SendGrid / Mailgun).
     *
     * @param uid     UID del usuario autenticado en Firebase Auth
     * @param isResend true si es un reenvío (ya está en el paso OTP), false si es la primera vez
     */
    private void generateAndSaveOtp(String uid, boolean isResend) {
        String code = generateOtpCode();

        OtpRecord record = new OtpRecord();
        record.code      = code;
        record.expiresAt = System.currentTimeMillis() + OTP_EXPIRY_MS;
        record.used      = false;

        RealtimeRetrofitClient.getApi()
                .putOtpCode(uid, record)
                .enqueue(new Callback<OtpRecord>() {

                    @Override
                    public void onResponse(@NonNull Call<OtpRecord> call,
                                           @NonNull Response<OtpRecord> response) {
                        if (!isAdded()) return;

                        if (!response.isSuccessful()) {
                            Toast.makeText(requireContext(),
                                    getString(R.string.otp_error_network), Toast.LENGTH_LONG).show();
                            if (isResend) btnResend.setEnabled(true);
                            return;
                        }

                        // ── MODO DEMO ──────────────────────────────────────
                        // En producción, el código se envía al email del usuario.
                        // Para demo/testing se muestra aquí en pantalla.
                        Snackbar.make(requireView(),
                                        getString(R.string.otp_demo_code, code),
                                        Snackbar.LENGTH_INDEFINITE)
                                .setAction("OK", v -> {})
                                .show();
                        // ──────────────────────────────────────────────────

                        if (isResend) {
                            clearOtpFields();
                            Toast.makeText(requireContext(),
                                    getString(R.string.otp_resent), Toast.LENGTH_SHORT).show();
                        } else {
                            showStep(STEP_OTP);
                        }
                        startCountdown();
                    }

                    @Override
                    public void onFailure(@NonNull Call<OtpRecord> call,
                                          @NonNull Throwable t) {
                        if (!isAdded()) return;
                        Toast.makeText(requireContext(),
                                getString(R.string.otp_error_network), Toast.LENGTH_LONG).show();
                        if (isResend) btnResend.setEnabled(true);
                    }
                });
    }

    // ─── Paso 4: verificar OTP contra RTDB ───────────────────────────────────

    private void attemptVerifyOtp(View view) {
        String entered = getEnteredOtp();
        setOtpLoading(true);

        RealtimeRetrofitClient.getApi()
                .getOtpCode(registeredUid)
                .enqueue(new Callback<OtpRecord>() {

                    @Override
                    public void onResponse(@NonNull Call<OtpRecord> call,
                                           @NonNull Response<OtpRecord> response) {
                        if (!isAdded()) return;
                        setOtpLoading(false);

                        OtpRecord record = response.body();

                        if (!response.isSuccessful() || record == null) {
                            Toast.makeText(requireContext(),
                                    getString(R.string.otp_error_not_found), Toast.LENGTH_SHORT).show();
                            return;
                        }

                        if (System.currentTimeMillis() > record.expiresAt) {
                            Toast.makeText(requireContext(),
                                    getString(R.string.otp_error_expired), Toast.LENGTH_SHORT).show();
                            btnVerify.setEnabled(false);
                            btnResend.setEnabled(true);
                            return;
                        }

                        if (!entered.equals(record.code)) {
                            Toast.makeText(requireContext(),
                                    getString(R.string.otp_error_invalid), Toast.LENGTH_SHORT).show();
                            clearOtpFields();
                            return;
                        }

                        // Código correcto → eliminar registro y navegar al home
                        deleteOtpAndNavigate(view);
                    }

                    @Override
                    public void onFailure(@NonNull Call<OtpRecord> call,
                                          @NonNull Throwable t) {
                        if (!isAdded()) return;
                        setOtpLoading(false);
                        Toast.makeText(requireContext(),
                                getString(R.string.otp_error_network), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void deleteOtpAndNavigate(View view) {
        RealtimeRetrofitClient.getApi()
                .deleteOtpCode(registeredUid)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(@NonNull Call<Void> call,
                                           @NonNull Response<Void> response) {
                        if (!isAdded()) return;
                        navigateToHome(view);
                    }

                    @Override
                    public void onFailure(@NonNull Call<Void> call,
                                          @NonNull Throwable t) {
                        if (!isAdded()) return;
                        // El OTP ya fue validado; navegamos aunque falle el borrado
                        navigateToHome(view);
                    }
                });
    }

    // ─── Timer ────────────────────────────────────────────────────────────────

    private void startCountdown() {
        if (countDownTimer != null) countDownTimer.cancel();
        btnResend.setEnabled(false);

        countDownTimer = new CountDownTimer(OTP_EXPIRY_MS, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (!isAdded()) return;
                long min = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished);
                long sec = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60;
                tvTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", min, sec));
            }
            @Override
            public void onFinish() {
                if (!isAdded()) return;
                tvTimer.setText(getString(R.string.otp_expired));
                btnResend.setEnabled(true);
            }
        }.start();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String generateOtpCode() {
        return String.format(Locale.getDefault(), "%06d", new Random().nextInt(1_000_000));
    }

    private String getEnteredOtp() {
        StringBuilder sb = new StringBuilder();
        for (EditText f : otpFields) sb.append(f.getText().toString());
        return sb.toString();
    }

    private void setFormLoading(boolean loading) {
        if (progressForm != null) progressForm.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSendOtp.setEnabled(!loading);
    }

    private void setOtpLoading(boolean loading) {
        if (progressOtp != null) progressOtp.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnVerify.setEnabled(!loading);
    }

    private void showStep(int step) {
        currentStep = step;
        stepForm.setVisibility(step == STEP_FORM ? View.VISIBLE : View.GONE);
        stepOtp.setVisibility(step == STEP_OTP   ? View.VISIBLE : View.GONE);
    }

    private boolean isOtpComplete() {
        for (EditText f : otpFields)
            if (f.getText().toString().isEmpty()) return false;
        return true;
    }

    private void clearOtpFields() {
        for (EditText f : otpFields) f.setText("");
        otpFields[0].requestFocus();
        btnVerify.setEnabled(false);
    }

    private void navigateToHome(View view) {
        Bundle args = new Bundle();
        args.putString("email", registeredEmail);
        args.putString("name",  registeredName);
        Navigation.findNavController(view)
                .navigate(R.id.action_auth_to_home, args);
    }
}
