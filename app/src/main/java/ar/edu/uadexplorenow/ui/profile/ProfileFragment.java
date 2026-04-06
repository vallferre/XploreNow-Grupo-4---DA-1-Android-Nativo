package ar.edu.uadexplorenow.ui.profile;

import android.os.Bundle;
import android.text.TextUtils;
import android.net.Uri;
import android.util.Log;
import android.widget.ImageView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException;
import com.google.firebase.auth.FirebaseUser;
import com.google.android.material.chip.Chip;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ar.edu.uadexplorenow.R;
import ar.edu.uadexplorenow.data.SessionStore;
import ar.edu.uadexplorenow.data.model.UserRtdbDto;
import ar.edu.uadexplorenow.data.network.RealtimeRetrofitClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileFragment extends Fragment {

    private static final String TAG = "ProfileFragment";

    private static final String PREF_AVENTURA = "aventura";
    private static final String PREF_CULTURA = "cultura";
    private static final String PREF_GASTRONOMIA = "gastronomia";
    private static final String PREF_NATURALEZA = "naturaleza";
    private static final String PREF_RELAX = "relax";

    private EditText etName;
    private EditText etPhotoUrl;
    private EditText etEmail;
    private EditText etPhone;
    private ImageView ivProfilePhoto;
    private Chip chipPrefAventura;
    private Chip chipPrefCultura;
    private Chip chipPrefGastronomia;
    private Chip chipPrefNaturaleza;
    private Chip chipPrefRelax;
    private TextView tvLegacyPreferences;
    private TextView tvReservedCount;
    private TextView tvCompletedCount;
    private ProgressBar progress;
    private Button btnSave;
    private Button btnLogout;

    private UserRtdbDto loadedUser;
    private boolean canSaveProfile;
    private final List<String> preservedLegacyPreferences = new ArrayList<>();

    // UID y email efectivos: para login OTP difieren del user anónimo de Firebase Auth.
    private String effectiveUid;
    private String effectiveEmail;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Navigation.findNavController(view).popBackStack();
            return;
        }

        // Resolvemos las credenciales efectivas una sola vez.
        // Para login clásico coinciden con currentUser; para login OTP apuntan
        // al usuario real guardado por SessionStore.
        effectiveUid   = SessionStore.getEffectiveUid(requireContext(), currentUser);
        effectiveEmail = SessionStore.getEffectiveEmail(requireContext(), currentUser);

        ImageButton btnBack = view.findViewById(R.id.btnBack);
        etName = view.findViewById(R.id.etName);
        ivProfilePhoto = view.findViewById(R.id.ivProfilePhoto);
        etPhotoUrl = view.findViewById(R.id.etPhotoUrl);
        etEmail = view.findViewById(R.id.etEmail);
        etPhone = view.findViewById(R.id.etPhone);
        chipPrefAventura = view.findViewById(R.id.chipPrefAventura);
        chipPrefCultura = view.findViewById(R.id.chipPrefCultura);
        chipPrefGastronomia = view.findViewById(R.id.chipPrefGastronomia);
        chipPrefNaturaleza = view.findViewById(R.id.chipPrefNaturaleza);
        chipPrefRelax = view.findViewById(R.id.chipPrefRelax);
        tvLegacyPreferences = view.findViewById(R.id.tvLegacyPreferences);
        tvReservedCount = view.findViewById(R.id.tvReservedCount);
        tvCompletedCount = view.findViewById(R.id.tvCompletedCount);
        progress = view.findViewById(R.id.progress);
        btnSave = view.findViewById(R.id.btnSaveProfile);
        btnLogout = view.findViewById(R.id.btnLogout);

        btnBack.setOnClickListener(v -> Navigation.findNavController(view).popBackStack());
        btnSave.setOnClickListener(v -> saveProfile(currentUser));
        btnLogout.setOnClickListener(v -> logout(view));
        etPhotoUrl.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                loadProfilePhoto(etPhotoUrl.getText().toString().trim());
            }
        });

        canSaveProfile = false;
        btnSave.setEnabled(false);
        loadProfile(currentUser);
    }

    private void loadProfile(@NonNull FirebaseUser currentUser) {
        setLoading(true);
        currentUser.reload().addOnCompleteListener(task -> {
            FirebaseUser refreshedUser = FirebaseAuth.getInstance().getCurrentUser();
            FirebaseUser userForLoad = refreshedUser != null ? refreshedUser : currentUser;
            if (!task.isSuccessful()) {
                Log.w(TAG, "Could not reload auth user before loading profile", task.getException());
            }
            fetchProfile(userForLoad);
        });
    }

    private void fetchProfile(@NonNull FirebaseUser currentUser) {
        RealtimeRetrofitClient.getApi().getUser(effectiveUid).enqueue(new Callback<UserRtdbDto>() {
            @Override
            public void onResponse(@NonNull Call<UserRtdbDto> call, @NonNull Response<UserRtdbDto> response) {
                if (!isAdded()) return;
                UserRtdbDto body = response.body();
                loadedUser = body != null ? body : buildFallbackUser(currentUser);
                if (isBlank(loadedUser.id))    loadedUser.id    = effectiveUid;
                if (isBlank(loadedUser.email)) loadedUser.email = effectiveEmail;
                if (loadedUser.preferences == null) loadedUser.preferences = new ArrayList<>();
                if (loadedUser.legacyPreferences == null) loadedUser.legacyPreferences = new ArrayList<>();
                if (loadedUser.phone == null) loadedUser.phone = "";
                if (loadedUser.photoUrl == null) loadedUser.photoUrl = "";
                if (loadedUser.name == null) loadedUser.name = "";
                syncEmailFromAuthIfNeeded(currentUser, loadedUser);
                canSaveProfile = true;
                setLoading(false);
                bindUser(loadedUser);
            }

            @Override
            public void onFailure(@NonNull Call<UserRtdbDto> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                setLoading(false);
                loadedUser = buildFallbackUser(currentUser);
                canSaveProfile = false;
                bindUser(loadedUser);
                Toast.makeText(requireContext(), R.string.profile_load_error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindUser(@NonNull UserRtdbDto user) {
        etName.setText(safe(user.name));
        etPhotoUrl.setText(safe(user.photoUrl));
        loadProfilePhoto(safe(user.photoUrl));
        etEmail.setText(isBlank(user.email)
                ? getString(R.string.profile_email_empty)
                : user.email);
        etPhone.setText(safe(user.phone));
        bindPreferenceSelection(user.preferences, user.legacyPreferences);
        bindActivitySummary(user);
    }

    private void saveProfile(@NonNull FirebaseUser currentUser) {
        if (!canSaveProfile) {
            Toast.makeText(requireContext(), R.string.profile_load_error, Toast.LENGTH_LONG).show();
            return;
        }

        String name = etName.getText().toString().trim();
        if (name.isEmpty()) {
            etName.setError(getString(R.string.error_name_required));
            etName.requestFocus();
            return;
        }

        UserRtdbDto dto = loadedUser != null ? loadedUser : buildFallbackUser(currentUser);
        String currentEmail = !isBlank(effectiveEmail) ? effectiveEmail : safe(currentUser.getEmail());
        if (isBlank(currentEmail)) currentEmail = safe(dto.email);
        final String appliedCurrentEmail = currentEmail;
        String photoUrl = etPhotoUrl.getText().toString().trim();
        if (!isValidPhotoUrl(photoUrl)) {
            etPhotoUrl.setError(getString(R.string.profile_photo_invalid));
            etPhotoUrl.requestFocus();
            return;
        }
        String email = etEmail.getText().toString().trim();
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError(getString(R.string.error_email_invalid));
            etEmail.requestFocus();
            return;
        }
        String phone = etPhone.getText().toString().trim();
        List<String> preferences = collectSelectedPreferences();
        List<String> legacyPreferences = new ArrayList<>(preservedLegacyPreferences);

        if (!sameValue(email, currentEmail)) {
            setLoading(true);
            currentUser.verifyBeforeUpdateEmail(email)
                    .addOnSuccessListener(unused -> {
                        if (!isAdded()) return;
                        setLoading(false);
                        etEmail.setText(appliedCurrentEmail);
                        Toast.makeText(
                                requireContext(),
                                R.string.profile_email_verification_sent,
                                Toast.LENGTH_LONG
                        ).show();
                    })
                    .addOnFailureListener(e -> {
                        if (!isAdded()) return;
                        setLoading(false);
                        Log.e(TAG, "Email verify-before-update failed", e);
                        Toast.makeText(
                                requireContext(),
                                resolveEmailUpdateErrorMessage(e),
                                Toast.LENGTH_LONG
                        ).show();
                    });
            return;
        }

        setLoading(true);
        patchProfile(currentUser, dto, name, email, phone, photoUrl, preferences, legacyPreferences);
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSave.setEnabled(!loading && canSaveProfile);
        etName.setEnabled(!loading);
        etPhotoUrl.setEnabled(!loading);
        etEmail.setEnabled(!loading);
        etPhone.setEnabled(!loading);
        chipPrefAventura.setEnabled(!loading);
        chipPrefCultura.setEnabled(!loading);
        chipPrefGastronomia.setEnabled(!loading);
        chipPrefNaturaleza.setEnabled(!loading);
        chipPrefRelax.setEnabled(!loading);
        btnLogout.setEnabled(!loading);
    }

    private void patchProfile(
            @NonNull FirebaseUser currentUser,
            @NonNull UserRtdbDto dto,
            @NonNull String name,
            @NonNull String email,
            @NonNull String phone,
            @NonNull String photoUrl,
            @NonNull List<String> preferences,
            @NonNull List<String> legacyPreferences
    ) {
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("id", effectiveUid);
        updates.put("email", email);
        updates.put("name", name);
        updates.put("phone", phone);
        updates.put("photoUrl", photoUrl);
        updates.put("preferences", preferences);
        if (!legacyPreferences.isEmpty()) {
            updates.put("legacy_preferences", legacyPreferences);
        }

        RealtimeRetrofitClient.getApi().patchUser(effectiveUid, updates)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                        if (!isAdded()) return;
                        setLoading(false);
                        if (!response.isSuccessful()) {
                            Toast.makeText(requireContext(), R.string.profile_save_error, Toast.LENGTH_LONG).show();
                            return;
                        }
                        dto.id = effectiveUid;
                        dto.email = email;
                        dto.name = name;
                        dto.phone = phone;
                        dto.photoUrl = photoUrl;
                        dto.preferences = preferences;
                        dto.legacyPreferences = legacyPreferences;
                        loadedUser = dto;
                        loadProfilePhoto(photoUrl);
                        Toast.makeText(requireContext(), R.string.profile_saved, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                        if (!isAdded()) return;
                        setLoading(false);
                        Toast.makeText(requireContext(), R.string.profile_save_error, Toast.LENGTH_LONG).show();
                    }
                    });
    }

    private void syncEmailFromAuthIfNeeded(
            @NonNull FirebaseUser currentUser,
            @NonNull UserRtdbDto dto
    ) {
        String authEmail = safe(currentUser.getEmail()).trim();
        if (isBlank(authEmail) || sameValue(authEmail, dto.email)) {
            return;
        }

        dto.email = authEmail;
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("email", authEmail);
        RealtimeRetrofitClient.getApi().patchUser(effectiveUid, updates)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                        if (!response.isSuccessful()) {
                            Log.w(TAG, "Could not sync verified auth email to RTDB: " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                        Log.w(TAG, "Could not sync verified auth email to RTDB", t);
                    }
                });
    }

    @NonNull
    private UserRtdbDto buildFallbackUser(@NonNull FirebaseUser currentUser) {
        UserRtdbDto dto = new UserRtdbDto();
        dto.id    = effectiveUid;
        dto.name  = safe(currentUser.getDisplayName());
        dto.email = !isBlank(effectiveEmail) ? effectiveEmail : safe(currentUser.getEmail());
        dto.phone = "";
        dto.photoUrl = "";
        dto.preferences = new ArrayList<>();
        dto.legacyPreferences = new ArrayList<>();
        dto.createdAt = "";
        return dto;
    }

    private boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean sameValue(@Nullable String a, @Nullable String b) {
        return safe(a).equals(safe(b));
    }

    @NonNull
    private String safe(@Nullable String value) {
        return value != null ? value : "";
    }

    private void loadProfilePhoto(@Nullable String photoUrl) {
        if (!isAdded()) return;
        if (photoUrl == null || photoUrl.trim().isEmpty()) {
            Glide.with(this).clear(ivProfilePhoto);
            ivProfilePhoto.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            int pad = dpToPx(18);
            ivProfilePhoto.setPadding(pad, pad, pad, pad);
            ivProfilePhoto.setBackgroundResource(R.drawable.bg_profile_avatar);
            ivProfilePhoto.setColorFilter(ContextCompat.getColor(requireContext(), R.color.explore_muted));
            ivProfilePhoto.setImageResource(R.drawable.ic_nav_person);
            return;
        }
        ivProfilePhoto.setPadding(0, 0, 0, 0);
        ivProfilePhoto.setBackground(null);
        ivProfilePhoto.setColorFilter(null);
        ivProfilePhoto.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Glide.with(this)
                .load(photoUrl.trim())
                .circleCrop()
                .placeholder(R.drawable.ic_nav_person)
                .error(R.drawable.ic_nav_person)
                .into(ivProfilePhoto);
    }

    private void bindPreferenceSelection(
            @Nullable List<String> preferences,
            @Nullable List<String> legacyPreferences
    ) {
        chipPrefAventura.setChecked(false);
        chipPrefCultura.setChecked(false);
        chipPrefGastronomia.setChecked(false);
        chipPrefNaturaleza.setChecked(false);
        chipPrefRelax.setChecked(false);

        LinkedHashSet<String> legacy = new LinkedHashSet<>();
        applyPreferenceSource(preferences, legacy);
        applyPreferenceSource(legacyPreferences, legacy);

        preservedLegacyPreferences.clear();
        preservedLegacyPreferences.addAll(legacy);

        if (preservedLegacyPreferences.isEmpty()) {
            tvLegacyPreferences.setVisibility(View.GONE);
        } else {
            tvLegacyPreferences.setVisibility(View.VISIBLE);
            tvLegacyPreferences.setText(getString(
                    R.string.profile_preferences_legacy_fmt,
                    TextUtils.join(", ", preservedLegacyPreferences)));
        }
    }

    private void applyPreferenceSource(@Nullable List<String> source, @NonNull LinkedHashSet<String> legacy) {
        if (source == null) return;
        for (String value : source) {
            String trimmed = safe(value).trim();
            if (trimmed.isEmpty()) continue;
            String canonical = canonicalPreference(trimmed);
            if (canonical == null) {
                legacy.add(trimmed);
                continue;
            }
            setPreferenceChecked(canonical, true);
        }
    }

    @NonNull
    private List<String> collectSelectedPreferences() {
        List<String> out = new ArrayList<>();
        if (chipPrefAventura.isChecked()) out.add(PREF_AVENTURA);
        if (chipPrefCultura.isChecked()) out.add(PREF_CULTURA);
        if (chipPrefGastronomia.isChecked()) out.add(PREF_GASTRONOMIA);
        if (chipPrefNaturaleza.isChecked()) out.add(PREF_NATURALEZA);
        if (chipPrefRelax.isChecked()) out.add(PREF_RELAX);
        return out;
    }

    private void setPreferenceChecked(@NonNull String preference, boolean checked) {
        switch (preference) {
            case PREF_AVENTURA:
                chipPrefAventura.setChecked(checked);
                return;
            case PREF_CULTURA:
                chipPrefCultura.setChecked(checked);
                return;
            case PREF_GASTRONOMIA:
                chipPrefGastronomia.setChecked(checked);
                return;
            case PREF_NATURALEZA:
                chipPrefNaturaleza.setChecked(checked);
                return;
            case PREF_RELAX:
                chipPrefRelax.setChecked(checked);
                return;
            default:
        }
    }

    @Nullable
    private String canonicalPreference(@Nullable String raw) {
        String normalized = normalize(raw);
        if (normalized.isEmpty()) return null;
        if (normalized.equals(PREF_AVENTURA) || normalized.equals("aventuras")) {
            return PREF_AVENTURA;
        }
        if (normalized.equals(PREF_CULTURA) || normalized.equals("cultural")) {
            return PREF_CULTURA;
        }
        if (normalized.equals(PREF_GASTRONOMIA) || normalized.equals("gastronomica")) {
            return PREF_GASTRONOMIA;
        }
        if (normalized.equals(PREF_NATURALEZA) || normalized.equals("natural")) {
            return PREF_NATURALEZA;
        }
        if (normalized.equals(PREF_RELAX) || normalized.equals("descanso")) {
            return PREF_RELAX;
        }
        return null;
    }

    @NonNull
    private String normalize(@Nullable String raw) {
        if (raw == null) return "";
        String text = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return text.toLowerCase(Locale.ROOT);
    }

    private void bindActivitySummary(@NonNull UserRtdbDto user) {
        tvReservedCount.setText(String.valueOf(resolveSummaryCount(
                user.reservedActivityIds,
                user.reservedActivitiesCount)));
        tvCompletedCount.setText(String.valueOf(resolveSummaryCount(
                user.completedActivityIds,
                user.completedActivitiesCount)));
    }

    private int resolveSummaryCount(@Nullable List<String> ids, @Nullable Long explicitCount) {
        int listCount = ids != null ? ids.size() : 0;
        int dbCount = explicitCount != null && explicitCount > 0 ? explicitCount.intValue() : 0;
        return Math.max(listCount, dbCount);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private boolean isValidPhotoUrl(@Nullable String raw) {
        if (raw == null || raw.trim().isEmpty()) return true;
        Uri uri = Uri.parse(raw.trim());
        String scheme = uri.getScheme();
        String host = uri.getHost();
        return scheme != null
                && "https".equalsIgnoreCase(scheme)
                && host != null
                && !host.trim().isEmpty();
    }

    @NonNull
    private String resolveEmailUpdateErrorMessage(@Nullable Exception e) {
        if (e instanceof FirebaseAuthRecentLoginRequiredException) {
            return getString(R.string.profile_email_recent_login_required);
        }
        if (e instanceof FirebaseAuthException) {
            String code = ((FirebaseAuthException) e).getErrorCode();
            if ("ERROR_EMAIL_ALREADY_IN_USE".equals(code)) {
                return getString(R.string.profile_email_already_in_use);
            }
            if ("ERROR_INVALID_EMAIL".equals(code)) {
                return getString(R.string.error_email_invalid);
            }
            if ("ERROR_REQUIRES_RECENT_LOGIN".equals(code)) {
                return getString(R.string.profile_email_recent_login_required);
            }
            if ("ERROR_OPERATION_NOT_ALLOWED".equals(code)) {
                return getString(R.string.profile_email_verification_required);
            }
            String localized = e.getLocalizedMessage();
            if (localized != null && !localized.trim().isEmpty()) {
                return localized;
            }
        }
        if (e != null) {
            String localized = e.getLocalizedMessage();
            if (localized != null && !localized.trim().isEmpty()) {
                return localized;
            }
        }
        return getString(R.string.profile_email_update_error);
    }

    private void logout(@NonNull View view) {
        SessionStore.clear(requireContext());
        FirebaseAuth.getInstance().signOut();
        NavOptions navOptions = new NavOptions.Builder()
                .setPopUpTo(R.id.exploreFragment, true)
                .build();
        Navigation.findNavController(view).navigate(R.id.loginFragment, null, navOptions);
    }
}
