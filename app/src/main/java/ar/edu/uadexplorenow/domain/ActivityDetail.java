package ar.edu.uadexplorenow.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ar.edu.uadexplorenow.data.model.ActivityRtdbDto;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.time.Instant;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
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

    public static final class BookingSlot {
        public final String slotId;
        public final String startAtIso;
        public final long availableSpots;

        BookingSlot(@NonNull String slotId, @NonNull String startAtIso, long availableSpots) {
            this.slotId = slotId;
            this.startAtIso = startAtIso;
            this.availableSpots = availableSpots;
        }

        @NonNull
        public String formattedDate() {
            ZonedDateTime zonedDateTime = parseFlexibleDateTime(startAtIso);
            if (zonedDateTime == null) return startAtIso;
            return zonedDateTime.toLocalDate().format(DateTimeFormatter.ofPattern("dd MMM yyyy", new Locale("es", "AR")));
        }

        @NonNull
        public String formattedTime() {
            ZonedDateTime zonedDateTime = parseFlexibleDateTime(startAtIso);
            if (zonedDateTime == null) return "";
            return zonedDateTime.format(DateTimeFormatter.ofPattern("HH:mm"));
        }

        @Nullable
        public LocalDate localDate() {
            ZonedDateTime zonedDateTime = parseFlexibleDateTime(startAtIso);
            return zonedDateTime != null ? zonedDateTime.toLocalDate() : null;
        }

        @Nullable
        public LocalTime localTime() {
            ZonedDateTime zonedDateTime = parseFlexibleDateTime(startAtIso);
            return zonedDateTime != null ? zonedDateTime.toLocalTime() : null;
        }

        @NonNull
        public String displayLabel() {
            String date = formattedDate();
            String time = formattedTime();
            return time.isEmpty() ? date : date + " · " + time;
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
    public final String day;
    public final String dateIso;
    public final List<BookingSlot> bookingSlots;
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
            List<BookingSlot> bookingSlots,
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
        this.bookingSlots = bookingSlots != null ? bookingSlots : Collections.emptyList();
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
                day, dateIso, buildSingleSlotList(dateIso, spots), policy
        );
    }

    @Nullable
    public static ActivityDetail fromRtdbDto(@Nullable ActivityRtdbDto raw, @NonNull String pathId) {
        if (raw == null || raw.name == null || raw.name.isEmpty()) return null;

        String stableId = str(pathId);
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
                day, dateIso, parseBookingSlots(raw.schedule, dateIso, spots), policy
        );
    }

    @Nullable
    public BookingSlot findBookingSlot(@Nullable String slotId, @Nullable String startAtIso) {
        for (BookingSlot slot : bookingSlots) {
            if (!slotIdIsEmpty(slotId) && slot.slotId.equals(slotId)) {
                return slot;
            }
            if (!slotIdIsEmpty(startAtIso) && slot.startAtIso.equals(startAtIso)) {
                return slot;
            }
        }
        return null;
    }

    @NonNull
    public String formattedWhenLine() {
        if (!day.isEmpty()) {
            String dayPart = capitalizeDay(day.trim());
            LocalTime time = primaryBookingTime();
            if (time == null) {
                return dayPart;
            }
            return dayPart + " · " + time.format(DateTimeFormatter.ofPattern("HH:mm"));
        }
        if (!bookingSlots.isEmpty()) {
            BookingSlot slot = bookingSlots.get(0);
            String date = slot.formattedDate();
            String time = slot.formattedTime();
            if (!time.isEmpty()) {
                return date + " · " + time;
            }
            if (!date.equals(slot.startAtIso)) {
                return date;
            }
        }
        if (dateIso.isEmpty() && day.isEmpty()) return "";

        ZonedDateTime z = parseFlexibleDateTime(dateIso);
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
        return cancellationTypeLabel(cancellationPolicy.type);
    }

    @NonNull
    public static String cancellationTypeLabel(@Nullable String rawType) {
        if (rawType == null || rawType.trim().isEmpty()) return "";
        String t = rawType.toLowerCase(Locale.ROOT).trim();
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

    @NonNull
    public String categoryLabel() {
        return ActivityItem.categoryLabel(category);
    }

    @NonNull
    public String formattedDurationLong() {
        if (durationMinutes <= 0) return "—";
        if (durationMinutes % 60 == 0) {
            long h = durationMinutes / 60;
            return h == 1 ? "1 hora" : h + " horas";
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

    @Nullable
    public DayOfWeek bookingDayOfWeek() {
        String normalized = ActivityItem.normalizeDay(day);
        switch (normalized) {
            case "lunes":
                return DayOfWeek.MONDAY;
            case "martes":
                return DayOfWeek.TUESDAY;
            case "miercoles":
                return DayOfWeek.WEDNESDAY;
            case "jueves":
                return DayOfWeek.THURSDAY;
            case "viernes":
                return DayOfWeek.FRIDAY;
            case "sabado":
                return DayOfWeek.SATURDAY;
            case "domingo":
                return DayOfWeek.SUNDAY;
            default:
                return null;
        }
    }

    @Nullable
    public LocalDate nextAvailableBookingDateFrom(@NonNull LocalDate baseDate) {
        DayOfWeek expectedDay = bookingDayOfWeek();
        if (expectedDay == null) {
            if (!bookingSlots.isEmpty() && bookingSlots.get(0).localDate() != null) {
                LocalDate date = bookingSlots.get(0).localDate();
                return date != null && !date.isBefore(baseDate) ? date : null;
            }
            ZonedDateTime zonedDateTime = parseFlexibleDateTime(dateIso);
            if (zonedDateTime != null) {
                LocalDate date = zonedDateTime.toLocalDate();
                return !date.isBefore(baseDate) ? date : null;
            }
            return null;
        }
        int offset = (expectedDay.getValue() - baseDate.getDayOfWeek().getValue() + 7) % 7;
        return baseDate.plusDays(offset);
    }

    @NonNull
    public List<LocalTime> bookingTimes() {
        LinkedHashSet<LocalTime> uniqueTimes = new LinkedHashSet<>();
        for (BookingSlot slot : bookingSlots) {
            LocalTime time = slot.localTime();
            if (time != null) {
                uniqueTimes.add(time.withSecond(0).withNano(0));
            }
        }
        LocalTime fallback = primaryBookingTime();
        if (fallback != null) {
            uniqueTimes.add(fallback.withSecond(0).withNano(0));
        }
        return new ArrayList<>(uniqueTimes);
    }

    @NonNull
    public BookingSlot buildCalendarBookingSlot(@NonNull LocalDate selectedDate, @Nullable LocalTime selectedTime) {
        LocalTime resolvedTime = selectedTime != null ? selectedTime : primaryBookingTime();
        String slotId = "calendar_" + selectedDate.toString()
                + (resolvedTime != null ? "_" + resolvedTime.toString().replace(":", "") : "");
        if (resolvedTime != null) {
            ZonedDateTime zonedDateTime = ZonedDateTime.of(selectedDate, resolvedTime, ZoneId.systemDefault());
            return new BookingSlot(slotId, zonedDateTime.toInstant().toString(), availableSpots);
        }
        return new BookingSlot(slotId, selectedDate.toString(), availableSpots);
    }

    @NonNull
    private static List<BookingSlot> buildSingleSlotList(@NonNull String dateIso, long availableSpots) {
        List<BookingSlot> out = new ArrayList<>();
        if (!dateIso.isEmpty()) {
            out.add(new BookingSlot("default", dateIso, availableSpots));
        }
        return out;
    }

    @NonNull
    private static List<BookingSlot> parseBookingSlots(
            @Nullable JsonElement raw,
            @NonNull String fallbackDateIso,
            long fallbackSpots
    ) {
        List<BookingSlot> out = new ArrayList<>();
        parseBookingSlotElement(raw, "slot", fallbackSpots, out);
        if (out.isEmpty()) {
            out.addAll(buildSingleSlotList(fallbackDateIso, fallbackSpots));
        }
        out.sort(Comparator.comparingLong(ActivityDetail::slotSortKey));
        return out;
    }

    private static void parseBookingSlotElement(
            @Nullable JsonElement raw,
            @NonNull String fallbackId,
            long fallbackSpots,
            @NonNull List<BookingSlot> out
    ) {
        if (raw == null || raw.isJsonNull()) return;

        if (raw.isJsonArray()) {
            JsonArray array = raw.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                parseBookingSlotElement(array.get(i), fallbackId + "_" + i, fallbackSpots, out);
            }
            return;
        }

        if (raw.isJsonObject()) {
            JsonObject obj = raw.getAsJsonObject();
            if (looksLikeSlot(obj)) {
                BookingSlot slot = parseSlotObject(obj, fallbackId, fallbackSpots);
                if (slot != null) {
                    out.add(slot);
                }
                return;
            }
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                parseBookingSlotElement(entry.getValue(), entry.getKey(), fallbackSpots, out);
            }
            return;
        }

        if (raw.isJsonPrimitive()) {
            JsonPrimitive primitive = raw.getAsJsonPrimitive();
            if (primitive.isString() || primitive.isNumber()) {
                String value = primitive.getAsString().trim();
                if (!value.isEmpty()) {
                    out.add(new BookingSlot(fallbackId, value, fallbackSpots));
                }
            }
        }
    }

    private static boolean looksLikeSlot(@NonNull JsonObject obj) {
        return hasAny(obj,
                "id", "slot_id", "slotId",
                "date", "date_time", "dateTime",
                "start_at", "startAt",
                "time", "start_time", "startTime");
    }

    @Nullable
    private static BookingSlot parseSlotObject(
            @NonNull JsonObject obj,
            @NonNull String fallbackId,
            long fallbackSpots
    ) {
        String slotId = firstNonBlank(
                stringOrNumber(obj, "id"),
                stringOrNumber(obj, "slot_id"),
                stringOrNumber(obj, "slotId"),
                fallbackId);
        String combinedDateTime = combineDateAndTime(
                firstNonBlank(
                        stringOrNumber(obj, "date"),
                        stringOrNumber(obj, "activity_date"),
                        stringOrNumber(obj, "activityDate")),
                firstNonBlank(
                        stringOrNumber(obj, "time"),
                        stringOrNumber(obj, "hour"),
                        stringOrNumber(obj, "start_time"),
                        stringOrNumber(obj, "startTime")));
        String startAtIso = firstNonBlank(
                stringOrNumber(obj, "start_at"),
                stringOrNumber(obj, "startAt"),
                stringOrNumber(obj, "date_time"),
                stringOrNumber(obj, "dateTime"),
                combinedDateTime);
        if (startAtIso.isEmpty()) return null;
        long spots = longValue(obj, "available_spots");
        if (spots <= 0) {
            long alt = longValue(obj, "availableSpots");
            spots = alt > 0 ? alt : fallbackSpots;
        }
        return new BookingSlot(slotId, startAtIso, spots);
    }

    private static long slotSortKey(@NonNull BookingSlot slot) {
        ZonedDateTime zonedDateTime = parseFlexibleDateTime(slot.startAtIso);
        return zonedDateTime != null ? zonedDateTime.toInstant().toEpochMilli() : Long.MAX_VALUE;
    }

    @Nullable
    private LocalTime primaryBookingTime() {
        for (BookingSlot slot : bookingSlots) {
            LocalTime time = slot.localTime();
            if (time != null) {
                return time.withSecond(0).withNano(0);
            }
        }
        ZonedDateTime zonedDateTime = parseFlexibleDateTime(dateIso);
        if (zonedDateTime != null) {
            return zonedDateTime.toLocalTime().withSecond(0).withNano(0);
        }
        return null;
    }

    @Nullable
    private static ZonedDateTime parseFlexibleDateTime(@Nullable String raw) {
        String value = str(raw);
        if (value.isEmpty()) return null;

        try {
            return Instant.parse(value).atZone(ZoneId.systemDefault());
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault());
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(value).atZone(ZoneId.systemDefault());
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(value).atStartOfDay(ZoneId.systemDefault());
        } catch (DateTimeParseException ignored) {
        }
        return null;
    }

    @NonNull
    private static String combineDateAndTime(@Nullable String rawDate, @Nullable String rawTime) {
        String date = str(rawDate);
        String time = str(rawTime);
        if (date.isEmpty()) return "";
        if (date.contains("T")) return date;
        if (time.isEmpty()) return date;
        try {
            LocalDate localDate = LocalDate.parse(date);
            LocalTime localTime = LocalTime.parse(time);
            return LocalDateTime.of(localDate, localTime).toString();
        } catch (DateTimeParseException ignored) {
        }
        return date + "T" + time;
    }

    @NonNull
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

    @NonNull
    private static String capitalizeDay(@NonNull String d) {
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

    @NonNull
    private static String buildFallbackDescription(String name, String dest, String cat) {
        String catLabel = ActivityItem.categoryLabel(cat);
        if (!dest.isEmpty() && !catLabel.isEmpty()) {
            return name + " en " + dest + ". Una experiencia " + catLabel.toLowerCase(Locale.getDefault())
                    + " para descubrir el destino con guia especializado.";
        }
        if (!dest.isEmpty()) {
            return "Disfruta esta actividad en " + dest + ".";
        }
        return "Descubri todos los detalles y reserva tu lugar.";
    }

    @SuppressWarnings("unchecked")
    @NonNull
    private static List<String> parsePhotoUrls(@NonNull DocumentSnapshot doc) {
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
    @NonNull
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

    private static boolean hasAny(@NonNull JsonObject obj, @NonNull String... keys) {
        for (String key : keys) {
            if (obj.has(key)) return true;
        }
        return false;
    }

    @NonNull
    private static String firstNonBlank(@Nullable String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private static boolean slotIdIsEmpty(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }

    @Nullable
    private static String stringOrNumber(@NonNull JsonObject obj, @NonNull String key) {
        JsonElement value = obj.get(key);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) return null;
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (primitive.isString() || primitive.isNumber()) {
            return primitive.getAsString().trim();
        }
        return null;
    }

    private static long longValue(@NonNull JsonObject obj, @NonNull String key) {
        JsonElement value = obj.get(key);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) return 0L;
        try {
            JsonPrimitive primitive = value.getAsJsonPrimitive();
            if (primitive.isNumber()) return primitive.getAsLong();
            if (primitive.isString()) return Long.parseLong(primitive.getAsString().trim());
        } catch (NumberFormatException ignored) {
        }
        return 0L;
    }

    @NonNull
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
}
