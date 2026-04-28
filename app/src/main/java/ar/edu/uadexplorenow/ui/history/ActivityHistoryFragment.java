package ar.edu.uadexplorenow.ui.history;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import ar.edu.uadexplorenow.R;
import ar.edu.uadexplorenow.data.SessionStore;
import ar.edu.uadexplorenow.data.local.db.CachedReservationDao;
import ar.edu.uadexplorenow.data.local.db.CachedReservationEntity;
import ar.edu.uadexplorenow.data.model.ActivityRtdbMapper;
import ar.edu.uadexplorenow.data.model.UserRtdbDto;
import ar.edu.uadexplorenow.data.network.RealtimeDatabaseApi;
import ar.edu.uadexplorenow.domain.ActivityDetail;
import ar.edu.uadexplorenow.domain.ReservationItem;
import ar.edu.uadexplorenow.ui.reservations.ReservationDetailFragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.gson.Gson;
import com.google.gson.JsonElement;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class ActivityHistoryFragment extends Fragment {

    private static final String DESTINATION_ALL = "";
    private static final DateTimeFormatter DATE_BUTTON_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", new Locale("es", "AR"));

    @Inject
    RealtimeDatabaseApi realtimeDatabaseApi;
    @Inject
    Gson gson;
    @Inject
    CachedReservationDao cachedReservationDao;

    private final ExecutorService cacheExecutor = Executors.newSingleThreadExecutor();

    private final List<ReservationItem> allItems = new ArrayList<>();
    private final List<String> destinationValues = new ArrayList<>();
    private final ActivityHistoryAdapter adapter = new ActivityHistoryAdapter();

    private EditText etSearch;
    private Spinner spinnerDestination;
    private MaterialButton btnDateRange;
    private MaterialButton btnClearFilters;
    private TextView tvResults;
    private TextView tvEmpty;
    private RecyclerView rvHistory;
    private ProgressBar progress;
    private BottomNavigationView bottomNav;

    private String searchQuery = "";
    private String filterDestination = DESTINATION_ALL;
    @Nullable
    private LocalDate filterStartDate;
    @Nullable
    private LocalDate filterEndDate;

    @Nullable
    private UserRtdbDto loadedUser;
    private Map<String, ActivityDetail> detailById = java.util.Collections.emptyMap();
    private boolean userRequestDone;
    private boolean activitiesRequestDone;
    private boolean hasShownPartialError;
    private boolean hasLoadedOnce;
    private boolean userNetworkFailed;
    @Nullable
    private String currentUid;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_activity_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            NavOptions navOptions = new NavOptions.Builder()
                    .setPopUpTo(R.id.activityHistoryFragment, true)
                    .build();
            Navigation.findNavController(view).navigate(R.id.loginFragment, null, navOptions);
            return;
        }

        ImageButton btnBack = view.findViewById(R.id.btnBack);
        etSearch = view.findViewById(R.id.etSearch);
        spinnerDestination = view.findViewById(R.id.spinnerDestination);
        btnDateRange = view.findViewById(R.id.btnDateRange);
        btnClearFilters = view.findViewById(R.id.btnClearFilters);
        tvResults = view.findViewById(R.id.tvResults);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        rvHistory = view.findViewById(R.id.rvHistory);
        progress = view.findViewById(R.id.progress);
        bottomNav = view.findViewById(R.id.bottomNav);

        rvHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvHistory.setAdapter(adapter);
        adapter.setOnItemClick(this::openReservationDetail);

        btnBack.setOnClickListener(v -> Navigation.findNavController(view).popBackStack());
        btnDateRange.setOnClickListener(v -> openDateRangePicker());
        btnClearFilters.setOnClickListener(v -> clearFilters());

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                searchQuery = s != null ? s.toString().trim() : "";
                applyFilters();
            }
        });

        spinnerDestination.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View selectedView, int position, long id) {
                if (position < 0 || position >= destinationValues.size()) {
                    filterDestination = DESTINATION_ALL;
                } else {
                    filterDestination = destinationValues.get(position);
                }
                applyFilters();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                filterDestination = DESTINATION_ALL;
                applyFilters();
            }
        });

        bottomNav.setSelectedItemId(R.id.nav_list);
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                navigateToExplore(view);
                return true;
            }
            if (itemId == R.id.nav_favorites) {
                Navigation.findNavController(view)
                        .navigate(R.id.action_activityHistoryFragment_to_favoritesFragment);
                return true;
            }
            if (itemId == R.id.nav_list) {
                rvHistory.smoothScrollToPosition(0);
                return true;
            }
            if (itemId == R.id.nav_profile) {
                Navigation.findNavController(view)
                        .navigate(R.id.action_activityHistoryFragment_to_profileFragment);
                return true;
            }
            return false;
        });

        updateDateButton();
        setupDestinationSpinner(new ArrayList<>());
        loadReservations(currentUser);
    }

    @Override
    public void onResume() {
        super.onResume();
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (hasLoadedOnce && userRequestDone && activitiesRequestDone && currentUser != null && isAdded()) {
            loadReservations(currentUser);
        }
    }

    private void loadReservations(@NonNull FirebaseUser currentUser) {
        hasLoadedOnce = true;
        setLoading(true);
        hasShownPartialError = false;
        userRequestDone = false;
        activitiesRequestDone = false;
        userNetworkFailed = false;

        currentUid = SessionStore.getEffectiveUid(requireContext(), currentUser);

        realtimeDatabaseApi.getUser(currentUid).enqueue(new Callback<UserRtdbDto>() {
            @Override
            public void onResponse(@NonNull Call<UserRtdbDto> call, @NonNull Response<UserRtdbDto> response) {
                if (!isAdded()) return;
                loadedUser = response.isSuccessful() ? response.body() : null;
                userRequestDone = true;
                maybeRenderReservations();
            }

            @Override
            public void onFailure(@NonNull Call<UserRtdbDto> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                loadedUser = null;
                userNetworkFailed = true;
                userRequestDone = true;
                maybeRenderReservations();
            }
        });

        realtimeDatabaseApi.getActivities().enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(@NonNull Call<JsonElement> call, @NonNull Response<JsonElement> response) {
                if (!isAdded()) return;
                detailById = response.isSuccessful() && response.body() != null
                        ? ActivityRtdbMapper.toActivityDetails(response.body(), gson)
                        : java.util.Collections.emptyMap();
                activitiesRequestDone = true;
                maybeRenderReservations();
            }

            @Override
            public void onFailure(@NonNull Call<JsonElement> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                detailById = java.util.Collections.emptyMap();
                activitiesRequestDone = true;
                maybeRenderReservations();
            }
        });
    }

    private void loadReservationsFromCache(@NonNull String uid) {
        cacheExecutor.execute(() -> {
            List<CachedReservationEntity> cached = cachedReservationDao.getAllByUid(uid);
            android.app.Activity act = getActivity();
            if (act == null) return;
            act.runOnUiThread(() -> {
                if (!isAdded()) return;
                setLoading(false);
                if (cached.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.history_load_error, Toast.LENGTH_LONG).show();
                    applyFilters();
                    return;
                }
                allItems.clear();
                allItems.addAll(CachedReservationEntity.toList(cached));
                setupDestinationSpinner(allItems);
                applyFilters();
                showOfflineBanner();
                Toast.makeText(requireContext(), R.string.history_offline_cache, Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void saveReservationsToCache(@NonNull String uid, @NonNull List<ReservationItem> items) {
        final List<CachedReservationEntity> entities = CachedReservationEntity.fromList(uid, items);
        cacheExecutor.execute(() -> {
            cachedReservationDao.deleteByUid(uid);
            cachedReservationDao.insertAll(entities);
        });
    }

    private void showOfflineBanner() {
        View view = getView();
        if (view == null) return;
        View banner = view.findViewById(R.id.offlineBanner);
        if (banner != null) {
            banner.setVisibility(View.VISIBLE);
        }
    }

    private void maybeRenderReservations() {
        if (!userRequestDone || !activitiesRequestDone || !isAdded()) {
            return;
        }

        if (userNetworkFailed && currentUid != null) {
            loadReservationsFromCache(currentUid);
            return;
        }

        setLoading(false);
        allItems.clear();
        allItems.addAll(ReservationItem.buildList(loadedUser, detailById));
        setupDestinationSpinner(allItems);
        applyFilters();

        if (!allItems.isEmpty() && currentUid != null) {
            saveReservationsToCache(currentUid, new ArrayList<>(allItems));
        }

        if (loadedUser == null && !hasShownPartialError) {
            hasShownPartialError = true;
            Toast.makeText(requireContext(), R.string.history_load_error, Toast.LENGTH_LONG).show();
        } else if (detailById.isEmpty() && loadedUser != null && !allItems.isEmpty() && !hasShownPartialError) {
            hasShownPartialError = true;
            Toast.makeText(requireContext(), R.string.history_partial_load_error, Toast.LENGTH_LONG).show();
        }
    }

    private void setupDestinationSpinner(@NonNull List<ReservationItem> items) {
        List<String> labels = new ArrayList<>();
        destinationValues.clear();
        labels.add(getString(R.string.history_filter_destination_all));
        destinationValues.add(DESTINATION_ALL);

        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (ReservationItem item : items) {
            if (!item.destination.trim().isEmpty()) {
                values.add(item.destination.trim());
            }
        }
        labels.addAll(values);
        destinationValues.addAll(values);

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                requireContext(), R.layout.item_history_spinner, labels);
        spinnerAdapter.setDropDownViewResource(R.layout.item_history_spinner_dropdown);
        spinnerDestination.setAdapter(spinnerAdapter);

        int selected = 0;
        if (!filterDestination.isEmpty()) {
            for (int i = 0; i < destinationValues.size(); i++) {
                if (filterDestination.equalsIgnoreCase(destinationValues.get(i))) {
                    selected = i;
                    break;
                }
            }
        }
        spinnerDestination.setSelection(selected, false);
    }

    private void applyFilters() {
        if (!isAdded()) {
            return;
        }

        List<ReservationItem> filtered = new ArrayList<>();
        for (ReservationItem item : allItems) {
            if (!filterDestination.isEmpty()
                    && !item.destination.trim().equalsIgnoreCase(filterDestination.trim())) {
                continue;
            }
            if ((filterStartDate != null || filterEndDate != null) && item.localDate() == null) {
                continue;
            }
            if (filterStartDate != null && item.localDate() != null
                    && item.localDate().isBefore(filterStartDate)) {
                continue;
            }
            if (filterEndDate != null && item.localDate() != null
                    && item.localDate().isAfter(filterEndDate)) {
                continue;
            }
            if (!item.matchesQuery(searchQuery)) {
                continue;
            }
            filtered.add(item);
        }

        adapter.submit(filtered);
        tvResults.setText(getString(R.string.history_results_count, filtered.size(), allItems.size()));
        tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        rvHistory.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);

        if (allItems.isEmpty()) {
            tvEmpty.setText(R.string.history_empty_data);
        } else {
            tvEmpty.setText(R.string.history_empty_filters);
        }
    }

    private void clearFilters() {
        etSearch.setText("");
        filterDestination = DESTINATION_ALL;
        filterStartDate = null;
        filterEndDate = null;
        updateDateButton();
        spinnerDestination.setSelection(0, false);
        applyFilters();
    }

    private void updateDateButton() {
        if (filterStartDate == null || filterEndDate == null) {
            btnDateRange.setText(R.string.history_filter_date_any);
            return;
        }
        btnDateRange.setText(getString(
                R.string.history_filter_date_range_fmt,
                filterStartDate.format(DATE_BUTTON_FORMAT),
                filterEndDate.format(DATE_BUTTON_FORMAT)));
    }

    private void openDateRangePicker() {
        MaterialDatePicker.Builder<Pair<Long, Long>> builder =
                MaterialDatePicker.Builder.dateRangePicker()
                        .setTitleText(R.string.history_filter_date_picker_title);

        if (filterStartDate != null && filterEndDate != null) {
            builder.setSelection(new Pair<>(toUtcMillis(filterStartDate), toUtcMillis(filterEndDate)));
        }

        MaterialDatePicker<Pair<Long, Long>> picker = builder.build();
        picker.addOnPositiveButtonClickListener(selection -> {
            if (selection == null || selection.first == null || selection.second == null) {
                return;
            }
            filterStartDate = fromUtcMillis(selection.first);
            filterEndDate = fromUtcMillis(selection.second);
            updateDateButton();
            applyFilters();
        });
        picker.show(getChildFragmentManager(), "history_date_range");
    }

    private void openReservationDetail(@NonNull ReservationItem item) {
        if (item.reservationId.isEmpty()) {
            Toast.makeText(requireContext(), R.string.history_detail_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        Bundle args = new Bundle();
        args.putString(ReservationDetailFragment.ARG_RESERVATION_ID, item.reservationId);
        Navigation.findNavController(requireView())
                .navigate(R.id.action_activityHistoryFragment_to_reservationDetailFragment, args);
    }

    private void navigateToExplore(@NonNull View view) {
        NavController navController = Navigation.findNavController(view);
        boolean popped = navController.popBackStack(R.id.exploreFragment, false);
        if (!popped) {
            navController.navigate(R.id.exploreFragment);
        }
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        rvHistory.setVisibility(loading ? View.GONE : rvHistory.getVisibility());
        tvEmpty.setVisibility(loading ? View.GONE : tvEmpty.getVisibility());
        btnDateRange.setEnabled(!loading);
        btnClearFilters.setEnabled(!loading);
        spinnerDestination.setEnabled(!loading);
        etSearch.setEnabled(!loading);
    }

    private static long toUtcMillis(@NonNull LocalDate date) {
        return date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    @NonNull
    private static LocalDate fromUtcMillis(long millis) {
        return Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate();
    }
}
