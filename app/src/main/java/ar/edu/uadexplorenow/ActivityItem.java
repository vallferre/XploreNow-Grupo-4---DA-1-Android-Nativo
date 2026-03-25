package ar.edu.uadexplorenow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.firestore.DocumentSnapshot;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Actividad del catálogo (colección {@code activities} en Firestore).
 */
public final class ActivityItem {

    public final String id;
    public final String name;
    public final String destination;
    public final String category;
    public final long durationMinutes;
    public final double price;
    public final String currency;
    public final long availableSpots;
    public final double rating;
    public final boolean featured;
    public final String coverImageUrl;
    /** Día de la actividad tal como viene en Firestore (ej. "domingo", "miércoles"). */
    public final String day;

    public ActivityItem(
            String id,
            String name,
            String destination,
            String category,
            long durationMinutes,
            double price,
            String currency,
            long availableSpots,
            double rating,
            boolean featured,
            String coverImageUrl,
            String day
    ) {
        this.id = id;
        this.name = name != null ? name : "";
        this.destination = destination != null ? destination : "";
        this.category = category != null ? category : "";
        this.durationMinutes = durationMinutes;
        this.price = price;
        this.currency = currency != null ? currency : "";
        this.availableSpots = availableSpots;
        this.rating = rating;
        this.featured = featured;
        this.coverImageUrl = coverImageUrl != null ? coverImageUrl : "";
        this.day = day != null ? day : "";
    }

    @Nullable
    public static ActivityItem fromDocument(@NonNull DocumentSnapshot doc) {
        String name = doc.getString("name");
        if (name == null || name.isEmpty()) return null;

        String id = doc.getId();
        String destination = safeString(doc.getString("destination"));
        String category = safeString(doc.getString("category"));
        long duration = longVal(doc.getLong("duration_minutes"));
        double price = doubleVal(doc.get("price"));
        String currency = safeString(doc.getString("currency"));
        long spots = longVal(doc.getLong("available_spots"));
        double rating = doubleVal(doc.get("rating"));
        boolean isFeatured = Boolean.TRUE.equals(doc.getBoolean("is_featured"));
        String cover = safeString(doc.getString("cover_image_url"));
        String day = safeString(doc.getString("day"));

        return new ActivityItem(
                id, name, destination, category, duration, price, currency,
                spots, rating, isFeatured, cover, day
        );
    }

    /** Compara días ignorando mayúsculas y acentos (Firestore vs chips). */
    public static boolean sameDay(@NonNull String a, @NonNull String b) {
        return normalizeDay(a).equals(normalizeDay(b));
    }

    @NonNull
    public static String normalizeDay(@Nullable String d) {
        if (d == null) return "";
        String n = Normalizer.normalize(d.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.ROOT);
    }

    private static String safeString(@Nullable String s) {
        return s != null ? s : "";
    }

    private static long longVal(@Nullable Long l) {
        return l != null ? l : 0L;
    }

    private static double doubleVal(@Nullable Object o) {
        if (o == null) return 0;
        if (o instanceof Double) return (Double) o;
        if (o instanceof Long) return ((Long) o).doubleValue();
        if (o instanceof Integer) return ((Integer) o).doubleValue();
        return 0;
    }

    /** Etiqueta legible para chips (p. ej. free_tour → Free tour). */
    /**
     * Día de la semana para el catálogo (ej. {@code "domingo"} → {@code "Domingo"}).
     * Cadena vacía si no hay valor en Firestore.
     */
    @NonNull
    public String dayDisplayLabel() {
        if (day == null || day.trim().isEmpty()) return "";
        String t = day.trim();
        return t.substring(0, 1).toUpperCase(Locale.getDefault()) + t.substring(1);
    }

    @NonNull
    public static String categoryLabel(String categoryKey) {
        if (categoryKey == null || categoryKey.isEmpty()) return "";
        switch (categoryKey) {
            case "free_tour":
                return "Free tour";
            case "aventura":
                return "Aventura";
            case "gastronomica":
                return "Gastro";
            case "visita_guiada":
                return "Visita guiada";
            case "excursion":
                return "Excursión";
            default:
                String u = categoryKey.replace('_', ' ');
                return u.substring(0, 1).toUpperCase(Locale.getDefault()) + u.substring(1);
        }
    }

    @NonNull
    public String formattedDurationHours() {
        if (durationMinutes <= 0) return "";
        if (durationMinutes % 60 == 0) {
            return (durationMinutes / 60) + "h";
        }
        double h = durationMinutes / 60.0;
        return String.format(Locale.getDefault(), "%.1fh", h);
    }

    @NonNull
    public String formattedPrice() {
        if (price <= 0) return "Gratis";
        if ("ARS".equalsIgnoreCase(currency)) {
            return String.format(Locale.getDefault(), "$%.0f", price);
        }
        return String.format(Locale.getDefault(), "%s %.0f", currency, price);
    }

    @NonNull
    public String listPriceSuffix() {
        if (price <= 0) return "Gratis";
        if ("ARS".equalsIgnoreCase(currency)) {
            return String.format(Locale.getDefault(), "$%.0f / pers.", price);
        }
        return String.format(Locale.getDefault(), "%s %.0f / pers.", currency, price);
    }
}
