package ar.edu.uadexplorenow.data.model;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ar.edu.uadexplorenow.domain.NewsItem;

/**
 * Parsea noticias/promociones desde un endpoint flexible: array u objeto.
 */
public final class NewsMapper {

    private NewsMapper() {}

    @NonNull
    public static List<NewsItem> toNewsItems(JsonElement root, Gson gson) {
        List<NewsItem> out = new ArrayList<>();
        if (root == null || root.isJsonNull()) return out;

        if (root.isJsonArray()) {
            JsonArray array = root.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                NewsDto dto = gson.fromJson(array.get(i), NewsDto.class);
                if (dto == null) continue;
                NewsItem item = dto.toNewsItem(String.valueOf(i));
                if (item != null) {
                    out.add(item);
                }
            }
            return out;
        }

        if (root.isJsonObject()) {
            JsonObject obj = root.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                NewsDto dto = gson.fromJson(entry.getValue(), NewsDto.class);
                if (dto == null) continue;
                NewsItem item = dto.toNewsItem(entry.getKey());
                if (item != null) {
                    out.add(item);
                }
            }
        }

        return out;
    }
}
