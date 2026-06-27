package ar.edu.uadexplorenow.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import javax.inject.Inject;

import ar.edu.uadexplorenow.R;
import ar.edu.uadexplorenow.domain.NotificationItem;
import ar.edu.uadexplorenow.notifications.LocalNotificationPresenter;
import ar.edu.uadexplorenow.notifications.NotificationForegroundPoller;
import ar.edu.uadexplorenow.notifications.NotificationWorkScheduler;
import ar.edu.uadexplorenow.ui.explore.ActivityDetailFragment;
import ar.edu.uadexplorenow.ui.explore.NewsDetailFragment;
import ar.edu.uadexplorenow.ui.reservations.ReservationDetailFragment;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_POST_NOTIFICATIONS = 41;

    @Inject
    NotificationForegroundPoller notificationForegroundPoller;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        LocalNotificationPresenter.ensureChannel(this);
        requestNotificationPermissionIfNeeded();
        NotificationWorkScheduler.schedule(this);
        handleNotificationIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNotificationIntent(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        notificationForegroundPoller.start();
    }

    @Override
    protected void onStop() {
        notificationForegroundPoller.stop();
        super.onStop();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        ActivityCompat.requestPermissions(
                this,
                new String[] {Manifest.permission.POST_NOTIFICATIONS},
                REQUEST_POST_NOTIFICATIONS
        );
    }

    private void handleNotificationIntent(@NonNull Intent intent) {
        if (!intent.hasExtra(LocalNotificationPresenter.EXTRA_NOTIFICATION_ID)) return;
        String sourceType = intent.getStringExtra(LocalNotificationPresenter.EXTRA_SOURCE_TYPE);
        String sourceId = intent.getStringExtra(LocalNotificationPresenter.EXTRA_SOURCE_ID);
        getWindow().getDecorView().post(() -> navigateFromNotification(sourceType, sourceId));
    }

    private void navigateFromNotification(String sourceType, String sourceId) {
        NavHostFragment host = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (host == null) return;
        NavController navController = host.getNavController();
        Bundle args = new Bundle();
        if (sourceId == null || sourceId.trim().isEmpty()) {
            navController.navigate(R.id.notificationsFragment);
            return;
        }
        if (NotificationItem.SOURCE_ACTIVITY.equals(sourceType)) {
            args.putString(ActivityDetailFragment.ARG_ACTIVITY_ID, sourceId);
            navController.navigate(R.id.activityDetailFragment, args);
        } else if (NotificationItem.SOURCE_RESERVATION.equals(sourceType)) {
            args.putString(ReservationDetailFragment.ARG_RESERVATION_ID, sourceId);
            navController.navigate(R.id.reservationDetailFragment, args);
        } else if (NotificationItem.SOURCE_NEWS.equals(sourceType)) {
            args.putString(NewsDetailFragment.ARG_NEWS_ID, sourceId);
            navController.navigate(R.id.newsDetailFragment, args);
        } else {
            navController.navigate(R.id.notificationsFragment);
        }
    }
}