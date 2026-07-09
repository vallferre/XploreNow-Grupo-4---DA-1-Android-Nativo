package ar.edu.uadexplorenow.ui.auth;

import android.os.Bundle;
import android.util.Patterns;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricManager.Authenticators;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import javax.inject.Inject;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.LinkedHashMap;
import java.util.Map;

import ar.edu.uadexplorenow.R;
import ar.edu.uadexplorenow.data.SessionStore;
import ar.edu.uadexplorenow.data.local.BiometricPrefs;
import ar.edu.uadexplorenow.data.model.UserRtdbDto;
import ar.edu.uadexplorenow.data.network.RealtimeDatabaseApi;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class LoginFragment extends Fragment {

    private static final String TAG = "LoginFragment";

    @Inject RealtimeDatabaseApi realtimeDatabaseApi;

    private EditText     etEmail, etPassword;
    private Button       btnLogin;
    private TextView     btnForgotPassword;
    private ProgressBar  progress;
    private FirebaseAuth mAuth;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();

        // Inicializar vistas siempre: si la biometría falla quedan disponibles
        etEmail    = view.findViewById(R.id.etEmail);
        etPassword = view.findViewById(R.id.etPassword);
        btnLogin   = view.findViewById(R.id.btnLogin);
        btnForgotPassword = view.findViewById(R.id.btnForgotPassword);
        progress   = view.findViewById(R.id.progress);

        ((TextView) view.findViewById(R.id.btnGoToRegister)).setOnClickListener(v ->
                Navigation.findNavController(view).navigate(R.id.action_login_to_register));
        ((Button) view.findViewById(R.id.btnLoginWithOtp)).setOnClickListener(v ->
                Navigation.findNavController(view).navigate(R.id.action_login_to_otp_login));
        btnLogin.setOnClickListener(v -> attemptLogin(view));
        btnForgotPassword.setOnClickListener(v -> requestPasswordReset());

        if (mAuth.getCurrentUser() != null) {
            // Sesión activa: si biometría habilitada pedir confirmación antes de pasar al home
            if (BiometricPrefs.isEnabled(requireContext()) && isBiometricAvailable()) {
                showBiometricUnlock(view);
            } else {
                fetchUserAndNavigate(view, mAuth.getCurrentUser().getUid(), false);
            }
        }
    }

    // ─── Biometría: desbloqueo al abrir app ──────────────────────────────────

    private boolean isBiometricAvailable() {
        int r = BiometricManager.from(requireContext())
                .canAuthenticate(Authenticators.BIOMETRIC_STRONG | Authenticators.DEVICE_CREDENTIAL);
        return r == BiometricManager.BIOMETRIC_SUCCESS;
    }

    /**
     * Se llama cuando hay sesión activa y biometría habilitada.
     * Éxito   → navega al home.
     * Cancelar/Error → queda el formulario para que el usuario entre con contraseña.
     */
    private void showBiometricUnlock(View navView) {
        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.biometric_prompt_title))
                .setSubtitle(getString(R.string.biometric_prompt_subtitle))
                .setAllowedAuthenticators(
                        Authenticators.BIOMETRIC_STRONG | Authenticators.DEVICE_CREDENTIAL)
                .build();

        new BiometricPrompt(this, ContextCompat.getMainExecutor(requireContext()),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(
                            @NonNull BiometricPrompt.AuthenticationResult result) {
                        if (!isAdded()) return;
                        fetchUserAndNavigate(navView, mAuth.getCurrentUser().getUid(), false);
                    }
                    @Override public void onAuthenticationFailed() { /* prompt sigue abierto */ }
                    @Override
                    public void onAuthenticationError(int code, @NonNull CharSequence msg) {
                        Log.w(TAG, "Biometric unlock error " + code + ": " + msg);
                        // Usuario canceló → deja el formulario visible sin hacer nada
                    }
                }).authenticate(info);
    }

    // ─── Login clásico ────────────────────────────────────────────────────────

    private void attemptLogin(View view) {
        String email    = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(requireContext(),
                    getString(R.string.error_fields_required), Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    if (!isAdded()) return;
                    // Login fresco → ofrecer biometría si no fue configurada
                    fetchUserAndNavigate(view, authResult.getUser().getUid(), true);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    setLoading(false);
                    Toast.makeText(requireContext(),
                            getString(R.string.error_login_failed), Toast.LENGTH_LONG).show();
                });
    }

    private void requestPasswordReset() {
        String email = etEmail.getText().toString().trim();
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError(getString(R.string.error_email_invalid));
            etEmail.requestFocus();
            return;
        }

        setLoading(true);
        mAuth.sendPasswordResetEmail(email)
                .addOnSuccessListener(unused -> {
                    if (!isAdded()) return;
                    setLoading(false);
                    Toast.makeText(
                            requireContext(),
                            R.string.login_password_reset_sent,
                            Toast.LENGTH_LONG
                    ).show();
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    setLoading(false);
                    Log.e(TAG, "Password reset email failed", e);
                    Toast.makeText(
                            requireContext(),
                            R.string.login_password_reset_error,
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    // ─── Buscar perfil y (opcionalmente) ofrecer biometría ───────────────────

    /**
     * @param offerBiometric true solo después de un login fresco con email+contraseña.
     *                       false cuando la sesión ya existía o se desbloquea con biometría.
     */
    private void fetchUserAndNavigate(View view, String uid, boolean offerBiometric) {
        setLoading(true);

        realtimeDatabaseApi.getUser(uid).enqueue(new Callback<UserRtdbDto>() {
            @Override
            public void onResponse(@NonNull Call<UserRtdbDto> call,
                                   @NonNull Response<UserRtdbDto> response) {
                if (!isAdded()) return;

                String email = "", name = "";
                UserRtdbDto user = null;
                if (response.isSuccessful() && response.body() != null) {
                    user = response.body();
                    email = user.email != null ? user.email : "";
                    name  = user.name  != null ? user.name  : "";
                } else if (mAuth.getCurrentUser() != null) {
                    email = mAuth.getCurrentUser().getEmail() != null
                            ? mAuth.getCurrentUser().getEmail() : "";
                }

                FirebaseUser authUser = mAuth.getCurrentUser();
                String authEmail = authUser != null && authUser.getEmail() != null
                        ? authUser.getEmail().trim()
                        : "";
                final String finalEmail = !authEmail.isEmpty() ? authEmail : email;
                final String finalName  = name;
                synchronizeVerifiedEmailIndex(uid, user, () -> {
                    if (!isAdded()) return;
                    setLoading(false);
                    if (offerBiometric && shouldOfferBiometric()) {
                        showBiometricOfferDialog(view, uid, finalEmail, finalName);
                    } else {
                        navigateToHome(view, finalEmail, finalName);
                    }
                });
            }

            @Override
            public void onFailure(@NonNull Call<UserRtdbDto> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                setLoading(false);
                String email = mAuth.getCurrentUser() != null
                        ? mAuth.getCurrentUser().getEmail() : "";
                if (offerBiometric && shouldOfferBiometric()) {
                    showBiometricOfferDialog(view, uid, email != null ? email : "", "");
                } else {
                    navigateToHome(view, email != null ? email : "", "");
                }
            }
        });
    }

    private void synchronizeVerifiedEmailIndex(
            @NonNull String uid,
            @Nullable UserRtdbDto profile,
            @NonNull Runnable completion
    ) {
        FirebaseUser authUser = mAuth.getCurrentUser();
        String verifiedEmail = authUser != null && authUser.getEmail() != null
                ? authUser.getEmail().trim()
                : "";
        String storedEmail = profile != null && profile.email != null
                ? profile.email.trim()
                : "";
        if (profile == null
                || verifiedEmail.isEmpty()
                || verifiedEmail.equalsIgnoreCase(storedEmail)) {
            completion.run();
            return;
        }

        String newEmailKey = RegisterFragment.emailToKey(verifiedEmail);
        realtimeDatabaseApi.getUidByEmail(newEmailKey).enqueue(new Callback<String>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                if (!isAdded()) return;
                if (!response.isSuccessful()) {
                    Log.w(TAG, "Could not validate verified email index: " + response.code());
                    completion.run();
                    return;
                }

                String indexedUid = response.body();
                if (indexedUid != null && !indexedUid.trim().isEmpty() && !uid.equals(indexedUid.trim())) {
                    Toast.makeText(
                            requireContext(),
                            R.string.profile_email_already_in_use,
                            Toast.LENGTH_LONG
                    ).show();
                    completion.run();
                    return;
                }
                patchVerifiedProfileEmail(uid, storedEmail, verifiedEmail, completion);
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                Log.w(TAG, "Could not validate verified email index", t);
                completion.run();
            }
        });
    }

    private void patchVerifiedProfileEmail(
            @NonNull String uid,
            @NonNull String oldEmail,
            @NonNull String newEmail,
            @NonNull Runnable completion
    ) {
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("email", newEmail);
        realtimeDatabaseApi.patchUser(uid, updates).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (!isAdded()) return;
                if (!response.isSuccessful()) {
                    Log.w(TAG, "Could not sync verified profile email: " + response.code());
                    completion.run();
                    return;
                }
                putVerifiedEmailIndex(uid, oldEmail, newEmail, completion);
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                Log.w(TAG, "Could not sync verified profile email", t);
                completion.run();
            }
        });
    }

    private void putVerifiedEmailIndex(
            @NonNull String uid,
            @NonNull String oldEmail,
            @NonNull String newEmail,
            @NonNull Runnable completion
    ) {
        realtimeDatabaseApi.putEmailIndex(RegisterFragment.emailToKey(newEmail), uid)
                .enqueue(new Callback<String>() {
                    @Override
                    public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                        if (!isAdded()) return;
                        if (!response.isSuccessful()) {
                            Log.w(TAG, "Could not sync verified email index: " + response.code());
                            completion.run();
                            return;
                        }
                        deletePreviousEmailIndex(oldEmail, newEmail, completion);
                    }

                    @Override
                    public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                        if (!isAdded()) return;
                        Log.w(TAG, "Could not sync verified email index", t);
                        completion.run();
                    }
                });
    }

    private void deletePreviousEmailIndex(
            @NonNull String oldEmail,
            @NonNull String newEmail,
            @NonNull Runnable completion
    ) {
        if (oldEmail.isEmpty() || oldEmail.equalsIgnoreCase(newEmail)) {
            completion.run();
            return;
        }
        realtimeDatabaseApi.deleteEmailIndex(RegisterFragment.emailToKey(oldEmail))
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                        if (!response.isSuccessful()) {
                            Log.w(TAG, "Could not delete previous email index: " + response.code());
                        }
                        completion.run();
                    }

                    @Override
                    public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                        Log.w(TAG, "Could not delete previous email index", t);
                        completion.run();
                    }
                });
    }

    // ─── Oferta de biometría post-login ──────────────────────────────────────

    private boolean shouldOfferBiometric() {
        return !BiometricPrefs.isEnabled(requireContext())
                && !BiometricPrefs.wasDeclined(requireContext())
                && isBiometricAvailable();
    }

    /** Muestra el diálogo "¿Querés activar la huella?" después de un login exitoso. */
    private void showBiometricOfferDialog(View navView, String uid,
                                          String email, String name) {
        if (!isAdded()) return;

        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.biometric_offer_title))
                .setMessage(getString(R.string.biometric_offer_message))
                .setCancelable(false)
                .setPositiveButton(getString(R.string.biometric_offer_yes), (d, w) ->
                        verifyBiometricAndEnable(navView, email, name))
                .setNegativeButton(getString(R.string.biometric_offer_no), (d, w) -> {
                    BiometricPrefs.setDeclined(requireContext(), true);
                    navigateToHome(navView, email, name);
                })
                .show();
    }

    /** Muestra el prompt biométrico para verificar que funciona antes de activarlo. */
    private void verifyBiometricAndEnable(View navView, String email, String name) {
        if (!isAdded()) return;

        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.biometric_setup_title))
                .setSubtitle(getString(R.string.biometric_setup_subtitle))
                .setAllowedAuthenticators(
                        Authenticators.BIOMETRIC_STRONG | Authenticators.DEVICE_CREDENTIAL)
                .build();

        new BiometricPrompt(this, ContextCompat.getMainExecutor(requireContext()),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(
                            @NonNull BiometricPrompt.AuthenticationResult result) {
                        if (!isAdded()) return;
                        BiometricPrefs.setEnabled(requireContext(), true);
                        Toast.makeText(requireContext(),
                                getString(R.string.biometric_enabled_ok),
                                Toast.LENGTH_SHORT).show();
                        navigateToHome(navView, email, name);
                    }
                    @Override public void onAuthenticationFailed() { /* prompt sigue abierto */ }
                    @Override
                    public void onAuthenticationError(int code, @NonNull CharSequence msg) {
                        if (!isAdded()) return;
                        Log.w(TAG, "Biometric setup error " + code + ": " + msg);
                        // No se pudo verificar → navegamos igual sin activar
                        navigateToHome(navView, email, name);
                    }
                }).authenticate(info);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void setLoading(boolean loading) {
        if (progress != null) progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (btnLogin  != null) btnLogin.setEnabled(!loading);
        if (btnForgotPassword != null) btnForgotPassword.setEnabled(!loading);
    }

    private void navigateToHome(View view, String email, String name) {
        Bundle args = new Bundle();
        args.putString("email", email);
        args.putString("name",  name);
        Navigation.findNavController(view).navigate(R.id.action_auth_to_home, args);
    }
}
