package ar.edu.uadexplorenow;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class AllActivitiesAdapter extends RecyclerView.Adapter<AllActivitiesAdapter.Holder> {

    private static final int CUPOS_UMBRAL = 5;

    private final List<ActivityItem> items = new ArrayList<>();
    private Consumer<String> onItemClick;

    public void setOnItemClick(Consumer<String> listener) {
        onItemClick = listener;
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
        h.tvTitle.setText(a.name);
        String dayLbl = a.dayDisplayLabel();
        if (a.destination.trim().isEmpty()) {
            h.tvLocation.setText(dayLbl.isEmpty() ? "—" : dayLbl);
        } else {
            String loc = "📍 " + a.destination.trim();
            if (!dayLbl.isEmpty()) {
                loc += " · " + dayLbl;
            }
            h.tvLocation.setText(loc);
        }
        h.tvTagCategory.setText(ActivityItem.categoryLabel(a.category));
        h.tvTagDuration.setText(a.formattedDurationHours());
        h.tvPriceRow.setText(a.listPriceSuffix());
        h.tvRating.setText(h.itemView.getContext().getString(R.string.explore_rating, a.rating));

        if (a.availableSpots > 0 && a.availableSpots <= CUPOS_UMBRAL) {
            h.tvCupos.setVisibility(View.VISIBLE);
            h.tvCupos.setText(h.itemView.getContext().getString(
                    R.string.explore_cupos_left, (int) a.availableSpots));
        } else {
            h.tvCupos.setVisibility(View.GONE);
        }

        if (!a.coverImageUrl.isEmpty()) {
            Glide.with(h.ivThumb.getContext())
                    .load(a.coverImageUrl)
                    .centerCrop()
                    .placeholder(R.color.explore_search_bg)
                    .into(h.ivThumb);
        } else {
            h.ivThumb.setImageResource(android.R.color.darker_gray);
        }

        h.itemView.setOnClickListener(v -> {
            int pos = h.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && onItemClick != null) {
                onItemClick.accept(items.get(pos).id);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView ivThumb;
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
