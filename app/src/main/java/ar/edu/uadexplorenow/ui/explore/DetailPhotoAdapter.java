package ar.edu.uadexplorenow.ui.explore;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import ar.edu.uadexplorenow.R;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

public class DetailPhotoAdapter extends RecyclerView.Adapter<DetailPhotoAdapter.Holder> {

    private final List<String> urls = new ArrayList<>();

    public void submit(List<String> data) {
        urls.clear();
        if (data != null && !data.isEmpty()) {
            urls.addAll(data);
        } else {
            urls.add("");
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_detail_photo, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        String url = urls.get(position);
        if (url != null && !url.isEmpty()) {
            Glide.with(h.iv.getContext())
                    .load(url)
                    .centerCrop()
                    .placeholder(R.color.explore_accent_purple)
                    .into(h.iv);
        } else {
            h.iv.setImageResource(R.color.explore_accent_purple);
        }
    }

    @Override
    public int getItemCount() {
        return urls.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView iv;

        Holder(@NonNull View itemView) {
            super(itemView);
            iv = itemView.findViewById(R.id.ivPhoto);
        }
    }
}
