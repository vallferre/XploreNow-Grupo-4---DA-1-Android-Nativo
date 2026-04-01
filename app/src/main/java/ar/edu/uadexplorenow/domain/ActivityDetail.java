package ar.edu.uadexplorenow.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ar.edu.uadexplorenow.data.model.ActivityRtdbDto;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Datos completos de una actividad para la pantalla de detalle.
 */
public final class ActivityDetail {

    public static final class CancellationPolicy {
        public final String type;
        public final String description;
        public final long freeCancelHours;

        public CancellationPolicy(String type, String description, long freeCancelHours) {
            this.type = type != null ? type : "";
            this.description = description != null ? description : "";
            this.freeCancelHours = freeCancelHours;
        }

        public boolean hasContent() {
            return !type.isEmpty() || !description.isEmpty() || freeCancelHours > 0;
        }

        @SuppressWarnings("unchecked")
        @Nullable
        static CancellationPolicy fromFirestore(@Nullable Object raw) {
            if (!(raw instanceof Map)) return null;
            Map<String, Object> m = (Map<String, Object>) raw;
            String type = str((String) m.get("type"));
            String desc = str((String) m.get("description"));
            long hours = 0;
            Object h = m.get("free_cancel_hours");
            if (h instanceof Long) hours = (Long) h;
            else if (h instanceof Integer) hours = ((Integer) h).longValue();
            CancellationPolicy p = new CancellationPolicy(type, desc, hours);
            return p.hasContent() ? p : null;
        }
    }

    public final String id;
    public final String name;
    public final String destination;
    public final String category;
    public final long durationMinutes;
    public final double price;
    public final String currency;
    public final long availableSpots;
    public final double rating;
    public final long reviewCount;
    public final String guideName;
    public final String meetingPoint;
    public final String description;
    public final List<String> imageUrls;
    public final List<String> includes;
    public final List<String> languages;
    /** Día de la semana en español (ej. "domingo") desde Firestore. */
    public final String day;
    /** Fecha/hora ISO de la actividad (campo {@code date}). */
    public final String dateIso;
    @Nullable
    public final CancellationPolicy cancellationPolicy;

    public ActivityDetail(
            String id,
            String name,
            String destination,
            String category,
            long durationMinutes,
            double price,
            String currency,
            long availableSpots,
            double rating,
            long reviewCount,
            String guideName,
            String meetingPoint,
            String description,
            List<String> imageUrls,
            List<String> includes,
            List<String> languages,
            String day,
            String dateIso,
            @Nullable CancellationPolicy cancellationPolicy
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
        this.reviewCount = reviewCount;
        this.guideName = guideName != null ? guideName : "";
        this.meetingPoint = meetingPoint != null ? meetingPoint : "";
        this.description = description != null ? description : "";
        this.imageUrls = imageUrls != null ? imageUrls : Collections.emptyList();
        this.includes = includes != null ? includes : Collections.emptyList();
        this.languages = languages != null ? languages : Collections.emptyList();
        this.day = day != null ? day : "";
        this.dateIso = dateIso != null ? dateIso : "";
        this.cancellationPolicy = cancellationPolicy;
    }

    @Nullable
    public static ActivityDetail fromDocument(@NonNull DocumentSnapshot doc) {
        String name = doc.getString("name");
        if (name == null || name.isEmpty()) return null;

        String id = doc.getId();
        String destination = str(doc.getString("destination"));
        String category = str(doc.getString("category"));
        long duration = longVal(doc.getLong("duration_minutes"));
        double price = doubleVal(doc.get("price"));
        String currency = str(doc.getString("currency"));
        long spots = longVal(doc.getLong("available_spots"));
        double rating = doubleVal(doc.get("rating"));
        long reviews = longVal(doc.getLong("review_count"));
        String guide = str(doc.getString("guide_name"));
        String meeting = str(doc.getString("meeting_point"));
        String desc = str(doc.getString("description"));
        if (desc.isEmpty()) {
            desc = buildFallbackDescription(name, destination, category);
        }

        String day = str(doc.getString("day"));
        String dateIso = extractDateIso(doc);
        CancellationPolicy policy = CancellationPolicy.fromFirestore(doc.get("cancellation_policy"));

        String cover = str(doc.getString("cover_image_url"));
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        if (!cover.isEmpty()) ordered.add(cover);
        ordered.addAll(parsePhotoUrls(doc));
        List<String> urls = new ArrayList<>(ordered);

        return new ActivityDetail(
                id, name, destination, category, duration, price, currency,
                spots, rating, reviews, guide, meeting, desc,
                urls, parseStringList(doc.get("includes")), parseStringList(doc.get("language")),
                day, dateIso, policy
        );
    }

