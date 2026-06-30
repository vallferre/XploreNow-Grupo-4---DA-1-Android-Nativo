package ar.edu.uadexplorenow.ui.common;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** Keeps top controls outside the status-bar touch area on edge-to-edge screens. */
public final class SystemBarInsetsHelper {

    private SystemBarInsetsHelper() {}

    public static void applyTopPadding(@NonNull View view) {
        int initialTop = view.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(view, (target, insets) -> {
            int statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            target.setPadding(
                    target.getPaddingLeft(),
                    initialTop + statusBarTop,
                    target.getPaddingRight(),
                    target.getPaddingBottom());
            return insets;
        });
        ViewCompat.requestApplyInsets(view);
    }

    public static void applyTopMargin(@NonNull View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams)) return;
        int initialTop = ((ViewGroup.MarginLayoutParams) params).topMargin;
        ViewCompat.setOnApplyWindowInsetsListener(view, (target, insets) -> {
            ViewGroup.LayoutParams currentParams = target.getLayoutParams();
            if (!(currentParams instanceof ViewGroup.MarginLayoutParams)) return insets;
            ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) currentParams;
            margins.topMargin = initialTop
                    + insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            target.setLayoutParams(margins);
            return insets;
        });
        ViewCompat.requestApplyInsets(view);
    }
}
