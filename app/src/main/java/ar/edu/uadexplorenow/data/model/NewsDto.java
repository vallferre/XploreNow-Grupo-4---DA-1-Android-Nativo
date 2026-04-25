package ar.edu.uadexplorenow.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ar.edu.uadexplorenow.domain.NewsItem;

public class NewsDto {

    @Nullable public String id;
    @Nullable public String title;
    @Nullable public String brief;
    @Nullable public String summary;
    @Nullable public String description;
    @Nullable public String body;
    @Nullable public String imageUrl;
    @Nullable public String image_url;
    @Nullable public String coverImageUrl;
    @Nullable public String cover_image_url;
    @Nullable public String activityId;
    @Nullable public String activity_id;
    @Nullable public String destination;
    @Nullable public String type;
    @Nullable public String tag;
    @Nullable public Boolean featured;
    @Nullable public Boolean offer;

    @Nullable
    public NewsItem toNewsItem(@NonNull String fallbackId) {
        String safeTitle = safe(title);
        if (safeTitle.isEmpty()) {
            return null;
        }

        String safeDescription = firstNonBlank(description, body, summary, brief);
        String safeBrief = firstNonBlank(brief, summary, safeDescription);
        String safeImage = firstNonBlank(imageUrl, image_url, coverImageUrl, cover_image_url);
        String safeActivityId = firstNonBlank(activityId, activity_id);
        String safeTag = firstNonBlank(tag, type);
        String safeDestination = safe(destination);
        boolean safeFeatured = Boolean.TRUE.equals(featured) || Boolean.TRUE.equals(offer);

        return new NewsItem(
                firstNonBlank(id, fallbackId),
                safeTitle,
                safeBrief,
                safeDescription,
                safeImage,
                safeTag,
                safeDestination,
                safeActivityId,
                safeFeatured
        );
    }

    @NonNull
    private static String firstNonBlank(@Nullable String... values) {
        if (values == null) return "";
        for (String value : values) {
            String safe = safe(value);
            if (!safe.isEmpty()) {
                return safe;
            }
        }
        return "";
    }

    @NonNull
    private static String safe(@Nullable String value) {
        return value != null ? value.trim() : "";
    }
}
