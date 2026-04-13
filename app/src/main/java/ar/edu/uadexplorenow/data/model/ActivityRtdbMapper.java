package ar.edu.uadexplorenow.data.model;

import ar.edu.uadexplorenow.domain.ActivityItem;
import ar.edu.uadexplorenow.domain.ActivityDetail;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parsea la respuesta de {@code activities.json} (array u objeto con claves numéricas).
 */
public final class ActivityRtdbMapper {

    private ActivityRtdbMapper() {}

    public static List<ActivityItem> toActivityItems(JsonElement root, Gson gson) {
        List<ActivityItem> out = new ArrayList<>();
        if (root == null || root.isJsonNull()) return out;

        if (root.isJsonArray()) {
            JsonArray arr = root.getAsJsonArray();
            for (int i = 0; i < arr.size(); i++) {
                ActivityRtdbDto dto = gson.fromJson(arr.get(i), ActivityRtdbDto.class);
                if (dto == null || dto.name == null || dto.name.isEmpty()) continue;
                String fallbackId = String.valueOf(i);
                out.add(dto.toActivityItem(fallbackId));
            }
        } else if (root.isJsonObject()) {
            JsonObject obj = root.getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                ActivityRtdbDto dto = gson.fromJson(e.getValue(), ActivityRtdbDto.class);
                if (dto == null || dto.name == null || dto.name.isEmpty()) continue;
                out.add(dto.toActivityItem(e.getKey()));
            }
        }

        return out;
    }

    public static Map<String, ActivityDetail> toActivityDetails(JsonElement root, Gson gson) {
        Map<String, ActivityDetail> out = new LinkedHashMap<>();
        if (root == null || root.isJsonNull()) return out;

        if (root.isJsonArray()) {
            JsonArray arr = root.getAsJsonArray();
            for (int i = 0; i < arr.size(); i++) {
                ActivityRtdbDto dto = gson.fromJson(arr.get(i), ActivityRtdbDto.class);
                ActivityDetail detail = ActivityDetail.fromRtdbDto(dto, String.valueOf(i));
                if (detail != null) {
                    out.put(detail.id, detail);
                }
            }
        } else if (root.isJsonObject()) {
            JsonObject obj = root.getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                ActivityRtdbDto dto = gson.fromJson(e.getValue(), ActivityRtdbDto.class);
                ActivityDetail detail = ActivityDetail.fromRtdbDto(dto, e.getKey());
                if (detail != null) {
                    out.put(detail.id, detail);
                }
            }
        }

        return out;
    }
}
