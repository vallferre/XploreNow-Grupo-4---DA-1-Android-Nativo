package ar.edu.uadexplorenow.ui.notifications;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import ar.edu.uadexplorenow.R;
import ar.edu.uadexplorenow.domain.NotificationItem;

public final class NotificationsAdapter extends RecyclerView.Adapter<NotificationsAdapter.Holder> {

    private final List<NotificationItem> items = new ArrayList<>();
    private Consumer<NotificationItem> onItemClick;

    public void setOnItemClick(Consumer<NotificationItem> listener) {
        onItemClick = listener;
    }

    public void submit(List<NotificationItem> data) {
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
                .inflate(R.layout.item_notification, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        NotificationItem item = items.get(position);
        holder.tvSource.setText(item.sourceLabel());
        holder.tvTime.setText(item.relativeTimeLabel());
        holder.tvTitle.setText(item.title);
        holder.tvMessage.setText(item.message);
        holder.unreadDot.setVisibility(item.isRead() ? View.INVISIBLE : View.VISIBLE);

        int bg = ContextCompat.getColor(holder.itemView.getContext(),
                item.isRead() ? R.color.explore_search_bg : R.color.white);
        int stroke = ContextCompat.getColor(holder.itemView.getContext(),
                item.isRead() ? R.color.explore_chip_stroke : R.color.filter_primary);
        holder.card.setCardBackgroundColor(bg);
        holder.card.setStrokeColor(stroke);
        holder.card.setAlpha(item.isRead() ? 0.72f : 1f);

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
        final MaterialCardView card;
        final View unreadDot;
        final TextView tvSource;
        final TextView tvTime;
        final TextView tvTitle;
        final TextView tvMessage;

        Holder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.cardNotification);
            unreadDot = itemView.findViewById(R.id.unreadDot);
            tvSource = itemView.findViewById(R.id.tvSource);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvMessage = itemView.findViewById(R.id.tvMessage);
        }
    }
}
