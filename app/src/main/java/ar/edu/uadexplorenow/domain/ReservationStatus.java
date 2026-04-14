package ar.edu.uadexplorenow.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

public final class ReservationStatus {

    public static final String CONFIRMED = "confirmed";
    public static final String CANCELLED = "cancelled";
    public static final String FINISHED = "finished";

    private ReservationStatus() {}

    @NonNull
    public static String normalize(@Nullable String raw) {
        if (raw == null) return CONFIRMED;
        String value = raw.trim().toLowerCase(Locale.ROOT);
        switch (value) {
            case "confirmada":
            case "confirmed":
            case "reservada":
            case "reserved":
                return CONFIRMED;
            case "cancelada":
            case "cancelled":
            case "canceled":
                return CANCELLED;
            case "finalizada":
            case "finished":
            case "completed":
            case "realizada":
                return FINISHED;
            default:
                return CONFIRMED;
        }
    }

    @NonNull
    public static String label(@Nullable String raw) {
        switch (normalize(raw)) {
            case CANCELLED:
                return "Cancelada";
            case FINISHED:
                return "Finalizada";
            case CONFIRMED:
            default:
                return "Confirmada";
        }
    }

    public static boolean canCancel(@Nullable String raw) {
        return CONFIRMED.equals(normalize(raw));
    }

    public static boolean canFinish(@Nullable String raw) {
        return CONFIRMED.equals(normalize(raw));
    }
}