    /**
     * Detalle desde JSON de Realtime Database (misma forma de campos que Firestore).
     */
    @Nullable
    public static ActivityDetail fromRtdbDto(@Nullable ActivityRtdbDto raw, @NonNull String pathId) {
        if (raw == null || raw.name == null || raw.name.isEmpty()) return null;

        String stableId = (raw.id != null && !raw.id.isEmpty()) ? raw.id : pathId;
        String destination = str(raw.destination);
        String category = str(raw.category);
        long duration = raw.durationMinutes != null ? raw.durationMinutes : 0L;
        double price = raw.price != null ? raw.price : 0;
        String currency = str(raw.currency);
        long spots = raw.availableSpots != null ? raw.availableSpots : 0L;
        double rating = raw.rating != null ? raw.rating : 0;
        long reviews = raw.reviewCount != null ? raw.reviewCount : 0L;
        String guide = str(raw.guideName);
        String meeting = str(raw.meetingPoint);
        String desc = str(raw.description);
        if (desc.isEmpty()) {
            desc = buildFallbackDescription(raw.name, destination, category);
        }
        String day = str(raw.day);
        String dateIso = str(raw.date);

        CancellationPolicy policy = null;
        if (raw.cancellationPolicy != null) {
            ActivityRtdbDto.CancellationDto c = raw.cancellationPolicy;
            CancellationPolicy p = new CancellationPolicy(
                    str(c.type),
                    str(c.description),
                    c.freeCancelHours != null ? c.freeCancelHours : 0L);
            if (p.hasContent()) policy = p;
        }

        String cover = str(raw.coverImageUrl);
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        if (!cover.isEmpty()) ordered.add(cover);
        ordered.addAll(parsePhotoUrlsFromRtdb(raw.photos));
        List<String> urls = new ArrayList<>(ordered);

        List<String> includes = new ArrayList<>();
        if (raw.includes != null) {
            for (String s : raw.includes) {
                if (s != null && !s.isEmpty()) includes.add(s);
            }
        }
        List<String> langs = new ArrayList<>();
        if (raw.language != null) {
            for (String s : raw.language) {
                if (s != null && !s.isEmpty()) langs.add(s);
            }
        }

        return new ActivityDetail(
                stableId, raw.name, destination, category, duration, price, currency,
                spots, rating, reviews, guide, meeting, desc,
                urls, includes, langs,
                day, dateIso, policy
        );
    }

    private static List<String> parsePhotoUrlsFromRtdb(@Nullable List<ActivityRtdbDto.PhotoDto> photos) {
        List<String> out = new ArrayList<>();
        if (photos == null) return out;
        List<MapSort> tmp = new ArrayList<>();
        for (ActivityRtdbDto.PhotoDto p : photos) {
            if (p == null || p.url == null || p.url.isEmpty()) continue;
            long order = p.order != null ? p.order : 0L;
            tmp.add(new MapSort(order, p.url));
        }
        Collections.sort(tmp, Comparator.comparingLong(a -> a.order));
        for (MapSort e : tmp) out.add(e.url);
        return out;
    }

    /**
     * Solo día de la semana (ej. "Lunes") y hora local (ej. "07:30"), sin fecha calendario.
     */
    @NonNull
    public String formattedWhenLine() {
        if (dateIso.isEmpty() && day.isEmpty()) return "";

        ZonedDateTime z = null;
        if (!dateIso.isEmpty()) {
            try {
                z = Instant.parse(dateIso.trim()).atZone(ZoneId.systemDefault());
            } catch (DateTimeParseException ignored) {
            }
        }

        String dayPart = "";
        if (!day.isEmpty()) {
            dayPart = capitalizeDay(day.trim());
        } else if (z != null) {
            dayPart = capitalizeDay(z.format(DateTimeFormatter.ofPattern("EEEE", new Locale("es", "AR"))));
        }

        String timePart = "";
        if (z != null) {
            timePart = z.format(DateTimeFormatter.ofPattern("HH:mm"));
        }

        if (dayPart.isEmpty() && timePart.isEmpty()) return "";
        if (dayPart.isEmpty()) return timePart;
        if (timePart.isEmpty()) return dayPart;
        return dayPart + " · " + timePart;
    }

