package ar.edu.uadexplorenow.ui.explore;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavOptions;

import javax.inject.Inject;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import ar.edu.uadexplorenow.R;
import ar.edu.uadexplorenow.data.FavoritesRepository;
import ar.edu.uadexplorenow.data.SessionStore;
import ar.edu.uadexplorenow.data.model.ActivityRtdbMapper;
import ar.edu.uadexplorenow.data.model.FavoriteRtdbDto;
import ar.edu.uadexplorenow.data.model.UserRtdbDto;
import ar.edu.uadexplorenow.data.network.RealtimeDatabaseApi;
import ar.edu.uadexplorenow.domain.ActivityItem;
import ar.edu.uadexplorenow.ui.auth.LoginFragment;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.slider.RangeSlider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.gson.Gson;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ExploreFragment extends Fragment {

    @Inject
    RealtimeDatabaseApi realtimeDatabaseApi;
    @Inject
    Gson gson;
    @Inject
    FavoritesRepository favoritesRepository;

    private static final String TAG_ALL = "";
    private static final String PREF_AVENTURA = "aventura";
    private static final String PREF_CULTURA = "cultura";
    private static final String PREF_GASTRONOMIA = "gastronomia";
    private static final String PREF_NATURALEZA = "naturaleza";
    private static final String PREF_RELAX = "relax";

    private static final String[] DAY_ORDER_NORM = {
            "lunes", "martes", "miercoles", "jueves", "viernes", "sabado", "domingo"
    };

    private final List<ActivityItem> allActivities = new ArrayList<>();
    private final LinkedHashSet<String> userPreferenceKeys = new LinkedHashSet<>();
    private final Map<String, FavoriteRtdbDto> favoriteByActivityId = new LinkedHashMap<>();
    private String effectiveUid = "";

    private EditText etSearch;
    private RecyclerView rvFeatured;
    private RecyclerView rvAll;
    private ProgressBar progress;
    private TextView tvSectionFeatured;
    private TextView tvSectionAll;
    private TextView tvEmpty;
    private BottomNavigationView bottomNav;
    private NestedScrollView scrollContent;
    private ImageButton btnProfile;

    private final FeaturedActivitiesAdapter featuredAdapter = new FeaturedActivitiesAdapter();
    private final AllActivitiesAdapter allAdapter = new AllActivitiesAdapter();

    private String searchQuery = "";

    private String filterDestination = TAG_ALL;
    private String filterCategory = TAG_ALL;
    private String filterDay = TAG_ALL;
    private double filterPriceMin = 0;
    private double filterPriceMax = 0;

    private double catalogMaxPrice = 80_000;
    private boolean hasUsefulUserPreferences;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.activity_explore, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            NavOptions navOptions = new NavOptions.Builder()
                    .setPopUpTo(R.id.exploreFragment, true)
                    .build();
            Navigation.findNavController(view).navigate(R.id.loginFragment, null, navOptions);
            return;
        }

        scrollContent = view.findViewById(R.id.scrollContent);
        etSearch = view.findViewById(R.id.etSearch);
        rvFeatured = view.findViewById(R.id.rvFeatured);
        rvAll = view.findViewById(R.id.rvAll);
        progress = view.findViewById(R.id.progress);
        tvSectionFeatured = view.findViewById(R.id.tvSectionFeatured);
        tvSectionAll = view.findViewById(R.id.tvSectionAll);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        bottomNav = view.findViewById(R.id.bottomNav);
        btnProfile = view.findViewById(R.id.btnProfile);
        ImageButton btnFilter = view.findViewById(R.id.btnFilter);

        rvFeatured.setLayoutManager(
                new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        rvFeatured.setAdapter(featuredAdapter);

        rvAll.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvAll.setAdapter(allAdapter);
        rvAll.setItemAnimator(null);

        Consumer<String> openDetail = id -> {
            Bundle args = new Bundle();
            args.putString(ActivityDetailFragment.ARG_ACTIVITY_ID, id);
            Navigation.findNavController(view).navigate(
                    R.id.action_exploreFragment_to_activityDetailFragment, args);
        };
        featuredAdapter.setOnItemClick(openDetail);
        allAdapter.setOnItemClick(openDetail);
        Consumer<ActivityItem> onFavorite = this::onFavoriteToggle;
        featuredAdapter.setOnFavoriteClick(onFavorite);
        allAdapter.setOnFavoriteClick(onFavorite);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                searchQuery = s != null ? s.toString().trim().toLowerCase(Locale.getDefault()) : "";
                applyFilters();
            }
        });

        btnFilter.setOnClickListener(v -> showFilterBottomSheet());

        btnProfile.setOnClickListener(v -> navigateToProfile(view));

        bottomNav.setSelectedItemId(R.id.nav_home);
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                if (scrollContent != null) {
                    scrollContent.post(() -> scrollContent.smoothScrollTo(0, 0));
                }
                return true;
            }
            if (itemId == R.id.nav_favorites) {
                Navigation.findNavController(view)
                        .navigate(R.id.action_exploreFragment_to_favoritesFragment);
                return true;
            }
            if (itemId == R.id.nav_list) {
                Navigation.findNavController(view)
                        .navigate(R.id.action_exploreFragment_to_activityHistoryFragment);
                return true;
            }
            if (itemId == R.id.nav_profile) {
                navigateToProfile(view);
                return true;
            }
            return false;
        });

        effectiveUid = SessionStore.getEffectiveUid(
                requireContext(), FirebaseAuth.getInstance().getCurrentUser());
        loadUserPreferences(FirebaseAuth.getInstance().getCurrentUser().getUid());
        loadActivities();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (FirebaseAuth.getInstance().getCurrentUser() != null && !effectiveUid.isEmpty()) {
            loadFavoritesFromServer();
        }
    }

    private void loadFavoritesFromServer() {
        favoritesRepository.loadAll(effectiveUid, map -> {
            if (!isAdded()) return;
            favoriteByActivityId.clear();
            favoriteByActivityId.putAll(map);
            applyFilters();
        });
    }

    private void onFavoriteToggle(@NonNull ActivityItem activity) {
        if (favoriteByActivityId.containsKey(activity.id)) {
            favoritesRepository.removeFavorite(effectiveUid, activity.id, err -> {
                if (!isAdded()) return;
                if (err == null) {
                    favoriteByActivityId.remove(activity.id);
                    applyFilters();
                } else {
                    Toast.makeText(requireContext(), R.string.favorites_load_error, Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            favoritesRepository.addFavorite(effectiveUid, activity, err -> {
                if (!isAdded()) return;
                if (err == null) {
                    favoriteByActivityId.put(
                            activity.id, new FavoriteRtdbDto(activity.price, activity.availableSpots));
                    applyFilters();
                } else {
                    Toast.makeText(requireContext(), R.string.favorites_load_error, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void loadActivities() {
        progress.setVisibility(View.VISIBLE);
        realtimeDatabaseApi.getActivities().enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(@NonNull Call<JsonElement> call, @NonNull Response<JsonElement> response) {
                if (!isAdded()) return;
                progress.setVisibility(View.GONE);
                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(requireContext(), R.string.explore_load_error, Toast.LENGTH_LONG).show();
                    return;
                }
                allActivities.clear();
                allActivities.addAll(ActivityRtdbMapper.toActivityItems(response.body(), gson));
                Collections.sort(allActivities, (a, b) -> a.name.compareToIgnoreCase(b.name));
                recomputeCatalogMaxPrice();
                applyFilters();
            }

            @Override
            public void onFailure(@NonNull Call<JsonElement> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                progress.setVisibility(View.GONE);
                Toast.makeText(requireContext(), R.string.explore_load_error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void recomputeCatalogMaxPrice() {
        double maxP = 0;
        for (ActivityItem a : allActivities) {
            if (a.price > maxP) maxP = a.price;
        }
        if (maxP < 1) {
            catalogMaxPrice = 50_000;
        } else {
            catalogMaxPrice = Math.ceil(maxP / 1000.0) * 1000.0;
        }
        if (filterPriceMax <= 0 || filterPriceMax > catalogMaxPrice) {
            filterPriceMax = catalogMaxPrice;
        }
        filterPriceMin = Math.min(filterPriceMin, catalogMaxPrice);
    }

    private void applyFilters() {
        Set<String> favIds = new HashSet<>(favoriteByActivityId.keySet());
        featuredAdapter.setFavoriteIdSet(favIds);
        allAdapter.setFavoriteIdSet(favIds);

        List<ActivityItem> filtered = new ArrayList<>();
        for (ActivityItem a : allActivities) {
            if (!filterDestination.isEmpty()
                    && !a.destination.trim().equalsIgnoreCase(filterDestination.trim())) {
                continue;
            }
            if (!filterCategory.isEmpty() && !filterCategory.equals(a.category)) {
                continue;
            }
            if (!filterDay.isEmpty() && !ActivityItem.sameDay(filterDay, a.day)) {
                continue;
            }
            if (a.price < filterPriceMin - 0.01 || a.price > filterPriceMax + 0.01) {
                continue;
            }
            if (!searchQuery.isEmpty()) {
                if (!a.name.toLowerCase(Locale.getDefault()).contains(searchQuery)) continue;
            }
            filtered.add(a);
        }

        List<ActivityItem> featured = buildRecommendedActivities(filtered);

        featuredAdapter.submit(featured);
        allAdapter.submit(filtered);

        boolean showFeatured = !featured.isEmpty();
        tvSectionFeatured.setVisibility(showFeatured ? View.VISIBLE : View.GONE);
        rvFeatured.setVisibility(showFeatured ? View.VISIBLE : View.GONE);

        tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        rvAll.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);

        updateAllListHeightForNestedScroll(filtered);

        if (scrollContent != null) {
            scrollContent.post(() -> {
                rvAll.requestLayout();
                scrollContent.requestLayout();
            });
        }
    }

    private void loadUserPreferences(@NonNull String uid) {
        realtimeDatabaseApi.getUser(uid).enqueue(new Callback<UserRtdbDto>() {
            @Override
            public void onResponse(@NonNull Call<UserRtdbDto> call, @NonNull Response<UserRtdbDto> response) {
                if (!isAdded()) return;
                UserRtdbDto user = response.body();
                applyUserPreferences(user);
            }

            @Override
            public void onFailure(@NonNull Call<UserRtdbDto> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                userPreferenceKeys.clear();
                hasUsefulUserPreferences = false;
                updateProfileButtonPhoto(null);
                applyFilters();
            }
        });
    }

    private void applyUserPreferences(@Nullable UserRtdbDto user) {
        userPreferenceKeys.clear();
        hasUsefulUserPreferences = false;
        if (user != null) {
            collectPreferenceKeys(user.preferences);
            collectPreferenceKeys(user.legacyPreferences);
        }
        hasUsefulUserPreferences = !userPreferenceKeys.isEmpty();
        updateProfileButtonPhoto(user != null ? user.photoUrl : null);
        applyFilters();
    }

    private void updateProfileButtonPhoto(@Nullable String photoUrl) {
        if (!isAdded() || btnProfile == null) return;

        String safePhotoUrl = photoUrl != null ? photoUrl.trim() : "";
        if (safePhotoUrl.isEmpty()) {
            Glide.with(this).clear(btnProfile);
            btnProfile.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
            btnProfile.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
            btnProfile.setImageResource(R.drawable.ic_nav_person);
            btnProfile.setImageTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.explore_title)));
            return;
        }

        btnProfile.setPadding(0, 0, 0, 0);
        btnProfile.setScaleType(ImageButton.ScaleType.CENTER_CROP);
        btnProfile.setImageTintList(null);
        Glide.with(this)
                .load(safePhotoUrl)
                .circleCrop()
                .placeholder(R.drawable.ic_nav_person)
                .error(R.drawable.ic_nav_person)
                .into(btnProfile);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void collectPreferenceKeys(@Nullable List<String> rawPreferences) {
        if (rawPreferences == null) return;
        for (String raw : rawPreferences) {
            String canonical = canonicalPreference(raw);
            if (canonical != null) {
                userPreferenceKeys.add(canonical);
            }
        }
    }

    @NonNull
    private List<ActivityItem> buildRecommendedActivities(@NonNull List<ActivityItem> filtered) {
        if (!hasUsefulUserPreferences) {
            return buildFeaturedFallback(filtered);
        }

        List<ActivityItem> recommended = new ArrayList<>();
        for (ActivityItem a : filtered) {
            int score = recommendationScore(a, userPreferenceKeys);
            if (score > 0) {
                recommended.add(a);
            }
        }

        if (recommended.isEmpty()) {
            return buildFeaturedFallback(filtered);
        }

        recommended.sort((a, b) -> {
            int scoreCompare = Integer.compare(
                    recommendationScore(b, userPreferenceKeys),
                    recommendationScore(a, userPreferenceKeys));
            if (scoreCompare != 0) return scoreCompare;
            int ratingCompare = Double.compare(b.rating, a.rating);
            if (ratingCompare != 0) return ratingCompare;
            return a.name.compareToIgnoreCase(b.name);
        });
        return recommended;
    }

    @NonNull
    private List<ActivityItem> buildFeaturedFallback(@NonNull List<ActivityItem> filtered) {
        List<ActivityItem> featured = new ArrayList<>();
        for (ActivityItem a : filtered) {
            if (a.featured) featured.add(a);
        }
        return featured;
    }

    private int recommendationScore(@NonNull ActivityItem activity, @NonNull Set<String> preferences) {
        boolean categoryMatch = activityMatchesPreferences(activity, preferences);
        if (!categoryMatch && !activity.featured) return 0;

        int score = 0;
        if (categoryMatch) score += 2;
        if (activity.featured) score += 1;
        return score;
    }

    private boolean activityMatchesPreferences(@NonNull ActivityItem activity, @NonNull Set<String> preferences) {
        if (preferences.isEmpty()) return false;
        String category = activity.category != null ? activity.category.trim().toLowerCase(Locale.ROOT) : "";
        switch (category) {
            case "aventura":
                return preferences.contains(PREF_AVENTURA);
            case "gastronomica":
                return preferences.contains(PREF_GASTRONOMIA);
            case "free_tour":
            case "visita_guiada":
                return preferences.contains(PREF_CULTURA);
            case "excursion":
                return preferences.contains(PREF_NATURALEZA);
            default:
                return false;
        }
    }

    @Nullable
    private String canonicalPreference(@Nullable String raw) {
        if (raw == null) return null;
        String normalized = ActivityItem.normalizeDay(raw)
                .replace("á", "a")
                .replace("é", "e")
                .replace("í", "i")
                .replace("ó", "o")
                .replace("ú", "u");
        switch (normalized) {
            case PREF_AVENTURA:
            case "aventuras":
                return PREF_AVENTURA;
            case PREF_CULTURA:
            case "cultural":
                return PREF_CULTURA;
            case PREF_GASTRONOMIA:
            case "gastronomica":
                return PREF_GASTRONOMIA;
            case PREF_NATURALEZA:
            case "natural":
                return PREF_NATURALEZA;
            case PREF_RELAX:
            case "descanso":
                return PREF_RELAX;
            default:
                return null;
        }
    }

    /**
     * Fija la altura del {@link RecyclerView} sumando cada fila medida con los mismos textos que en pantalla
     * (títulos de 2 líneas, cupos, etc.); una sola altura × N subestimaba y cortaba el último ítem.
     */
    private void updateAllListHeightForNestedScroll(@NonNull List<ActivityItem> filteredItems) {
        final ArrayList<ActivityItem> copy = new ArrayList<>(filteredItems);
        rvAll.post(new Runnable() {
            int attempts = 0;

            @Override
            public void run() {
                if (!isAdded()) return;
                ViewGroup.LayoutParams lp = rvAll.getLayoutParams();
                if (copy.isEmpty()) {
                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    rvAll.setLayoutParams(lp);
                    return;
                }
                int w = rvAll.getWidth();
                if (w <= 0 && attempts++ < 25) {
                    rvAll.post(this);
                    return;
                }
                if (w <= 0) return;
                LayoutInflater inflater = LayoutInflater.from(requireContext());
                int total = 0;
                for (ActivityItem a : copy) {
                    total += AllActivitiesAdapter.measureVerticalSpanForItem(a, w, inflater, rvAll);
                }
                int paddingV = rvAll.getPaddingTop() + rvAll.getPaddingBottom();
                lp.height = total + paddingV;
                rvAll.setLayoutParams(lp);
            }
        });
    }

    private void scrollExploreTo(@NonNull View anchor) {
        if (scrollContent == null) return;
        scrollContent.post(() -> {
            if (!isAdded()) return;
            int y = offsetTopFromScrollChild(anchor);
            int pad = (int) (8 * getResources().getDisplayMetrics().density);
            scrollContent.smoothScrollTo(0, Math.max(0, y - pad));
        });
    }

    /**
     * Offset del borde superior del {@link #scrollContent} contenido hasta {@code v}.
     */
    private int offsetTopFromScrollChild(@NonNull View v) {
        int y = 0;
        View current = v;
        while (true) {
            ViewParent parent = current.getParent();
            if (!(parent instanceof View)) {
                break;
            }
            View pv = (View) parent;
            y += current.getTop();
            if (pv.getParent() == scrollContent) {
                break;
            }
            current = pv;
        }
        return y;
    }

    private void showFilterBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View root = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_filter_activities, null, false);
        dialog.setContentView(root);

        Spinner spinnerDestination = root.findViewById(R.id.spinnerDestination);
        ChipGroup chipGroupCategory = root.findViewById(R.id.chipGroupCategory);
        ChipGroup chipGroupDay = root.findViewById(R.id.chipGroupDay);
        RangeSlider rangePrice = root.findViewById(R.id.rangePrice);
        TextView tvPriceMinLabel = root.findViewById(R.id.tvPriceMinLabel);
        TextView tvPriceMaxLabel = root.findViewById(R.id.tvPriceMaxLabel);
        TextView tvPriceSelection = root.findViewById(R.id.tvPriceSelection);
        MaterialButton btnClear = root.findViewById(R.id.btnFilterClear);
        MaterialButton btnApply = root.findViewById(R.id.btnFilterApply);

        List<String> destLabels = new ArrayList<>();
        List<String> destValues = new ArrayList<>();
        destLabels.add(getString(R.string.filter_destination_todos));
        destValues.add(TAG_ALL);
        TreeSet<String> destSet = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (ActivityItem a : allActivities) {
            if (!a.destination.isEmpty()) destSet.add(a.destination.trim());
        }
        for (String d : destSet) {
            destLabels.add(d);
            destValues.add(d);
        }
        ArrayAdapter<String> destAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_item, destLabels);
        destAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDestination.setAdapter(destAdapter);
        int destPos = 0;
        if (!filterDestination.isEmpty()) {
            for (int i = 0; i < destValues.size(); i++) {
                if (filterDestination.equalsIgnoreCase(destValues.get(i))) {
                    destPos = i;
                    break;
                }
            }
        }
        spinnerDestination.setSelection(destPos);

        chipGroupCategory.removeAllViews();
        addFilterChip(chipGroupCategory, getString(R.string.filter_category_todos), TAG_ALL,
                TAG_ALL.equals(filterCategory));
        TreeSet<String> catKeys = new TreeSet<>();
        for (ActivityItem a : allActivities) {
            if (!a.category.isEmpty()) catKeys.add(a.category);
        }
        for (String key : catKeys) {
            addFilterChip(chipGroupCategory, ActivityItem.categoryLabel(key), key,
                    key.equals(filterCategory));
        }

        chipGroupDay.removeAllViews();
        addFilterChip(chipGroupDay, getString(R.string.filter_day_todos), TAG_ALL,
                TAG_ALL.equals(filterDay));
        TreeSet<String> uniqueDays = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (ActivityItem a : allActivities) {
            if (!a.day.isEmpty()) uniqueDays.add(a.day.trim());
        }
        List<String> dayValues = new ArrayList<>(uniqueDays);
        dayValues.sort(Comparator.comparingInt(this::dayOrderIndex));
        for (String day : dayValues) {
            addFilterChip(chipGroupDay, capitalizeDayLabel(day), day,
                    !filterDay.isEmpty() && ActivityItem.sameDay(filterDay, day));
        }

        float max = (float) catalogMaxPrice;
        if (max < 1000f) max = 1000f;
        float step = max <= 25_000f ? 500f : 1000f;
        rangePrice.setValueFrom(0f);
        rangePrice.setValueTo(max);
        rangePrice.setStepSize(step);

        float lo = (float) Math.max(0, Math.min(filterPriceMin, catalogMaxPrice));
        float hi = (float) Math.max(lo, Math.min(filterPriceMax, catalogMaxPrice));
        lo = Math.round(lo / step) * step;
        hi = Math.round(hi / step) * step;
        hi = Math.max(lo, Math.min(hi, max));
        rangePrice.setValues(lo, hi);

        ensureChipGroupSelection(chipGroupCategory);
        ensureChipGroupSelection(chipGroupDay);

        tvPriceMinLabel.setText(formatMoney(0));
        tvPriceMaxLabel.setText(formatMoney(Math.round(max)));

        Runnable updatePriceLabel = () -> {
            List<Float> v = rangePrice.getValues();
            int a = Math.round(v.get(0));
            int b = Math.round(v.get(1));
            tvPriceSelection.setText(getString(R.string.filter_price_range_fmt, a, b));
        };
        updatePriceLabel.run();
        rangePrice.addOnChangeListener((slider, value, fromUser) -> updatePriceLabel.run());

        final float rangeMax = max;
        btnClear.setOnClickListener(v -> {
            spinnerDestination.setSelection(0);
            checkChipByTag(chipGroupCategory, TAG_ALL);
            checkChipByTag(chipGroupDay, TAG_ALL);
            rangePrice.setValues(0f, rangeMax);
            updatePriceLabel.run();
        });

        btnApply.setOnClickListener(v -> {
            int sp = spinnerDestination.getSelectedItemPosition();
            filterDestination = destValues.get(Math.min(sp, destValues.size() - 1));

            int catId = chipGroupCategory.getCheckedChipId();
            Chip catChip = root.findViewById(catId);
            filterCategory = catChip != null && catChip.getTag() instanceof String
                    ? (String) catChip.getTag() : TAG_ALL;

            int dayId = chipGroupDay.getCheckedChipId();
            Chip dayChip = root.findViewById(dayId);
            filterDay = dayChip != null && dayChip.getTag() instanceof String
                    ? (String) dayChip.getTag() : TAG_ALL;

            List<Float> vals = rangePrice.getValues();
            filterPriceMin = vals.get(0);
            filterPriceMax = vals.get(1);

            applyFilters();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void navigateToProfile(@NonNull View view) {
        Navigation.findNavController(view).navigate(R.id.action_exploreFragment_to_profileFragment);
    }

    private void ensureChipGroupSelection(ChipGroup group) {
        if (group.getChildCount() == 0) return;
        if (group.getCheckedChipId() == View.NO_ID) {
            View first = group.getChildAt(0);
            if (first instanceof Chip) group.check(first.getId());
        }
    }

    private void checkChipByTag(ChipGroup group, String tag) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View c = group.getChildAt(i);
            if (c instanceof Chip) {
                Chip chip = (Chip) c;
                Object t = chip.getTag();
                if (t instanceof String && tag.equals(t)) {
                    group.check(chip.getId());
                    return;
                }
            }
        }
    }

    private void addFilterChip(ChipGroup group, String label, String tag, boolean select) {
        Chip chip = new Chip(requireContext());
        chip.setText(label);
        chip.setCheckable(true);
        chip.setTag(tag);
        chip.setChipBackgroundColor(ContextCompat.getColorStateList(requireContext(), R.color.filter_sheet_chip_background));
        chip.setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.filter_sheet_chip_text));
        chip.setChipStrokeWidth(1f);
        chip.setChipStrokeColor(ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.explore_chip_stroke)));
        chip.setEnsureMinTouchTargetSize(false);
        group.addView(chip);
        if (select) {
            group.check(chip.getId());
        }
    }

    private int dayOrderIndex(String firestoreDay) {
        String n = ActivityItem.normalizeDay(firestoreDay);
        for (int i = 0; i < DAY_ORDER_NORM.length; i++) {
            if (DAY_ORDER_NORM[i].equals(n)) return i;
        }
        return 50;
    }

    private String capitalizeDayLabel(String day) {
        if (day == null || day.isEmpty()) return "";
        return day.substring(0, 1).toUpperCase(Locale.getDefault()) + day.substring(1);
    }

    private String formatMoney(int amount) {
        return "$" + amount;
    }
}
