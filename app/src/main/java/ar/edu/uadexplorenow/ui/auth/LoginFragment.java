package ar.edu.uadexplorenow.ui.auth;

import android.os.Bundle;
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

import ar.edu.uadexplorenow.R;
import ar.edu.uadexplorenow.data.SessionStore;
import ar.edu.uadexplorenow.data.local.BiometricPrefs;
import ar.edu.uadexplorenow.data.local.TokenManager;
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
    @Inject TokenManager tokenManager;

    private EditText     etEmail, etPassword;
    private Button       btnLogin;
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
        progress   = view.findViewById(R.id.progress);

        ((TextView) view.findViewById(R.id.btnGoToRegister)).setOnClickListener(v ->
                Navigation.findNavController(view).navigate(R.id.action_login_to_register));
        ((Button) view.findViewById(R.id.btnLoginWithOtp)).setOnClickListener(v ->
                Navigation.findNavController(view).navigate(R.id.action_login_to_otp_login));
        btnLogin.setOnClickListener(v -> attemptLogin(view));

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
                    tokenManager.saveToken("fake-token-abc123");
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
                setLoading(false);

                String email = "", name = "";
                if (response.isSuccessful() && response.body() != null) {
                    UserRtdbDto u = response.body();
                    email = u.email != null ? u.email : "";
                    name  = u.name  != null ? u.name  : "";
                } else if (mAuth.getCurrentUser() != null) {
                    email = mAuth.getCurrentUser().getEmail() != null
                            ? mAuth.getCurrentUser().getEmail() : "";
                }

                final String finalEmail = email;
                final String finalName  = name;

                if (offerBiometric && shouldOfferBiometric()) {
                    showBiometricOfferDialog(view, uid, finalEmail, finalName);
                } else {
                    navigateToHome(view, finalEmail, finalName);
                }
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
    }

    private void navigateToHome(View view, String email, String name) {
        Bundle args = new Bundle();
        args.putString("email", email);
        args.putString("name",  name);
        Navigation.findNavController(view).navigate(R.id.action_auth_to_home, args);
    }
}
