package ar.edu.uadexplorenow.ui.reservations;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.navigation.Navigation;

import ar.edu.uadexplorenow.R;
import ar.edu.uadexplorenow.data.ReservationRepository;
import ar.edu.uadexplorenow.data.SessionStore;
import ar.edu.uadexplorenow.data.model.ActivityRtdbMapper;
import ar.edu.uadexplorenow.data.model.UserRtdbDto;
import ar.edu.uadexplorenow.data.network.RealtimeDatabaseApi;
import ar.edu.uadexplorenow.domain.ActivityDetail;
import ar.edu.uadexplorenow.domain.ReservationItem;
import ar.edu.uadexplorenow.domain.ReservationStatus;

import com.bumptech.glide.Glide;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.gson.Gson;
import com.google.gson.JsonElement;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
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
public class ReservationDetailFragment extends Fragment {

    private static final String MAP_VIEW_BUNDLE_KEY = "reservation_detail_map_view_state";
    private static final String MAPS_API_KEY_META_DATA = "com.google.android.geo.API_KEY";
    private static final float MEETING_POINT_ZOOM = 15f;

    public static final String ARG_RESERVATION_ID = "reservation_id";

    @Inject
    RealtimeDatabaseApi realtimeDatabaseApi;
    @Inject
    Gson gson;

    @Nullable
    private String effectiveUid;
    @Nullable
    private ReservationItem loadedReservation;
    @Nullable
    private MaterialButton btnCancelReservation;
    @Nullable
    private MaterialButton btnFinishReservation;
    @Nullable
    private MaterialButton btnOpenDirections;
    @Nullable
    private ProgressBar progress;
    @Nullable
    private View contentRoot;
    @Nullable
    private ReservationRepository reservationRepository;
    @Nullable
    private MapView meetingPointMapView;
    @Nullable
    private View meetingPointMapContainer;
    @Nullable
    private TextView tvMeetingMapFallback;
    @Nullable
    private GoogleMap meetingPointMap;
    @Nullable
    private Bundle pendingMapViewState;

