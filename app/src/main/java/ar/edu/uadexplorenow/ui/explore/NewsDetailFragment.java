package ar.edu.uadexplorenow.ui.explore;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
import com.google.gson.Gson;
import com.google.gson.JsonElement;

import java.util.List;

import javax.inject.Inject;

import ar.edu.uadexplorenow.R;
import ar.edu.uadexplorenow.data.model.NewsMapper;
import ar.edu.uadexplorenow.data.network.RealtimeDatabaseApi;
import ar.edu.uadexplorenow.domain.NewsItem;
import ar.edu.uadexplorenow.ui.common.SystemBarInsetsHelper;
import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class NewsDetailFragment extends Fragment {

    public static final String ARG_NEWS_ID = "news_id";

    @Inject
    RealtimeDatabaseApi realtimeDatabaseApi;
    @Inject
    Gson gson;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_news_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String newsId = getArguments() != null ? getArguments().getString(ARG_NEWS_ID) : null;
        if (newsId == null || newsId.trim().isEmpty()) {
            Navigation.findNavController(view).popBackStack();
            return;
        }

        ImageButton btnBack = view.findViewById(R.id.btnBack);
        SystemBarInsetsHelper.applyTopMargin(btnBack);
        ProgressBar progress = view.findViewById(R.id.progress);
        View contentRoot = view.findViewById(R.id.contentRoot);
        ImageView ivCover = view.findViewById(R.id.ivNewsCover);
        TextView tvTag = view.findViewById(R.id.tvNewsTag);
        TextView tvTitle = view.findViewById(R.id.tvNewsTitle);
        TextView tvBrief = view.findViewById(R.id.tvNewsBrief);
        TextView tvBody = view.findViewById(R.id.tvNewsBody);
        TextView tvRelated = view.findViewById(R.id.tvNewsRelated);

        btnBack.setOnClickListener(v -> Navigation.findNavController(view).popBackStack());

        progress.setVisibility(View.VISIBLE);
        contentRoot.setVisibility(View.GONE);

        realtimeDatabaseApi.getNews().enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(@NonNull Call<JsonElement> call, @NonNull Response<JsonElement> response) {
                if (!isAdded()) return;
                progress.setVisibility(View.GONE);
                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(requireContext(), R.string.news_load_error, Toast.LENGTH_LONG).show();
                    Navigation.findNavController(view).popBackStack();
                    return;
                }

                List<NewsItem> newsItems = NewsMapper.toNewsItems(response.body(), gson);
                NewsItem selected = null;
                for (NewsItem item : newsItems) {
                    if (newsId.equals(item.id)) {
                        selected = item;
                        break;
                    }
                }
                if (selected == null) {
                    Toast.makeText(requireContext(), R.string.news_detail_missing, Toast.LENGTH_LONG).show();
                    Navigation.findNavController(view).popBackStack();
                    return;
                }

                bindNews(selected, ivCover, tvTag, tvTitle, tvBrief, tvBody, tvRelated);
                contentRoot.setVisibility(View.VISIBLE);
            }

            @Override
            public void onFailure(@NonNull Call<JsonElement> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                progress.setVisibility(View.GONE);
                Toast.makeText(requireContext(), R.string.news_load_error, Toast.LENGTH_LONG).show();
                Navigation.findNavController(view).popBackStack();
            }
        });
    }

    private void bindNews(
            @NonNull NewsItem item,
            @NonNull ImageView ivCover,
            @NonNull TextView tvTag,
            @NonNull TextView tvTitle,
            @NonNull TextView tvBrief,
            @NonNull TextView tvBody,
            @NonNull TextView tvRelated
    ) {
        tvTag.setText(item.displayTag());
        tvTitle.setText(item.title);
        tvBrief.setText(item.displayBrief());
        tvBody.setText(item.description.isEmpty() ? item.displayBrief() : item.description);

        if (item.hasRelatedActivity()) {
            tvRelated.setVisibility(View.VISIBLE);
            tvRelated.setText(R.string.news_detail_related_activity);
        } else {
            tvRelated.setVisibility(View.GONE);
        }

        if (!item.imageUrl.isEmpty()) {
            Glide.with(this)
                    .load(item.imageUrl)
                    .centerCrop()
                    .placeholder(R.color.explore_search_bg)
                    .into(ivCover);
        } else {
            ivCover.setImageResource(R.color.explore_search_bg);
        }
    }
}
