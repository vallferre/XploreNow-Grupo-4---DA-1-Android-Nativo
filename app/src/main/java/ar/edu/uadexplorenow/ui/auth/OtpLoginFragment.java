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
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import javax.inject.Inject;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import ar.edu.uadexplorenow.R;
import ar.edu.uadexplorenow.data.SessionStore;
import ar.edu.uadexplorenow.data.auth.OtpVerificationHelper;
import ar.edu.uadexplorenow.data.email.OtpEmailSender;
import ar.edu.uadexplorenow.data.model.UserRtdbDto;
import ar.edu.uadexplorenow.data.network.RealtimeDatabaseApi;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Flujo de login con OTP (sin contraseña).
 *
 * Paso 1 – STEP_EMAIL: el usuario ingresa su email y solicita un código.
 * Paso 2 – STEP_OTP:   ingresa el código de 6 dígitos para verificar la identidad.
 *
 * Al verificar el OTP se usa signInAnonymously() para obtener un token Firebase
 * válido (auth != null). Luego se busca el UID real del usuario mediante el índice
 * user_emails/{emailKey} guardado durante el registro, y se carga el perfil real.
 *
 * ⚠️  REQUISITO: Habilitar "Autenticación anónima" en Firebase Console →
 *     Authentication → Sign-in methods → Anonymous → Enable.
 *
 * En producción: reemplazar signInAnonymously() por signInWithCustomToken()
 * donde el token es emitido por una Cloud Function tras verificar el OTP.
 */
@AndroidEntryPoint
public class OtpLoginFragment extends Fragment {

    @Inject
    RealtimeDatabaseApi realtimeDatabaseApi;

    // ─── Pasos ────────────────────────────────────────────────────────────────
    private static final int STEP_EMAIL = 0;
    private static final int STEP_OTP   = 1;
    private int currentStep = STEP_EMAIL;

    // ─── Step EMAIL ───────────────────────────────────────────────────────────
    private View     stepEmail;
    private EditText etOtpLoginEmail;
    private Button   btnOtpLoginSend;

    // ─── Step OTP ─────────────────────────────────────────────────────────────
    private View             stepOtpLogin;
    private final EditText[] otpFields = new EditText[6];
    private TextView         tvOtpLoginInfo, tvOtpLoginTimer;
    private Button           btnOtpLoginVerify, btnOtpLoginResend;

    // ─── Estado ───────────────────────────────────────────────────────────────
    private CountDownTimer countDownTimer;
    private String         otpEmail = "";

    private static final String PREFS_NAME = OtpVerificationHelper.PREFS_LOGIN;

