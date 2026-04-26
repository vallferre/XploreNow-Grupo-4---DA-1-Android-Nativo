package ar.edu.uadexplorenow.domain;

import androidx.annotation.NonNull;

import java.util.Locale;

public final class NewsItem {

    public final String id;
    public final String title;
    public final String brief;
    public final String description;
    public final String imageUrl;
    public final String tag;
    public final String destination;
    public final String relatedActivityId;
    public final boolean featured;

    public NewsItem(
            String id,
            String title,
            String brief,
            String description,
            String imageUrl,
            String tag,
            String destination,
            String relatedActivityId,
            boolean featured
    ) {
        this.id = id != null ? id : "";
        this.title = title != null ? title : "";
        this.brief = brief != null ? brief : "";
        this.description = description != null ? description : "";
        this.imageUrl = imageUrl != null ? imageUrl : "";
        this.tag = tag != null ? tag : "";
        this.destination = destination != null ? destination : "";
        this.relatedActivityId = relatedActivityId != null ? relatedActivityId : "";
        this.featured = featured;
    }

    public boolean hasRelatedActivity() {
        return !relatedActivityId.trim().isEmpty();
    }

    @NonNull
    public String displayTag() {
        String safeTag = tag.trim();
        if (!safeTag.isEmpty()) {
            String normalized = safeTag.replace('_', ' ');
            return normalized.substring(0, 1).toUpperCase(Locale.getDefault()) + normalized.substring(1);
        }
        if (featured) {
            return "Oferta";
        }
        return "Novedad";
    }

    @NonNull
    public String displayBrief() {
        if (!brief.trim().isEmpty()) return brief.trim();
        if (!description.trim().isEmpty()) return description.trim();
        return "";
    }

    @NonNull
    public String displayMeta() {
        if (!destination.trim().isEmpty()) {
            return destination.trim();
        }
        return displayTag();
    }
}
