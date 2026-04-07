package ar.edu.uadexplorenow.ui.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import javax.inject.Inject;

import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;

import ar.edu.uadexplorenow.R;
import ar.edu.uadexplorenow.data.model.UserRtdbDto;
import ar.edu.uadexplorenow.data.network.RealtimeDatabaseApi;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class RegisterFragment extends Fragment {

    @Inject
    RealtimeDatabaseApi realtimeDatabaseApi;

    private EditText etName, etEmail, etPassword, etConfirmPassword;
    private Button   btnRegister;
    private ProgressBar progressForm;

    private FirebaseAuth mAuth;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_register, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();

        etName            = view.findViewById(R.id.etName);
        etEmail           = view.findViewById(R.id.etEmail);
        etPassword        = view.findViewById(R.id.etPassword);
        etConfirmPassword = view.findViewById(R.id.etConfirmPassword);
        btnRegister       = view.findViewById(R.id.btnRegister);
        progressForm      = view.findViewById(R.id.progressForm);

        btnRegister.setOnClickListener(v -> attemptRegister());

        view.findViewById(R.id.tvGoToLogin).setOnClickListener(v ->
                Navigation.findNavController(view).popBackStack());
    }

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

        setLoading(true);

        mAuth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener(authResult -> {
                    if (!isAdded()) return;
                    String uid = authResult.getUser().getUid();
                    saveUserToRtdb(uid, name, email);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    setLoading(false);
                    Toast.makeText(requireContext(),
                            e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void saveUserToRtdb(String uid, String name, String email) {
        UserRtdbDto dto = new UserRtdbDto();
        dto.id          = uid;
        dto.email       = email;
        dto.name        = name;
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

    /** Guarda email → uid en RTDB para que el login OTP pueda encontrar el perfil. */
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

    private void setLoading(boolean loading) {
        if (progressForm != null) progressForm.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnRegister.setEnabled(!loading);
    }

    private void navigateToHome(View view, String name, String email) {
        Bundle args = new Bundle();
        args.putString("email", email);
        args.putString("name",  name);
        Navigation.findNavController(view)
                .navigate(R.id.action_auth_to_home, args);
    }
}