    // ─── Ciclo de vida ────────────────────────────────────────────────────────

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_otp_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        requireActivity().getOnBackPressedDispatcher()
                .addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (currentStep == STEP_OTP) {
                            if (countDownTimer != null) countDownTimer.cancel();
                            showStep(STEP_EMAIL);
                        } else {
                            setEnabled(false);
                            requireActivity().getOnBackPressedDispatcher().onBackPressed();
                        }
                    }
                });

        initViews(view);
        setupOtpFields();
        setListeners(view);
        showStep(STEP_EMAIL);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (countDownTimer != null) countDownTimer.cancel();
    }

    // ─── Inicialización ───────────────────────────────────────────────────────

    private void initViews(View view) {
        stepEmail    = view.findViewById(R.id.stepEmail);
        stepOtpLogin = view.findViewById(R.id.stepOtpLogin);

        etOtpLoginEmail  = view.findViewById(R.id.etOtpLoginEmail);
        btnOtpLoginSend  = view.findViewById(R.id.btnOtpLoginSend);

        otpFields[0] = view.findViewById(R.id.etOtpL1);
        otpFields[1] = view.findViewById(R.id.etOtpL2);
        otpFields[2] = view.findViewById(R.id.etOtpL3);
        otpFields[3] = view.findViewById(R.id.etOtpL4);
        otpFields[4] = view.findViewById(R.id.etOtpL5);
        otpFields[5] = view.findViewById(R.id.etOtpL6);

        tvOtpLoginInfo    = view.findViewById(R.id.tvOtpLoginInfo);
        tvOtpLoginTimer   = view.findViewById(R.id.tvOtpLoginTimer);
        btnOtpLoginVerify = view.findViewById(R.id.btnOtpLoginVerify);
        btnOtpLoginResend = view.findViewById(R.id.btnOtpLoginResend);
    }

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
                    btnOtpLoginVerify.setEnabled(isOtpComplete());
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
        btnOtpLoginVerify.setEnabled(false);
    }

    private void setListeners(View view) {
        btnOtpLoginSend.setOnClickListener(v -> attemptSendOtp());

        btnOtpLoginVerify.setOnClickListener(v -> attemptVerifyOtp(view));

        btnOtpLoginResend.setOnClickListener(v -> {
            btnOtpLoginResend.setEnabled(false);
            generateAndStoreOtp();
            clearOtpFields();
            Toast.makeText(requireContext(), getString(R.string.otp_resent), Toast.LENGTH_SHORT).show();
            startCountdown();
        });

        view.findViewById(R.id.tvOtpLoginBack).setOnClickListener(v ->
                Navigation.findNavController(view).popBackStack());

        view.findViewById(R.id.tvOtpLoginGoRegister).setOnClickListener(v ->
                Navigation.findNavController(view)
                        .navigate(R.id.action_otp_login_to_register));
    }

    // ─── Paso 1: validar email y enviar OTP ──────────────────────────────────

    private void attemptSendOtp() {
        String email = etOtpLoginEmail.getText().toString().trim();

        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etOtpLoginEmail.setError(getString(R.string.error_email_invalid));
            etOtpLoginEmail.requestFocus();
            return;
        }

        otpEmail = email;
        generateAndStoreOtp();
        tvOtpLoginInfo.setText(getString(R.string.otp_sent_to, email));
        showStep(STEP_OTP);
        startCountdown();
    }

    // ─── Generar OTP y persistir en SharedPreferences ─────────────────────────

    /**
     * Genera un código OTP de 6 dígitos y lo guarda localmente con su expiración.
     * El usuario aún no tiene sesión Firebase en este paso, por eso se usa
     * SharedPreferences en lugar de RTDB.
     *
     * Muestra el código en Snackbar (modo demo).
     * En producción: llamar a una Cloud Function que envíe el código por email.
     */
    private void generateAndStoreOtp() {
        String code = OtpVerificationHelper.generateCode();
        OtpVerificationHelper.storeCode(requireContext(), PREFS_NAME, code);

        OtpVerificationHelper.sendByEmail(otpEmail, code, new OtpEmailSender.SendCallback() {
            @Override public void onSuccess() {
                if (!isAdded()) return;
                Toast.makeText(requireContext(),
                        getString(R.string.otp_sent_to, otpEmail),
                        Toast.LENGTH_LONG).show();
            }
            @Override public void onNotConfigured() {
                if (!isAdded()) return;
                Snackbar.make(requireView(),
                                getString(R.string.otp_demo_code, code),
                                Snackbar.LENGTH_INDEFINITE)
                        .setAction("OK", v -> {}).show();
            }
            @Override public void onFailure() {
                if (!isAdded()) return;
                Snackbar.make(requireView(),
                                getString(R.string.otp_demo_code, code),
                                Snackbar.LENGTH_INDEFINITE)
                        .setAction("OK", v -> {}).show();
            }
        });
        // ──────────────────────────────────────────────────────────────────────
    }

    // ─── Paso 2: verificar OTP ────────────────────────────────────────────────

    private void attemptVerifyOtp(View view) {
        String entered = getEnteredOtp();
        OtpVerificationHelper.VerifyResult result = OtpVerificationHelper.verify(
                requireContext(),
                PREFS_NAME,
                entered);

        switch (result) {
            case NOT_FOUND:
                Toast.makeText(requireContext(),
                        getString(R.string.otp_error_not_found), Toast.LENGTH_SHORT).show();
                return;
            case EXPIRED:
                Toast.makeText(requireContext(),
                        getString(R.string.otp_error_expired), Toast.LENGTH_SHORT).show();
                btnOtpLoginVerify.setEnabled(false);
                btnOtpLoginResend.setEnabled(true);
                return;
            case INVALID:
                Toast.makeText(requireContext(),
                        getString(R.string.otp_error_invalid), Toast.LENGTH_SHORT).show();
                clearOtpFields();
                return;
            case OK:
                break;
        }

        OtpVerificationHelper.clear(requireContext(), PREFS_NAME);
        if (countDownTimer != null) countDownTimer.cancel();
        btnOtpLoginVerify.setEnabled(false);

        createFirebaseSession(view);
    }

    // ─── Crear sesión Firebase + buscar perfil real ───────────────────────────

    /**
     * signInAnonymously() obtiene un token Firebase válido que satisface
     * "auth != null" en las reglas de RTDB.
     *
     * Con ese token buscamos el UID real del usuario usando el índice
     * user_emails/{emailKey} guardado durante el registro, y cargamos su perfil.
     *
     * ⚠️  Requiere que "Anonymous" esté habilitado en Firebase Console →
     *     Authentication → Sign-in methods → Anonymous.
     */
    private void createFirebaseSession(View view) {
        FirebaseAuth.getInstance().signInAnonymously()
                .addOnSuccessListener(authResult -> {
                    if (!isAdded()) return;
                    // Con token anónimo ya podemos leer la RTDB (auth != null).
                    // Buscamos el UID real del usuario por su email.
                    lookupUidAndNavigate(view);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    // Más probable: Anonymous auth no está habilitado en Firebase Console.
                    Toast.makeText(requireContext(),
                            "No se pudo crear la sesión. Habilitá la autenticación " +
                            "anónima en Firebase Console (Authentication → Sign-in methods → Anonymous).",
                            Toast.LENGTH_LONG).show();
                    btnOtpLoginVerify.setEnabled(true);
                });
    }

    /** Busca el UID real del usuario mediante el índice email → uid guardado al
     *  registrarse, y luego carga su perfil para pasarlo al home. */
    private void lookupUidAndNavigate(View view) {
        String emailKey = RegisterFragment.emailToKey(otpEmail);

        realtimeDatabaseApi
                .getUidByEmail(emailKey)
                .enqueue(new Callback<String>() {
                    @Override
                    public void onResponse(@NonNull Call<String> call,
                                           @NonNull Response<String> response) {
                        if (!isAdded()) return;
                        String uid = response.body();
                        if (uid != null && !uid.isEmpty()) {
                            // Guardamos el UID real para que ProfileFragment
                            // pueda operar sobre el perfil correcto aunque la
                            // sesión Firebase sea anónima.
                            SessionStore.saveOtpSession(requireContext(), uid, otpEmail);
                            fetchUserAndNavigate(view, uid);
                        } else {
                            handleProfileLookupFailure(R.string.otp_login_profile_not_found);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<String> call,
                                          @NonNull Throwable t) {
                        if (!isAdded()) return;
                        handleProfileLookupFailure(R.string.otp_login_profile_lookup_error);
                    }
                });
    }

    private void handleProfileLookupFailure(int messageResId) {
        if (!isAdded()) return;
        SessionStore.clear(requireContext());
        FirebaseAuth.getInstance().signOut();
        btnOtpLoginVerify.setEnabled(true);
        Toast.makeText(requireContext(), messageResId, Toast.LENGTH_LONG).show();
    }

    private void fetchUserAndNavigate(View view, String uid) {
        realtimeDatabaseApi
                .getUser(uid)
                .enqueue(new Callback<UserRtdbDto>() {
                    @Override
                    public void onResponse(@NonNull Call<UserRtdbDto> call,
                                           @NonNull Response<UserRtdbDto> response) {
                        if (!isAdded()) return;
                        UserRtdbDto user = response.body();
                        navigateToHome(view,
                                user != null && user.email != null ? user.email : otpEmail,
                                user != null && user.name  != null ? user.name  : "");
                    }

                    @Override
                    public void onFailure(@NonNull Call<UserRtdbDto> call,
                                          @NonNull Throwable t) {
                        if (!isAdded()) return;
                        navigateToHome(view, otpEmail, "");
                    }
                });
    }

    // ─── Timer ────────────────────────────────────────────────────────────────

    private void startCountdown() {
        if (countDownTimer != null) countDownTimer.cancel();
        btnOtpLoginResend.setEnabled(false);

        countDownTimer = new CountDownTimer(OtpVerificationHelper.OTP_EXPIRY_MS, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (!isAdded()) return;
                long min = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished);
                long sec = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60;
                tvOtpLoginTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", min, sec));
            }
            @Override
            public void onFinish() {
                if (!isAdded()) return;
                tvOtpLoginTimer.setText(getString(R.string.otp_expired));
                btnOtpLoginResend.setEnabled(true);
            }
        }.start();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String getEnteredOtp() {
        StringBuilder sb = new StringBuilder();
        for (EditText f : otpFields) sb.append(f.getText().toString());
        return sb.toString();
    }

    private boolean isOtpComplete() {
        for (EditText f : otpFields)
            if (f.getText().toString().isEmpty()) return false;
        return true;
    }

    private void clearOtpFields() {
        for (EditText f : otpFields) f.setText("");
        otpFields[0].requestFocus();
        btnOtpLoginVerify.setEnabled(false);
    }

    private void showStep(int step) {
        currentStep = step;
        stepEmail.setVisibility(step == STEP_EMAIL ? View.VISIBLE : View.GONE);
        stepOtpLogin.setVisibility(step == STEP_OTP ? View.VISIBLE : View.GONE);
    }

    private void navigateToHome(View view, String email, String name) {
        Bundle args = new Bundle();
        args.putString("email", email);
        args.putString("name",  name);
        Navigation.findNavController(view)
                .navigate(R.id.action_auth_to_home, args);
    }
}