    private boolean userDone;
    private boolean activitiesDone;
    private boolean mapViewInitialized;
    private int mapRenderVersion;
    private final ExecutorService geocodeExecutor = Executors.newSingleThreadExecutor();
    @Nullable
    private UserRtdbDto loadedUser;
    private Map<String, ActivityDetail> detailById = Collections.emptyMap();

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_reservation_detail, container, false);
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
        reservationRepository = new ReservationRepository(realtimeDatabaseApi);
        pendingMapViewState = savedInstanceState != null
                ? savedInstanceState.getBundle(MAP_VIEW_BUNDLE_KEY)
                : null;

        String reservationId = getArguments() != null ? getArguments().getString(ARG_RESERVATION_ID) : null;
        if (reservationId == null || reservationId.isEmpty()) {
            Navigation.findNavController(view).popBackStack();
            return;
        }

        ImageButton btnBack = view.findViewById(R.id.btnBack);
        contentRoot = view.findViewById(R.id.contentRoot);
        progress = view.findViewById(R.id.progress);
        btnCancelReservation = view.findViewById(R.id.btnCancelReservation);
        btnFinishReservation = view.findViewById(R.id.btnFinishReservation);
        btnOpenDirections = view.findViewById(R.id.btnOpenDirections);
        meetingPointMapContainer = view.findViewById(R.id.meetingPointMapContainer);
        meetingPointMapView = view.findViewById(R.id.meetingPointMapView);
        tvMeetingMapFallback = view.findViewById(R.id.tvMeetingMapFallback);

        btnBack.setOnClickListener(v -> Navigation.findNavController(view).popBackStack());
        btnCancelReservation.setOnClickListener(v -> promptCancelReservation());
        btnFinishReservation.setOnClickListener(v -> finishReservation());
        loadReservation(reservationId);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mapViewInitialized && meetingPointMapView != null) {
            meetingPointMapView.onStart();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapViewInitialized && meetingPointMapView != null) {
            meetingPointMapView.onResume();
        }
    }

    @Override
    public void onPause() {
        if (mapViewInitialized && meetingPointMapView != null) {
            meetingPointMapView.onPause();
        }
        super.onPause();
    }

    @Override
    public void onStop() {
        if (mapViewInitialized && meetingPointMapView != null) {
            meetingPointMapView.onStop();
        }
        super.onStop();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapViewInitialized && meetingPointMapView != null) {
            meetingPointMapView.onLowMemory();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (!mapViewInitialized || meetingPointMapView == null) {
            return;
        }
        Bundle mapBundle = new Bundle();
        meetingPointMapView.onSaveInstanceState(mapBundle);
        outState.putBundle(MAP_VIEW_BUNDLE_KEY, mapBundle);
    }

    @Override
    public void onDestroyView() {
        if (mapViewInitialized && meetingPointMapView != null) {
            meetingPointMapView.onDestroy();
        }
        meetingPointMap = null;
        meetingPointMapContainer = null;
        meetingPointMapView = null;
        tvMeetingMapFallback = null;
        btnOpenDirections = null;
        pendingMapViewState = null;
        mapViewInitialized = false;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        geocodeExecutor.shutdownNow();
        super.onDestroy();
    }

    private void loadReservation(@NonNull String reservationId) {
        setLoading(true);
        userDone = false;
        activitiesDone = false;

        realtimeDatabaseApi.getUser(effectiveUid).enqueue(new Callback<UserRtdbDto>() {
            @Override
            public void onResponse(@NonNull Call<UserRtdbDto> call, @NonNull Response<UserRtdbDto> response) {
                if (!isAdded()) return;
                loadedUser = response.isSuccessful() ? response.body() : null;
                userDone = true;
                maybeBindReservation(reservationId);
            }

            @Override
            public void onFailure(@NonNull Call<UserRtdbDto> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                loadedUser = null;
                userDone = true;
                maybeBindReservation(reservationId);
            }
        });

        realtimeDatabaseApi.getActivities().enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(@NonNull Call<JsonElement> call, @NonNull Response<JsonElement> response) {
                if (!isAdded()) return;
                detailById = response.isSuccessful() && response.body() != null
                        ? ActivityRtdbMapper.toActivityDetails(response.body(), gson)
                        : Collections.emptyMap();
                activitiesDone = true;
                maybeBindReservation(reservationId);
            }

            @Override
            public void onFailure(@NonNull Call<JsonElement> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                detailById = Collections.emptyMap();
                activitiesDone = true;
                maybeBindReservation(reservationId);
            }
        });
    }

    private void maybeBindReservation(@NonNull String reservationId) {
        if (!userDone || !activitiesDone || !isAdded()) {
            return;
        }
        setLoading(false);

        List<ReservationItem> reservations = ReservationItem.buildList(loadedUser, detailById);
        for (ReservationItem item : reservations) {
            if (reservationId.equals(item.reservationId)) {
                loadedReservation = item;
                bindReservation(item);
                return;
            }
        }

        Toast.makeText(requireContext(), R.string.reservation_detail_missing, Toast.LENGTH_LONG).show();
        Navigation.findNavController(requireView()).popBackStack();
    }

    private void bindReservation(@NonNull ReservationItem reservation) {
        if (contentRoot == null) return;

        ImageView ivHero = requireView().findViewById(R.id.ivHero);
        TextView tvStatus = requireView().findViewById(R.id.tvStatus);
        TextView tvTitle = requireView().findViewById(R.id.tvTitle);
        TextView tvDestination = requireView().findViewById(R.id.tvDestination);
        TextView tvWhen = requireView().findViewById(R.id.tvWhen);
        TextView tvParticipants = requireView().findViewById(R.id.tvParticipants);
        TextView tvDuration = requireView().findViewById(R.id.tvDuration);
        TextView tvGuide = requireView().findViewById(R.id.tvGuide);
        TextView tvTotal = requireView().findViewById(R.id.tvTotal);
        TextView tvMeetingTitle = requireView().findViewById(R.id.tvMeetingTitle);
        View cardMeeting = requireView().findViewById(R.id.cardMeeting);
        TextView tvMeetingPoint = requireView().findViewById(R.id.tvMeetingPoint);
        TextView tvCancellationTitle = requireView().findViewById(R.id.tvCancellationTitle);
        View cardCancellation = requireView().findViewById(R.id.cardCancellation);
        TextView tvCancellationType = requireView().findViewById(R.id.tvCancellationType);
        TextView tvCancellationDesc = requireView().findViewById(R.id.tvCancellationDesc);
        TextView tvDescriptionTitle = requireView().findViewById(R.id.tvDescriptionTitle);
        TextView tvDescription = requireView().findViewById(R.id.tvDescription);

        if (!reservation.imageUrl.isEmpty()) {
            Glide.with(this)
                    .load(reservation.imageUrl)
                    .centerCrop()
                    .placeholder(R.color.explore_search_bg)
                    .into(ivHero);
        } else {
            ivHero.setImageResource(R.color.explore_search_bg);
        }

        tvStatus.setText(reservation.statusLabel());
        bindStatusChip(tvStatus, reservation.status);
        tvTitle.setText(reservation.activityName);
        tvDestination.setText(reservation.destinationLabel());
        tvWhen.setText(reservation.formattedDateLong());
        tvParticipants.setText(reservation.participantsLabel());
        tvDuration.setText(reservation.formattedDuration());
        tvGuide.setText(reservation.guideName.isEmpty() ? "-" : reservation.guideName);
        tvTotal.setText(reservation.formattedTotalPrice());

        ActivityDetail activityDetail = reservation.activityId.isEmpty()
                ? null
                : detailById.get(reservation.activityId);
        bindMeetingSection(reservation, activityDetail, tvMeetingTitle, cardMeeting, tvMeetingPoint);

        String cancellationType = reservation.cancellationTypeLabel();
        String cancellationSummary = reservation.cancellationSummary();
        if (cancellationType.isEmpty() && cancellationSummary.isEmpty()) {
            tvCancellationTitle.setVisibility(View.GONE);
            cardCancellation.setVisibility(View.GONE);
        } else {
            tvCancellationTitle.setVisibility(View.VISIBLE);
            cardCancellation.setVisibility(View.VISIBLE);
            tvCancellationType.setVisibility(cancellationType.isEmpty() ? View.GONE : View.VISIBLE);
            if (!cancellationType.isEmpty()) {
                tvCancellationType.setText(getString(R.string.detail_cancel_type_fmt, cancellationType));
            }
            tvCancellationDesc.setText(cancellationSummary);
        }

        if (reservation.description.isEmpty()) {
            tvDescriptionTitle.setVisibility(View.GONE);
            tvDescription.setVisibility(View.GONE);
        } else {
            tvDescriptionTitle.setVisibility(View.VISIBLE);
            tvDescription.setVisibility(View.VISIBLE);
            tvDescription.setText(reservation.description);
        }

        updateActionButtons(reservation);
        contentRoot.setVisibility(View.VISIBLE);
    }

    private void bindMeetingSection(
            @NonNull ReservationItem reservation,
            @Nullable ActivityDetail activityDetail,
            @NonNull TextView tvMeetingTitle,
            @NonNull View cardMeeting,
            @NonNull TextView tvMeetingPoint
    ) {
        String meetingPoint = reservation.meetingPoint == null ? "" : reservation.meetingPoint.trim();
        if (meetingPoint.isEmpty()) {
            tvMeetingTitle.setVisibility(View.GONE);
            cardMeeting.setVisibility(View.GONE);
            if (btnOpenDirections != null) {
                btnOpenDirections.setOnClickListener(null);
            }
            hideMeetingMap();
            return;
        }

        tvMeetingTitle.setVisibility(View.VISIBLE);
        cardMeeting.setVisibility(View.VISIBLE);
        tvMeetingPoint.setText(getString(R.string.reservation_detail_meeting_point_fmt, meetingPoint));
        if (btnOpenDirections != null) {
            btnOpenDirections.setVisibility(View.VISIBLE);
            btnOpenDirections.setOnClickListener(v -> openDirections(meetingPoint, activityDetail));
        }
        renderMeetingPointOnMap(reservation, activityDetail);
    }

    private void renderMeetingPointOnMap(
            @NonNull ReservationItem reservation,
            @Nullable ActivityDetail activityDetail
    ) {
        if (meetingPointMapView == null || tvMeetingMapFallback == null) {
            return;
        }
        if (!hasConfiguredMapsApiKey()) {
            showMeetingMapFallback(getString(R.string.reservation_detail_map_no_key));
            return;
        }
        if (!isGooglePlayServicesAvailable()) {
            showMeetingMapFallback(getString(R.string.reservation_detail_map_unavailable));
            return;
        }

        initializeMeetingMapIfNeeded();
        if (meetingPointMap == null) {
            showMeetingMapFallback(getString(R.string.reservation_detail_map_pending));
            return;
        }
        LatLng directMeetingPoint = extractMeetingPointLatLng(activityDetail);
        if (directMeetingPoint != null) {
            showMeetingMarkers(reservation, activityDetail, directMeetingPoint);
            return;
        }
        if (!Geocoder.isPresent()) {
            showMeetingMapFallback(getString(R.string.reservation_detail_map_no_location));
            return;
        }

        showMeetingMapFallback(getString(R.string.reservation_detail_map_pending));
        resolveMeetingPointLatLng(reservation, activityDetail);
    }

    private void initializeMeetingMapIfNeeded() {
        if (mapViewInitialized || meetingPointMapView == null) {
            return;
        }

        meetingPointMapView.onCreate(pendingMapViewState);
        mapViewInitialized = true;

        Lifecycle.State currentState = getViewLifecycleOwner().getLifecycle().getCurrentState();
        if (currentState.isAtLeast(Lifecycle.State.STARTED)) {
            meetingPointMapView.onStart();
        }
        if (currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            meetingPointMapView.onResume();
        }

        meetingPointMapView.getMapAsync(googleMap -> {
            if (!isAdded()) {
                return;
            }
            meetingPointMap = googleMap;
            googleMap.getUiSettings().setMapToolbarEnabled(false);
            googleMap.getUiSettings().setMyLocationButtonEnabled(false);
            googleMap.getUiSettings().setCompassEnabled(true);
            googleMap.getUiSettings().setZoomControlsEnabled(false);

            if (loadedReservation != null && !loadedReservation.meetingPoint.isEmpty()) {
                ActivityDetail activityDetail = loadedReservation.activityId.isEmpty()
                        ? null
                        : detailById.get(loadedReservation.activityId);
                renderMeetingPointOnMap(loadedReservation, activityDetail);
            }
        });
    }

    private void resolveMeetingPointLatLng(
            @NonNull ReservationItem reservation,
            @Nullable ActivityDetail activityDetail
    ) {
        final int requestVersion = ++mapRenderVersion;
        final String meetingPoint = reservation.meetingPoint;
        final MapView localMapView = meetingPointMapView;
        if (localMapView == null) {
            return;
        }

        Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocationName(meetingPoint, 1, new Geocoder.GeocodeListener() {
                @Override
                public void onGeocode(@NonNull List<Address> addresses) {
                    localMapView.post(() -> handleMeetingPointResolved(
                            requestVersion,
                            reservation,
                            activityDetail,
                            addresses));
                }

                @Override
                public void onError(@Nullable String errorMessage) {
                    localMapView.post(() -> handleMeetingPointResolutionFailure(requestVersion));
                }
            });
            return;
        }

        geocodeExecutor.execute(() -> {
            try {
                @SuppressWarnings("deprecation")
                List<Address> addresses = geocoder.getFromLocationName(meetingPoint, 1);
                localMapView.post(() -> handleMeetingPointResolved(
                        requestVersion,
                        reservation,
                        activityDetail,
                        addresses));
            } catch (IOException | IllegalArgumentException e) {
                localMapView.post(() -> handleMeetingPointResolutionFailure(requestVersion));
            }
        });
    }

    private void handleMeetingPointResolved(
            int requestVersion,
            @NonNull ReservationItem reservation,
            @Nullable ActivityDetail activityDetail,
            @Nullable List<Address> addresses
    ) {
        if (!isAdded() || requestVersion != mapRenderVersion || meetingPointMap == null) {
            return;
        }
        if (addresses == null || addresses.isEmpty()) {
            showMeetingMapFallback(getString(R.string.reservation_detail_map_no_location));
            return;
        }

        Address address = addresses.get(0);
        if (!address.hasLatitude() || !address.hasLongitude()) {
            showMeetingMapFallback(getString(R.string.reservation_detail_map_no_location));
            return;
        }

        showMeetingMarkers(
                reservation,
                activityDetail,
                new LatLng(address.getLatitude(), address.getLongitude()));
    }

    private void handleMeetingPointResolutionFailure(int requestVersion) {
        if (!isAdded() || requestVersion != mapRenderVersion) {
            return;
        }
        showMeetingMapFallback(getString(R.string.reservation_detail_map_no_location));
    }

    private void showMeetingMarkers(
            @NonNull ReservationItem reservation,
            @Nullable ActivityDetail activityDetail,
            @NonNull LatLng meetingLatLng
    ) {
        if (meetingPointMap == null || meetingPointMapView == null || tvMeetingMapFallback == null) {
            return;
        }

        List<MapMarkerSpec> markerSpecs = new ArrayList<>();
        markerSpecs.add(new MapMarkerSpec(
                meetingLatLng,
                getString(R.string.detail_section_meeting),
                reservation.meetingPoint,
                BitmapDescriptorFactory.HUE_RED
        ));
        List<ActivityDetail.ItineraryPoint> itineraryPoints = activityDetail != null
                ? activityDetail.itineraryPoints
                : Collections.emptyList();
        List<LatLng> itineraryPath = new ArrayList<>();
        markerSpecs.addAll(buildItineraryMapMarkers(reservation, itineraryPoints, itineraryPath, meetingLatLng));

        meetingPointMap.clear();
        if (itineraryPath.size() > 1) {
            meetingPointMap.addPolyline(new PolylineOptions()
                    .addAll(itineraryPath)
                    .color(ContextCompat.getColor(requireContext(), R.color.filter_primary))
                    .width(dpToPx(3)));
        }
        if (markerSpecs.size() == 1) {
            MapMarkerSpec meetingMarker = markerSpecs.get(0);
            meetingPointMap.addMarker(buildMarkerOptions(meetingMarker));
            meetingPointMap.moveCamera(CameraUpdateFactory.newLatLngZoom(meetingMarker.position, MEETING_POINT_ZOOM));
        } else {
            LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
            for (MapMarkerSpec markerSpec : markerSpecs) {
                meetingPointMap.addMarker(buildMarkerOptions(markerSpec));
                boundsBuilder.include(markerSpec.position);
            }
            moveCameraToVisibleBounds(boundsBuilder.build());
        }

        meetingPointMapView.setVisibility(View.VISIBLE);
        tvMeetingMapFallback.setVisibility(View.GONE);
    }

    private void moveCameraToVisibleBounds(@NonNull LatLngBounds bounds) {
        if (meetingPointMap == null || meetingPointMapView == null) {
            return;
        }
        int width = meetingPointMapView.getWidth();
        int height = meetingPointMapView.getHeight();
        int padding = dpToPx(48);
        if (width > 0 && height > 0) {
            meetingPointMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, width, height, padding));
            return;
        }
        meetingPointMapView.post(() -> moveCameraToVisibleBounds(bounds));
    }

    @NonNull
    private MarkerOptions buildMarkerOptions(@NonNull MapMarkerSpec markerSpec) {
        MarkerOptions options = new MarkerOptions()
                .position(markerSpec.position)
                .title(markerSpec.title)
                .icon(BitmapDescriptorFactory.defaultMarker(markerSpec.hue));
        if (markerSpec.snippet != null && !markerSpec.snippet.isEmpty()) {
            options.snippet(markerSpec.snippet);
        }
        return options;
    }

    @NonNull
    private List<MapMarkerSpec> buildItineraryMapMarkers(
            @NonNull ReservationItem reservation,
            @Nullable List<ActivityDetail.ItineraryPoint> itineraryPoints,
            @NonNull List<LatLng> itineraryPath,
            @NonNull LatLng meetingLatLng
    ) {
        List<MapMarkerSpec> markers = new ArrayList<>();
        if (itineraryPoints == null || itineraryPoints.isEmpty()) {
            return markers;
        }
        for (ActivityDetail.ItineraryPoint point : itineraryPoints) {
            if (point == null || !point.hasValidCoordinates() || point.coords == null) {
                continue;
            }
            LatLng pointLatLng = new LatLng(point.coords.lat, point.coords.lng);
            itineraryPath.add(pointLatLng);
            if (isSameCoordinate(pointLatLng, meetingLatLng)) {
                continue;
            }
            String title = point.title.isEmpty() ? reservation.activityName : point.title;
            markers.add(new MapMarkerSpec(
                    pointLatLng,
                    title,
                    point.description,
                    BitmapDescriptorFactory.HUE_AZURE
            ));
        }
        return markers;
    }

    @Nullable
    private LatLng extractMeetingPointLatLng(@Nullable ActivityDetail activityDetail) {
        if (activityDetail == null || activityDetail.meetingPointCoords == null) {
            return null;
        }
        return new LatLng(activityDetail.meetingPointCoords.lat, activityDetail.meetingPointCoords.lng);
    }

    private boolean isSameCoordinate(@NonNull LatLng first, @NonNull LatLng second) {
        return Math.abs(first.latitude - second.latitude) < 0.00001d
                && Math.abs(first.longitude - second.longitude) < 0.00001d;
    }

    private void openDirections(@NonNull String meetingPoint, @Nullable ActivityDetail activityDetail) {
        ActivityDetail.GeoPoint meetingPointCoords = activityDetail != null ? activityDetail.meetingPointCoords : null;
        if (meetingPointCoords != null) {
            openDirectionsWithCoordinates(meetingPoint, meetingPointCoords);
            return;
        }

        String encodedMeetingPoint = Uri.encode(meetingPoint);

        Intent googleMapsIntent = new Intent(
                Intent.ACTION_VIEW,
                Uri.parse("geo:0,0?q=" + encodedMeetingPoint + "(" + encodedMeetingPoint + ")")
        );
        googleMapsIntent.setPackage("com.google.android.apps.maps");
        if (startExternalIntent(googleMapsIntent)) {
            return;
        }

        Intent genericMapsIntent = new Intent(
                Intent.ACTION_VIEW,
                Uri.parse("geo:0,0?q=" + encodedMeetingPoint + "(" + encodedMeetingPoint + ")")
        );
        if (startExternalIntent(genericMapsIntent)) {
            return;
        }

        Intent browserFallbackIntent = new Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/maps/search/?api=1&query=" + encodedMeetingPoint)
        );
        if (startExternalIntent(browserFallbackIntent)) {
            return;
        }

        Toast.makeText(requireContext(), R.string.reservation_detail_map_app_missing, Toast.LENGTH_LONG).show();
    }

    private void openDirectionsWithCoordinates(
            @NonNull String meetingPoint,
            @NonNull ActivityDetail.GeoPoint meetingPointCoords
    ) {
        String encodedLabel = Uri.encode(meetingPoint);
        String latLng = meetingPointCoords.lat + "," + meetingPointCoords.lng;

        Intent googleMapsIntent = new Intent(
                Intent.ACTION_VIEW,
                Uri.parse("geo:" + latLng + "?q=" + latLng + "(" + encodedLabel + ")")
        );
        googleMapsIntent.setPackage("com.google.android.apps.maps");
        if (startExternalIntent(googleMapsIntent)) {
            return;
        }

        Intent genericMapsIntent = new Intent(
                Intent.ACTION_VIEW,
                Uri.parse("geo:" + latLng + "?q=" + latLng + "(" + encodedLabel + ")")
        );
        if (startExternalIntent(genericMapsIntent)) {
            return;
        }

        Intent browserFallbackIntent = new Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/maps/search/?api=1&query=" + latLng)
        );
        if (startExternalIntent(browserFallbackIntent)) {
            return;
        }

        Toast.makeText(requireContext(), R.string.reservation_detail_map_app_missing, Toast.LENGTH_LONG).show();
    }

    private boolean startExternalIntent(@NonNull Intent intent) {
        if (!isAdded() || intent.resolveActivity(requireContext().getPackageManager()) == null) {
            return false;
        }
        Toast.makeText(requireContext(), R.string.reservation_detail_opening_navigation, Toast.LENGTH_SHORT).show();
        startActivity(intent);
        return true;
    }

    private void hideMeetingMap() {
        if (meetingPointMapView != null) {
            meetingPointMapView.setVisibility(View.GONE);
        }
        if (tvMeetingMapFallback != null) {
            tvMeetingMapFallback.setText(null);
            tvMeetingMapFallback.setVisibility(View.GONE);
        }
    }

    private void showMeetingMapFallback(@NonNull String message) {
        if (meetingPointMapView != null) {
            meetingPointMapView.setVisibility(View.GONE);
        }
        if (tvMeetingMapFallback != null) {
            tvMeetingMapFallback.setVisibility(View.VISIBLE);
            tvMeetingMapFallback.setText(message);
        }
    }

    private boolean hasConfiguredMapsApiKey() {
        try {
            PackageManager packageManager = requireContext().getPackageManager();
            ApplicationInfo applicationInfo;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                applicationInfo = packageManager.getApplicationInfo(
                        requireContext().getPackageName(),
                        PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA)
                );
            } else {
                @SuppressWarnings("deprecation")
                ApplicationInfo legacyApplicationInfo = packageManager.getApplicationInfo(
                        requireContext().getPackageName(),
                        PackageManager.GET_META_DATA
                );
                applicationInfo = legacyApplicationInfo;
            }

            Bundle metaData = applicationInfo.metaData;
            String apiKey = metaData != null ? metaData.getString(MAPS_API_KEY_META_DATA, "") : "";
            return apiKey != null && !apiKey.trim().isEmpty();
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private boolean isGooglePlayServicesAvailable() {
        return GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(requireContext())
                == ConnectionResult.SUCCESS;
    }

    private int dpToPx(int valueInDp) {
        return Math.round(valueInDp * requireContext().getResources().getDisplayMetrics().density);
    }

    private void bindStatusChip(@NonNull TextView tvStatus, @NonNull String status) {
        int bgColor;
        int textColor;
        switch (ReservationStatus.normalize(status)) {
            case ReservationStatus.CANCELLED:
                bgColor = ContextCompat.getColor(requireContext(), R.color.detail_cancel_card_bg);
                textColor = ContextCompat.getColor(requireContext(), R.color.detail_cupos_low);
                break;
            case ReservationStatus.FINISHED:
                bgColor = ContextCompat.getColor(requireContext(), R.color.detail_meeting_bg);
                textColor = ContextCompat.getColor(requireContext(), R.color.explore_price_green);
                break;
            case ReservationStatus.CONFIRMED:
            default:
                bgColor = ContextCompat.getColor(requireContext(), R.color.filter_primary_light);
                textColor = ContextCompat.getColor(requireContext(), R.color.filter_primary);
                break;
        }
        tvStatus.setBackgroundTintList(ColorStateList.valueOf(bgColor));
        tvStatus.setTextColor(textColor);
    }

    private void updateActionButtons(@NonNull ReservationItem reservation) {
        if (btnCancelReservation == null || btnFinishReservation == null) return;

        boolean canCancel = reservation.canCancel();
        boolean canFinish = reservation.canFinish();
        btnCancelReservation.setEnabled(canCancel);
        btnFinishReservation.setEnabled(canFinish);

        if (!canCancel) {
            btnCancelReservation.setText(
                    ReservationStatus.CANCELLED.equals(ReservationStatus.normalize(reservation.status))
                            ? R.string.reservation_detail_cancelled
                            : R.string.reservation_detail_cancel);
        } else {
            btnCancelReservation.setText(R.string.reservation_detail_cancel);
        }

        if (!canFinish) {
            btnFinishReservation.setText(
                    ReservationStatus.FINISHED.equals(ReservationStatus.normalize(reservation.status))
                            ? R.string.reservation_detail_finished
                            : R.string.reservation_detail_finish);
        } else {
            btnFinishReservation.setText(R.string.reservation_detail_finish);
        }
    }

    private void promptCancelReservation() {
        if (loadedReservation == null || reservationRepository == null) return;
        if (!loadedReservation.canCancel()) return;

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.reservation_detail_cancel)
                .setMessage(getString(
                        R.string.reservation_detail_cancel_message,
                        loadedReservation.cancellationSummary()))
                .setNegativeButton(R.string.detail_booking_cancel, null)
                .setPositiveButton(R.string.reservation_detail_cancel_confirm, null)
                .create();
        dialog.setOnShowListener(dlg -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            setActionButtonsEnabled(false);
            reservationRepository.cancelReservation(effectiveUid, loadedReservation, new ReservationRepository.ActionCallback() {
                @Override
                public void onSuccess() {
                    if (!isAdded()) return;
                    dialog.dismiss();
                    Toast.makeText(requireContext(), R.string.reservation_detail_cancel_success, Toast.LENGTH_SHORT).show();
                    Navigation.findNavController(requireView()).popBackStack();
                }

                @Override
                public void onError(@NonNull String message) {
                    if (!isAdded()) return;
                    dialog.dismiss();
                    setActionButtonsEnabled(true);
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                }
            });
        }));
        dialog.show();
    }

    private void finishReservation() {
        if (loadedReservation == null || reservationRepository == null) return;
        if (!loadedReservation.canFinish()) return;

        setActionButtonsEnabled(false);
        reservationRepository.finishReservation(effectiveUid, loadedReservation, new ReservationRepository.ActionCallback() {
            @Override
            public void onSuccess() {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), R.string.reservation_detail_finish_success, Toast.LENGTH_SHORT).show();
                Navigation.findNavController(requireView()).popBackStack();
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isAdded()) return;
                setActionButtonsEnabled(true);
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setActionButtonsEnabled(boolean enabled) {
        if (btnCancelReservation != null) {
            btnCancelReservation.setEnabled(enabled && loadedReservation != null && loadedReservation.canCancel());
        }
        if (btnFinishReservation != null) {
            btnFinishReservation.setEnabled(enabled && loadedReservation != null && loadedReservation.canFinish());
        }
    }

    private void setLoading(boolean loading) {
        if (progress != null) {
            progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (contentRoot != null) {
            contentRoot.setVisibility(loading ? View.GONE : contentRoot.getVisibility());
        }
        if (btnCancelReservation != null) {
            btnCancelReservation.setEnabled(!loading);
        }
        if (btnFinishReservation != null) {
            btnFinishReservation.setEnabled(!loading);
        }
    }

    private static final class MapMarkerSpec {
        @NonNull
        final LatLng position;
        @NonNull
        final String title;
        @Nullable
        final String snippet;
        final float hue;

        private MapMarkerSpec(
                @NonNull LatLng position,
                @NonNull String title,
                @Nullable String snippet,
                float hue
        ) {
            this.position = position;
            this.title = title;
            this.snippet = snippet;
            this.hue = hue;
        }
    }
}
