package ar.edu.uadexplorenow.ui.explore;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import ar.edu.uadexplorenow.R;
import ar.edu.uadexplorenow.domain.ActivityItem;
import ar.edu.uadexplorenow.domain.FavoriteListRow;
import ar.edu.uadexplorenow.domain.FavoriteListRow.SpotsNovelty;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class FavoritesAdapter extends RecyclerView.Adapter<FavoritesAdapter.Holder> {

    private final List<FavoriteListRow> rows = new ArrayList<>();
    private Consumer<String> onOpenDetail;
    private Consumer<ActivityItem> onRemoveFavorite;

    public void setOnOpenDetail(Consumer<String> listener) {
        onOpenDetail = listener;
    }

    public void setOnRemoveFavorite(Consumer<ActivityItem> listener) {
        onRemoveFavorite = listener;
    }

    public void submit(List<FavoriteListRow> data) {
        rows.clear();
        if (data != null) {
            rows.addAll(data);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_favorite_activity, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        FavoriteListRow row = rows.get(position);
        ActivityItem a = row.activity;

        h.tvTitle.setText(a.name);
        String dayLbl = a.dayDisplayLabel();
        if (a.destination.trim().isEmpty()) {
            h.tvLocation.setText(dayLbl.isEmpty() ? "—" : dayLbl);
        } else {
            String loc = "\uD83D\uDCCD " + a.destination.trim();
            if (!dayLbl.isEmpty()) {
                loc += " · " + dayLbl;
            }
            h.tvLocation.setText(loc);
        }

        boolean anyNovelty = row.priceNovelty || row.spotsNovelty != SpotsNovelty.NONE;
        h.columnNovelty.setVisibility(anyNovelty ? View.VISIBLE : View.GONE);

        if (row.priceNovelty) {
            h.blockPriceChange.setVisibility(View.VISIBLE);
            h.tvPriceRow.setVisibility(View.GONE);

            double delta = Math.abs(a.price - row.favoritedPrice);
            String deltaStr = formatMoneyAmount(delta, a.currency, false);
            int msgFmt = row.priceWentUp
                    ? R.string.favorites_price_increased_fmt
                    : R.string.favorites_price_reduced_fmt;
            h.tvPriceDelta.setText(h.itemView.getContext().getString(msgFmt, deltaStr));
            int deltaColor = row.priceWentUp
                    ? ContextCompat.getColor(h.itemView.getContext(), R.color.explore_warning)
                    : ContextCompat.getColor(h.itemView.getContext(), R.color.explore_price_green);
            h.tvPriceDelta.setTextColor(deltaColor);

            h.tvPriceOld.setPaintFlags(h.tvPriceOld.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            h.tvPriceOld.setText(listPriceSuffix(row.favoritedPrice, a.currency));
            h.tvPriceNow.setText(listPriceSuffix(a.price, a.currency));
        } else {
            h.blockPriceChange.setVisibility(View.GONE);
            h.tvPriceRow.setVisibility(View.VISIBLE);
            h.tvPriceRow.setText(a.listPriceSuffix());
            h.tvPriceOld.setPaintFlags(h.tvPriceOld.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        }

        if (row.spotsNovelty == SpotsNovelty.INCREASED) {
            h.tvSpotsNovelty.setVisibility(View.VISIBLE);
            int n = (int) Math.min(row.spotsIncreaseDelta, Integer.MAX_VALUE);
            h.tvSpotsNovelty.setText(h.itemView.getContext().getString(
                    R.string.favorites_spots_increased_fmt, n));
            h.tvSpotsNovelty.setTextColor(
                    ContextCompat.getColor(h.itemView.getContext(), R.color.explore_price_green));
        } else if (row.spotsNovelty == SpotsNovelty.SOLD_OUT) {
            h.tvSpotsNovelty.setVisibility(View.VISIBLE);
            h.tvSpotsNovelty.setText(R.string.favorites_spots_exhausted);
            h.tvSpotsNovelty.setTextColor(
                    ContextCompat.getColor(h.itemView.getContext(), R.color.explore_warning));
        } else {
            h.tvSpotsNovelty.setVisibility(View.GONE);
        }

        h.btnFavorite.setImageResource(R.drawable.ic_favorite_filled_24);
        ImageViewCompat.setImageTintList(h.btnFavorite, null);
        h.btnFavorite.setOnClickListener(v -> {
            if (onRemoveFavorite != null) {
                onRemoveFavorite.accept(a);
            }
        });

        if (!a.coverImageUrl.isEmpty()) {
            Glide.with(h.ivThumb.getContext())
                    .load(a.coverImageUrl)
                    .centerCrop()
                    .placeholder(R.color.explore_search_bg)
                    .into(h.ivThumb);
        } else {
            h.ivThumb.setImageResource(R.color.explore_search_bg);
        }

        h.btnReserve.setOnClickListener(v -> {
            if (onOpenDetail != null) {
                onOpenDetail.accept(a.id);
            }
        });
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    @NonNull
    private static String formatMoneyAmount(double price, String currency, boolean allowGratis) {
        String c = currency != null ? currency : "";
        if (price <= 0 && allowGratis) {
            return "Gratis";
        }
        if (price <= 0) {
            return "—";
        }
        if ("ARS".equalsIgnoreCase(c)) {
            return String.format(Locale.getDefault(), "$%.0f", price);
        }
        return String.format(Locale.getDefault(), "%s %.0f", c, price);
    }

    @NonNull
    private static String listPriceSuffix(double price, String currency) {
        String c = currency != null ? currency : "";
        if (price <= 0) {
            return "Gratis";
        }
        if ("ARS".equalsIgnoreCase(c)) {
            return String.format(Locale.getDefault(), "$%.0f / pers.", price);
        }
        return String.format(Locale.getDefault(), "%s %.0f / pers.", c, price);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView ivThumb;
        final ImageButton btnFavorite;
        final TextView tvTitle;
        final TextView tvLocation;
        final LinearLayout columnNovelty;
        final LinearLayout blockPriceChange;
        final TextView tvPriceDelta;
        final TextView tvPriceOld;
        final TextView tvPriceNow;
        final TextView tvSpotsNovelty;
        final TextView tvPriceRow;
        final MaterialButton btnReserve;

        Holder(@NonNull View itemView) {
            super(itemView);
            ivThumb = itemView.findViewById(R.id.ivThumb);
            btnFavorite = itemView.findViewById(R.id.btnFavorite);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvLocation = itemView.findViewById(R.id.tvLocation);
            columnNovelty = itemView.findViewById(R.id.columnNovelty);
            blockPriceChange = itemView.findViewById(R.id.blockPriceChange);
            tvPriceDelta = itemView.findViewById(R.id.tvPriceDelta);
            tvPriceOld = itemView.findViewById(R.id.tvPriceOld);
            tvPriceNow = itemView.findViewById(R.id.tvPriceNow);
            tvSpotsNovelty = itemView.findViewById(R.id.tvSpotsNovelty);
            tvPriceRow = itemView.findViewById(R.id.tvPriceRow);
            btnReserve = itemView.findViewById(R.id.btnReserve);
        }
    }
}
