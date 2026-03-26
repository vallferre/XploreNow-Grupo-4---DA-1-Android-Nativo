package ar.edu.uadexplorenow;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

public class ActivityDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ACTIVITY_ID = "activity_id";

    private static final int CUPOS_LOW = 5;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_activity_detail);

        String id = getIntent().getStringExtra(EXTRA_ACTIVITY_ID);
        if (id == null || id.isEmpty()) {
            finish();
            return;
        }

        View contentRoot = findViewById(R.id.contentRoot);
        ProgressBar progress = findViewById(R.id.progress);
        ViewPager2 photoPager = findViewById(R.id.photoPager);
        LinearLayout dotsContainer = findViewById(R.id.dotsContainer);
        ImageButton btnBack = findViewById(R.id.btnBack);
        TextView tvHeroCategory = findViewById(R.id.tvHeroCategory);
        TextView tvTitle = findViewById(R.id.tvTitle);
        TextView tvSubtitle = findViewById(R.id.tvSubtitle);
        TextView tvDuration = findViewById(R.id.tvDuration);
        TextView tvLanguage = findViewById(R.id.tvLanguage);
        TextView tvCupos = findViewById(R.id.tvCupos);
        TextView tvGuide = findViewById(R.id.tvGuide);
        TextView tvDescription = findViewById(R.id.tvDescription);
        TextView tvIncludesTitle = findViewById(R.id.tvIncludesTitle);
        LinearLayout includesContainer = findViewById(R.id.includesContainer);
        TextView tvMeetingTitle = findViewById(R.id.tvMeetingTitle);
        View cardMeeting = findViewById(R.id.cardMeetingPoint);
        TextView tvMeetingPoint = findViewById(R.id.tvMeetingPoint);
        TextView tvWhenLabel = findViewById(R.id.tvWhenLabel);
        TextView tvActivityWhen = findViewById(R.id.tvActivityWhen);
        TextView tvCancellationTitle = findViewById(R.id.tvCancellationTitle);
        View cardCancellation = findViewById(R.id.cardCancellation);
        TextView tvCancellationType = findViewById(R.id.tvCancellationType);
        TextView tvCancellationDesc = findViewById(R.id.tvCancellationDesc);
        TextView tvBottomPrice = findViewById(R.id.tvBottomPrice);
        MaterialButton btnReserve = findViewById(R.id.btnReserve);

        btnBack.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        btnReserve.setOnClickListener(v ->
                Toast.makeText(this, R.string.detail_book_soon, Toast.LENGTH_SHORT).show());

        DetailPhotoAdapter photoAdapter = new DetailPhotoAdapter();
        photoPager.setAdapter(photoAdapter);

        FirebaseFirestore.getInstance()
                .collection("activities")
                .document(id)
                .get()
                .addOnSuccessListener(doc -> {
                    ActivityDetail d = ActivityDetail.fromDocument(doc);
                    if (d == null) {
                        Toast.makeText(this, R.string.detail_load_error, Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }
                    bindDetail(
                            d, photoPager, dotsContainer, photoAdapter,
                            tvHeroCategory, tvTitle, tvSubtitle, tvWhenLabel, tvActivityWhen,
                            tvDuration, tvLanguage, tvCupos, tvGuide, tvDescription,
                            tvIncludesTitle, includesContainer,
                            tvMeetingTitle, cardMeeting, tvMeetingPoint,
                            tvCancellationTitle, cardCancellation, tvCancellationType, tvCancellationDesc,
                            tvBottomPrice);
                    progress.setVisibility(View.GONE);
                    contentRoot.setVisibility(View.VISIBLE);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, R.string.detail_load_error, Toast.LENGTH_LONG).show();
                    finish();
                });
    }

    private void bindDetail(
            ActivityDetail d,
            ViewPager2 photoPager,
            LinearLayout dotsContainer,
            DetailPhotoAdapter photoAdapter,
            TextView tvHeroCategory,
            TextView tvTitle,
            TextView tvSubtitle,
            TextView tvWhenLabel,
            TextView tvActivityWhen,
            TextView tvDuration,
            TextView tvLanguage,
            TextView tvCupos,
            TextView tvGuide,
            TextView tvDescription,
            TextView tvIncludesTitle,
            LinearLayout includesContainer,
            TextView tvMeetingTitle,
            View cardMeeting,
            TextView tvMeetingPoint,
            TextView tvCancellationTitle,
            View cardCancellation,
            TextView tvCancellationType,
            TextView tvCancellationDesc,
            TextView tvBottomPrice
    ) {
        List<String> urls = d.imageUrls;
        photoAdapter.submit(urls.isEmpty() ? null : urls);
        int photoCount = urls.isEmpty() ? 1 : urls.size();
        photoPager.setOffscreenPageLimit(Math.min(photoCount, 3));

        setupDots(dotsContainer, photoPager, photoCount);

        tvHeroCategory.setText(d.categoryLabel());
        tvTitle.setText(d.name);
        tvSubtitle.setText(getString(R.string.detail_subtitle_fmt,
                d.destination.isEmpty() ? "—" : d.destination,
                d.rating,
                d.reviewCount));

        String whenLine = d.formattedWhenLine();
        if (whenLine.isEmpty()) {
            tvWhenLabel.setVisibility(View.GONE);
            tvActivityWhen.setVisibility(View.GONE);
        } else {
            tvWhenLabel.setVisibility(View.VISIBLE);
            tvActivityWhen.setVisibility(View.VISIBLE);
            tvActivityWhen.setText("📅 " + whenLine);
        }

        tvDuration.setText(d.formattedDurationLong());
        tvLanguage.setText(d.languagesDisplay());
        tvCupos.setText(getString(R.string.detail_spots_fmt, (int) d.availableSpots));
        if (d.availableSpots > 0 && d.availableSpots <= CUPOS_LOW) {
            tvCupos.setTextColor(ContextCompat.getColor(this, R.color.detail_cupos_low));
        } else {
            tvCupos.setTextColor(ContextCompat.getColor(this, R.color.explore_title));
        }

        tvGuide.setText(d.guideName.isEmpty() ? "—" : d.guideName);
        tvDescription.setText(d.description);

        includesContainer.removeAllViews();
        if (d.includes.isEmpty()) {
            tvIncludesTitle.setVisibility(View.GONE);
            includesContainer.setVisibility(View.GONE);
        } else {
            tvIncludesTitle.setVisibility(View.VISIBLE);
            includesContainer.setVisibility(View.VISIBLE);
            for (String line : d.includes) {
                TextView row = new TextView(this);
                row.setText("✓ " + line);
                row.setTextColor(ContextCompat.getColor(this, R.color.explore_muted));
                row.setTextSize(15);
                int sp = (int) (6 * getResources().getDisplayMetrics().density);
                row.setPadding(0, sp, 0, sp);
                includesContainer.addView(row);
            }
        }

        if (d.meetingPoint.isEmpty()) {
            tvMeetingTitle.setVisibility(View.GONE);
            cardMeeting.setVisibility(View.GONE);
        } else {
            tvMeetingTitle.setVisibility(View.VISIBLE);
            cardMeeting.setVisibility(View.VISIBLE);
            tvMeetingPoint.setText("📍 " + d.meetingPoint);
        }

        bindCancellation(d, tvCancellationTitle, cardCancellation, tvCancellationType, tvCancellationDesc);

        tvBottomPrice.setText(d.priceLarge());
    }

    private void bindCancellation(
            ActivityDetail d,
            TextView tvCancellationTitle,
            View cardCancellation,
            TextView tvCancellationType,
            TextView tvCancellationDesc
    ) {
        ActivityDetail.CancellationPolicy pol = d.cancellationPolicy;
        if (pol == null || !pol.hasContent()) {
            tvCancellationTitle.setVisibility(View.GONE);
            cardCancellation.setVisibility(View.GONE);
            return;
        }

        String typeLabel = d.cancellationTypeLabel();
        String descText = pol.description;
        if (descText.isEmpty() && pol.freeCancelHours > 0) {
            descText = getString(R.string.detail_cancel_hours_only, pol.freeCancelHours);
        }

        boolean showType = !typeLabel.isEmpty();
        boolean showDesc = !descText.isEmpty();
        if (!showType && !showDesc) {
            tvCancellationTitle.setVisibility(View.GONE);
            cardCancellation.setVisibility(View.GONE);
            return;
        }

        tvCancellationTitle.setVisibility(View.VISIBLE);
        cardCancellation.setVisibility(View.VISIBLE);
        tvCancellationType.setVisibility(showType ? View.VISIBLE : View.GONE);
        if (showType) {
            tvCancellationType.setText(getString(R.string.detail_cancel_type_fmt, typeLabel));
        }
        tvCancellationDesc.setVisibility(showDesc ? View.VISIBLE : View.GONE);
        if (showDesc) {
            tvCancellationDesc.setText(descText);
        }
    }

    private void setupDots(LinearLayout dotsContainer, ViewPager2 pager, int count) {
        dotsContainer.removeAllViews();
        if (count <= 1) {
            dotsContainer.setVisibility(View.GONE);
            return;
        }
        dotsContainer.setVisibility(View.VISIBLE);
        float d = getResources().getDisplayMetrics().density;
        int sel = (int) (8 * d);
        int unsel = (int) (6 * d);
        int margin = (int) (4 * d);

        for (int i = 0; i < count; i++) {
            View dot = new View(this);
            boolean first = i == 0;
            int w = first ? sel : unsel;
            int h = first ? sel : unsel;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
            lp.setMargins(margin, 0, margin, 0);
            dot.setLayoutParams(lp);
            dot.setBackgroundResource(first ? R.drawable.dot_detail_selected : R.drawable.dot_detail_unselected);
            dotsContainer.addView(dot);
        }

        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                for (int i = 0; i < dotsContainer.getChildCount(); i++) {
                    View dot = dotsContainer.getChildAt(i);
                    ViewGroup.LayoutParams lp = dot.getLayoutParams();
                    if (i == position) {
                        lp.width = sel;
                        lp.height = sel;
                        dot.setBackgroundResource(R.drawable.dot_detail_selected);
                    } else {
                        lp.width = unsel;
                        lp.height = unsel;
                        dot.setBackgroundResource(R.drawable.dot_detail_unselected);
                    }
                    dot.setLayoutParams(lp);
                }
            }
        });
    }
}
