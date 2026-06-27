package ar.edu.uadexplorenow.ui.notifications;

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
import androidx.navigation.NavOptions;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import ar.edu.uadexplorenow.R;
import ar.edu.uadexplorenow.data.NotificationsRepository;
import ar.edu.uadexplorenow.data.SessionStore;
import ar.edu.uadexplorenow.domain.NotificationItem;
import ar.edu.uadexplorenow.ui.common.OfflineBannerHelper;
import ar.edu.uadexplorenow.ui.explore.ActivityDetailFragment;
import ar.edu.uadexplorenow.ui.explore.NewsDetailFragment;
import ar.edu.uadexplorenow.ui.reservations.ReservationDetailFragment;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public final class NotificationsFragment extends Fragment {

    @Inject
    NotificationsRepository notificationsRepository;

    private final NotificationsAdapter adapter = new NotificationsAdapter();
    private final List<NotificationItem> currentItems = new ArrayList<>();

    private RecyclerView rvNotifications;
    private TextView tvEmpty;
    private TextView tvUnreadCount;
    private ProgressBar progress;
    private BottomNavigationView bottomNav;
    private String effectiveUid = "";

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_notifications, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            NavOptions navOptions = new NavOptions.Builder()
                    .setPopUpTo(R.id.notificationsFragment, true)
                    .build();
            Navigation.findNavController(view).navigate(R.id.loginFragment, null, navOptions);
            return;
        }
        effectiveUid = SessionStore.getEffectiveUid(requireContext(), user);

        ImageButton btnBack = view.findViewById(R.id.btnBack);
        rvNotifications = view.findViewById(R.id.rvNotifications);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        tvUnreadCount = view.findViewById(R.id.tvUnreadCount);
        progress = view.findViewById(R.id.progress);
        bottomNav = view.findViewById(R.id.bottomNav);

        rvNotifications.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvNotifications.setAdapter(adapter);
        adapter.setOnItemClick(this::openNotificationSource);

        btnBack.setOnClickListener(v -> Navigation.findNavController(view).popBackStack());
        setupBottomNav(view);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!effectiveUid.isEmpty()) {
            loadNotifications();
        }
    }

    private void loadNotifications() {
        setLoading(true);
        notificationsRepository.loadAll(effectiveUid, (items, fromCache) -> {
            if (!isAdded()) return;
            setLoading(false);
            currentItems.clear();
            currentItems.addAll(items);
            adapter.submit(items);
            updateEmptyAndCount();
            if (fromCache) {
                OfflineBannerHelper.show(getView());
            } else {
                OfflineBannerHelper.hide(getView());
            }
        });
    }

    private void updateEmptyAndCount() {
        int unread = 0;
        for (NotificationItem item : currentItems) {
            if (!item.isRead()) unread++;
        }
        tvUnreadCount.setText(getString(R.string.notifications_unread_count_fmt, unread));
        tvUnreadCount.setVisibility(unread > 0 ? View.VISIBLE : View.GONE);
        tvEmpty.setVisibility(currentItems.isEmpty() ? View.VISIBLE : View.GONE);
        rvNotifications.setVisibility(currentItems.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void openNotificationSource(@NonNull NotificationItem item) {
        notificationsRepository.markOpened(effectiveUid, item, error -> {
            if (!isAdded()) return;
            loadNotifications();
        });
        navigateToSource(item);
    }

    private void navigateToSource(@NonNull NotificationItem item) {
        if (item.sourceId.trim().isEmpty()) {
            Toast.makeText(requireContext(), R.string.notifications_source_missing, Toast.LENGTH_SHORT).show();
            return;
        }
        NavController navController = Navigation.findNavController(requireView());
        Bundle args = new Bundle();
        switch (NotificationItem.normalizeSourceType(item.sourceType)) {
            case NotificationItem.SOURCE_ACTIVITY:
                args.putString(ActivityDetailFragment.ARG_ACTIVITY_ID, item.sourceId);
                navController.navigate(R.id.action_notificationsFragment_to_activityDetailFragment, args);
                break;
            case NotificationItem.SOURCE_RESERVATION:
                args.putString(ReservationDetailFragment.ARG_RESERVATION_ID, item.sourceId);
                navController.navigate(R.id.action_notificationsFragment_to_reservationDetailFragment, args);
                break;
            case NotificationItem.SOURCE_NEWS:
                args.putString(NewsDetailFragment.ARG_NEWS_ID, item.sourceId);
                navController.navigate(R.id.action_notificationsFragment_to_newsDetailFragment, args);
                break;
            default:
                Toast.makeText(requireContext(), R.string.notifications_source_missing, Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private void setupBottomNav(@NonNull View view) {
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                Navigation.findNavController(view).navigate(R.id.action_notificationsFragment_to_exploreFragment);
                return true;
            }
            if (itemId == R.id.nav_favorites) {
                Navigation.findNavController(view).navigate(R.id.action_notificationsFragment_to_favoritesFragment);
                return true;
            }
            if (itemId == R.id.nav_list) {
                Navigation.findNavController(view).navigate(R.id.action_notificationsFragment_to_activityHistoryFragment);
                return true;
            }
            if (itemId == R.id.nav_profile) {
                Navigation.findNavController(view).navigate(R.id.action_notificationsFragment_to_profileFragment);
                return true;
            }
            return false;
        });
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        rvNotifications.setVisibility(loading ? View.GONE : rvNotifications.getVisibility());
        tvEmpty.setVisibility(loading ? View.GONE : tvEmpty.getVisibility());
    }
}
