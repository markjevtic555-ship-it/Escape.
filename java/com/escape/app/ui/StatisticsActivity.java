package com.escape.app.ui;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.escape.app.R;
import com.escape.app.model.AppRestriction;
import com.escape.app.model.UsageStats;
import com.escape.app.utils.PreferencesManager;
import com.escape.app.utils.AppUsageStatsManager;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

// statistics activity
// prikazuje statistike korišćenja aplikacija
public class StatisticsActivity extends BaseActivity {

    private PreferencesManager preferencesManager;
    private AppUsageStatsManager usageStatsManager;

    private TextView kairosBalanceTextView;
    private TextView dateSubtitle;
    private TextView digitalWellbeingScoreText;
    private TextView scoreStatusText;
    private TextView timeSavedText;
    private TextView appsTrackedText;
    private TextView totalScreenTimeText;
    private TextView appsOverLimitText;
    private LinearLayout appsContainer;
    private TextView summaryTotalUsageText;
    private TextView summaryAppsUsedText;
    private TextView summaryDateText;

    private View headerSection;
    private View scoreHeroCard;
    private View quickStatsRow;
    private View dailySummaryCard;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        initializeUtilities();
        initializeViews();
        loadStatistics();
        animateEntrance();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadStatistics();
    }

    private void initializeUtilities() {
        preferencesManager = new PreferencesManager(this);
        usageStatsManager = new AppUsageStatsManager(this);
    }

    private void initializeViews() {
        headerSection = findViewById(R.id.headerSection);
        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        kairosBalanceTextView = findViewById(R.id.kairosBalanceTextView);
        dateSubtitle = findViewById(R.id.dateSubtitle);

        scoreHeroCard = findViewById(R.id.scoreHeroCard);
        digitalWellbeingScoreText = findViewById(R.id.digitalWellbeingScoreText);
        scoreStatusText = findViewById(R.id.scoreStatusText);
        timeSavedText = findViewById(R.id.timeSavedText);
        appsTrackedText = findViewById(R.id.appsTrackedText);

        quickStatsRow = findViewById(R.id.quickStatsRow);
        totalScreenTimeText = findViewById(R.id.totalScreenTimeText);
        appsOverLimitText = findViewById(R.id.appsOverLimitText);

        appsContainer = findViewById(R.id.appsContainer);

        dailySummaryCard = findViewById(R.id.dailySummaryCard);
        summaryTotalUsageText = findViewById(R.id.summaryTotalUsageText);
        summaryAppsUsedText = findViewById(R.id.summaryAppsUsedText);
        summaryDateText = findViewById(R.id.summaryDateText);

        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, MMMM d", Locale.getDefault());
        dateSubtitle.setText(dateFormat.format(new Date()));

        updateKairosDisplay();
    }

    private void updateKairosDisplay() {
        preferencesManager.checkAndPerformWeeklyKairosReset();
        int balance = preferencesManager.getKairosBalance();
        kairosBalanceTextView.setText(String.valueOf(balance));
    }

    private void loadStatistics() {
        appsContainer.removeAllViews();

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        List<AppRestriction> restrictions = preferencesManager.loadAppRestrictions();

        if (restrictions.isEmpty()) {
            addEmptyStateCard();
            updateSummaryWithNoData();
            return;
        }

        long totalUsageToday = 0;
        long totalReasonableLimit = 0;
        int totalAppsUsed = 0;
        int appsExceeded = 0;
        int trackedAppsCount = 0;

        final long MAX_REASONABLE_LIMIT_MS = 8 * 60 * 60 * 1000L;

        for (AppRestriction restriction : restrictions) {
            if (!restriction.isEnabled() || restriction.getDailyLimitMinutes() <= 0) {
                continue;
            }

            long appUsage = usageStatsManager.getAppUsageTimeToday(restriction.getPackageName());
            long limitMs = restriction.getDailyLimitMinutes() * 60 * 1000L;

            trackedAppsCount++;

            long cappedLimit = Math.min(limitMs, MAX_REASONABLE_LIMIT_MS);

            totalUsageToday += appUsage;
            totalReasonableLimit += cappedLimit;

            if (appUsage > 0) {
                totalAppsUsed++;
            }

            if (appUsage > limitMs) {
                appsExceeded++;
            }
        }

        long totalScreenTimeToday = preferencesManager.getTotalScreenTimeToday();
        
        double usageRatio = totalReasonableLimit > 0 ? (double) totalUsageToday / totalReasonableLimit : 0.0;
        double exceedRatio = trackedAppsCount > 0 ? (double) appsExceeded / trackedAppsCount : 0.0;
        int wellbeingScore = (int) (100 * (1.0 - Math.min(1.0, usageRatio) * 0.6 - exceedRatio * 0.4));
        wellbeingScore = Math.max(0, Math.min(100, wellbeingScore));

        long timeSaved = Math.max(0, totalReasonableLimit - totalUsageToday);
        timeSaved = Math.min(timeSaved, 24 * 60 * 60 * 1000L);

        animateScoreCounter(wellbeingScore);
        updateScoreStatus(wellbeingScore);

        timeSavedText.setText(formatTimeSaved(timeSaved));
        appsTrackedText.setText(String.valueOf(trackedAppsCount));
        totalScreenTimeText.setText(UsageStats.formatTime(totalScreenTimeToday));
        appsOverLimitText.setText(String.valueOf(appsExceeded));

        if (appsExceeded > 0) {
            appsOverLimitText.setTextColor(Color.parseColor("#FF6B6B"));
        } else {
            appsOverLimitText.setTextColor(Color.parseColor("#69DB7C"));
        }

        for (int i = 0; i < restrictions.size(); i++) {
            AppRestriction restriction = restrictions.get(i);
            long appUsage = usageStatsManager.getAppUsageTimeToday(restriction.getPackageName());
            addAppCard(restriction, appUsage, i);
        }

        summaryTotalUsageText.setText(UsageStats.formatTime(totalScreenTimeToday));
        summaryAppsUsedText.setText(totalAppsUsed + "/" + trackedAppsCount);
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault());
        summaryDateText.setText(dateFormat.format(new Date()));
    }

    private String formatTimeSaved(long millis) {
        long totalMinutes = millis / (1000 * 60);
        if (totalMinutes >= 60) {
            long hours = totalMinutes / 60;
            long minutes = totalMinutes % 60;
            if (hours > 24) {
                return "24h+"; // Cap display
            }
            return hours + "h " + minutes + "m";
        } else {
            return totalMinutes + "m";
        }
    }

    private void animateScoreCounter(int targetScore) {
        ValueAnimator animator = ValueAnimator.ofInt(0, targetScore);
        animator.setDuration(1500);
        animator.setInterpolator(new DecelerateInterpolator(2f));
        animator.addUpdateListener(animation -> {
            int value = (int) animation.getAnimatedValue();
            digitalWellbeingScoreText.setText(String.valueOf(value));
        });
        animator.start();
    }

    private void updateScoreStatus(int score) {
        String status;
        int color;

        if (score >= 80) {
            status = getString(R.string.excellent_control);
            color = Color.parseColor("#69DB7C"); // Soft green
        } else if (score >= 60) {
            status = getString(R.string.good_progress);
            color = Color.parseColor("#8CE99A"); // Light green
        } else if (score >= 40) {
            status = getString(R.string.needs_attention);
            color = Color.parseColor("#FFE066"); // Soft yellow
        } else if (score >= 20) {
            status = getString(R.string.high_usage);
            color = Color.parseColor("#FFA94D"); // Soft orange
        } else {
            status = getString(R.string.over_limit_status);
            color = Color.parseColor("#FF6B6B"); // Soft red
        }

        scoreStatusText.setText(status);
        scoreStatusText.setTextColor(color);
    }

    private void addEmptyStateCard() {
        LinearLayout emptyCard = new LinearLayout(this);
        emptyCard.setOrientation(LinearLayout.VERTICAL);
        emptyCard.setGravity(Gravity.CENTER);
        emptyCard.setBackgroundResource(R.drawable.stats_mini_card);
        emptyCard.setPadding(dpToPx(24), dpToPx(48), dpToPx(24), dpToPx(48));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dpToPx(16));
        emptyCard.setLayoutParams(params);

        TextView emoji = new TextView(this);
        emoji.setText("📊");
        emoji.setTextSize(48);
        emoji.setGravity(Gravity.CENTER);
        emoji.setPadding(0, 0, 0, dpToPx(16));

        TextView title = new TextView(this);
        title.setText(getString(R.string.no_apps_tracked_yet));
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dpToPx(8));

        TextView subtitle = new TextView(this);
        subtitle.setText(getString(R.string.add_apps_to_track));
        subtitle.setTextColor(Color.parseColor("#8B8BA7"));
        subtitle.setTextSize(14);
        subtitle.setGravity(Gravity.CENTER);

        emptyCard.addView(emoji);
        emptyCard.addView(title);
        emptyCard.addView(subtitle);
        appsContainer.addView(emptyCard);
    }

    private void updateSummaryWithNoData() {
        digitalWellbeingScoreText.setText(getString(R.string.dash));
        scoreStatusText.setText(getString(R.string.add_apps_to_track_short));
        scoreStatusText.setTextColor(Color.parseColor("#8B8BA7"));
        timeSavedText.setText(getString(R.string.dash));
        appsTrackedText.setText("0");
        totalScreenTimeText.setText("0m");
        appsOverLimitText.setText("0");
        summaryTotalUsageText.setText("0m");
        summaryAppsUsedText.setText(getString(R.string.zero_slash_zero));
    }

    private void addAppCard(AppRestriction restriction, long appUsage, int index) {
        // glavni card container
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.stats_app_card_premium);
        card.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
        card.setClickable(true);
        card.setFocusable(true);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dpToPx(12));
        card.setLayoutParams(cardParams);

        // top red: icon, name, chevron
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        topRow.setPadding(0, 0, 0, dpToPx(12));

        // ikona aplikacije
        FrameLayout iconContainer = new FrameLayout(this);
        LinearLayout.LayoutParams iconContainerParams = new LinearLayout.LayoutParams(
            dpToPx(48), dpToPx(48));
        iconContainer.setLayoutParams(iconContainerParams);

        ImageView iconView = new ImageView(this);
        try {
            Drawable icon = getPackageManager().getApplicationIcon(restriction.getPackageName());
            iconView.setImageDrawable(icon);
        } catch (PackageManager.NameNotFoundException e) {
            iconView.setImageResource(R.mipmap.ic_launcher);
        }
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
            dpToPx(48), dpToPx(48));
        iconParams.gravity = Gravity.CENTER;
        iconView.setLayoutParams(iconParams);

        iconContainer.addView(iconView);

        // ime aplikacije i informacije o korišćenju
        LinearLayout infoLayout = new LinearLayout(this);
        infoLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        infoParams.setMargins(dpToPx(14), 0, dpToPx(8), 0);
        infoLayout.setLayoutParams(infoParams);

        TextView appName = new TextView(this);
        appName.setText(restriction.getAppName());
        appName.setTextColor(Color.WHITE);
        appName.setTextSize(16);
        appName.setTypeface(null, android.graphics.Typeface.BOLD);
        appName.setMaxLines(1);
        appName.setEllipsize(android.text.TextUtils.TruncateAt.END);

        long limitMs = restriction.getDailyLimitMinutes() * 60 * 1000L;
        double percentage = limitMs > 0 ? (appUsage / (double) limitMs) * 100 : 0;

        TextView usageText = new TextView(this);
        usageText.setText(UsageStats.formatTime(appUsage) + " / " + 
            restriction.getDailyLimitMinutes() + " min");
        usageText.setTextColor(Color.parseColor("#A0A0B8"));
        usageText.setTextSize(13);
        usageText.setPadding(0, dpToPx(2), 0, 0);

        infoLayout.addView(appName);
        infoLayout.addView(usageText);

        // chevron dugme
        FrameLayout chevronContainer = new FrameLayout(this);
        LinearLayout.LayoutParams chevronContainerParams = new LinearLayout.LayoutParams(
            dpToPx(32), dpToPx(32));
        chevronContainer.setLayoutParams(chevronContainerParams);
        chevronContainer.setBackgroundResource(R.drawable.stats_chevron_circle);

        ImageView chevron = new ImageView(this);
        chevron.setImageResource(android.R.drawable.ic_media_play);
        chevron.setColorFilter(Color.parseColor("#6B6B8A"));
        FrameLayout.LayoutParams chevronParams = new FrameLayout.LayoutParams(
            dpToPx(14), dpToPx(14));
        chevronParams.gravity = Gravity.CENTER;
        chevron.setLayoutParams(chevronParams);

        chevronContainer.addView(chevron);

        topRow.addView(iconContainer);
        topRow.addView(infoLayout);
        topRow.addView(chevronContainer);

        // progress bar sekcija
        LinearLayout progressSection = new LinearLayout(this);
        progressSection.setOrientation(LinearLayout.VERTICAL);

        // progress bar pozadina
        FrameLayout progressContainer = new FrameLayout(this);
        LinearLayout.LayoutParams progressContainerParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(6));
        progressContainer.setLayoutParams(progressContainerParams);

        GradientDrawable progressBg = new GradientDrawable();
        progressBg.setShape(GradientDrawable.RECTANGLE);
        progressBg.setColor(Color.parseColor("#1A1A2E"));
        progressBg.setCornerRadius(dpToPx(3));
        progressContainer.setBackground(progressBg);

        // progress bar fill
        View progressFill = new View(this);
        float progressRatio = Math.min(1f, (float) percentage / 100f);
        FrameLayout.LayoutParams fillParams = new FrameLayout.LayoutParams(
            0, FrameLayout.LayoutParams.MATCH_PARENT);
        progressFill.setLayoutParams(fillParams);

        // setujem boju na osnovu procenta - koristim theme-matching boje
        GradientDrawable fillDrawable = new GradientDrawable();
        fillDrawable.setShape(GradientDrawable.RECTANGLE);
        fillDrawable.setCornerRadius(dpToPx(3));

        if (percentage >= 100) {
            fillDrawable.setColors(new int[]{
                Color.parseColor("#FF6B6B"),
                Color.parseColor("#EE5A5A")
            });
        } else if (percentage >= 80) {
            fillDrawable.setColors(new int[]{
                Color.parseColor("#FFA94D"),
                Color.parseColor("#FF922B")
            });
        } else if (percentage >= 50) {
            fillDrawable.setColors(new int[]{
                Color.parseColor("#FFE066"),
                Color.parseColor("#FFD43B")
            });
        } else {
            fillDrawable.setColors(new int[]{
                Color.parseColor("#69DB7C"),
                Color.parseColor("#51CF66")
            });
        }
        fillDrawable.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        progressFill.setBackground(fillDrawable);

        progressContainer.addView(progressFill);

        // percentage label
        LinearLayout percentageRow = new LinearLayout(this);
        percentageRow.setOrientation(LinearLayout.HORIZONTAL);
        percentageRow.setGravity(Gravity.END);
        LinearLayout.LayoutParams percentRowParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        percentRowParams.setMargins(0, dpToPx(6), 0, 0);
        percentageRow.setLayoutParams(percentRowParams);

        TextView percentageText = new TextView(this);
        percentageText.setText(String.format(Locale.getDefault(), getString(R.string.percent_used), Math.min(percentage, 999)));
        percentageText.setTextSize(11);

        if (percentage >= 100) {
            percentageText.setTextColor(Color.parseColor("#FF6B6B"));
        } else if (percentage >= 80) {
            percentageText.setTextColor(Color.parseColor("#FFA94D"));
        } else if (percentage >= 50) {
            percentageText.setTextColor(Color.parseColor("#FFE066"));
        } else {
            percentageText.setTextColor(Color.parseColor("#69DB7C"));
        }

        percentageRow.addView(percentageText);

        progressSection.addView(progressContainer);
        progressSection.addView(percentageRow);

        card.addView(topRow);
        card.addView(progressSection);

        // klik na karticu - otvara detalje aplikacije
        card.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(StatisticsActivity.this, AppDetailActivity.class);
                intent.putExtra(AppDetailActivity.EXTRA_PACKAGE_NAME, restriction.getPackageName());
                intent.putExtra(AppDetailActivity.EXTRA_APP_NAME, restriction.getAppName());
                startActivity(intent);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        appsContainer.addView(card);

        // animiram progress bar fill
        card.post(() -> {
            int targetWidth = (int) (progressContainer.getWidth() * progressRatio);
            ValueAnimator animator = ValueAnimator.ofInt(0, targetWidth);
            animator.setDuration(800);
            animator.setStartDelay(index * 100L + 300);
            animator.setInterpolator(new DecelerateInterpolator(2f));
            animator.addUpdateListener(animation -> {
                fillParams.width = (int) animation.getAnimatedValue();
                progressFill.setLayoutParams(fillParams);
            });
            animator.start();
        });

        // entrance animacija
        card.setAlpha(0f);
        card.setTranslationY(dpToPx(20));
        card.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400)
            .setStartDelay(index * 80L + 200)
            .setInterpolator(new DecelerateInterpolator(2f))
            .start();
    }

    private void animateEntrance() {
        View[] views = {headerSection, scoreHeroCard, quickStatsRow, dailySummaryCard};

        for (int i = 0; i < views.length; i++) {
            View view = views[i];
            if (view != null) {
                view.setAlpha(0f);
                view.setTranslationY(dpToPx(30));
                view.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(500)
                    .setStartDelay(i * 100L)
                    .setInterpolator(new DecelerateInterpolator(2f))
                    .start();
            }
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
