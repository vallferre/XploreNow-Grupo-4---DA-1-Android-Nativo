package ar.edu.uadexplorenow.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

import ar.edu.uadexplorenow.R;
import ar.edu.uadexplorenow.ui.MainActivity;

public class LoginActivity extends AppCompatActivity {

    private EditText    etEmail, etPassword;

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();

        // Si ya hay sesión activa, saltear el login e ir directo al Home
        if (mAuth.getCurrentUser() != null) {
            navigateToHome(mAuth.getCurrentUser().getEmail());
            return;
        }

        etEmail     = findViewById(R.id.etEmail);
        etPassword  = findViewById(R.id.etPassword);

        Button   btnLogin       = findViewById(R.id.btnLogin);
        TextView tvGoToRegister = findViewById(R.id.btnGoToRegister);

        btnLogin.setOnClickListener(v -> attemptLogin());
        tvGoToRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void attemptLogin() {
        String email    = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_fields_required), Toast.LENGTH_SHORT).show();
            return;
        }

        // Firebase Authentication: verifica email y contraseña
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    navigateToHome(email);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, getString(R.string.error_login_failed), Toast.LENGTH_LONG).show();
                });
    }

    private void navigateToHome(String email) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("EMAIL_USUARIO", email);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
