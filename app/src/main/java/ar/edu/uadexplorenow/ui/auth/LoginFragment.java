package ar.edu.uadexplorenow.ui.auth;

import android.os.Bundle;
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
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.firebase.auth.FirebaseAuth;

import ar.edu.uadexplorenow.R;
import ar.edu.uadexplorenow.data.model.UserRtdbDto;
import ar.edu.uadexplorenow.data.network.RealtimeRetrofitClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginFragment extends Fragment {

    private EditText    etEmail, etPassword;
    private Button      btnLogin;
    private ProgressBar progress;
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

        // Sesión activa → traer perfil y saltar directo al home
        if (mAuth.getCurrentUser() != null) {
            fetchUserAndNavigate(view, mAuth.getCurrentUser().getUid());
            return;
        }

        etEmail    = view.findViewById(R.id.etEmail);
        etPassword = view.findViewById(R.id.etPassword);
        btnLogin   = view.findViewById(R.id.btnLogin);
        progress   = view.findViewById(R.id.progress);

        TextView tvGoToRegister = view.findViewById(R.id.btnGoToRegister);

        Button btnLoginWithOtp = view.findViewById(R.id.btnLoginWithOtp);

        btnLogin.setOnClickListener(v -> attemptLogin(view));
        tvGoToRegister.setOnClickListener(v ->
                Navigation.findNavController(view)
                        .navigate(R.id.action_login_to_register));
        btnLoginWithOtp.setOnClickListener(v ->
                Navigation.findNavController(view)
                        .navigate(R.id.action_login_to_otp_login));
    }

    // ─── Paso 1: Firebase Auth ─────────────────────────────────────────────────

    private void attemptLogin(View view) {
        String email    = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(requireContext(),
                    getString(R.string.error_fields_required),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    if (!isAdded()) return;
                    // Auth OK → traer perfil de la RTDB
                    fetchUserAndNavigate(view, authResult.getUser().getUid());
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    setLoading(false);
                    Toast.makeText(requireContext(),
                            getString(R.string.error_login_failed),
                            Toast.LENGTH_LONG).show();
                });
    }

    // ─── Paso 2: GET /users/{uid}.json ────────────────────────────────────────

    private void fetchUserAndNavigate(View view, String uid) {
        setLoading(true);

        RealtimeRetrofitClient.getApi()
                .getUser(uid)
                .enqueue(new Callback<UserRtdbDto>() {

                    @Override
                    public void onResponse(@NonNull Call<UserRtdbDto> call,
                                           @NonNull Response<UserRtdbDto> response) {
                        if (!isAdded()) return;
                        setLoading(false);

                        if (!response.isSuccessful() || response.body() == null) {
                            // El usuario autenticó bien pero no tiene perfil en la DB
                            // igual lo dejamos pasar, el email alcanza para el home
                            navigateToHome(view,
                                    mAuth.getCurrentUser() != null
                                            ? mAuth.getCurrentUser().getEmail()
                                            : "",
                                    "");
                            return;
                        }

                        UserRtdbDto user = response.body();
                        navigateToHome(view,
                                user.email != null ? user.email : "",
                                user.name  != null ? user.name  : "");
                    }

                    @Override
                    public void onFailure(@NonNull Call<UserRtdbDto> call,
                                          @NonNull Throwable t) {
                        if (!isAdded()) return;
                        setLoading(false);
                        // Fallo de red — igual navegamos, el home puede funcionar sin perfil
                        Toast.makeText(requireContext(),
                                getString(R.string.explore_load_error),
                                Toast.LENGTH_SHORT).show();
                        navigateToHome(view,
                                mAuth.getCurrentUser() != null
                                        ? mAuth.getCurrentUser().getEmail()
                                        : "",
                                "");
                    }
                });
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void setLoading(boolean loading) {
        if (progress != null) progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (btnLogin != null) btnLogin.setEnabled(!loading);
    }

    private void navigateToHome(View view, String email, String name) {
        Bundle args = new Bundle();
        args.putString("email", email);
        args.putString("name",  name);
        Navigation.findNavController(view)
                .navigate(R.id.action_auth_to_home, args);
    }
}