package ar.edu.uadexplorenow.ui.history;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import ar.edu.uadexplorenow.R;
import ar.edu.uadexplorenow.domain.ActivityHistoryItem;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ActivityHistoryAdapter extends RecyclerView.Adapter<ActivityHistoryAdapter.Holder> {

    private final List<ActivityHistoryItem> items = new ArrayList<>();
    private Consumer<ActivityHistoryItem> onItemClick;

    public void setOnItemClick(Consumer<ActivityHistoryItem> listener) {
        onItemClick = listener;
    }

    public void submit(List<ActivityHistoryItem> data) {
        items.clear();
        if (data != null) {
            items.addAll(data);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_activity_history, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        ActivityHistoryItem item = items.get(position);
        holder.tvTitle.setText(item.name);
        holder.tvDateDestination.setText(
                holder.itemView.getContext().getString(
                        R.string.history_card_date_destination_fmt,
                        item.formattedDateShort(),
                        item.destinationLabel()));
        holder.tvGuide.setText(item.guideLabel());
        holder.tvDuration.setText(item.formattedDuration());
        holder.tvRating.setText(item.ratingLabel());

        String category = item.categoryLabel();
        holder.tvCategory.setVisibility(category.isEmpty() ? View.GONE : View.VISIBLE);
        holder.tvCategory.setText(category);

        if (!item.imageUrl.isEmpty()) {
            Glide.with(holder.ivThumb.getContext())
                    .load(item.imageUrl)
                    .centerCrop()
                    .placeholder(R.color.explore_search_bg)
                    .into(holder.ivThumb);
        } else {
            holder.ivThumb.setImageResource(R.color.explore_search_bg);
        }

        holder.itemView.setAlpha(item.hasDetail ? 1f : 0.82f);
        holder.itemView.setOnClickListener(v -> {
            int adapterPosition = holder.getAdapterPosition();
            if (adapterPosition != RecyclerView.NO_POSITION && onItemClick != null) {
                onItemClick.accept(items.get(adapterPosition));
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
        final TextView tvDateDestination;
        final TextView tvGuide;
        final TextView tvCategory;
        final TextView tvDuration;
        final TextView tvRating;

        Holder(@NonNull View itemView) {
            super(itemView);
            ivThumb = itemView.findViewById(R.id.ivThumb);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvDateDestination = itemView.findViewById(R.id.tvDateDestination);
            tvGuide = itemView.findViewById(R.id.tvGuide);
            tvCategory = itemView.findViewById(R.id.tvCategory);
            tvDuration = itemView.findViewById(R.id.tvDuration);
            tvRating = itemView.findViewById(R.id.tvRating);
        }
    }
}