    @NonNull
    public String cancellationTypeLabel() {
        if (cancellationPolicy == null || cancellationPolicy.type.isEmpty()) return "";
        String t = cancellationPolicy.type.toLowerCase(Locale.ROOT).trim();
        switch (t) {
            case "flexible":
                return "Flexible";
            case "moderada":
                return "Moderada";
            case "estricta":
                return "Estricta";
            default:
                String u = t.replace('_', ' ');
                if (u.isEmpty()) return "";
                return u.substring(0, 1).toUpperCase(Locale.getDefault()) + u.substring(1);
        }
    }

    private static String capitalizeDay(String d) {
        if (d.isEmpty()) return "";
        return d.substring(0, 1).toUpperCase(Locale.getDefault()) + d.substring(1);
    }

    @NonNull
    private static String extractDateIso(@NonNull DocumentSnapshot doc) {
        Object v = doc.get("date");
        if (v instanceof String) return str((String) v);
        if (v instanceof Timestamp) {
            return ((Timestamp) v).toDate().toInstant().toString();
        }
        return "";
    }

    private static String buildFallbackDescription(String name, String dest, String cat) {
        String catLabel = ActivityItem.categoryLabel(cat);
        if (!dest.isEmpty() && !catLabel.isEmpty()) {
            return name + " en " + dest + ". Una experiencia " + catLabel.toLowerCase(Locale.getDefault())
                    + " para descubrir el destino con guía especializado.";
        }
        if (!dest.isEmpty()) {
            return "Disfrutá esta actividad en " + dest + ".";
        }
        return "Descubrí todos los detalles y reservá tu lugar.";
    }

    @SuppressWarnings("unchecked")
    private static List<String> parsePhotoUrls(DocumentSnapshot doc) {
        Object raw = doc.get("photos");
        if (!(raw instanceof List)) return new ArrayList<>();
        List<?> list = (List<?>) raw;
        List<MapSort> tmp = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map)) continue;
            Map<String, Object> m = (Map<String, Object>) o;
            Object u = m.get("url");
            if (!(u instanceof String) || ((String) u).isEmpty()) continue;
            long order = 0;
            Object ord = m.get("order");
            if (ord instanceof Long) order = (Long) ord;
            else if (ord instanceof Integer) order = ((Integer) ord).longValue();
            tmp.add(new MapSort(order, (String) u));
        }
        Collections.sort(tmp, Comparator.comparingLong(a -> a.order));
        List<String> out = new ArrayList<>();
        for (MapSort e : tmp) out.add(e.url);
        return out;
    }

    private static final class MapSort {
        final long order;
        final String url;

        MapSort(long order, String url) {
            this.order = order;
            this.url = url;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> parseStringList(@Nullable Object raw) {
        List<String> out = new ArrayList<>();
        if (!(raw instanceof List)) return out;
        for (Object o : (List<?>) raw) {
            if (o instanceof String && !((String) o).isEmpty()) {
                out.add((String) o);
            }
        }
        return out;
    }

    private static String str(@Nullable String s) {
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

    @NonNull
    public String categoryLabel() {
        return ActivityItem.categoryLabel(category);
    }

    /** Texto tipo "2 horas" / "2 horas 30 min" para la grilla. */
    @NonNull
    public String formattedDurationLong() {
        if (durationMinutes <= 0) return "—";
        if (durationMinutes % 60 == 0) {
            long h = durationMinutes / 60;
            if (h == 1) return "1 hora";
            return h + " horas";
        }
        long h = durationMinutes / 60;
        long m = durationMinutes % 60;
        if (h == 0) return m + " min";
        String hs = h == 1 ? "1 hora" : h + " horas";
        return hs + " " + m + " min";
    }

    @NonNull
    public String languagesDisplay() {
        if (languages.isEmpty()) return "—";
        List<String> up = new ArrayList<>();
        for (String code : languages) {
            if (code == null || code.isEmpty()) continue;
            up.add(code.toUpperCase(Locale.ROOT));
        }
        if (up.isEmpty()) return "—";
        return String.join(" / ", up);
    }

    @NonNull
    public String priceLarge() {
        if (price <= 0) return "Gratis";
        if ("ARS".equalsIgnoreCase(currency)) {
            return String.format(Locale.getDefault(), "$%.0f", price);
        }
        return String.format(Locale.getDefault(), "%.0f %s", price, currency);
    }
}
