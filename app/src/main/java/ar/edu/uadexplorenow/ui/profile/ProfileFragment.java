package ar.edu.uadexplorenow.ui.profile;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigation;

import javax.inject.Inject;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.signature.ObjectKey;
import com.google.android.material.chip.Chip;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricManager.Authenticators;
import androidx.biometric.BiometricPrompt;

import ar.edu.uadexplorenow.R;
import ar.edu.uadexplorenow.data.SessionStore;
import ar.edu.uadexplorenow.data.local.BiometricPrefs;
import ar.edu.uadexplorenow.data.local.cache.UserProfileFileCache;
import ar.edu.uadexplorenow.data.model.UserRtdbDto;
import ar.edu.uadexplorenow.data.network.RealtimeDatabaseApi;
import ar.edu.uadexplorenow.domain.ReservationItem;
import ar.edu.uadexplorenow.domain.ReservationStatus;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ProfileFragment extends Fragment {

    @Inject
    RealtimeDatabaseApi realtimeDatabaseApi;
    @Inject
    UserProfileFileCache userProfileFileCache;

    private static final String TAG = "ProfileFragment";

    private static final String PREF_AVENTURA = "aventura";
    private static final String PREF_CULTURA = "cultura";
    private static final String PREF_GASTRONOMIA = "gastronomia";
    private static final String PREF_NATURALEZA = "naturaleza";
    private static final String PREF_RELAX = "relax";
    private static final String PROFILE_IMAGE_DIR = "profile_images";
    private static final String PROFILE_IMAGE_FILE_PREFIX = "profile_photo_";
    private static final String PROFILE_IMAGE_CAPTURE_SUFFIX = "_capture";
    private static final long MAX_PROFILE_IMAGE_BYTES = 5L * 1024L * 1024L;

    private interface PhotoUploadCallback {
        void onSuccess(@NonNull String photoUrl);
        void onFailure(@NonNull Exception error);
    }

    private EditText etName;
    private EditText etEmail;
    private EditText etPhone;
    private ImageView ivProfilePhoto;
    private Button btnSelectProfileImage;
    private Chip chipPrefAventura;
    private Chip chipPrefCultura;
    private Chip chipPrefGastronomia;
    private Chip chipPrefNaturaleza;
    private Chip chipPrefRelax;
    private TextView tvLegacyPreferences;
    private TextView tvReservedCount;
    private TextView tvCompletedCount;
    private ProgressBar progress;
    private Button btnOpenHistory;
    private Button btnSave;
    private Button btnBiometric;
    private Button btnLogout;

    private UserRtdbDto loadedUser;
    private boolean canSaveProfile;
    private final List<String> preservedLegacyPreferences = new ArrayList<>();
    private String currentPhotoUrl = "";
    @Nullable
    private Uri pendingCameraPhotoUri;
    @Nullable
    private File pendingCameraPhotoFile;
    private final ActivityResultLauncher<String> requestGalleryPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (!isAdded()) return;
                if (isGranted) {
                    openGallery();
                    return;
                }
                Toast.makeText(requireContext(), R.string.profile_photo_permission_denied, Toast.LENGTH_LONG).show();
            });
    private final ActivityResultLauncher<String> requestCameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (!isAdded()) return;
                if (isGranted) {
                    openCamera();
                    return;
                }
                Toast.makeText(requireContext(), R.string.profile_photo_camera_permission_denied, Toast.LENGTH_LONG).show();
            });
    private final ActivityResultLauncher<String> pickProfileImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::handleSelectedProfileImage);
    private final ActivityResultLauncher<Uri> takeProfilePhotoLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), this::handleCapturedProfilePhoto);

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
        btnSelectProfileImage = view.findViewById(R.id.btnSelectProfileImage);
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
        btnOpenHistory = view.findViewById(R.id.btnOpenHistory);
        btnSave      = view.findViewById(R.id.btnSaveProfile);
        btnBiometric = view.findViewById(R.id.btnBiometric);
        btnLogout    = view.findViewById(R.id.btnLogout);

        btnBack.setOnClickListener(v -> Navigation.findNavController(view).popBackStack());
        btnOpenHistory.setOnClickListener(v -> Navigation.findNavController(view)
                .navigate(R.id.action_profileFragment_to_activityHistoryFragment));
        btnSelectProfileImage.setOnClickListener(v -> showPhotoSourceDialog());
        btnSave.setOnClickListener(v -> saveProfile(currentUser));
        btnLogout.setOnClickListener(v -> logout(view));

        setupBiometricButton();

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
        realtimeDatabaseApi.getUser(effectiveUid).enqueue(new Callback<UserRtdbDto>() {
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
                if (body != null) {
                    new Thread(() -> userProfileFileCache.save(body)).start();
                }
                boolean otpSession = SessionStore.isOtpSession(requireContext());
                loadedUser.email = ProfileEmailChangePolicy.resolveDisplayedEmail(
                        loadedUser.email,
                        effectiveEmail,
                        otpSession
                );
                if (!otpSession) {
                    syncEmailFromAuthIfNeeded(currentUser, loadedUser);
                }
                canSaveProfile = true;
                setLoading(false);
                bindUser(loadedUser);
            }

            @Override
            public void onFailure(@NonNull Call<UserRtdbDto> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                setLoading(false);
                UserRtdbDto cached = userProfileFileCache.load();
                if (cached != null) {
                    if (isBlank(cached.id))    cached.id    = effectiveUid;
                    if (isBlank(cached.email)) cached.email = effectiveEmail;
                    if (cached.preferences == null) cached.preferences = new ArrayList<>();
                    if (cached.legacyPreferences == null) cached.legacyPreferences = new ArrayList<>();
                    if (cached.phone == null) cached.phone = "";
                    if (cached.photoUrl == null) cached.photoUrl = "";
                    if (cached.name == null) cached.name = "";
                    loadedUser = cached;
                    canSaveProfile = false;
                    bindUser(loadedUser);
                } else {
                    loadedUser = buildFallbackUser(currentUser);
                    canSaveProfile = false;
                    bindUser(loadedUser);
                }
                Toast.makeText(requireContext(), R.string.profile_load_error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindUser(@NonNull UserRtdbDto user) {
        etName.setText(safe(user.name));
        currentPhotoUrl = safe(user.photoUrl).trim();
        loadProfilePhoto(currentPhotoUrl);
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
        String photoUrl = currentPhotoUrl.trim();
        String email = etEmail.getText().toString().trim();
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError(getString(R.string.error_email_invalid));
            etEmail.requestFocus();
            return;
        }
        String phone = etPhone.getText().toString().trim();
        List<String> preferences = collectSelectedPreferences();
        List<String> legacyPreferences = new ArrayList<>(preservedLegacyPreferences);

        boolean emailChanged = !sameValue(email, currentEmail);
        if (emailChanged) {
            boolean otpSession = SessionStore.isOtpSession(requireContext());
            if (!ProfileEmailChangePolicy.canRequestFirebaseEmailChange(otpSession)) {
                String message = getString(R.string.profile_email_password_login_required);
                etEmail.setError(message);
                etEmail.requestFocus();
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                return;
            }
        }

        setLoading(true);
        uploadProfilePhotoIfNeeded(photoUrl, new PhotoUploadCallback() {
            @Override
            public void onSuccess(@NonNull String persistedPhotoUrl) {
                if (!isAdded()) return;
                continueSavingProfile(
                        currentUser,
                        dto,
                        name,
                        appliedCurrentEmail,
                        email,
                        phone,
                        persistedPhotoUrl,
                        preferences,
                        legacyPreferences,
                        emailChanged
                );
            }

            @Override
            public void onFailure(@NonNull Exception error) {
                if (!isAdded()) return;
                Log.e(TAG, "Could not upload profile photo", error);
                continueSavingProfile(
                        currentUser,
                        dto,
                        name,
                        appliedCurrentEmail,
                        email,
                        phone,
                        photoUrl,
                        preferences,
                        legacyPreferences,
                        emailChanged
                );
            }
        });
    }

    private void continueSavingProfile(
            @NonNull FirebaseUser currentUser,
            @NonNull UserRtdbDto dto,
            @NonNull String name,
            @NonNull String currentEmail,
            @NonNull String requestedEmail,
            @NonNull String phone,
            @NonNull String photoUrl,
            @NonNull List<String> preferences,
            @NonNull List<String> legacyPreferences,
            boolean emailChanged
    ) {
        if (emailChanged) {
            preparePendingEmailChange(
                    currentUser,
                    dto,
                    name,
                    currentEmail,
                    requestedEmail,
                    phone,
                    photoUrl,
                    preferences,
                    legacyPreferences
            );
            return;
        }
        patchProfile(
                currentUser,
                dto,
                name,
                requestedEmail,
                phone,
                photoUrl,
                preferences,
                legacyPreferences
        );
    }

    private void uploadProfilePhotoIfNeeded(
            @NonNull String photoUrl,
            @NonNull PhotoUploadCallback callback
    ) {
        Object photoModel = resolvePhotoModel(photoUrl);
        if (!(photoModel instanceof File)) {
            callback.onSuccess(photoUrl);
            return;
        }

        File localPhoto = (File) photoModel;
        if (!localPhoto.exists()) {
            callback.onFailure(new IOException("Local profile photo does not exist"));
            return;
        }
        if (localPhoto.length() <= 0L || localPhoto.length() >= MAX_PROFILE_IMAGE_BYTES) {
            callback.onFailure(new IOException("Profile photo must be smaller than 5 MB"));
            return;
        }

        FirebaseUser authenticatedUser = FirebaseAuth.getInstance().getCurrentUser();
        if (authenticatedUser == null) {
            callback.onFailure(new IOException("No authenticated Firebase user for photo upload"));
            return;
        }

        String contentType = resolveLocalImageContentType(localPhoto);
        StorageMetadata metadata = new StorageMetadata.Builder()
                .setContentType(contentType)
                .build();
        StorageReference photoReference = FirebaseStorage.getInstance()
                .getReference()
                .child("profile_images")
                .child(authenticatedUser.getUid())
                .child("avatar");

        photoReference.putFile(Uri.fromFile(localPhoto), metadata)
                .addOnSuccessListener(snapshot -> photoReference.getDownloadUrl()
                        .addOnSuccessListener(downloadUri -> callback.onSuccess(downloadUri.toString()))
                        .addOnFailureListener(callback::onFailure))
                .addOnFailureListener(callback::onFailure);
    }

    @NonNull
    private String resolveLocalImageContentType(@NonNull File localPhoto) {
        String name = localPhoto.getName();
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            String extension = name.substring(dot + 1).toLowerCase(Locale.ROOT);
            String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (!isBlank(mimeType) && mimeType.startsWith("image/")) {
                return mimeType;
            }
        }
        return "image/jpeg";
    }

    private void preparePendingEmailChange(
            @NonNull FirebaseUser currentUser,
            @NonNull UserRtdbDto dto,
            @NonNull String name,
            @NonNull String confirmedEmail,
            @NonNull String requestedEmail,
            @NonNull String phone,
            @NonNull String photoUrl,
            @NonNull List<String> preferences,
            @NonNull List<String> legacyPreferences
    ) {
        String newEmailKey = ProfileEmailChangePolicy.emailToKey(requestedEmail);
        realtimeDatabaseApi.getUidByEmail(newEmailKey)
                .enqueue(new Callback<String>() {
                    @Override
                    public void onResponse(
                            @NonNull Call<String> call,
                            @NonNull Response<String> response
                    ) {
                        if (!isAdded()) return;
                        if (!response.isSuccessful()) {
                            finishPendingEmailChangeWithError(R.string.profile_email_update_error);
                            return;
                        }

                        String indexedUid = response.body();
                        if (!ProfileEmailChangePolicy.canUseEmailIndex(indexedUid, effectiveUid)) {
                            finishPendingEmailChangeWithError(R.string.profile_email_already_in_use);
                            return;
                        }

                        currentUser.verifyBeforeUpdateEmail(requestedEmail)
                                .addOnSuccessListener(unused -> {
                                    if (!isAdded()) return;
                                    persistProfileBeforeEmailChange(
                                            dto,
                                            name,
                                            confirmedEmail,
                                            phone,
                                            photoUrl,
                                            preferences,
                                            legacyPreferences
                                    );
                                })
                                .addOnFailureListener(e -> {
                                    if (!isAdded()) return;
                                    Log.e(TAG, "Email verify-before-update failed", e);
                                    setLoading(false);
                                    Toast.makeText(
                                            requireContext(),
                                            resolveEmailUpdateErrorMessage(e),
                                            Toast.LENGTH_LONG
                                    ).show();
                                });
                    }

                    @Override
                    public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                        if (!isAdded()) return;
                        Log.e(TAG, "Could not validate pending email index", t);
                        finishPendingEmailChangeWithError(R.string.profile_email_update_error);
                    }
                });
    }

    private void persistProfileBeforeEmailChange(
            @NonNull UserRtdbDto dto,
            @NonNull String name,
            @NonNull String confirmedEmail,
            @NonNull String phone,
            @NonNull String photoUrl,
            @NonNull List<String> preferences,
            @NonNull List<String> legacyPreferences
    ) {
        Map<String, Object> updates = ProfileEmailChangePolicy.buildProfileUpdates(
                effectiveUid,
                confirmedEmail,
                name,
                phone,
                photoUrl,
                preferences,
                legacyPreferences
        );
        realtimeDatabaseApi.patchUser(effectiveUid, updates)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                        if (!isAdded()) return;
                        if (!response.isSuccessful()) {
                            finishPendingEmailChangeWithError(R.string.profile_save_error);
                            return;
                        }

                        applyPersistedProfile(
                                dto,
                                name,
                                confirmedEmail,
                                phone,
                                photoUrl,
                                preferences,
                                legacyPreferences
                        );

                        finishPendingEmailChangeSuccessfully();
                    }

                    @Override
                    public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                        if (!isAdded()) return;
                        Log.e(TAG, "Could not persist profile before email change", t);
                        finishPendingEmailChangeWithError(R.string.profile_save_error);
                    }
                });
    }

    private void applyPersistedProfile(
            @NonNull UserRtdbDto dto,
            @NonNull String name,
            @NonNull String email,
            @NonNull String phone,
            @NonNull String photoUrl,
            @NonNull List<String> preferences,
            @NonNull List<String> legacyPreferences
    ) {
        dto.id = effectiveUid;
        dto.email = email;
        dto.name = name;
        dto.phone = phone;
        dto.photoUrl = photoUrl;
        dto.preferences = preferences;
        dto.legacyPreferences = legacyPreferences;
        loadedUser = dto;
        currentPhotoUrl = photoUrl;
        new Thread(() -> userProfileFileCache.save(dto)).start();
    }

    private void finishPendingEmailChangeSuccessfully() {
        if (!isAdded()) return;
        Toast.makeText(
                requireContext(),
                R.string.profile_email_verification_sent,
                Toast.LENGTH_LONG
        ).show();
        signOutAndNavigateToLogin();
    }

    private void finishPendingEmailChangeWithError(int messageResId) {
        if (!isAdded()) return;
        setLoading(false);
        Toast.makeText(requireContext(), messageResId, Toast.LENGTH_LONG).show();
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSave.setEnabled(!loading && canSaveProfile);
        etName.setEnabled(!loading);
        btnSelectProfileImage.setEnabled(!loading);
        etEmail.setEnabled(!loading);
        etPhone.setEnabled(!loading);
        chipPrefAventura.setEnabled(!loading);
        chipPrefCultura.setEnabled(!loading);
        chipPrefGastronomia.setEnabled(!loading);
        chipPrefNaturaleza.setEnabled(!loading);
        chipPrefRelax.setEnabled(!loading);
        btnOpenHistory.setEnabled(!loading);
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
        Map<String, Object> updates = ProfileEmailChangePolicy.buildProfileUpdates(
                effectiveUid,
                email,
                name,
                phone,
                photoUrl,
                preferences,
                legacyPreferences
        );

        realtimeDatabaseApi.patchUser(effectiveUid, updates)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                        if (!isAdded()) return;
                        setLoading(false);
                        if (!response.isSuccessful()) {
                            Toast.makeText(requireContext(), R.string.profile_save_error, Toast.LENGTH_LONG).show();
                            return;
                        }
                        applyPersistedProfile(
                                dto,
                                name,
                                email,
                                phone,
                                photoUrl,
                                preferences,
                                legacyPreferences
                        );
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
        String previousEmail = safe(dto.email).trim();
        if (isBlank(authEmail) || sameValue(authEmail, previousEmail)) {
            return;
        }

        dto.email = authEmail;
        effectiveEmail = authEmail;
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("email", authEmail);
        realtimeDatabaseApi.patchUser(effectiveUid, updates)
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
        syncEmailIndexIfNeeded(previousEmail, authEmail);
    }

    private void syncEmailIndexIfNeeded(@Nullable String previousEmail, @NonNull String newEmail) {
        if (isBlank(newEmail)) return;
        String newKey = ProfileEmailChangePolicy.emailToKey(newEmail);
        realtimeDatabaseApi.putEmailIndex(newKey, effectiveUid)
                .enqueue(new Callback<String>() {
                    @Override
                    public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                        if (!response.isSuccessful()) {
                            Log.w(TAG, "Could not sync new email index: " + response.code());
                            return;
                        }
                        deleteOldEmailIndex(previousEmail, newEmail);
                    }

                    @Override
                    public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                        Log.w(TAG, "Could not sync new email index", t);
                    }
                });
    }

    private void deleteOldEmailIndex(@Nullable String previousEmail, @NonNull String newEmail) {
        String oldEmail = safe(previousEmail).trim();
        if (isBlank(oldEmail) || sameValue(oldEmail, newEmail)) {
            return;
        }
        realtimeDatabaseApi.deleteEmailIndex(ProfileEmailChangePolicy.emailToKey(oldEmail))
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                        if (!response.isSuccessful()) {
                            Log.w(TAG, "Could not delete old email index: " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                        Log.w(TAG, "Could not delete old email index", t);
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

    private void showPhotoSourceDialog() {
        if (!isAdded()) return;
        String[] options = {
                getString(R.string.profile_photo_option_gallery),
                getString(R.string.profile_photo_option_camera)
        };
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.profile_photo_source_title)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        requestGalleryAccess();
                        return;
                    }
                    requestCameraAccess();
                })
                .show();
    }

    private void requestGalleryAccess() {
        if (!isAdded()) return;
        String permission = resolveGalleryPermission();
        if (ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED) {
            openGallery();
            return;
        }
        requestGalleryPermissionLauncher.launch(permission);
    }

    @NonNull
    private String resolveGalleryPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return Manifest.permission.READ_MEDIA_IMAGES;
        }
        return Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    private void requestCameraAccess() {
        if (!isAdded()) return;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            openCamera();
            return;
        }
        requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
    }

    private void openGallery() {
        pickProfileImageLauncher.launch("image/*");
    }

    private void openCamera() {
        try {
            File captureFile = createPendingCameraPhotoFile(requireContext().getApplicationContext());
            Uri captureUri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    captureFile
            );
            pendingCameraPhotoFile = captureFile;
            pendingCameraPhotoUri = captureUri;
            takeProfilePhotoLauncher.launch(captureUri);
        } catch (IOException e) {
            Log.e(TAG, "Could not prepare camera output file", e);
            Toast.makeText(requireContext(), R.string.profile_photo_store_error, Toast.LENGTH_LONG).show();
        }
    }

    private void handleSelectedProfileImage(@Nullable Uri selectedImageUri) {
        if (selectedImageUri == null || !isAdded()) return;
        Context appContext = requireContext().getApplicationContext();
        new Thread(() -> {
            try {
                String storedPhotoUrl = copyProfileImageToInternalStorage(appContext, selectedImageUri);
                applyStoredPhotoUrl(storedPhotoUrl);
            } catch (IOException e) {
                Log.e(TAG, "Could not persist selected profile image", e);
                showPhotoStoreError();
            }
        }).start();
    }

    private void handleCapturedProfilePhoto(boolean wasSaved) {
        if (!isAdded()) return;
        File capturedFile = pendingCameraPhotoFile;
        if (!wasSaved || capturedFile == null || !capturedFile.exists()) {
            clearPendingCameraPhoto(true);
            return;
        }
        Context appContext = requireContext().getApplicationContext();
        new Thread(() -> {
            try {
                String storedPhotoUrl = persistCapturedPhotoFile(appContext, capturedFile);
                clearPendingCameraPhoto(true);
                applyStoredPhotoUrl(storedPhotoUrl);
            } catch (IOException e) {
                Log.e(TAG, "Could not persist captured profile image", e);
                clearPendingCameraPhoto(true);
                showPhotoStoreError();
            }
        }).start();
    }

    private void applyStoredPhotoUrl(@NonNull String storedPhotoUrl) {
        FragmentActivity activity = getActivity();
        if (activity == null) return;
        activity.runOnUiThread(() -> {
            if (!isAdded()) return;
            currentPhotoUrl = storedPhotoUrl;
            loadProfilePhoto(currentPhotoUrl);
            Toast.makeText(requireContext(), R.string.profile_photo_selected, Toast.LENGTH_SHORT).show();
        });
    }

    private void showPhotoStoreError() {
        FragmentActivity activity = getActivity();
        if (activity == null) return;
        activity.runOnUiThread(() -> {
            if (!isAdded()) return;
            Toast.makeText(requireContext(), R.string.profile_photo_store_error, Toast.LENGTH_LONG).show();
        });
    }

    @NonNull
    private String copyProfileImageToInternalStorage(@NonNull Context context, @NonNull Uri sourceUri)
            throws IOException {
        File directory = ensureProfileImageDirectory(context);
        String extension = resolveImageExtension(context.getContentResolver(), sourceUri);
        File destination = new File(directory, buildProfileImageFileName(extension));

        try (InputStream inputStream = context.getContentResolver().openInputStream(sourceUri)) {
            if (inputStream == null) {
                throw new IOException("Selected image stream is null");
            }
            try (OutputStream outputStream = new FileOutputStream(destination, false)) {
                byte[] buffer = new byte[8 * 1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();
            }
        }

        deleteOtherStoredProfileImages(directory, destination);
        return Uri.fromFile(destination).toString();
    }

    @NonNull
    private String persistCapturedPhotoFile(@NonNull Context context, @NonNull File capturedFile)
            throws IOException {
        File directory = ensureProfileImageDirectory(context);
        File destination = new File(directory, buildProfileImageFileName("jpg"));

        try (InputStream inputStream = new FileInputStream(capturedFile);
             OutputStream outputStream = new FileOutputStream(destination, false)) {
            byte[] buffer = new byte[8 * 1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();
        }

        deleteOtherStoredProfileImages(directory, destination);
        return Uri.fromFile(destination).toString();
    }

    @NonNull
    private File ensureProfileImageDirectory(@NonNull Context context) throws IOException {
        File directory = new File(context.getFilesDir(), PROFILE_IMAGE_DIR);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Could not create internal profile image directory");
        }
        return directory;
    }

    @NonNull
    private File createPendingCameraPhotoFile(@NonNull Context context) throws IOException {
        File directory = ensureProfileImageDirectory(context);
        File captureFile = new File(directory, buildCameraCaptureFileName("jpg"));
        if (captureFile.exists() && !captureFile.delete()) {
            throw new IOException("Could not replace pending camera photo file");
        }
        if (!captureFile.createNewFile()) {
            throw new IOException("Could not create pending camera photo file");
        }
        return captureFile;
    }

    @NonNull
    private String resolveImageExtension(@NonNull ContentResolver resolver, @NonNull Uri sourceUri) {
        String mimeType = resolver.getType(sourceUri);
        if (!isBlank(mimeType)) {
            String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
            if (!isBlank(extension)) {
                return extension.toLowerCase(Locale.ROOT);
            }
        }

        String path = sourceUri.getLastPathSegment();
        if (!isBlank(path)) {
            int lastDot = path.lastIndexOf('.');
            if (lastDot >= 0 && lastDot < path.length() - 1) {
                return path.substring(lastDot + 1).toLowerCase(Locale.ROOT);
            }
        }
        return "jpg";
    }

    @NonNull
    private String buildProfileImageFileName(@NonNull String extension) {
        String safeUid = safe(effectiveUid).trim().replaceAll("[^a-zA-Z0-9._-]", "_");
        if (safeUid.isEmpty()) {
            safeUid = "user";
        }
        return PROFILE_IMAGE_FILE_PREFIX + safeUid + "." + extension;
    }

    @NonNull
    private String buildCameraCaptureFileName(@NonNull String extension) {
        String safeUid = safe(effectiveUid).trim().replaceAll("[^a-zA-Z0-9._-]", "_");
        if (safeUid.isEmpty()) {
            safeUid = "user";
        }
        return PROFILE_IMAGE_FILE_PREFIX + safeUid + PROFILE_IMAGE_CAPTURE_SUFFIX + "." + extension;
    }

    private void clearPendingCameraPhoto(boolean deleteFile) {
        File pendingFile = pendingCameraPhotoFile;
        pendingCameraPhotoUri = null;
        pendingCameraPhotoFile = null;
        if (!deleteFile || pendingFile == null || !pendingFile.exists()) return;
        if (!pendingFile.delete()) {
            Log.w(TAG, "Could not delete pending camera photo file: " + pendingFile.getAbsolutePath());
        }
    }

    private void deleteOtherStoredProfileImages(@NonNull File directory, @NonNull File keepFile) {
        String prefix = PROFILE_IMAGE_FILE_PREFIX + safe(effectiveUid).trim().replaceAll("[^a-zA-Z0-9._-]", "_");
        if (prefix.equals(PROFILE_IMAGE_FILE_PREFIX)) {
            prefix = PROFILE_IMAGE_FILE_PREFIX + "user";
        }
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (!file.isFile() || file.equals(keepFile)) continue;
            if (!file.getName().startsWith(prefix + ".")) continue;
            if (!file.delete()) {
                Log.w(TAG, "Could not delete old internal profile image: " + file.getAbsolutePath());
            }
        }
    }

    @Nullable
    private Object resolvePhotoModel(@Nullable String photoUrl) {
        String safePhotoUrl = safe(photoUrl).trim();
        if (safePhotoUrl.isEmpty()) {
            return null;
        }

        Uri uri = Uri.parse(safePhotoUrl);
        String scheme = uri.getScheme();
        if (scheme == null || scheme.trim().isEmpty()) {
            File localFile = new File(safePhotoUrl);
            return localFile.exists() ? localFile : null;
        }
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            return safePhotoUrl;
        }
        if ("file".equalsIgnoreCase(scheme)) {
            String path = uri.getPath();
            if (isBlank(path)) {
                return null;
            }
            File localFile = new File(path);
            return localFile.exists() ? localFile : null;
        }
        return uri;
    }

    private void loadProfilePhoto(@Nullable String photoUrl) {
        if (!isAdded()) return;
        Object photoModel = resolvePhotoModel(photoUrl);
        if (photoModel == null) {
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
        RequestBuilder<Drawable> request = Glide.with(this).load(photoModel);
        if (photoModel instanceof File) {
            File localPhoto = (File) photoModel;
            request = request.signature(new ObjectKey(
                    localPhoto.getAbsolutePath()
                            + ":" + localPhoto.lastModified()
                            + ":" + localPhoto.length()
            ));
        }
        request
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
        List<ReservationItem> reservations = ReservationItem.buildList(user, java.util.Collections.emptyMap());
        if (!reservations.isEmpty()) {
            int confirmed = 0;
            int finished = 0;
            for (ReservationItem item : reservations) {
                String normalized = ReservationStatus.normalize(item.status);
                if (ReservationStatus.CONFIRMED.equals(normalized)) confirmed++;
                if (ReservationStatus.FINISHED.equals(normalized)) finished++;
            }
            tvReservedCount.setText(String.valueOf(confirmed));
            tvCompletedCount.setText(String.valueOf(finished));
            return;
        }

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

    // ─── Biometría ────────────────────────────────────────────────────────────

    /** Configura el texto y el click del botón según el estado del hardware y la preferencia. */
    private void setupBiometricButton() {
        int status = BiometricManager.from(requireContext())
                .canAuthenticate(Authenticators.BIOMETRIC_STRONG | Authenticators.DEVICE_CREDENTIAL);

        switch (status) {
            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
                btnBiometric.setEnabled(false);
                btnBiometric.setText(getString(R.string.biometric_unavailable));
                return;

            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                // Hardware presente pero sin huella/PIN registrado → ofrecer ir a ajustes
                btnBiometric.setText(getString(R.string.biometric_enroll_first));
                btnBiometric.setOnClickListener(v -> {
                    android.content.Intent intent = new android.content.Intent(
                            android.provider.Settings.ACTION_BIOMETRIC_ENROLL);
                    startActivity(intent);
                });
                return;

            default:
                // BIOMETRIC_SUCCESS o HW_UNAVAILABLE transitorio → botón normal
                break;
        }

        // Texto según preferencia guardada
        refreshBiometricButtonText();

        btnBiometric.setOnClickListener(v -> {
            if (BiometricPrefs.isEnabled(requireContext())) {
                BiometricPrefs.setEnabled(requireContext(), false);
                refreshBiometricButtonText();
                Toast.makeText(requireContext(),
                        getString(R.string.biometric_disabled_ok), Toast.LENGTH_SHORT).show();
            } else {
                showBiometricSetupPrompt();
            }
        });
    }

    private void refreshBiometricButtonText() {
        btnBiometric.setText(BiometricPrefs.isEnabled(requireContext())
                ? getString(R.string.biometric_disable)
                : getString(R.string.biometric_enable));
    }

    /** Muestra el prompt biométrico del sistema para verificar antes de activar. */
    private void showBiometricSetupPrompt() {
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
                        // Si antes lo había rechazado en el login, limpiamos ese flag
                        BiometricPrefs.setDeclined(requireContext(), false);
                        refreshBiometricButtonText();
                        Toast.makeText(requireContext(),
                                getString(R.string.biometric_enabled_ok), Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        // Prompt sigue abierto — el sistema gestiona los reintentos
                    }

                    @Override
                    public void onAuthenticationError(int errorCode,
                                                      @NonNull CharSequence errString) {
                        if (!isAdded()) return;
                        // Mostramos el mensaje del sistema para que sea descriptivo
                        Log.w(TAG, "Biometric setup error " + errorCode + ": " + errString);
                        Toast.makeText(requireContext(), errString, Toast.LENGTH_LONG).show();
                    }
                }).authenticate(info);
    }

    private void signOutAndNavigateToLogin() {
        SessionStore.clear(requireContext());
        FirebaseAuth.getInstance().signOut();
        View currentView = getView();
        if (currentView == null) return;
        NavOptions navOptions = new NavOptions.Builder()
                .setPopUpTo(R.id.exploreFragment, true)
                .build();
        Navigation.findNavController(currentView).navigate(R.id.loginFragment, null, navOptions);
    }

    private void logout(@NonNull View view) {
        signOutAndNavigateToLogin();
    }
}
