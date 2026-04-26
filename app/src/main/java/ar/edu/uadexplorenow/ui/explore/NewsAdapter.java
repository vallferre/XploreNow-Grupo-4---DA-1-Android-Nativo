package ar.edu.uadexplorenow.ui.explore;

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

import ar.edu.uadexplorenow.R;
import ar.edu.uadexplorenow.domain.NewsItem;

public class NewsAdapter extends RecyclerView.Adapter<NewsAdapter.Holder> {

    private final List<NewsItem> items = new ArrayList<>();
    private Consumer<NewsItem> onItemClick;

    public void setOnItemClick(Consumer<NewsItem> listener) {
        onItemClick = listener;
    }

    public void submit(List<NewsItem> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_news_card, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        NewsItem item = items.get(position);
        holder.tvTag.setText(item.displayTag());
        holder.tvTitle.setText(item.title);
        holder.tvBrief.setText(item.displayBrief());
        holder.tvMeta.setText(item.displayMeta());

        if (!item.imageUrl.isEmpty()) {
            Glide.with(holder.ivCover.getContext())
                    .load(item.imageUrl)
                    .centerCrop()
                    .placeholder(R.color.explore_search_bg)
                    .into(holder.ivCover);
        } else {
            holder.ivCover.setImageResource(R.color.explore_search_bg);
        }

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
        final ImageView ivCover;
        final TextView tvTag;
        final TextView tvTitle;
        final TextView tvBrief;
        final TextView tvMeta;

        Holder(@NonNull View itemView) {
            super(itemView);
            ivCover = itemView.findViewById(R.id.ivNewsCover);
            tvTag = itemView.findViewById(R.id.tvNewsTag);
            tvTitle = itemView.findViewById(R.id.tvNewsTitle);
            tvBrief = itemView.findViewById(R.id.tvNewsBrief);
            tvMeta = itemView.findViewById(R.id.tvNewsMeta);
        }
    }
}
