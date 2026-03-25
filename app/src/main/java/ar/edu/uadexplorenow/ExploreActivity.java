package ar.edu.uadexplorenow;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.slider.RangeSlider;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.function.Consumer;

public class ExploreActivity extends AppCompatActivity {

    private static final String TAG_ALL = "";

    /** Orden de días para chips (normalizado sin acentos). */
    private static final String[] DAY_ORDER_NORM = {
            "lunes", "martes", "miercoles", "jueves", "viernes", "sabado", "domingo"
    };

    private FirebaseFirestore db;
    private final List<ActivityItem> allActivities = new ArrayList<>();

    private EditText etSearch;
    private RecyclerView rvFeatured;
    private RecyclerView rvAll;
    private ProgressBar progress;
    private TextView tvSectionFeatured;
    private TextView tvEmpty;
    private BottomNavigationView bottomNav;

    private final FeaturedActivitiesAdapter featuredAdapter = new FeaturedActivitiesAdapter();
    private final AllActivitiesAdapter allAdapter = new AllActivitiesAdapter();

    private String searchQuery = "";

    /** Filtros aplicados (cruzados con la búsqueda por nombre). */
    private String filterDestination = TAG_ALL;
    private String filterCategory = TAG_ALL;
    private String filterDay = TAG_ALL;
    private double filterPriceMin = 0;
    private double filterPriceMax = 0;

    /** Tope de precio del catálogo (ARS), se recalcula al cargar Firestore. */
    private double catalogMaxPrice = 80_000;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_explore);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        db = FirebaseFirestore.getInstance();

        etSearch = findViewById(R.id.etSearch);
        rvFeatured = findViewById(R.id.rvFeatured);
        rvAll = findViewById(R.id.rvAll);
        progress = findViewById(R.id.progress);
        tvSectionFeatured = findViewById(R.id.tvSectionFeatured);
        tvEmpty = findViewById(R.id.tvEmpty);
        bottomNav = findViewById(R.id.bottomNav);
        ImageButton btnProfile = findViewById(R.id.btnProfile);
        ImageButton btnFilter = findViewById(R.id.btnFilter);

        rvFeatured.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvFeatured.setAdapter(featuredAdapter);

        rvAll.setLayoutManager(new LinearLayoutManager(this));
        rvAll.setAdapter(allAdapter);

        Consumer<String> openDetail = id -> {
            Intent i = new Intent(this, ActivityDetailActivity.class);
            i.putExtra(ActivityDetailActivity.EXTRA_ACTIVITY_ID, id);
            startActivity(i);
        };
        featuredAdapter.setOnItemClick(openDetail);
        allAdapter.setOnItemClick(openDetail);

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

        btnProfile.setOnClickListener(v ->
                Toast.makeText(this, R.string.explore_nav_profile, Toast.LENGTH_SHORT).show());

        bottomNav.setSelectedItemId(R.id.nav_home);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                NestedScrollView scroll = findViewById(R.id.scrollContent);
                scroll.post(() -> scroll.scrollTo(0, 0));
                return true;
            }
            if (id == R.id.nav_search) {
                etSearch.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT);
                return true;
            }
            if (id == R.id.nav_list) {
                NestedScrollView scroll = findViewById(R.id.scrollContent);
                scroll.post(() -> {
                    int y = (int) rvAll.getY() - (int) (8 * getResources().getDisplayMetrics().density);
                    scroll.smoothScrollTo(0, Math.max(0, y));
                });
                return true;
            }
            if (id == R.id.nav_profile) {
                Toast.makeText(this, R.string.explore_nav_profile, Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        });

        loadActivities();
    }

    private void loadActivities() {
        progress.setVisibility(View.VISIBLE);
        db.collection("activities")
                .get()
                .addOnSuccessListener(query -> {
                    allActivities.clear();
                    for (QueryDocumentSnapshot doc : query) {
                        ActivityItem item = ActivityItem.fromDocument(doc);
                        if (item != null) allActivities.add(item);
                    }
                    Collections.sort(allActivities, (a, b) -> a.name.compareToIgnoreCase(b.name));
                    recomputeCatalogMaxPrice();
                    applyFilters();
                    progress.setVisibility(View.GONE);
                })
                .addOnFailureListener(e -> {
                    progress.setVisibility(View.GONE);
                    Toast.makeText(this, R.string.explore_load_error, Toast.LENGTH_LONG).show();
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

        List<ActivityItem> featured = new ArrayList<>();
        for (ActivityItem a : filtered) {
            if (a.featured) featured.add(a);
        }

        featuredAdapter.submit(featured);
        allAdapter.submit(filtered);

        boolean showFeatured = !featured.isEmpty();
        tvSectionFeatured.setVisibility(showFeatured ? View.VISIBLE : View.GONE);
        rvFeatured.setVisibility(showFeatured ? View.VISIBLE : View.GONE);

        tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        rvAll.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showFilterBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View root = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_filter_activities, null, false);
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
                this, android.R.layout.simple_spinner_item, destLabels);
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
        Chip chip = new Chip(this);
        chip.setText(label);
        chip.setCheckable(true);
        chip.setTag(tag);
        chip.setChipBackgroundColor(ContextCompat.getColorStateList(this, R.color.filter_sheet_chip_background));
        chip.setTextColor(ContextCompat.getColorStateList(this, R.color.filter_sheet_chip_text));
        chip.setChipStrokeWidth(1f);
        chip.setChipStrokeColor(ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.explore_chip_stroke)));
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
