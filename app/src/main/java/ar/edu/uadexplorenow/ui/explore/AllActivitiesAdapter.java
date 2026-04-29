package ar.edu.uadexplorenow.ui.explore;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
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

public class AllActivitiesAdapter extends RecyclerView.Adapter<AllActivitiesAdapter.Holder> {

    private static final int CUPOS_UMBRAL = 5;

    /**
     * Altura vertical de una fila ya “bindeada” (títulos largos, cupos, etc.), para {@link NestedScrollView}.
     */
    static int measureVerticalSpanForItem(
            @NonNull ActivityItem item,
            int widthPx,
            @NonNull LayoutInflater inflater,
            @NonNull RecyclerView parentRv
    ) {
        View row = inflater.inflate(R.layout.item_activity_row, parentRv, false);
        populateRowViews(row, item, false);
        ViewGroup.LayoutParams rawLp = row.getLayoutParams();
        int top = 0;
        int bottom = 0;
        if (rawLp instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams mp = (ViewGroup.MarginLayoutParams) rawLp;
            top = mp.topMargin;
            bottom = mp.bottomMargin;
        }
        row.measure(
                View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        return row.getMeasuredHeight() + top + bottom;
    }

    static void populateRowViews(@NonNull View itemView, @NonNull ActivityItem a) {
        populateRowViews(itemView, a, true);
    }

    static void populateRowViews(@NonNull View itemView, @NonNull ActivityItem a, boolean loadImages) {
        ImageView ivThumb = itemView.findViewById(R.id.ivThumb);
        TextView tvTitle = itemView.findViewById(R.id.tvTitle);
        TextView tvLocation = itemView.findViewById(R.id.tvLocation);
        TextView tvTagCategory = itemView.findViewById(R.id.tvTagCategory);
        TextView tvTagDuration = itemView.findViewById(R.id.tvTagDuration);
        TextView tvPriceRow = itemView.findViewById(R.id.tvPriceRow);
        TextView tvCupos = itemView.findViewById(R.id.tvCupos);
        TextView tvRating = itemView.findViewById(R.id.tvRating);

        tvTitle.setText(a.name);
        String dayLbl = a.dayDisplayLabel();
        if (a.destination.trim().isEmpty()) {
            tvLocation.setText(dayLbl.isEmpty() ? "—" : dayLbl);
        } else {
            String loc = "📍 " + a.destination.trim();
            if (!dayLbl.isEmpty()) {
                loc += " · " + dayLbl;
            }
            tvLocation.setText(loc);
        }
        tvTagCategory.setText(ActivityItem.categoryLabel(a.category));
        tvTagDuration.setText(a.formattedDurationHours());
        tvPriceRow.setText(a.listPriceSuffix());
        if (a.reviewCount <= 0) {
            tvRating.setText(R.string.explore_rating_none);
        } else {
            tvRating.setText(itemView.getContext().getString(
                    R.string.explore_rating_with_count, a.rating, a.reviewCount));
        }

        if (a.availableSpots <= 0) {
            tvCupos.setVisibility(View.VISIBLE);
            tvCupos.setText(R.string.detail_no_spots);
            tvCupos.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.explore_muted));
        } else if (a.availableSpots <= CUPOS_UMBRAL) {
            tvCupos.setVisibility(View.VISIBLE);
            tvCupos.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.explore_warning));
            tvCupos.setText(itemView.getContext().getString(
                    R.string.explore_cupos_left, (int) a.availableSpots));
        } else {
            tvCupos.setVisibility(View.GONE);
        }

        itemView.setAlpha(a.availableSpots <= 0 ? 0.58f : 1f);

        if (loadImages && !a.coverImageUrl.isEmpty()) {
            Glide.with(ivThumb.getContext())
                    .load(a.coverImageUrl)
                    .centerCrop()
                    .placeholder(R.color.explore_search_bg)
                    .into(ivThumb);
        } else {
            ivThumb.setImageResource(R.color.explore_search_bg);
        }
    }

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
                .inflate(R.layout.item_activity_row, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        ActivityItem a = items.get(position);
        populateRowViews(h.itemView, a);

        boolean fav = favoriteIds.contains(a.id);
        bindFavoriteIcon(h.btnFavorite, fav, ContextCompat.getColor(h.itemView.getContext(), R.color.explore_title));
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

    static void bindFavoriteIcon(@NonNull ImageButton btn, boolean favorite, int borderTintColor) {
        if (favorite) {
            btn.setImageResource(R.drawable.ic_favorite_filled_24);
            ImageViewCompat.setImageTintList(btn, null);
        } else {
            btn.setImageResource(R.drawable.ic_favorite_border_24);
            ImageViewCompat.setImageTintList(btn, ColorStateList.valueOf(borderTintColor));
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView ivThumb;
        final ImageButton btnFavorite;
        final TextView tvTitle;
        final TextView tvLocation;
        final TextView tvTagCategory;
        final TextView tvTagDuration;
        final TextView tvPriceRow;
        final TextView tvCupos;
        final TextView tvRating;

        Holder(@NonNull View itemView) {
            super(itemView);
            ivThumb = itemView.findViewById(R.id.ivThumb);
            btnFavorite = itemView.findViewById(R.id.btnFavorite);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvLocation = itemView.findViewById(R.id.tvLocation);
            tvTagCategory = itemView.findViewById(R.id.tvTagCategory);
            tvTagDuration = itemView.findViewById(R.id.tvTagDuration);
            tvPriceRow = itemView.findViewById(R.id.tvPriceRow);
            tvCupos = itemView.findViewById(R.id.tvCupos);
            tvRating = itemView.findViewById(R.id.tvRating);
        }
    }
}
