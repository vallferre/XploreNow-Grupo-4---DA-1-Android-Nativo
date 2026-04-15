package ar.edu.uadexplorenow.ui.history;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import ar.edu.uadexplorenow.R;
import ar.edu.uadexplorenow.domain.ReservationItem;
import ar.edu.uadexplorenow.domain.ReservationStatus;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ActivityHistoryAdapter extends RecyclerView.Adapter<ActivityHistoryAdapter.Holder> {

    private final List<ReservationItem> items = new ArrayList<>();
    private Consumer<ReservationItem> onItemClick;

    public void setOnItemClick(Consumer<ReservationItem> listener) {
        onItemClick = listener;
    }

    public void submit(List<ReservationItem> data) {
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
        ReservationItem item = items.get(position);
        holder.tvTitle.setText(item.activityName);
        holder.tvDateDestination.setText(
                holder.itemView.getContext().getString(
                        R.string.history_card_date_destination_fmt,
                        item.formattedScheduleCompact(),
                        item.destinationLabel()));
        holder.tvGuide.setText(item.guideLabel());
        holder.tvDuration.setText(item.formattedDuration());
        holder.tvRating.setText(item.ratingLabel());
        holder.tvStatus.setText(item.statusLabel());
        bindStatus(holder, item.status);

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

        holder.itemView.setOnClickListener(v -> {
            int adapterPosition = holder.getAdapterPosition();
            if (adapterPosition != RecyclerView.NO_POSITION && onItemClick != null) {
                onItemClick.accept(items.get(adapterPosition));
            }
        });
    }

    private void bindStatus(@NonNull Holder holder, @NonNull String status) {
        int bgColor;
        int textColor;
        switch (ReservationStatus.normalize(status)) {
            case ReservationStatus.CANCELLED:
                bgColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.detail_cancel_card_bg);
                textColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.detail_cupos_low);
                break;
            case ReservationStatus.FINISHED:
                bgColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.detail_meeting_bg);
                textColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.explore_price_green);
                break;
            case ReservationStatus.CONFIRMED:
            default:
                bgColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.filter_primary_light);
                textColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.filter_primary);
                break;
        }
        holder.tvStatus.setBackgroundTintList(ColorStateList.valueOf(bgColor));
        holder.tvStatus.setTextColor(textColor);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView ivThumb;
        final TextView tvStatus;
        final TextView tvTitle;
        final TextView tvDateDestination;
        final TextView tvGuide;
        final TextView tvCategory;
        final TextView tvDuration;
        final TextView tvRating;

        Holder(@NonNull View itemView) {
            super(itemView);
            ivThumb = itemView.findViewById(R.id.ivThumb);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvDateDestination = itemView.findViewById(R.id.tvDateDestination);
            tvGuide = itemView.findViewById(R.id.tvGuide);
            tvCategory = itemView.findViewById(R.id.tvCategory);
            tvDuration = itemView.findViewById(R.id.tvDuration);
            tvRating = itemView.findViewById(R.id.tvRating);
        }
    }
}
