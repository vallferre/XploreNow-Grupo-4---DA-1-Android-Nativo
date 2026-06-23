package ar.edu.uadexplorenow.ui.common;

import android.view.View;

import androidx.annotation.Nullable;

import ar.edu.uadexplorenow.R;

public final class OfflineBannerHelper {

    private OfflineBannerHelper() {}

    public static void show(@Nullable View root) {
        if (root == null) return;
        View banner = root.findViewById(R.id.offlineBanner);
        if (banner != null) {
            banner.setVisibility(View.VISIBLE);
        }
    }

    public static void hide(@Nullable View root) {
        if (root == null) return;
        View banner = root.findViewById(R.id.offlineBanner);
        if (banner != null) {
            banner.setVisibility(View.GONE);
        }
    }
}
