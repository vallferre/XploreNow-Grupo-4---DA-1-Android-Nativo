package ar.edu.uadexplorenow.ui.explore;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import ar.edu.uadexplorenow.R;
import ar.edu.uadexplorenow.data.FavoritesRepository;
import ar.edu.uadexplorenow.data.SessionStore;
import ar.edu.uadexplorenow.data.local.db.CachedActivityDao;
import ar.edu.uadexplorenow.data.local.db.CachedActivityEntity;
import ar.edu.uadexplorenow.data.model.ActivityRtdbMapper;
import ar.edu.uadexplorenow.data.model.FavoriteRtdbDto;
import ar.edu.uadexplorenow.data.network.RealtimeDatabaseApi;
import ar.edu.uadexplorenow.domain.ActivityItem;
import ar.edu.uadexplorenow.domain.FavoriteListRow;
import ar.edu.uadexplorenow.domain.FavoriteListRow.SpotsNovelty;
import ar.edu.uadexplorenow.ui.common.OfflineBannerHelper;
import ar.edu.uadexplorenow.ui.common.SystemBarInsetsHelper;

import android.os.Handler;
import android.os.Looper;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.gson.Gson;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class FavoritesFragment extends Fragment {

    @Inject
    RealtimeDatabaseApi realtimeDatabaseApi;
    @Inject
    Gson gson;
    @Inject
    FavoritesRepository favoritesRepository;
    @Inject
    CachedActivityDao cachedActivityDao;

    private final FavoritesAdapter adapter = new FavoritesAdapter();
    private final Map<String, ActivityItem> activityById = new HashMap<>();

    private ProgressBar progress;
    private TextView tvEmpty;
    private RecyclerView rvFavorites;
    private BottomNavigationView bottomNav;
    private String effectiveUid = "";

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_favorites, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Navigation.findNavController(view).popBackStack();
            return;
        }
        effectiveUid = SessionStore.getEffectiveUid(requireContext(), currentUser);

        SystemBarInsetsHelper.applyTopPadding(view.findViewById(R.id.headerContainer));
        ImageButton btnBack = view.findViewById(R.id.btnBack);
        progress = view.findViewById(R.id.progress);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        rvFavorites = view.findViewById(R.id.rvFavorites);
        bottomNav = view.findViewById(R.id.bottomNav);

        rvFavorites.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvFavorites.setAdapter(adapter);

        adapter.setOnOpenDetail(id -> {
            Bundle args = new Bundle();
            args.putString(ActivityDetailFragment.ARG_ACTIVITY_ID, id);
            Navigation.findNavController(view).navigate(
                    R.id.action_favoritesFragment_to_activityDetailFragment, args);
        });
        adapter.setOnRemoveFavorite(this::removeFavorite);

        btnBack.setOnClickListener(v -> Navigation.findNavController(view).popBackStack());

        bottomNav.setSelectedItemId(R.id.nav_favorites);
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                navigateToExplore(view);
                return true;
            }
            if (itemId == R.id.nav_favorites) {
                if (rvFavorites != null) {
                    rvFavorites.smoothScrollToPosition(0);
                }
                return true;
            }
            if (itemId == R.id.nav_list) {
                navigateThenHistory(view);
                return true;
            }
            if (itemId == R.id.nav_profile) {
                Navigation.findNavController(view)
                        .navigate(R.id.action_favoritesFragment_to_profileFragment);
                return true;
            }
            return false;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (FirebaseAuth.getInstance().getCurrentUser() != null && !effectiveUid.isEmpty()) {
            loadAll();
        }
    }

    private void loadAll() {
        progress.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        favoritesRepository.loadAll(effectiveUid, favMap -> {
            if (!isAdded()) return;
            realtimeDatabaseApi.getActivities().enqueue(new Callback<JsonElement>() {
                @Override
                public void onResponse(@NonNull Call<JsonElement> call, @NonNull Response<JsonElement> response) {
                    if (!isAdded()) return;
                    progress.setVisibility(View.GONE);
                    activityById.clear();
                    if (response.isSuccessful() && response.body() != null) {
                        for (ActivityItem a : ActivityRtdbMapper.toActivityItems(response.body(), gson)) {
                            activityById.put(a.id, a);
                        }
                    }
                    OfflineBannerHelper.hide(getView());
                    renderFavorites(favMap);
                }

                @Override
                public void onFailure(@NonNull Call<JsonElement> call, @NonNull Throwable t) {
                    if (!isAdded()) return;
                    loadActivitiesFromCache(favMap);
                }
            });
        });
    }

    private void loadActivitiesFromCache(@NonNull Map<String, FavoriteRtdbDto> favMap) {
        new Thread(() -> {
            List<CachedActivityEntity> cached = cachedActivityDao.getAll();
            new Handler(Looper.getMainLooper()).post(() -> {
                if (!isAdded()) return;
                progress.setVisibility(View.GONE);
                activityById.clear();
                for (ActivityItem a : CachedActivityEntity.toList(cached)) {
                    activityById.put(a.id, a);
                }
                OfflineBannerHelper.show(getView());
                renderFavorites(favMap);
            });
        }).start();
    }

    private void renderFavorites(@NonNull Map<String, FavoriteRtdbDto> favMap) {
        List<FavoriteListRow> rows = new ArrayList<>();
        for (Map.Entry<String, FavoriteRtdbDto> e : favMap.entrySet()) {
            ActivityItem a = activityById.get(e.getKey());
            if (a == null) {
                continue;
            }
            FavoriteRtdbDto dto = e.getValue();
            boolean priceN = FavoritesRepository.hasPriceNovelty(dto, a.price);
            SpotsNovelty spotsKind = SpotsNovelty.NONE;
            long spotsDelta = 0;
            if (FavoritesRepository.spotsBecameUnavailable(dto, a.availableSpots)) {
                spotsKind = SpotsNovelty.SOLD_OUT;
            } else {
                spotsDelta = FavoritesRepository.spotsIncreaseDelta(dto, a.availableSpots);
                if (spotsDelta > 0) {
                    spotsKind = SpotsNovelty.INCREASED;
                }
            }
            double favoritedPrice = 0;
            boolean priceUp = false;
            if (priceN && dto.lastSeenPrice != null) {
                favoritedPrice = dto.lastSeenPrice;
                priceUp = a.price > dto.lastSeenPrice + 0.009;
            }
            rows.add(new FavoriteListRow(a, priceN, spotsKind, spotsDelta, favoritedPrice, priceUp));
        }
        rows.sort((a, b) -> a.activity.name.compareToIgnoreCase(b.activity.name));
        adapter.submit(rows);
        boolean empty = rows.isEmpty();
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvFavorites.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void removeFavorite(@NonNull ActivityItem activity) {
        favoritesRepository.removeFavorite(effectiveUid, activity.id, err -> {
            if (!isAdded()) return;
            if (err != null) {
                Toast.makeText(requireContext(), R.string.favorites_load_error, Toast.LENGTH_SHORT).show();
                return;
            }
            loadAll();
        });
    }

    private void navigateToExplore(@NonNull View view) {
        NavController navController = Navigation.findNavController(view);
        boolean popped = navController.popBackStack(R.id.exploreFragment, false);
        if (!popped) {
            navController.navigate(R.id.exploreFragment);
        }
    }

    private void navigateThenHistory(@NonNull View view) {
        NavController nav = Navigation.findNavController(view);
        boolean popped = nav.popBackStack(R.id.exploreFragment, false);
        if (!popped) {
            nav.navigate(R.id.exploreFragment);
        }
        nav.navigate(R.id.action_exploreFragment_to_activityHistoryFragment);
    }
}
