package ar.edu.uadexplorenow.ui.explore;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import ar.edu.uadexplorenow.R;
import ar.edu.uadexplorenow.domain.ActivityItem;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class FeaturedActivitiesAdapter extends RecyclerView.Adapter<FeaturedActivitiesAdapter.Holder> {

    private final List<ActivityItem> items = new ArrayList<>();
    private Consumer<String> onItemClick;
    private Consumer<ActivityItem> onFavoriteClick;
    @NonNull
    private Set<String> favoriteIds = Collections.emptySet();

    public void setOnItemClick(Consumer<String> listener) {
        onItemClick = listener;
    }

    public void setOnFavoriteClick(@Nullable Consumer<ActivityItem> listener) {
        onFavoriteClick = listener;
    }

    public void setFavoriteIdSet(@Nullable Set<String> ids) {
        favoriteIds = ids != null ? new HashSet<>(ids) : Collections.emptySet();
        notifyDataSetChanged();
    }

    public void submit(List<ActivityItem> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_featured_activity, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        ActivityItem a = items.get(position);
        h.tvName.setText(shortTitle(a.name));
        h.tvCategoryTag.setText(ActivityItem.categoryLabel(a.category));
        String meta = a.destination.trim();
        String dayLbl = a.dayDisplayLabel();
        if (!dayLbl.isEmpty()) {
            meta = meta.isEmpty() ? dayLbl : meta + " · " + dayLbl;
        }
        String dur = a.formattedDurationHours();
        if (!dur.isEmpty()) {
            meta = meta.isEmpty() ? dur : meta + " · " + dur;
        }
        h.tvMeta.setText(meta);
        h.tvPrice.setText(a.formattedPrice());
        if (a.reviewCount <= 0) {
            h.tvRating.setVisibility(View.GONE);
        } else {
            h.tvRating.setVisibility(View.VISIBLE);
            h.tvRating.setText(h.itemView.getContext().getString(
                    R.string.explore_rating_with_count, a.rating, a.reviewCount));
        }

        if (!a.coverImageUrl.isEmpty()) {
            Glide.with(h.ivCover.getContext())
                    .load(a.coverImageUrl)
                    .centerCrop()
                    .placeholder(R.color.explore_search_bg)
                    .into(h.ivCover);
        } else {
            h.ivCover.setImageResource(android.R.color.darker_gray);
        }

        boolean fav = favoriteIds.contains(a.id);
        AllActivitiesAdapter.bindFavoriteIcon(h.btnFavorite, fav, Color.WHITE);
        h.btnFavorite.setOnClickListener(v -> {
            if (onFavoriteClick != null) {
                onFavoriteClick.accept(a);
            }
        });

        h.itemView.setOnClickListener(v -> {
            int pos = h.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && onItemClick != null) {
                onItemClick.accept(items.get(pos).id);
            }
        });
    }

    /** Título más corto para tarjetas del carrusel. */
    private static String shortTitle(String name) {
        if (name == null) return "";
        int comma = name.indexOf(',');
        if (comma > 12 && comma < 48) return name.substring(0, comma).trim();
        if (name.length() > 52) return name.substring(0, 49).trim() + "…";
        return name;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView ivCover;
        final ImageButton btnFavorite;
        final TextView tvCategoryTag;
        final TextView tvName;
        final TextView tvMeta;
        final TextView tvRating;
        final TextView tvPrice;

        Holder(@NonNull View itemView) {
            super(itemView);
            ivCover = itemView.findViewById(R.id.ivCover);
            btnFavorite = itemView.findViewById(R.id.btnFavorite);
            tvCategoryTag = itemView.findViewById(R.id.tvCategoryTag);
            tvName = itemView.findViewById(R.id.tvName);
            tvMeta = itemView.findViewById(R.id.tvMeta);
            tvRating = itemView.findViewById(R.id.tvRating);
            tvPrice = itemView.findViewById(R.id.tvPrice);
        }
    }
}
