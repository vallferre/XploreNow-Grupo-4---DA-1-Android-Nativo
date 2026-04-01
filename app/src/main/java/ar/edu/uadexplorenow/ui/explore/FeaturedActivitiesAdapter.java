package ar.edu.uadexplorenow.ui.explore;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import ar.edu.uadexplorenow.R;
import ar.edu.uadexplorenow.domain.ActivityItem;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class FeaturedActivitiesAdapter extends RecyclerView.Adapter<FeaturedActivitiesAdapter.Holder> {

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

        if (!a.coverImageUrl.isEmpty()) {
            Glide.with(h.ivCover.getContext())
                    .load(a.coverImageUrl)
                    .centerCrop()
                    .placeholder(R.color.explore_search_bg)
                    .into(h.ivCover);
        } else {
            h.ivCover.setImageResource(android.R.color.darker_gray);
        }

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
        final TextView tvCategoryTag;
        final TextView tvName;
        final TextView tvMeta;
        final TextView tvPrice;

        Holder(@NonNull View itemView) {
            super(itemView);
            ivCover = itemView.findViewById(R.id.ivCover);
            tvCategoryTag = itemView.findViewById(R.id.tvCategoryTag);
            tvName = itemView.findViewById(R.id.tvName);
            tvMeta = itemView.findViewById(R.id.tvMeta);
            tvPrice = itemView.findViewById(R.id.tvPrice);
        }
    }
}
