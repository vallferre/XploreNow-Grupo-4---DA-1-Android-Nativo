package ar.edu.uadexplorenow;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RegisterActivity extends AppCompatActivity {

    // ─── Pasos ────────────────────────────────────────────────────────────────
    private static final int STEP_FORM = 0;
    private static final int STEP_OTP  = 1;
    private int currentStep = STEP_FORM;

    // ─── Step FORM ────────────────────────────────────────────────────────────
    private View     stepForm;
    private EditText etName, etEmail, etPassword, etConfirmPassword;
    private Button   btnSendOtp;

    // ─── Step OTP ─────────────────────────────────────────────────────────────
    private View             stepOtp;
    private final EditText[] otpFields = new EditText[6];
    private TextView         tvOtpEmail, tvTimer;
    private Button           btnVerify, btnResend;

    // ─── Compartido ───────────────────────────────────────────────────────────
    private TextView       tvGoToLogin;
    private CountDownTimer countDownTimer;

    private FirebaseAuth      mAuth;
    private FirebaseFirestore db;

    private static final long OTP_EXPIRY_MS = 5 * 60 * 1000L;

    // ─── Ciclo de vida ────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseFirestore.getInstance();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (currentStep == STEP_OTP) {
                    if (countDownTimer != null) countDownTimer.cancel();
                    showStep(STEP_FORM);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        initViews();
        setupOtpFields();
        setListeners();
        showStep(STEP_FORM);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) countDownTimer.cancel();
    }

    // ─── Inicialización ───────────────────────────────────────────────────────

    private void initViews() {
        stepForm = findViewById(R.id.stepForm);
        stepOtp  = findViewById(R.id.stepOtp);

        etName            = findViewById(R.id.etName);
        etEmail           = findViewById(R.id.etEmail);
        etPassword        = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnSendOtp        = findViewById(R.id.btnSendOtp);

        otpFields[0] = findViewById(R.id.etOtp1);
        otpFields[1] = findViewById(R.id.etOtp2);
        otpFields[2] = findViewById(R.id.etOtp3);
        otpFields[3] = findViewById(R.id.etOtp4);
        otpFields[4] = findViewById(R.id.etOtp5);
        otpFields[5] = findViewById(R.id.etOtp6);
        tvOtpEmail   = findViewById(R.id.tvOtpEmail);
        tvTimer      = findViewById(R.id.tvTimer);
        btnVerify    = findViewById(R.id.btnVerify);
        btnResend    = findViewById(R.id.btnResend);

        tvGoToLogin = findViewById(R.id.tvGoToLogin);
    }

    // ─── Auto-avance y retroceso entre los 6 campos del OTP ──────────────────

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

    // ─── Listeners ────────────────────────────────────────────────────────────

    private void setListeners() {
        btnSendOtp.setOnClickListener(v -> attemptSendOtp());
        btnVerify.setOnClickListener(v -> attemptVerifyOtp());

        btnResend.setOnClickListener(v -> {
            clearOtpFields();
            Toast.makeText(this, getString(R.string.otp_resent), Toast.LENGTH_SHORT).show();
            btnResend.setEnabled(false);
            startCountdown();
        });

        tvGoToLogin.setOnClickListener(v -> finish());
    }

    // ─── Paso 1: validar formulario → crear cuenta en Firebase Auth ──────────

    private void attemptSendOtp() {
        String name    = etName.getText().toString().trim();
        String email   = etEmail.getText().toString().trim();
        String pass    = etPassword.getText().toString().trim();
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

        btnSendOtp.setEnabled(false);

        // 1) Crear usuario en Firebase Authentication
        mAuth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();

                    // 2) Guardar datos del usuario en Firestore (colección "users")
                    Map<String, Object> userData = new HashMap<>();
                    userData.put("id",          uid);
                    userData.put("email",        email);
                    userData.put("name",         name);
                    userData.put("preferences",  new ArrayList<>());
                    userData.put("createdAt",    FieldValue.serverTimestamp());

                    db.collection("users")
                            .document(uid)   // usamos el mismo UID de Auth como ID del documento
                            .set(userData)
                            .addOnSuccessListener(unused -> {
                                btnSendOtp.setEnabled(true);

                                // 3) Mostrar step OTP (simulado por ahora)
                                tvOtpEmail.setText(getString(R.string.otp_sent_to, email));
                                showStep(STEP_OTP);
                                startCountdown();
                            })
                            .addOnFailureListener(e -> {
                                btnSendOtp.setEnabled(true);
                                Toast.makeText(this, getString(R.string.error_firestore), Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    btnSendOtp.setEnabled(true);
                    Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                });
    }

    // ─── Paso 2: verificar OTP → navegar al Home ─────────────────────────────

    private void attemptVerifyOtp() {
        // TODO: conectar con servicio OTP real cuando el backend lo implemente
        // Por ahora cualquier código de 6 dígitos es válido
        navigateToHome(etEmail.getText().toString().trim());
    }

    // ─── Timer ────────────────────────────────────────────────────────────────

    private void startCountdown() {
        if (countDownTimer != null) countDownTimer.cancel();
        btnResend.setEnabled(false);

        countDownTimer = new CountDownTimer(OTP_EXPIRY_MS, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long min = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished);
                long sec = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60;
                tvTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", min, sec));
            }

            @Override
            public void onFinish() {
                tvTimer.setText(getString(R.string.otp_expired));
                btnResend.setEnabled(true);
            }
        }.start();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void showStep(int step) {
        currentStep = step;
        stepForm.setVisibility(step == STEP_FORM ? View.VISIBLE : View.GONE);
        stepOtp.setVisibility(step == STEP_OTP   ? View.VISIBLE : View.GONE);
    }

    private boolean isOtpComplete() {
        for (EditText field : otpFields) {
            if (field.getText().toString().isEmpty()) return false;
        }
        return true;
    }

    private void clearOtpFields() {
        for (EditText field : otpFields) field.setText("");
        otpFields[0].requestFocus();
        btnVerify.setEnabled(false);
    }

    private void navigateToHome(String email) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("EMAIL_USUARIO", email);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}